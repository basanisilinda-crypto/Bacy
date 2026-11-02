"""Recovery tests use disposable local data, never an MT5 or broker account."""
import importlib
import json
from pathlib import Path
import sqlite3
import zipfile

import pytest


@pytest.fixture
def operations(monkeypatch):
    monkeypatch.syspath_prepend(str(Path(__file__).resolve().parents[1] / "scripts"))
    return importlib.import_module("runtime_config"), importlib.import_module("backup")


def config_for(tmp_path):
    return {
        "version": 1,
        "database_path": str(tmp_path / "trader.db"),
        "bridge_dir": str(tmp_path / "bridge"),
        "password_hash_file": str(tmp_path / "password.hash"),
        "host": "127.0.0.1",
        "port": 8765,
        "allowed_hosts": ["localhost", "127.0.0.1"],
        "allowed_origins": [],
    }


def make_database(path, value="original"):
    with sqlite3.connect(path) as connection:
        connection.execute("CREATE TABLE observations(value TEXT)")
        connection.execute("INSERT INTO observations VALUES (?)", (value,))


def test_remote_listening_rejected_without_tls_and_opt_in(operations, tmp_path):
    runtime, _ = operations
    config = config_for(tmp_path)
    config.update(host="0.0.0.0", network_access_confirmed=True)
    target = tmp_path / "local.json"
    target.write_text(json.dumps(config))
    with pytest.raises(ValueError, match="TLS"):
        runtime.load_config(target)


def test_network_host_wildcards_are_rejected(operations, tmp_path):
    runtime, _ = operations
    config = config_for(tmp_path)
    config["allowed_hosts"] = ["*"]
    target = tmp_path / "local.json"
    target.write_text(json.dumps(config))
    with pytest.raises(ValueError, match="wildcard"):
        runtime.load_config(target)


def test_process_lock_blocks_second_runner_or_restore(operations, tmp_path):
    runtime, _ = operations
    lock = tmp_path / "server.lock"
    with runtime.exclusive_lock(lock):
        with pytest.raises(RuntimeError, match="already running"):
            with runtime.exclusive_lock(lock):
                pytest.fail("A second process acquired the runner lock")
    with runtime.exclusive_lock(lock):
        pass


def test_backup_preserves_committed_wal_and_excludes_secret_files(operations, tmp_path):
    _, backup = operations
    config = config_for(tmp_path)
    database = Path(config["database_path"])
    connection = sqlite3.connect(database)
    try:
        connection.execute("PRAGMA journal_mode=WAL")
        connection.execute("CREATE TABLE observations(value TEXT)")
        connection.execute("INSERT INTO observations VALUES ('committed')")
        connection.commit()
        (tmp_path / "password.hash").write_text("excluded-test-fixture")
        archive = tmp_path / "snapshot.zip"
        backup.create(config, archive)
        with zipfile.ZipFile(archive) as source:
            assert set(source.namelist()) == {"trader.db", "manifest.json"}
            assert json.loads(source.read("manifest.json"))["secrets_included"] is False
            snapshot = tmp_path / "verify.db"
            snapshot.write_bytes(source.read("trader.db"))
        with sqlite3.connect(snapshot) as restored:
            assert restored.execute("SELECT value FROM observations").fetchone()[0] == "committed"
    finally:
        connection.close()


def test_restore_requires_explicit_offline_confirmation(operations, tmp_path):
    _, backup = operations
    with pytest.raises(ValueError, match="confirm-stopped"):
        backup.restore(config_for(tmp_path), tmp_path / "missing.zip", False)


def test_corrupt_backup_does_not_replace_existing_database(operations, tmp_path):
    _, backup = operations
    config = config_for(tmp_path)
    database = Path(config["database_path"])
    make_database(database)
    archive = tmp_path / "corrupt.zip"
    with zipfile.ZipFile(archive, "w") as output:
        output.writestr("manifest.json", json.dumps({"format": 1, "contents": ["trader.db"]}))
        output.writestr("trader.db", "not a database")
        output.writestr("../must-not-be-extracted", "invalid traversal")
    with pytest.raises((ValueError, sqlite3.DatabaseError)):
        backup.restore(config, archive, True)
    with sqlite3.connect(database) as original:
        assert original.execute("SELECT value FROM observations").fetchone()[0] == "original"
    assert not (tmp_path / "must-not-be-extracted").exists()


def test_restore_retains_pre_restore_copy(operations, tmp_path):
    _, backup = operations
    config = config_for(tmp_path)
    database = Path(config["database_path"])
    make_database(database, "old")
    archive = tmp_path / "snapshot.zip"
    backup.create(config, archive)
    with sqlite3.connect(database) as connection:
        connection.execute("UPDATE observations SET value='new'")
    backup.restore(config, archive, True)
    with sqlite3.connect(database) as connection:
        assert connection.execute("SELECT value FROM observations").fetchone()[0] == "old"
    with sqlite3.connect(database.with_suffix(".before-restore.db")) as connection:
        assert connection.execute("SELECT value FROM observations").fetchone()[0] == "new"
