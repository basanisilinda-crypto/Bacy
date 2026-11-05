"""Reference calculation for recovering equity baselines from verified flat-account history.

No orders are sent. MT5 remains the runtime authority; native implementation needs
Windows acceptance. Never use balance-only arithmetic with boundary exposure.
"""
import math


class BaselineUnavailable(ValueError):
    pass


def reconcile_flat_period(*, equity, balance, credit, period_start, now, deals,
                          history_from, history_to, open_positions=0, pending_orders=0,
                          boundary_guard=10800):
    values=(equity,balance,credit,period_start,now,history_from,history_to,boundary_guard)
    if any(not math.isfinite(v) for v in values) or equity<=0 or boundary_guard<0:
        raise BaselineUnavailable('Invalid account or history input')
    if open_positions or pending_orders or abs(equity-balance-credit)>.01:
        raise BaselineUnavailable('Account must be flat, with no pending orders')
    if now<period_start+boundary_guard or history_from>period_start-boundary_guard or history_to<now:
        raise BaselineUnavailable('Verified history does not cover the guarded period')
    net=0.0
    volumes={}
    for row in deals:
        stamp=row['time']
        change=row['equity_change']
        if not math.isfinite(stamp) or not math.isfinite(change) or not history_from<=stamp<=history_to:
            raise BaselineUnavailable('Invalid history row')
        if abs(stamp-period_start)<=boundary_guard:
            raise BaselineUnavailable('Activity near boundary needs historical timezone/equity review')
        if stamp<period_start:
            continue
        if row['kind'] not in {'buy','sell','cash'}:
            raise BaselineUnavailable('Unsupported correction or cancelled deal')
        net+=change
        if row['kind'] in {'buy','sell'}:
            volume=row['volume']
            key=row['position_id']
            if not key or not math.isfinite(volume) or volume<=0:
                raise BaselineUnavailable('Missing position audit details')
            volumes[key]=volumes.get(key,0)+volume*(1 if row['kind']=='buy' else -1)
    if any(abs(v)>1e-8 for v in volumes.values()):
        raise BaselineUnavailable('A position crossed the period boundary')
    anchor=equity-net
    if not math.isfinite(anchor) or anchor<=0:
        raise BaselineUnavailable('Reconstructed baseline is invalid')
    return anchor
