"""Causal technical indicators. Warm-up values remain NaN rather than zero."""
from __future__ import annotations

import numpy as np

from .data import Bars


def _period(period: int) -> None:
    if not isinstance(period, int) or period < 1:
        raise ValueError("period must be a positive integer")


def sma(values, period: int = 20) -> np.ndarray:
    _period(period)
    a = np.asarray(values, dtype=float)
    result = np.full(a.shape, np.nan)
    for i in range(period - 1, len(a)):
        window = a[i - period + 1:i + 1]
        if np.all(np.isfinite(window)):
            result[i] = np.mean(window)
    return result


def rolling_std(values, period: int = 20) -> np.ndarray:
    _period(period)
    a = np.asarray(values, dtype=float)
    result = np.full(a.shape, np.nan)
    for i in range(period - 1, len(a)):
        window = a[i - period + 1:i + 1]
        if np.all(np.isfinite(window)):
            result[i] = np.std(window)
    return result


def ema(values, period: int = 20, *, alpha: float | None = None) -> np.ndarray:
    _period(period)
    a = np.asarray(values, dtype=float)
    result = np.full(a.shape, np.nan)
    weight = 2 / (period + 1) if alpha is None else alpha
    if not 0 < weight <= 1:
        raise ValueError("alpha must be in (0,1]")
    for i in range(period - 1, len(a)):
        if not np.isfinite(a[i]):
            continue
        if i > 0 and np.isfinite(result[i - 1]):
            result[i] = weight * a[i] + (1 - weight) * result[i - 1]
        elif np.all(np.isfinite(a[i - period + 1:i + 1])):
            result[i] = np.mean(a[i - period + 1:i + 1])
    return result


def rsi(values, period: int = 14) -> np.ndarray:
    a = np.asarray(values, dtype=float)
    change = np.r_[np.nan, np.diff(a)]
    gain = ema(np.maximum(change, 0), period, alpha=1 / period)
    loss = ema(np.maximum(-change, 0), period, alpha=1 / period)
    result = np.full(a.shape, np.nan)
    ready = np.isfinite(gain) & np.isfinite(loss)
    both_zero = ready & (gain == 0) & (loss == 0)
    result[both_zero] = 50
    result[ready & (loss == 0) & (gain > 0)] = 100
    mask = ready & (loss > 0)
    result[mask] = 100 - 100 / (1 + gain[mask] / loss[mask])
    return result


def true_range(bars: Bars) -> np.ndarray:
    prior = np.r_[bars.close[0], bars.close[:-1]]
    return np.maximum.reduce([bars.high - bars.low, np.abs(bars.high - prior), np.abs(bars.low - prior)])


def atr(bars: Bars, period: int = 14) -> np.ndarray:
    return ema(true_range(bars), period, alpha=1 / period)


def macd(values, fast: int = 12, slow: int = 26, signal: int = 9) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    if fast >= slow:
        raise ValueError("MACD fast must be below slow")
    line = ema(values, fast) - ema(values, slow)
    trigger = ema(line, signal)
    return line, trigger, line - trigger


def bollinger(values, period: int = 20, deviations: float = 2) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    if deviations <= 0:
        raise ValueError("deviations must be positive")
    middle, std = sma(values, period), rolling_std(values, period)
    return middle - deviations * std, middle, middle + deviations * std


def adx(bars: Bars, period: int = 14) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    up = np.r_[0, np.diff(bars.high)]
    down = np.r_[0, -np.diff(bars.low)]
    plus = np.where((up > down) & (up > 0), up, 0)
    minus = np.where((down > up) & (down > 0), down, 0)
    average_range = atr(bars, period)
    p = np.divide(100 * ema(plus, period, alpha=1 / period), average_range, out=np.zeros_like(up), where=average_range != 0)
    m = np.divide(100 * ema(minus, period, alpha=1 / period), average_range, out=np.zeros_like(up), where=average_range != 0)
    dx = np.divide(100 * np.abs(p - m), p + m, out=np.zeros_like(up), where=(p + m) != 0)
    return ema(dx, period, alpha=1 / period), p, m


def stochastic(bars: Bars, period: int = 14, smooth: int = 3) -> tuple[np.ndarray, np.ndarray]:
    _period(period)
    k = np.full(len(bars), np.nan)
    for i in range(period - 1, len(bars)):
        lo, hi = np.min(bars.low[i - period + 1:i + 1]), np.max(bars.high[i - period + 1:i + 1])
        k[i] = 50 if hi == lo else 100 * (bars.close[i] - lo) / (hi - lo)
    return k, sma(k, smooth)


def momentum(values, period: int = 10) -> np.ndarray:
    _period(period)
    a = np.asarray(values, dtype=float)
    result = np.full(a.shape, np.nan)
    result[period:] = a[period:] / a[:-period] - 1
    return result


def support_resistance(bars: Bars, period: int = 20) -> tuple[np.ndarray, np.ndarray]:
    """Previous bars only; current bar cannot move its own breakout threshold."""
    _period(period)
    support, resistance = np.full(len(bars), np.nan), np.full(len(bars), np.nan)
    for i in range(period, len(bars)):
        support[i], resistance[i] = np.min(bars.low[i - period:i]), np.max(bars.high[i - period:i])
    return support, resistance


def candles(bars: Bars) -> dict[str, np.ndarray]:
    body = bars.close - bars.open
    width = bars.high - bars.low
    upper = bars.high - np.maximum(bars.open, bars.close)
    lower = np.minimum(bars.open, bars.close) - bars.low
    engulf = np.zeros(len(bars), dtype=int)
    engulf[1:] = np.where((body[1:] > 0) & (body[:-1] < 0) & (bars.open[1:] <= bars.close[:-1]) & (bars.close[1:] >= bars.open[:-1]), 1,
                          np.where((body[1:] < 0) & (body[:-1] > 0) & (bars.open[1:] >= bars.close[:-1]) & (bars.close[1:] <= bars.open[:-1]), -1, 0))
    return {"body": body, "doji": (np.abs(body) <= .1 * width) & (width > 0),
            "engulfing": engulf, "bullish_pin": (lower >= 2 * np.abs(body)) & (upper <= np.abs(body)) & (width > 0),
            "bearish_pin": (upper >= 2 * np.abs(body)) & (lower <= np.abs(body)) & (width > 0)}


def structure(bars: Bars, period: int = 20) -> dict[str, np.ndarray]:
    support, resistance = support_resistance(bars, period)
    direction = np.where((bars.close > resistance), 1, np.where(bars.close < support, -1, 0))
    return {"support": support, "resistance": resistance, "breakout": direction,
            "false_up": (bars.high > resistance) & (bars.close <= resistance),
            "false_down": (bars.low < support) & (bars.close >= support),
            "higher_high": np.r_[False, np.diff(bars.high) > 0], "lower_low": np.r_[False, np.diff(bars.low) < 0]}


def statistical_summary(bars: Bars, lookback: int = 100) -> dict:
    _period(lookback)
    returns = np.diff(np.log(bars.close))[-lookback:]
    if len(returns) < 3:
        return {"status": "insufficient_data", "observations": len(returns)}
    std = float(np.std(returns, ddof=1))
    lag1 = None if np.std(returns[:-1]) == 0 or np.std(returns[1:]) == 0 else float(np.corrcoef(returns[:-1], returns[1:])[0, 1])
    a = adx(bars)[0][-1]
    vol = atr(bars)[-1] / bars.close[-1]
    return {"status": "descriptive_only", "observations": len(returns), "mean_log_return": float(np.mean(returns)),
            "bar_volatility": std, "return_quantiles": [float(x) for x in np.quantile(returns, [.05, .5, .95])],
            "lag1_correlation": lag1, "atr_fraction": float(vol) if np.isfinite(vol) else None,
            "regime": "unknown" if not np.isfinite(a) else "trend" if a >= 25 else "range" if a < 20 else "transition"}


def aligned_return_correlations(series: dict[str, Bars], min_observations: int = 30) -> dict:
    """Only jointly timestamp-aligned returns; no forward-filling disconnected markets."""
    output = {}
    names = sorted(series)
    for i, left in enumerate(names):
        for right in names[i + 1:]:
            a, b = series[left], series[right]
            da = {(float(a.timestamp[k - 1]), float(a.timestamp[k])): float(np.log(a.close[k] / a.close[k - 1])) for k in range(1, len(a))}
            db = {(float(b.timestamp[k - 1]), float(b.timestamp[k])): float(np.log(b.close[k] / b.close[k - 1])) for k in range(1, len(b))}
            shared = sorted(da.keys() & db.keys())
            av, bv = np.array([da[k] for k in shared]), np.array([db[k] for k in shared])
            valid = len(shared) >= min_observations and np.std(av) > 0 and np.std(bv) > 0
            output[f"{left}|{right}"] = {"observations": len(shared), "correlation": float(np.corrcoef(av, bv)[0, 1]) if valid else None}
    return output
