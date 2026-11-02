"""Launcher helpers do not open the browser before a healthy service exists."""
import importlib
import io
from pathlib import Path
import pytest


@pytest.fixture
def launcher(monkeypatch):
    monkeypatch.syspath_prepend(str(Path(__file__).resolve().parents[1]/'scripts'))
    return importlib.import_module('open_dashboard')


def test_dashboard_url_preserves_tls_port_and_ipv6(launcher):
    assert launcher.dashboard_url({'host':'::1','port':8765}) == 'http://[::1]:8765'
    assert launcher.dashboard_url({'host':'192.168.1.5','port':9443,'tls_cert':'local-cert'}) == 'https://192.168.1.5:9443'


def test_browser_readiness_stops_when_owned_service_exits(launcher, monkeypatch):
    monkeypatch.setattr(launcher,'process_running',lambda pid:False)
    with pytest.raises(RuntimeError,match='process exited'):
        launcher.wait_until_ready('http://127.0.0.1:8765',process_id=999)


def test_healthy_local_service_is_required(launcher,monkeypatch):
    class Response(io.BytesIO):
        status=200
    class Opener:
        def open(self,url,timeout):
            assert url.endswith('/api/health')
            return Response(b'{"status":"ok","version":"0.1.0"}')
    monkeypatch.setattr(launcher.urllib.request,'build_opener',lambda *args:Opener())
    launcher.wait_until_ready('http://127.0.0.1:8765')


def test_timeout_never_claims_success(launcher):
    with pytest.raises(RuntimeError,match='timed out'):
        launcher.wait_until_ready('http://127.0.0.1:8765',timeout=0)
