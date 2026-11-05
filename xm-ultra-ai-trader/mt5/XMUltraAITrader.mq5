// XM ULTRA AI TRADER -- demo-default, account-authorized execution engine.
// SPDX-License-Identifier: MIT
#property copyright "XM Ultra AI Trader contributors"
#property version   "1.001"
#property strict
#property description "Demo-only, fail-closed MT5 execution. No profitability claim."

input string InpSymbols="";                 // Empty = this chart; comma-separated exact broker names.
input bool InpScanMarketWatch=true;         // Scan selected Market Watch instruments when InpSymbols is empty.
input int InpMaxScanSymbols=8;               // Bounded workload for low-memory Windows hardware.
input ENUM_TIMEFRAMES InpTimeframe=PERIOD_M15;
input ENUM_TIMEFRAMES InpConfirmationTimeframe=PERIOD_H1;
input long InpMagic=70125001;
input string InpBridgeFolder="XMUltraAI";
input int InpFastEMA=12;
input int InpSlowEMA=26;
input int InpATRPeriod=14;
input int InpLookback=20;
input double InpStopATR=2.0;
input double InpRewardRisk=1.5;
input int InpMaxSpreadPoints=25;
input int InpMaxDeviationPoints=10;
input int InpMaxTickAgeSeconds=15;
input int InpCooldownMinutes=60;
input int InpMaxHoldingBars=96;
input bool InpTrailing=true;
input bool InpBreakEven=true;
input bool InpPartialClose=false;             // Hedging only; default disabled.
input bool InpNewsGuard=true;
input int InpNewsBeforeMinutes=30;
input int InpNewsAfterMinutes=15;
input int InpSessionStartHour=7;              // Broker-server time; used by session strategy only.
input int InpSessionEndHour=17;
input bool InpAllowLive=false;              // Local opt-in, never inferred from a signal.
input long InpLiveAccountLogin=0;             // Exact authorized broker login.
input string InpLiveAccountServer="";        // Exact authorized broker server.
input bool InpTesterAutoStart=false;          // Explicit Strategy Tester use only; never live terminal.
input string InpTesterStrategy="ema_cross";

struct MarketContext
{
   string symbol;
   int fast_handle,slow_handle,atr_handle,rsi_handle,adx_handle,higher_fast,higher_slow;
   datetime last_bar,attempted_bar,last_tick_time,last_evaluation;
   double fast1,fast2,slow1,slow2,atr,rsi,adx,high,low,close1,close2,open1;
   double higher_fast1,higher_slow1;
   bool valid,news_known,news_blocked;
   datetime news_checked;
   string explanation;
};
struct QueuedEvent
{
   string id,kind,symbol,side,strategy,message;
   ulong ticket;
   datetime at;
   double volume,price,profit,commission,swap;
};
MarketContext g_markets[];
QueuedEvent g_events[];
string g_secret="",g_state="paused",g_reason="Waiting for an authenticated demo start",g_strategy="ema_cross";
string g_last_command="",g_prefix="";
bool g_tester=false,g_emergency=false,g_uncertain=false,g_anchor_block=false,g_events_overflow=false;
bool g_running_requested=false;
int g_lock=INVALID_HANDLE,g_common=FILE_COMMON,g_losses=0;
datetime g_lease=0,g_day=0,g_week=0,g_cooldown=0,g_last_timer=0,g_last_history=0;
double g_day_equity=0,g_week_equity=0,g_high_water=0;
double g_risk_pct=.25,g_daily_pct=1,g_weekly_pct=3,g_drawdown_pct=5;
int g_max_positions=2,g_max_losses=3;
ulong g_event_counter=0,g_heartbeat=0,g_decision_us=0,g_risk_us=0,g_submission_us=0,g_broker_us=0;
ulong g_tick_us=0,g_pending_order=0,g_pending_deal=0;
datetime g_pending_at=0;
long g_account=0;

string Path(const string name) { return InpBridgeFolder+"\\"+name; }
bool Finite(const double x) { return MathIsValidNumber(x); }
string N(const double value,const int digits=8) { return DoubleToString(value,digits); }
string U(const ulong value) { return StringFormat("%I64u",value); }
string L(const long value) { return StringFormat("%I64d",value); }
string Safe(string s)
{
   StringReplace(s,",",";"); StringReplace(s,"\r"," "); StringReplace(s,"\n"," ");
   StringReplace(s,"\"","'"); return s;
}
datetime UtcNow() { return TimeGMT(); }
datetime DayStart(const datetime t) { return (datetime)((long)t-((long)t%86400)); }
datetime WeekStart(const datetime t)
{
   MqlDateTime dt; TimeToStruct(t,dt); int offset=(dt.day_of_week+6)%7;
   return DayStart(t)-offset*86400;
}
string Key(const string field) { return g_prefix+field; }
uint Hash(const string value)
{
   uint h=2166136261;
   for(int i=0;i<StringLen(value);i++) { h^=(uint)StringGetCharacter(value,i); h*=16777619; }
   return h;
}
bool AllowedAccount()
{
   long mode=AccountInfoInteger(ACCOUNT_TRADE_MODE);
   return mode==ACCOUNT_TRADE_MODE_DEMO || (mode==ACCOUNT_TRADE_MODE_REAL && InpAllowLive && InpLiveAccountLogin>0 && AccountInfoInteger(ACCOUNT_LOGIN)==InpLiveAccountLogin && InpLiveAccountServer!="" && AccountInfoString(ACCOUNT_SERVER)==InpLiveAccountServer);
}
string AccountIdentity() { return L(g_account)+"-"+U(Hash(AccountInfoString(ACCOUNT_SERVER))); }
void SetReason(const string reason) { g_reason=reason; }
void QueueEvent(const string kind,const string message,const string symbol="",const ulong ticket=0,
                const string side="",const double volume=0,const double price=0,const double profit=0,
                const double commission=0,const double swap=0,const string strategy="",const string stable_id="")
{
   int n=ArraySize(g_events);
   if(n>=4096) { g_events_overflow=true; g_running_requested=false; g_reason="Event buffer full; audit persistence unavailable"; return; }
   ArrayResize(g_events,n+1);
   g_events[n].id=AccountIdentity()+":"+(stable_id!="" ? stable_id : L(g_account)+"-"+L((long)UtcNow())+"-"+U(GetMicrosecondCount())+"-"+U(++g_event_counter));
   g_events[n].at=UtcNow(); g_events[n].kind=kind; g_events[n].message=Safe(message);
   g_events[n].symbol=Safe(symbol); g_events[n].ticket=ticket; g_events[n].side=side;
   g_events[n].volume=volume; g_events[n].price=price; g_events[n].profit=profit;
   g_events[n].commission=commission; g_events[n].swap=swap;
   g_events[n].strategy=(strategy=="" ? g_strategy : strategy);
}
bool OpenSnapshot(const string filename,int &handle)
{
   handle=FileOpen(Path(filename+".tmp"),FILE_WRITE|FILE_CSV|FILE_ANSI|g_common,',',CP_UTF8);
   return handle!=INVALID_HANDLE;
}
bool CommitSnapshot(const string filename,const int handle)
{
   FileFlush(handle); FileClose(handle);
   return FileMove(Path(filename+".tmp"),g_common,Path(filename),FILE_REWRITE|g_common);
}
bool ValidStrategy(const string id)
{
   return id=="ema_cross" || id=="trend" || id=="momentum" || id=="breakout" ||
          id=="mean_reversion" || id=="support_resistance" || id=="volatility" ||
          id=="session" || id=="multi_timeframe" || id=="regime";
}
void Persist()
{
   if(g_tester) return;
   GlobalVariableSet(Key("day"),(double)g_day); GlobalVariableSet(Key("week"),(double)g_week);
   GlobalVariableSet(Key("dayeq"),g_day_equity); GlobalVariableSet(Key("weekeq"),g_week_equity);
   GlobalVariableSet(Key("hwm"),g_high_water); GlobalVariableSet(Key("losses"),g_losses);
   GlobalVariableSet(Key("cooldown"),(double)g_cooldown);
   GlobalVariableSet(Key("emergency"),g_emergency?1:0);
   GlobalVariableSet(Key("uncertain"),g_uncertain?1:0);
   GlobalVariableSet(Key("anchorblock"),g_anchor_block?1:0);
   GlobalVariableSet(Key("lastseen"),(double)UtcNow());
   GlobalVariablesFlush();
}
string g_anchor_reason="Verified broker history is required";
bool ReconstructFlatBaseline(const datetime period,double &baseline,string &why)
{
   const long guard=10800; // Refuse activity around uncertain historical broker UTC offsets.
   datetime now=UtcNow(),server_now=TimeTradeServer();
   if(now<period+guard) { why="Period is too recent for guarded broker-history reconciliation"; return false; }
   if(!TerminalInfoInteger(TERMINAL_CONNECTED)||PositionsTotal()!=0||OrdersTotal()!=0)
   { why="Reconciliation requires a connected account with no positions or pending orders"; return false; }
   double equity=AccountInfoDouble(ACCOUNT_EQUITY),balance=AccountInfoDouble(ACCOUNT_BALANCE),credit=AccountInfoDouble(ACCOUNT_CREDIT);
   if(!Finite(equity)||!Finite(balance)||!Finite(credit)||equity<=0||MathAbs(equity-balance-credit)>.01)
   { why="Account equity cannot be verified as flat"; return false; }
   long offset=(long)server_now-(long)now;
   datetime boundary=(datetime)((long)period+offset);
   if(!HistorySelect((datetime)((long)boundary-guard),server_now))
   { why="Broker history is unavailable"; return false; }
   int count=HistoryDealsTotal();
   if(count>5000) { why="History exceeds automatic reconciliation budget; independent review required"; return false; }
   ulong ids[]; double volumes[]; int used=0; double changes=0;
   for(int i=0;i<count;i++)
   {
      ulong ticket=HistoryDealGetTicket(i); if(ticket==0) { why="Incomplete broker history"; return false; }
      datetime stamp=(datetime)HistoryDealGetInteger(ticket,DEAL_TIME);
      if(MathAbs((double)((long)stamp-(long)boundary))<=guard)
      { why="Activity near UTC boundary needs historical timezone/equity review"; return false; }
      if(stamp<boundary) continue;
      ENUM_DEAL_TYPE type=(ENUM_DEAL_TYPE)HistoryDealGetInteger(ticket,DEAL_TYPE);
      if(type==DEAL_TYPE_BUY_CANCELED||type==DEAL_TYPE_SELL_CANCELED)
      { why="Cancelled historical deals require independent reconciliation"; return false; }
      double delta=HistoryDealGetDouble(ticket,DEAL_PROFIT)+HistoryDealGetDouble(ticket,DEAL_COMMISSION)+HistoryDealGetDouble(ticket,DEAL_SWAP)+HistoryDealGetDouble(ticket,DEAL_FEE);
      if(!Finite(delta)) { why="Invalid historical account cash flow"; return false; }
      changes+=delta;
      if(type==DEAL_TYPE_BUY||type==DEAL_TYPE_SELL)
      {
         ulong id=(ulong)HistoryDealGetInteger(ticket,DEAL_POSITION_ID);
         double volume=HistoryDealGetDouble(ticket,DEAL_VOLUME);
         if(id==0||!Finite(volume)||volume<=0) { why="Missing position audit details"; return false; }
         int at=-1; for(int j=0;j<used;j++) if(ids[j]==id) { at=j; break; }
         if(at<0) { at=used++; ArrayResize(ids,used); ArrayResize(volumes,used); ids[at]=id; volumes[at]=0; }
         volumes[at]+=(type==DEAL_TYPE_BUY?volume:-volume);
      }
   }
   for(int i=0;i<used;i++) if(MathAbs(volumes[i])>1e-8)
   { why="A position crossed the period boundary; historical floating equity must be verified"; return false; }
   baseline=equity-changes;
   if(!Finite(baseline)||baseline<=0) { why="Reconstructed equity baseline is invalid"; return false; }
   return true;
}
bool ReconcileMissedBoundaries()
{
   if(g_tester||!g_anchor_block) return !g_anchor_block;
   datetime day=DayStart(UtcNow()),week=WeekStart(UtcNow());
   double day_equity=g_day_equity,week_equity=g_week_equity; string why;
   if(g_day==day&&g_week==week) { g_anchor_reason="Flagged current-period state requires independent reconciliation"; return false; }
   if(g_day!=day&&!ReconstructFlatBaseline(day,day_equity,why)) { g_anchor_reason=why; return false; }
   if(g_week!=week&&!ReconstructFlatBaseline(week,week_equity,why)) { g_anchor_reason=why; return false; }
   // Update only verifiable period anchors. Never erase loss streaks or lower the lifetime peak.
   g_day=day; g_week=week; g_day_equity=day_equity; g_week_equity=week_equity;
   g_high_water=MathMax(g_high_water,MathMax(day_equity,week_equity));
   g_anchor_block=false; g_anchor_reason="";
   Persist(); QueueEvent("risk_baseline_reconciled","Missed-period equity baselines reconstructed from flat-account broker history; valid current-period anchors and loss/drawdown checks remain active");
   return true;
}

bool LoadAnchors()
{
   datetime now=UtcNow(); double equity=AccountInfoDouble(ACCOUNT_EQUITY);
   if(!Finite(equity)||equity<=0) return false;
   if(!g_tester && GlobalVariableCheck(Key("day")))
   {
      string required[]={"week","dayeq","weekeq","hwm","losses","cooldown","emergency","uncertain","anchorblock","lastseen"};
      for(int i=0;i<ArraySize(required);i++) if(!GlobalVariableCheck(Key(required[i]))) return false;
      g_day=(datetime)GlobalVariableGet(Key("day")); g_week=(datetime)GlobalVariableGet(Key("week"));
      g_day_equity=GlobalVariableGet(Key("dayeq")); g_week_equity=GlobalVariableGet(Key("weekeq"));
      g_high_water=GlobalVariableGet(Key("hwm")); g_losses=(int)GlobalVariableGet(Key("losses"));
      g_cooldown=(datetime)GlobalVariableGet(Key("cooldown"));
      g_emergency=GlobalVariableGet(Key("emergency"))!=0;
      g_uncertain=GlobalVariableGet(Key("uncertain"))!=0;
      g_anchor_block=GlobalVariableGet(Key("anchorblock"))!=0;
      // A missed UTC boundary cannot be reconstructed from balance alone because floating P/L matters.
      if(g_day!=DayStart(now)||g_week!=WeekStart(now)) g_anchor_block=true;
      if(!Finite(g_day_equity)||!Finite(g_week_equity)||!Finite(g_high_water)||
         g_day_equity<=0||g_week_equity<=0||g_high_water<=0) return false;
   }
   else
   {
      g_day=DayStart(now); g_week=WeekStart(now);
      g_day_equity=equity; g_week_equity=equity; g_high_water=equity;
   }
   if(g_anchor_block) ReconcileMissedBoundaries();
   Persist(); return true;
}
void UpdateAnchors()
{
   datetime now=UtcNow(); double equity=AccountInfoDouble(ACCOUNT_EQUITY);
   if(!Finite(equity)||equity<=0) return;
   if(g_day!=DayStart(now)||g_week!=WeekStart(now))
   {
      // Only a continuously observed boundary permits automatic reset of a period anchor.
      if(g_last_timer==0||now-g_last_timer>5) g_anchor_block=true;
      if(!g_anchor_block)
      {
         if(g_day!=DayStart(now)) { g_day=DayStart(now); g_day_equity=equity; QueueEvent("daily_summary","UTC daily period closed; a new equity anchor was recorded"); }
         if(g_week!=WeekStart(now)) { g_week=WeekStart(now); g_week_equity=equity; }
      }
   }
   if(equity>g_high_water) g_high_water=equity;
   if(g_cooldown>0 && now>=g_cooldown) { g_losses=0; g_cooldown=0; }
}
bool CopyValue(const int handle,const int shift,double &value)
{
   double buffer[1];
   if(handle==INVALID_HANDLE||BarsCalculated(handle)<InpSlowEMA+5||CopyBuffer(handle,0,shift,1,buffer)!=1) return false;
   value=buffer[0]; return Finite(value)&&value!=EMPTY_VALUE;
}
bool InitializeMarket(const string symbol,MarketContext &m)
{
   m.symbol=symbol; m.valid=false; m.last_bar=0; m.attempted_bar=0; m.news_checked=0;
   m.news_known=false; m.news_blocked=true; m.last_tick_time=0;
   if(!SymbolSelect(symbol,true)) return false;
   m.fast_handle=iMA(symbol,InpTimeframe,InpFastEMA,0,MODE_EMA,PRICE_CLOSE);
   m.slow_handle=iMA(symbol,InpTimeframe,InpSlowEMA,0,MODE_EMA,PRICE_CLOSE);
   m.atr_handle=iATR(symbol,InpTimeframe,InpATRPeriod);
   m.rsi_handle=iRSI(symbol,InpTimeframe,14,PRICE_CLOSE);
   m.adx_handle=iADX(symbol,InpTimeframe,14);
   m.higher_fast=iMA(symbol,InpConfirmationTimeframe,InpFastEMA,0,MODE_EMA,PRICE_CLOSE);
   m.higher_slow=iMA(symbol,InpConfirmationTimeframe,InpSlowEMA,0,MODE_EMA,PRICE_CLOSE);
   return m.fast_handle!=INVALID_HANDLE&&m.slow_handle!=INVALID_HANDLE&&m.atr_handle!=INVALID_HANDLE&&
          m.rsi_handle!=INVALID_HANDLE&&m.adx_handle!=INVALID_HANDLE&&m.higher_fast!=INVALID_HANDLE&&m.higher_slow!=INVALID_HANDLE;
}
bool RefreshIndicators(MarketContext &m)
{
   m.valid=false;
   if(!SeriesInfoInteger(m.symbol,InpTimeframe,SERIES_SYNCHRONIZED)) return false;
   if(!CopyValue(m.fast_handle,1,m.fast1)||!CopyValue(m.fast_handle,2,m.fast2)||
      !CopyValue(m.slow_handle,1,m.slow1)||!CopyValue(m.slow_handle,2,m.slow2)||
      !CopyValue(m.atr_handle,1,m.atr)||!CopyValue(m.rsi_handle,1,m.rsi)||
      !CopyValue(m.adx_handle,1,m.adx)||!CopyValue(m.higher_fast,1,m.higher_fast1)||
      !CopyValue(m.higher_slow,1,m.higher_slow1)||m.atr<=0) return false;
   MqlRates bars[]; ArraySetAsSeries(bars,true);
   if(CopyRates(m.symbol,InpTimeframe,1,InpLookback+2,bars)!=InpLookback+2) return false;
   m.close1=bars[0].close; m.close2=bars[1].close; m.open1=bars[0].open;
   m.high=bars[1].high; m.low=bars[1].low;
   // Range excludes the latest closed bar; therefore a breakout does not peek into its own range.
   for(int i=1;i<=InpLookback;i++) { m.high=MathMax(m.high,bars[i].high); m.low=MathMin(m.low,bars[i].low); }
   m.valid=Finite(m.close1)&&Finite(m.high)&&Finite(m.low)&&m.low>0;
   return m.valid;
}
bool InSession(const string symbol)
{
   MqlDateTime now; TimeToStruct(TimeTradeServer(),now);
   int sec=now.hour*3600+now.min*60+now.sec;
   datetime from,to; bool found=false;
   for(uint i=0;i<20;i++)
   {
      if(!SymbolInfoSessionTrade(symbol,(ENUM_DAY_OF_WEEK)now.day_of_week,i,from,to)) break;
      found=true; int a=(int)((long)from%86400),b=(int)((long)to%86400);
      if(a==b || (a<b&&sec>=a&&sec<b) || (a>b&&(sec>=a||sec<b))) return true;
   }
   return false; // Unknown trading-session metadata is not permission to trade.
}
bool SessionStrategyTime()
{
   MqlDateTime now; TimeToStruct(TimeTradeServer(),now);
   if(InpSessionStartHour<InpSessionEndHour) return now.hour>=InpSessionStartHour&&now.hour<InpSessionEndHour;
   return now.hour>=InpSessionStartHour||now.hour<InpSessionEndHour;
}
int Signal(MarketContext &m,const string strategy,string &why)
{
   int direction=0; why="No confirmed closed-bar entry";
   if(!m.valid) return 0;
   string use=strategy;
   if(use=="regime") use=(m.adx>=25 ? "trend" : (m.adx<20 ? "mean_reversion" : "breakout"));
   if(use=="ema_cross")
   {
      if(m.fast2<=m.slow2&&m.fast1>m.slow1) direction=1;
      if(m.fast2>=m.slow2&&m.fast1<m.slow1) direction=-1;
      why="The fast moving average crossed the slow moving average on a completed bar";
   }
   else if(use=="trend"||use=="session"||use=="multi_timeframe")
   {
      if(m.adx>=25&&m.fast1>m.slow1&&m.close1>m.fast1&&m.close2<=m.fast2) direction=1;
      if(m.adx>=25&&m.fast1<m.slow1&&m.close1<m.fast1&&m.close2>=m.fast2) direction=-1;
      if(use=="session"&&!SessionStrategyTime()) direction=0;
      if(use=="multi_timeframe"&&((direction==1&&m.higher_fast1<=m.higher_slow1)||(direction==-1&&m.higher_fast1>=m.higher_slow1))) direction=0;
      why="A completed-bar pullback resumed an ADX-confirmed trend";
   }
   else if(use=="momentum")
   {
      if(m.close1-m.close2>.5*m.atr&&m.rsi>=55&&m.rsi<70&&m.fast1>m.slow1) direction=1;
      if(m.close2-m.close1>.5*m.atr&&m.rsi<=45&&m.rsi>30&&m.fast1<m.slow1) direction=-1;
      why="Completed-bar momentum agrees with the moving-average direction";
   }
   else if(use=="breakout"||use=="volatility")
   {
      double buffer=(use=="volatility" ? .25*m.atr : .1*m.atr);
      if(m.close1>m.high+buffer) direction=1;
      if(m.close1<m.low-buffer) direction=-1;
      if(use=="volatility"&&MathAbs(m.close1-m.open1)<m.atr) direction=0;
      why="A completed bar closed outside the prior range with an ATR buffer";
   }
   else if(use=="mean_reversion")
   {
      if(m.adx<20&&m.rsi<30&&m.close1<m.slow1-1.5*m.atr&&m.close1>m.open1) direction=1;
      if(m.adx<20&&m.rsi>70&&m.close1>m.slow1+1.5*m.atr&&m.close1<m.open1) direction=-1;
      why="A quiet-regime overextension printed a completed-bar reversal";
   }
   else if(use=="support_resistance")
   {
      if(m.adx<25&&m.close1>m.low&&m.close1<m.low+.3*m.atr&&m.close1>m.open1) direction=1;
      if(m.adx<25&&m.close1<m.high&&m.close1>m.high-.3*m.atr&&m.close1<m.open1) direction=-1;
      why="A completed bar reacted near the prior support or resistance range";
   }
   if(strategy=="regime") why="Regime selector chose "+use+". "+why;
   return direction;
}
void RefreshNews(MarketContext &m)
{
   datetime now=UtcNow();
   if(!InpNewsGuard) { m.news_known=true; m.news_blocked=false; m.news_checked=now; return; }
   if(now-m.news_checked<30&&m.news_checked>0) return;
   m.news_checked=now; m.news_known=false; m.news_blocked=true;
   if(g_tester) return; // MT5 calendar is not supported in the Strategy Tester.
   string currencies[2]; currencies[0]=SymbolInfoString(m.symbol,SYMBOL_CURRENCY_BASE);
   currencies[1]=SymbolInfoString(m.symbol,SYMBOL_CURRENCY_PROFIT);
   if(currencies[0]==""||currencies[1]=="") return;
   datetime server_now=TimeTradeServer();
   bool blocked=false;
   for(int c=0;c<2;c++)
   {
      MqlCalendarValue values[]; ResetLastError();
      int count=CalendarValueHistory(values,server_now-InpNewsAfterMinutes*60,server_now+InpNewsBeforeMinutes*60,NULL,currencies[c]);
      if(count<0||GetLastError()!=0) return;
      for(int i=0;i<count;i++)
      {
         MqlCalendarEvent event;
         if(!CalendarEventById(values[i].event_id,event)) return;
         if(event.importance==CALENDAR_IMPORTANCE_HIGH) blocked=true;
      }
   }
   m.news_known=true; m.news_blocked=blocked;
}
bool AccountGate(string &why)
{
   if((long)AccountInfoInteger(ACCOUNT_LOGIN)!=g_account) { why="Account changed; reattach the EA"; return false; }
   if(!g_tester&&!AllowedAccount()) { why="Account is not explicitly authorized for this engine"; return false; }
   if(!TerminalInfoInteger(TERMINAL_CONNECTED)) { why="Broker disconnected"; return false; }
   if(!TerminalInfoInteger(TERMINAL_TRADE_ALLOWED)||!MQLInfoInteger(MQL_TRADE_ALLOWED)||
      !AccountInfoInteger(ACCOUNT_TRADE_ALLOWED)||!AccountInfoInteger(ACCOUNT_TRADE_EXPERT)) { why="Terminal or account trading permission is disabled"; return false; }
   if(g_emergency) { why="Emergency stop is latched"; return false; }
   if(g_uncertain) { why="Uncertain prior order requires reconciliation; no retry will be sent"; return false; }
   if(g_anchor_block) { why="Risk baseline reconciliation pending: "+g_anchor_reason; return false; }
   if(g_events_overflow) { why="Audit event storage failed"; return false; }
   if(g_cooldown>UtcNow()) { why="Consecutive-loss cooldown is active"; return false; }
   double equity=AccountInfoDouble(ACCOUNT_EQUITY),margin=AccountInfoDouble(ACCOUNT_MARGIN_FREE);
   if(!Finite(equity)||equity<=0||!Finite(margin)||margin<=0) { why="Equity or free-margin data is invalid"; return false; }
   if(g_day_equity<=0||g_week_equity<=0||g_high_water<=0) { why="Risk anchors are missing"; return false; }
   if(equity<=g_day_equity*(1-g_daily_pct/100)) { why="Daily equity-loss limit reached (includes floating P/L)"; return false; }
   if(equity<=g_week_equity*(1-g_weekly_pct/100)) { why="Weekly equity-loss limit reached (includes floating P/L)"; return false; }
   if(equity<=g_high_water*(1-g_drawdown_pct/100)) { why="Equity drawdown limit reached"; return false; }
   return true;
}
bool CurrentQuote(const string symbol,MqlTick &tick,string &why)
{
   if(!SymbolInfoTick(symbol,tick)||!Finite(tick.ask)||!Finite(tick.bid)||tick.bid<=0||tick.ask<tick.bid) { why="Invalid quote"; return false; }
   datetime age=TimeTradeServer()-tick.time;
   if(age>InpMaxTickAgeSeconds||age< -5) { why="Stale quote or inconsistent server clock"; return false; }
   double point=SymbolInfoDouble(symbol,SYMBOL_POINT);
   if(point<=0||(tick.ask-tick.bid)/point>InpMaxSpreadPoints) { why="Spread exceeds the approved limit"; return false; }
   if(!InSession(symbol)) { why="Instrument trading session closed or unavailable"; return false; }
   return true;
}
bool SharesCurrency(const string a,const string b)
{
   string abase=SymbolInfoString(a,SYMBOL_CURRENCY_BASE),aprofit=SymbolInfoString(a,SYMBOL_CURRENCY_PROFIT);
   string bbase=SymbolInfoString(b,SYMBOL_CURRENCY_BASE),bprofit=SymbolInfoString(b,SYMBOL_CURRENCY_PROFIT);
   if(abase==""||aprofit==""||bbase==""||bprofit=="") return true;
   return abase==bbase||abase==bprofit||aprofit==bbase||aprofit==bprofit;
}
bool ExposureRisk(const string proposed,double &risk,string &why)
{
   risk=0; int count=PositionsTotal()+OrdersTotal();
   if(count>=g_max_positions) { why="Account-wide position and pending-order limit reached"; return false; }
   for(int i=PositionsTotal()-1;i>=0;i--)
   {
      ulong ticket=PositionGetTicket(i); if(ticket==0) { why="Position reconciliation failed"; return false; }
      string symbol=PositionGetString(POSITION_SYMBOL); double stop=PositionGetDouble(POSITION_SL);
      double volume=PositionGetDouble(POSITION_VOLUME); long side=PositionGetInteger(POSITION_TYPE);
      if(symbol==proposed||SharesCurrency(proposed,symbol)) { why="Existing exposure shares an instrument or currency"; return false; }
      MqlTick quote; if(!SymbolInfoTick(symbol,quote)||stop<=0||volume<=0) { why="Existing position has unknown risk or no protective stop"; return false; }
      double mark=(side==POSITION_TYPE_BUY ? quote.bid : quote.ask),pnl=0;
      if(!OrderCalcProfit(side==POSITION_TYPE_BUY?ORDER_TYPE_BUY:ORDER_TYPE_SELL,symbol,volume,mark,stop,pnl)||!Finite(pnl)) { why="Existing position risk cannot be valued in account currency"; return false; }
      risk+=MathMax(0,-pnl);
   }
   for(int i=OrdersTotal()-1;i>=0;i--)
   {
      ulong ticket=OrderGetTicket(i); if(ticket==0) { why="Pending-order reconciliation failed"; return false; }
      string symbol=OrderGetString(ORDER_SYMBOL); double stop=OrderGetDouble(ORDER_SL);
      double entry=OrderGetDouble(ORDER_PRICE_OPEN),volume=OrderGetDouble(ORDER_VOLUME_CURRENT);
      ENUM_ORDER_TYPE type=(ENUM_ORDER_TYPE)OrderGetInteger(ORDER_TYPE);
      bool buy=type==ORDER_TYPE_BUY_LIMIT||type==ORDER_TYPE_BUY_STOP||type==ORDER_TYPE_BUY_STOP_LIMIT;
      if(symbol==proposed||SharesCurrency(proposed,symbol)) { why="Pending exposure shares an instrument or currency"; return false; }
      if(stop<=0||entry<=0||volume<=0||(buy&&stop>=entry)||(!buy&&stop<=entry)) { why="Pending order has unknown or unprotected risk"; return false; }
      double pnl=0;
      if(!OrderCalcProfit(buy?ORDER_TYPE_BUY:ORDER_TYPE_SELL,symbol,volume,entry,stop,pnl)||!Finite(pnl)) { why="Pending order risk cannot be valued"; return false; }
      risk+=MathMax(0,-pnl);
   }
   return Finite(risk);
}
double RoundPrice(const string symbol,const double price,const bool down)
{
   double step=SymbolInfoDouble(symbol,SYMBOL_TRADE_TICK_SIZE);
   if(step<=0) return 0;
   int digits=(int)SymbolInfoInteger(symbol,SYMBOL_DIGITS);
   return NormalizeDouble((down?MathFloor(price/step):MathCeil(price/step))*step,digits);
}
double FloorVolume(const string symbol,const double requested)
{
   double minimum=SymbolInfoDouble(symbol,SYMBOL_VOLUME_MIN),maximum=SymbolInfoDouble(symbol,SYMBOL_VOLUME_MAX),step=SymbolInfoDouble(symbol,SYMBOL_VOLUME_STEP);
   if(minimum<=0||maximum<minimum||step<=0||!Finite(requested)||requested<minimum) return 0;
   double volume=MathFloor((MathMin(requested,maximum)+step*1e-8)/step)*step;
   volume=NormalizeDouble(volume,8);
   if(volume<minimum||volume>maximum) return 0;
   return volume;
}
bool Filling(const string symbol,ENUM_ORDER_TYPE_FILLING &fill)
{
   long modes=SymbolInfoInteger(symbol,SYMBOL_FILLING_MODE);
   if((modes&SYMBOL_FILLING_FOK)!=0) { fill=ORDER_FILLING_FOK; return true; }
   if((modes&SYMBOL_FILLING_IOC)!=0) { fill=ORDER_FILLING_IOC; return true; }
   if(SymbolInfoInteger(symbol,SYMBOL_TRADE_EXEMODE)!=SYMBOL_TRADE_EXECUTION_MARKET) { fill=ORDER_FILLING_RETURN; return true; }
   return false;
}
bool StopsValid(const string symbol,const bool buy,const double sl,const double tp,const MqlTick &tick,const bool modification=false)
{
   double point=SymbolInfoDouble(symbol,SYMBOL_POINT),step=SymbolInfoDouble(symbol,SYMBOL_TRADE_TICK_SIZE);
   long points=SymbolInfoInteger(symbol,SYMBOL_TRADE_STOPS_LEVEL);
   if(modification) points=(long)MathMax((double)points,(double)SymbolInfoInteger(symbol,SYMBOL_TRADE_FREEZE_LEVEL));
   double distance=points*point+step;
   if(point<=0||step<=0||!Finite(sl)||!Finite(tp)||sl<=0||tp<=0) return false;
   if(buy) return sl<tick.bid-distance&&tp>tick.ask+distance;
   return sl>tick.ask+distance&&tp<tick.bid-distance;
}
bool ApprovedRequest(MarketContext &m,const int direction,MqlTradeRequest &request,string &why)
{
   ulong start=GetMicrosecondCount(); ZeroMemory(request);
   if(!AccountGate(why)) { g_risk_us=GetMicrosecondCount()-start; return false; }
   if(!m.news_known||UtcNow()-m.news_checked>90||m.news_blocked) { why="Economic-event data unavailable or high-impact event window active"; g_risk_us=GetMicrosecondCount()-start; return false; }
   MqlTick tick;
   if(!CurrentQuote(m.symbol,tick,why)) { g_risk_us=GetMicrosecondCount()-start; return false; }
   long mode=SymbolInfoInteger(m.symbol,SYMBOL_TRADE_MODE),orders=SymbolInfoInteger(m.symbol,SYMBOL_ORDER_MODE);
   if(mode==SYMBOL_TRADE_MODE_DISABLED||mode==SYMBOL_TRADE_MODE_CLOSEONLY||(direction==1&&mode==SYMBOL_TRADE_MODE_SHORTONLY)||(direction==-1&&mode==SYMBOL_TRADE_MODE_LONGONLY)||
      (orders&SYMBOL_ORDER_MARKET)==0||(orders&SYMBOL_ORDER_SL)==0||(orders&SYMBOL_ORDER_TP)==0) { why="Broker instrument permissions do not allow this protected market order"; return false; }
   if((tick.ask-tick.bid)>m.atr*.2) { why="Spread is too large relative to recent volatility"; return false; }
   double existing=0;
   if(!ExposureRisk(m.symbol,existing,why)) return false;
   bool buy=direction==1; double price=buy?tick.ask:tick.bid;
   double point=SymbolInfoDouble(m.symbol,SYMBOL_POINT);
   double minimum=(SymbolInfoInteger(m.symbol,SYMBOL_TRADE_STOPS_LEVEL)+2)*point+(tick.ask-tick.bid);
   double distance=MathMax(m.atr*InpStopATR,minimum);
   double sl=RoundPrice(m.symbol,price+(buy?-distance:distance),buy);
   double tp=RoundPrice(m.symbol,price+(buy?distance*InpRewardRisk:-distance*InpRewardRisk),!buy);
   if(!StopsValid(m.symbol,buy,sl,tp,tick)) { why="Protective stop or target is invalid"; return false; }
   double equity=AccountInfoDouble(ACCOUNT_EQUITY),one_lot=0;
   // Reserve an additional spread plus configured deviation against the per-trade budget.
   double adverse=price+(buy?1:-1)*(tick.ask-tick.bid+InpMaxDeviationPoints*point);
   ENUM_ORDER_TYPE type=buy?ORDER_TYPE_BUY:ORDER_TYPE_SELL;
   if(!OrderCalcProfit(type,m.symbol,1.0,adverse,sl,one_lot)||!Finite(one_lot)||one_lot>=0) { why="Stop risk calculation failed"; return false; }
   double available=MathMin(equity*g_risk_pct/100,equity-g_day_equity*(1-g_daily_pct/100));
   available=MathMin(available,equity-g_week_equity*(1-g_weekly_pct/100));
   available=MathMin(available,equity-g_high_water*(1-g_drawdown_pct/100));
   // Aggregate protected-loss budget is at most 0.5% equity and at most positions * trade cap.
   double aggregate=equity*MathMin(.5,g_max_positions*g_risk_pct)/100;
   available=MathMin(available,aggregate-existing);
   available=MathMin(available,equity-g_day_equity*(1-g_daily_pct/100)-existing);
   available=MathMin(available,equity-g_week_equity*(1-g_weekly_pct/100)-existing);
   available=MathMin(available,equity-g_high_water*(1-g_drawdown_pct/100)-existing);
   if(!Finite(available)||available<=0) { why="No remaining account risk budget"; return false; }
   double volume=FloorVolume(m.symbol,available/(-one_lot));
   if(volume<=0) { why="Minimum lot would exceed approved risk"; return false; }
   double margin=0;
   if(!OrderCalcMargin(type,m.symbol,volume,price,margin)||!Finite(margin)||margin<=0||margin>AccountInfoDouble(ACCOUNT_MARGIN_FREE)*.9) { why="Order margin unavailable or insufficient reserve"; return false; }
   request.action=TRADE_ACTION_DEAL; request.magic=(ulong)InpMagic; request.symbol=m.symbol;
   request.type=type; request.volume=volume; request.price=price; request.sl=sl; request.tp=tp;
   request.deviation=(ulong)InpMaxDeviationPoints; request.comment="XMU:"+g_strategy;
   if(!Filling(m.symbol,request.type_filling)) { why="No supported filling policy"; return false; }
   MqlTradeCheckResult check; ZeroMemory(check);
   if(!OrderCheck(request,check)||check.retcode!=TRADE_RETCODE_DONE) { why="Broker preflight rejected order: "+Safe(check.comment); return false; }
   g_risk_us=GetMicrosecondCount()-start; return true;
}
bool AcceptedRetcode(const uint code)
{
   return code==TRADE_RETCODE_DONE||code==TRADE_RETCODE_DONE_PARTIAL||code==TRADE_RETCODE_PLACED;
}
bool UncertainRetcode(const uint code)
{
   return code==TRADE_RETCODE_TIMEOUT||code==TRADE_RETCODE_CONNECTION||code==TRADE_RETCODE_ERROR||code==0;
}
bool Submit(MqlTradeRequest &request,const string operation)
{
   MqlTradeResult result; ZeroMemory(result);
   ulong before=GetMicrosecondCount(); bool sent=OrderSend(request,result);
   g_submission_us=GetMicrosecondCount()-before;
   // Synchronous OrderSend measures client call/response round-trip, not exchange matching latency.
   g_broker_us=g_submission_us;
   QueueEvent("order_submitted",operation+"; broker response "+IntegerToString((int)result.retcode)+"; "+Safe(result.comment),request.symbol,result.order,
              request.type==ORDER_TYPE_BUY?"buy":"sell",request.volume,request.price);
   if(sent&&AcceptedRetcode(result.retcode))
   {
      if(result.retcode==TRADE_RETCODE_DONE_PARTIAL) QueueEvent("partial_execution","Broker reported partial execution; actual deals and positions are reconciled",request.symbol,result.order);
      // The actual fill is recorded only by OnTradeTransaction/history reconciliation.
      if(result.order>0) g_pending_order=result.order;
      if(result.deal>0) g_pending_deal=result.deal;
      g_pending_at=UtcNow();
      return true;
   }
   QueueEvent("order_rejected",operation+" was not confirmed: "+Safe(result.comment),request.symbol,result.order);
   if(UncertainRetcode(result.retcode))
   {
      g_uncertain=true; g_running_requested=false;
      g_pending_order=result.order; g_pending_deal=result.deal; g_pending_at=UtcNow();
      QueueEvent("critical_error","Uncertain order outcome latched; no automatic retry",request.symbol,result.order);
   }
   return false;
}
void Evaluate(MarketContext &m)
{
   datetime bar=iTime(m.symbol,InpTimeframe,0); if(bar==0||m.last_evaluation==UtcNow()) return;
   m.last_evaluation=UtcNow();
   bool refresh=(bar!=m.last_bar||!m.valid); m.last_bar=bar;
   ulong before=GetMicrosecondCount();
   if(refresh&&!RefreshIndicators(m)) { g_reason="Waiting for complete indicator history"; return; }
   string why; int direction=Signal(m,g_strategy,why); m.explanation=why;
   g_decision_us=GetMicrosecondCount()-before;
   if(direction==0||m.attempted_bar==bar) return;
   // Re-evaluate conditions without retrying an order already submitted this candle.
   MqlTradeRequest request;
   if(!ApprovedRequest(m,direction,request,why)) { if(g_reason!=why) QueueEvent("risk_rejected",why,m.symbol); g_reason=why; return; }
   if(!g_running_requested||g_emergency||(!g_tester&&UtcNow()>=g_lease)) return;
   m.attempted_bar=bar;
   QueueEvent("strategy_signal",why,m.symbol,0,direction==1?"buy":"sell");
   Submit(request,"Protected account-authorized entry");
}
bool ModifyStop(const ulong ticket,const string symbol,const bool buy,const double sl,const double tp)
{
   MqlTick tick; if(!SymbolInfoTick(symbol,tick)||!StopsValid(symbol,buy,sl,tp,tick,true)) return false;
   MqlTradeRequest request; ZeroMemory(request); request.action=TRADE_ACTION_SLTP;
   request.position=ticket; request.symbol=symbol; request.magic=(ulong)InpMagic; request.sl=sl; request.tp=tp;
   MqlTradeCheckResult check; ZeroMemory(check);
   if(!OrderCheck(request,check)||check.retcode!=TRADE_RETCODE_DONE) return false;
   MqlTradeResult result; ZeroMemory(result);
   bool sent=OrderSend(request,result);
   if(!sent||!AcceptedRetcode(result.retcode)) { QueueEvent("stop_modify_rejected",Safe(result.comment),symbol,ticket); return false; }
   QueueEvent("stop_adjusted","Protective stop modification acknowledged; broker state remains authoritative",symbol,ticket,"",0,sl);
   return true;
}
bool ClosePosition(const ulong ticket,const double requested,const string reason)
{
   if(!PositionSelectByTicket(ticket)||PositionGetInteger(POSITION_MAGIC)!=InpMagic) return false;
   string symbol=PositionGetString(POSITION_SYMBOL); bool buy=PositionGetInteger(POSITION_TYPE)==POSITION_TYPE_BUY;
   MqlTick quote; if(!SymbolInfoTick(symbol,quote)) return false;
   double volume=FloorVolume(symbol,MathMin(requested,PositionGetDouble(POSITION_VOLUME))); if(volume<=0) return false;
   MqlTradeRequest request; ZeroMemory(request); request.action=TRADE_ACTION_DEAL;
   request.position=ticket; request.symbol=symbol; request.magic=(ulong)InpMagic;
   request.type=buy?ORDER_TYPE_SELL:ORDER_TYPE_BUY; request.volume=volume; request.price=buy?quote.bid:quote.ask;
   request.deviation=(ulong)InpMaxDeviationPoints; request.comment="XMU exit";
   if(!Filling(symbol,request.type_filling)) return false;
   MqlTradeCheckResult check; ZeroMemory(check);
   if(!OrderCheck(request,check)||check.retcode!=TRADE_RETCODE_DONE) { QueueEvent("exit_rejected",Safe(check.comment),symbol,ticket); return false; }
   return Submit(request,reason);
}
int MarketIndex(const string symbol)
{
   for(int i=0;i<ArraySize(g_markets);i++) if(g_markets[i].symbol==symbol) return i;
   return -1;
}
void ManagePositions()
{
   if(!TerminalInfoInteger(TERMINAL_CONNECTED)||!MQLInfoInteger(MQL_TRADE_ALLOWED)||
      (!g_tester&&!AllowedAccount())||AccountInfoInteger(ACCOUNT_LOGIN)!=g_account) return;
   for(int i=PositionsTotal()-1;i>=0;i--)
   {
      ulong ticket=PositionGetTicket(i); if(ticket==0||PositionGetInteger(POSITION_MAGIC)!=InpMagic) continue;
      string symbol=PositionGetString(POSITION_SYMBOL); int index=MarketIndex(symbol);
      if(index<0||!g_markets[index].valid) continue;
      bool buy=PositionGetInteger(POSITION_TYPE)==POSITION_TYPE_BUY;
      double entry=PositionGetDouble(POSITION_PRICE_OPEN),sl=PositionGetDouble(POSITION_SL),tp=PositionGetDouble(POSITION_TP);
      double volume=PositionGetDouble(POSITION_VOLUME),atr=g_markets[index].atr;
      MqlTick quote; if(!SymbolInfoTick(symbol,quote)||TimeTradeServer()-quote.time>InpMaxTickAgeSeconds) continue;
      if(sl<=0) { g_running_requested=false; g_reason="An owned position has no stop; explicit review is required"; QueueEvent("critical_error",g_reason,symbol,ticket); continue; }
      datetime opened=(datetime)PositionGetInteger(POSITION_TIME);
      if(InpMaxHoldingBars>0&&TimeTradeServer()-opened>InpMaxHoldingBars*PeriodSeconds(InpTimeframe))
      { ClosePosition(ticket,volume,"Maximum holding time exit"); continue; }
      string why; int opposite=Signal(g_markets[index],g_strategy,why);
      if((buy&&opposite==-1)||(!buy&&opposite==1)) { ClosePosition(ticket,volume,"Strategy invalidation exit"); continue; }
      double mark=buy?quote.bid:quote.ask,move=(buy?mark-entry:entry-mark),candidate=sl;
      double point=SymbolInfoDouble(symbol,SYMBOL_POINT);
      if(InpBreakEven&&move>=InpStopATR*atr)
      {
         double safe_entry=entry+(buy?1:-1)*(quote.ask-quote.bid+InpMaxDeviationPoints*point);
         if(buy) candidate=MathMax(candidate,safe_entry); else candidate=MathMin(candidate,safe_entry);
      }
      if(InpTrailing&&move>=InpStopATR*atr)
      {
         double trailing=mark+(buy?-1:1)*InpStopATR*atr;
         if(buy) candidate=MathMax(candidate,trailing); else candidate=MathMin(candidate,trailing);
      }
      candidate=RoundPrice(symbol,candidate,buy);
      if((buy&&candidate>sl+2*point)||(!buy&&candidate<sl-2*point)) ModifyStop(ticket,symbol,buy,candidate,tp);
      // Optional one-off partial close is persisted and restricted to hedging accounts.
      if(InpPartialClose&&AccountInfoInteger(ACCOUNT_MARGIN_MODE)==ACCOUNT_MARGIN_MODE_RETAIL_HEDGING&&move>=InpStopATR*atr)
      {
         string marker=Key("partial."+U(ticket));
         if(!GlobalVariableCheck(marker))
         {
            double half=FloorVolume(symbol,volume/2),minimum=SymbolInfoDouble(symbol,SYMBOL_VOLUME_MIN);
            if(half>=minimum&&volume-half>=minimum)
            {
               GlobalVariableSet(marker,1); // At-most-once even if the broker result is uncertain.
               ClosePosition(ticket,half,"Optional one-off partial demo exit");
            }
         }
      }
   }
}
void LiquidateOwned()
{
   if(!g_tester&&!AllowedAccount()) return;
   g_running_requested=false;
   for(int i=OrdersTotal()-1;i>=0;i--)
   {
      ulong ticket=OrderGetTicket(i); if(ticket==0||OrderGetInteger(ORDER_MAGIC)!=InpMagic) continue;
      MqlTradeRequest request; ZeroMemory(request); request.action=TRADE_ACTION_REMOVE; request.order=ticket;
      request.symbol=OrderGetString(ORDER_SYMBOL); request.magic=(ulong)InpMagic;
      Submit(request,"Explicit removal of bot-owned pending order");
   }
   for(int i=PositionsTotal()-1;i>=0;i--)
   {
      ulong ticket=PositionGetTicket(i); if(ticket!=0&&PositionGetInteger(POSITION_MAGIC)==InpMagic)
         ClosePosition(ticket,PositionGetDouble(POSITION_VOLUME),"Explicit demo position liquidation");
   }
   QueueEvent("liquidation_requested","Explicit liquidation requests submitted; positions are reconciled before completion is claimed");
}
bool ReadSecret()
{
   int file=FileOpen(Path("bridge.secret"),FILE_READ|FILE_TXT|FILE_ANSI|g_common,0,CP_UTF8);
   if(file==INVALID_HANDLE) return false;
   g_secret=FileReadString(file); FileClose(file); StringTrimLeft(g_secret); StringTrimRight(g_secret);
   return StringLen(g_secret)>=32&&StringLen(g_secret)<=256&&StringFind(g_secret,",")<0;
}
bool ValidNumberText(const string s)
{
   if(StringLen(s)==0) return false;
   int dots=0;
   for(int i=0;i<StringLen(s);i++)
   {
      ushort c=StringGetCharacter(s,i);
      if(c==46) { dots++; if(dots>1) return false; }
      else if(c<48||c>57) return false;
   }
   double value=StringToDouble(s); return Finite(value)&&value>0;
}
void Ack(const string id,const bool accepted,const string message)
{
   int file;
   if(!OpenSnapshot("ack.csv",file)) { g_running_requested=false; g_reason="Cannot persist command acknowledgment"; return; }
   FileWrite(file,"command_id","timestamp","accepted","state","message");
   FileWrite(file,id,L((long)UtcNow()),accepted?"true":"false",g_state,Safe(message));
   if(!CommitSnapshot("ack.csv",file)) { g_running_requested=false; g_reason="Cannot commit command acknowledgment"; }
}
void RememberCommand(const string command)
{
   g_last_command=command;
   int file;
   if(OpenSnapshot("last_command.csv",file))
   {
      FileWrite(file,"command_id"); FileWrite(file,command); CommitSnapshot("last_command.csv",file);
   }
}
bool ParseControl(string &fields[])
{
   int file=FileOpen(Path("control.csv"),FILE_READ|FILE_CSV|FILE_ANSI|g_common|FILE_SHARE_READ,',',CP_UTF8);
   if(file==INVALID_HANDLE) return false;
   string expected[]={"version","command_id","issued_at","expires_at","action","secret","risk_per_trade_pct","daily_loss_pct","weekly_loss_pct","max_drawdown_pct","max_positions","max_consecutive_losses","strategy"};
   bool okay=true;
   for(int i=0;i<13;i++) if(FileReadString(file)!=expected[i]) okay=false;
   ArrayResize(fields,13);
   for(int i=0;i<13;i++) { if(FileIsEnding(file)) okay=false; fields[i]=FileReadString(file); }
   // No trailing nonempty records are allowed.
   while(!FileIsEnding(file)) if(FileReadString(file)!="") okay=false;
   FileClose(file); return okay;
}
void ProcessControl()
{
   if(g_tester) return;
   string f[]; if(!ParseControl(f)) return;
   if(f[0]!="1"||f[1]==""||f[1]==g_last_command||f[5]!=g_secret) return;
   if(StringLen(f[1])>128||StringFind(f[1],"\n")>=0||!ValidNumberText(f[2])||!ValidNumberText(f[3])) return;
   datetime now=UtcNow(),issued=(datetime)StringToInteger(f[2]),expires=(datetime)StringToInteger(f[3]);
   if(issued>now+5||issued<now-30||expires<=now||expires>issued+30||expires<=issued) { Ack(f[1],false,"Expired or invalid command lease"); return; }
   string action=f[4];
   if(action!="start"&&action!="pause"&&action!="resume"&&action!="emergency_stop"&&action!="liquidate"&&action!="heartbeat"&&action!="configure") { Ack(f[1],false,"Unknown action"); return; }
   bool limits=true; for(int j=6;j<=11;j++) if(!ValidNumberText(f[j])) limits=false;
   double risk=StringToDouble(f[6]),daily=StringToDouble(f[7]),weekly=StringToDouble(f[8]),draw=StringToDouble(f[9]);
   int positions=(int)StringToInteger(f[10]),losses=(int)StringToInteger(f[11]);
   if(risk>.25||daily>1||weekly>3||draw>5||positions<1||positions>2||losses<1||losses>3||
      StringToDouble(f[10])!=positions||StringToDouble(f[11])!=losses||!ValidStrategy(f[12])) limits=false;
   // Stopping is always honored after authentication and freshness, even if other configuration is invalid.
   if(action=="pause"||action=="emergency_stop")
   {
      g_running_requested=false; g_lease=0;
      if(action=="emergency_stop") { g_emergency=true; g_state="emergency"; g_reason="Emergency stop: new entries disabled; existing protective stops remain"; }
      else { g_state=g_emergency?"emergency":"paused"; g_reason="New entries paused; existing positions remain managed"; }
      RememberCommand(f[1]); Persist(); Ack(f[1],true,g_reason); QueueEvent(action,g_reason); return;
   }
   if(!limits) { RememberCommand(f[1]); Ack(f[1],false,"Risk settings or strategy outside enforced demo boundaries"); return; }
   if(action=="configure"&&g_running_requested) { RememberCommand(f[1]); Ack(f[1],false,"Pause before changing configuration"); return; }
   if(g_running_requested&&(risk!=g_risk_pct||daily!=g_daily_pct||weekly!=g_weekly_pct||draw!=g_drawdown_pct||positions!=g_max_positions||losses!=g_max_losses||f[12]!=g_strategy))
   { RememberCommand(f[1]); Ack(f[1],false,"Running configuration may not be changed by heartbeat"); return; }
   g_risk_pct=risk; g_daily_pct=daily; g_weekly_pct=weekly; g_drawdown_pct=draw;
   g_max_positions=positions; g_max_losses=losses; g_strategy=f[12];
   bool accepted=true; string message="Configuration accepted";
   if(action=="heartbeat")
   {
      if(g_running_requested&&!g_emergency) { g_lease=expires; message="Control lease renewed"; }
      else { accepted=false; message="Heartbeat cannot start or resume trading"; }
   }
   else if(action=="start"||action=="resume")
   {
      bool previous=g_emergency;
      // Only a deliberate resume clears a latched emergency; start cannot clear it.
      if(action=="resume") g_emergency=false;
      string why;
      if(!AccountGate(why)) { accepted=false; g_emergency=previous; message=why; }
      else
      {
         bool prepared=true;
         for(int i=0;i<ArraySize(g_markets);i++)
         {
            if(!RefreshIndicators(g_markets[i])) prepared=false;
            RefreshNews(g_markets[i]);
            if(!g_markets[i].news_known) prepared=false;
         }
         if(!prepared) { accepted=false; g_emergency=previous; message="History or required news source is not ready"; }
         else { g_running_requested=true; g_state="running"; g_lease=expires; message="Automatic operation resumed; every trade still needs independent risk approval"; }
      }
   }
   else if(action=="liquidate")
   {
      g_running_requested=false; g_lease=0;
      if(!TerminalInfoInteger(TERMINAL_CONNECTED)||!AllowedAccount()) { accepted=false; message="Demo liquidation unavailable while disconnected or on a real account"; }
      else { LiquidateOwned(); message="Demo liquidation requested; check reconciled position results"; }
   }
   RememberCommand(f[1]); Persist();
   if(accepted) { g_reason=message; QueueEvent("control_"+action,message); }
   Ack(f[1],accepted,message);
}
void FlushEvents()
{
   if(g_tester||ArraySize(g_events)==0) return;
   int file=FileOpen(Path("events.csv"),FILE_READ|FILE_WRITE|FILE_CSV|FILE_ANSI|g_common|FILE_SHARE_READ,',',CP_UTF8);
   if(file==INVALID_HANDLE) { g_running_requested=false; g_reason="Cannot write audit events"; return; }
   if(FileSize(file)==0) FileWrite(file,"event_id","timestamp","kind","symbol","ticket","side","volume","price","profit","commission","swap","strategy","message");
   FileSeek(file,0,SEEK_END);
   for(int i=0;i<ArraySize(g_events);i++)
      FileWrite(file,g_events[i].id,L((long)g_events[i].at),g_events[i].kind,g_events[i].symbol,U(g_events[i].ticket),
                g_events[i].side,N(g_events[i].volume),N(g_events[i].price),N(g_events[i].profit),N(g_events[i].commission),
                N(g_events[i].swap),g_events[i].strategy,g_events[i].message);
   FileFlush(file); FileClose(file); ArrayResize(g_events,0);
}
void WriteAccount()
{
   int file; if(!OpenSnapshot("account.csv",file)) return;
   FileWrite(file,"version","timestamp","account_id","broker","mode","currency","balance","equity","margin","free_margin","floating_pnl","daily_pnl","weekly_pnl","robot_state","reason","heartbeat_seq","decision_us","risk_us","submission_us","broker_response_us");
   double equity=AccountInfoDouble(ACCOUNT_EQUITY);
   FileWrite(file,"1",L((long)UtcNow()),AccountIdentity(),Safe(AccountInfoString(ACCOUNT_COMPANY)),AccountInfoInteger(ACCOUNT_TRADE_MODE)==ACCOUNT_TRADE_MODE_DEMO?"demo":"live",AccountInfoString(ACCOUNT_CURRENCY),
             N(AccountInfoDouble(ACCOUNT_BALANCE)),N(equity),N(AccountInfoDouble(ACCOUNT_MARGIN)),N(AccountInfoDouble(ACCOUNT_MARGIN_FREE)),
             N(AccountInfoDouble(ACCOUNT_PROFIT)),N(equity-g_day_equity),N(equity-g_week_equity),g_state,Safe(g_reason),U(++g_heartbeat),
             U(g_decision_us),U(g_risk_us),U(g_submission_us),U(g_broker_us));
   CommitSnapshot("account.csv",file);
}
void WritePositions()
{
   int file; if(!OpenSnapshot("positions.csv",file)) return;
   FileWrite(file,"ticket","symbol","side","volume","entry_price","sl","tp","profit","opened_at","strategy");
   long server_offset=(long)TimeTradeServer()-(long)UtcNow();
   for(int i=0;i<PositionsTotal();i++)
   {
      ulong ticket=PositionGetTicket(i); if(ticket==0) continue;
      bool own=PositionGetInteger(POSITION_MAGIC)==InpMagic;
      string comment=PositionGetString(POSITION_COMMENT),strategy="external";
      if(own) strategy=(StringFind(comment,"XMU:")==0 ? StringSubstr(comment,4) : "owned_unknown");
      FileWrite(file,U(ticket),PositionGetString(POSITION_SYMBOL),PositionGetInteger(POSITION_TYPE)==POSITION_TYPE_BUY?"buy":"sell",
                N(PositionGetDouble(POSITION_VOLUME)),N(PositionGetDouble(POSITION_PRICE_OPEN)),N(PositionGetDouble(POSITION_SL)),
                N(PositionGetDouble(POSITION_TP)),N(PositionGetDouble(POSITION_PROFIT)),
                L(PositionGetInteger(POSITION_TIME)-server_offset),strategy);
   }
   CommitSnapshot("positions.csv",file);
}
void WriteSymbols()
{
   int file; if(!OpenSnapshot("symbols.csv",file)) return;
   FileWrite(file,"symbol","selected","trade_mode","volume_min","volume_max","volume_step","tick_size","tick_value","contract_size","stops_level","spread_points");
   int total=SymbolsTotal(false);
   for(int i=0;i<total;i++)
   {
      string symbol=SymbolName(i,false); if(symbol=="") continue;
      FileWrite(file,symbol,MarketIndex(symbol)>=0?"true":"false",L(SymbolInfoInteger(symbol,SYMBOL_TRADE_MODE)),
                N(SymbolInfoDouble(symbol,SYMBOL_VOLUME_MIN)),N(SymbolInfoDouble(symbol,SYMBOL_VOLUME_MAX)),N(SymbolInfoDouble(symbol,SYMBOL_VOLUME_STEP)),
                N(SymbolInfoDouble(symbol,SYMBOL_TRADE_TICK_SIZE)),N(SymbolInfoDouble(symbol,SYMBOL_TRADE_TICK_VALUE)),
                N(SymbolInfoDouble(symbol,SYMBOL_TRADE_CONTRACT_SIZE)),L(SymbolInfoInteger(symbol,SYMBOL_TRADE_STOPS_LEVEL)),L(SymbolInfoInteger(symbol,SYMBOL_SPREAD)));
   }
   CommitSnapshot("symbols.csv",file);
}
void WriteScanner()
{
   int file; if(!OpenSnapshot("scanner.csv",file)) return;
   FileWrite(file,"account_id","timestamp","symbol","bid","ask");
   if(TerminalInfoInteger(TERMINAL_CONNECTED))
      for(int i=0;i<ArraySize(g_markets);i++)
      {
         MqlTick tick; string symbol=g_markets[i].symbol;
         if(!SymbolInfoTick(symbol,tick)||!Finite(tick.bid)||!Finite(tick.ask)||tick.bid<=0||tick.ask<tick.bid) continue;
         long age=(long)TimeTradeServer()-(long)tick.time;
         if(age< -5||age>InpMaxTickAgeSeconds) continue;
         FileWrite(file,AccountIdentity(),L((long)UtcNow()-(long)MathMax(0,age)),symbol,N(tick.bid),N(tick.ask));
      }
   CommitSnapshot("scanner.csv",file);
}
void RecordDeal(const ulong deal,const bool update_risk)
{
   if(!HistoryDealSelect(deal)) return;
   ENUM_DEAL_TYPE type=(ENUM_DEAL_TYPE)HistoryDealGetInteger(deal,DEAL_TYPE);
   double profit=HistoryDealGetDouble(deal,DEAL_PROFIT),commission=HistoryDealGetDouble(deal,DEAL_COMMISSION),swap=HistoryDealGetDouble(deal,DEAL_SWAP);
   bool own=HistoryDealGetInteger(deal,DEAL_MAGIC)==InpMagic;
   if(type!=DEAL_TYPE_BUY&&type!=DEAL_TYPE_SELL)
   {
      if(update_risk&&(type==DEAL_TYPE_BALANCE||type==DEAL_TYPE_CREDIT||type==DEAL_TYPE_CORRECTION||type==DEAL_TYPE_CHARGE))
      {
         double flow=profit+commission+swap;
         g_day_equity+=flow; g_week_equity+=flow; g_high_water+=flow;
         QueueEvent("cashflow","Account cash movement adjusted risk anchors","",deal,"",0,0,profit,commission,swap,"account","cash-"+L(g_account)+"-"+U(deal));
      }
      return;
   }
   ENUM_DEAL_ENTRY entry=(ENUM_DEAL_ENTRY)HistoryDealGetInteger(deal,DEAL_ENTRY);
   string kind=(entry==DEAL_ENTRY_IN ? "trade_opened" : "trade_closed");
   string comment=HistoryDealGetString(deal,DEAL_COMMENT),strategy="external";
   if(own) strategy=(StringFind(comment,"XMU:")==0 ? StringSubstr(comment,4) : "owned_unknown");
   string symbol=HistoryDealGetString(deal,DEAL_SYMBOL);
   ENUM_DEAL_REASON reason=(ENUM_DEAL_REASON)HistoryDealGetInteger(deal,DEAL_REASON);
   string message="Verified broker deal";
   if(reason==DEAL_REASON_SL) message="Verified broker stop-loss execution";
   if(reason==DEAL_REASON_TP) message="Verified broker take-profit execution";
   if(entry==DEAL_ENTRY_INOUT) message+="; netting reversal deal requires position-level accounting";
   QueueEvent(kind,message,symbol,deal,type==DEAL_TYPE_BUY?"buy":"sell",HistoryDealGetDouble(deal,DEAL_VOLUME),
              HistoryDealGetDouble(deal,DEAL_PRICE),profit,commission,swap,strategy,"deal-"+L(g_account)+"-"+U(deal));
   if(reason==DEAL_REASON_SL||reason==DEAL_REASON_TP) QueueEvent(reason==DEAL_REASON_SL?"stop_loss_reached":"take_profit_reached",message,symbol,deal,"",0,0,0,0,0,strategy,"exit-reason-"+L(g_account)+"-"+U(deal));
   if(update_risk&&own&&entry!=DEAL_ENTRY_IN)
   {
      if(profit+commission+swap<0) g_losses++; else if(profit+commission+swap>0) g_losses=0;
      if(g_losses>=g_max_losses) { g_cooldown=UtcNow()+InpCooldownMinutes*60; QueueEvent("risk_limit","Consecutive-loss cooldown started",symbol,deal); }
   }
   if(deal==g_pending_deal) { g_pending_deal=0; g_pending_order=0; g_pending_at=0; }
}
void ReconcileHistory()
{
   datetime server_now=TimeTradeServer();
   datetime since=(g_last_history==0 ? server_now-7*86400 : g_last_history-5);
   if(!HistorySelect(since,server_now+1)) { g_running_requested=false; g_reason="Broker deal history reconciliation failed"; return; }
   ulong deals[]; int count=HistoryDealsTotal(); ArrayResize(deals,count);
   for(int i=0;i<count;i++) deals[i]=HistoryDealGetTicket(i);
   // Stable deal IDs allow database deduplication of the overlap and restart replay.
   for(int i=0;i<count;i++) if(deals[i]>0) RecordDeal(deals[i],false);
   g_last_history=server_now;
   if(g_pending_order>0)
   {
      if(OrderSelect(g_pending_order)) return; // Still pending: reconciliation continues, never resubmit.
      if(HistoryOrderSelect(g_pending_order))
      {
         ENUM_ORDER_STATE state=(ENUM_ORDER_STATE)HistoryOrderGetInteger(g_pending_order,ORDER_STATE);
         if(state==ORDER_STATE_FILLED||state==ORDER_STATE_CANCELED||state==ORDER_STATE_REJECTED||state==ORDER_STATE_EXPIRED)
         { g_pending_order=0; g_pending_deal=0; g_pending_at=0; }
      }
   }
   if(g_pending_at>0&&UtcNow()-g_pending_at>30) { g_uncertain=true; g_running_requested=false; g_reason="Order outcome remains unresolved after reconciliation"; }
}
void RefreshState()
{
   if(!TerminalInfoInteger(TERMINAL_CONNECTED)) { g_state="disconnected"; g_reason="Broker connection unavailable"; return; }
   if(g_emergency) { g_state="emergency"; g_reason="Stopped by operator; press Start to deliberately resume"; return; }
   if(!g_running_requested) { g_state="paused"; return; }
   if(!g_tester&&UtcNow()>=g_lease)
   {
      g_running_requested=false; g_state="paused"; g_reason="Control lease expired; new entries paused";
      QueueEvent("connection_failure",g_reason); return;
   }
   string why;
   if(!AccountGate(why)) { g_state="blocked"; if(g_reason!=why) QueueEvent("risk_limit",why); g_reason=why; return; }
   g_state="running";
}
int OnInit()
{
   g_tester=(bool)MQLInfoInteger(MQL_TESTER); g_account=AccountInfoInteger(ACCOUNT_LOGIN);
   if(InpMagic<=0||InpFastEMA<2||InpSlowEMA<=InpFastEMA||InpSlowEMA>500||InpATRPeriod<2||InpLookback<5||InpLookback>500||
      InpStopATR<1||InpStopATR>10||InpRewardRisk<.5||InpRewardRisk>10||InpMaxSpreadPoints<1||InpMaxDeviationPoints<0||
      InpMaxTickAgeSeconds<1||InpMaxTickAgeSeconds>60||InpCooldownMinutes<1||InpMaxHoldingBars<1||
      InpNewsBeforeMinutes<0||InpNewsAfterMinutes<0||InpSessionStartHour<0||InpSessionStartHour>23||InpSessionEndHour<0||InpSessionEndHour>23||
      StringFind(InpBridgeFolder,"..")>=0||StringFind(InpBridgeFolder,"/")>=0||StringFind(InpBridgeFolder,"\\")>=0||InpBridgeFolder=="") return INIT_PARAMETERS_INCORRECT;
   if(!g_tester&&!AllowedAccount())
   { Print("Account not authorized: live requires exact local login/server opt-in."); return INIT_FAILED; }
   g_prefix="XMU."+L(g_account)+"."+IntegerToString((int)Hash(AccountInfoString(ACCOUNT_SERVER)))+"."+L(InpMagic)+".";
   if(!g_tester)
   {
      FolderCreate(InpBridgeFolder,FILE_COMMON);
      g_lock=FileOpen(Path("engine.lock"),FILE_READ|FILE_WRITE|FILE_BIN|FILE_COMMON);
      if(g_lock==INVALID_HANDLE) { Print("Another EA owns the bridge or the bridge directory is inaccessible."); return INIT_FAILED; }
      if(!ReadSecret()) { Print("Bridge secret is missing or invalid. Run local setup; never paste broker credentials."); return INIT_FAILED; }
      int prior=FileOpen(Path("last_command.csv"),FILE_READ|FILE_CSV|FILE_ANSI|FILE_COMMON,',',CP_UTF8);
      if(prior!=INVALID_HANDLE) { FileReadString(prior); g_last_command=FileReadString(prior); FileClose(prior); }
   }
   if(!LoadAnchors()) { Print("Risk state is missing or damaged; initialization refused."); return INIT_FAILED; }
   string symbols[]; int count=StringSplit(InpSymbols,',',symbols);
   if(InpMaxScanSymbols<1||InpMaxScanSymbols>12) return INIT_PARAMETERS_INCORRECT;
   if(InpSymbols=="")
   {
      count=1; ArrayResize(symbols,1); symbols[0]=_Symbol;
      if(InpScanMarketWatch&&!g_tester)
         for(int n=0;n<SymbolsTotal(true)&&count<InpMaxScanSymbols;n++)
         {
            string selected=SymbolName(n,true);
            if(selected==""||selected==_Symbol||SymbolInfoInteger(selected,SYMBOL_TRADE_MODE)==SYMBOL_TRADE_MODE_DISABLED) continue;
            ArrayResize(symbols,count+1); symbols[count++]=selected;
         }
   }
   if(count<1||count>12) return INIT_PARAMETERS_INCORRECT;
   ArrayResize(g_markets,count);
   for(int i=0;i<count;i++)
   {
      StringTrimLeft(symbols[i]); StringTrimRight(symbols[i]);
      for(int j=0;j<i;j++) if(symbols[j]==symbols[i]) return INIT_PARAMETERS_INCORRECT;
      if(symbols[i]==""||!InitializeMarket(symbols[i],g_markets[i])) { Print("Cannot initialize selected broker instrument: ",symbols[i]); return INIT_FAILED; }
   }
   if(g_tester)
   {
      if(!ValidStrategy(InpTesterStrategy)) return INIT_PARAMETERS_INCORRECT;
      g_strategy=InpTesterStrategy; g_running_requested=InpTesterAutoStart; g_state=g_running_requested?"running":"paused";
      g_reason="SIMULATION: Strategy Tester, no bridge telemetry or real account execution";
   }
   QueueEvent("engine_started","Demo EA initialized paused; actual broker positions will be reconciled");
   EventSetTimer(1); return INIT_SUCCEEDED;
}
void OnDeinit(const int reason)
{
   EventKillTimer(); g_running_requested=false; Persist();
   for(int i=0;i<ArraySize(g_markets);i++)
   {
      IndicatorRelease(g_markets[i].fast_handle); IndicatorRelease(g_markets[i].slow_handle);
      IndicatorRelease(g_markets[i].atr_handle); IndicatorRelease(g_markets[i].rsi_handle);
      IndicatorRelease(g_markets[i].adx_handle); IndicatorRelease(g_markets[i].higher_fast); IndicatorRelease(g_markets[i].higher_slow);
   }
   if(g_lock!=INVALID_HANDLE) FileClose(g_lock);
}
void OnTick()
{
   ulong before=GetMicrosecondCount();
   // No network calls or explicit file reads/writes in the tick path.
   if(g_running_requested&&!g_emergency&&(g_tester||UtcNow()<g_lease))
      for(int i=0;i<ArraySize(g_markets);i++) if(g_markets[i].symbol==_Symbol) Evaluate(g_markets[i]);
   g_tick_us=GetMicrosecondCount()-before;
}
void OnTimer()
{
   UpdateAnchors();
   static datetime last_reconcile=0;
   if(g_anchor_block&&UtcNow()-last_reconcile>=30) { last_reconcile=UtcNow(); ReconcileMissedBoundaries(); }
   for(int i=0;i<ArraySize(g_markets);i++) { RefreshNews(g_markets[i]); if(!g_markets[i].valid) RefreshIndicators(g_markets[i]); }
   ProcessControl(); RefreshState();
   if(g_running_requested&&g_state=="running") for(int i=0;i<ArraySize(g_markets);i++) Evaluate(g_markets[i]);
   ManagePositions();
   static datetime last_scan_report=0;
   if(!g_tester&&g_running_requested&&g_state=="running"&&UtcNow()-last_scan_report>=5)
   {
      int checked=0;
      for(int i=0;i<ArraySize(g_markets);i++) if(g_markets[i].last_evaluation>=UtcNow()-2) checked++;
      QueueEvent("scan_cycle","Scanner checked "+IntegerToString(checked)+" of "+IntegerToString(ArraySize(g_markets))+" selected instruments; strategy and risk approval remain required");
      last_scan_report=UtcNow();
   }
   static int cycle=0; cycle++;
   if(cycle%10==1) ReconcileHistory();
   if(!g_tester)
   {
      WriteAccount(); WritePositions(); WriteScanner(); FlushEvents();
      if(cycle%60==1) WriteSymbols();
      Persist();
   }
   else ArrayResize(g_events,0); // Simulator never contaminates actual-broker telemetry.
   g_last_timer=UtcNow();
}
void OnTradeTransaction(const MqlTradeTransaction &trans,const MqlTradeRequest &request,const MqlTradeResult &result)
{
   if(trans.type==TRADE_TRANSACTION_DEAL_ADD&&trans.deal>0) RecordDeal(trans.deal,true);
   if(trans.type==TRADE_TRANSACTION_ORDER_DELETE&&trans.order==g_pending_order) g_pending_at=UtcNow();
   // No assumption about transaction ordering; the next timer reconciles broker history and positions.
}
