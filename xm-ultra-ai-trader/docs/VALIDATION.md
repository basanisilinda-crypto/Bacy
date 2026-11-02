> Update: account-bound live-mode source support was added after the original demo baseline. See [live-mode notes](LIVE_MODE.md). Defaults remain demo; no live broker validation has been performed. Older demo-specific procedures remain the recommended validation path.

# Validation record and Windows acceptance

## Completed in the development workspace

- 102 pytest tests passed, plus 16 risk subtests.
- Python lint passed for application, scripts and tests.
- JavaScript syntax check passed.
- Chromium acceptance against a real disposable local FastAPI server: login, authenticated navigation, unavailable account values, disabled entry control, grounded typed assistant, desktop/mobile rendering and no JavaScript errors.
- Synthetic research tests cover ten strategy-prefix causality checks, explicit-cost simulation labeling, invalid OHLC and temporal model partitions. Synthetic observations never enter broker history.
- Authentication, CSRF/origin/host validation, idempotency, stale telemetry, command acknowledgment, risk limits, uncertain order states, WAL backups, corrupt restoration and process locking are covered by tests.

Commands: `python -m pytest -q`; `python -m ruff check xmtrader scripts tests`; `node --check web/app.js`. Install the developer/research extras first. The browser test requires Chromium and Playwright. Two dependency deprecation warnings remain in the cloud runtime (pytest plugin and Starlette/httpx); they are not failed tests.

These tests establish only the behavior exercised in this environment. No MetaEditor compiler, Windows desktop, XM account, physical Android device or Celeron performance benchmark was available. No real broker orders were submitted. Screenshots in artifacts show the genuine disconnected state, not invented trading results.

## Required before supervised demo execution

1. Compile the EA in current MetaEditor. Save compiler errors/warnings and resolve them. Do not enable it if compilation fails.
2. Compare Python/EA strategy behavior on identical exported data; these are independent implementations, not established equivalents.
3. Run MT5 Strategy Tester with documented data, realistic costs and several out-of-sample periods. Follow mt5/README.md for its explicit tester-only start/calendar settings. Label all outputs as simulations.
4. Check account/server identity, exact suffixes, tick values, lot steps, minimum lots, margin, fill modes, stops/freeze levels and account currency against the actual demo terminal.
5. Exercise loss limits including floating losses, pending orders, external positions, gaps, consecutive losses and calendar failure. Verify every rejection reason and ensure no order lacks a protective stop.
6. Disconnect/reconnect network, stop/restart Python and MT5, miss a day/week boundary, corrupt disposable state, duplicate requests and simulate uncertain results. New entries must block; unknown outcomes must not retry blindly.
7. Test emergency stop separately from owned-position liquidation. Verify command acknowledgment and actual broker positions; an in-flight submission or unreachable terminal may prevent instant stopping.
8. Reconcile each event and all costs against MT5 History. Replay timestamps and entry-side commissions currently limit historical daily/session and complete-net analytics; preserve broker exports.
9. Check Android HTTPS trust, login, stale/offline states, installation and voice availability on the actual device. Notifications require a reachable, open dashboard.
10. Observe CPU, RAM, disk and actual order latency on the target Windows computer. Start with one instrument; pause trading during research/model training. No sustained-soak or throughput claim is made.

No test result authorizes live trading. The delivered EA rejects real accounts unless explicitly paired locally. Live source support has not been compiled or broker-tested.
