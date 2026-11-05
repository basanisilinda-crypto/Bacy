"""Browser integration against an actual disposable local HTTP backend."""
import shutil
import socket
import threading
import time
from pathlib import Path

import httpx
import pytest
import uvicorn
from xmtrader.api import create_app
from xmtrader.settings import Settings


def test_dashboard_login_navigation_and_grounded_chat(tmp_path):
    chromium = shutil.which('chromium')
    if not chromium:
        pytest.skip('Chromium required for browser acceptance test')
    playwright = pytest.importorskip('playwright.sync_api')
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    app = create_app(Settings(local_passwordless=True,database_path=tmp_path/'state.db', bridge_dir=tmp_path/'bridge',
        password_hash_file=tmp_path/'absent', admin_password='browser-test-password', background_poll=False))
    server = uvicorn.Server(uvicorn.Config(app, host='127.0.0.1', port=port, log_level='error'))
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()
    url = f'http://127.0.0.1:{port}'
    try:
        for _ in range(100):
            try:
                if httpx.get(url+'/api/health').status_code == 200:
                    break
            except httpx.ConnectError:
                time.sleep(.05)
        with playwright.sync_playwright() as p:
            browser = p.chromium.launch(executable_path=chromium, args=['--no-sandbox'])
            page = browser.new_page(viewport={'width':1440,'height':1000})
            errors=[]
            page.on('pageerror', lambda error: errors.append(str(error)))
            page.goto(url)
            page.locator('#workspace').wait_for(state='visible')
            playwright.expect(page.locator("#connection-text")).to_have_text("MT5 unavailable")
            assert page.locator('#metric-equity').inner_text() == '—'
            assert page.locator('#overview-start').is_disabled()
            page.goto(url+'/#intelligence')
            page.locator('#chat-input').fill('How much profit did we make today?')
            page.locator('#chat-submit').click()
            playwright.expect(page.locator(".chat-message.assistant")).to_have_count(2)
            assert 'unavailable' in page.locator('.chat-message.assistant').last.inner_text()
            page.goto(url+'/#controls')
            playwright.expect(page.locator('#page-controls')).to_be_visible()
            assert not errors, errors
            page.set_viewport_size({'width':390,'height':844})
            page.goto(url+'/#overview')
            playwright.expect(page.locator('#page-overview')).to_be_visible()
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1')
            artifacts=Path(__file__).resolve().parents[1]/'artifacts'
            artifacts.mkdir(exist_ok=True)
            page.screenshot(path=str(artifacts/'dashboard-mobile.png'),full_page=True)
            page.screenshot(path=str(artifacts/'dashboard-mobile-firstlook.png'),full_page=False)
            page.set_viewport_size({'width':1440,'height':1000})
            page.screenshot(path=str(artifacts/'dashboard-desktop.png'),full_page=True)
            browser.close()
    finally:
        server.should_exit=True
        thread.join(timeout=5)
