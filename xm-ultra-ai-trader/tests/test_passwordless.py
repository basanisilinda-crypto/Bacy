"""No-password desktop access retains sessions, CSRF and the loopback boundary."""
import pytest
from fastapi.testclient import TestClient
from xmtrader.api import create_app
from xmtrader.settings import Settings


@pytest.fixture
def local_app(tmp_path):
    bridge=tmp_path/'bridge'
    bridge.mkdir()
    (bridge/'bridge.secret').write_text('x'*48)
    return create_app(Settings(database_path=tmp_path/'state.db',bridge_dir=bridge,
        password_hash_file=tmp_path/'absent',local_passwordless=True,
        allowed_hosts=('testserver',),background_poll=False))


def test_local_session_opens_without_password_and_keeps_csrf(local_app):
    with TestClient(local_app) as client:
        assert client.get('/api/status').status_code == 401
        response=client.get('/api/auth/session')
        assert response.status_code == 200 and response.json()['passwordless']
        assert 'HttpOnly' in response.headers['set-cookie']
        assert client.get('/api/status').status_code == 200
        body={'action':'pause','request_id':'local-pause-1'}
        assert client.post('/api/control',json=body).status_code == 403
        assert client.post('/api/control',json=body,headers={'X-CSRF-Token':response.json()['csrf_token']}).status_code == 200


def test_cross_site_cannot_bootstrap_local_session(local_app):
    with TestClient(local_app) as client:
        response=client.get('/api/auth/session',headers={'Origin':'https://evil.invalid','Sec-Fetch-Site':'cross-site'})
        assert response.status_code == 403 and 'set-cookie' not in response.headers


def test_remote_peer_cannot_use_passwordless_bootstrap(local_app):
    with TestClient(local_app,client=('192.168.1.8',5000)) as client:
        assert client.get('/api/auth/session').status_code == 403


def test_network_configuration_cannot_enable_passwordless_mode():
    with pytest.raises(ValueError,match='loopback-only'):
        Settings(allowed_hosts=('192.168.1.8',),secure_cookie=True,local_passwordless=True)
