> Update: account-bound live-mode source support was added after the original demo baseline. See [live-mode notes](LIVE_MODE.md). Defaults remain demo; no live broker validation has been performed. Older demo-specific procedures remain the recommended validation path.

# Windows demo quickstart

**Easier setup:** Extract the project ZIP and double-click `Start Here.cmd`. Read `READ ME FIRST.txt` first. The guided launcher runs local setup, attempts to install/compile the EA, and opens the dashboard. It never enters an XM password, attaches a chart, or starts trading. The manual instructions below remain available if a step needs diagnosis. Windows launcher execution has not been verified in this cloud workspace.

This release is a **demo-only research and execution system**. It defaults to demo; live operation requires local account pairing described in LIVE_MODE.md. It does not have a proven profitable strategy. The Python service does not hold your XM password; sign in to XM only inside MetaTrader 5.

The package contains source code, not a precompiled Windows trading program. MQL5 compilation and XM demo acceptance tests must be completed on Windows before unattended use. A successful dashboard login is not a successful trading test.

## 1. Prepare the computer

Use your existing supported 64-bit Windows computer, internet connection, and a Windows account you control. Keep the project in a private user folder that is not synchronized to a public/shared cloud folder. MT5 and the backend must run as the **same Windows user**.

Install, using official distribution sources:

1. Python 3.11 or newer, with its Python launcher. Python is free. A supported stable 64-bit Python version with dependency wheels is preferable to a brand-new prerelease.
2. XM's MetaTrader **5**, including MetaEditor. MT4 is a different platform and cannot run this EA. MT5 is proprietary software; its desktop download has no initial software fee. XM account availability and terms depend on your region.
3. An up-to-date Edge or Chrome browser. Android installation is a browser PWA, not a Play Store app.

Create or use an eligible **XM demo account** and sign into it in MT5. Do not deposit money for these steps. The setup scripts do not create a broker account, accept broker terms, or install MT5 for you.

Check Windows date/time synchronization. The connection protocol rejects stale and future-dated information. MT5, Python, and the computer must stay running while the system operates. Windows sleep, a closed laptop, or a power failure interrupts monitoring; an Android phone is not a replacement execution server.

## 2. Set up the dashboard locally

Extract the source archive. In PowerShell, open its project folder and run:

```powershell
.\scripts\Setup-Windows.ps1
```

If Windows blocks locally downloaded scripts, first read them. For this one invocation, you may use:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Setup-Windows.ps1
```

This does not change the machine-wide execution policy. Administrator rights are not normally required. Setup downloads the project's free Python dependencies, creates a project virtual environment, and asks for a **new dashboard password of at least 12 characters**. Do not reuse or enter your XM password.

The script saves a salted password hash, generates a random connection secret, and restricts the private data/configuration/bridge folders to your Windows user and Windows SYSTEM. It never prints the password or secret. It does not change firewall rules or publish anything.

The default bridge folder is:

```text
%APPDATA%\MetaQuotes\Terminal\Common\Files\XMUltraAI
```

For a custom MT5 environment, confirm the terminal's `TERMINAL_COMMONDATA_PATH`, then use `-BridgeDir` with its `Files\XMUltraAI` directory. The terminal's ordinary **File → Open Data Folder** directory and its **Common Files** directory are different. Do not point two different accounts or EAs at one bridge.

Rerunning setup preserves your database, existing password, and connection secret. To change only the dashboard password while the service is stopped:

```powershell
.\scripts\Setup-Windows.ps1 -ChangePassword
```

Restart the dashboard afterwards so old sessions no longer remain active. Never share `config/local.json`, `data/password.hash`, `bridge.secret`, or `control.csv` in screenshots, chat, or source control.

## 3. Install and compile the Expert Advisor

1. In MT5 choose **File → Open Data Folder**.
2. Copy the supplied `mt5` source tree into an `XMUltraAI` folder under that terminal's `MQL5\Experts` directory, preserving its relative include structure.
3. Open the supplied main EA, `XMUltraAITrader.mq5`, in MetaEditor and choose **Compile**.
4. Resolve every compile error before proceeding. Save the actual compiler log. This project was not compiled by MetaEditor in the Linux cloud environment; source delivery is not a claim of successful compilation.
5. Refresh **Navigator → Expert Advisors** in MT5. Open a chart for an instrument that actually exists on your XM demo account. Suffixes and available instruments differ between accounts.
6. Attach one copy of the EA. Begin with one chart instrument and the delivered default strategy. Keep algorithmic trading disabled until you have reviewed its Inputs and completed the validation procedure.
7. Read [the MT5 component notes](../mt5/README.md) for the final input names, state persistence, and any tester-specific controls. Do not remove the demo-account check to make a test run.

Start with one terminal, one demo account, and one EA instance. Concurrent account/terminal instances sharing a bridge are unsupported.

## 4. Open the local dashboard

Run:

```powershell
.\scripts\Start-Windows.ps1
```

Keep that window open. Visit:

```text
http://127.0.0.1:8765
```

Sign in with the dashboard password. Until MT5 publishes a fresh snapshot, account values should show **unavailable**, not a fictional balance. Confirm all of these:

- The displayed account is the intended **demo** account and the broker information matches MT5.
- Balance, equity, margin, and existing positions agree with the terminal.
- Connection data is fresh, and the robot is paused or blocked for an understandable reason.
- The selected instrument and risk settings are correct.
- A missing news source or invalid symbol produces a block rather than a trade.

Do not interpret an open dashboard port, green web server health check, or working voice response as proof of broker connectivity.

## 5. Validate before starting automation

Follow [VALIDATION.md](VALIDATION.md), including manual failure tests and the MT5 tester procedure. Once the demo validation checks are complete, enable algorithmic trading in MT5 and explicitly select **Start trading** in the dashboard, confirming **START DEMO**.

A submitted command remains pending until MT5 acknowledges it. Confirm the terminal state changes, then verify any actual deals in MT5's History tab. A signal or submitted order is not a filled trade.

The EA may legitimately place **no trades**. It must not trade to satisfy a timing target. If the broker's minimum lot exceeds the configured risk allowance, if news data is unavailable, or if safety conditions are incomplete, remaining idle is the intended behavior.

## Everyday controls

| Control | Intended effect |
|---|---|
| Pause | Prevent new entries; continue protective management when MT5 can run. |
| Resume | Request a fresh, authenticated demo start after checks pass. |
| Emergency stop | Latch a stop on new submissions. It does not liquidate positions. |
| Close demo positions | Separate, explicitly confirmed attempt to close this robot's positions and cancel its orders. Broker execution is not guaranteed. |
| Close browser | Stops that browser's monitoring and notifications, not necessarily the backend or EA. |
| Stop backend | Heartbeat lease expires and new entries are blocked; broker-held stops remain subject to broker/market execution. |

Emergency commands require connectivity to reach MT5. If the dashboard cannot reach the terminal, use MT5 directly to disable algorithmic trading or manage positions. Do not assume the app's pending button state closed an order.

For Android access, use [SECURITY.md](SECURITY.md). Localhost on a phone means the phone itself; it does not connect to your Windows computer.
