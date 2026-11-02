import csv
import time

from fastapi.testclient import TestClient
import pytest

from xmtrader.api import create_app
from xmtrader.bridge import ACCOUNT_FIELDS, POSITION_FIELDS, EVENT_FIELDS
from xmtrader.settings import Settings


@pytest.fixture
def client(tmp_path):
    directory = tmp_path / "bridge"
    directory.mkdir()
    (directory / "bridge.secret").write_text("x" * 48)
    settings = Settings(database_path=tmp_path / "state.db", bridge_dir=directory,
                        password_hash_file=tmp_path / "absent", admin_password="a-private-dashboard-password",
                        allowed_hosts=("testserver",), background_poll=False)
    with TestClient(create_app(settings)) as value:
        yield value


def login(client):
    response = client.post("/api/auth/login", json={"password": "a-private-dashboard-password"})
    assert response.status_code == 200
    return {"X-CSRF-Token": response.json()["csrf_token"]}


def fixture_account(client, mode="demo", robot_state="paused"):
    directory = client.app.state.settings.bridge_dir
    row = dict(zip(ACCOUNT_FIELDS, [1, time.time(), "12345", "XM Demo", mode, "USD", 10000, 10000, 0, 10000, 0, 0, 0, robot_state, "Waiting", 1, 10, 20, 30, 40]))
    for name, fields, rows in [("account.csv", ACCOUNT_FIELDS, [row]), ("positions.csv", POSITION_FIELDS, []), ("events.csv", EVENT_FIELDS, [])]:
        with (directory / name).open("w", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=fields)
            writer.writeheader()
            writer.writerows(rows)
    client.app.state.bridge.poll()


def test_all_private_data_requires_authentication(client):
    for endpoint in ("status", "positions", "trades", "events", "settings", "strategies", "analytics", "diagnostics", "auth/session"):
        assert client.get("/api/" + endpoint).status_code == 401
    assert client.get("/api/health").status_code == 200
    assert client.get("/openapi.json").status_code == 404


def test_session_cookie_and_no_store_headers(client):
    headers = login(client)
    response = client.get("/api/auth/session")
    assert response.json()["csrf_token"] == headers["X-CSRF-Token"]
    assert response.headers["cache-control"] == "no-store"
    response = client.post("/api/auth/login", json={"password": "a-private-dashboard-password"})
    assert "HttpOnly" in response.headers["set-cookie"]
    assert "SameSite=strict" in response.headers["set-cookie"]


def test_csrf_and_cross_origin_controls_rejected(client):
    headers = login(client)
    body = {"action": "pause", "request_id": "unique-request-1"}
    assert client.post("/api/control", json=body).status_code == 403
    assert client.post("/api/control", json=body, headers={**headers, "Origin": "https://attacker.invalid"}).status_code == 403
    assert client.post("/api/control", json=body, headers=headers).status_code == 200


def test_unknown_account_values_are_null_not_demo_fabrications(client):
    login(client)
    state = client.get("/api/status").json()
    assert state["balance"] is None and state["mode"] == "unknown"
    assert not state["connected"]
    assert client.get("/api/trades").json()["items"] == []


def test_control_confirmation_and_real_account_rejection(client):
    headers = login(client)
    fixture_account(client)
    assert client.post("/api/control", json={"action": "start", "request_id": "no-confirmation"}, headers=headers).status_code == 409
    result = client.post("/api/control", json={"action": "start", "request_id": "valid-demo-start", "confirmation": "START DEMO"}, headers=headers)
    assert result.status_code == 200 and result.json()["state"] == "pending"
    assert client.get("/api/status").json()["robot_state"] == "paused"


def test_sensitive_response_validation_never_echoes_password(client):
    secret = "do-not-echo-this-value"
    response = client.post("/api/auth/login", json={"password": secret, "unexpected": secret})
    assert response.status_code == 422
    assert secret not in response.text


def test_payload_limits_and_host_protection(client):
    assert client.post("/api/auth/login", content="x" * 20_000).status_code == 413
    assert client.get("/api/health", headers={"host": "evil.invalid"}).status_code == 400


def test_assistant_proposes_stop_without_executing(client):
    headers = login(client)
    before = (client.app.state.settings.bridge_dir / "control.csv").read_text()
    reply = client.post("/api/assistant", json={"message": "Stop the robot"}, headers=headers).json()
    assert reply["proposed_action"] == "emergency_stop" and reply["requires_confirmation"]
    assert (client.app.state.settings.bridge_dir / "control.csv").read_text() == before
    assert client.app.state.bridge.pending is None


def test_login_failures_are_rate_limited(client):
    statuses = [client.post("/api/auth/login", json={"password": "wrong"}).status_code for _ in range(9)]
    assert statuses[-1] == 429


def test_logout_invalidates_cookie_and_session(client):
    headers = login(client)
    assert client.post("/api/auth/logout", headers=headers).status_code == 200
    assert client.get("/api/status").status_code == 401


def test_risk_configuration_cannot_be_loosened_or_nonfinite(client):
    headers = login(client)
    assert client.put("/api/settings", json={"risk_per_trade_pct": 2}, headers=headers).status_code == 422
    response = client.put("/api/settings", content='{"risk_per_trade_pct":NaN}', headers={**headers, "Content-Type": "application/json"})
    assert response.status_code == 422


def test_private_paths_and_secrets_are_not_exposed(client):
    login(client)
    output = client.get("/api/diagnostics").text
    assert "x" * 48 not in output
    assert str(client.app.state.settings.bridge_dir) not in output
    assert client.get("/../data/password.hash").status_code == 404
    assert client.get("/xmtrader/settings.py").status_code == 404


def test_strategy_changes_are_blocked_with_pending_command(client):
    headers = login(client)
    fixture_account(client)
    client.post("/api/control", json={"action": "start", "request_id": "valid-demo-start", "confirmation": "START DEMO"}, headers=headers)
    response = client.post("/api/strategies/trend", json={"enabled": True, "confirmation": "CHANGE DEMO STRATEGY"}, headers=headers)
    assert response.status_code == 409


def test_live_requires_exact_local_account_pairing_and_live_confirmation(client):
    headers = login(client)
    fixture_account(client, mode='live')
    client.app.state.settings.live_account_id = 'different-account'
    body = {'action': 'start', 'request_id': 'live-wrong-account', 'confirmation': 'START LIVE'}
    assert client.post('/api/control', json=body, headers=headers).status_code == 409
    client.app.state.settings.live_account_id = '12345'
    body.update(request_id='live-wrong-confirmation', confirmation='START DEMO')
    assert client.post('/api/control', json=body, headers=headers).status_code == 409
    body.update(request_id='live-authorized-start', confirmation='START LIVE')
    result = client.post('/api/control', json=body, headers=headers)
    assert result.status_code == 200
    assert result.json()['state'] == 'pending'
    assert client.get('/api/status').json()['robot_state'] == 'paused'


def test_workspace_selection_does_not_switch_or_start_broker_account(client):
    headers = login(client)
    fixture_account(client)
    result = client.post('/api/workspace', json={'mode': 'live'}, headers=headers)
    assert result.status_code == 200
    state = client.get('/api/workspace').json()
    assert state['selected_mode'] == 'live' and state['actual_mode'] == 'demo'
    assert not state['ready']
    assert client.post('/api/control', json={'action':'start','request_id':'workspace-mismatch','confirmation':'START DEMO'},headers=headers).status_code == 409
    assert client.get('/api/status').json()['robot_state'] == 'paused'


def test_workspace_requires_auth_and_csrf(client):
    assert client.get('/api/workspace').status_code == 401
    login(client)
    assert client.post('/api/workspace',json={'mode':'live'}).status_code == 403


def test_history_download_is_authenticated_and_never_invents_executions(client):
    assert client.get('/api/history.csv').status_code == 401
    login(client)
    result=client.get('/api/history.csv')
    assert result.status_code == 200
    assert result.headers['cache-control']=='no-store'
    assert len(result.text.strip().splitlines())==1
