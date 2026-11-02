"""Run one local dashboard service; startup never enables trading."""
from __future__ import annotations
import argparse
from pathlib import Path
import sys
from runtime_config import DEFAULT_CONFIG, PROJECT, apply_environment, exclusive_lock, load_config


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    args = parser.parse_args()
    config = load_config(args.config)
    if not Path(config["password_hash_file"]).is_file():
        raise ValueError("Dashboard password is missing. Run Setup-Windows.ps1 first.")
    apply_environment(config)
    sys.path.insert(0, str(PROJECT))
    import uvicorn
    with exclusive_lock(Path(config["database_path"]).parent / "server.lock"):
        uvicorn.run("xmtrader.api:app", host=config.get("host", "127.0.0.1"),
                    port=config.get("port", 8765), workers=1, reload=False,
                    proxy_headers=False, access_log=False,
                    ssl_certfile=config.get("tls_cert"), ssl_keyfile=config.get("tls_key"),
                    limit_concurrency=20, timeout_keep_alive=5)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, RuntimeError) as exc:
        print(f"Dashboard could not start: {exc}", file=sys.stderr)
        raise SystemExit(1)
