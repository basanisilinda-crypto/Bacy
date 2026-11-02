"""Bar-based simulation with next-bar orders and explicit instrument/cost assumptions.

This is not a tick simulator or evidence of achievable fills. Intrabar ambiguity is
resolved stop-first. Broker-specific conversion, funding and execution require MT5
Strategy Tester and demo verification.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import math


from .data import Bars
from .metrics import performance_metrics, grouped_performance, period_reports, session_name
from .strategies import Strategy, StrategyConfig, Signal


@dataclass(frozen=True)
class InstrumentModel:
    price_value_per_lot: float
    margin_per_lot: float
    account_currency: str
    volume_min: float = .01
    volume_step: float = .01
    volume_max: float = 100.0

    def __post_init__(self):
        values = (self.price_value_per_lot, self.margin_per_lot, self.volume_min, self.volume_step, self.volume_max)
        if not all(math.isfinite(x) and x > 0 for x in values) or self.volume_min > self.volume_max or not self.account_currency:
            raise ValueError("Explicit positive instrument conversion/margin/volume assumptions required")


@dataclass(frozen=True)
class Costs:
    spread_price: float
    slippage_price: float
    commission_per_lot_per_side: float
    swap_per_lot_per_day: float = 0.0  # Signed; negative is a charge.

    def __post_init__(self):
        if not all(math.isfinite(v) for v in asdict(self).values()):
            raise ValueError("Costs must be finite")
        if min(self.spread_price, self.slippage_price, self.commission_per_lot_per_side) < 0:
            raise ValueError("Spread, adverse slippage and commission cost cannot be negative")


@dataclass(frozen=True)
class BacktestConfig:
    initial_equity: float = 10000.0
    risk_per_trade_pct: float = .25
    daily_loss_pct: float = 1.0
    weekly_loss_pct: float = 3.0
    max_drawdown_pct: float = 5.0
    max_consecutive_losses: int = 3
    cooldown_bars: int = 48
    max_gap_bars: int = 4
    liquidate_on_risk_breach: bool = False

    def __post_init__(self):
        limits = ((self.risk_per_trade_pct, .25), (self.daily_loss_pct, 1), (self.weekly_loss_pct, 3), (self.max_drawdown_pct, 5))
        if not math.isfinite(self.initial_equity) or self.initial_equity <= 0 or any(not math.isfinite(v) or not 0 < v <= maximum for v, maximum in limits):
            raise ValueError("Backtest limits must be positive and cannot exceed conservative release bounds")
        if type(self.max_consecutive_losses) is not int or not 1 <= self.max_consecutive_losses <= 3 or type(self.cooldown_bars) is not int or self.cooldown_bars < 1 or self.max_gap_bars < 1:
            raise ValueError("Invalid cooldown or gap limit")


@dataclass
class _Position:
    side: int
    volume: float
    entry: float
    stop: float
    target: float
    opened_at: float
    entry_index: int
    signal: Signal
    entry_commission: float
    funding_charged: float = 0.0


def run_backtest(bars: Bars, strategy_id: str, *, instrument: InstrumentModel, costs: Costs,
                 config: BacktestConfig | None = None, strategy_config: StrategyConfig | None = None,
                 trade_start_index: int = 0, strategy: Strategy | None = None) -> dict:
    """Research-only execution; supplied bars are mid prices and timestamps are OPEN.

    `trade_start_index` permits causal warm-up before a walk-forward test interval.
    A strategy injected for tests must have signal(i) and should_exit(i, side, entry).
    The final bar is marked to market and force-closed with adverse costs.
    """
    cfg = config or BacktestConfig()
    if not 0 <= trade_start_index < len(bars):
        raise ValueError("Invalid first tradable bar")
    strat = strategy or Strategy(strategy_id, bars, strategy_config)
    balance = cfg.initial_equity
    position: _Position | None = None
    pending_signal: Signal | None = None
    pending_exit = False
    trades, curve, decisions, risk_events = [], [], [], []
    peak = daily_start = weekly_start = balance
    day_key = week_key = None
    losses = 0
    cooldown_until = -1
    drawdown_latched = False

    def liquidation_price(mid: float, side: int) -> float:
        return mid - side * (costs.spread_price / 2 + costs.slippage_price)

    def mark_equity(mid: float) -> float:
        if position is None:
            return balance
        return balance + position.side * (liquidation_price(mid, position.side) - position.entry) * position.volume * instrument.price_value_per_lot - position.volume * costs.commission_per_lot_per_side

    def finish(price: float, stamp: float, reason: str, i: int) -> None:
        nonlocal position, balance, losses, cooldown_until
        assert position is not None
        p = position
        gross = p.side * (price - p.entry) * p.volume * instrument.price_value_per_lot
        exit_fee = p.volume * costs.commission_per_lot_per_side
        balance += gross - exit_fee
        commission = -(p.entry_commission + exit_fee)
        net = gross + commission + p.funding_charged
        spread_slippage_cost = p.volume * instrument.price_value_per_lot * (costs.spread_price + 2 * costs.slippage_price)
        trades.append({"simulated": True, "symbol": bars.symbol, "strategy": strategy_id, "side": "buy" if p.side > 0 else "sell",
                       "volume": p.volume, "entry_price": p.entry, "exit_price": price, "sl": p.stop, "tp": p.target,
                       "opened_at": p.opened_at, "closed_at": stamp, "duration_seconds": max(0, stamp - p.opened_at),
                       "entry_index": p.entry_index, "exit_index": i, "signal_index": p.signal.index,
                       "profit": gross, "commission": commission, "swap": p.funding_charged, "net_profit": net,
                       "spread_slippage_cost": spread_slippage_cost, "reason": reason, "explanation": p.signal.explanation,
                       "session": session_name(p.opened_at)})
        losses = losses + 1 if net < 0 else 0
        if losses >= cfg.max_consecutive_losses:
            cooldown_until = i + cfg.cooldown_bars
            losses = 0
            risk_events.append({"bar": i, "reason": "consecutive_loss_cooldown", "until_bar": cooldown_until})
        position = None

    for i in range(trade_start_index, len(bars)):
        stamp = float(bars.timestamp[i])
        date = datetime.fromtimestamp(stamp, timezone.utc)
        new_day, new_week = date.date().isoformat(), date.isocalendar()[:2]
        open_equity = mark_equity(float(bars.open[i]))
        if new_day != day_key:
            day_key, daily_start = new_day, open_equity
        if new_week != week_key:
            week_key, weekly_start = new_week, open_equity
        peak = max(peak, open_equity)

        if position is not None:
            p = position
            # Gap loss can exceed the requested stop risk; no fill-at-stop fiction.
            market_open = liquidation_price(float(bars.open[i]), p.side)
            if p.side * (market_open - p.stop) <= 0:
                finish(market_open, stamp, "gap_stop", i)
            elif p.side * (market_open - p.target) >= 0:
                # Do not assume price improvement for a limit target.
                finish(p.target, stamp, "gap_target", i)
            elif pending_exit:
                finish(market_open, stamp, "strategy_exit", i)
        pending_exit = False

        if pending_signal is not None and position is None:
            signal = pending_signal
            equity = balance
            reason = None
            if i != signal.index + 1 or stamp - float(bars.timestamp[signal.index]) > bars.timeframe_seconds * cfg.max_gap_bars:
                reason = "stale_signal_or_market_gap"
            elif drawdown_latched or (peak - equity) / peak * 100 >= cfg.max_drawdown_pct:
                reason, drawdown_latched = "maximum_drawdown", True
            elif daily_start <= 0 or equity <= 0 or (daily_start - equity) / daily_start * 100 >= cfg.daily_loss_pct:
                reason = "daily_loss_limit"
            elif weekly_start <= 0 or (weekly_start - equity) / weekly_start * 100 >= cfg.weekly_loss_pct:
                reason = "weekly_loss_limit"
            elif i < cooldown_until:
                reason = "consecutive_loss_cooldown"
            risk_cash = max(0.0, equity * min(cfg.risk_per_trade_pct, signal.risk_pct) / 100)
            budget = min(risk_cash, max(0, equity - daily_start * (1 - cfg.daily_loss_pct / 100)),
                         max(0, equity - weekly_start * (1 - cfg.weekly_loss_pct / 100)),
                         max(0, equity - peak * (1 - cfg.max_drawdown_pct / 100)))
            per_lot_loss = (signal.stop_distance + costs.slippage_price) * instrument.price_value_per_lot + 2 * costs.commission_per_lot_per_side
            if not math.isfinite(per_lot_loss) or per_lot_loss <= 0 or signal.stop_distance <= 0 or signal.side not in {-1, 1}:
                reason = "invalid_protective_stop"
                volume = 0.0
            else:
                volume = math.floor(min(budget / per_lot_loss, instrument.volume_max, max(0, equity) / instrument.margin_per_lot) / instrument.volume_step + 1e-12) * instrument.volume_step
            if volume < instrument.volume_min:
                reason = reason or "minimum_lot_exceeds_risk_or_margin"
            entry = float(bars.open[i]) + signal.side * (costs.spread_price / 2 + costs.slippage_price)
            stop = entry - signal.side * signal.stop_distance
            target = entry + signal.side * signal.stop_distance * signal.reward_risk
            if min(entry, stop, target) <= 0:
                reason = "invalid_protective_prices"
            if reason:
                decisions.append({"bar": i, "accepted": False, "reason": reason})
            else:
                fee = volume * costs.commission_per_lot_per_side
                if volume * instrument.margin_per_lot + fee > equity:
                    decisions.append({"bar": i, "accepted": False, "reason": "insufficient_margin_after_fee"})
                else:
                    balance -= fee
                    position = _Position(signal.side, volume, entry, stop, target, stamp, i, signal, fee)
                    decisions.append({"bar": i, "accepted": True, "signal_bar": signal.index, "risk_cash": budget})
        pending_signal = None

        if position is not None:
            p = position
            # Model constant signed funding pro-rata, not a broker rollover calendar.
            funding = p.volume * costs.swap_per_lot_per_day * bars.timeframe_seconds / 86400
            p.funding_charged += funding
            balance += funding
            bid_low = float(bars.low[i]) - costs.spread_price / 2
            bid_high = float(bars.high[i]) - costs.spread_price / 2
            ask_low = float(bars.low[i]) + costs.spread_price / 2
            ask_high = float(bars.high[i]) + costs.spread_price / 2
            stop_hit = bid_low <= p.stop if p.side > 0 else ask_high >= p.stop
            target_hit = bid_high >= p.target if p.side > 0 else ask_low <= p.target
            if stop_hit:
                finish(p.stop - p.side * costs.slippage_price, stamp + bars.timeframe_seconds, "stop_first_ambiguous" if target_hit else "stop", i)
            elif target_hit:
                finish(p.target, stamp + bars.timeframe_seconds, "target", i)

        equity = mark_equity(float(bars.close[i]))
        peak = max(peak, equity)
        breach = None
        if equity <= 0 or (peak - equity) / peak * 100 >= cfg.max_drawdown_pct:
            breach, drawdown_latched = "maximum_drawdown", True
        elif daily_start > 0 and (daily_start - equity) / daily_start * 100 >= cfg.daily_loss_pct:
            breach = "daily_loss_limit"
        elif weekly_start > 0 and (weekly_start - equity) / weekly_start * 100 >= cfg.weekly_loss_pct:
            breach = "weekly_loss_limit"
        if breach:
            if not risk_events or risk_events[-1].get("reason") != breach:
                risk_events.append({"bar": i, "reason": breach, "equity": equity})
            if position is not None and cfg.liquidate_on_risk_breach:
                # Observed at bar close, liquidation is a next-open instruction.
                pending_exit = True
        if position is not None:
            pending_exit = pending_exit or strat.should_exit(i, position.side, position.entry_index)
        elif not breach and i < len(bars) - 1:
            pending_signal = strat.signal(i)
        curve.append({"timestamp": stamp + bars.timeframe_seconds, "balance": balance, "equity": equity})

    if position is not None:
        finish(liquidation_price(float(bars.close[-1]), position.side), float(bars.timestamp[-1] + bars.timeframe_seconds), "end_of_sample", len(bars) - 1)
        curve[-1]["balance"] = curve[-1]["equity"] = balance
    metrics = performance_metrics(trades, [cfg.initial_equity] + [row["equity"] for row in curve])
    return {"simulated": True, "source": bars.source, "input_is_synthetic": bars.simulated,
            "symbol": bars.symbol, "strategy": strategy_id, "account_currency": instrument.account_currency,
            "initial_equity": cfg.initial_equity, "final_equity": balance, "trades": trades, "equity_curve": curve,
            "metrics": metrics, "by_strategy": grouped_performance(trades, "strategy"), "by_symbol": grouped_performance(trades, "symbol"),
            "by_session": grouped_performance(trades, "session"), "period_reports": period_reports(trades),
            "decisions": decisions, "risk_events": risk_events, "data_gaps": bars.gaps,
            "assumptions": {"bar_prices": "mid", "entry_timing": "next bar open", "intrabar_collision": "stop first",
                            "instrument": asdict(instrument), "costs": asdict(costs), "risk": asdict(cfg),
                            "funding": "constant pro-rata, not actual XM rollover", "conversion": "constant account-currency value supplied by researcher"},
            "limitations": ["Not a tick or liquidity simulation; no guaranteed execution latency or fills.",
                            "One symbol and one position per simulation. Portfolio tests require synchronized multi-asset testing in MT5.",
                            "No intrabar equity sampling: equity limits are checked on bar opens/closes; protective stops are checked against high/low.",
                            "Historical profitability is not evidence of future profitability. Nothing is promoted to live trading."]}
