"""Bounded in-memory sessions and password verification for a single owner."""

from collections import defaultdict, deque
from dataclasses import dataclass
import base64
import hashlib
import hmac
import secrets
import threading
import time


def hash_password(password: str) -> str:
    if not 12 <= len(password) <= 1024:
        raise ValueError("Use a dashboard password of 12 to 1024 characters")
    salt = secrets.token_bytes(16)
    digest = hashlib.scrypt(password.encode(), salt=salt, n=16384, r=8, p=1, dklen=32)
    return "scrypt$16384$8$1$" + base64.b64encode(salt).decode() + "$" + base64.b64encode(digest).decode()


def verify_password(password: str, encoded: str) -> bool:
    try:
        scheme, n, r, p, salt, digest = encoded.split("$")
        if scheme != "scrypt" or (n, r, p) != ("16384", "8", "1") or not 1 <= len(password) <= 1024:
            return False
        salt_bytes = base64.b64decode(salt, validate=True)
        expected = base64.b64decode(digest, validate=True)
        if len(salt_bytes) != 16 or len(expected) != 32:
            return False
        actual = hashlib.scrypt(password.encode(), salt=salt_bytes, n=16384, r=8, p=1, dklen=32)
        return hmac.compare_digest(actual, expected)
    except (ValueError, TypeError):
        return False


@dataclass(frozen=True)
class Session:
    csrf_token: str
    expires_at: float


class Auth:
    def __init__(self, settings):
        self.settings = settings
        self.lock = threading.RLock()
        self.sessions: dict[str, Session] = {}
        self.buckets: dict[tuple[str, str], deque] = defaultdict(deque)
        self.encoded = settings.password_hash
        if not self.encoded and settings.password_hash_file.is_file():
            self.encoded = settings.password_hash_file.read_text(encoding="ascii").strip()
        if not self.encoded and settings.admin_password:
            self.encoded = hash_password(settings.admin_password)
        # Password must not remain in the app's printable Settings instance.
        settings.admin_password = ""

    @property
    def configured(self):
        return bool(self.encoded)

    def check_limit(self, key: str, category: str, limit: int, window: int) -> bool:
        now = time.monotonic()
        with self.lock:
            if len(self.buckets) > 2048:
                self.buckets = defaultdict(deque, {k: v for k, v in self.buckets.items() if v and now - v[-1] < 300})
            bucket = self.buckets[(category, key)]
            while bucket and now - bucket[0] >= window:
                bucket.popleft()
            if len(bucket) >= limit:
                return False
            bucket.append(now)
            return True

    def login(self, password: str) -> tuple[str, Session] | None:
        if not self.encoded or not verify_password(password, self.encoded):
            return None
        now = time.time()
        with self.lock:
            self.sessions = {k: v for k, v in self.sessions.items() if v.expires_at > now}
            if len(self.sessions) >= 32:
                self.sessions.pop(next(iter(self.sessions)))
            token = secrets.token_urlsafe(32)
            session = Session(secrets.token_urlsafe(32), now + self.settings.session_seconds)
            self.sessions[token] = session
            return token, session

    def get(self, token: str | None) -> Session | None:
        with self.lock:
            session = self.sessions.get(token or "")
            if session and session.expires_at > time.time():
                return session
            self.sessions.pop(token or "", None)
            return None

    def logout(self, token):
        with self.lock:
            self.sessions.pop(token or "", None)
