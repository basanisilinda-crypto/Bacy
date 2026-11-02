import csv
import time

import pytest

from xmtrader.bridge import ACK_FIELDS, ACCOUNT_FIELDS, EVENT_FIELDS, POSITION_FIELDS, Bridge, BridgeError
from xmtrader.settings import Settings
from xmtrader.storage import Storage


def write_csv(path, fields, rows):
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def account_row(**overrides):
    return {**dict(zip(ACCOUNT_FIELDS, [1, time.time(), "12345", "XM Demo", "demo", "USD", 10000, 9998, 0, 9998, -2, -2, -2, "paused", "Waiting for explicit start", 1, 10, 20, 30, 40])), **overrides}


@pytest.fixture
def setup(tmp_path):
    directory = tmp_path / "bridge"
    directory.mkdir()
    (directory / "bridge.secret").write_text("s" * 48)
    write_csv(directory / "account.csv", ACCOUNT_FIELDS, [account_row()])
    write_csv(directory / "positions.csv", POSITION_FIELDS, [])
    write_csv(directory / "events.csv", EVENT_FIELDS, [])
    settings = Settings(database_path=tmp_path / "state.db", bridge_dir=directory, background_poll=False)
    storage = Storage(settings.database_path)
    bridge = Bridge(settings, storage)
    yield bridge, directory, storage
    storage.close()


def test_disconnected_status_never_invents_balances(tmp_path):
    settings = Settings(database_path=tmp_path / "state.db", bridge_dir=tmp_path / "missing")
    storage = Storage(settings.database_path)
    bridge = Bridge(settings, storage)
    bridge.poll()
    status = bridge.status()
    assert status["balance"] is None and status["equity"] is None
    assert status["mode"] == "unknown" and not status["connected"]
    storage.close()


def test_valid_demo_request_waits_for_actual_ack_and_is_idempotent(setup):
    bridge, directory, storage = setup
    result = bridge.control("start", "unique-request-1", "START DEMO")
    assert result["state"] == "pending"
    assert bridge.status()["robot_state"] == "paused"
    assert not bridge.lease_enabled
    assert bridge.control("start", "unique-request-1", "START DEMO") == result
    with pytest.raises(BridgeError, match="different action"):
        bridge.control("pause", "unique-request-1")
    bridge.poll()
    with (directory / "control.csv").open() as file:
        assert next(csv.DictReader(file))["action"] == "start"
    write_csv(directory / "ack.csv", ACK_FIELDS, [{"command_id": result["command_id"], "timestamp": time.time(), "accepted": "1", "state": "running", "message": "Demo operation started"}])
    write_csv(directory / "account.csv", ACCOUNT_FIELDS, [account_row(robot_state="running")])
    bridge.poll()
    assert bridge.lease_enabled and bridge.pending is None
    assert bridge.control("start", "unique-request-1", "START DEMO")["state"] == "acknowledged"


def test_stale_and_nonfinite_market_snapshots_block_start(setup):
    bridge, directory, storage = setup
    write_csv(directory / "account.csv", ACCOUNT_FIELDS, [account_row(timestamp=time.time() - 60)])
    with pytest.raises(BridgeError, match="current demo"):
        bridge.control("start", "stale-request-1", "START DEMO")
    write_csv(directory / "account.csv", ACCOUNT_FIELDS, [account_row(equity="NaN")])
    bridge.poll()
    assert not bridge.status()["connected"]
    assert not bridge.lease_enabled


def test_live_accounts_cannot_start_or_liquidate(setup):
    bridge, directory, storage = setup
    write_csv(directory / "account.csv", ACCOUNT_FIELDS, [account_row(mode="live")])
    for action, confirmation in [("start", "START LIVE"), ("liquidate", "CLOSE LIVE POSITIONS")]:
        with pytest.raises(BridgeError, match="current demo"):
            bridge.control(action, "live-" + action, confirmation)


def test_emergency_supersedes_pending_start_without_liquidation(setup):
    bridge, directory, storage = setup
    bridge.control("start", "request-start-1", "START DEMO")
    stopped = bridge.control("emergency_stop", "request-stop-1")
    assert stopped["state"] == "pending"
    assert not bridge.lease_enabled
    assert storage.command("request-start-1")["status"] == "superseded"
    with (directory / "control.csv").open() as file:
        assert next(csv.DictReader(file))["action"] == "emergency_stop"


def test_missing_secret_cannot_issue_command(setup):
    bridge, directory, storage = setup
    (directory / "bridge.secret").unlink()
    with pytest.raises(BridgeError, match="could not reach"):
        bridge.control("pause", "missing-secret")
    assert storage.command("missing-secret")["status"] == "failed"


def test_account_prefixed_events_are_actual_deduplicated_and_not_mixed(setup):
    bridge, directory, storage = setup
    common = {"timestamp": time.time(), "symbol": "EURUSD", "ticket": "555", "side": "buy", "volume": 0.01, "price": 1.1, "profit": 2, "commission": -0.1, "swap": 0, "strategy": "ema_cross", "message": "Broker deal"}
    write_csv(directory / "events.csv", EVENT_FIELDS, [
        {**common, "event_id": "12345:order:1", "kind": "order_submitted"},
        {**common, "event_id": "12345:deal:2", "kind": "trade_closed"},
        {**common, "event_id": "999:deal:3", "kind": "trade_closed"},
    ])
    bridge.poll()
    bridge.poll()
    current = storage.trades("12345")
    assert len(current) == 1 and current[0]["kind"] == "trade_closed"
    assert len(storage.trades("999")) == 1


def test_incomplete_event_line_is_waited_for_not_counted(setup):
    bridge, directory, storage = setup
    bridge.poll()
    with (directory / "events.csv").open("a") as file:
        file.write("12345:deal:broken")
    bridge.poll()
    assert storage.trades("12345") == []


def test_restart_never_inherits_trading_lease(setup):
    bridge, directory, storage = setup
    bridge.lease_enabled = True
    fresh = Bridge(bridge.settings, storage)
    fresh.startup()
    assert not fresh.lease_enabled
    with (directory / "control.csv").open() as file:
        assert next(csv.DictReader(file))["action"] == "pause"


def test_corrupted_database_is_not_silently_recreated(tmp_path):
    path = tmp_path / "broken.db"
    path.write_bytes(b"invalid database")
    with pytest.raises(Exception):
        Storage(path)
    assert path.read_bytes() == b"invalid database"


def test_sqlite_backup_contains_committed_audit(setup, tmp_path):
    bridge, directory, storage = setup
    storage.event("test", "Audit record")
    path = tmp_path / "backup.db"
    storage.backup(path)
    recovered = Storage(path)
    assert recovered.events()[0]["message"] == "Audit record"
    recovered.close()
