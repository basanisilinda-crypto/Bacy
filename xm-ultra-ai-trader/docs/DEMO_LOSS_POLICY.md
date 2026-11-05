# Demo loss-stop option — version 1.002

This release defaults `InpDemoIgnoreLossStops` to `true`. On an actual MT5 DEMO account only, daily loss, weekly loss, high-water drawdown, missed-baseline suspension and consecutive-loss cooldown no longer reject Start or reduce the entry budget. It can exhaust the demo balance. Set the input to `false` to restore those suspensions.

Account type comes from MT5, not the dashboard selector. REAL accounts and the Strategy Tester retain their loss controls. Anchors, recorded losses, deal history and cooldown timestamps are retained. Corrupt or incomplete persisted state still requires repair; initialization does not silently discard it.

Per-trade planned stop risk remains capped at 0.25% of current equity, aggregate protected risk at 0.5%, and simultaneous account-wide positions/orders at two. Stop, positive equity/free margin, protective stops, spread limits, broker permissions, news availability, strategy signals, authenticated commands, fresh quotes, the service lease and uncertain-order reconciliation still apply. No fill deadline or profitability is promised.

The activity stream records demo_loss_policy when active. No simulated scans or trades are introduced.

## Update on Windows

1. Click Stop. In MT5, right-click the chart, open Expert List, select the robot and Remove. Do not delete its files or global variables.
2. Extract the new ZIP into a new folder and double-click Start Here.cmd. Let it update the EA source. If compilation is not confirmed, open the installed XMUltraAITrader.mq5 in MetaEditor and click Compile.
3. Attach version 1.002 to the same demo account. Verify InpDemoIgnoreLossStops is true in Inputs. Keep existing account pairing and bridge settings.
4. Keep the service running, enable MT5 Algo Trading, wait for verified fresh dashboard data, then click Start Demo Trading once.

Automated checks execute the actual portable native policy functions and account gate with platform stubs, including live/tester separation, operational failures and unchanged entry budgets. Complete MetaEditor compilation and broker acceptance remain unverified in this Linux development environment.
