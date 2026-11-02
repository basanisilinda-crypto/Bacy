# Offline research

The research package consumes explicit OHLC CSV data; it does not download paid or free feeds automatically. Header: `timestamp,open,high,low,close,volume`. Use Unix UTC seconds or ISO timestamps with offsets. Convert broker-server timestamps correctly before importing. Each row identifies the bar's opening time. Supply data provenance and the actual symbol/timeframe. Synthetic data must be marked with `--synthetic`.

Run `python -m xmtrader.research --help` for the command interface. Required inputs include account-currency price value per lot, margin per lot, spread in price units, adverse slippage, and commission per lot per side. These are explicit assumptions, not broker-discovered conversions. The output is a JSON simulation report created in a new file; existing output files are not overwritten.

`research.strategies.REGISTRY` documents entry/exit conditions and supported regimes. The strategy engine uses completed bars and the simulator enters at the next bar opening. Stops win ambiguous same-bar stop/target collisions. No bar simulator can reproduce tick liquidity, real fills or intrabar equity exactly. The simulator handles one instrument/position at a time; use the MT5 tester for account-specific and portfolio tests.

Use `research.validation.walk_forward` and `sensitivity_analysis` for separated periods and parameter comparisons. `research.ml` provides explicitly optional scikit-learn classifiers, regression and anomaly models with temporal separation. Keep the final holdout untouched during optimization. Outputs do not activate an EA strategy or bypass independent risk checks. Research rules and MQL rules must be compared on identical exported data before claiming numerical equivalence.

Historical profitability never proves future returns. No supplied strategy or model has been validated as profitable, and no automatic promotion to live execution is available.
