"""Local runner configuration and an exclusive process lock (no broker secrets)."""
from __future__ import annotations
import json
import os
from pathlib import Path
from contextlib import contextmanager

PROJECT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = PROJECT / "config" / "local.json"


def load_config(path: Path = DEFAULT_CONFIG) -> dict:
    config = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(config, dict) or config.get("version") != 1:
        raise ValueError("Unsupported local configuration. Run Setup-Windows.ps1.")
    for key in ("database_path", "bridge_dir", "password_hash_file"):
        if not isinstance(config.get(key), str) or not Path(config[key]).is_absolute():
            raise ValueError(f"{key} must be an absolute local path")
    host = config.get("host", "127.0.0.1")
    port = config.get("port", 8765)
    if not isinstance(host, str) or not isinstance(port, int) or not 1024 <= port <= 65535:
        raise ValueError("Invalid listening address or port")
    cert, key = config.get("tls_cert"), config.get("tls_key")
    if bool(cert) != bool(key):
        raise ValueError("TLS certificate and key must be supplied together")
    remote = host not in {"127.0.0.1", "localhost", "::1"}
    if remote and not (cert and key and config.get("network_access_confirmed") is True):
        raise ValueError("Network listening requires TLS and explicit local configuration")
    hosts = config.get("allowed_hosts", ["localhost", "127.0.0.1", "::1"])
    origins = config.get("allowed_origins", [])
    if not isinstance(hosts, list) or not hosts or not all(isinstance(x, str) and x and "*" not in x and "," not in x for x in hosts):
        raise ValueError("Configure explicit allowed hosts; wildcard hosts are prohibited")
    if not isinstance(origins, list) or not all(isinstance(x, str) and x.startswith("https://") and "," not in x for x in origins):
        raise ValueError("Network origins must be exact HTTPS origins")
    if remote and not origins:
        raise ValueError("Network listening requires at least one exact HTTPS origin")
    for filename in (cert, key):
        if filename and not Path(filename).is_file():
            raise ValueError("TLS file is missing")
    return config


def apply_environment(config: dict) -> None:
    """Set only this application’s variables; preserve proxy/system environment."""
    os.environ["XM_LIVE_ACCOUNT_ID"] = str(config.get("live_account_id", ""))
    os.environ["XM_DATABASE_PATH"] = config["database_path"]
    os.environ["XM_BRIDGE_DIR"] = config["bridge_dir"]
    os.environ["XM_PASSWORD_HASH_FILE"] = config["password_hash_file"]
    os.environ["XM_HTTPS"] = "1" if config.get("tls_cert") else "0"
    os.environ["XM_ALLOWED_HOSTS"] = ",".join(config.get("allowed_hosts", ["localhost", "127.0.0.1", "::1"]))
    os.environ["XM_ALLOWED_ORIGINS"] = ",".join(config.get("allowed_origins", []))
    # Local setup is the authoritative credential source for this runner.
    os.environ.pop("XM_ADMIN_PASSWORD", None)
    os.environ.pop("XM_PASSWORD_HASH", None)


@contextmanager
def exclusive_lock(path: Path):
    """Refuse a second server or restore while the managed server is running."""
    path.parent.mkdir(parents=True, exist_ok=True)
    stream = path.open("a+b")
    stream.seek(0)
    if path.stat().st_size == 0:
        stream.write(b"0")
        stream.flush()
    stream.seek(0)
    try:
        if os.name == "nt":
            import msvcrt
            msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError as exc:
        stream.close()
        raise RuntimeError("The dashboard is already running. Stop it before restoring or starting another instance.") from exc
    try:
        yield
    finally:
        stream.seek(0)
        if os.name == "nt":
            import msvcrt
            msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            import fcntl
            fcntl.flock(stream.fileno(), fcntl.LOCK_UN)
        stream.close()
