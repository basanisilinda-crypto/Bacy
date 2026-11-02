# Account-bound live-mode source support

The updated source permits automatic live trading only after explicit local account pairing. It is disabled by default. This change is not evidence that the EA compiles, trades correctly with XM, or is suitable for real money. No live account has been connected or tested. Complete the Windows compilation, Strategy Tester and demo acceptance checks first. There is no forced trade or guarantee of execution within a minute.

The engine continues automatically after an acknowledged Start, without approval for every order. Every entry still passes the strategy, protective-stop, exposure, margin, news, connection and loss gates. The engine may place no orders. Pause and emergency stop block new entries; liquidation is separate. Startup/reconnect protection and expiring leases remain.

Two independent local settings are necessary for live permission:

1. In EA Inputs, set `InpAllowLive` only for the exact intended real account, with `InpLiveAccountLogin` and `InpLiveAccountServer` matching the terminal. The defaults are false, zero and empty. Demo operation needs none of these. Recompile the updated source before using the new inputs.
2. With the Python backend stopped, set `live_account_id` in private `config/local.json` to the exact server-bound account identity shown in current telemetry. This is the application's opaque identity, not merely the broker login number. The Windows runner maps it to `XM_LIVE_ACCOUNT_ID`; direct development launches must supply that setting themselves. No setting is prefilled with an actual account. Keep credentials in MT5.

The dashboard displays a live-account warning. Starting/resuming or liquidating requires a mode-specific confirmation (`START LIVE`, `RESUME LIVE`, `CLOSE LIVE POSITIONS`). This is an operation-level confirmation, not approval for individual trades. The backend rejects other accounts even if they are real accounts. The EA independently checks the exact login and server. There is no dashboard API that changes the local live-account authorization.

Conservative risk ceilings remain unchanged. Optional Python research sizing checks remain demo-only; those tools do not submit orders. MQL5 remains the final execution/risk authority for both supported account modes. Models never bypass it.

Automated tests verify backend account matching, wrong-mode confirmation rejection and pending-command semantics. They do not validate actual live broker orders or MQL compilation. Source changes, including account-mode guards, must be independently reviewed on Windows before any real-account use. Trading capital, spreads, commissions, swaps and losses are outside the zero-cost software requirement.
