"""Browser integration against an actual disposable local HTTP backend."""
import csv
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
from xmtrader.bridge import ACCOUNT_FIELDS, POSITION_FIELDS, EVENT_FIELDS


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
            page.locator('#motion-toggle').click()
            assert 'visuals-paused' in page.locator('body').get_attribute('class')
            assert page.locator('#motion-toggle').inner_text() == 'Play visuals'
            page.locator('#motion-toggle').click()
            # Regression: clicking Start while emergency is latched deliberately
            # sends resume to the real local API; it never fabricates an ACK.
            directory=app.state.settings.bridge_dir
            directory.mkdir(exist_ok=True)
            (directory/'bridge.secret').write_text('x'*48)
            account=dict(zip(ACCOUNT_FIELDS,[1,time.time(),'12345','XM Demo','demo','USD',10000,10000,0,10000,0,0,0,'emergency','Emergency stop (test fixture)',2,10,20,30,40]))
            for name,fields,rows in [('account.csv',ACCOUNT_FIELDS,[account]),('positions.csv',POSITION_FIELDS,[]),('events.csv',EVENT_FIELDS,[])]:
                with (directory/name).open('w',newline='') as stream:
                    writer=csv.DictWriter(stream,fieldnames=fields)
                    writer.writeheader()
                    writer.writerows(rows)
            page.reload()
            playwright.expect(page.locator('#workspace')).to_be_visible()
            playwright.expect(page.locator('#connection-text')).to_have_text('MT5 connected')
            with page.expect_response(lambda response: response.url.endswith('/api/control') and response.request.method=='POST') as sent:
                page.locator('#overview-start').click()
            assert sent.value.request.post_data_json['action']=='resume'
            assert sent.value.request.post_data_json['confirmation']=='RESUME DEMO'
            assert sent.value.status==200
            assert sent.value.json()['state']=='pending'
            assert app.state.bridge.pending['action']=='resume'
            assert app.state.bridge.status()['robot_state']=='emergency'
            browser.close()
    finally:
        server.should_exit=True
        thread.join(timeout=5)
