"""Local runtime configuration. No broker credentials belong in this service."""

from dataclasses import dataclass, field
from pathlib import Path
import os


DEFAULT_RISK = {
    "risk_per_trade_pct": 0.25,
    "daily_loss_pct": 1.0,
    "weekly_loss_pct": 3.0,
    "max_drawdown_pct": 5.0,
    "max_positions": 2,
    "max_consecutive_losses": 3,
}


@dataclass
class Settings:
    database_path: Path = field(default_factory=lambda: Path(os.getenv("XM_DATABASE_PATH", "data/trader.db")))
    bridge_dir: Path = field(default_factory=lambda: Path(os.getenv("XM_BRIDGE_DIR", "data/bridge")))
    web_dir: Path = field(default_factory=lambda: Path(__file__).resolve().parent.parent / "web")
    # Prefer a scrypt hash file created by the Windows setup script.
    password_hash: str = field(default_factory=lambda: os.getenv("XM_PASSWORD_HASH", ""))
    password_hash_file: Path = field(default_factory=lambda: Path(os.getenv("XM_PASSWORD_HASH_FILE", "data/password.hash")))
    admin_password: str = field(default_factory=lambda: os.getenv("XM_ADMIN_PASSWORD", ""))
    secure_cookie: bool = field(default_factory=lambda: os.getenv("XM_HTTPS", "0") == "1")
    allowed_hosts: tuple[str, ...] = field(default_factory=lambda: tuple(x.strip() for x in os.getenv("XM_ALLOWED_HOSTS", "localhost,127.0.0.1,::1").split(",") if x.strip()))
    allowed_origins: tuple[str, ...] = field(default_factory=lambda: tuple(x.strip().rstrip("/") for x in os.getenv("XM_ALLOWED_ORIGINS", "").split(",") if x.strip()))
    session_seconds: int = 3600
    stale_seconds: float = 10.0
    clock_skew_seconds: float = 5.0
    poll_seconds: float = 1.0
    lease_seconds: int = 20
    heartbeat_seconds: float = 5.0
    login_attempts: int = 8
    login_window_seconds: int = 300
    requests_per_minute: int = 240
    max_body_bytes: int = 16_384
    background_poll: bool = True
    live_account_id: str = field(default_factory=lambda: os.getenv("XM_LIVE_ACCOUNT_ID", ""))

    def __post_init__(self):
        self.database_path = Path(self.database_path)
        self.bridge_dir = Path(self.bridge_dir)
        self.web_dir = Path(self.web_dir)
        self.password_hash_file = Path(self.password_hash_file)
        if not 5 <= self.lease_seconds <= 30:
            raise ValueError("Control lease must be between five and thirty seconds")
        if not 0 < self.heartbeat_seconds < self.lease_seconds:
            raise ValueError("Heartbeat interval must be shorter than the lease")
        if not self.allowed_hosts or "*" in self.allowed_hosts:
            raise ValueError("Explicit local host names are required")
        remote = any(x not in {"localhost", "127.0.0.1", "::1", "testserver"} for x in self.allowed_hosts)
        if remote and not self.secure_cookie:
            raise ValueError("Network access requires HTTPS and secure cookies")
