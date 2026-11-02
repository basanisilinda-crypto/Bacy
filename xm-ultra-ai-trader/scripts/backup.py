"""Consistent SQLite backup and explicit offline restore. Excludes all secrets."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import sqlite3
import sys
import tempfile
import zipfile

from runtime_config import DEFAULT_CONFIG, exclusive_lock, load_config


def check_database(path: Path) -> None:
    with sqlite3.connect(f"file:{path.as_posix()}?mode=ro", uri=True) as connection:
        result = connection.execute("PRAGMA quick_check").fetchall()
        if result != [("ok",)]:
            raise ValueError("The database failed its integrity check. Original database was not replaced.")


def create(config: dict, destination: Path) -> None:
    source = Path(config["database_path"])
    if not source.is_file():
        raise ValueError("There is no application database yet. Start the dashboard once before backing it up.")
    if destination.exists():
        raise ValueError("Backup output already exists; choose a new filename.")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="xm-backup-") as directory:
        snapshot = Path(directory) / "trader.db"
        with sqlite3.connect(f"file:{source.as_posix()}?mode=ro", uri=True) as src, sqlite3.connect(snapshot) as dst:
            src.backup(dst)
        check_database(snapshot)
        descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        try:
            with os.fdopen(descriptor, "wb") as output, zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                archive.write(snapshot, "trader.db")
                archive.writestr("manifest.json", json.dumps({"format": 1, "created_utc": datetime.now(timezone.utc).isoformat(),
                    "contents": ["trader.db"], "secrets_included": False,
                    "mt5_risk_anchors_included": False,
                    "warning": "Contains private financial history. Does not restore MT5 terminal state or enable trading."}, indent=2))
        except BaseException:
            destination.unlink(missing_ok=True)
            raise
    print("Consistent database backup created. Keep it private; it contains account history, not passwords or MT5 risk anchors.")


def restore(config: dict, archive_path: Path, confirmed_stopped: bool) -> None:
    if not confirmed_stopped:
        raise ValueError("Pause the EA, stop the dashboard, then explicitly pass --confirm-stopped.")
    destination = Path(config["database_path"])
    destination.parent.mkdir(parents=True, exist_ok=True)
    with exclusive_lock(destination.parent / "server.lock"):
        with tempfile.TemporaryDirectory(dir=destination.parent, prefix="restore-") as directory:
            temporary = Path(directory) / "trader.db"
            with zipfile.ZipFile(archive_path, "r") as archive:
                # Extract only a fixed named entry; never trust arbitrary archive paths.
                if archive.namelist().count("trader.db") != 1 or archive.namelist().count("manifest.json") != 1:
                    raise ValueError("This is not a valid application backup.")
                info = archive.getinfo("trader.db")
                manifest_info = archive.getinfo("manifest.json")
                if info.file_size > 2 * 1024**3 or manifest_info.file_size > 16_384:
                    raise ValueError("Backup exceeds supported restore size.")
                manifest = json.loads(archive.read("manifest.json"))
                if manifest.get("format") != 1 or manifest.get("contents") != ["trader.db"]:
                    raise ValueError("Unsupported backup format.")
                with archive.open("trader.db") as source, temporary.open("xb") as output:
                    while block := source.read(1024 * 1024):
                        output.write(block)
            check_database(temporary)
            if destination.exists():
                previous = destination.with_suffix(".before-restore.db")
                if previous.exists():
                    raise ValueError("A previous pre-restore copy exists. Keep it safely elsewhere before another restore.")
                # Checkpoint only under the exclusive managed-runner lock. Manual
                # uvicorn launches are outside this lock and must also be stopped.
                with sqlite3.connect(destination) as connection:
                    busy, _, _ = connection.execute("PRAGMA wal_checkpoint(TRUNCATE)").fetchone()
                    if busy:
                        raise ValueError("Database is still busy. Stop every dashboard process before restoring.")
                    with sqlite3.connect(previous) as prior:
                        connection.backup(prior)
                os.chmod(previous, 0o600)
            for suffix in ("-wal", "-shm"):
                Path(str(destination) + suffix).unlink(missing_ok=True)
            os.replace(temporary, destination)
            os.chmod(destination, 0o600)
    print("Database restored. Restart paused, verify the account, and reconcile actual MT5 positions before any resume.")
    print("MT5 risk anchors and account history must be checked separately; this backup cannot reconstruct them.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    subparsers = parser.add_subparsers(dest="action", required=True)
    backup = subparsers.add_parser("create")
    backup.add_argument("destination", type=Path)
    recover = subparsers.add_parser("restore")
    recover.add_argument("archive", type=Path)
    recover.add_argument("--confirm-stopped", action="store_true")
    args = parser.parse_args()
    config = load_config(args.config)
    if args.action == "create":
        create(config, args.destination)
    else:
        restore(config, args.archive, args.confirm_stopped)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, RuntimeError, sqlite3.Error, zipfile.BadZipFile) as exc:
        print(f"Backup operation stopped: {exc}", file=sys.stderr)
        raise SystemExit(1)
