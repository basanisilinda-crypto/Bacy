"""Interactive, local-only initial configuration; never requests XM credentials."""
from __future__ import annotations
import argparse
import getpass
import json
import os
from pathlib import Path
import secrets
import sys

from runtime_config import PROJECT, DEFAULT_CONFIG
sys.path.insert(0, str(PROJECT))
from xmtrader.auth import hash_password


def private_write(path: Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    descriptor = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(value)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(tmp, path)
    finally:
        tmp.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bridge-dir", type=Path, required=True)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--change-password", action="store_true")
    args = parser.parse_args()
    bridge = args.bridge_dir.resolve()
    data = PROJECT / "data"
    bridge.mkdir(parents=True, exist_ok=True)
    data.mkdir(parents=True, exist_ok=True)
    password_file = data / "password.hash"
    if not password_file.exists() or args.change_password:
        print("Create a dashboard password (12 or more characters). This is NOT your XM password.")
        password = getpass.getpass("New dashboard password: ")
        confirmation = getpass.getpass("Repeat dashboard password: ")
        if password != confirmation:
            raise ValueError("Passwords did not match. No password was saved.")
        private_write(password_file, hash_password(password) + "\n")
        del password, confirmation
    secret_file = bridge / "bridge.secret"
    if not secret_file.exists():
        private_write(secret_file, secrets.token_urlsafe(48) + "\n")
    else:
        token = secret_file.read_text(encoding="ascii").strip()
        if len(token) < 32 or not token.isascii() or any(c.isspace() for c in token):
            raise ValueError("Existing bridge secret is malformed; stop MT5/backend and repair it locally.")
        del token
    if args.config.exists():
        existing = json.loads(args.config.read_text(encoding="utf-8"))
        if Path(existing.get("bridge_dir", "")).resolve() != bridge:
            raise ValueError("Existing configuration points to a different bridge. Do not silently switch trading terminals.")
        print("Existing configuration and trading history preserved.")
    else:
        config = {"version": 1, "database_path": str((data / "trader.db").resolve()),
                  "bridge_dir": str(bridge), "password_hash_file": str(password_file.resolve()),
                  "host": "127.0.0.1", "port": 8765,
                  "allowed_hosts": ["localhost", "127.0.0.1", "::1"], "allowed_origins": [],
                  "network_access_confirmed": False, "tls_cert": None, "tls_key": None}
        private_write(args.config, json.dumps(config, indent=2) + "\n")
    print("Local setup is ready. Start the dashboard with Start-Windows.ps1. Trading remains paused.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as exc:
        print(f"Setup stopped: {exc}", file=sys.stderr)
        raise SystemExit(1)
