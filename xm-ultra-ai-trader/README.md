> Update: account-bound live-mode source support was added after the original demo baseline. See [live-mode notes](docs/LIVE_MODE.md). Defaults remain demo; no live broker validation has been performed. Older demo-specific procedures remain the recommended validation path.

# BC SILINDA FX / BACY05TEN

Independent, demo-default MT5 automation source for Windows, with a lightweight Python service, SQLite history, offline research tools, and an Android-compatible browser dashboard. Not affiliated with XM or MetaQuotes. No broker is connected in this workspace and no orders have been placed.

**No-command Windows setup:** Extract the ZIP and double-click `Start Here.cmd`. Read `READ ME FIRST.txt`. It handles local setup and startup, attempts MetaEditor compilation, and never starts trades automatically. Windows launcher execution remains unverified in this cloud environment.

**Start here: [Windows setup guide](docs/QUICKSTART.md).** This is a source release requiring MetaEditor compilation and broker-demo acceptance testing. It is not a certified, proven-profitable, or production-ready trading product. Live-account source support is available through explicit local account pairing; it is disabled by default and unverified in MT5.

## Included

- MQL5 EA: ten selectable experimental strategy rules, protective stops, broker-aware sizing, independent loss/exposure gates, native-calendar restrictions, position management, expiring authenticated local controls and restart protection.
- FastAPI: authenticated owner sessions, CSRF/origin controls, rate limiting, actual broker-event storage, idempotent commands, risk settings and grounded explanations.
- Responsive dashboard: account/position/history views, performance, strategy controls, emergency stop separate from liquidation, browser speech input/output with typed fallback and local notifications.
- Offline research: technical indicators, ten strategy modules, explicit-cost bar backtesting, walk-forward validation, sensitivity and optional scikit-learn models. No automatic model deployment.
- Local setup, HTTPS/private-network opt-in, process locks, diagnostics and consistent database backups.

## Documentation

- [Windows quickstart](docs/QUICKSTART.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Feature status and limitations](docs/FEATURES.md)
- [Validation and remaining acceptance tests](docs/VALIDATION.md)
- [Android access and security](docs/SECURITY.md)
- [Account workspaces and connection features](docs/WORKSPACE.md)
- [Research usage](docs/RESEARCH.md)
- [Costs](docs/COSTS.md)
- [MT5 engine details](mt5/README.md)

## Developer checks

Python 3.11+, a project virtual environment, and `pip install -e '.[dev,research]'` provide the development dependencies. Run `python -m pytest -q`, `python -m ruff check xmtrader scripts tests`, and `node --check web/app.js`. Chromium is required for the browser acceptance test; it skips when unavailable. Node is a development syntax-check tool, not a Windows runtime requirement.

Start the configured Windows service through the supplied scripts; direct `uvicorn xmtrader.api:app --host 127.0.0.1 --port 8765` is available for development. Without a configured local password authentication fails closed. Do not expose this development command to the internet.

## License

Project source is MIT licensed. MT5 and broker software retain their proprietary licenses. Browser speech services vary by device and provider. Existing electricity, connectivity, hardware, trading costs, and account eligibility are not free services supplied by this project.

Latest dashboard matches the supplied BC SILINDA FX layout. See docs/SCANNER.md for bounded multi-market scanning, one-second signal reevaluation, and remaining execution-validation limits.
