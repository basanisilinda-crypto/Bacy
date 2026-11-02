"""Ten transparent, causal research strategies; none is a validated money maker."""
from __future__ import annotations

from dataclasses import dataclass, asdict
from datetime import datetime, timezone

import numpy as np

from .data import Bars, aggregate_completed
from . import indicators as ta


@dataclass(frozen=True)
class StrategyConfig:
    fast: int = 10
    slow: int = 30
    lookback: int = 20
    atr_period: int = 14
    stop_atr: float = 2.0
    reward_risk: float = 1.5
    max_hold_bars: int = 48
    max_risk_pct: float = .25
    session_start_utc: int = 7
    session_end_utc: int = 20
    higher_factor: int = 4
    min_adx: float = 25.0

    def __post_init__(self):
        ints = (self.fast, self.slow, self.lookback, self.atr_period, self.max_hold_bars, self.higher_factor)
        if any(type(value) is not int or value < 1 for value in ints):
            raise ValueError("Strategy periods must be positive integers")
        if self.fast >= self.slow or self.higher_factor < 2:
            raise ValueError("fast must be below slow and higher_factor >=2")
        if not 0 < self.max_risk_pct <= .25 or not .5 <= self.stop_atr <= 10 or not .25 <= self.reward_risk <= 5:
            raise ValueError("Risk/stop/reward parameters outside research safety boundaries")
        if not 0 <= self.session_start_utc < self.session_end_utc <= 24 or not 5 <= self.min_adx <= 60:
            raise ValueError("Invalid session or trend threshold")


@dataclass(frozen=True)
class StrategyDefinition:
    id: str
    name: str
    supported_conditions: str
    entry_rule: str
    exit_rule: str
    description: str
    validated: bool = False
    live_enabled: bool = False


REGISTRY: dict[str, StrategyDefinition] = {
    item.id: item for item in [
        StrategyDefinition("ema_cross", "EMA crossover", "Emerging trends", "Fast EMA crosses slow EMA on a completed bar", "Opposite crossover, stop, target or time limit", "A fast price average has crossed a slower average."),
        StrategyDefinition("trend", "Trend following", "ADX trend strength above threshold", "Price returns through fast EMA in slow-EMA trend direction", "Close crosses slow EMA, stop, target or time limit", "A pullback has recovered in an established trend."),
        StrategyDefinition("momentum", "Momentum continuation", "Directional market with strengthening momentum", "RSI crosses 55/45 with matching MACD histogram and fast/slow EMA order", "RSI crosses neutral against position, stop, target or time limit", "Several causal momentum measures agree on direction."),
        StrategyDefinition("breakout", "Price breakout", "Expansion beyond recent range", "Close exceeds previous lookback high/low", "Close returns inside broken range, stop, target or time limit", "Price closed outside the earlier trading range."),
        StrategyDefinition("mean_reversion", "Mean reversion", "ADX below 20, range market", "Price returns inside Bollinger band after RSI extreme", "Price reaches middle band, stop, target or time limit", "Price has returned inside its range after an extreme move."),
        StrategyDefinition("support_resistance", "Support/resistance reaction", "Range or weak trend", "False breakout beyond prior range with rejection close and matching candle direction", "Price reaches range midpoint, stop, target or time limit", "Price tested a previous boundary but could not stay beyond it."),
        StrategyDefinition("volatility", "Volatility expansion", "Range expansion", "Range exceeds 1.5 previous ATR and closes beyond previous range", "Close returns through fast EMA, stop, target or time limit", "A larger-than-usual move broke the previous price range."),
        StrategyDefinition("session", "Session-aware crossover", "Configured fixed UTC hours", "EMA crossover inside configured UTC entry hours", "Session ends, opposite crossover, stop, target or time limit", "A crossover occurred during the configured trading hours."),
        StrategyDefinition("multi_timeframe", "Multiple-timeframe confirmation", "Aligned lower and completed higher-timeframe trend", "Lower EMA crossover agrees with completed higher EMA order", "Lower opposite crossover, stop, target or time limit", "A lower-timeframe crossover agrees with the last finished higher-timeframe trend."),
        StrategyDefinition("regime", "Regime-adaptive research", "Trend or range, excluding transition", "Trend pullback when ADX>=threshold; mean reversion when ADX<20", "Selected module invalidation, regime transition, stop, target or time limit", "A transparent regime rule selected either the trend or range strategy."),
    ]
}


@dataclass(frozen=True)
class Signal:
    index: int
    timestamp: float
    strategy: str
    side: int  # +1 buy, -1 sell; absence of signal is None
    reference_price: float
    stop_distance: float
    reward_risk: float
    risk_pct: float
    max_hold_bars: int
    explanation: str
    evidence: tuple[str, ...] = ()
    score_is_probability: bool = False


class Strategy:
    """Pre-computes causal arrays once; signals use bar i and earlier bars only."""
    def __init__(self, strategy_id: str, bars: Bars, config: StrategyConfig | None = None):
        if strategy_id not in REGISTRY:
            raise ValueError(f"Unknown strategy: {strategy_id}")
        self.definition, self.bars, self.config = REGISTRY[strategy_id], bars, config or StrategyConfig()
        cfg = self.config
        self.fast, self.slow = ta.ema(bars.close, cfg.fast), ta.ema(bars.close, cfg.slow)
        self.atr = ta.atr(bars, cfg.atr_period)
        self.rsi = ta.rsi(bars.close)
        self.adx, self.plus_di, self.minus_di = ta.adx(bars)
        self.macd, self.macd_signal, self.histogram = ta.macd(bars.close)
        self.lower, self.middle, self.upper = ta.bollinger(bars.close, cfg.lookback)
        self.support, self.resistance = ta.support_resistance(bars, cfg.lookback)
        self.patterns = ta.candles(bars)
        self.higher_fast = self.higher_slow = self.higher_map = None
        if strategy_id == "multi_timeframe":
            try:
                higher, self.higher_map = aggregate_completed(bars, cfg.higher_factor)
                self.higher_fast, self.higher_slow = ta.ema(higher.close, cfg.fast), ta.ema(higher.close, cfg.slow)
            except ValueError:
                pass

    def _cross(self, i: int) -> int:
        if self.fast[i - 1] <= self.slow[i - 1] and self.fast[i] > self.slow[i]:
            return 1
        if self.fast[i - 1] >= self.slow[i - 1] and self.fast[i] < self.slow[i]:
            return -1
        return 0

    def _trend(self, i: int) -> int:
        b = self.bars
        if self.adx[i] < self.config.min_adx:
            return 0
        if self.fast[i] > self.slow[i] and b.close[i - 1] <= self.fast[i - 1] and b.close[i] > self.fast[i]:
            return 1
        if self.fast[i] < self.slow[i] and b.close[i - 1] >= self.fast[i - 1] and b.close[i] < self.fast[i]:
            return -1
        return 0

    def _mean_reversion(self, i: int) -> int:
        b = self.bars
        if self.adx[i] >= 20:
            return 0
        if b.close[i - 1] < self.lower[i - 1] and b.close[i] >= self.lower[i] and self.rsi[i - 1] < 35:
            return 1
        if b.close[i - 1] > self.upper[i - 1] and b.close[i] <= self.upper[i] and self.rsi[i - 1] > 65:
            return -1
        return 0

    def signal(self, i: int) -> Signal | None:
        b, cfg, sid = self.bars, self.config, self.definition.id
        if not 1 <= i < len(b):
            return None
        prerequisites = [self.fast[i - 1], self.slow[i - 1], self.atr[i], self.adx[i], self.rsi[i], self.support[i], self.resistance[i]]
        if not all(np.isfinite(v) for v in prerequisites) or self.atr[i] <= 0:
            return None
        side = 0
        if sid in {"ema_cross", "session", "multi_timeframe"}:
            side = self._cross(i)
            if sid == "session":
                hour = datetime.fromtimestamp(b.timestamp[i] + b.timeframe_seconds, timezone.utc).hour
                if not cfg.session_start_utc <= hour < cfg.session_end_utc:
                    side = 0
            if sid == "multi_timeframe":
                if self.higher_map is None:
                    side = 0
                else:
                    j = self.higher_map[i]
                    if j < 0 or not np.isfinite(self.higher_slow[j]) or side * (self.higher_fast[j] - self.higher_slow[j]) <= 0:
                        side = 0
        elif sid == "trend":
            side = self._trend(i)
        elif sid == "momentum":
            if self.rsi[i - 1] <= 55 < self.rsi[i] and self.histogram[i] > 0 and self.fast[i] > self.slow[i]:
                side = 1
            elif self.rsi[i - 1] >= 45 > self.rsi[i] and self.histogram[i] < 0 and self.fast[i] < self.slow[i]:
                side = -1
        elif sid in {"breakout", "volatility"}:
            side = 1 if b.close[i] > self.resistance[i] else -1 if b.close[i] < self.support[i] else 0
            if sid == "volatility" and b.high[i] - b.low[i] <= 1.5 * self.atr[i - 1]:
                side = 0
        elif sid == "mean_reversion":
            side = self._mean_reversion(i)
        elif sid == "support_resistance":
            if self.adx[i] < cfg.min_adx:
                if b.low[i] < self.support[i] < b.close[i] and b.close[i] > b.open[i]:
                    side = 1
                elif b.high[i] > self.resistance[i] > b.close[i] and b.close[i] < b.open[i]:
                    side = -1
        elif sid == "regime":
            side = self._trend(i) if self.adx[i] >= cfg.min_adx else self._mean_reversion(i) if self.adx[i] < 20 else 0
        if not side:
            return None
        return Signal(i, float(b.timestamp[i] + b.timeframe_seconds), sid, side, float(b.close[i]),
                      float(self.atr[i] * cfg.stop_atr), cfg.reward_risk, cfg.max_risk_pct, cfg.max_hold_bars,
                      self.definition.description,
                      (f"Completed bar: {i}", f"ADX: {self.adx[i]:.1f}", f"ATR stop distance: {self.atr[i] * cfg.stop_atr:.8g}"))

    def should_exit(self, i: int, side: int, entry_index: int) -> bool:
        b, sid, cfg = self.bars, self.definition.id, self.config
        if i - entry_index >= cfg.max_hold_bars:
            return True
        if sid in {"ema_cross", "multi_timeframe", "session"}:
            if sid == "session":
                hour = datetime.fromtimestamp(b.timestamp[i] + b.timeframe_seconds, timezone.utc).hour
                if not cfg.session_start_utc <= hour < cfg.session_end_utc:
                    return True
            return self._cross(i) == -side
        if sid == "momentum":
            return bool(side * (self.rsi[i] - 50) < 0)
        if sid == "breakout":
            level = self.resistance[max(0, entry_index - 1)] if side > 0 else self.support[max(0, entry_index - 1)]
            return bool(side * (b.close[i] - level) < 0)
        if sid in {"mean_reversion", "support_resistance"}:
            middle = self.middle[i] if sid == "mean_reversion" else (self.support[i] + self.resistance[i]) / 2
            return bool(side * (b.close[i] - middle) >= 0)
        if sid == "regime":
            return bool(20 <= self.adx[i] < cfg.min_adx or side * (b.close[i] - self.slow[i]) < 0)
        reference = self.fast[i] if sid == "volatility" else self.slow[i]
        return bool(side * (b.close[i] - reference) < 0)


@dataclass(frozen=True)
class DecisionContext:
    spread_price: float
    max_spread_price: float
    data_fresh: bool
    news_allowed: bool
    exposure_allowed: bool
    session_allowed: bool = True
    historical_evaluation_available: bool = False


def evaluate_signals(signals: list[Signal], context: DecisionContext) -> dict:
    """Advisory vetoes, never an execution/risk-approval endpoint or probability."""
    reasons = []
    if not np.isfinite(context.spread_price) or not np.isfinite(context.max_spread_price) or context.spread_price < 0 or context.max_spread_price <= 0 or context.spread_price > context.max_spread_price:
        reasons.append("Spread is unavailable or above the configured limit")
    for allowed, message in ((context.data_fresh, "Market data is stale"), (context.news_allowed, "News uncertainty blocks entries"),
                             (context.exposure_allowed, "Existing exposure blocks entries"), (context.session_allowed, "Outside approved trading hours")):
        if not allowed:
            reasons.append(message)
    sides = {signal.side for signal in signals}
    if len(sides) > 1:
        reasons.append("Strategies disagree on direction")
    if not signals:
        reasons.append("No eligible signal")
    return {"status": "rejected" if reasons else "research_candidate", "reasons": reasons,
            "signals": [asdict(s) for s in signals], "score_is_probability": False,
            "historical_evaluation_available": context.historical_evaluation_available,
            "execution_authorized": False, "explanation": "A research candidate still requires the independent MT5 risk gate and demo validation."}


def strategy_catalogue() -> list[dict]:
    return [{**asdict(item), "parameters": asdict(StrategyConfig()), "enabled": False} for item in REGISTRY.values()]
