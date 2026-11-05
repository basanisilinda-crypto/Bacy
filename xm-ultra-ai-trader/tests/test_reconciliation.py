import pytest
from xmtrader.reconciliation import BaselineUnavailable, reconcile_flat_period


def calculate(**changes):
    inputs=dict(equity=9800,balance=9800,credit=0,period_start=20000,now=70000,
                history_from=0,history_to=70000,deals=[
                    dict(time=40000,equity_change=-2,kind='buy',volume=1,position_id=11),
                    dict(time=50000,equity_change=-198,kind='sell',volume=1,position_id=11)])
    inputs.update(changes)
    return reconcile_flat_period(**inputs)


def test_reconstruction_preserves_period_losses_and_costs():
    assert calculate()==10000


def test_cash_movements_are_removed_from_starting_equity():
    assert calculate(equity=10800,balance=10800,deals=[dict(time=40000,equity_change=1000,kind='cash')])==9800


def test_boundary_position_cannot_be_reconstructed_as_balance():
    with pytest.raises(BaselineUnavailable,match='crossed'):
        calculate(deals=[dict(time=50000,equity_change=-198,kind='sell',volume=1,position_id=11)])


@pytest.mark.parametrize('changes',[
    dict(open_positions=1),dict(pending_orders=1),dict(equity=9700),
    dict(history_from=20000),dict(history_to=60000),dict(now=22000),
    dict(deals=[dict(time=21000,equity_change=-1,kind='cash')]),
    dict(deals=[dict(time=40000,equity_change=1,kind='cancelled')]),
])
def test_unverifiable_inputs_fail_closed(changes):
    with pytest.raises(BaselineUnavailable):
        calculate(**changes)
