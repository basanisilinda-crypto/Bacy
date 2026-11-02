> Update: account-bound live-mode source support was added after the original demo baseline. See [live-mode notes](docs/LIVE_MODE.md). Defaults remain demo; no live broker validation has been performed. Older demo-specific procedures remain the recommended validation path.

# Integration contract

Project: XM ULTRA AI TRADER. Windows MT5 execution, Python FastAPI/SQLite analytical and control backend, vanilla-JavaScript Android-installable PWA. No paid APIs, no broker passwords in this application. Real account execution is hard-disabled in this initial release; demo validation is required before a separately reviewed live implementation. MT5/XM software is proprietary and available without an initial software fee; our code and other dependencies use free/open-source components. No invented results or connected states.

## Ownership
- `mt5/`: execution engineer.
- `xmtrader/api.py`, `auth.py`, `storage.py`, `bridge.py`, `settings.py`, `assistant.py`, `__init__.py`, `tests/test_api.py`, `test_bridge.py`, `test_auth.py`: backend engineer.
- `xmtrader/research/`, `tests/test_research.py`: quantitative engineer.
- `xmtrader/risk.py`, `tests/test_risk.py`, `tests/test_order_state.py`: risk engineer.
- `web/`: frontend engineer.
- `docs/`, `scripts/`, `config/`: delivery engineer.
- Root files, integration, final review: coordinator.

## Python runtime
Python >=3.11. Package is `xmtrader` directly at project root. Start `python -m uvicorn xmtrader.api:app --host 127.0.0.1 --port 8765`. API also exposes `create_app(settings=None)` for isolated tests. Runtime needs FastAPI, uvicorn, numpy; research extras scikit-learn optional. No API-key setup, LLM downloads, external news calls or external hosting required. Environment names use `XM_` prefix. SQLite state defaults to `data/trader.db`, Common Files bridge to `data/bridge` for development; Windows installer selects the user's actual MT5 Common Files directory. Account/financial fields unknown until telemetry arrives must be null, never synthetic zero balances.

## HTTP API (same origin dashboard)
- `GET /api/health`: public minimal `{status, version}`.
- `POST /api/auth/login` body `{password}`: HttpOnly SameSite=Strict session cookie; response `{authenticated:true,csrf_token}`. Password must be configured; fail closed if missing. UI keeps CSRF token only in memory.
- `GET /api/auth/session`: authenticated `{authenticated:true,csrf_token}`.
- `POST /api/auth/logout`: authenticated + CSRF.
- All `/api/` except health/login require session authentication. State-changing requests use `X-CSRF-Token`; validate origin where supplied; bounded payloads, rate limits. No CORS wildcard.
- `GET /api/status`: `{connected,stale,demo_only:true,mode:'demo'|'live'|'unknown',broker,account_id,currency,balance,equity,margin,free_margin,floating_pnl,daily_pnl,weekly_pnl,robot_state,last_update,reason,latency:{},risk:{},capabilities:{}}`. Nullable unknown values. `robot_state` is `disconnected`, `paused`, `running`, `emergency`, or `blocked`. Terminal-reported state wins; requested command is not completion.
- `GET /api/positions`: `{items:[{ticket,symbol,side,volume,entry_price,sl,tp,profit,opened_at,strategy}]}`.
- `GET /api/trades`: `{items:[...]}` immutable actual broker events, not simulations.
- `GET /api/events?after=0`: `{items:[{id,time,kind,message,severity,...}],last_id}` authenticated bounded polling, serves alerts and audit-safe messages.
- `GET /api/strategies`: `{items:[{id,name,enabled,validated:false,description,parameters}]}`.
- `GET /api/analytics?period=daily|weekly|monthly|all`: actual-only metrics, plus explicit insufficient-data state.
- `GET /api/diagnostics`: no secrets; runtime state and supported capabilities.
- `GET /api/settings`: approved risk limits; `PUT /api/settings` validates enforced bounds. Defaults risk_per_trade_pct .25, daily_loss_pct 1, weekly_loss_pct 3, max_drawdown_pct 5, max_positions 2, max_consecutive_losses 3. Configuration risk values in percent units, not fractions.
- `POST /api/control` body `{action:'start'|'pause'|'resume'|'emergency_stop'|'liquidate',confirmation?:string,request_id:string}`. Require confirmation equal `START DEMO`, `RESUME DEMO`, or `CLOSE DEMO POSITIONS` for respective actions. Pause/stop need authenticated explicit action but no obstructive extra confirmation. Request IDs idempotent. Return `{accepted,command_id,state:'pending',message}`, not assumed execution. Liquidation is separate from stop and only bot-owned positions/orders.
- `POST /api/strategies/{id}` body `{enabled,confirmation:'CHANGE DEMO STRATEGY'}` affects only supported execution IDs and requires paused state; cannot enable unimplemented execution strategies.
- `POST /api/assistant` body `{message}` returns `{reply,intent,proposed_action:null|'pause'|'emergency_stop',requires_confirmation:boolean}`. Analytical/rule-based only by default, grounded in database/telemetry. Never executes control by itself; UI uses normal authenticated control flow.
- Static web routes `/`, `/app.js`, `/style.css`, `/manifest.webmanifest`, `/sw.js`, `/icons/...`. No caching authenticated API responses in service worker. Production HTTPS for network/PWA/voice; HTTP allowed only localhost for local Windows use.

## MT5 file bridge protocol v1
File IO on timer only, never network requests in OnTick. Root directory `XMUltraAI` under MT5 FILE_COMMON on Windows (backend XM_BRIDGE_DIR). Read/write CSV UTF-8, comma separator, decimal dot, header mandatory. Producer writes temp then replaces final. Consumer rejects incomplete/malformed/nonfinite/stale files. OS user permissions are the trust boundary, plus random shared secret in `bridge.secret` (ASCII token, generated by setup; never committed or logged). Secrets never included in telemetry. All times Unix UTC seconds (`TimeGMT()` for EA emission; execution timestamps can additionally carry broker server time explicitly).

`account.csv` one row:
`version,timestamp,account_id,broker,mode,currency,balance,equity,margin,free_margin,floating_pnl,daily_pnl,weekly_pnl,robot_state,reason,heartbeat_seq,decision_us,risk_us,submission_us,broker_response_us`

`positions.csv`:
`ticket,symbol,side,volume,entry_price,sl,tp,profit,opened_at,strategy`
Full snapshot, all account positions for monitoring and exposure; mark non-bot ownership in strategy (`external`) and never modify them.

`symbols.csv`:
`symbol,selected,trade_mode,volume_min,volume_max,volume_step,tick_size,tick_value,contract_size,stops_level,spread_points`
Discover actual account symbols, do not hardcode XM suffixes. UI starts with selected chart instrument only.

`events.csv` append-only:
`event_id,timestamp,kind,symbol,ticket,side,volume,price,profit,commission,swap,strategy,message`
Stable unique event IDs permit DB deduplication. OnTradeTransaction actual deals record `trade_opened`/`trade_closed`; order requests use distinct event kinds and never count as fills. Read only complete lines. Python maintains offsets or dedupes with bounded reads.

`control.csv` one row (latest desired state/command), atomically replaced:
`version,command_id,issued_at,expires_at,action,secret,risk_per_trade_pct,daily_loss_pct,weekly_loss_pct,max_drawdown_pct,max_positions,max_consecutive_losses,strategy`
Allowed actions `start`, `pause`, `resume`, `emergency_stop`, `liquidate`, `heartbeat`, `configure`. `command_id` unique, persisted dedup. `heartbeat` renews a lease but NEVER clears paused/emergency state; at most 30-second expiry and max age. Start/resume require current demo account, validated symbols, history and persisted risk anchors. Bridge renews heartbeat every 5 seconds ONLY after a start/resume request; process restart defaults paused and requires fresh start/resume. Missing/expired control lease blocks entries. Initial EA state paused; terminal restart always pauses. Emergency stop latches; resume requires distinct deliberate command. `ack.csv`: `command_id,timestamp,accepted,state,message`. Settings can only tighten defaults in release one; invalid/out-of-range values rejected on both sides. Default strategy `ema_cross`; one execution strategy at a time until interaction validated.

Economic events: EA may use native MT5 calendar with server-time handling, optional trusted manual CSV fixture `news.csv` fields `timestamp,currency,impact,title` plus freshness metadata; missing essential news source blocks new entries when enabled. Do not claim live news during offline testing. Document tester/calendar limitations.

## Research and risk integration
Research must be pure Python using explicit OHLC data provenance, no implicit downloads, no external credentials. All backtests/synthetic fixtures clearly marked simulated. Expose documented functions/classes and a CLI, strategy registry, indicators, cost-aware next-bar backtesting, walk-forward splits, sensitivity and optional ML. Backend analytics uses actual broker records. Analysis cannot bypass EA risk gate or submit broker orders.

Risk module standalone typed dataclasses with configurable but enforced conservative limits, include floating losses, account-wide existing and pending exposure, currency-aware stop risk, margin, stale data, and stop validation. Provide durable risk anchors/account isolation and explicit state machine with uncertain-submission reconciliation. Coordinate Python interfaces with backend engineer; MQL risk implementation remains final authority.

## Delivery truthfulness
Never assert MQL compiled or broker-tested without MetaEditor/XM terminal. Automated Python/browser tests are separate from required Windows MT5 Strategy Tester and demo soak testing. The user can compile delivered source on Windows for free. No account creation, real trades, deposits, publishing, paid services or optional external services are authorized. Provide full project archive, beginner setup guide, feature matrix distinguishing implemented/experimental/unsupported/unvalidated, test report and limitations.
