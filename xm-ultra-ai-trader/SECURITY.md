> Update: account-bound live-mode source support was added after the original demo baseline. See [live-mode notes](docs/LIVE_MODE.md). Defaults remain demo; no live broker validation has been performed. Older demo-specific procedures remain the recommended validation path.

# Security scope

This project controls a **demo-default** MetaTrader Expert Advisor. It does not accept XM account passwords or offer a live-trading activation endpoint. Broker credentials remain in the MT5 terminal.

The default backend listens only on the Windows loopback interface. Network access requires explicit HTTPS configuration, approved host names, an authenticated session, and browser CSRF checks. A local Windows user or administrator who can alter the EA, its state files, or backend source is inside the trust boundary; the application is not protection against a compromised computer.

Keep `data/`, bridge secrets, password hashes, certificates, backups, and broker account exports out of version control and public shares. Use an ordinary Windows user account, updates, disk protection where available, and a dedicated demo account. Do not open router ports to the dashboard.

An emergency stop requests that the EA stop new submissions. It is confirmed only after acknowledgment from the terminal. It does not imply that open positions have been closed, that an in-flight order was canceled, or that an unreachable terminal received the request. Existing broker-held stops remain subject to slippage, market closures, and gaps. Closing bot-owned demo positions is a distinct explicit operation.

If a defect might affect order safety, pause the EA in MT5, inspect actual positions in the terminal, preserve the local diagnostic records, and keep trading disabled until it is understood. Do not include passwords or the bridge secret in bug reports.

See the operating, remote-access, and recovery guides in `docs/` for the supported setup and failure handling. This source release has not been subjected to an independent security audit.
