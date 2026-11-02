"""Small transactional SQLite store; only broker events become trade records."""

from contextlib import contextmanager
import json
from pathlib import Path
import sqlite3
import threading
import time


MIGRATIONS = [
    """
    CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS events(id INTEGER PRIMARY KEY AUTOINCREMENT, event_key TEXT UNIQUE,
      timestamp REAL NOT NULL, kind TEXT NOT NULL, severity TEXT NOT NULL, message TEXT NOT NULL,
      source TEXT NOT NULL, account_id TEXT, payload TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS config(key TEXT PRIMARY KEY,value TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS commands(request_id TEXT PRIMARY KEY,command_id TEXT UNIQUE NOT NULL,
      fingerprint TEXT NOT NULL,action TEXT NOT NULL,created_at REAL NOT NULL,status TEXT NOT NULL,
      response TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS snapshots(account_id TEXT PRIMARY KEY,timestamp REAL NOT NULL,payload TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS equity_samples(account_id TEXT NOT NULL,minute INTEGER NOT NULL,timestamp REAL NOT NULL,
      equity REAL NOT NULL,balance REAL NOT NULL,PRIMARY KEY(account_id,minute));
    CREATE INDEX IF NOT EXISTS events_kind_time ON events(kind,timestamp);
    """
]


class Storage:
    def __init__(self, path: Path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()
        self.connection = sqlite3.connect(self.path, check_same_thread=False, timeout=5)
        self.connection.row_factory = sqlite3.Row
        self.connection.execute("PRAGMA journal_mode=WAL")
        self.connection.execute("PRAGMA foreign_keys=ON")
        self.connection.execute("PRAGMA busy_timeout=5000")
        with self.lock:
            self.connection.executescript(MIGRATIONS[0])
            row = self.connection.execute("SELECT version FROM schema_version LIMIT 1").fetchone()
            if not row:
                self.connection.execute("INSERT INTO schema_version VALUES (1)")
            elif row[0] != 1:
                raise RuntimeError("Unsupported database schema version")
            self.connection.commit()

    @contextmanager
    def transaction(self):
        with self.lock:
            try:
                yield self.connection
                self.connection.commit()
            except Exception:
                self.connection.rollback()
                raise

    def get_config(self, key, default=None):
        with self.lock:
            row = self.connection.execute("SELECT value FROM config WHERE key=?", (key,)).fetchone()
            return json.loads(row[0]) if row else default

    def set_config(self, key, value):
        with self.transaction() as conn:
            conn.execute("INSERT INTO config VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (key, json.dumps(value, allow_nan=False)))

    def event(self, kind, message, *, severity="info", source="backend", event_key=None, account_id=None, timestamp=None, payload=None):
        with self.transaction() as conn:
            cur = conn.execute("INSERT OR IGNORE INTO events(event_key,timestamp,kind,severity,message,source,account_id,payload) VALUES(?,?,?,?,?,?,?,?)", (event_key, timestamp or time.time(), kind, severity, message, source, account_id, json.dumps(payload or {}, allow_nan=False)))
            return cur.rowcount > 0

    def events(self, after=0, limit=200):
        with self.lock:
            rows = self.connection.execute("SELECT * FROM events WHERE id>? ORDER BY id LIMIT ?", (after, limit)).fetchall()
        return [self._event_dict(r) for r in rows]

    @staticmethod
    def _event_dict(row):
        payload = json.loads(row["payload"])
        return {**payload, "id": row["id"], "time": row["timestamp"], "kind": row["kind"], "severity": row["severity"], "message": row["message"], "source": row["source"], "account_id": row["account_id"]}

    def trades(self, account_id=None, since=0, limit=1000):
        query = "SELECT * FROM events WHERE source='mt5' AND kind IN ('trade_opened','trade_closed') AND timestamp>=?"
        args = [since]
        if account_id is not None:
            query += " AND account_id=?"
            args.append(account_id)
        query += " ORDER BY timestamp DESC,id DESC LIMIT ?"
        args.append(limit)
        with self.lock:
            return [self._event_dict(r) for r in self.connection.execute(query, args).fetchall()]

    def save_snapshot(self, account):
        with self.transaction() as conn:
            conn.execute("INSERT INTO snapshots VALUES (?,?,?) ON CONFLICT(account_id) DO UPDATE SET timestamp=excluded.timestamp,payload=excluded.payload", (account["account_id"], account["timestamp"], json.dumps(account, allow_nan=False)))
            conn.execute("INSERT OR IGNORE INTO equity_samples VALUES (?,?,?,?,?)", (account["account_id"], int(account["timestamp"] // 60), account["timestamp"], account["equity"], account["balance"]))

    def equity_samples(self, account_id, since=0, limit=5000):
        with self.lock:
            rows = self.connection.execute("SELECT timestamp,equity,balance FROM equity_samples WHERE account_id=? AND timestamp>=? ORDER BY timestamp DESC LIMIT ?", (account_id, since, limit)).fetchall()
        return [dict(row) for row in reversed(rows)]

    def command(self, request_id):
        with self.lock:
            row = self.connection.execute("SELECT * FROM commands WHERE request_id=?", (request_id,)).fetchone()
            return dict(row) if row else None

    def save_command(self, request_id, command_id, fingerprint, action, response):
        with self.transaction() as conn:
            conn.execute("INSERT INTO commands VALUES (?,?,?,?,?,?,?)", (request_id, command_id, fingerprint, action, time.time(), response["state"], json.dumps(response)))

    def acknowledge(self, command_id, accepted, state, message):
        with self.transaction() as conn:
            row = conn.execute("SELECT response FROM commands WHERE command_id=?", (command_id,)).fetchone()
            if row:
                response = json.loads(row[0])
                response.update(accepted=accepted, state=state, message=message)
                conn.execute("UPDATE commands SET status=?,response=? WHERE command_id=?", (state, json.dumps(response), command_id))

    def backup(self, destination: Path):
        destination = Path(destination)
        destination.parent.mkdir(parents=True, exist_ok=True)
        with self.lock, sqlite3.connect(destination) as target:
            self.connection.backup(target)

    def close(self):
        with self.lock:
            self.connection.close()
