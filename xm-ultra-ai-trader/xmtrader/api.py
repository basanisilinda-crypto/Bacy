"""Authenticated local dashboard and demo-only control API."""

import asyncio
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
import hmac
import ipaddress
import logging
from typing import Literal

from fastapi import Depends, FastAPI, HTTPException, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, ConfigDict, Field
from starlette.middleware.trustedhost import TrustedHostMiddleware

from . import __version__
from .assistant import respond
from .auth import Auth
from .bridge import Bridge, BridgeError, STRATEGIES
from .settings import DEFAULT_RISK, Settings
from .storage import Storage

logger = logging.getLogger("xmtrader")
COOKIE = "xm_session"


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)


class Login(StrictModel):
    password: str = Field(min_length=1, max_length=1024, repr=False)


class Control(StrictModel):
    action: Literal["start", "pause", "resume", "emergency_stop", "liquidate"]
    confirmation: str = Field(default="", max_length=64)
    request_id: str = Field(min_length=8, max_length=128, pattern=r"^[a-zA-Z0-9_-]+$")


class RiskSettings(StrictModel):
    risk_per_trade_pct: float = Field(default=0.25, gt=0, le=0.25)
    daily_loss_pct: float = Field(default=1.0, gt=0, le=1.0)
    weekly_loss_pct: float = Field(default=3.0, gt=0, le=3.0)
    max_drawdown_pct: float = Field(default=5.0, gt=0, le=5.0)
    max_positions: int = Field(default=2, ge=1, le=2, strict=True)
    max_consecutive_losses: int = Field(default=3, ge=1, le=3, strict=True)


class StrategyChange(StrictModel):
    enabled: bool
    confirmation: Literal["CHANGE DEMO STRATEGY"]


class AssistantQuery(StrictModel):
    message: str = Field(min_length=1, max_length=2000)


def create_app(settings: Settings | None = None):
    settings = settings or Settings()
    storage = Storage(settings.database_path)
    auth = Auth(settings)
    bridge = Bridge(settings, storage)

    async def poll_forever():
        while True:
            try:
                await asyncio.to_thread(bridge.poll)
            except Exception:
                bridge.lease_enabled = False
                bridge.last_error = "Backend monitoring failed; new trades are blocked"
                logger.error("Backend monitoring failed; trading lease disabled")
            await asyncio.sleep(settings.poll_seconds)

    @asynccontextmanager
    async def lifespan(application):
        bridge.startup()
        task = asyncio.create_task(poll_forever()) if settings.background_poll else None
        try:
            yield
        finally:
            if task:
                task.cancel()
                try:
                    await task
                except asyncio.CancelledError:
                    pass
            bridge.shutdown()
            storage.close()

    app = FastAPI(title="XM ULTRA AI TRADER", version=__version__, lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.state.settings, app.state.storage, app.state.auth, app.state.bridge = settings, storage, auth, bridge
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=list(settings.allowed_hosts))

    @app.exception_handler(RequestValidationError)
    async def validation_error(request, error):
        # Pydantic's default error includes raw inputs, which can include a password.
        return JSONResponse({"detail": [{"loc": list(item["loc"]), "msg": item["msg"], "type": item["type"]} for item in error.errors()]}, status_code=422)

    @app.exception_handler(BridgeError)
    async def bridge_error(request, error):
        return JSONResponse({"detail": str(error)}, status_code=409)

    @app.middleware("http")
    async def security(request: Request, call_next):
        client = request.client.host if request.client else "unknown"
        try:
            local_client = ipaddress.ip_address(client).is_loopback
        except ValueError:
            local_client = client == "testclient" and "testserver" in settings.allowed_hosts
        if not local_client and not settings.secure_cookie:
            return JSONResponse({"detail": "Network access requires configured HTTPS"}, status_code=403)
        if settings.secure_cookie and not local_client and request.url.scheme != "https":
            return JSONResponse({"detail": "HTTPS is required"}, status_code=403)
        if request.url.path.startswith("/api/"):
            if not auth.check_limit(client, "request", settings.requests_per_minute, 60):
                return JSONResponse({"detail": "Request rate exceeded"}, status_code=429, headers={"Retry-After": "60"})
            if request.method in {"POST", "PUT", "PATCH", "DELETE"}:
                origin = request.headers.get("origin")
                expected = str(request.base_url).rstrip("/")
                allowed = {expected, *settings.allowed_origins}
                if origin and (origin == "null" or origin.rstrip("/") not in allowed):
                    return JSONResponse({"detail": "Request origin is not allowed"}, status_code=403)
                content_length = request.headers.get("content-length", "0")
                if not content_length.isdigit() or int(content_length) > settings.max_body_bytes:
                    return JSONResponse({"detail": "Request body is too large"}, status_code=413)
                body = await request.body()
                if len(body) > settings.max_body_bytes:
                    return JSONResponse({"detail": "Request body is too large"}, status_code=413)
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Content-Security-Policy"] = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"
        response.headers["Permissions-Policy"] = "camera=(), geolocation=(), microphone=(self)"
        if settings.secure_cookie:
            response.headers["Strict-Transport-Security"] = "max-age=31536000"
        if request.url.path.startswith("/api/"):
            response.headers["Cache-Control"] = "no-store"
        return response

    def session(request: Request):
        value = auth.get(request.cookies.get(COOKIE))
        if not value:
            raise HTTPException(401, "Sign in to the local dashboard")
        if request.method not in {"GET", "HEAD", "OPTIONS"}:
            supplied = request.headers.get("X-CSRF-Token", "")
            if not hmac.compare_digest(supplied, value.csrf_token):
                raise HTTPException(403, "Invalid request verification token")
        return value

    @app.get("/api/health")
    def health():
        return {"status": "ok", "version": __version__}

    @app.post("/api/auth/login")
    def login(body: Login, request: Request, response: Response):
        client = request.client.host if request.client else "unknown"
        if not auth.check_limit(client, "login", settings.login_attempts, settings.login_window_seconds):
            raise HTTPException(429, "Sign-in attempts exceeded; wait before trying again", headers={"Retry-After": str(settings.login_window_seconds)})
        result = auth.login(body.password)
        if not result:
            storage.event("login_failed", "Dashboard sign-in failed.", severity="warning")
            raise HTTPException(401, "Unable to sign in; verify local dashboard setup and password")
        auth.logout(request.cookies.get(COOKIE))
        token, value = result
        response.set_cookie(COOKIE, token, max_age=settings.session_seconds, httponly=True, samesite="strict", secure=settings.secure_cookie, path="/")
        storage.event("login", "Owner signed in to the local dashboard.")
        return {"authenticated": True, "csrf_token": value.csrf_token}

    @app.get("/api/auth/session")
    def auth_session(value=Depends(session)):
        return {"authenticated": True, "csrf_token": value.csrf_token}

    @app.post("/api/auth/logout")
    def logout(request: Request, response: Response, value=Depends(session)):
        auth.logout(request.cookies.get(COOKIE))
        response.delete_cookie(COOKIE, path="/", secure=settings.secure_cookie, httponly=True, samesite="strict")
        storage.event("logout", "Owner signed out of the dashboard; terminal operation is unchanged.")
        return {"authenticated": False}

    @app.get("/api/status")
    def status(value=Depends(session)):
        bridge.poll()
        return bridge.status()

    @app.get("/api/positions")
    def positions(value=Depends(session)):
        state = bridge.status()
        return {"items": list(bridge.positions) if state["connected"] else [], "stale": state["stale"], "source": "mt5"}

    @app.get("/api/trades")
    def trades(value=Depends(session)):
        account_id = bridge.status()["account_id"]
        return {"items": storage.trades(account_id) if account_id else [], "source": "mt5", "limit": 1000}

    @app.get("/api/events")
    def events(after: int = 0, value=Depends(session)):
        if after < 0:
            raise HTTPException(422, "Event cursor must be nonnegative")
        items = storage.events(after=after)
        return {"items": items, "last_id": items[-1]["id"] if items else after}

    @app.get("/api/settings")
    def get_settings(value=Depends(session)):
        return {**storage.get_config("risk", DEFAULT_RISK), "strategy": storage.get_config("strategy", "ema_cross"), "demo_only": not bool(settings.live_account_id)}

    def require_paused():
        bridge.poll()
        state = bridge.status()
        if bridge.pending or state["robot_state"] == "running" or bridge.lease_enabled:
            raise HTTPException(409, "Pause and wait for terminal acknowledgement before changing configuration")

    @app.put("/api/settings")
    def put_settings(body: RiskSettings, value=Depends(session)):
        with bridge.lock:
            require_paused()
            limits = body.model_dump()
            from .risk import validate_limits
            limits = validate_limits(limits)
            storage.set_config("risk", limits)
            storage.event("risk_configuration", "Demo risk settings changed; they take effect on the next explicit start or resume.", payload={"risk": limits})
            return {**limits, "demo_only": not bool(settings.live_account_id), "applies_on": "next_start_or_resume"}

    @app.get("/api/strategies")
    def strategies(value=Depends(session)):
        return {"items": bridge.strategies()}

    @app.post("/api/strategies/{strategy_id}")
    def strategy(strategy_id: str, body: StrategyChange, value=Depends(session)):
        with bridge.lock:
            if strategy_id not in STRATEGIES:
                raise HTTPException(404, "Unknown execution strategy")
            require_paused()
            current = storage.get_config("strategy", "ema_cross")
            chosen = strategy_id if body.enabled else (None if current == strategy_id else current)
            storage.set_config("strategy", chosen)
            storage.event("strategy_configuration", "Demo strategy selection changed; one strategy may operate at a time.", payload={"strategy": chosen})
            return {"items": bridge.strategies(), "applies_on": "next_start_or_resume"}

    @app.post("/api/control")
    def control(body: Control, value=Depends(session)):
        return bridge.control(body.action, body.request_id, body.confirmation)

    @app.post("/api/assistant")
    def assistant(body: AssistantQuery, value=Depends(session)):
        state = bridge.status()
        items = storage.trades(state["account_id"], limit=100) if state["account_id"] else []
        return respond(body.message, state, bridge.positions, items)

    @app.get("/api/analytics")
    def analytics(period: Literal["daily", "weekly", "monthly", "all"] = "daily", value=Depends(session)):
        from .research.metrics import grouped_performance, performance_metrics
        now = datetime.now(timezone.utc)
        day = now.replace(hour=0, minute=0, second=0, microsecond=0)
        starts = {"daily": day, "weekly": day - timedelta(days=day.weekday()), "monthly": day.replace(day=1)}
        since = starts[period].timestamp() if period != "all" else 0
        account_id = bridge.status()["account_id"]
        history = storage.trades(account_id, since=since, limit=10_000) if account_id else []
        closed = [record for record in history if record["kind"] == "trade_closed"]
        equity = storage.equity_samples(account_id, since) if account_id else []
        metrics = performance_metrics(closed)
        return {"status": metrics.get("status", "available" if closed else "insufficient_data"), "period": period,
                "timezone": "UTC", "source": "actual_mt5_events", "metrics": metrics, "closed_trade_count": len(closed),
                "counting_basis": "closed_deal_events; partial closes may create more than one event per position",
                "entry_costs_note": "Close-event metrics include costs recorded on those close events. Entry-deal commissions require position-level reconciliation for a complete net result.",
                "history_truncated": len(history) == 10_000, "by_strategy": grouped_performance(closed, key="strategy"),
                "by_symbol": grouped_performance(closed, key="symbol"), "equity_curve": equity,
                "notice": "Recorded historical results do not establish future profitability."}

    @app.get("/api/diagnostics")
    def diagnostics(value=Depends(session)):
        state = bridge.status()
        return {"version": __version__, "authentication_configured": auth.configured, "https_configured": settings.secure_cookie,
                "database": "ready", "bridge_connected": state["connected"], "bridge_reason": state["reason"],
                "demo_only": not bool(settings.live_account_id), "lease_active": bridge.lease_enabled, "pending_command": bridge.pending,
                "capabilities": state["capabilities"], "validation": {"mt5_compilation": "requires_windows_metaeditor", "broker_demo_test": "not_verified_by_backend"}}

    @app.get("/")
    def index():
        path = settings.web_dir / "index.html"
        if not path.is_file():
            return JSONResponse({"detail": "Dashboard assets are unavailable"}, status_code=503)
        return FileResponse(path, media_type="text/html", headers={"Cache-Control": "no-cache"})

    @app.get("/{asset}")
    def asset(asset: str):
        permitted = {"app.js": "application/javascript", "style.css": "text/css", "manifest.webmanifest": "application/manifest+json", "sw.js": "application/javascript"}
        if asset not in permitted:
            raise HTTPException(404)
        path = settings.web_dir / asset
        if not path.is_file():
            raise HTTPException(404)
        return FileResponse(path, media_type=permitted[asset], headers={"Cache-Control": "no-cache"})

    icons = settings.web_dir / "icons"
    if icons.is_dir():
        app.mount("/icons", StaticFiles(directory=icons), name="icons")
    return app


app = create_app()
