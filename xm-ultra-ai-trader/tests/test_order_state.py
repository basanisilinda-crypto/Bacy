"""Synthetic broker outcomes exercise exactly-once bookkeeping, not real orders."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import tempfile
import unittest

from xmtrader.risk import OrderJournal, OrderState, RiskStateError


NOW = 1793620800.0


class OrderJournalTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "orders.sqlite"
        self.journal = OrderJournal(self.path)

    def tearDown(self):
        self.temp.cleanup()

    def reserve(self, request="request-1", signal="signal-1", account="demo-A"):
        return self.journal.reserve(request_id=request, account_id=account, signal_id=signal,
                                    symbol="EURUSD.demo", side="buy", volume=0.1, now=NOW)

    def approved(self, request="request-1", signal="signal-1", account="demo-A"):
        self.reserve(request, signal, account)
        return self.journal.transition(request, OrderState.APPROVED, event_id="approve:" + request, now=NOW + 1)

    def submitted(self, request="request-1", signal="signal-1", account="demo-A"):
        self.approved(request, signal, account)
        return self.journal.begin_submission(request, event_id="submit:" + request, now=NOW + 2)

    def test_reservation_is_idempotent_but_cannot_change_intent(self):
        self.assertEqual(self.reserve(), self.reserve())
        with self.assertRaises(RiskStateError):
            self.journal.reserve(request_id="request-1", account_id="demo-A", signal_id="signal-1",
                                 symbol="EURUSD.demo", side="buy", volume=0.2, now=NOW)
        with self.assertRaises(RiskStateError):
            self.reserve("request-2", "signal-1")

    def test_cannot_submit_without_approval(self):
        self.reserve()
        with self.assertRaises(RiskStateError):
            self.journal.begin_submission("request-1", event_id="submit", now=NOW + 1)

    def test_submission_is_not_a_fill_and_cannot_be_repeated(self):
        state = self.submitted()
        self.assertEqual(state["state"], "submitting")
        self.assertEqual(state["filled_volume"], 0)
        with self.assertRaises(RiskStateError):
            self.journal.begin_submission("request-1", event_id="submit-again", now=NOW + 3)

    def test_concurrent_submissions_only_one_transition_wins(self):
        self.approved()
        def run(number):
            try:
                self.journal.begin_submission("request-1", event_id=f"submit-{number}", now=NOW + 2)
                return True
            except RiskStateError:
                return False
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(run, (1, 2)))
        self.assertEqual(sum(results), 1)

    def test_restart_marks_uncertain_and_does_not_resubmit(self):
        self.submitted()
        restarted = OrderJournal(self.path)
        self.assertEqual(restarted.recover_inflight(now=NOW + 5), 1)
        self.assertEqual(restarted.get("request-1")["state"], "unknown")
        self.assertEqual(restarted.recover_inflight(now=NOW + 6), 0)
        self.assertTrue(restarted.has_unresolved("demo-A"))
        with self.assertRaises(RiskStateError):
            restarted.transition("request-1", "accepted", event_id="no-proof", broker_order_id="broker-1", now=NOW + 6)
        state = restarted.transition("request-1", "accepted", event_id="reconcile", broker_order_id="broker-1", broker_reconciled=True, now=NOW + 6)
        self.assertEqual(state["state"], "accepted")
        self.assertFalse(restarted.has_unresolved("demo-A"))

    def test_uncertain_order_blocks_new_requests_on_same_account(self):
        self.submitted()
        self.journal.transition("request-1", "unknown", event_id="timeout", now=NOW + 3)
        self.approved("request-2", "signal-2")
        with self.assertRaises(RiskStateError):
            self.journal.begin_submission("request-2", event_id="second-submit", now=NOW + 4)
        self.approved("request-3", "signal-3", account="demo-B")
        self.assertEqual(self.journal.begin_submission("request-3", event_id="other-account", now=NOW + 4)["state"], "submitting")

    def test_partial_fills_must_be_monotonic_and_broker_confirmed(self):
        self.submitted()
        with self.assertRaises(RiskStateError):
            self.journal.transition("request-1", "partially_filled", event_id="missing-reference", cumulative_filled=0.03, now=NOW + 3)
        partial = self.journal.transition("request-1", "partially_filled", event_id="fill-1", cumulative_filled=0.03, broker_order_id="broker-1", now=NOW + 3)
        self.assertEqual(partial["filled_volume"], 0.03)
        with self.assertRaises(RiskStateError):
            self.journal.transition("request-1", "partially_filled", event_id="backwards", cumulative_filled=0.02, now=NOW + 4)
        with self.assertRaises(RiskStateError):
            self.journal.transition("request-1", "filled", event_id="overfill", cumulative_filled=0.11, now=NOW + 4)
        final = self.journal.transition("request-1", "filled", event_id="fill-2", cumulative_filled=0.1, now=NOW + 4)
        self.assertEqual(final["state"], "filled")

    def test_event_id_replay_dedup_and_conflict(self):
        self.submitted()
        kwargs = {"event_id": "fill-1", "cumulative_filled": 0.1, "broker_order_id": "broker-1", "now": NOW + 3}
        original = self.journal.transition("request-1", "filled", **kwargs)
        self.assertEqual(original, self.journal.transition("request-1", "filled", **kwargs))
        with self.assertRaises(RiskStateError):
            self.journal.transition("request-1", "filled", **{**kwargs, "reason": "changed payload"})

    def test_partial_cancel_preserves_actual_fills(self):
        self.submitted()
        self.journal.transition("request-1", "partially_filled", event_id="fill-1", cumulative_filled=0.04, broker_order_id="broker-1", now=NOW + 3)
        self.journal.transition("request-1", "cancel_pending", event_id="cancel-request", now=NOW + 4)
        final = self.journal.transition("request-1", "cancelled", event_id="cancel-confirm", now=NOW + 5)
        self.assertEqual(final["filled_volume"], 0.04)
        with self.assertRaises(RiskStateError):
            self.journal.begin_submission("request-1", event_id="bad-retry", now=NOW + 6)

    def test_cancel_race_allows_fill_before_cancel_confirmation(self):
        self.submitted()
        self.journal.transition("request-1", "accepted", event_id="broker-accept", broker_order_id="broker-1", now=NOW + 3)
        self.journal.transition("request-1", "cancel_pending", event_id="cancel-request", now=NOW + 4)
        final = self.journal.transition("request-1", "filled", event_id="broker-fill", cumulative_filled=0.1, now=NOW + 5)
        self.assertEqual(final["state"], "filled")
        with self.assertRaises(RiskStateError):
            self.journal.transition("request-1", "cancelled", event_id="late-cancel", now=NOW + 6)

    def test_broker_reference_cannot_change(self):
        self.submitted()
        self.journal.transition("request-1", "accepted", event_id="accept", broker_order_id="broker-1", now=NOW + 3)
        with self.assertRaises(RiskStateError):
            self.journal.transition("request-1", "filled", event_id="fill", broker_order_id="broker-2", cumulative_filled=0.1, now=NOW + 4)

    def test_order_state_and_event_history_survive_reopening(self):
        self.submitted()
        self.journal.transition("request-1", "rejected", event_id="broker-reject", reason="Synthetic insufficient-margin outcome", now=NOW + 3)
        reopened = OrderJournal(self.path)
        self.assertEqual(reopened.get("request-1")["state"], "rejected")
        with self.assertRaises(RiskStateError):
            reopened.begin_submission("request-1", event_id="retry", now=NOW + 4)


if __name__ == "__main__":
    unittest.main()
