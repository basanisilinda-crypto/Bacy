"""Independent, fail-closed demo risk checks and durable order bookkeeping.

This module never connects to a broker or submits an order. MT5 is the final risk
authority. Monetary inputs (including loss-per-lot and margin-per-lot) must be
calculated by MT5 for the *normalized proposed prices*, in the account currency.
Existing exposure is loss from current mark-to-market equity to the protective
stop, plus estimated exit costs: floating losses are already included in equity.
Stops and the budgets below are estimates, not guarantees against market gaps.

RiskLedger uses explicit verified anchors, not guessed midnight balances. Missing
state, incomplete history, clock rollback and unverified rollovers fail closed.
OrderJournal records a request before submission and never retries an uncertain
request. No model or strategy is allowed to override these decisions.
"""
from __future__ import annotations

from contextlib import contextmanager
from dataclasses import asdict, dataclass, fields
from datetime import datetime, timezone
from decimal import Decimal, ROUND_CEILING, ROUND_FLOOR
from enum import Enum
import hashlib
import json
import math
from pathlib import Path
import sqlite3
import time
from typing import Any, Iterator, Mapping, Sequence


def _number(value: Any, *, positive: bool = False, nonnegative: bool = False) -> bool:
    if isinstance(value, bool) or not isinstance(value, (int, float, Decimal)):
        return False
    try:
        numeric = float(value)
    except (ValueError, OverflowError):
        return False
    return math.isfinite(numeric) and (not positive or numeric > 0) and (not nonnegative or numeric >= 0)


def _text(value: Any) -> bool:
    return isinstance(value, str) and 0 < len(value.strip()) <= 128 and not any(ord(c) < 32 for c in value)


@dataclass(frozen=True)
class RiskLimits:
    """Percentage units: 0.25 means one quarter of one percent, never 25%."""

    risk_per_trade_pct: float = 0.25
    daily_loss_pct: float = 1.0
    weekly_loss_pct: float = 3.0
    max_drawdown_pct: float = 5.0
    max_positions: int = 2
    max_consecutive_losses: int = 3
    max_portfolio_risk_pct: float = 0.5
    max_correlated_risk_pct: float = 0.25
    correlation_threshold: float = 0.75
    max_margin_utilization_pct: float = 25.0
    max_quote_age_seconds: float = 5.0
    max_account_age_seconds: float = 10.0
    max_clock_skew_seconds: float = 2.0
    max_spread_to_stop_ratio: float = 0.2
    cooldown_seconds: int = 3600

    def __post_init__(self) -> None:
        maxima = {
            "risk_per_trade_pct": 0.25, "daily_loss_pct": 1.0,
            "weekly_loss_pct": 3.0, "max_drawdown_pct": 5.0,
            "max_portfolio_risk_pct": 0.5, "max_correlated_risk_pct": 0.25,
            "max_margin_utilization_pct": 25.0, "max_quote_age_seconds": 5.0,
            "max_account_age_seconds": 10.0, "max_clock_skew_seconds": 2.0,
            "max_spread_to_stop_ratio": 0.2,
        }
        for name, maximum in maxima.items():
            value = getattr(self, name)
            if not _number(value, positive=True) or value > maximum:
                raise ValueError(f"{name} must be positive and at most {maximum}")
        for name, maximum in (("max_positions", 2), ("max_consecutive_losses", 3)):
            value = getattr(self, name)
            if type(value) is not int or not 1 <= value <= maximum:
                raise ValueError(f"{name} must be an integer from 1 to {maximum}")
        if not _number(self.correlation_threshold, positive=True) or not 0.1 <= self.correlation_threshold <= 0.75:
            raise ValueError("correlation_threshold must be between 0.1 and 0.75")
        if type(self.cooldown_seconds) is not int or not 3600 <= self.cooldown_seconds <= 604800:
            raise ValueError("cooldown_seconds must be an integer from 3600 to 604800")
        if not self.risk_per_trade_pct <= self.daily_loss_pct <= self.weekly_loss_pct <= self.max_drawdown_pct:
            raise ValueError("Trade, daily, weekly and total loss limits must be ordered")
        if self.max_portfolio_risk_pct < self.risk_per_trade_pct:
            raise ValueError("Portfolio risk limit must accommodate the per-trade upper bound")

    @classmethod
    def from_mapping(cls, values: Mapping[str, Any]) -> "RiskLimits":
        if not isinstance(values, Mapping):
            raise ValueError("Risk limits must be an object")
        unknown = set(values) - {field.name for field in fields(cls)}
        if unknown:
            raise ValueError("Unknown risk setting: " + ", ".join(sorted(map(str, unknown))))
        return cls(**dict(values))

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def validate_limits(values: Mapping[str, Any]) -> dict[str, Any]:
    """Validate a partial configuration against release-one safety boundaries."""
    return RiskLimits.from_mapping(values).to_dict()


@dataclass(frozen=True)
class AccountSnapshot:
    account_id: str
    currency: str
    equity: float
    balance: float
    margin: float
    free_margin: float
    timestamp: float
    day_start_equity: float | None = None
    week_start_equity: float | None = None
    high_water_mark: float | None = None
    mode: str = "demo"
    connected: bool = True
    trade_allowed: bool = True
    history_complete: bool = False
    exposure_complete: bool = False
    consecutive_losses: int = 0
    cooldown_until: float = 0.0
    suspended_reason: str = ""


@dataclass(frozen=True)
class InstrumentSpec:
    symbol: str
    bid: float
    ask: float
    quote_time: float
    point: float
    tick_size: float
    volume_min: float
    volume_max: float
    volume_step: float
    min_stop_points: float = 0.0
    max_spread_points: float = 30.0
    max_slippage_points: float = 10.0
    trade_mode: str = "full"  # full, long_only, short_only, close_only, disabled
    session_open: bool = True
    liquid: bool = True
    selected: bool = True


@dataclass(frozen=True)
class TradeIntent:
    symbol: str
    side: str
    entry_price: float
    stop_loss: float
    loss_per_lot: float | None
    margin_per_lot: float | None
    account_currency: str
    take_profit: float | None = None
    estimated_cost_per_lot: float = 0.0
    volume_cap: float | None = None
    order_type: str = "market"
    signal_id: str = ""
    news_clear: bool = False
    news_required: bool = True


@dataclass(frozen=True)
class Exposure:
    symbol: str
    side: str
    volume: float
    remaining_loss_to_stop: float | None
    account_currency: str
    pending: bool = False
    margin_reserved: float = 0.0
    protective_stop_valid: bool = True
    owned_by_robot: bool = False


@dataclass(frozen=True)
class RiskDecision:
    approved: bool
    reason_codes: tuple[str, ...]
    explanation: str
    volume: float = 0.0
    estimated_cash_risk: float = 0.0
    existing_cash_risk: float = 0.0
    remaining_daily_budget: float | None = None
    remaining_weekly_budget: float | None = None
    remaining_drawdown_budget: float | None = None
    estimated_margin: float = 0.0


def _reject(code: str, explanation: str) -> RiskDecision:
    return RiskDecision(False, (code,), explanation)


def _aligned(price: float, increment: float) -> bool:
    quotient = Decimal(str(price)) / Decimal(str(increment))
    return abs(quotient - quotient.to_integral_value()) <= Decimal("0.000001")


def normalize_protective_prices(side: str, stop_loss: float, take_profit: float | None,
                                tick_size: float) -> tuple[float, float | None]:
    """Round stops away from entry; caller MUST recalculate monetary risk afterward.

    This does not certify broker stop/freeze levels or the direction relative to
    current quotes. evaluate_trade performs those checks on the normalized input.
    """
    if side not in ("buy", "sell") or not _number(tick_size, positive=True) or not _number(stop_loss, positive=True):
        raise ValueError("Valid side, tick size and stop price are required")
    if take_profit is not None and not _number(take_profit, positive=True):
        raise ValueError("Take-profit must be a positive finite price")
    step = Decimal(str(tick_size))
    stop_rounding = ROUND_FLOOR if side == "buy" else ROUND_CEILING
    profit_rounding = ROUND_CEILING if side == "buy" else ROUND_FLOOR
    stop = float((Decimal(str(stop_loss)) / step).to_integral_value(rounding=stop_rounding) * step)
    profit = None if take_profit is None else float((Decimal(str(take_profit)) / step).to_integral_value(rounding=profit_rounding) * step)
    return stop, profit


def evaluate_trade(account: AccountSnapshot, instrument: InstrumentSpec, intent: TradeIntent,
                   exposures: Sequence[Exposure], *, limits: RiskLimits | None = None,
                   now: float | None = None,
                   correlations: Mapping[tuple[str, str], float] | None = None) -> RiskDecision:
    """Return a bounded lot size, or a reason for rejecting a NEW demo order.

    A missing correlation for an existing different instrument rejects the new
    order. Correlations use absolute magnitude; inverse markets are not assumed
    to hedge execution gaps. Both external positions and pending orders count.
    The returned decision expires with its input quote and must be checked again
    by MT5 immediately before submission.
    """
    limits = limits or RiskLimits()
    now = time.time() if now is None else now
    if not isinstance(limits, RiskLimits) or not _number(now, positive=True):
        return _reject("invalid_data", "Risk configuration or current time is invalid.")
    if not isinstance(account, AccountSnapshot) or not isinstance(instrument, InstrumentSpec) or not isinstance(intent, TradeIntent):
        return _reject("invalid_data", "Typed account, instrument and trade data are required.")
    if account.mode != "demo":
        return _reject("demo_only", "This release only approves demo-account trades.")
    if not all((_text(account.account_id), _text(account.currency), account.connected is True, account.trade_allowed is True)):
        return _reject("account_unavailable", "A connected account with trading permission is required.")
    if account.history_complete is not True or account.exposure_complete is not True:
        return _reject("incomplete_account", "Verified history and all account exposures are required.")
    if account.suspended_reason:
        return _reject("suspended", "Trading is suspended: " + str(account.suspended_reason)[:200])
    if not all(_number(value, positive=True) for value in (account.equity, account.balance, account.day_start_equity,
                                                          account.week_start_equity, account.high_water_mark)):
        return _reject("missing_anchors", "Positive equity and verified daily, weekly and peak-equity anchors are required.")
    if not all(_number(value, nonnegative=True) for value in (account.margin, account.free_margin, account.cooldown_until)):
        return _reject("invalid_data", "Account margin and cooldown data must be finite and nonnegative.")
    if not _number(account.timestamp, positive=True) or not -limits.max_clock_skew_seconds <= now - account.timestamp <= limits.max_account_age_seconds:
        return _reject("stale_account", "The account snapshot is stale or its clock is ahead.")
    if type(account.consecutive_losses) is not int or account.consecutive_losses < 0:
        return _reject("invalid_data", "Loss streak data is invalid.")
    if account.consecutive_losses >= limits.max_consecutive_losses or account.cooldown_until > now:
        return _reject("loss_cooldown", "The loss-streak cooldown requires review before new trades.")
    if not isinstance(exposures, (tuple, list)):
        return _reject("incomplete_exposure", "A complete exposure snapshot is required.")
    if len(exposures) >= limits.max_positions:
        return _reject("position_limit", "Existing positions and pending orders reach the position limit.")
    if not _text(instrument.symbol) or instrument.symbol != intent.symbol or intent.side not in ("buy", "sell"):
        return _reject("invalid_instrument", "The trade must match a valid selected broker instrument and direction.")
    spec_positive = (instrument.bid, instrument.ask, instrument.point, instrument.tick_size, instrument.volume_min,
                     instrument.volume_max, instrument.volume_step, instrument.max_spread_points)
    if not all(_number(value, positive=True) for value in spec_positive) or instrument.ask < instrument.bid:
        return _reject("invalid_specification", "Broker quotes, tick size and volume specifications must be valid.")
    if not all(_number(value, nonnegative=True) for value in (instrument.min_stop_points, instrument.max_slippage_points)):
        return _reject("invalid_specification", "Broker distance restrictions are invalid.")
    if (instrument.volume_min > instrument.volume_max or not _aligned(instrument.volume_min, instrument.volume_step)
            or instrument.tick_size < instrument.point):
        return _reject("invalid_specification", "Broker lot increments or tick specifications are inconsistent.")
    if not _number(instrument.quote_time, positive=True) or not -limits.max_clock_skew_seconds <= now - instrument.quote_time <= limits.max_quote_age_seconds:
        return _reject("stale_quote", "The price is stale or its clock is ahead.")
    allowed_modes = {"full", "long_only" if intent.side == "buy" else "short_only"}
    if instrument.trade_mode not in allowed_modes or instrument.selected is not True or instrument.session_open is not True:
        return _reject("market_closed", "The broker instrument is unavailable for this trade direction or session.")
    if instrument.liquid is not True:
        return _reject("liquidity", "The market-liquidity check did not pass.")
    if intent.news_required is not False and intent.news_clear is not True:
        return _reject("news_uncertain", "Required economic-event information is missing or trading is restricted.")
    if not _number(intent.entry_price, positive=True) or not _number(intent.stop_loss, positive=True):
        return _reject("missing_stop", "Every new trade requires an entry price and a valid protective stop.")
    if intent.take_profit is not None and not _number(intent.take_profit, positive=True):
        return _reject("invalid_take_profit", "Take-profit must be a positive finite price.")
    if intent.account_currency != account.currency:
        return _reject("currency_mismatch", "Monetary risk and margin must be converted to the account currency by MT5.")
    if not _number(intent.loss_per_lot, positive=True) or not _number(intent.margin_per_lot, positive=True) or not _number(intent.estimated_cost_per_lot, nonnegative=True):
        return _reject("unknown_cash_risk", "Verified cash loss, margin and transaction-cost estimates are required.")
    if intent.volume_cap is not None and not _number(intent.volume_cap, positive=True):
        return _reject("invalid_volume", "The optional lot cap must be positive and finite.")
    prices = (intent.entry_price, intent.stop_loss) + (() if intent.take_profit is None else (intent.take_profit,))
    if not all(_aligned(price, instrument.tick_size) for price in prices):
        return _reject("price_increment", "Prices must use broker tick increments; recalculate risk after rounding.")
    minimum_distance = instrument.min_stop_points * instrument.point
    sign = 1 if intent.side == "buy" else -1
    stop_distance = sign * (intent.entry_price - intent.stop_loss)
    if stop_distance <= 0 or stop_distance + 1e-12 < minimum_distance:
        return _reject("invalid_stop", "The protective stop is on the wrong side or too close to entry.")
    if intent.take_profit is not None and (sign * (intent.take_profit - intent.entry_price) <= 0 or sign * (intent.take_profit - intent.entry_price) + 1e-12 < minimum_distance):
        return _reject("invalid_take_profit", "The profit target is on the wrong side or violates broker distance rules.")
    reference = instrument.ask if intent.side == "buy" else instrument.bid
    if intent.order_type == "market":
        if abs(intent.entry_price - reference) > instrument.max_slippage_points * instrument.point + 1e-12:
            return _reject("price_moved", "The market has moved beyond the approved entry tolerance.")
        stop_reference = instrument.bid if intent.side == "buy" else instrument.ask
        if sign * (stop_reference - intent.stop_loss) + 1e-12 < minimum_distance:
            return _reject("invalid_stop", "The stop is too close to the broker's closing-side quote.")
        if intent.take_profit is not None and sign * (intent.take_profit - stop_reference) + 1e-12 < minimum_distance:
            return _reject("invalid_take_profit", "The target is too close to the broker's closing-side quote.")
    elif intent.order_type in ("limit", "stop"):
        entry_offset = sign * (intent.entry_price - reference)
        if (intent.order_type == "limit" and entry_offset >= -minimum_distance) or (intent.order_type == "stop" and entry_offset <= minimum_distance):
            return _reject("invalid_pending_price", "The pending entry price is on the wrong side or too near the market.")
    else:
        return _reject("invalid_order_type", "Unsupported order type.")
    spread = instrument.ask - instrument.bid
    if spread / instrument.point > instrument.max_spread_points + 1e-9 or spread / stop_distance > limits.max_spread_to_stop_ratio:
        return _reject("spread_limit", "The spread is too large for this instrument or stop distance.")

    existing_risk = correlated_risk = reserved_margin = 0.0
    for exposure in exposures:
        if (not isinstance(exposure, Exposure) or not _text(exposure.symbol) or exposure.side not in ("buy", "sell")
                or not _number(exposure.volume, positive=True) or exposure.protective_stop_valid is not True
                or not _number(exposure.remaining_loss_to_stop, nonnegative=True)
                or not _number(exposure.margin_reserved, nonnegative=True)
                or exposure.account_currency != account.currency or type(exposure.pending) is not bool):
            return _reject("unknown_exposure", "All positions and pending orders, including manual trades, need known stop risk.")
        existing_risk += exposure.remaining_loss_to_stop
        if exposure.pending:
            reserved_margin += exposure.margin_reserved
        if exposure.symbol == intent.symbol:
            coefficient = 1.0
        else:
            coefficient = None if correlations is None else correlations.get((exposure.symbol, intent.symbol), correlations.get((intent.symbol, exposure.symbol)))
            if not _number(coefficient) or not -1 <= coefficient <= 1:
                return _reject("unknown_correlation", "A valid correlation estimate is required for this portfolio combination.")
        if abs(coefficient) >= limits.correlation_threshold:
            correlated_risk += exposure.remaining_loss_to_stop

    daily_remaining = account.equity - account.day_start_equity * (1 - limits.daily_loss_pct / 100)
    weekly_remaining = account.equity - account.week_start_equity * (1 - limits.weekly_loss_pct / 100)
    peak = max(account.high_water_mark, account.equity)
    drawdown_remaining = account.equity - peak * (1 - limits.max_drawdown_pct / 100)
    for amount, code, description in ((daily_remaining, "daily_loss_limit", "daily"),
                                      (weekly_remaining, "weekly_loss_limit", "weekly"),
                                      (drawdown_remaining, "drawdown_limit", "total drawdown")):
        if amount <= 1e-9:
            return _reject(code, f"The {description} equity-loss limit has been reached, including floating losses.")
    cash_budget = min(account.equity * limits.risk_per_trade_pct / 100,
                      daily_remaining - existing_risk,
                      weekly_remaining - existing_risk,
                      drawdown_remaining - existing_risk,
                      account.equity * limits.max_portfolio_risk_pct / 100 - existing_risk,
                      account.equity * limits.max_correlated_risk_pct / 100 - correlated_risk)
    if cash_budget <= 1e-9:
        return _reject("exposure_limit", "Existing stop risk exhausts an account or correlated-exposure budget.")
    available_margin = min(account.free_margin - reserved_margin,
                           account.equity * limits.max_margin_utilization_pct / 100 - account.margin - reserved_margin)
    if available_margin <= 0:
        return _reject("margin_limit", "Available margin is insufficient under the margin-utilization limit.")
    per_lot = intent.loss_per_lot + intent.estimated_cost_per_lot
    raw_volume = min(cash_budget / per_lot, available_margin / intent.margin_per_lot,
                     instrument.volume_max, intent.volume_cap if intent.volume_cap is not None else instrument.volume_max)
    step = Decimal(str(instrument.volume_step))
    volume = float((Decimal(str(raw_volume)) / step).to_integral_value(rounding=ROUND_FLOOR) * step)
    if volume < instrument.volume_min - 1e-12 or volume <= 0:
        return _reject("minimum_volume_risk", "The broker's minimum lot size would exceed the approved risk or margin budget.")
    risk = volume * per_lot
    margin = volume * intent.margin_per_lot
    if risk > cash_budget + 1e-9 or margin > available_margin + 1e-9:
        return _reject("rounding_limit", "A rounding check found the trade above its approved budget.")
    return RiskDecision(True, (), "Demo trade passes current risk checks; MT5 must revalidate before submission.",
                        volume, risk, existing_risk, daily_remaining, weekly_remaining, drawdown_remaining, margin)


class RiskStateError(RuntimeError):
    """Persistent risk state is unavailable or inconsistent: no new entries."""


def _periods(timestamp: float) -> tuple[str, str]:
    if not _number(timestamp, positive=True):
        raise RiskStateError("A valid UTC timestamp is required")
    dt = datetime.fromtimestamp(timestamp, timezone.utc)
    iso = dt.isocalendar()
    return dt.date().isoformat(), f"{iso.year}-W{iso.week:02d}"


class _DurableStore:
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)

    @contextmanager
    def _connection(self, *, write: bool = False) -> Iterator[sqlite3.Connection]:
        connection = None
        try:
            connection = sqlite3.connect(str(self.path), timeout=10, isolation_level=None)
            connection.row_factory = sqlite3.Row
            connection.execute("PRAGMA foreign_keys=ON")
            connection.execute("PRAGMA journal_mode=WAL")
            connection.execute("PRAGMA synchronous=FULL")
            if write:
                connection.execute("BEGIN IMMEDIATE")
            yield connection
            if write:
                connection.commit()
        except (sqlite3.Error, OSError) as exc:
            if connection is not None and connection.in_transaction:
                connection.rollback()
            raise RiskStateError("Persistent risk state could not be read or committed") from exc
        except BaseException:
            if connection is not None and connection.in_transaction:
                connection.rollback()
            raise
        finally:
            if connection is not None:
                connection.close()


class RiskLedger(_DurableStore):
    """Account-isolated, persisted equity anchors; UTC daily/ISO weekly periods.

    initialize() requires verified broker history and explicit acknowledgment.
    Reinitializing an account is forbidden. On rollover observe() requires the
    actual reconstructed period-start equity; it never substitutes current equity.
    Deposits/withdrawals require record_cash_flow(), with an immutable broker
    event ID and verified amount. They adjust capital anchors, never erase losses.
    """

    def __init__(self, path: str | Path) -> None:
        super().__init__(path)
        with self._connection(write=True) as db:
            db.execute("""CREATE TABLE IF NOT EXISTS risk_accounts(
                account_id TEXT PRIMARY KEY, currency TEXT NOT NULL, day_key TEXT NOT NULL,
                week_key TEXT NOT NULL, day_start_equity REAL NOT NULL, week_start_equity REAL NOT NULL,
                high_water_mark REAL NOT NULL, last_equity REAL NOT NULL, last_seen REAL NOT NULL,
                consecutive_losses INTEGER NOT NULL DEFAULT 0, cooldown_until REAL NOT NULL DEFAULT 0,
                suspended_reason TEXT NOT NULL DEFAULT '')""")
            db.execute("""CREATE TABLE IF NOT EXISTS risk_events(
                account_id TEXT NOT NULL REFERENCES risk_accounts(account_id), event_id TEXT NOT NULL,
                kind TEXT NOT NULL, amount REAL NOT NULL, timestamp REAL NOT NULL,
                PRIMARY KEY(account_id,event_id))""")

    def initialize(self, account_id: str, currency: str, *, equity: float, day_start_equity: float,
                   week_start_equity: float, high_water_mark: float, now: float,
                   history_verified: bool, acknowledge: bool = False) -> dict[str, Any]:
        if history_verified is not True or acknowledge is not True:
            raise RiskStateError("Verified broker history and explicit first-use acknowledgment are required")
        if not _text(account_id) or not _text(currency) or not all(_number(v, positive=True) for v in (equity, day_start_equity, week_start_equity, high_water_mark)):
            raise RiskStateError("Account identity, currency and positive verified equity anchors are required")
        if high_water_mark < max(equity, day_start_equity, week_start_equity):
            raise RiskStateError("The high-water mark cannot be below known account equity")
        day, week = _periods(now)
        with self._connection(write=True) as db:
            if db.execute("SELECT 1 FROM risk_accounts WHERE account_id=?", (account_id,)).fetchone():
                raise RiskStateError("Existing account anchors cannot be reset")
            db.execute("""INSERT INTO risk_accounts(account_id,currency,day_key,week_key,day_start_equity,
                week_start_equity,high_water_mark,last_equity,last_seen) VALUES(?,?,?,?,?,?,?,?,?)""",
                       (account_id, currency, day, week, day_start_equity, week_start_equity, high_water_mark, equity, now))
        return self.snapshot(account_id, now=now)

    def snapshot(self, account_id: str, *, now: float | None = None) -> dict[str, Any]:
        with self._connection() as db:
            row = db.execute("SELECT * FROM risk_accounts WHERE account_id=?", (account_id,)).fetchone()
        if row is None:
            raise RiskStateError("No persisted anchors for this account")
        result = dict(row)
        if now is not None:
            day, week = _periods(now)
            if now < result["last_seen"] - 2:
                raise RiskStateError("Clock moved backwards; reconciliation is required")
            if (day, week) != (result["day_key"], result["week_key"]):
                raise RiskStateError("Period rollover requires verified opening equity")
        return result

    def observe(self, account_id: str, currency: str, *, equity: float, now: float,
                history_verified: bool, day_start_equity: float | None = None,
                week_start_equity: float | None = None) -> dict[str, Any]:
        if history_verified is not True or not _number(equity, positive=True):
            raise RiskStateError("A verified positive equity snapshot is required")
        day, week = _periods(now)
        with self._connection(write=True) as db:
            row = db.execute("SELECT * FROM risk_accounts WHERE account_id=?", (account_id,)).fetchone()
            if row is None or currency != row["currency"]:
                raise RiskStateError("Account state is missing or its currency changed")
            if now < row["last_seen"] - 2:
                raise RiskStateError("Clock moved backwards; reconciliation is required")
            day_anchor, week_anchor = row["day_start_equity"], row["week_start_equity"]
            if day != row["day_key"]:
                if not _number(day_start_equity, positive=True):
                    raise RiskStateError("Daily rollover requires verified opening equity")
                day_anchor = day_start_equity
            elif day_start_equity is not None and not math.isclose(day_start_equity, day_anchor, abs_tol=1e-8):
                raise RiskStateError("Existing daily anchor cannot be reset")
            if week != row["week_key"]:
                if not _number(week_start_equity, positive=True):
                    raise RiskStateError("Weekly rollover requires verified opening equity")
                week_anchor = week_start_equity
            elif week_start_equity is not None and not math.isclose(week_start_equity, week_anchor, abs_tol=1e-8):
                raise RiskStateError("Existing weekly anchor cannot be reset")
            peak = max(row["high_water_mark"], equity, day_anchor, week_anchor)
            db.execute("""UPDATE risk_accounts SET day_key=?,week_key=?,day_start_equity=?,week_start_equity=?,
                high_water_mark=?,last_equity=?,last_seen=? WHERE account_id=?""",
                       (day, week, day_anchor, week_anchor, peak, equity, max(now, row["last_seen"]), account_id))
        return self.snapshot(account_id, now=now)

    def record_cash_flow(self, account_id: str, event_id: str, *, amount: float, timestamp: float,
                         verified: bool) -> bool:
        """Apply a broker-verified deposit/withdrawal exactly once, in chronological order."""
        if verified is not True or not _text(event_id) or not _number(amount) or amount == 0:
            raise RiskStateError("A verified broker cash-flow event is required")
        day, week = _periods(timestamp)
        with self._connection(write=True) as db:
            row = db.execute("SELECT * FROM risk_accounts WHERE account_id=?", (account_id,)).fetchone()
            if row is None:
                raise RiskStateError("Unknown account")
            old = db.execute("SELECT * FROM risk_events WHERE account_id=? AND event_id=?", (account_id, event_id)).fetchone()
            if old:
                if old["kind"] != "cash_flow" or old["amount"] != amount or old["timestamp"] != timestamp:
                    raise RiskStateError("A broker event ID was reused with different data")
                return False
            if timestamp < row["last_seen"] - 2 or (day, week) != (row["day_key"], row["week_key"]):
                raise RiskStateError("Out-of-order cash flow requires broker-history reconciliation")
            adjusted = [row[key] + amount for key in ("day_start_equity", "week_start_equity", "high_water_mark", "last_equity")]
            if not all(_number(v, positive=True) for v in adjusted):
                raise RiskStateError("Cash withdrawal leaves invalid capital anchors; suspend trading")
            db.execute("INSERT INTO risk_events VALUES(?,?,?,?,?)", (account_id, event_id, "cash_flow", amount, timestamp))
            db.execute("""UPDATE risk_accounts SET day_start_equity=?,week_start_equity=?,high_water_mark=?,
                last_equity=?,last_seen=? WHERE account_id=?""", (*adjusted, max(timestamp, row["last_seen"]), account_id))
        return True

    def record_closed_trade(self, account_id: str, event_id: str, *, net_profit: float, timestamp: float,
                            limits: RiskLimits | None = None) -> bool:
        """Record a fully closed logical trade, including fees; partial deals must be aggregated."""
        limits = limits or RiskLimits()
        if not _text(event_id) or not _number(net_profit):
            raise RiskStateError("Closed-trade data is invalid")
        _periods(timestamp)
        with self._connection(write=True) as db:
            row = db.execute("SELECT * FROM risk_accounts WHERE account_id=?", (account_id,)).fetchone()
            if row is None:
                raise RiskStateError("Unknown account")
            old = db.execute("SELECT * FROM risk_events WHERE account_id=? AND event_id=?", (account_id, event_id)).fetchone()
            if old:
                if old["kind"] != "closed_trade" or old["amount"] != net_profit or old["timestamp"] != timestamp:
                    raise RiskStateError("A broker event ID was reused with different data")
                return False
            last_trade = db.execute("SELECT MAX(timestamp) FROM risk_events WHERE account_id=? AND kind='closed_trade'", (account_id,)).fetchone()[0]
            if last_trade is not None and timestamp < last_trade:
                raise RiskStateError("Out-of-order closed trades require historical reconciliation")
            losses = row["consecutive_losses"] + 1 if net_profit < 0 else (0 if net_profit > 0 else row["consecutive_losses"])
            cooldown = max(row["cooldown_until"], timestamp + limits.cooldown_seconds) if losses >= limits.max_consecutive_losses else row["cooldown_until"]
            db.execute("INSERT INTO risk_events VALUES(?,?,?,?,?)", (account_id, event_id, "closed_trade", net_profit, timestamp))
            db.execute("UPDATE risk_accounts SET consecutive_losses=?,cooldown_until=? WHERE account_id=?", (losses, cooldown, account_id))
        return True

    def acknowledge_cooldown(self, account_id: str, *, now: float, history_verified: bool,
                             acknowledge: bool) -> None:
        """A deliberate reviewed restart after cooldown; never resets equity anchors."""
        if history_verified is not True or acknowledge is not True:
            raise RiskStateError("Verified history and explicit cooldown review are required")
        _periods(now)
        with self._connection(write=True) as db:
            row = db.execute("SELECT * FROM risk_accounts WHERE account_id=?", (account_id,)).fetchone()
            if row is None or now < max(row["cooldown_until"], row["last_seen"]):
                raise RiskStateError("The cooldown has not elapsed or account state is unavailable")
            db.execute("UPDATE risk_accounts SET consecutive_losses=0,cooldown_until=0 WHERE account_id=?", (account_id,))


class OrderState(str, Enum):
    CREATED = "created"
    APPROVED = "approved"
    SUBMITTING = "submitting"
    UNKNOWN = "unknown"
    ACCEPTED = "accepted"
    PARTIALLY_FILLED = "partially_filled"
    FILLED = "filled"
    REJECTED = "rejected"
    CANCEL_PENDING = "cancel_pending"
    CANCELLED = "cancelled"
    EXPIRED = "expired"


_TRANSITIONS = {
    OrderState.CREATED: {OrderState.APPROVED, OrderState.REJECTED, OrderState.CANCELLED},
    OrderState.APPROVED: {OrderState.SUBMITTING, OrderState.REJECTED, OrderState.CANCELLED},
    OrderState.SUBMITTING: {OrderState.UNKNOWN, OrderState.ACCEPTED, OrderState.PARTIALLY_FILLED, OrderState.FILLED, OrderState.REJECTED},
    OrderState.UNKNOWN: {OrderState.ACCEPTED, OrderState.PARTIALLY_FILLED, OrderState.FILLED, OrderState.REJECTED, OrderState.CANCELLED, OrderState.EXPIRED},
    OrderState.ACCEPTED: {OrderState.PARTIALLY_FILLED, OrderState.FILLED, OrderState.CANCEL_PENDING, OrderState.CANCELLED, OrderState.EXPIRED, OrderState.UNKNOWN},
    OrderState.PARTIALLY_FILLED: {OrderState.PARTIALLY_FILLED, OrderState.FILLED, OrderState.CANCEL_PENDING, OrderState.CANCELLED, OrderState.UNKNOWN},
    OrderState.CANCEL_PENDING: {OrderState.PARTIALLY_FILLED, OrderState.FILLED, OrderState.CANCELLED, OrderState.UNKNOWN},
    OrderState.FILLED: set(), OrderState.REJECTED: set(), OrderState.CANCELLED: set(), OrderState.EXPIRED: set(),
}


class OrderJournal(_DurableStore):
    """Transactional request state machine, idempotency and crash reconciliation.

    reserve() persists a unique account/signal before submission. begin_submission()
    changes APPROVED -> SUBMITTING atomically, exactly once. An uncertain result
    becomes UNKNOWN and blocks further account submissions until authoritative
    broker reconciliation. Reusing request/signal IDs never makes another order.
    """

    def __init__(self, path: str | Path) -> None:
        super().__init__(path)
        with self._connection(write=True) as db:
            db.execute("""CREATE TABLE IF NOT EXISTS risk_orders(
                request_id TEXT PRIMARY KEY, account_id TEXT NOT NULL, signal_id TEXT NOT NULL,
                symbol TEXT NOT NULL, side TEXT NOT NULL, volume REAL NOT NULL, state TEXT NOT NULL,
                filled_volume REAL NOT NULL DEFAULT 0, broker_order_id TEXT, updated_at REAL NOT NULL,
                UNIQUE(account_id,signal_id))""")
            db.execute("""CREATE TABLE IF NOT EXISTS risk_order_events(
                event_id TEXT PRIMARY KEY, request_id TEXT NOT NULL REFERENCES risk_orders(request_id),
                timestamp REAL NOT NULL, from_state TEXT, to_state TEXT NOT NULL,
                payload_hash TEXT NOT NULL, reason TEXT NOT NULL)""")

    def reserve(self, *, request_id: str, account_id: str, signal_id: str, symbol: str,
                side: str, volume: float, now: float | None = None) -> dict[str, Any]:
        now = time.time() if now is None else now
        if not all(_text(v) for v in (request_id, account_id, signal_id, symbol)) or side not in ("buy", "sell") or not _number(volume, positive=True) or not _number(now, positive=True):
            raise RiskStateError("A valid account, request, signal and order intent are required")
        with self._connection(write=True) as db:
            previous = db.execute("SELECT * FROM risk_orders WHERE request_id=? OR (account_id=? AND signal_id=?)", (request_id, account_id, signal_id)).fetchone()
            if previous is not None:
                expected = (request_id, account_id, signal_id, symbol, side, volume)
                actual = tuple(previous[k] for k in ("request_id", "account_id", "signal_id", "symbol", "side", "volume"))
                if expected != actual:
                    raise RiskStateError("Request or signal ID already belongs to a different order")
                return dict(previous)
            db.execute("""INSERT INTO risk_orders(request_id,account_id,signal_id,symbol,side,volume,state,updated_at)
                VALUES(?,?,?,?,?,?,?,?)""", (request_id, account_id, signal_id, symbol, side, volume, OrderState.CREATED.value, now))
            payload = hashlib.sha256(json.dumps([account_id, signal_id, symbol, side, volume]).encode()).hexdigest()
            db.execute("INSERT INTO risk_order_events VALUES(?,?,?,?,?,?,?)", ("reserve:" + request_id, request_id, now, None, OrderState.CREATED.value, payload, "Intent persisted before broker submission"))
        return self.get(request_id)

    def get(self, request_id: str) -> dict[str, Any]:
        with self._connection() as db:
            row = db.execute("SELECT * FROM risk_orders WHERE request_id=?", (request_id,)).fetchone()
        if row is None:
            raise RiskStateError("Unknown order request")
        return dict(row)

    def has_unresolved(self, account_id: str) -> bool:
        with self._connection() as db:
            return db.execute("SELECT 1 FROM risk_orders WHERE account_id=? AND state IN ('unknown','submitting','cancel_pending') LIMIT 1", (account_id,)).fetchone() is not None

    def transition(self, request_id: str, new_state: OrderState | str, *, event_id: str,
                   cumulative_filled: float | None = None, broker_order_id: str | None = None,
                   reason: str = "", broker_reconciled: bool = False,
                   now: float | None = None) -> dict[str, Any]:
        now = time.time() if now is None else now
        try:
            new_state = OrderState(new_state)
        except ValueError as exc:
            raise RiskStateError("Unknown order state") from exc
        if not _text(event_id) or not _number(now, positive=True) or not isinstance(reason, str) or len(reason) > 1000:
            raise RiskStateError("Order-event metadata is invalid")
        if broker_order_id is not None and not _text(broker_order_id):
            raise RiskStateError("Broker order reference is invalid")
        payload_hash = hashlib.sha256(json.dumps([request_id, new_state.value, cumulative_filled, broker_order_id, reason, broker_reconciled], allow_nan=False).encode()).hexdigest()
        with self._connection(write=True) as db:
            row = db.execute("SELECT * FROM risk_orders WHERE request_id=?", (request_id,)).fetchone()
            if row is None:
                raise RiskStateError("Unknown order request")
            prior_event = db.execute("SELECT * FROM risk_order_events WHERE event_id=?", (event_id,)).fetchone()
            if prior_event:
                if prior_event["request_id"] != request_id or prior_event["payload_hash"] != payload_hash:
                    raise RiskStateError("An order event ID was replayed with different data")
                return dict(row)
            old = OrderState(row["state"])
            if new_state not in _TRANSITIONS[old]:
                raise RiskStateError(f"Order transition {old.value} to {new_state.value} is forbidden")
            if now < row["updated_at"] - 2:
                raise RiskStateError("Order event clock moved backwards")
            if old == OrderState.UNKNOWN and broker_reconciled is not True:
                raise RiskStateError("Uncertain submissions require authoritative broker reconciliation")
            if new_state == OrderState.SUBMITTING:
                unresolved = db.execute("SELECT 1 FROM risk_orders WHERE account_id=? AND request_id<>? AND state IN ('unknown','submitting','cancel_pending') LIMIT 1", (row["account_id"], request_id)).fetchone()
                if unresolved:
                    raise RiskStateError("Another uncertain account order must be reconciled before submission")
            filled = row["filled_volume"] if cumulative_filled is None else cumulative_filled
            if not _number(filled, nonnegative=True) or filled < row["filled_volume"] - 1e-12 or filled > row["volume"] + 1e-12:
                raise RiskStateError("Cumulative fills must be finite, monotonic and no larger than requested volume")
            if new_state == OrderState.FILLED and not math.isclose(filled, row["volume"], rel_tol=0, abs_tol=1e-10):
                raise RiskStateError("Filled state requires the entire requested volume")
            if new_state == OrderState.PARTIALLY_FILLED and not 0 < filled < row["volume"]:
                raise RiskStateError("Partial fill must be greater than zero and less than the requested volume")
            if new_state in (OrderState.CREATED, OrderState.APPROVED, OrderState.SUBMITTING, OrderState.REJECTED) and filled != 0:
                raise RiskStateError("This order state cannot discard an actual partial fill")
            if new_state in (OrderState.ACCEPTED, OrderState.PARTIALLY_FILLED, OrderState.FILLED) and not (broker_order_id or row["broker_order_id"]):
                raise RiskStateError("A confirmed broker order reference is required")
            if row["broker_order_id"] and broker_order_id and row["broker_order_id"] != broker_order_id:
                raise RiskStateError("The broker reference cannot change for an existing request")
            db.execute("""UPDATE risk_orders SET state=?,filled_volume=?,broker_order_id=?,updated_at=? WHERE request_id=?""",
                       (new_state.value, filled, broker_order_id or row["broker_order_id"], max(now, row["updated_at"]), request_id))
            db.execute("INSERT INTO risk_order_events VALUES(?,?,?,?,?,?,?)", (event_id, request_id, now, old.value, new_state.value, payload_hash, reason))
        return self.get(request_id)

    def begin_submission(self, request_id: str, *, event_id: str, now: float | None = None) -> dict[str, Any]:
        return self.transition(request_id, OrderState.SUBMITTING, event_id=event_id, now=now)

    def recover_inflight(self, *, now: float | None = None) -> int:
        """Call once after process restart; uncertain requests stay blocked, never resubmitted."""
        now = time.time() if now is None else now
        if not _number(now, positive=True):
            raise RiskStateError("Recovery time is invalid")
        with self._connection(write=True) as db:
            rows = db.execute("SELECT * FROM risk_orders WHERE state IN ('submitting','cancel_pending')").fetchall()
            for row in rows:
                event_id = f"recovery:{row['request_id']}:{now}"
                db.execute("UPDATE risk_orders SET state='unknown',updated_at=? WHERE request_id=?", (max(now, row["updated_at"]), row["request_id"]))
                db.execute("INSERT INTO risk_order_events VALUES(?,?,?,?,?,?,?)", (event_id, row["request_id"], now, row["state"], "unknown", hashlib.sha256(event_id.encode()).hexdigest(), "Restart requires broker reconciliation; no automatic resubmission"))
            return len(rows)

