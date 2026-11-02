"""Synthetic inputs test causality, validation, and explicit simulation reporting."""
import numpy as np
import pytest
from xmtrader.research.data import from_records
from xmtrader.research.strategies import REGISTRY, Strategy
from xmtrader.research.backtest import Costs, InstrumentModel, run_backtest
from xmtrader.research.metrics import performance_metrics
from xmtrader.research.ml import build_dataset, temporal_partitions


def bars(count=240):
    c=1.1+np.sin(np.arange(count)/9)*.008+np.arange(count)*.00001
    return from_records([{'timestamp':1700000100+i*60,'open':v,'close':v,'high':v+.001,'low':v-.001} for i,v in enumerate(c)], symbol='TEST',timeframe_seconds=60,source='synthetic test fixture',simulated=True)


@pytest.mark.parametrize('strategy', REGISTRY)
def test_future_data_cannot_change_existing_signals(strategy):
    full=bars()
    a=Strategy(strategy,full)
    b=Strategy(strategy,full.slice(0,180))
    for i in range(100,180):
        assert a.signal(i)==b.signal(i)


def test_backtest_labels_simulation_and_uses_explicit_costs():
    result=run_backtest(bars(),'trend',instrument=InstrumentModel(100000,1000,'TEST'),costs=Costs(.0002,.0001,3))
    assert result['simulated'] and result['input_is_synthetic']
    assert result['assumptions']['entry_timing']=='next bar open'
    assert np.isfinite(result['final_equity'])
    assert result['assumptions']['costs']['commission_per_lot_per_side']==3


def test_invalid_ohlc_and_missing_results():
    with pytest.raises(ValueError):
        from_records([{'timestamp':1,'open':2,'high':1,'low':1,'close':2}],symbol='TEST',timeframe_seconds=60,source='synthetic')
    assert performance_metrics([])['net_profit'] is None
    with pytest.raises(ValueError):
        Costs(-1,0,0)


def test_ml_labels_do_not_cross_temporal_partitions():
    data=build_dataset(bars(500),horizon=5)
    splits=temporal_partitions(data)
    assert max(splits['train']) < min(splits['calibration'])
    assert max(splits['calibration']) < min(splits['test'])
