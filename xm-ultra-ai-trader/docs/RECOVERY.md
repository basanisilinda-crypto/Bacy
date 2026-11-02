# Backup and recovery

Pause in MT5 and verify actual positions before recovery work. A dashboard database is not authoritative broker state.

With local setup complete, `python scripts/backup.py create PRIVATE_BACKUP.zip` uses SQLite's online backup API. The archive contains the database and a manifest, excluding passwords, connection secret and MT5 terminal risk anchors. Financial history is still private. Back up terminal state and broker exports separately through supported MT5/Windows procedures.

To restore, pause the EA, stop every backend process, retain current broker exports, then run `python scripts/backup.py restore PRIVATE_BACKUP.zip --confirm-stopped`. The managed runner lock prevents concurrent restoration; a manually launched uvicorn process must also be stopped. The helper verifies database integrity and preserves a pre-restore copy. It does not clear MT5 risk latches or enable trading.

Restart paused and reconcile the account, positions, pending orders, stops and trade history with MT5. Do not delete equity anchors, cooldowns or uncertain-order state to bypass a block. If accurate historical risk baselines cannot be reconstructed, start a separately documented isolated demo evaluation. See mt5/README.md for period-boundary and risk-state restrictions.

For a lost dashboard password, stop the service and rerun Setup-Windows.ps1 with -ChangePassword. Never supply your XM password. For corrupted secrets or suspected compromise, disable the EA, preserve diagnostics privately and repair/re-pair locally before resuming. A compromised Windows user account is beyond the application's isolation boundary.
