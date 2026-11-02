"""Deterministic risk checks; all account values here are synthetic test data."""
from dataclasses import replace
from datetime import datetime, timezone
from pathlib import Path
import tempfile
import unittest

from xmtrader.risk import (
    AccountSnapshot, Exposure, InstrumentSpec, RiskLedger, RiskStateError, TradeIntent, evaluate_trade, normalize_protective_prices, validate_limits,
)


NOW = datetime(2026, 11, 2, 12, tzinfo=timezone.utc).timestamp()


def account(**changes):
    default = AccountSnapshot("demo-A", "USD", 10000.0, 10000.0, 0.0, 10000.0, NOW,
                              day_start_equity=10000, week_start_equity=10000, high_water_mark=10000,
                              history_complete=True, exposure_complete=True)
    return replace(default, **changes)


def instrument(**changes):
    default = InstrumentSpec("EURUSD.demo", 1.1000, 1.1002, NOW, 0.00001, 0.00001,
                             0.01, 100.0, 0.01, min_stop_points=20)
    return replace(default, **changes)


def intent(**changes):
    default = TradeIntent("EURUSD.demo", "buy", 1.1002, 1.0982, 200.0, 1000.0, "USD",
                          take_profit=1.1042, estimated_cost_per_lot=10.0, news_clear=True)
    return replace(default, **changes)


class RiskDecisionTests(unittest.TestCase):
    def decision(self, *, snapshot=None, spec=None, trade=None, exposures=(), **kwargs):
        return evaluate_trade(snapshot or account(), spec or instrument(), trade or intent(), exposures, now=NOW, **kwargs)

    def assert_code(self, expected, **kwargs):
        result = self.decision(**kwargs)
        self.assertFalse(result.approved)
        self.assertIn(expected, result.reason_codes)
        self.assertEqual(result.volume, 0)

    def test_demo_size_floors_lots_and_includes_costs(self):
        result = self.decision()
        self.assertTrue(result.approved)
        self.assertEqual(result.volume, 0.11)
        self.assertAlmostEqual(result.estimated_cash_risk, 23.1)
        self.assertLessEqual(result.estimated_cash_risk, 25)
        self.assertEqual(result.estimated_margin, 110)

    def test_sizing_does_not_increase_after_loss(self):
        original = self.decision()
        after_loss = self.decision(snapshot=account(equity=9950, free_margin=9950))
        self.assertTrue(after_loss.approved)
        self.assertLessEqual(after_loss.volume, original.volume)

    def test_live_accounts_always_rejected(self):
        self.assert_code("demo_only", snapshot=account(mode="live"))

    def test_missing_history_and_exposure_fail_closed(self):
        self.assert_code("incomplete_account", snapshot=account(history_complete=False))
        self.assert_code("incomplete_account", snapshot=account(exposure_complete=False))

    def test_missing_or_nonfinite_anchors_fail_closed(self):
        for value in (None, 0, -1, float("nan"), float("inf"), True):
            with self.subTest(value=value):
                self.assert_code("missing_anchors", snapshot=account(day_start_equity=value))

    def test_account_and_quote_freshness_both_checked(self):
        self.assert_code("stale_account", snapshot=account(timestamp=NOW - 11))
        self.assert_code("stale_account", snapshot=account(timestamp=NOW + 3))
        self.assert_code("stale_quote", spec=instrument(quote_time=NOW - 6))
        self.assert_code("stale_quote", spec=instrument(quote_time=NOW + 3))

    def test_missing_connectivity_permission_and_session(self):
        self.assert_code("account_unavailable", snapshot=account(connected=False))
        self.assert_code("account_unavailable", snapshot=account(trade_allowed=False))
        self.assert_code("market_closed", spec=instrument(session_open=False))
        self.assert_code("market_closed", spec=instrument(trade_mode="short_only"))
        self.assert_code("liquidity", spec=instrument(liquid=False))

    def test_stops_are_mandatory_valid_and_tick_aligned(self):
        self.assert_code("missing_stop", trade=intent(stop_loss=0))
        self.assert_code("invalid_stop", trade=intent(stop_loss=1.1010))
        self.assert_code("invalid_stop", trade=intent(stop_loss=1.1001))
        self.assert_code("price_increment", trade=intent(stop_loss=1.098201))
        self.assert_code("invalid_take_profit", trade=intent(take_profit=1.0990))

    def test_stop_rounding_preserves_protective_direction(self):
        self.assertEqual(normalize_protective_prices("buy", 1.098207, 1.104201, 0.00001), (1.0982, 1.10421))
        self.assertEqual(normalize_protective_prices("sell", 1.102201, 1.096207, 0.00001), (1.10221, 1.0962))

    def test_stop_gap_based_on_brokers_closing_quote(self):
        self.assert_code("invalid_stop", spec=instrument(min_stop_points=30), trade=intent(stop_loss=1.0998))

    def test_spread_and_entry_tolerance(self):
        self.assert_code("spread_limit", spec=instrument(max_spread_points=10))
        self.assert_code("price_moved", trade=intent(entry_price=1.1005))

    def test_currency_and_cash_estimates_are_required(self):
        self.assert_code("currency_mismatch", trade=intent(account_currency="EUR"))
        self.assert_code("unknown_cash_risk", trade=intent(loss_per_lot=None))
        self.assert_code("unknown_cash_risk", trade=intent(margin_per_lot=float("nan")))
        self.assert_code("unknown_cash_risk", trade=intent(estimated_cost_per_lot=-1))

    def test_minimum_lot_not_rounded_up(self):
        self.assert_code("minimum_volume_risk", spec=instrument(volume_min=1, volume_step=1))

    def test_small_volume_cap_respected(self):
        result = self.decision(trade=intent(volume_cap=0.03))
        self.assertTrue(result.approved)
        self.assertEqual(result.volume, 0.03)

    def test_margin_utilization_limits_total_not_just_increment(self):
        self.assert_code("margin_limit", snapshot=account(margin=2500, free_margin=7500))
        result = self.decision(snapshot=account(margin=2450, free_margin=7550))
        self.assertTrue(result.approved)
        self.assertEqual(result.volume, 0.05)

    def test_unrealized_loss_hits_daily_limit_even_if_balance_unchanged(self):
        self.assert_code("daily_loss_limit", snapshot=account(equity=9899, balance=10000, free_margin=9899))

    def test_weekly_and_high_water_limits_independent(self):
        self.assert_code("weekly_loss_limit", snapshot=account(equity=9690, balance=10000, free_margin=9690, day_start_equity=9690))
        self.assert_code("drawdown_limit", snapshot=account(equity=9490, balance=10000, free_margin=9490, day_start_equity=9490, week_start_equity=9490))

    def test_exposure_includes_manual_positions_and_pending_orders(self):
        manual = Exposure("EURUSD.demo", "buy", 0.1, 15, "USD", owned_by_robot=False)
        result = self.decision(exposures=[manual])
        self.assertTrue(result.approved)
        self.assertLessEqual(result.estimated_cash_risk, 10)
        self.assertEqual(result.existing_cash_risk, 15)
        pending = Exposure("EURUSD.demo", "sell", 0.1, 5, "USD", pending=True)
        self.assert_code("position_limit", exposures=[manual, pending])

    def test_unknown_unprotected_exposure_rejected(self):
        self.assert_code("unknown_exposure", exposures=[Exposure("EURUSD.demo", "buy", 1, None, "USD")])
        self.assert_code("unknown_exposure", exposures=[Exposure("EURUSD.demo", "buy", 1, 1, "USD", protective_stop_valid=False)])

    def test_correlations_required_and_inverse_not_assumed_hedge(self):
        other = Exposure("USDCHF.demo", "buy", 0.1, 25, "USD")
        self.assert_code("unknown_correlation", exposures=[other])
        self.assert_code("exposure_limit", exposures=[other], correlations={("EURUSD.demo", "USDCHF.demo"): -0.9})
        result = self.decision(exposures=[other], correlations={("EURUSD.demo", "USDCHF.demo"): 0.1})
        self.assertTrue(result.approved)

    def test_remaining_daily_budget_reserves_existing_stop_risk(self):
        existing = Exposure("USDCHF.demo", "buy", 0.01, 4, "USD")
        result = self.decision(snapshot=account(equity=9910, free_margin=9910), exposures=[existing], correlations={("EURUSD.demo", "USDCHF.demo"): 0.1})
        self.assertTrue(result.approved)
        self.assertLessEqual(result.estimated_cash_risk + result.existing_cash_risk, 10)

    def test_pending_margin_reserved(self):
        pending = Exposure("EURUSD.demo", "buy", 0.01, 1, "USD", pending=True, margin_reserved=2490)
        result = self.decision(exposures=[pending])
        self.assertTrue(result.approved)
        self.assertEqual(result.volume, 0.01)

    def test_news_fails_closed_when_required(self):
        self.assert_code("news_uncertain", trade=intent(news_clear=False))
        self.assertTrue(self.decision(trade=intent(news_required=False, news_clear=False)).approved)

    def test_cooldown_persists_until_review_even_after_time_passes(self):
        self.assert_code("loss_cooldown", snapshot=account(consecutive_losses=3, cooldown_until=NOW - 1))
        self.assert_code("loss_cooldown", snapshot=account(cooldown_until=NOW + 1))

    def test_pending_price_direction(self):
        self.assertTrue(self.decision(trade=intent(order_type="limit", entry_price=1.0990, stop_loss=1.0970, take_profit=1.1030)).approved)
        self.assert_code("invalid_pending_price", trade=intent(order_type="limit", entry_price=1.1008, stop_loss=1.0988, take_profit=1.1048))

    def test_limits_cannot_loosen_defaults_or_use_nonfinite_values(self):
        for values in ({"risk_per_trade_pct": 0.26}, {"daily_loss_pct": 2}, {"weekly_loss_pct": 4},
                       {"max_drawdown_pct": 6}, {"max_positions": 3}, {"max_positions": True},
                       {"max_consecutive_losses": 4}, {"daily_loss_pct": float("nan")},
                       {"unknown": 1}, {"cooldown_seconds": 30}):
            with self.subTest(values=values), self.assertRaises(ValueError):
                validate_limits(values)
        self.assertEqual(validate_limits({"risk_per_trade_pct": 0.1})["risk_per_trade_pct"], 0.1)


class RiskLedgerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "risk.sqlite"
        self.ledger = RiskLedger(self.path)

    def tearDown(self):
        self.temp.cleanup()

    def initialize(self, name="demo-A"):
        return self.ledger.initialize(name, "USD", equity=10000, day_start_equity=10000,
                                      week_start_equity=10000, high_water_mark=10000, now=NOW,
                                      history_verified=True, acknowledge=True)

    def test_missing_state_and_unverified_initialization_fail_closed(self):
        with self.assertRaises(RiskStateError):
            self.ledger.snapshot("demo-A")
        with self.assertRaises(RiskStateError):
            self.ledger.initialize("demo-A", "USD", equity=10000, day_start_equity=10000,
                                   week_start_equity=10000, high_water_mark=10000,
                                   now=NOW, history_verified=False, acknowledge=True)

    def test_peak_survives_restart_and_cannot_be_reinitialized(self):
        self.initialize()
        self.ledger.observe("demo-A", "USD", equity=10300, now=NOW + 1, history_verified=True)
        restored = RiskLedger(self.path)
        snapshot = restored.observe("demo-A", "USD", equity=9900, now=NOW + 2, history_verified=True)
        self.assertEqual(snapshot["high_water_mark"], 10300)
        self.assertEqual(snapshot["day_start_equity"], 10000)
        with self.assertRaises(RiskStateError):
            self.initialize()

    def test_account_isolation_and_currency_validation(self):
        self.initialize()
        self.initialize("demo-B")
        self.ledger.observe("demo-A", "USD", equity=10500, now=NOW + 1, history_verified=True)
        self.assertEqual(self.ledger.snapshot("demo-B")["high_water_mark"], 10000)
        with self.assertRaises(RiskStateError):
            self.ledger.observe("demo-A", "EUR", equity=10000, now=NOW + 2, history_verified=True)

    def test_rollover_requires_verified_anchors_does_not_guess(self):
        self.initialize()
        tomorrow = NOW + 86400
        with self.assertRaises(RiskStateError):
            self.ledger.observe("demo-A", "USD", equity=9800, now=tomorrow, history_verified=True)
        self.assertEqual(self.ledger.snapshot("demo-A")["day_start_equity"], 10000)
        result = self.ledger.observe("demo-A", "USD", equity=9800, now=tomorrow, history_verified=True, day_start_equity=9900)
        self.assertEqual(result["day_start_equity"], 9900)
        self.assertEqual(result["week_start_equity"], 10000)
        self.assertEqual(result["high_water_mark"], 10000)
        with self.assertRaises(RiskStateError):
            self.ledger.observe("demo-A", "USD", equity=9800, now=tomorrow + 1, history_verified=True, day_start_equity=9800)

    def test_week_rollover_does_not_reset_peak(self):
        self.initialize()
        future = NOW + 7 * 86400
        with self.assertRaises(RiskStateError):
            self.ledger.observe("demo-A", "USD", equity=9700, now=future, history_verified=True, day_start_equity=9700)
        state = self.ledger.observe("demo-A", "USD", equity=9700, now=future, history_verified=True,
                                    day_start_equity=9700, week_start_equity=9800)
        self.assertEqual(state["high_water_mark"], 10000)
        self.assertEqual(state["week_start_equity"], 9800)

    def test_clock_rollback_fails_closed(self):
        self.initialize()
        with self.assertRaises(RiskStateError):
            self.ledger.observe("demo-A", "USD", equity=10000, now=NOW - 10, history_verified=True)

    def test_cash_flow_exactly_once_preserves_existing_loss_amount(self):
        self.initialize()
        self.ledger.observe("demo-A", "USD", equity=9900, now=NOW + 1, history_verified=True)
        self.assertTrue(self.ledger.record_cash_flow("demo-A", "deposit-1", amount=1000, timestamp=NOW + 2, verified=True))
        state = self.ledger.snapshot("demo-A")
        self.assertEqual(state["high_water_mark"] - state["last_equity"], 100)
        self.assertFalse(self.ledger.record_cash_flow("demo-A", "deposit-1", amount=1000, timestamp=NOW + 2, verified=True))
        with self.assertRaises(RiskStateError):
            self.ledger.record_cash_flow("demo-A", "deposit-1", amount=2000, timestamp=NOW + 2, verified=True)

    def test_loss_streak_dedup_and_explicit_cooldown_review(self):
        self.initialize()
        for index in range(3):
            self.assertTrue(self.ledger.record_closed_trade("demo-A", f"trade-{index}", net_profit=-5, timestamp=NOW + index))
        self.assertFalse(self.ledger.record_closed_trade("demo-A", "trade-2", net_profit=-5, timestamp=NOW + 2))
        state = self.ledger.snapshot("demo-A")
        self.assertEqual(state["consecutive_losses"], 3)
        with self.assertRaises(RiskStateError):
            self.ledger.acknowledge_cooldown("demo-A", now=NOW + 100, history_verified=True, acknowledge=True)
        self.ledger.acknowledge_cooldown("demo-A", now=NOW + 3603, history_verified=True, acknowledge=True)
        state = self.ledger.snapshot("demo-A")
        self.assertEqual(state["consecutive_losses"], 0)
        self.assertEqual(state["high_water_mark"], 10000)

    def test_corrupt_store_does_not_start_fresh(self):
        broken = Path(self.temp.name) / "broken.sqlite"
        broken.write_bytes(b"not a sqlite database")
        with self.assertRaises(RiskStateError):
            RiskLedger(broken)


if __name__ == "__main__":
    unittest.main()
