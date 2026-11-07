"""Browser acceptance checks using intercepted fixture responses, never production writes."""
from pathlib import Path
import json,time
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
t=int(time.time())
def device(name,ref,due,status):
 return dict(id=ref,name=name,phone='0600000000',reference=ref,due=due,revision=1,acknowledged_revision=1,released=False,policy='{}',model='Test handset' if status!='not_enrolled' else None,last_seen=t,enforcement_status=status,approved_total=300,approved_count=1)
records=dict(client_release=dict(version='0.18.0',version_code=27),devices=[device('Alpha fixture','TEST-A',t+1000,'unrestricted'),device('Beta fixture','TEST-B',t-1000,'not_enrolled')],claims=[dict(id='1',name='Alpha fixture',reference='TEST-A',bank_reference='BANK-TEST-1',amount=300,state='pending',submitted=t),dict(id='2',name='Beta fixture',reference='TEST-B',bank_reference='BANK-TEST-2',amount=300,state='approved',submitted=t)],audit=[],support=[],messages=[],server_time=t,payment_summary=dict(approved_total=300,approved_count=1,pending_count=1))
records['devices'][0]['app_version']=dict(code=26,name='0.17.1',update_state='ready',progress=100,reported_at=t)
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True,args=['--no-sandbox'])
 context=browser.new_context(viewport={'width':1440,'height':1050})
 def route(req):
  path=req.request.url.split('loop-ui.test',1)[-1].split('?',1)[0]
  if path.startswith('/api/'):
   assert req.request.method=='GET','UI checks must never write customer data'
   value=records if path=='/api/admin/overview' else {'email':'owner@fixture.invalid','role':'owner'} if path=='/api/auth/me' else {'requests':[]}
   req.fulfill(status=200,content_type='application/json',body=json.dumps(value));return
  f=ROOT/'backend/static'/('index.html' if path=='/' else path.removeprefix('/static/'))
  kind='text/html' if f.suffix=='.html' else 'text/javascript' if f.suffix=='.js' else 'text/css' if f.suffix=='.css' else 'image/png' if f.suffix=='.png' else 'font/woff2'
  req.fulfill(status=200,content_type=kind,body=f.read_bytes())
 context.route('https://loop-ui.test/**',route)
 page=context.new_page();errors=[];page.on('pageerror',lambda err:errors.append(str(err)))
 page.goto('https://loop-ui.test/');page.locator('#workspace').wait_for(state='visible')
 page.keyboard.press('Control+k');assert page.locator('#search').evaluate('(el)=>el===document.activeElement')
 page.select_option('#customer-sort','due');assert 'Beta fixture' in page.locator('#devices-table tr').first.inner_text()
 page.get_by_role('button',name='Setup pending · 1',exact=True).click();assert page.locator('#devices-table tr').count()==1
 page.get_by_role('button',name='All · 2',exact=True).click()
 page.get_by_role('button',name='Compact rows',exact=True).click();assert page.locator('html').get_attribute('data-density')=='compact'
 page.reload();page.locator('#workspace').wait_for(state='visible');assert page.locator('html').get_attribute('data-density')=='compact';assert page.locator('#devices-view').is_visible()
 page.evaluate("notice('Fixture status message')");page.get_by_role('button',name='Dismiss message').click();assert page.locator('#notice').is_hidden()
 page.get_by_role('button',name='Setup help',exact=True).click();assert page.locator('#workspace-help').is_visible();page.keyboard.press('Escape')
 page.locator('nav [data-view="payments"]').click();page.locator('#payment-search').fill('BANK-TEST-1');assert page.locator('#payments-list .payment-card').count()==1
 assert page.locator('[data-payment-filter="approved"]').last.inner_text()=='Approved (1)'
 page.locator('nav [data-view="devices"]').click();page.set_viewport_size({'width':390,'height':844});assert page.locator('#devices-table tr').first.evaluate('(el)=>getComputedStyle(el).display')=='block'
 assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'),'Mobile page overflows horizontally'
 assert page.locator('#devices-table tr').first.bounding_box()['height']<600,'Mobile customer card is unnecessarily tall'
 assert page.locator('.full-register table').evaluate('(el)=>el.scrollWidth<=el.clientWidth+1'),'Mobile table clips card content'
 context.set_offline(True);assert page.locator('.connection-banner').is_visible();context.set_offline(False)
 page.locator("#refresh").click()
 page.wait_for_function("document.querySelector('#sync-status').textContent.includes('Updated ')",timeout=3000)
 assert page.locator('#sync-status').inner_text().startswith('Updated ')
 assert not errors,errors
 out=ROOT/'artifacts';out.mkdir(exist_ok=True);page.screenshot(path=str(out/'admin-mobile-improvements.png'),full_page=True)
 page.set_viewport_size({'width':1440,'height':1050});page.screenshot(path=str(out/'admin-desktop-improvements.png'),full_page=True)
 page.locator('nav [data-view="overview"]').click();page.screenshot(path=str(out/'admin-overview-redesign.png'),full_page=True)
 page.locator('nav [data-view="payments"]').click();page.screenshot(path=str(out/'admin-payments-redesign.png'),full_page=True)
 # Gateway UI uses intercepted fixture calls only: no live financial writes.
 def gateway_route(route):
  request=route.request
  if request.method=='PUT':
   data=request.post_data_json
   assert data=={'app_id':'','app_secret':'','enabled':True},data
  else: assert request.method=='GET'
  route.fulfill(status=200,content_type='application/json',body=json.dumps({'configured':True,'enabled':True,'app_id_hint':'…TEST','callback_url':'https://fixture.invalid/callback','return_url':'https://fixture.invalid/return'}))
 context.route('https://loop-ui.test/api/admin/payments/ikhokha',gateway_route)
 page.locator('nav [data-view="devices"]').click()
 page.locator('#show-update-needed').click()
 assert page.locator('#devices-table tr').count()==1
 assert '0.17.1' in page.locator('#devices-table').inner_text()
 assert '1 ready to install' in page.locator('#update-fleet-summary').inner_text()
 page.locator('#customer-filter').select_option('all')
 page.locator('nav [data-view="payments"]').click()
 assert page.get_by_role('button',name='Export displayed claims').is_visible()
 context.route('https://loop-ui.test/api/admin/payments/ikhokha/reconcile',lambda r:r.fulfill(status=200,content_type='application/json',body=json.dumps({'checked':True})))
 page.get_by_role('button',name='Verify recent online payments').click()
 page.wait_for_function("document.querySelector('#notice').textContent.includes('checked against iKhokha')")
 page.locator('#open-ikpay-settings').click()
 page.locator('#ikpay-settings-dialog').wait_for(state='visible')
 assert page.locator('#ikpay-settings-form input[name="app_secret"]').input_value()==''
 page.locator('#ikpay-settings-form button[type="submit"]').click()
 page.locator('#ikpay-settings-dialog').wait_for(state='hidden')
 def link_route(route):
  assert route.request.method=='POST'
  route.fulfill(status=200,content_type='application/json',body=json.dumps({'checkout_url':'https://securepay.ikhokha.red/fixture-only','state':'pending'}))
 context.route('https://loop-ui.test/api/admin/devices/TEST-A/payment-link',link_route)
 page.locator('nav [data-view="devices"]').click()
 page.get_by_role('button',name='Manage Alpha fixture',exact=True).click()
 page.locator('#create-ikpay-link').click()
 page.locator('#ikpay-link-url').wait_for(state='visible')
 assert page.locator('#ikpay-link-url').input_value()=='https://securepay.ikhokha.red/fixture-only'
 assert page.locator('#copy-ikpay-link').is_visible()
 page.once('dialog',lambda dialog:dialog.accept())
 page.locator('#renew-ikpay-link').click()
 page.wait_for_function("document.querySelector('#ikpay-link-status').textContent.startsWith('Fresh checkout ready')")
 page.keyboard.press('Escape')
 assert not errors,errors
 browser.close()
 print('Browser checks passed: sorting, filters, saved density/navigation, payment search/counts, help, dismissible status, refresh feedback, offline banner and mobile cards without horizontal overflow.')
