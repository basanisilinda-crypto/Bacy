"""Safe local diagnostic summary: no passwords, tokens, account IDs, or balances."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import platform
import socket
import sys
import time
from runtime_config import DEFAULT_CONFIG, load_config


def report(config_path: Path) -> dict:
    result = {"python_supported": sys.version_info >= (3, 11), "operating_system": platform.system(),
              "config_valid": False, "dashboard_listening": False, "password_configured": False,
              "bridge_secret_present": False, "account_snapshot_present": False,
              "snapshot_file_age_seconds": None, "database_present": False,
              "note": "File presence and a listening port do not prove broker connectivity or execution readiness."}
    try:
        config = load_config(config_path)
    except (ValueError, OSError, json.JSONDecodeError):
        result["problem"] = "Local configuration missing or invalid; run setup or check the private configuration locally."
        return result
    result["config_valid"] = True
    result["password_configured"] = Path(config["password_hash_file"]).is_file()
    bridge = Path(config["bridge_dir"])
    result["bridge_secret_present"] = (bridge / "bridge.secret").is_file()
    result["database_present"] = Path(config["database_path"]).is_file()
    snapshot = bridge / "account.csv"
    result["account_snapshot_present"] = snapshot.is_file()
    if snapshot.is_file():
        result["snapshot_file_age_seconds"] = round(max(0, time.time() - snapshot.stat().st_mtime), 1)
    host = config.get("host", "127.0.0.1")
    if host == "0.0.0.0":
        host = "127.0.0.1"
    elif host == "::":
        host = "::1"
    try:
        with socket.create_connection((host, config.get("port", 8765)), timeout=1):
            result["dashboard_listening"] = True
    except OSError:
        pass
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    args = parser.parse_args()
    print(json.dumps(report(args.config), indent=2))
