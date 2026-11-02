"""Authenticated local-file bridge. Requests and broker acknowledgements are distinct.

The shared file secret authenticates commands to the EA; Windows file permissions
are still the boundary for another process running as the same desktop user.
"""

import csv
import hashlib
import io
import json
import math
import os
from pathlib import Path
import re
import secrets
import threading
import time

from .settings import DEFAULT_RISK


ACCOUNT_FIELDS = "version,timestamp,account_id,broker,mode,currency,balance,equity,margin,free_margin,floating_pnl,daily_pnl,weekly_pnl,robot_state,reason,heartbeat_seq,decision_us,risk_us,submission_us,broker_response_us".split(",")
POSITION_FIELDS = "ticket,symbol,side,volume,entry_price,sl,tp,profit,opened_at,strategy".split(",")
EVENT_FIELDS = "event_id,timestamp,kind,symbol,ticket,side,volume,price,profit,commission,swap,strategy,message".split(",")
CONTROL_FIELDS = "version,command_id,issued_at,expires_at,action,secret,risk_per_trade_pct,daily_loss_pct,weekly_loss_pct,max_drawdown_pct,max_positions,max_consecutive_losses,strategy".split(",")
ACK_FIELDS = "command_id,timestamp,accepted,state,message".split(",")
STRATEGIES = {
    "ema_cross": ("EMA crossover", "Follows a change in the relationship between fast and slow moving averages."),
    "trend": ("Trend following", "Looks for directional markets with confirmation from trend strength."),
    "momentum": ("Momentum", "Looks for sustained price movement with momentum confirmation."),
    "breakout": ("Breakout", "Looks for price moving beyond a recent trading range."),
    "mean_reversion": ("Mean reversion", "Looks for unusually stretched prices in suitable ranging markets."),
    "support_resistance": ("Support and resistance", "Looks for reactions near recent price boundaries."),
    "volatility": ("Volatility", "Uses changing volatility to identify eligible movements."),
    "session": ("Session aware", "Restricts signals to configured trading-session conditions."),
    "multi_timeframe": ("Multiple timeframes", "Requires agreement between the trading and confirmation timeframes."),
    "regime": ("Market regime", "Selects one signal family according to measured market conditions."),
}


class BridgeError(ValueError):
    pass


def finite(value, *, minimum=None):
    number = float(value)
    if not math.isfinite(number) or (minimum is not None and number < minimum):
        raise BridgeError("Nonfinite or invalid numeric telemetry")
    return number


def read_csv(path, expected, *, max_bytes=2_000_000, max_rows=10_000):
    path = Path(path)
    size = path.stat().st_size
    if size > max_bytes:
        raise BridgeError("Telemetry snapshot exceeds the bounded size")
    raw = path.read_bytes()
    if not raw.endswith(b"\n"):
        raise BridgeError("Incomplete telemetry snapshot")
    text = raw.decode("utf-8-sig", errors="strict")
    reader = csv.DictReader(io.StringIO(text, newline=""))
    if reader.fieldnames != expected:
        raise BridgeError("Unsupported telemetry schema")
    rows = []
    for row in reader:
        if len(rows) >= max_rows or None in row or any(v is None for v in row.values()):
            raise BridgeError("Malformed telemetry row")
        if any(len(v) > 4096 for v in row.values()):
            raise BridgeError("Oversized telemetry value")
        rows.append(row)
    return rows


class Bridge:
    def __init__(self, settings, storage):
        self.settings = settings
        self.storage = storage
        self.lock = threading.RLock()
        self.account = None
        self.positions = []
        self.last_ack = None
        self.pending = None
        self.lease_enabled = False
        self.last_heartbeat = 0.0
        self.last_error = "Waiting for MT5 telemetry"
        self.last_connection = None
        self.last_account_id = None
        self.heartbeat_sequence = None
        self.event_offset = 0
        self.event_file_identity = None
        self.event_header_checked = False
        self.started = False

    def _secret(self):
        path = self.settings.bridge_dir / "bridge.secret"
        if not path.is_file() or path.stat().st_size > 256:
            raise BridgeError("Bridge secret is missing; complete Windows setup")
        secret = path.read_text(encoding="ascii").strip()
        if not re.fullmatch(r"[A-Za-z0-9_-]{32,128}", secret):
            raise BridgeError("Bridge secret is invalid; repeat Windows setup")
        return secret

    def _write(self, command_id, action, now):
        if not self.settings.bridge_dir.is_dir():
            raise BridgeError("MT5 bridge directory does not exist")
        risk = self.storage.get_config("risk", DEFAULT_RISK)
        strategy = self.storage.get_config("strategy", "ema_cross") or "ema_cross"
        values = {"version": 1, "command_id": command_id, "issued_at": int(now),
                  "expires_at": int(now + self.settings.lease_seconds), "action": action,
                  "secret": self._secret(), **risk, "strategy": strategy}
        temporary = self.settings.bridge_dir / ("control." + secrets.token_hex(8) + ".tmp")
        try:
            fd = os.open(temporary, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            with os.fdopen(fd, "w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=CONTROL_FIELDS)
                writer.writeheader()
                writer.writerow(values)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, self.settings.bridge_dir / "control.csv")
        finally:
            temporary.unlink(missing_ok=True)

    def startup(self):
        """Never inherit an authorization to trade from a prior backend process."""
        with self.lock:
            if self.started:
                return
            self.started = True
            self.lease_enabled = False
            self.storage.event("backend_started", "Backend started; a new explicit demo start or resume is required.")
            try:
                self._write("startup-" + secrets.token_hex(12), "pause", time.time())
            except (OSError, ValueError):
                pass  # The lease still expires if the local bridge is unavailable.

    def shutdown(self):
        with self.lock:
            self.lease_enabled = False
            try:
                self._write("shutdown-" + secrets.token_hex(12), "pause", time.time())
            except (OSError, ValueError):
                pass

    def _read_account(self, now):
        rows = read_csv(self.settings.bridge_dir / "account.csv", ACCOUNT_FIELDS, max_rows=1, max_bytes=32_768)
        if len(rows) != 1:
            raise BridgeError("An account snapshot must contain exactly one row")
        row = rows[0]
        if row["version"] != "1" or row["mode"] not in {"demo", "live"}:
            raise BridgeError("Unsupported account telemetry")
        if not re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", row["account_id"]) or not row["currency"] or len(row["currency"]) > 12:
            raise BridgeError("Invalid account identity")
        if row["robot_state"] not in {"paused", "running", "emergency", "blocked"}:
            raise BridgeError("Invalid robot state")
        row["timestamp"] = finite(row["timestamp"], minimum=0)
        age = now - row["timestamp"]
        if age < -self.settings.clock_skew_seconds:
            raise BridgeError("MT5 clock is ahead of this computer; check clock synchronisation")
        if age > self.settings.stale_seconds:
            raise BridgeError("MT5 telemetry is stale; new trades are blocked")
        for key in ("balance", "equity", "margin", "free_margin", "floating_pnl", "daily_pnl", "weekly_pnl"):
            row[key] = finite(row[key])
        if row["margin"] < 0:
            raise BridgeError("Invalid margin telemetry")
        for key in ("heartbeat_seq", "decision_us", "risk_us", "submission_us", "broker_response_us"):
            row[key] = finite(row[key], minimum=0)
        if self.last_account_id and self.last_account_id != row["account_id"]:
            self.lease_enabled = False
            self.positions = []
            self.storage.event("account_changed", "MT5 account changed; demo operation must be explicitly started again.", severity="warning")
            self._write("account-change-" + secrets.token_hex(12), "pause", now)
        if (self.heartbeat_sequence is not None and self.last_account_id == row["account_id"]
                and row["heartbeat_seq"] < self.heartbeat_sequence):
            self.lease_enabled = False
            self.storage.event("terminal_restart", "MT5 telemetry sequence restarted; new explicit demo authorization is required.", severity="warning")
        self.heartbeat_sequence = row["heartbeat_seq"]
        self.last_account_id = row["account_id"]
        self.account = row
        self.storage.save_snapshot(row)

    def _read_positions(self, now):
        path = self.settings.bridge_dir / "positions.csv"
        if now - path.stat().st_mtime > self.settings.stale_seconds:
            raise BridgeError("Position telemetry is stale")
        rows = read_csv(path, POSITION_FIELDS)
        seen = set()
        for row in rows:
            if not row["ticket"].isdigit() or row["ticket"] in seen or not row["symbol"]:
                raise BridgeError("Invalid or duplicate position identity")
            if row["side"].lower() not in {"buy", "sell"}:
                raise BridgeError("Invalid position direction")
            row["side"] = row["side"].lower()
            seen.add(row["ticket"])
            for key in ("volume", "entry_price", "sl", "tp", "opened_at"):
                row[key] = finite(row[key], minimum=0)
            row["profit"] = finite(row["profit"])
            if row["volume"] <= 0 or row["entry_price"] <= 0:
                raise BridgeError("Invalid position volume or entry")
        self.positions = rows

    def _read_events(self):
        path = self.settings.bridge_dir / "events.csv"
        if not path.is_file():
            return
        stat = path.stat()
        identity = (stat.st_dev, stat.st_ino)
        if identity != self.event_file_identity or stat.st_size < self.event_offset:
            self.event_offset = 0
            self.event_header_checked = False
            self.event_file_identity = identity
        with path.open("rb") as stream:
            stream.seek(self.event_offset)
            chunk = stream.read(256_000)
        boundary = chunk.rfind(b"\n")
        if boundary < 0:
            return
        complete = chunk[:boundary + 1]
        # EA text fields must have line breaks removed; records are single-line CSV.
        text = complete.decode("utf-8-sig", errors="strict")
        rows = list(csv.reader(io.StringIO(text)))
        if not self.event_header_checked:
            if not rows or rows[0] != EVENT_FIELDS:
                raise BridgeError("Unsupported event schema")
            rows.pop(0)
            self.event_header_checked = True
        for values in rows:
            if len(values) != len(EVENT_FIELDS) or any(len(v) > 4096 for v in values):
                raise BridgeError("Invalid event telemetry")
            row = dict(zip(EVENT_FIELDS, values))
            event_id = row["event_id"]
            # Prevent old rows from another login being attributed to this account.
            account_id, separator, _ = event_id.partition(":")
            if not separator or not re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", account_id):
                raise BridgeError("Broker event must carry an account-prefixed identifier")
            for key in ("timestamp", "volume", "price", "profit", "commission", "swap"):
                row[key] = finite(row[key])
            if row["timestamp"] < 0 or row["timestamp"] > time.time() + self.settings.clock_skew_seconds:
                raise BridgeError("Invalid event timestamp")
            if not re.fullmatch(r"[a-z_]{1,48}", row["kind"]):
                raise BridgeError("Invalid event kind")
            self.storage.event(row["kind"], row["message"], source="mt5", event_key=event_id,
                               account_id=account_id, timestamp=row["timestamp"], payload=row,
                               severity="warning" if any(x in row["kind"] for x in ("error", "rejected", "risk", "emergency")) else "info")
        self.event_offset += len(complete)

    def _read_ack(self, now):
        path = self.settings.bridge_dir / "ack.csv"
        if not path.exists():
            return
        rows = read_csv(path, ACK_FIELDS, max_rows=1, max_bytes=16_384)
        if len(rows) != 1:
            raise BridgeError("Invalid acknowledgement snapshot")
        ack = rows[0]
        ack["timestamp"] = finite(ack["timestamp"], minimum=0)
        if ack["timestamp"] > now + self.settings.clock_skew_seconds:
            raise BridgeError("Invalid acknowledgement clock")
        if ack["accepted"].lower() not in {"true", "false", "0", "1"}:
            raise BridgeError("Invalid acknowledgement result")
        ack["accepted"] = ack["accepted"].lower() in {"true", "1"}
        if ack["state"] not in {"paused", "running", "emergency", "blocked"}:
            raise BridgeError("Invalid acknowledgement state")
        if self.pending and ack["command_id"] == self.pending["command_id"]:
            if ack["timestamp"] < self.pending["issued_at"] - self.settings.clock_skew_seconds:
                raise BridgeError("Old acknowledgement does not match request time")
            action = self.pending["action"]
            self.lease_enabled = bool(ack["accepted"] and action in {"start", "resume"} and ack["state"] == "running")
            self.storage.acknowledge(ack["command_id"], ack["accepted"], "acknowledged" if ack["accepted"] else "rejected", ack["message"])
            self.storage.event("command_acknowledged" if ack["accepted"] else "command_rejected", ack["message"], payload={"command_id": ack["command_id"], "action": action, "terminal_state": ack["state"]})
            self.last_ack = ack
            self.pending = None

    def poll(self):
        now = time.time()
        with self.lock:
            self.startup()
            try:
                self._read_account(now)
                self._read_positions(now)
                self._read_events()
                self._read_ack(now)
                self.last_error = None
                connected = True
            except (OSError, ValueError, UnicodeError, csv.Error) as error:
                self.last_error = str(error) if isinstance(error, BridgeError) else "MT5 telemetry unavailable or invalid"
                self.lease_enabled = False
                connected = False
            if connected != self.last_connection:
                self.storage.event("connection_restored" if connected else "connection_failure",
                                   "MT5 telemetry is connected." if connected else self.last_error,
                                   severity="info" if connected else "warning")
                self.last_connection = connected
            if self.pending and now - self.pending["issued_at"] > self.settings.lease_seconds:
                self.storage.acknowledge(self.pending["command_id"], False, "unknown", "MT5 did not acknowledge before expiry; inspect terminal before retrying.")
                self.storage.event("command_timeout", "Control request expired without acknowledgement; it is not automatically repeated.", severity="warning")
                self.pending = None
                self.lease_enabled = False
            if connected and self.lease_enabled and not self.pending and now - self.last_heartbeat >= self.settings.heartbeat_seconds:
                try:
                    self._write("heartbeat-" + secrets.token_hex(12), "heartbeat", now)
                    self.last_heartbeat = now
                except (OSError, ValueError):
                    self.lease_enabled = False
                    self.last_error = "Cannot renew local trading lease; new orders will be blocked"

    def status(self):
        with self.lock:
            account = self.account or {}
            timestamp = account.get("timestamp")
            stale = timestamp is None or time.time() - timestamp > self.settings.stale_seconds or bool(self.last_error)
            connected = bool(account) and not stale
            status = {key: account.get(key) for key in ("broker", "account_id", "currency", "balance", "equity", "margin", "free_margin", "floating_pnl", "daily_pnl", "weekly_pnl")}
            status.update(connected=connected, stale=stale, demo_only=not bool(self.settings.live_account_id), mode=account.get("mode", "unknown"),
                          robot_state=account.get("robot_state", "disconnected") if connected else "disconnected",
                          last_update=timestamp, reason=self.last_error or account.get("reason", "Waiting for MT5"),
                          latency={key: account.get(key) for key in ("decision_us", "risk_us", "submission_us", "broker_response_us")},
                          risk=self.storage.get_config("risk", DEFAULT_RISK), pending_command=self.pending,
                          last_ack=self.last_ack, lease_active=self.lease_enabled,
                          capabilities={"live_trading": bool(self.settings.live_account_id) and account.get("account_id") == self.settings.live_account_id, "demo_trading": True, "remote_push": False,
                                        "assistant": "rule_based", "news": "native_mt5_calendar_required", "external_ai": False})
            return status

    def control(self, action, request_id, confirmation=""):
        with self.lock:
            fingerprint = hashlib.sha256(json.dumps({"action": action, "confirmation": confirmation}, sort_keys=True).encode()).hexdigest()
            prior = self.storage.command(request_id)
            if prior:
                if prior["fingerprint"] != fingerprint:
                    raise BridgeError("Request identifier was already used for a different action")
                return json.loads(prior["response"])
            self.poll()
            mode = "LIVE" if self.status()["mode"] == "live" else "DEMO"
            required = {"start": "START " + mode, "resume": "RESUME " + mode, "liquidate": "CLOSE " + mode + " POSITIONS"}
            if action in required and confirmation != required[action]:
                raise BridgeError("Explicit confirmation is required for this action")
            if action in {"start", "resume", "liquidate"}:
                status = self.status()
                if not status["connected"] or (status["mode"] != "demo" and not (status["mode"] == "live" and self.settings.live_account_id and status["account_id"] == self.settings.live_account_id)):
                    raise BridgeError("A connected, current demo account is required, or the exact live account must be authorized locally")
                if action in {"start", "resume"} and not self.storage.get_config("strategy", "ema_cross"):
                    raise BridgeError("Enable one demo strategy before starting")
                if action == "start" and status["robot_state"] == "emergency":
                    raise BridgeError("Emergency stop requires an explicit resume, not start")
            if self.pending and action not in {"pause", "emergency_stop"}:
                raise BridgeError("Wait for the previous command acknowledgement")
            command_id = secrets.token_hex(16)
            now = time.time()
            self.lease_enabled = False
            # Record durable intent before writing a command; no automatic retry on failure.
            response = {"accepted": True, "command_id": command_id, "state": "pending", "message": "Request queued; waiting for MT5 acknowledgement."}
            self.storage.save_command(request_id, command_id, fingerprint, action, response)
            try:
                self._write(command_id, action, now)
            except (OSError, ValueError):
                self.storage.acknowledge(command_id, False, "failed", "Command could not be written to the local MT5 bridge.")
                raise BridgeError("Command could not reach MT5; inspect local bridge setup")
            if self.pending:
                self.storage.acknowledge(self.pending["command_id"], False, "superseded", "Replaced by a pause or emergency request; inspect terminal state.")
            self.pending = {"command_id": command_id, "action": action, "issued_at": now}
            self.storage.event("control_requested", f"Requested {action.replace('_', ' ')}; awaiting terminal acknowledgement.", payload={"command_id": command_id, "action": action})
            return response

    def strategies(self):
        selected = self.storage.get_config("strategy", "ema_cross")
        return [{"id": key, "name": value[0], "enabled": key == selected, "validated": False,
                 "description": value[1], "parameters": {}, "execution_supported": True,
                 "demo_only": not bool(self.settings.live_account_id)} for key, value in STRATEGIES.items()]
