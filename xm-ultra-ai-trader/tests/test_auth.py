import pytest

from xmtrader.auth import Auth, hash_password, verify_password
from xmtrader.settings import Settings


def test_password_hash_is_salted_and_verifies_without_plaintext():
    first = hash_password("a-private-dashboard-password")
    second = hash_password("a-private-dashboard-password")
    assert first != second
    assert "a-private-dashboard-password" not in first
    assert verify_password("a-private-dashboard-password", first)
    assert not verify_password("wrong", first)
    assert not verify_password("anything", "scrypt$999999999$8$1$bad$bad")


def test_short_password_rejected():
    with pytest.raises(ValueError):
        hash_password("short")


def test_sessions_expire_and_csrf_tokens_are_distinct(tmp_path):
    settings = Settings(admin_password="a-private-dashboard-password", password_hash_file=tmp_path / "absent", session_seconds=-1)
    auth = Auth(settings)
    token, session = auth.login("a-private-dashboard-password")
    assert token != session.csrf_token
    assert auth.get(token) is None
    assert settings.admin_password == ""


def test_unconfigured_authentication_fails_closed(tmp_path):
    auth = Auth(Settings(password_hash_file=tmp_path / "absent"))
    assert not auth.configured
    assert auth.login("anything") is None


def test_auth_attempt_limits():
    auth = Auth(Settings())
    assert auth.check_limit("owner", "login", 2, 300)
    assert auth.check_limit("owner", "login", 2, 300)
    assert not auth.check_limit("owner", "login", 2, 300)


def test_network_hosts_require_secure_configuration():
    with pytest.raises(ValueError, match="HTTPS"):
        Settings(allowed_hosts=("192.168.1.5",), local_passwordless=False)
    with pytest.raises(ValueError, match="Explicit"):
        Settings(allowed_hosts=("*",), secure_cookie=True)
