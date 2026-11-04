# BACY05TEN scanner and timing

The EA now scans the chart plus up to eight selected Market Watch instruments by default when InpSymbols is empty. InpMaxScanSymbols is bounded to 1–12 for low-memory hardware. Supply explicit InpSymbols to select an exact list, or disable InpScanMarketWatch for chart-only operation. Symbols must exist on the account and initialize successfully; a failed symbol initialization stops startup with an error. Start with a small instrument set on the Celeron computer.

On an acknowledged Start, cached completed-candle signals can be checked on the next tick/timer cycle rather than waiting for another candle to open. Evaluation is bounded to once per second per symbol; indicator values refresh on a new bar. A currently eligible signal may therefore be evaluated quickly, subject to MT5 event scheduling and broker conditions. Closed-candle strategy rules still apply; this does not manufacture a new signal every second.

No trade is forced after 30 seconds or 30 minutes. Risk rejections are re-evaluated; once an order submission is attempted, it is not repeated within the same candle. Uncertain-order reconciliation, account checks, stop-loss requirements, margin, correlation, spread, calendar, cooldown and loss gates remain in force. There is no martingale or loss-based exposure escalation.

scanner.csv contains only actual fresh bid/ask quotes from selected instruments, tagged with the server-bound account identity. The authenticated backend rejects stale, invalid and cross-account rows. Missing quote files produce an unavailable state, not simulated quotes. This monitoring export is optional and independent of execution risk approval.

The screenshot-matched planned-loss field is an estimate derived from verified equity and the configured percentage cap. It remains blank when account data is absent. It is not a guaranteed maximum loss or an independently editable cash-risk override. Change approved limits through Settings while paused.

The visual preview never connects to an XM account or sends orders. The updated MQL source requires MetaEditor compilation and broker demo testing. No execution timing, profitability or 100% reliability has been verified or guaranteed. General web searches/LLM calls are not installed in the order path; no reliable zero-cost real-time web-research provider is configured. Local technical analysis and the available native calendar are the automated inputs.
