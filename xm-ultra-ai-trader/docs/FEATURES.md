> Update: account-bound live-mode source support was added after the original demo baseline. See [live-mode notes](LIVE_MODE.md). Defaults remain demo; no live broker validation has been performed. Older demo-specific procedures remain the recommended validation path.

# Source-release feature status

This is an engineering baseline for supervised demo evaluation. Implementation does not establish broker compatibility or strategy profitability.

| Component | Delivered | Remaining boundary |
|---|---|---|
| MQL5 execution | EA source, ten strategy rules, market entries and position management | MetaEditor compilation and XM account acceptance unverified |
| Pending orders | Existing pending exposure counted; owned-order cancellation | Creation of new pending entries not implemented |
| Independent risk | Python tested gates; EA authority, stops, sizing, equity anchors, cooldown and lease | Broker-specific edge cases require Windows tests; stop fills can exceed intended loss |
| Multiple instruments | Actual instrument discovery and selected-set monitoring | Begin with one instrument on low-end hardware; portfolio behavior unvalidated |
| Analytics | Actual broker-event metrics and account equity samples | Closed deals are not whole positions; entry commissions not fully reconciled in close-only statistics |
| Research | Ten causal strategies, indicators, bar simulation, walk-forward/sensitivity | Python strategy outputs are research tools, not automatically fed into the EA |
| Machine learning | Optional local logistic/forest/boosting, regression, anomaly models and temporal partitions | Experimental; no production deployment or online learning |
| News | Native MT5 calendar guard | Availability must be verified; no general live disruption feed; manual-news CSV import not implemented |
| Dashboard | Windows/mobile responsive PWA shell, controls, history, assistant | Physical Android install, HTTPS trust and device voice support need acceptance |
| Conversation | Grounded deterministic intents and explanations | No general-purpose local language model/provider adapter in this release |
| Voice/alerts | Browser recognition/TTS, typed fallback, connected-browser alerts | Browser provider may process speech remotely; no background remote push guarantee |
| Security | Sessions, password hashes, CSRF, host/origin validation, private defaults | No independent penetration audit; compromised Windows users are outside boundary |
| Operations | Windows setup/runner, SQLite backup/restore, diagnostics | PowerShell and scheduled task behavior not executed on Windows here |
| Live trading | Account-bound source support, disabled by default | Compilation and actual broker execution unverified; local pairing required |

Deal-history replay timestamps currently reflect observation time, as documented in the MT5 notes. Do not use replayed events for precise historical daily/session performance attribution. The dashboard discloses closed-event accounting limitations. Preserve actual broker exports for independent reconciliation.

Default limits cannot be increased above the conservative release ceilings. A separate reviewed release is needed for expanded permissions. No return, fill speed, news coverage, uptime, or loss ceiling is guaranteed.
