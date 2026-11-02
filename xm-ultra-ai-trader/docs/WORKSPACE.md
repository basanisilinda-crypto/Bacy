# Account workspaces and connection features

The Operations update adds seven user-facing capabilities:

1. Separate Demo and Live workspace selections. These are dashboard views; sign into the actual matching broker account in Windows MT5 yourself. Selecting Live never creates an account or authorizes it for execution.
2. Server-side account-view matching: Start, Resume and liquidation reject a workspace/account mismatch. Change workspace only while paused, with no command pending.
3. Current MT5 connection, telemetry age and control-lease status from real terminal snapshots.
4. Last command receipt and explicit pending state, with no assumptions that requests succeeded.
5. Readiness checklist: owner authentication, fresh telemetry, account-view matching and local live pairing. Passing it does not establish strategy/news/risk approval or certify broker execution.
6. Authenticated CSV export of actual recorded broker events. Empty history produces headers only. Spreadsheet formula injection is neutralized; data remains private and can include account history.
7. A downloadable diagnostic report from the System Health screen, excluding passwords, bridge secrets and configuration file paths.

The layout now uses a graphite/violet operations interface rather than the earlier light editorial style. The published Site is explicitly an interactive design preview, with no broker account, synthetic balances or trade execution. Its account selector changes only the preview view. The installed Windows backend connects through the MQL5 bridge and retains all risk, authentication, expiry and account-pairing gates.

Keep the browser and backend updated together. If the account selector does not match the terminal, pause and sign into the correct MT5 account; do not treat changing a label as switching real accounts. The default is Demo. Live capability remains disabled until account-specific local pairing, and actual MT5 compilation and broker acceptance remain unverified in this cloud workspace.
