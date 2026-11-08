package za.co.loopmanager;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native customer experience. Customer identity comes only from the signed enrollment. */
public class MainActivity extends AppCompatActivity {
    private static final int INK=za.co.bacy.ui.CustomerHome.INK,GREEN=za.co.bacy.ui.CustomerHome.BLUE,MUTED=za.co.bacy.ui.CustomerHome.MUTED,PAPER=za.co.bacy.ui.CustomerHome.PAPER,LIME=za.co.bacy.ui.CustomerHome.SOFT;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Store store;
    private Entitlement entitlement;
    private LinearLayout content;
    private TextView daysLabel,timerLabel,connection;
    private boolean destroyed,syncing,enrolling,bankOpened,pendingInbox,pendingPaymentCheck,pendingFreshLink;
    private String paymentOutcome;
    private String currentPage="Home";
    private int ticks;
    private boolean claiming,claimDialogOpen,supportSending;
    private String historyFilter="all";
    private AccountTiming.Stage renderedStage;
    private String status="needs_online_check";
    private String accountIssue;
    private final BroadcastReceiver updates=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            if(!LiveConnectionService.UPDATED.equals(intent.getAction()))return;
            try{entitlement=Entitlement.load(MainActivity.this,store);if(entitlement!=null&&intent.getBooleanExtra("account_verified",false))accountIssue=null;renderCurrent();}catch(Exception ignored){}
        }
    };
    private final Runnable ticker=new Runnable(){
        @Override public void run(){if(destroyed)return;if(entitlement!=null&&entitlement.stage(MainActivity.this)!=renderedStage)renderCurrent();else updateCountdown();if(++ticks%60==0)refresh();handler.postDelayed(this,1000);}
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new Store(this);pendingPaymentCheck=getIntent().getBooleanExtra("check_payment",false);pendingFreshLink=getIntent().getBooleanExtra("fresh_payment",false);paymentOutcome=getIntent().getStringExtra("payment_outcome");ManagedUpdates.schedule(this);ManagedUpdates.checkSoon(this);pendingInbox=getIntent().getBooleanExtra("open_messages",false);
        androidx.core.content.ContextCompat.registerReceiver(this,updates,new IntentFilter(LiveConnectionService.UPDATED),getPackageName()+".permission.INTERNAL",null,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        SyncJobService.schedule(this);
        try{
            entitlement=Entitlement.load(this,store);
            if(!store.get("device_token").isEmpty()){if(entitlement==null||!entitlement.data.optBoolean("released"))PolicyController.protectFinancedDevice(this);dashboard();refresh();}
            else if(!store.get("enrollment_token").isEmpty()&&PolicyController.owns(this))connectEnrollment();
            else setupScreen(null,false);
        }catch(Exception failure){setupScreen("Your administrator needs to check this phone's enrollment.",false);}
        handler.post(ticker);
        showStartupPayment();
    }
    @Override protected void onResume(){
        super.onResume();if(store==null)return;if(SupportAccess.active(this)){SupportAccess.end(this);try{if(entitlement!=null)PolicyController.apply(this,entitlement);}catch(Exception ignored){}}ManagedUpdates.checkSoon(this);if(getIntent().getBooleanExtra("open_updates",false)){getIntent().removeExtra("open_updates");handler.post(this::appUpdates);}
        try{if(!store.get("device_token").isEmpty()){LiveConnectionService.start(this);refresh();}}
        catch(Exception ignored){}
        if(pendingFreshLink){pendingFreshLink=false;requestFreshLink();}else if(pendingPaymentCheck){pendingPaymentCheck=false;onlinePayment(true);}
        if(bankOpened){bankOpened=false;if(entitlement==null||entitlement.data.optBoolean("released"))return;AccountSheets.confirm(this,"Made your EFT?","Send an EFT claim only after paying. Your administrator must confirm the bank funds before paid time changes.","Send an EFT claim",this::claim);}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);pendingPaymentCheck=intent.getBooleanExtra("check_payment",false);pendingFreshLink=intent.getBooleanExtra("fresh_payment",false);paymentOutcome=intent.getStringExtra("payment_outcome");pendingInbox=intent.getBooleanExtra("open_messages",false);if(intent.getBooleanExtra("open_updates",false)){intent.removeExtra("open_updates");handler.post(this::appUpdates);}try{if(store.get("device_token").isEmpty()&&!store.get("enrollment_token").isEmpty()&&PolicyController.owns(this)){connectEnrollment();return;}}catch(Exception ignored){}refresh();showStartupPayment();}
    private void showStartupPayment(){if(getIntent().getBooleanExtra("open_payment_options",false)){getIntent().removeExtra("open_payment_options");handler.post(()->{if(!destroyed&&entitlement!=null&&!CheckoutAccess.inProgress()&&(entitlement.shouldRestrict(this)||RestrictionStartup.wasRestricted(this,entitlement.data)))paymentMethods();});}}
    @Override protected void onStart(){super.onStart();ManagedUpdates.visible(true);}
    @Override protected void onStop(){ManagedUpdates.visible(false);super.onStop();}
    @Override protected void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);unregisterReceiver(updates);worker.shutdown();super.onDestroy();}

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void screen(){screen("Home");}
    private void renderCurrent(){if(!UpdateGate.active(this)&&"Payments".equals(currentPage)&&entitlement!=null){try{status=PolicyController.apply(this,entitlement);}catch(Exception ignored){}history();}else dashboard();}
    private void screen(String selectedTab){
        currentPage=selectedTab;
        daysLabel=null;timerLabel=null;connection=null;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(PAPER);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(14),dp(20),dp(24));scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.WHITE);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(entitlement!=null){
            String[] labels={"Home","Payments","Banking","Support"},icons={"home","payment","bank","help"};Runnable[] taps={this::dashboard,this::history,this::openBank,this::support};int selected=0;for(int i=0;i<labels.length;i++)if(labels[i].equals(selectedTab))selected=i;
            View nav=za.co.bacy.ui.SecureNavigation.create(this,labels,icons,selected,index->taps[index].run());nav.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.navigationBars());v.setPadding(dp(14)+bars.left,dp(8),dp(14)+bars.right,dp(10)+bars.bottom);return insets;});page.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        }
        setContentView(page);
    }
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){
        TextView label=new TextView(this);label.setText(value);label.setTextSize(size);label.setTextColor(color);
        label.setTypeface(size>=20?za.co.bacy.ui.BrandFonts.heading(this):za.co.bacy.ui.BrandFonts.sans(this,bold));label.setLineSpacing(dp(3),1);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(10);parent.addView(label,params);return label;
    }
    private void gap(int height){content.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private LinearLayout card(int background){
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(20),dp(20),dp(16));body.setBackground(AppControls.surface(this,background,10));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(14);content.addView(body,params);return body;
    }
    private View action(LinearLayout parent,String label,boolean primary,Runnable click){String lower=label.toLowerCase(Locale.ROOT);String icon=lower.contains("bank")||lower.contains("eft")?"bank":lower.contains("update")||lower.contains("refresh")||lower.contains("check")?"refresh":lower.contains("wi-fi")?"wifi":lower.contains("emergency")?"phone":"payment";View button=AppControls.action(this,label,"",icon,primary?AccountSheets.CORAL:AccountSheets.MINT,click);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(6);p.bottomMargin=dp(6);parent.addView(button,p);return button;}
    private void detail(LinearLayout parent,String label,String value){text(parent,label,11,MUTED,true);text(parent,value,16,INK,false);}
    private void brand(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView title=new TextView(this);title.setText("LOOP SECURE");title.setTextSize(28);title.setLetterSpacing(.035f);title.setTextColor(INK);title.setTypeface(za.co.bacy.ui.BrandFonts.heading(this));row.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView help=new TextView(this);help.setText("Help ↗");help.setTextSize(13);help.setTextColor(INK);help.setTypeface(za.co.bacy.ui.BrandFonts.sans(this,true));help.setGravity(Gravity.CENTER);help.setMinHeight(dp(48));help.setOnClickListener(v->support());row.addView(help);content.addView(row);View line=new View(this);line.setBackgroundColor(INK);content.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));gap(20);}
    private void setupScreen(String error,boolean connecting){
        screen();brand();boolean owner=PolicyController.owns(this),linked=false,pending=false;
        try{linked=!store.get("device_token").isEmpty();pending=!store.get("enrollment_token").isEmpty();}catch(Exception ignored){}
        text(content,connecting?"Connecting your account":"Phone setup",27,INK,true);
        text(content,"Your business links this phone to the correct customer. You do not need a customer login.",15,MUTED,false);gap(16);
        LinearLayout statusPanel=card(Color.WHITE);detail(statusPanel,"Device management",owner?"Assigned to this phone":"Business setup needed");gap(12);detail(statusPanel,"Customer record",linked?"Saved on this phone":pending?"Enrollment ready to connect":"Waiting for enrollment");
        if(connecting){com.google.android.material.progressindicator.LinearProgressIndicator progress=new com.google.android.material.progressindicator.LinearProgressIndicator(this);progress.setIndeterminate(true);progress.setIndicatorColor(GREEN);content.addView(progress,new LinearLayout.LayoutParams(-1,dp(5)));gap(14);text(content,"Keep Wi-Fi or mobile data connected while we verify the account.",14,MUTED,false);}
        else if(error!=null){text(content,error,14,INK,false);if(linked)action(content,"Refresh account",true,this::refresh);else if(owner&&pending)action(content,"Try connection again",true,this::connectEnrollment);}
        else text(content,"Ask your business to enroll the phone using Android setup QR or the Windows USB setup bundle.",14,MUTED,false);
        gap(10);action(content,"Open banking app",false,this::openBank);action(content,"Wi-Fi connection",false,()->openSettings(true));action(content,"Phone settings",false,()->openSettings(false));action(content,"Emergency call",false,this::emergency);gap(12);text(content,"Setup support",12,MUTED,true);text(content,"basanisilinda@gmail.com",14,GREEN,false);action(content,"Call support · "+SupportAccess.DISPLAY,false,()->contactSupport(false));action(content,"WhatsApp support",false,()->contactSupport(true));
    }
    private void connectEnrollment(){
        if(enrolling||destroyed)return;
        if(!PolicyController.owns(this)){setupScreen("Android has not assigned device ownership. Use your administrator's Android setup QR or Windows USB enrollment.",false);return;}
        enrolling=true;setupScreen(null,true);
        worker.execute(()->{
            try{
                String enrollmentToken=store.get("enrollment_token"),url=store.get("server_url"),key=store.get("public_key");
                if(enrollmentToken.isEmpty()||url.isEmpty()||key.isEmpty())throw new IllegalStateException("The enrollment configuration is missing. Ask your administrator to check this phone's setup.");
                ManagedPermissions.grant(this);
                String nonce=store.get("activation_nonce");if(nonce.isEmpty()){nonce=UUID.randomUUID().toString();store.put("activation_nonce",nonce);}
                JSONObject body=new JSONObject().put("enrollment_token",enrollmentToken).put("model",android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+" / Android "+android.os.Build.VERSION.RELEASE).put("consent",true).put("consent_source","administrator_recorded").put("exchange_nonce",nonce).put("identifiers",DeviceIdentifiers.collect(this));
                JSONObject result=new Api(store).post("/api/device/enroll",body,false);
                if(!result.getString("public_key").equals(key))throw new SecurityException("The enrollment signing key did not match. Contact your administrator.");
                store.establishDevice(result.getString("device_id"),result.getString("device_token"));store.put("enrollment_token","");PolicyController.protectFinancedDevice(this);
                runOnUiThread(()->{if(destroyed)return;enrolling=false;LiveConnectionService.start(this);dashboard();refresh();});
            }catch(Exception failure){runOnUiThread(()->{if(destroyed)return;enrolling=false;setupScreen(failure.getMessage(),false);});}
        });
    }
    private void dashboard(){
        if(destroyed)return;
        if(entitlement!=null){try{status=PolicyController.apply(this,entitlement);}catch(Exception failure){status="policy_error";}}
        screen();brand();
        if(UpdateGate.active(this)){updateScreen();return;}
        if(entitlement==null){text(content,accountIssue==null?"Connecting your account":"Account not loaded",30,INK,true);text(content,"Your customer account is linked to this installation. No login is needed.",16,MUTED,false);connection=text(content,accountIssue==null?"Checking your account…":accountIssue,15,MUTED,false);action(content,"Refresh connection",true,this::refresh);action(content,"Open banking app",false,this::openBank);action(content,"Wi-Fi connection",false,()->openSettings(true));action(content,"Phone settings",false,()->openSettings(false));action(content,"Emergency call",false,this::emergency);return;}
        JSONObject data=entitlement.data;renderedStage=entitlement.stage(this);boolean released=data.optBoolean("released"),overdue=entitlement.expired(this),pending=hasPendingClaim(),grace=renderedStage==AccountTiming.Stage.GRACE;
        za.co.bacy.ui.CustomerHome.Model model=new za.co.bacy.ui.CustomerHome.Model();model.appVersion=BuildConfig.VERSION_NAME;
        model.paidOff=released;model.monthlyPayment="R"+data.optLong("amount",300);JSONObject progress=data.optJSONObject("payment_summary");if(progress!=null){model.paidTotal="R"+progress.optLong("approved_total");model.approvedPayments=progress.optInt("approved_count",-1);if(!progress.isNull("contract_total")&&!progress.isNull("progress_percent")){model.totalPrice="R"+progress.optLong("contract_total");model.progressPercent=(float)progress.optDouble("progress_percent",-1);model.balance="R"+progress.optLong("remaining_balance");model.finalPayment=progress.optLong("remaining_balance")>0&&progress.optLong("remaining_balance")<=300;model.paymentsRemaining=String.valueOf(progress.optInt("payments_remaining"));}}model.name=data.optString("customer_name");model.device=android.os.Build.MODEL;model.reference=data.optString("reference");
        model.status=released?"Paid off":status.equals("restricted")||status.equals("restricted_banking_unavailable")?"Restricted":grace?"Grace period":overdue?"Overdue":"Account connected";
        model.due=date(data.optLong("due"));long seconds=entitlement.secondsLeft(this);model.remaining=seconds<0?"—":String.valueOf((seconds+86399)/86400);model.fraction=seconds<0?0:Math.min(1f,seconds/(30f*86400));if(released){model.remaining="✓";model.ringCaption="paid off";model.fraction=1;}else if(grace){model.remaining=String.valueOf(Math.max(0,(entitlement.restrictionDue()-entitlement.serverNow(this)+3599)/3600));model.ringCaption="grace hours";}
        model.foot=accountIssue==null?statusText():accountIssue+" Showing your last verified account.";model.banner=pending?"Your payment claim is awaiting administrator review.":"EFT reference: "+data.optString("reference");
        setContentView(za.co.bacy.ui.CustomerHome.create(this,model,index->{switch(index){case 0 -> accountDetails();case 1 -> history();case 2,5 -> managementStatus();case 3 -> support();case 4 -> showInbox();case 6 -> onlinePayment(false);case 8 -> emergency();case 9 -> openBank();case 10 -> openSettings(true);case 11 -> openSettings(false);case 13 -> whatsNew();case 14 -> copyReference();case 15 -> enterUnlockCode();case 16 -> onlinePayment(true);case 17 -> paymentReminder();default -> refresh();}}));
        daysLabel=null;timerLabel=null;connection=null;applySystemBars();
    }
    private void updateScreen(){var p=getSharedPreferences("loop.updates",0);text(content,"An app update is ready",28,INK,true);text(content,"The server published a required update. Banking, Wi-Fi and emergency calls remain available.",14,MUTED,false);LinearLayout box=card(AccountSheets.MINT);text(box,p.getString("status","Checking for an update…"),16,INK,true);android.widget.ProgressBar progress=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgress(p.getInt("progress",0));progress.setProgressTintList(android.content.res.ColorStateList.valueOf(AccountSheets.TEAL));box.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));if("ready".equals(p.getString("stage","")))action(content,"Install update",true,()->ManagedUpdates.request(this));else action(content,"Retry update check",true,()->ManagedUpdates.checkSoon(this));action(content,"Open banking app",false,this::openBank);action(content,"Wi-Fi connection",false,()->openSettings(true));action(content,"Call support · "+SupportAccess.DISPLAY,false,()->contactSupport(false));action(content,"Emergency call",false,this::emergency);text(content,"If an update fails, the maintenance restriction is released and your existing finance policy resumes. Your account is retained.",12,MUTED,false);}
    private boolean hasPendingClaim(){if(entitlement==null)return false;JSONArray claims=entitlement.data.optJSONArray("claims");if(claims!=null)for(int i=0;i<claims.length();i++)if(claims.optJSONObject(i).optString("state").equals("pending"))return true;return false;}
    private String date(long epoch){return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(epoch*1000));}
    private String dateTime(long epoch){return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(epoch*1000));}
    private String statusText(){return switch(status){case "unsupported"->"Management needs administrator attention.";case "needs_online_check"->"Connect to verify your account after restart.";case "policy_error"->"Your administrator needs to check the phone's policy.";case "restricted_banking_unavailable"->"No banking app is installed. Pay by EFT from another device or contact support.";case "released"->"Fully paid · finance management released.";case "restricted"->"Payment required · banking, Wi-Fi, settings and emergency access available.";default->"Account connected. Tap to refresh.";};}
    private void updateCountdown(){
        if(entitlement==null||daysLabel==null)return;
        if(entitlement.data.optBoolean("released")){daysLabel.setText(R.string.paid_off);timerLabel.setText(status.equals("released")?"Finance management released":"Device release awaiting confirmation");return;}
        if(entitlement.manualLock()){daysLabel.setText("Admin lock");timerLabel.setText(status.equals("restricted")||status.equals("restricted_banking_unavailable")?"Phone restricted · support and payment available":"Check management status for actual enforcement");return;}
        if(entitlement.stage(this)==AccountTiming.Stage.GRACE){
            long graceLeft=Math.max(0,entitlement.restrictionDue()-entitlement.serverNow(this));
            daysLabel.setText("Grace period");timerLabel.setText(String.format(Locale.getDefault(),"%02dh %02dm %02ds remaining · ends %s",graceLeft/3600,(graceLeft%3600)/60,graceLeft%60,dateTime(entitlement.restrictionDue())));return;
        }
        if(entitlement.expired(this)){daysLabel.setText("Payment overdue");timerLabel.setText(status.equals("restricted")||status.equals("restricted_banking_unavailable")?"Phone restricted · EFT payment available":"Check phone management status below");return;}
        long remaining=entitlement.secondsLeft(this);
        if(remaining<0){daysLabel.setText(R.string.sync_needed);timerLabel.setText("Connect to verify your deadline");return;}
        long days=(remaining+86399)/86400;
        daysLabel.setText(days==0?getString(R.string.due_now):getResources().getQuantityString(R.plurals.days_left,(int)Math.min(Integer.MAX_VALUE,days),days));
        timerLabel.setText(String.format(Locale.getDefault(),"%02dh %02dm %02ds · due %s",(remaining%86400)/3600,(remaining%3600)/60,remaining%60,date(entitlement.data.optLong("due"))));
    }
    private void applySystemBars(){
        if(entitlement==null)return;
        ActivityManager activity=getSystemService(ActivityManager.class);WindowInsetsController bars=getWindow().getInsetsController();
        if(bars==null)return;
        if(activity.getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_LOCKED&&!entitlement.data.optBoolean("released")){
            bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            JSONObject p=entitlement.data.optJSONObject("policy");
            int hide=0;if(p.optBoolean("block_navigation",true))hide|=WindowInsets.Type.navigationBars();if(p.optBoolean("block_notification_center",true))hide|=WindowInsets.Type.statusBars();
            if(hide!=0)bars.hide(hide);
        }else bars.show(WindowInsets.Type.systemBars());
    }
    private void refresh(){
        if(syncing||enrolling||destroyed)return;
        try{if(store.get("device_token").isEmpty())return;}catch(Exception ignored){return;}
        syncing=true;if(connection!=null)connection.setText(R.string.checking);
        worker.execute(()->{
            Entitlement loaded=null;String issue=null;
            try{ManagedPermissions.grant(this);loaded=Sync.run(this);}catch(Exception failure){issue=AccountConnectionError.describe(failure);android.util.Log.w("BacyAccountSync","Account check failed: "+failure.getClass().getSimpleName());try{loaded=Entitlement.load(this,store);}catch(Exception ignored){}}
            final Entitlement result=loaded;final String failureMessage=issue;
            runOnUiThread(()->{if(destroyed)return;syncing=false;accountIssue=failureMessage;entitlement=result;renderCurrent();
                if(pendingInbox&&entitlement!=null){pendingInbox=false;showInbox();}
                String applied=status;worker.execute(()->{try{Sync.report(this,result,applied);}catch(Exception ignored){}});
            });
        });
    }
    private void copyReference(){if(entitlement==null)return;getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("EFT reference",entitlement.data.optString("reference")));toast("EFT reference copied");}
    private String summaryMoney(JSONObject values,String key){return values!=null&&values.opt(key) instanceof Number?"R"+values.optLong(key):"Not available";}
    private void copyPaymentSummary(){
        if(entitlement==null)return;JSONObject data=entitlement.data,summary=data.optJSONObject("payment_summary");
        if(summary==null){toast("Refresh your account to load the verified payment summary.");return;}
        String progress=summary.opt("progress_percent") instanceof Number?summary.opt("progress_percent").toString()+"%":"Not available";
        String message="Loop Secure payment summary\nEFT reference: "+data.optString("reference","Not available")+"\nTotal phone price: "+summaryMoney(summary,"contract_total")+"\nVerified paid: "+summaryMoney(summary,"approved_total")+"\nRemaining balance: "+summaryMoney(summary,"remaining_balance")+"\nProgress: "+progress+"\nNext payment: "+summaryMoney(data,"amount")+"\nBased on the last verified account check. Pending claims do not count as paid.";
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Loop Secure payment summary",message));toast("Payment summary copied");
    }
    private boolean onlinePaymentBusy;
    private String preferredPaymentMethod;
    private void paymentMethods(){if(entitlement==null||entitlement.data.optBoolean("released"))return;AccountSheets.payments(this,"R"+entitlement.data.optLong("amount",300),()->{preferredPaymentMethod=null;onlinePayment(false);},()->{preferredPaymentMethod="eft";onlinePayment(false);},this::paymentDetails,()->onlinePayment(true),this::requestFreshLink);}
    private void requestFreshLink(){AccountSheets.fresh(this,()->onlinePayment(false,true),()->onlinePayment(true));}
    private void accountDetails(){if(entitlement==null)return;JSONObject data=entitlement.data;AccountSheets.account(this,"R"+data.optLong("amount",300),dateTime(data.optLong("due")),dateTime(entitlement.restrictionDue()),data.optString("reference"),this::paymentMethods,this::copyReference,this::copyPaymentSummary);}
    private void onlinePayment(boolean checkOnly){onlinePayment(checkOnly,false);}
    private void onlinePayment(boolean checkOnly,boolean fresh){
        if(onlinePaymentBusy||destroyed)return;onlinePaymentBusy=true;toast(checkOnly?"Checking payment confirmation…":"Preparing your secure payment link…");
        worker.execute(()->{try{
            JSONObject request=new JSONObject();if(fresh)request.put("fresh",true);if(checkOnly&&paymentOutcome!=null){request.put("checkout_outcome",paymentOutcome);paymentOutcome=null;}JSONObject result=new Api(store).post(checkOnly?"/api/device/payments/ikhokha/check":"/api/device/payments/ikhokha",request,true);
            String message=result.optBoolean("needs_new_link")?"Checkout failed or reached its attempt limit. The payment has not been confirmed. Request a fresh link if your bank did not debit you.":paymentStatus(result.optString("state"));store.put("last_online_check",message);
            runOnUiThread(()->{onlinePaymentBusy=false;if(destroyed)return;
                if(checkOnly||result.optString("state").equals("paid")||result.optString("state").equals("review_required")){AccountSheets.status(this,result.optString("state"),result.optBoolean("needs_new_link"),message,()->onlinePayment(true),this::requestFreshLink,this::history);refresh();return;}
                String url=result.optString("checkout_url");if(!PaymentActivity.initialUrl(url)){problem("iKhokha did not return a supported checkout address. Bank EFT remains available.");return;}
                startActivity(new Intent(this,PaymentActivity.class).putExtra("payment_url",url).putExtra("preferred_method",preferredPaymentMethod));preferredPaymentMethod=null;
            });
        }catch(Exception failure){runOnUiThread(()->{onlinePaymentBusy=false;problem("Online payment could not complete this check. "+failure.getMessage()+" If your bank has debited you, check payment status before paying again. Bank EFT remains available.");});}});
    }
    private String paymentStatus(String value){return switch(value){case "paid"->"Your payment is verified. Paid time has been added once. Keep the phone online to refresh access.";case "pending","creating"->"iKhokha has not confirmed this payment. If checkout failed, get a fresh link. If your bank debited you, check again before paying again.";case "review_required"->"This payment needs administrator attention before account credit can be confirmed. Contact support; do not pay again if your bank already debited you.";case "failed"->"A payment link could not be completed. Check with your bank if you were debited before starting another payment.";default->"No online payment link has been recorded for this account yet.";};}
    private void paymentDetails(){if(entitlement==null)return;AccountSheets.eft(this,entitlement.data.optJSONObject("eft"),entitlement.data.optString("reference"),"R"+entitlement.data.optLong("amount",300),this::openBank,this::copyReference);}
    private void openBank(){JSONObject policy=entitlement==null?null:entitlement.data.optJSONObject("policy");java.util.List<BankApps.Choice> banks=BankApps.installed(this,policy);if(banks.isEmpty()){problem("No supported banking app is installed. Install your genuine bank app before handing over the phone, or pay from another device.");return;}AccountSheets.banks(this,banks,bank->{try{BankApps.open(this,bank,policy);bankOpened=true;}catch(Exception e){problem("The bank could not open. Your account has not been unlocked or marked paid.");}});}
    private void reviewStatus(){AccountSheets.panel(this,"EFT under review","Your claim is already recorded").note("Awaiting settlement review","Your administrator must confirm the bank funds before adding paid time. Sending another claim does not speed up approval.",AccountSheets.GOLD,"history").option("Check for approval","Refresh your verified account","refresh",AccountSheets.MINT,this::refresh).show();}
    private void openSettings(boolean wifi){try{DeviceSettings.open(this,wifi,entitlement==null?null:entitlement.data.optJSONObject("policy"));}catch(Exception unavailable){problem("The phone could not open its system settings. Contact your administrator.");}}
    private void managementStatus(){if(entitlement==null)return;try{AccountSheets.panel(this,"Your phone","Connection, access and updates in one place").line("Phone",android.os.Build.MODEL+" · Android "+android.os.Build.VERSION.RELEASE).line("Current access",statusText()).line("Last account check",dateTime(entitlement.data.optLong("server_time"))).option("Connection","Check internet and account sync","wifi",AccountSheets.PAPER,this::connectionStatus).option("Updates",ManagedUpdates.describe(this),"refresh",AccountSheets.PAPER,this::appUpdates).option("Detailed phone report","Permissions and management restrictions","shield",AccountSheets.PAPER,this::technicalStatus).option("Administrator code","Enter a one-use unlock code","shield",AccountSheets.PAPER,this::enterUnlockCode).option("Sync account","Check for the latest account changes","refresh",AccountSheets.CORAL,this::refresh).show();}catch(Exception e){problem("The phone could not read its status.");}}
    private void technicalStatus(){if(entitlement==null)return;AccountSheets sheet=AccountSheets.panel(this,"Phone report","Live Android readings · recovery-button reset protection is not guaranteed");String report=DeviceHealth.describe(this,entitlement);for(String line:report.split("\n")){if(line.trim().isEmpty())continue;int split=line.indexOf(": ");if(split>0)sheet.line(line.substring(0,split),line.substring(split+2));else sheet.line("",line);}sheet.show();}
    private void connectionStatus(){android.net.ConnectivityManager cm=getSystemService(android.net.ConnectivityManager.class);android.net.NetworkCapabilities n=cm.getNetworkCapabilities(cm.getActiveNetwork());boolean valid=n!=null&&n.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);String network=n==null?"No network":n.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":n.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)?"Mobile data":"Network";AccountSheets.panel(this,"Connection check","Network status and verified account contact").note(network,valid?"Android reports internet access. Refresh your account to verify contact with Loop Secure.":"Internet access is not confirmed. Check Wi-Fi or mobile data.",valid?AccountSheets.MINT:AccountSheets.GOLD,"wifi").line("Last verified account check",entitlement==null?"Not available":dateTime(entitlement.data.optLong("server_time"))).option("Check account now","Fetch a signed account update","refresh",AccountSheets.MINT,this::refresh).option("Open Wi-Fi","Choose an internet connection","wifi",AccountSheets.CORAL,()->openSettings(true)).show();}
    private void whatsNew(){AccountSheets.panel(this,"A fresh start","Loop Secure "+BuildConfig.VERSION_NAME).note("A simpler phone plan","White and black, new typography and a clearer view of your payments.",AccountSheets.MINT,"phone").line("Faster banking","Find an installed bank by name.").line("Support drafts","Unsent support messages are saved on this phone.").line("Payment records","Tap a payment to view or copy its record.").line("Personal reminders","Add the next payment to your calendar when the phone is unrestricted.").line("One-tap check","Check payment confirmation directly from Home.").option("Explore payments","See your verified payment history","payment",AccountSheets.CORAL,this::history).show();}
    private void paymentReminder(){if(entitlement==null||entitlement.data.optBoolean("released"))return;if(getSystemService(ActivityManager.class).getLockTaskModeState()!=ActivityManager.LOCK_TASK_MODE_NONE){problem("Calendar reminders can be added when your phone is unrestricted. Payment and banking remain available here.");return;}long due=entitlement.data.optLong("due");if(due<=0){problem("Refresh your account to load the next payment date.");return;}Intent reminder=new Intent(Intent.ACTION_INSERT,android.provider.CalendarContract.Events.CONTENT_URI).putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME,due*1000).putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME,(due+1800)*1000).putExtra(android.provider.CalendarContract.Events.TITLE,"Loop Secure payment · R"+entitlement.data.optLong("amount",300)).putExtra(android.provider.CalendarContract.Events.DESCRIPTION,"Payment reference: "+entitlement.data.optString("reference")+". Open Loop Secure to pay and check confirmation.");try{startActivity(reminder);}catch(android.content.ActivityNotFoundException missing){problem("No calendar app is available. Your next payment date is "+date(due)+".");}}
    private void appUpdates(){AccountSheets.panel(this,"App updates","Your account and enrollment stay in place").note("Update status",ManagedUpdates.describe(this),AccountSheets.MINT,"refresh").note("Check on this connection","A manual check may download on your current connection and restart the app after installation. Automatic checks run in the background on Wi-Fi or mobile data, even when this screen is closed. A managed phone installs verified updates automatically where Android allows it. Active payment checkouts defer installation.",AccountSheets.GOLD,"wifi").option("Check and install","Verify and install the latest published release","refresh",AccountSheets.CORAL,()->{ManagedUpdates.request(this);toast("Update check queued. Keep your phone online.");}).option("Open Wi-Fi settings","Connect to Wi-Fi for automatic downloads","wifi",AccountSheets.MINT,()->openSettings(true)).show();}
    private void claim(){
        if(entitlement==null||entitlement.data.optBoolean("released"))return;if(hasPendingClaim()){reviewStatus();return;}if(claiming){toast("Your payment claim is being sent. Please wait.");return;}if(claimDialogOpen)return;claimDialogOpen=true;
        com.google.android.material.bottomsheet.BottomSheetDialog dialog=AccountSheets.confirm(this,"Send your EFT claim?","Confirm your R"+entitlement.data.optLong("amount",300)+" transfer uses reference "+entitlement.data.optString("reference")+". Your administrator must verify the bank funds before extending paid time.","Send for review",()->{if(claiming)return;claiming=true;worker.execute(()->{try{String id=store.get("claim_request");if(id.isEmpty()){id=UUID.randomUUID().toString();store.put("claim_request",id);}new Api(store).post("/api/device/claims",new JSONObject().put("request_key",id).put("bank_reference",entitlement.data.optString("reference")),true);store.put("claim_request","");runOnUiThread(()->{claiming=false;toast("Payment claim sent for review");refresh();});}catch(Exception e){runOnUiThread(()->{claiming=false;problem("The claim is not confirmed as sent. Reconnect and try again; paid time has not changed.");});}});});dialog.setOnDismissListener(d->claimDialogOpen=false);
    }
    private LinearLayout dialogBody(ScrollView scroll){LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(12),dp(24),dp(20));scroll.addView(body);return body;}
    private void history(){
        if(entitlement==null){problem("Your payment history is loading. Please check again shortly.");return;}screen("Payments");brand();text(content,"Payments",36,INK,true);JSONObject data=entitlement.data,summary=data.optJSONObject("payment_summary");
        LinearLayout overview=card(Color.BLACK);text(overview,"VERIFIED PAYMENTS",11,Color.WHITE,true);text(overview,summary==null?"—":"R"+summary.optLong("approved_total"),48,Color.WHITE,true);long count=summary==null?0:summary.optLong("approved_count");text(overview,summary==null?"Sync your account to see payments":count+(count==1?" payment received":" payments received"),13,Color.WHITE,false);
        if(!data.optBoolean("released")){action(content,"Pay R"+data.optLong("amount",300),true,this::paymentMethods);LinearLayout tools=new LinearLayout(this);View statusButton=AppControls.action(this,"Check status","","refresh",AccountSheets.PAPER,()->onlinePayment(true));View freshButton=AppControls.action(this,"New link","","payment",AccountSheets.PAPER,this::requestFreshLink);LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);half.rightMargin=dp(8);tools.addView(statusButton,half);tools.addView(freshButton,new LinearLayout.LayoutParams(0,-2,1));content.addView(tools);gap(10);try{String last=store.get("last_online_check");if(!last.isEmpty())text(content,last,12,MUTED,false);}catch(Exception ignored){}action(content,"Bank transfer details",false,this::paymentDetails);action(content,hasPendingClaim()?"Track EFT review":"Report an EFT payment",false,hasPendingClaim()?this::reviewStatus:this::claim);}else text(content,"Your phone is paid off. No further payments are due.",15,INK,false);
        gap(20);text(content,"Activity",28,INK,true);JSONArray claims=data.optJSONArray("claims");android.widget.HorizontalScrollView filters=new android.widget.HorizontalScrollView(this);filters.setHorizontalScrollBarEnabled(false);LinearLayout choices=new LinearLayout(this);String[] keys={"all","approved","pending","rejected"},names={"All","Received","In review","Declined"};for(int k=0;k<keys.length;k++){final String key=keys[k];int n=0;if(claims!=null)for(int j=0;j<claims.length();j++)if(key.equals("all")||key.equals(claims.optJSONObject(j).optString("state")))n++;TextView chip=new TextView(this);chip.setText(names[k]+"  "+n);chip.setTextSize(12);chip.setTypeface(za.co.bacy.ui.BrandFonts.sans(this,true));chip.setTextColor(key.equals(historyFilter)?Color.WHITE:INK);chip.setPadding(dp(14),dp(10),dp(14),dp(10));chip.setMinHeight(dp(44));chip.setGravity(Gravity.CENTER);chip.setSelected(key.equals(historyFilter));chip.setBackground(AppControls.surface(this,key.equals(historyFilter)?Color.BLACK:Color.WHITE,3));chip.setOnClickListener(v->{historyFilter=key;history();});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.rightMargin=dp(6);choices.addView(chip,cp);}filters.addView(choices);content.addView(filters);gap(14);
        int shown=0;if(claims!=null)for(int i=0;i<claims.length();i++){JSONObject item=claims.optJSONObject(i);if(item==null)continue;String state=item.optString("state");if(!historyFilter.equals("all")&&!historyFilter.equals(state))continue;shown++;String label=state.equals("approved")?"Received":state.equals("pending")?"In review":state.equals("rejected")?"Declined":"Recorded";LinearLayout row=card(Color.WHITE);LinearLayout top=new LinearLayout(this);TextView amount=new TextView(this);amount.setText("R"+item.optInt("amount"));amount.setTextSize(28);amount.setTextColor(INK);amount.setTypeface(za.co.bacy.ui.BrandFonts.heading(this));top.addView(amount,new LinearLayout.LayoutParams(0,-2,1));TextView badge=new TextView(this);badge.setText(label+"  ↗");badge.setTextColor(INK);badge.setTextSize(12);badge.setTypeface(za.co.bacy.ui.BrandFonts.sans(this,true));top.addView(badge);row.addView(top);if(item.optLong("submitted")>0)text(row,date(item.optLong("submitted")),12,MUTED,false);row.setContentDescription("View payment record · R"+item.optInt("amount")+" · "+label);row.setFocusable(true);row.setMinimumHeight(dp(76));row.setOnClickListener(v->paymentRecord(item));}if(shown==0)text(content,"No payments in this view yet. Only confirmed payments count towards your phone.",14,MUTED,false);
    }
    private void paymentRecord(JSONObject item){String state=item.optString("state");String label="approved".equals(state)?"Received":"pending".equals(state)?"In review":"rejected".equals(state)?"Declined":"Recorded";String when=item.optLong("submitted")>0?dateTime(item.optLong("submitted")):"Not recorded";String reference=entitlement==null?"":entitlement.data.optString("reference");String record="Loop Secure payment record\nAmount: R"+item.optInt("amount")+"\nStatus: "+label+"\nSubmitted: "+when+"\nReference: "+reference+"\n"+("approved".equals(state)?"Approved in your account history.":"Not proof of a confirmed payment.");AccountSheets panel=AccountSheets.panel(this,"Payment record","Your account history").line("Amount","R"+item.optInt("amount")).line("Status",label).line("Submitted",when).line("Reference",reference);if(!item.optString("reason").isEmpty())panel.line("Note",item.optString("reason"));panel.option("Copy record","Keep or share the payment details","copy",AccountSheets.CORAL,()->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Payment record",record));toast("Payment record copied");}).show();}
    private void showInbox(){if(entitlement==null)return;JSONArray messages;try{String cached=store.get("inbox");messages=cached.isEmpty()?new JSONArray():new JSONArray(cached);}catch(Exception e){messages=entitlement.data.optJSONArray("messages");}AccountSheets sheet=AccountSheets.panel(this,"Your messages","Updates from your administrator");JSONArray ids=new JSONArray();if(messages==null||messages.length()==0)sheet.note("All caught up","You have no messages yet.",AccountSheets.MINT,"message");else for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);ids.put(m.optLong("id"));sheet.note(m.optString("title"),m.optString("body")+"\n\n"+date(m.optLong("created")),i%2==0?AccountSheets.MINT:AccountSheets.GOLD,"message");}if(ids.length()>0)sheet.option("Mark as read","Send a read receipt to your administrator","check",AccountSheets.CORAL,()->worker.execute(()->{try{new Api(store).post("/api/device/messages/receipt",new JSONObject().put("ids",ids).put("state","read"),true);runOnUiThread(this::refresh);}catch(Exception e){runOnUiThread(()->problem("Read receipt not sent. Reconnect and try again."));}}));sheet.show();}
    private void support(){AccountSheets sheet=AccountSheets.panel(this,"How can we help?","Call or WhatsApp · "+SupportAccess.DISPLAY);sheet.option("Call support",SupportAccess.DISPLAY,"phone",AccountSheets.CORAL,()->contactSupport(false)).option("WhatsApp support",SupportAccess.DISPLAY,"message",AccountSheets.MINT,()->contactSupport(true)).option("Enter unlock code","A one-use code from your administrator · internet required","shield",AccountSheets.GOLD,this::enterUnlockCode);EditText message=new EditText(this);message.setHint("How can we help?");try{message.setText(store.get("support_draft"));}catch(Exception ignored){}final Runnable saveDraft=()->{try{store.put("support_draft",message.getText().toString());}catch(Exception ignored){}};message.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence v,int start,int count,int after){}public void onTextChanged(CharSequence v,int start,int before,int count){handler.removeCallbacks(saveDraft);handler.postDelayed(saveDraft,400);}public void afterTextChanged(android.text.Editable e){}});sheet.dialog.setOnDismissListener(d->{handler.removeCallbacks(saveDraft);saveDraft.run();});message.setMinLines(4);message.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);message.setGravity(Gravity.TOP);message.setTextSize(15);message.setTextColor(AccountSheets.INK);message.setHintTextColor(AccountSheets.MUTED);message.setTypeface(za.co.bacy.ui.BrandFonts.sans(this,false));message.setBackground(AppControls.surface(this,AccountSheets.MINT,18));message.setPadding(dp(16),dp(16),dp(16),dp(16));message.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});sheet.content(message);TextView error=new TextView(this);error.setTypeface(za.co.bacy.ui.BrandFonts.sans(this,false));error.setTextColor(0xff000000);error.setTextSize(13);error.setVisibility(View.GONE);sheet.content(error);final View[] send={null};send[0]=sheet.formAction("Send support message",()->{String value=message.getText().toString().trim();if(value.length()<5){error.setText("Please add a little more detail.");error.setVisibility(View.VISIBLE);return;}if(supportSending)return;supportSending=true;send[0].setEnabled(false);send[0].setAlpha(.55f);error.setVisibility(View.GONE);worker.execute(()->{try{new Api(store).post("/api/device/support",new JSONObject().put("message",value),true);runOnUiThread(()->{supportSending=false;if(!destroyed){message.setText("");sheet.dialog.dismiss();toast("Support message sent");}});}catch(Exception e){runOnUiThread(()->{supportSending=false;if(!destroyed){send[0].setEnabled(true);send[0].setAlpha(1);error.setText("Not sent. Your message is kept here—check internet and try again.");error.setVisibility(View.VISIBLE);}});}});});sheet.show();}
    private void contactSupport(boolean whatsapp){try{SupportAccess.open(this,whatsapp,entitlement==null?null:entitlement.data.optJSONObject("policy"));}catch(Exception unavailable){problem(unavailable.getMessage());}}
    private boolean redeemingCode;
    private void enterUnlockCode(){
        if(entitlement==null){problem("Connect this phone to its customer account first.");return;}
        AccountSheets sheet=AccountSheets.panel(this,"Administrator unlock code","One use · valid for this phone · internet required");EditText input=new EditText(this);input.setHint("10-digit code");input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setTextSize(22);input.setTextColor(INK);input.setTypeface(za.co.bacy.ui.BrandFonts.sans(this,true));input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(14)});sheet.content(input);TextView message=new TextView(this);message.setTextColor(INK);message.setTextSize(13);message.setTypeface(za.co.bacy.ui.BrandFonts.sans(this,false));sheet.content(message);final View[] submit={null};submit[0]=sheet.formAction("Verify and unlock",()->{String code=input.getText().toString().replace(" ","").replace("-","");if(!code.matches("[0-9]{10}")){message.setText("Enter the 10 digits supplied by your administrator.");return;}if(redeemingCode)return;redeemingCode=true;submit[0].setEnabled(false);message.setText("Verifying with your administrator’s server…");worker.execute(()->{try{Store saved=new Store(this);String request=saved.get("unlock_request_key");if(request.isEmpty()){request=UUID.randomUUID().toString();saved.put("unlock_request_key",request);}JSONObject envelope=new Api(saved).post("/api/device/unlock-code",new JSONObject().put("code",code).put("request_key",request),true);Entitlement verified=Entitlement.accept(this,saved,envelope);saved.put("unlock_request_key","");runOnUiThread(()->{redeemingCode=false;if(destroyed)return;input.setText("");sheet.dialog.dismiss();entitlement=verified;renderCurrent();toast("Code accepted. Your verified account is refreshed.");refresh();});}catch(Exception error){runOnUiThread(()->{redeemingCode=false;if(!destroyed){submit[0].setEnabled(true);message.setText("Not unlocked. Check internet and the code, or ask support for a fresh code. Five incorrect attempts disable a code.");}});}});});sheet.show();
    }
    private void emergency(){try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:112")));}catch(Exception unavailable){problem("Use the phone's system emergency calling option.");}}
    private void toast(String value){if(!destroyed)AppControls.notice(this,value);}
    private void problem(String value){if(!destroyed)AccountSheets.error(this,value==null?"Contact your administrator for help.":value);}
}
