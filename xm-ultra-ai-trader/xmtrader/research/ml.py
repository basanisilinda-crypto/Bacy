"""Optional lightweight sklearn research. Training never touches the trading bridge."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import numpy as np

from .data import Bars
from . import indicators as ta


FEATURE_NAMES = ("return_1", "momentum_5", "momentum_20", "rsi_14", "atr_fraction", "ema_distance", "body_fraction", "range_fraction")


@dataclass(frozen=True)
class Dataset:
    x: np.ndarray
    y: np.ndarray
    future_return: np.ndarray
    timestamp: np.ndarray
    original_index: np.ndarray
    horizon: int


def build_dataset(bars: Bars, *, horizon: int = 5, minimum_net_move: float = 0.0) -> Dataset:
    if type(horizon) is not int or horizon < 1 or not np.isfinite(minimum_net_move) or minimum_net_move < 0:
        raise ValueError("Positive integer horizon and nonnegative minimum move are required")
    close = bars.close
    x = np.column_stack([ta.momentum(close, 1), ta.momentum(close, 5), ta.momentum(close, 20),
                         ta.rsi(close) / 100, ta.atr(bars) / close, (ta.ema(close, 10) - ta.ema(close, 30)) / close,
                         (close - bars.open) / close, (bars.high - bars.low) / close])
    future = np.full(len(bars), np.nan)
    future[:-horizon] = close[horizon:] / close[:-horizon] - 1
    valid = np.all(np.isfinite(x), axis=1) & np.isfinite(future)
    if np.sum(valid) < 10:
        raise ValueError("Not enough history after feature warmup and label horizon")
    # Binary target means future move exceeds explicitly supplied estimated costs.
    return Dataset(x[valid], (future[valid] > minimum_net_move).astype(int), future[valid],
                   bars.timestamp[valid] + bars.timeframe_seconds, np.flatnonzero(valid), horizon)


def temporal_partitions(dataset: Dataset, train_fraction: float = .6, calibration_fraction: float = .2) -> dict[str, np.ndarray]:
    if not .3 <= train_fraction <= .8 or not .1 <= calibration_fraction <= .4 or train_fraction + calibration_fraction >= .95:
        raise ValueError("Invalid chronological split proportions")
    n = len(dataset.y)
    train_stop, cal_stop = int(n * train_fraction), int(n * (train_fraction + calibration_fraction))
    if train_stop == 0 or cal_stop >= n:
        raise ValueError("Insufficient data for chronological split")
    indices = dataset.original_index
    # Purge examples whose future label touches calibration/test features.
    train = np.flatnonzero((np.arange(n) < train_stop) & (indices + dataset.horizon < indices[train_stop]))
    calibration = np.flatnonzero((np.arange(n) >= train_stop) & (np.arange(n) < cal_stop) & (indices + dataset.horizon < indices[cal_stop]))
    test = np.arange(cal_stop, n)
    if len(train) < 50 or len(calibration) < 25 or len(test) < 25:
        raise ValueError("Need at least 50 train, 25 calibration and 25 test observations after purging")
    return {"train": train, "calibration": calibration, "test": test}


def _dependencies():
    try:
        from sklearn import ensemble, linear_model, metrics, preprocessing, pipeline
    except ImportError as exc:
        raise RuntimeError("Optional research models require the free scikit-learn extra; trading does not depend on it") from exc
    return ensemble, linear_model, metrics, preprocessing, pipeline


@dataclass
class ResearchModel:
    estimator: Any
    calibrator: Any
    report: dict

    def predict_probability(self, features: np.ndarray) -> np.ndarray:
        if self.report["task"] != "classification":
            raise ValueError("This is not a probability model")
        probability = np.clip(self.estimator.predict_proba(features)[:, 1], 1e-6, 1 - 1e-6)
        logits = np.log(probability / (1 - probability)).reshape(-1, 1)
        return self.calibrator.predict_proba(logits)[:, 1]


def train_classifier(dataset: Dataset, *, model: str = "logistic", seed: int = 17) -> ResearchModel:
    ensemble, linear, metrics, prep, pipeline = _dependencies()
    splits = temporal_partitions(dataset)
    train, calibration, test = (splits[name] for name in ("train", "calibration", "test"))
    if len(np.unique(dataset.y[train])) < 2 or len(np.unique(dataset.y[calibration])) < 2:
        raise ValueError("Both classes are required in training and independent calibration")
    options = {
        "logistic": lambda: linear.LogisticRegression(C=.5, max_iter=400, random_state=seed),
        "random_forest": lambda: ensemble.RandomForestClassifier(n_estimators=64, max_depth=5, min_samples_leaf=10, random_state=seed, n_jobs=1),
        "gradient_boosting": lambda: ensemble.GradientBoostingClassifier(n_estimators=60, max_depth=2, learning_rate=.05, min_samples_leaf=10, random_state=seed),
    }
    if model not in options:
        raise ValueError("Choose logistic, random_forest or gradient_boosting")
    # Scaling and estimator see train only. Calibration sees a later purged set.
    estimator = pipeline.make_pipeline(prep.StandardScaler(), options[model]())
    estimator.fit(dataset.x[train], dataset.y[train])
    raw_calibration = np.clip(estimator.predict_proba(dataset.x[calibration])[:, 1], 1e-6, 1 - 1e-6)
    calibrator = linear.LogisticRegression(C=1, max_iter=300, random_state=seed)
    calibrator.fit(np.log(raw_calibration / (1 - raw_calibration)).reshape(-1, 1), dataset.y[calibration])
    result = ResearchModel(estimator, calibrator, {"task": "classification"})
    probabilities = result.predict_probability(dataset.x[test])
    y = dataset.y[test]
    prior = float(np.mean(dataset.y[train]))
    result.report = {
        "task": "classification", "model": model, "feature_names": list(FEATURE_NAMES), "label_horizon_bars": dataset.horizon,
        "splits": _split_report(dataset, splits), "probability_method": "Logistic sigmoid calibration on independent later data",
        "test_metrics": {"brier_score": float(metrics.brier_score_loss(y, probabilities)),
                         "training_prior_baseline_brier": float(metrics.brier_score_loss(y, np.full(len(y), prior))),
                         "log_loss": float(metrics.log_loss(y, probabilities, labels=[0, 1])),
                         "accuracy": float(metrics.accuracy_score(y, probabilities >= .5)),
                         "roc_auc": float(metrics.roc_auc_score(y, probabilities)) if len(np.unique(y)) == 2 else None},
        "reliability_bins": _calibration_bins(y, probabilities), "deployed": False, "live_enabled": False,
        "warning": "Estimated probabilities may be miscalibrated under changing regimes. Test labels were never fitted. No automatic deployment.",
    }
    return result


def _split_report(dataset: Dataset, splits: dict[str, np.ndarray]) -> dict:
    return {name: {"observations": len(index), "first_index": int(dataset.original_index[index[0]]),
                   "last_index": int(dataset.original_index[index[-1]]), "first_time": float(dataset.timestamp[index[0]]),
                   "last_time": float(dataset.timestamp[index[-1]])} for name, index in splits.items()}


def _calibration_bins(labels, probabilities) -> list:
    bins = []
    for low in np.arange(0, 1, .2):
        selection = (probabilities >= low) & (probabilities < low + .2 + (1e-12 if low > .79 else 0))
        bins.append({"lower": float(low), "upper": float(low + .2), "observations": int(np.sum(selection)),
                     "mean_predicted": float(np.mean(probabilities[selection])) if np.any(selection) else None,
                     "observed_frequency": float(np.mean(labels[selection])) if np.any(selection) else None})
    return bins


def train_regressor(dataset: Dataset, *, model: str = "ridge", seed: int = 17) -> ResearchModel:
    ensemble, linear, metrics, prep, pipeline = _dependencies()
    splits = temporal_partitions(dataset)
    train, test = splits["train"], splits["test"]
    options = {"ridge": lambda: linear.Ridge(alpha=10),
               "random_forest": lambda: ensemble.RandomForestRegressor(n_estimators=64, max_depth=5, min_samples_leaf=10, n_jobs=1, random_state=seed),
               "gradient_boosting": lambda: ensemble.GradientBoostingRegressor(n_estimators=60, max_depth=2, learning_rate=.05, min_samples_leaf=10, random_state=seed)}
    if model not in options:
        raise ValueError("Choose ridge, random_forest or gradient_boosting")
    estimator = pipeline.make_pipeline(prep.StandardScaler(), options[model]())
    estimator.fit(dataset.x[train], dataset.future_return[train])
    predicted = estimator.predict(dataset.x[test])
    return ResearchModel(estimator, None, {"task": "regression", "model": model, "splits": _split_report(dataset, splits),
                         "test_metrics": {"mean_absolute_error": float(metrics.mean_absolute_error(dataset.future_return[test], predicted)),
                                          "zero_return_baseline_mae": float(np.mean(np.abs(dataset.future_return[test])))},
                         "deployed": False, "live_enabled": False, "warning": "Predictions are research estimates, not expected guaranteed returns."})


def train_anomaly_detector(dataset: Dataset, *, seed: int = 17) -> ResearchModel:
    ensemble, linear, metrics, prep, pipeline = _dependencies()
    splits = temporal_partitions(dataset)
    train, test = splits["train"], splits["test"]
    estimator = pipeline.make_pipeline(prep.StandardScaler(), ensemble.IsolationForest(n_estimators=64, max_samples=256, contamination="auto", random_state=seed, n_jobs=1))
    estimator.fit(dataset.x[train])
    anomaly = estimator.predict(dataset.x[test]) == -1
    return ResearchModel(estimator, None, {"task": "anomaly_detection", "model": "isolation_forest", "splits": _split_report(dataset, splits),
                         "test_flagged_observations": int(np.sum(anomaly)), "test_observations": len(test),
                         "deployed": False, "live_enabled": False, "warning": "Anomaly means statistically unusual, not fraudulent or predictably losing."})
