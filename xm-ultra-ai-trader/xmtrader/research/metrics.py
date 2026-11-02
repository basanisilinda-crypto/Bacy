"""Descriptive metrics for explicitly supplied trades. Never infer absent balances."""
from __future__ import annotations

import math
from collections import defaultdict
from datetime import datetime, timezone
from typing import Iterable, Mapping

import numpy as np


def _finite(value, default: float = 0.0) -> float:
    number = default if value is None else float(value)
    if not math.isfinite(number):
        raise ValueError("Metrics input must be finite")
    return number


def net_profit(trade: Mapping) -> float:
    """MT5 commission and swap are signed (charges negative); profit excludes them."""
    if trade.get("net_profit") is not None:
        return _finite(trade["net_profit"])
    return _finite(trade.get("profit")) + _finite(trade.get("commission")) + _finite(trade.get("swap"))


def session_name(timestamp: float) -> str:
    hour = datetime.fromtimestamp(float(timestamp), tz=timezone.utc).hour
    # Fixed UTC buckets, deliberately not claims about exchange/DST schedules.
    return "00–07 UTC" if hour < 7 else "07–13 UTC" if hour < 13 else "13–21 UTC" if hour < 21 else "21–24 UTC"


def performance_metrics(trades: Iterable[Mapping], equity_curve: Iterable[float] | None = None,
                        initial_equity: float | None = None) -> dict:
    """Closed-trade/deal statistics; caller must reconcile partial fills for trade rates.

    No annualized Sharpe is inferred without a fixed-period return series. Equity
    drawdown includes unrealised P/L only when the supplied equity curve does.
    """
    rows = list(trades)
    profits = np.array([net_profit(t) for t in rows], dtype=float)
    wins, losses = profits[profits > 0], profits[profits < 0]
    count = len(rows)
    consecutive_wins = consecutive_losses = max_wins = max_losses = 0
    for p in profits:
        consecutive_wins = consecutive_wins + 1 if p > 0 else 0
        consecutive_losses = consecutive_losses + 1 if p < 0 else 0
        max_wins, max_losses = max(max_wins, consecutive_wins), max(max_losses, consecutive_losses)
    durations = []
    for row in rows:
        if row.get("duration_seconds") is not None:
            duration = _finite(row["duration_seconds"])
        elif row.get("opened_at") is not None and row.get("closed_at") is not None:
            duration = _finite(row["closed_at"]) - _finite(row["opened_at"])
        else:
            continue
        if duration < 0:
            raise ValueError("Trade duration cannot be negative")
        durations.append(duration)
    drawdown = drawdown_pct = None
    equity_source = "unavailable"
    curve = None
    if equity_curve is not None:
        curve = np.asarray(list(equity_curve), dtype=float)
        equity_source = "supplied_equity_curve"
    elif initial_equity is not None:
        if not math.isfinite(initial_equity) or initial_equity <= 0:
            raise ValueError("initial_equity must be positive")
        curve = initial_equity + np.r_[0.0, np.cumsum(profits)]
        equity_source = "closed_trade_equity_only"
    if curve is not None and len(curve):
        if not np.all(np.isfinite(curve)):
            raise ValueError("Invalid equity curve")
        peaks = np.maximum.accumulate(curve)
        dd = peaks - curve
        drawdown = float(np.max(dd))
        if np.all(peaks > 0):
            drawdown_pct = float(np.max(dd / peaks) * 100)
    gross_profit = float(np.sum(wins))
    gross_loss = float(-np.sum(losses))
    return {
        "status": "insufficient_data" if count == 0 else "descriptive_only",
        "sample_size": count, "wins": len(wins), "losses": len(losses), "breakeven": count - len(wins) - len(losses),
        "win_rate": len(wins) / count if count else None, "loss_rate": len(losses) / count if count else None,
        "average_profit": float(np.mean(wins)) if len(wins) else None,
        "average_loss": float(np.mean(losses)) if len(losses) else None,
        "net_profit": float(np.sum(profits)) if count else None,
        "gross_profit": gross_profit if count else None, "gross_loss": gross_loss if count else None,
        "profit_factor": gross_profit / gross_loss if gross_loss > 0 else None,
        "profit_factor_reason": "no losing observations" if count and gross_loss == 0 else "no observations" if not count else None,
        "expectancy": float(np.mean(profits)) if count else None,
        "max_consecutive_wins": max_wins, "max_consecutive_losses": max_losses,
        "average_duration_seconds": float(np.mean(durations)) if durations else None,
        "max_drawdown": drawdown, "max_drawdown_pct": drawdown_pct, "drawdown_source": equity_source,
        "commission": sum(_finite(t.get("commission")) for t in rows) if count else None,
        "swap": sum(_finite(t.get("swap")) for t in rows) if count else None,
        "estimated_spread_slippage_cost": sum(_finite(t.get("spread_slippage_cost")) for t in rows) if count else None,
        "sharpe_ratio": None, "sharpe_reason": "Use risk_adjusted_returns with fixed-period equity observations",
        "warning": "Historical results do not establish future returns. Rates describe supplied closed records, which may be partial-close deals.",
    }


def grouped_performance(trades: Iterable[Mapping], key: str = "strategy") -> dict:
    if key not in {"strategy", "symbol", "session", "period"}:
        raise ValueError("Unsupported group")
    groups: dict[str, list] = defaultdict(list)
    for row in trades:
        if key == "session" and not row.get("session"):
            stamp = row.get("opened_at", row.get("timestamp"))
            value = session_name(stamp) if stamp is not None else "unknown"
        else:
            value = str(row.get(key, "unknown"))
        groups[value].append(row)
    return {key: performance_metrics(value) for key, value in groups.items()}


def risk_adjusted_returns(equity: Iterable[float], *, periods_per_year: int,
                          annual_risk_free_rate: float = 0) -> dict:
    values = np.asarray(list(equity), dtype=float)
    if periods_per_year <= 0 or not math.isfinite(annual_risk_free_rate) or annual_risk_free_rate <= -1:
        raise ValueError("Invalid annualisation inputs")
    if len(values) < 3:
        return {"status": "insufficient_data", "sharpe_ratio": None, "sortino_ratio": None}
    if not np.all(np.isfinite(values)) or np.any(values <= 0):
        raise ValueError("Equity observations must be positive and finite")
    returns = values[1:] / values[:-1] - 1
    excess = returns - ((1 + annual_risk_free_rate) ** (1 / periods_per_year) - 1)
    sigma = np.std(excess, ddof=1)
    downside = np.sqrt(np.mean(np.minimum(excess, 0) ** 2))
    return {"status": "descriptive_only", "observations": len(returns), "periods_per_year": periods_per_year,
            "sharpe_ratio": float(np.mean(excess) / sigma * np.sqrt(periods_per_year)) if sigma > 0 else None,
            "sortino_ratio": float(np.mean(excess) / downside * np.sqrt(periods_per_year)) if downside > 0 else None,
            "warning": "Requires equally spaced equity observations; annualisation assumes comparable return periods."}


def period_reports(trades: Iterable[Mapping]) -> dict:
    """UTC closing-date reports; no reporting from timestamps that are missing."""
    groups: dict[str, dict[str, list]] = {"daily": defaultdict(list), "weekly": defaultdict(list), "monthly": defaultdict(list)}
    omitted = 0
    for row in trades:
        stamp = row.get("closed_at", row.get("timestamp"))
        if stamp is None:
            omitted += 1
            continue
        dt = datetime.fromtimestamp(float(stamp), tz=timezone.utc)
        iso = dt.isocalendar()
        for period, label in (("daily", dt.strftime("%Y-%m-%d")), ("weekly", f"{iso.year}-W{iso.week:02d}"), ("monthly", dt.strftime("%Y-%m"))):
            groups[period][label].append(row)
    return {**{period: {label: performance_metrics(rows) for label, rows in values.items()} for period, values in groups.items()}, "records_without_timestamp": omitted}
