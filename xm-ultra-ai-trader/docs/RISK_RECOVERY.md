# Recovery after a missed UTC risk boundary

The screenshot error means the EA lacks a verified daily/weekly starting-equity anchor. Connection and command delivery may work while this separate risk gate rejects entry. Repeated Start/Stop presses cannot repair the anchor.

Version 1.001 adds broker-history reconstruction. It operates only with a connected, flat account, zero pending orders, valid book equity, supported complete history, and no deal activity within a three-hour guard around the period boundary. The guard avoids guessing historical broker UTC/DST conversion. It rejects positions carried across the boundary, cancelled deals, unknown audit details, too-recent periods, and histories exceeding the bounded processing budget.

Already-valid current-period anchors are preserved. Only missed periods are reconstructed. For each proven-flat missed period it subtracts all signed broker financial changes (profit, commission, swap, fee, funding/credit flows) from current equity. Trade volume is reconciled separately by position ID; offsetting different positions cannot conceal boundary exposure. It never substitutes balance for historical floating equity. Successful reconstruction updates only period anchors and can only raise, never lower, the known high-water mark. Loss streaks, cooldowns, uncertain-order latches and the operator stop remain. Start is still a separate deliberate request.

If reconstructed history shows losses beyond an approved daily/weekly/drawdown limit, trading remains blocked for that limit. Recovery is not permission to erase losses. If history cannot prove the baseline, the EA reports why and requires independent reconciliation; it does not silently reset risk state.

The native routine attempts recovery on startup and about every 30 seconds while the baseline is blocked. A successful recovery emits risk_baseline_reconciled. The optional Python reference calculator has 11 focused tests, including losses/fees, funding, boundary exposure, missing coverage and unsupported corrections. This does not establish that the MQL implementation has compiled or passed broker tests.

## Install this fix

1. Stop new trading and remove the EA from its chart using MT5 Expert List; do not delete its files or global variables.
2. Update the project and the EA source. Compile XMUltraAITrader.mq5 in MetaEditor; this version is 1.001. Resolve compilation errors before proceeding.
3. Attach the compiled EA to the same demo account. Keep its existing risk state. Wait for fresh terminal data, then inspect the actual recovery or rejection message.
4. Request Start only after the recovered state and account are verified. A different risk rejection must be addressed on its own merits.

Do not delete terminal global variables, change the magic number to escape a limit, or create an unrecorded baseline reset. The supplied Windows test/acceptance procedures remain required. No live-account or timing guarantee is made.
