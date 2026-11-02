> Update: account-bound live-mode source support was added after the original demo baseline. See [live-mode notes](../docs/LIVE_MODE.md). Defaults remain demo; no live broker validation has been performed. Older demo-specific procedures remain the recommended validation path.

# MT5 demo execution engine

`XMUltraAITrader.mq5` is the native MQL5 execution engine. It is **demo-default**; non-demo accounts fail initialization and are rechecked before requests. This source has not been compiled in MetaEditor or connected to XM in the cloud workspace. A successful Python test run is not proof of MQL compilation or brokerage compatibility.

## What is implemented

- One EA instance monitors up to twelve explicitly selected broker instruments. Exact symbols and suffixes come from the connected terminal; no XM symbol name is invented.
- Cached completed-bar EMA, ATR, RSI and ADX signals, higher-timeframe EMA confirmation, and ten executable research strategies: `ema_cross`, `trend`, `momentum`, `breakout`, `mean_reversion`, `support_resistance`, `volatility`, `session`, `multi_timeframe`, `regime`. One strategy is active at a time. None has been validated as profitable.
- Chart-symbol evaluation on ticks and other selected instruments on the one-second timer. Entries occur only for a new completed-bar decision. Rapid evaluation does not mean rapid trading.
- Protected market entries, broker `OrderCheck`, stop/tick/volume restrictions, permitted filling policy, margin reserve, account-currency stop sizing with `OrderCalcProfit`, and rejection of lots below the broker minimum rather than rounding risk upward.
- Account-wide position and pending-order exposure, unrealised equity losses, fixed conservative ceilings, missing-stop rejection, and a conservative shared-currency exposure rule. The shared-currency rule is a concentration heuristic, **not** an estimated statistical correlation matrix.
- Broker-confirmed deal records distinct from order submissions. Partial executions are reconciled. Uncertain outcomes latch new entries off; they are not retried automatically.
- Owned-position stop management, trailing, break-even with a spread/deviation allowance, time/invalidation exits, optional one-off hedging-only partial close, and explicit owned-position liquidation.
- Authenticated expiring CSV control protocol, persisted command deduplication, startup pause, emergency latch and control-lease expiry. An emergency stop disables new entries; it does not imply liquidation. Existing server-side stops remain in place.
- Native MT5 economic-calendar guard, refreshed in timer work, with high-impact-event windows and missing-source refusal. Calendar support depends on the terminal/broker. Unexpected news cannot be exhaustively detected from scheduled events.
- UTC equity anchors persisted with terminal global variables; account/server/magic namespacing; append-only event CSV; full position snapshots; discovered symbol specifications; local microsecond timings.

## Important limitations

1. Market orders are implemented. Existing pending orders are included in risk and owned orders can be cancelled during liquidation, but creating new pending entries is deliberately not implemented until broker-specific expiry and stop-limit behavior is tested.
2. The ten strategies are research rules, not ten proven profitable systems. Session rules use broker-server hours; choose hours explicitly for the broker account. M1 through D1 can be selected, but low-timeframe scalping is not validated.
3. Stop-loss prices do not guarantee execution prices. Gaps, commissions and extreme slippage can exceed planned losses. The size calculation reserves spread/deviation but does not know future commission tiers. Validate costs in the tester and demo history.
4. The current baseline tracks initial or continuously observed UTC period equity. If the EA misses a UTC day/week boundary, it blocks new entries rather than inventing a midnight equity value. A safe period-baseline reconciliation workflow is described below.
5. Netting accounts combine positions by symbol. The engine prevents an entry when any current position or pending order shares that instrument and manages only its own magic. Manual trades can subsequently alter a netting position; use a dedicated demo account with no other trading. Mixed ownership requires manual review.
6. Whole-account snapshots include external/manual positions, but liquidation only targets bot-owned positions/orders. Deal-history restart replay covers the last seven days. Back up and preserve the database for older history.
7. Exit-deal timestamps exported via events currently use the observation/replay UTC time, not a historically DST-correct broker timestamp. Position opening timestamps apply the current broker offset. Do not use historical replay time for precise session attribution without validating broker timezone history.
8. Synchronous `OrderSend` round-trip time is reported as submission and broker-response timing. It is not an independently measured broker matching or network latency. Tick runtime is measured internally. No guaranteed speed claim is made.
9. There is no live-account override, paid AI call, DLL, remote WebRequest, real-time free-news guarantee, or remote push-delivery promise.

## Setup on Windows

1. Install the broker's MT5 terminal and log into a **demo** account in the terminal itself. Never put the XM password into this application's files or chat.
2. Run the project's Windows setup. It creates the shared secret and selects the actual MT5 Common Files directory. MT5 `File → Open Data Folder` is the terminal-specific folder; the bridge lives under the separate shared `Terminal\Common\Files\XMUltraAI` directory.
3. Copy this source into the terminal's `MQL5\Experts` folder. Open it in MetaEditor and compile. Record and resolve every compiler error; do not assume cloud-side static checks compiled MQL.
4. Attach one compiled EA to a demo chart. Allow algorithmic trading in the terminal and EA. DLL imports and WebRequest allowlists are unnecessary.
5. Leave `InpSymbols` empty to use that chart's exact broker instrument, or enter exact comma-separated symbols. Keep the small instrument set appropriate for the owner's low-memory Windows machine.
6. Start the local backend, log into the dashboard, verify `demo`, balances, broker connection and timestamp, then explicitly request `Start demo`. Missing calendar/history/permissions blocks start or entries and is shown in the reason.
7. Keep the PC awake and MT5 running. Restart or backend interruption pauses new entries. The owner must explicitly resume. The broker's server-side stops remain active while the terminal is offline, subject to broker execution conditions.

## Strategy Tester

Use a separate test run, historical tick data with documented provenance, realistic spread/commission and out-of-sample periods. Set `InpTesterAutoStart=true` **only in the MT5 Strategy Tester**. Because MT5 calendar APIs are not available in the tester, set `InpNewsGuard=false` for that offline test and clearly label that omission. The tester branch does not write shared live/demo bridge telemetry, so simulations cannot masquerade as actual broker executions. Test all supported fill/account modes and lot/stop edge cases. No strategy gets promoted merely because one test is profitable.

## Risk-state reconciliation

The terminal global variables use an `XMU` account/server/magic prefix. Do not delete them to evade losses. If a missed UTC boundary, damaged state or uncertain order blocks operation, first pause and inspect actual MT5 positions, pending orders and account history. Export the relevant history and state before making changes. The delivered release intentionally has no dashboard button to wipe a risk stop. Begin a new isolated demo evaluation/account when an accurate historical baseline cannot be reconstructed; document the new evaluation rather than representing it as continuous performance. Future releases can add an independently reviewed baseline-import workflow.

## Required Windows validation

- Compile with current broker MetaEditor; record build number and warnings.
- Strategy Tester runs for entry/exit direction, stop/freeze distance, volume floors, spread spikes, gapped stops and minimum-lot rejection.
- Demo check of exact instruments, account currency conversion, fill policy, trade permissions, partial fills and symbol sessions.
- Kill/restart backend and terminal: verify new entries stop and positions reconcile.
- Double-submit a command and an expired command: verify no repeated entry/control action.
- Emergency stop while trading: verify no new entries; liquidation remains separately requested.
- Loss-limit boundaries including floating losses and external positions; UTC rollover with and without downtime.
- Disconnection and order-timeout simulation: verify no blind retry.
- Compare every displayed trade to actual broker history, including commission/swap and rejected orders.

Run a sustained demo observation period and independent review before considering a separate live-capable implementation.
