"""Explicit-cost, offline CSV backtesting: python -m xmtrader.research --help."""
import argparse
import json
from pathlib import Path
from .data import load_csv
from .backtest import Costs, InstrumentModel, run_backtest
from .strategies import REGISTRY


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('csv', type=Path)
    p.add_argument('--symbol', required=True)
    p.add_argument('--timeframe-seconds', type=int, required=True)
    p.add_argument('--source', required=True)
    p.add_argument('--synthetic', action='store_true')
    p.add_argument('--strategy', choices=REGISTRY, default='ema_cross')
    p.add_argument('--price-value-per-lot', type=float, required=True)
    p.add_argument('--margin-per-lot', type=float, required=True)
    p.add_argument('--currency', required=True)
    p.add_argument('--spread-price', type=float, required=True)
    p.add_argument('--slippage-price', type=float, required=True)
    p.add_argument('--commission-per-lot-per-side', type=float, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    bars = load_csv(a.csv, symbol=a.symbol, timeframe_seconds=a.timeframe_seconds, source=a.source, simulated=a.synthetic)
    result = run_backtest(bars, a.strategy, instrument=InstrumentModel(a.price_value_per_lot, a.margin_per_lot, a.currency),
        costs=Costs(a.spread_price, a.slippage_price, a.commission_per_lot_per_side))
    with a.output.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, indent=2, allow_nan=False)
    print('Simulation report saved. No broker orders were submitted.')


if __name__ == '__main__':
    main()
