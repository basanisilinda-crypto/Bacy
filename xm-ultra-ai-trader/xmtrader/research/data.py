"""Validated, explicitly sourced OHLC input. No implicit market-data download."""
from __future__ import annotations

import csv
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable, Mapping

import numpy as np


def utc_timestamp(value: str | float | int | datetime) -> float:
    if isinstance(value, datetime):
        if value.tzinfo is None:
            raise ValueError("Timestamps must include a timezone; broker time is not necessarily UTC")
        return value.timestamp()
    if isinstance(value, str):
        try:
            return float(value)
        except ValueError:
            parsed = datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
            if parsed.tzinfo is None:
                raise ValueError("ISO timestamps must include a timezone")
            return parsed.timestamp()
    return float(value)


@dataclass(frozen=True)
class Bars:
    timestamp: np.ndarray
    open: np.ndarray
    high: np.ndarray
    low: np.ndarray
    close: np.ndarray
    volume: np.ndarray
    symbol: str
    timeframe_seconds: int
    source: str
    simulated: bool = False

    def __post_init__(self) -> None:
        if not self.source.strip() or not self.symbol.strip():
            raise ValueError("A source description and symbol are required")
        if self.timeframe_seconds <= 0:
            raise ValueError("timeframe_seconds must be positive")
        names = ("timestamp", "open", "high", "low", "close", "volume")
        for name in names:
            array = np.asarray(getattr(self, name), dtype=float).copy()
            if array.ndim != 1 or not np.all(np.isfinite(array)):
                raise ValueError(f"{name}: expected a one-dimensional finite series")
            array.flags.writeable = False
            object.__setattr__(self, name, array)
        sizes = {len(getattr(self, field)) for field in names}
        if len(sizes) != 1 or len(self.close) == 0:
            raise ValueError("OHLC arrays must have equal nonzero lengths")
        if np.any(np.diff(self.timestamp) <= 0):
            raise ValueError("Bar timestamps must be unique and strictly increasing")
        if np.any(self.timestamp <= 0) or np.any(self.low <= 0) or np.any(self.volume < 0):
            raise ValueError("Prices/timestamps must be positive and volume nonnegative")
        if np.any(self.high < np.maximum(self.open, self.close)) or np.any(self.low > np.minimum(self.open, self.close)):
            raise ValueError("Open/close must lie inside each bar's high/low")
        if np.any(self.high < self.low):
            raise ValueError("High cannot be below low")

    def __len__(self) -> int:
        return len(self.close)

    def slice(self, start: int | None = None, stop: int | None = None) -> "Bars":
        fields = {name: getattr(self, name)[start:stop] for name in ("timestamp", "open", "high", "low", "close", "volume")}
        return Bars(**fields, symbol=self.symbol, timeframe_seconds=self.timeframe_seconds, source=self.source, simulated=self.simulated)

    @property
    def gaps(self) -> list[dict[str, float]]:
        """Gaps can be holidays/weekends; report them instead of manufacturing bars."""
        return [{"after": float(self.timestamp[i]), "seconds": float(gap)} for i, gap in enumerate(np.diff(self.timestamp)) if gap > self.timeframe_seconds * 1.5]


def from_records(records: Iterable[Mapping], *, symbol: str, timeframe_seconds: int, source: str, simulated: bool = False) -> Bars:
    rows = list(records)
    if not rows:
        raise ValueError("No OHLC records")
    fields = {name: [] for name in ("timestamp", "open", "high", "low", "close", "volume")}
    for row in rows:
        fields["timestamp"].append(utc_timestamp(row["timestamp"]))
        for name in ("open", "high", "low", "close"):
            fields[name].append(float(row[name]))
        fields["volume"].append(float(row.get("volume", 0)))
    return Bars(**fields, symbol=symbol, timeframe_seconds=timeframe_seconds, source=source, simulated=simulated)


def load_csv(path: str | Path, *, symbol: str, timeframe_seconds: int, source: str, simulated: bool = False) -> Bars:
    """CSV header: timestamp,open,high,low,close[,volume]; UTC Unix or offset ISO time."""
    with Path(path).open("r", encoding="utf-8-sig", newline="") as handle:
        return from_records(csv.DictReader(handle), symbol=symbol, timeframe_seconds=timeframe_seconds, source=source, simulated=simulated)


def aggregate_completed(bars: Bars, factor: int) -> tuple[Bars, np.ndarray]:
    """UTC-aligned higher-timeframe bars with mapping that excludes unfinished bars.

    Input timestamps identify bar OPEN time. A lower bar can only see a higher bar
    once its closing time has passed. Incomplete groups (including gaps) are omitted.
    Mapping -1 means there is no completed higher timeframe bar yet.
    """
    if not isinstance(factor, int) or factor < 2:
        raise ValueError("Aggregation factor must be an integer >=2")
    width = bars.timeframe_seconds * factor
    buckets = np.floor(bars.timestamp / width).astype(np.int64)
    rows: list[dict] = []
    for bucket in np.unique(buckets):
        ix = np.flatnonzero(buckets == bucket)
        expected = bucket * width + np.arange(factor) * bars.timeframe_seconds
        if len(ix) != factor or not np.array_equal(bars.timestamp[ix], expected):
            continue
        rows.append({"timestamp": float(bucket * width), "open": bars.open[ix[0]], "high": np.max(bars.high[ix]), "low": np.min(bars.low[ix]), "close": bars.close[ix[-1]], "volume": np.sum(bars.volume[ix])})
    if not rows:
        raise ValueError("No complete higher-timeframe bars")
    higher = from_records(rows, symbol=bars.symbol, timeframe_seconds=width, source=f"UTC aggregation of {bars.source}", simulated=bars.simulated)
    observed_close = bars.timestamp + bars.timeframe_seconds
    mapping = np.searchsorted(higher.timestamp + width, observed_close, side="right") - 1
    return higher, mapping
