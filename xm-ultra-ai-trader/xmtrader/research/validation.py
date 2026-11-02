"""Chronological research validation, with no automatic strategy promotion."""
from __future__ import annotations

from dataclasses import dataclass, replace, asdict
from typing import Iterable

from .backtest import BacktestConfig, InstrumentModel, Costs, run_backtest
from .data import Bars
from .strategies import StrategyConfig


@dataclass(frozen=True)
class WalkForwardWindow:
    train_start: int
    train_end: int  # exclusive
    test_start: int
    test_end: int  # exclusive


def walk_forward_windows(size: int, *, train_size: int, test_size: int, purge_bars: int = 1,
                         label_horizon: int = 1, expanding: bool = True) -> list[WalkForwardWindow]:
    if min(size, train_size, test_size, label_horizon) < 1 or purge_bars < label_horizon:
        raise ValueError("Positive windows and purge_bars >= label_horizon are required")
    windows = []
    train_end = train_size
    while train_end + purge_bars + test_size <= size:
        test_start = train_end + purge_bars
        windows.append(WalkForwardWindow(0 if expanding else train_end - train_size, train_end, test_start, test_start + test_size))
        train_end = test_start + test_size
    return windows


def walk_forward(bars: Bars, strategy_id: str, *, instrument: InstrumentModel, costs: Costs,
                 train_size: int, test_size: int, candidates: Iterable[StrategyConfig] | None = None,
                 purge_bars: int = 1, label_horizon: int = 1, config: BacktestConfig | None = None) -> dict:
    """Select parameters on training only; never use test results to choose a fold.

    Each fold resets equity. The result is a sequence of independent experiments,
    not a claimed continuously compounded tradable track record.
    """
    choices = list(candidates or [StrategyConfig()])
    if not choices or len(choices) > 64:
        raise ValueError("Use between 1 and 64 predefined parameter candidates")
    windows = walk_forward_windows(len(bars), train_size=train_size, test_size=test_size, purge_bars=purge_bars, label_horizon=label_horizon)
    folds = []
    for window in windows:
        training = bars.slice(window.train_start, window.train_end)
        scored = []
        for index, candidate in enumerate(choices):
            report = run_backtest(training, strategy_id, instrument=instrument, costs=costs, config=config, strategy_config=candidate)
            # Predeclared net-return-minus-drawdown score, not a success probability.
            metrics = report["metrics"]
            score = (report["final_equity"] / report["initial_equity"] - 1) * 100 - (metrics["max_drawdown_pct"] or 0)
            scored.append({"candidate": index, "score": score, "sample_size": metrics["sample_size"]})
        eligible = [row for row in scored if row["sample_size"] >= 5]
        if not eligible:
            folds.append({"window": asdict(window), "status": "insufficient_training_trades", "training_scores": scored})
            continue
        winner = max(eligible, key=lambda item: (item["score"], -item["candidate"]))
        # Indicators can use historical warmup; entries cannot occur before test_start.
        test = run_backtest(bars.slice(0, window.test_end), strategy_id, instrument=instrument, costs=costs,
                            config=config, strategy_config=choices[winner["candidate"]], trade_start_index=window.test_start)
        folds.append({"window": asdict(window), "status": "evaluated", "selected_parameters": asdict(choices[winner["candidate"]]),
                      "training_scores": scored, "out_of_sample_metrics": test["metrics"], "out_of_sample_trades": test["trades"]})
    return {"simulated": True, "folds": folds, "selection_rule": "Training net return percent minus max drawdown percent, at least five training closes",
            "promotion_allowed": False, "warning": "A separate untouched final holdout and independent demo review are still required. Fold equity resets."}


def sensitivity_analysis(bars: Bars, strategy_id: str, *, instrument: InstrumentModel, costs: Costs,
                         parameter: str, values: list, base: StrategyConfig | None = None,
                         config: BacktestConfig | None = None) -> dict:
    if parameter not in StrategyConfig.__dataclass_fields__ or not 1 <= len(values) <= 32:
        raise ValueError("A valid StrategyConfig field and 1–32 values are required")
    base = base or StrategyConfig()
    results = []
    for value in values:
        parameters = replace(base, **{parameter: value})
        report = run_backtest(bars, strategy_id, instrument=instrument, costs=costs, config=config, strategy_config=parameters)
        results.append({"value": value, "metrics": report["metrics"]})
    return {"simulated": True, "parameter": parameter, "results": results,
            "warning": "These comparisons use the same sample and are not independent out-of-sample evidence."}


@dataclass(frozen=True)
class PromotionRequirements:
    minimum_closed_trades: int = 100
    minimum_oos_folds: int = 3
    minimum_profit_factor: float = 1.1
    maximum_drawdown_pct: float = 5.0
    require_positive_expectancy: bool = True


def review_candidate(*, holdout_metrics: dict, out_of_sample_folds: int, sensitivity_passed: bool,
                     untouched_holdout: bool, costs_verified: bool, independent_demo_review: bool,
                     requirements: PromotionRequirements | None = None) -> dict:
    """Evidence checklist only. No live toggle, auto-retraining or deployment hook."""
    r = requirements or PromotionRequirements()
    reasons = []
    if holdout_metrics.get("sample_size", 0) < r.minimum_closed_trades:
        reasons.append("Insufficient untouched-holdout closed trades")
    if out_of_sample_folds < r.minimum_oos_folds:
        reasons.append("Insufficient out-of-sample folds")
    if holdout_metrics.get("profit_factor") is None or holdout_metrics["profit_factor"] < r.minimum_profit_factor:
        reasons.append("Profit factor has not met the predefined threshold")
    if holdout_metrics.get("max_drawdown_pct") is None or holdout_metrics["max_drawdown_pct"] > r.maximum_drawdown_pct:
        reasons.append("Drawdown evidence is missing or over the limit")
    if r.require_positive_expectancy and (holdout_metrics.get("expectancy") is None or holdout_metrics["expectancy"] <= 0):
        reasons.append("Positive net expectancy has not been established")
    for yes, reason in ((sensitivity_passed, "Sensitivity review incomplete"), (untouched_holdout, "Holdout independence unverified"),
                        (costs_verified, "Trading-cost assumptions unverified"), (independent_demo_review, "Independent demo review incomplete")):
        if not yes:
            reasons.append(reason)
    return {"status": "needs_review" if reasons else "eligible_for_human_review", "reasons": reasons,
            "requirements": asdict(r), "automatic_promotion": False, "live_enabled": False}
