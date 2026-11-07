import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const root=new URL('../',import.meta.url);
const source=await readFile(new URL('dist/server/index.js',root),'utf8');
JSON.parse(await readFile(new URL('dist/.openai/hosting.json',root),'utf8'));
const originalFetch=globalThis.fetch;let upstreamMode='ok';let calls=0;
globalThis.fetch=async(url,options)=>{
 calls++;assert.ok(url.startsWith('https://backend.example/'));
 assert.equal(options.redirect,'manual','Workers supports manual/follow redirect modes; credentials must never follow redirects');
 assert.equal(options.headers.Authorization,'Bearer fixture-server-key');
 if(upstreamMode==='failure')throw new TypeError('Fixture transport unavailable');
 if(upstreamMode==='redirect')return new Response(null,{status:302,headers:{Location:'https://untrusted.example/'}});
 return Response.json({devices:[],claims:[],audit:[],messages:[],support:[]});
};
const module=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
globalThis.fetch=originalFetch;
const env={LOOP_ADMIN_EMAIL:'owner@example.com',LOOP_ADMIN_TOKEN:'fixture-server-key',LOOP_API_URL:'https://backend.example'};
const ownerHeaders={'oai-authenticated-user-id':'site-owner','oai-authenticated-user-email':'owner@example.com'};
assert.equal((await module.default.fetch(new Request('https://admin.example/health'),{})).status,503);
assert.equal((await module.default.fetch(new Request('https://admin.example/health'),env)).status,200);
const rootPage=await module.default.fetch(new Request('https://admin.example/'),env);
assert.equal(rootPage.status,200);assert.match(await rootPage.text(),/enrollment-form/);
const before=calls;
assert.equal((await module.default.fetch(new Request('https://admin.example/api/admin/overview'),env)).status,401);
assert.equal(calls,before);
assert.equal((await module.default.fetch(new Request('https://admin.example/api/auth/me',{headers:ownerHeaders}),env)).status,200);
assert.equal((await module.default.fetch(new Request('https://admin.example/api/admin/overview',{headers:ownerHeaders}),env)).status,200);
upstreamMode='redirect';assert.equal((await module.default.fetch(new Request('https://admin.example/api/admin/overview',{headers:ownerHeaders}),env)).status,502);
upstreamMode='failure';assert.equal((await module.default.fetch(new Request('https://admin.example/health'),env)).status,503);
assert.equal((await module.default.fetch(new Request('https://admin.example/api/admin/overview',{headers:ownerHeaders}),env)).status,502);
const cross=await module.default.fetch(new Request('https://admin.example/api/admin/messages',{method:'POST',headers:{...ownerHeaders,origin:'https://attacker.example'},body:'{}'}),env);
assert.equal(cross.status,403);
console.log('Administrator checks passed: owner access, authenticated backend proxy, supported redirect handling, failed dependency health and cross-origin denial.');

// Regression: an upstream data failure must not turn a valid login into a sign-in loop.
const vm=await import('node:vm');
const client=await (await module.default.fetch(new Request('https://admin.example/static/app.js'),env)).text();
const apiFunction=client.match(/^async function api\(.*$/m)[0];
const enterFunction=client.match(/^async function enter\(.*$/m)[0];
const loginFunction=[...client.matchAll(/^function showLogin\(.*$/gm)].at(-1)[0];
const startup=client.match(/^enter\(\)\.catch.*$/m)[0];
for(const failureStatus of [502,401]){
 const elements=new Map();const errors=[];
 const element=selector=>{if(!elements.has(selector))elements.set(selector,{hidden:selector==='#workspace',textContent:'',innerHTML:''});return elements.get(selector);};
 const context=vm.createContext({account:null,$:element,document:{querySelectorAll:()=>[]},Response,Error,Object,
  fetch:async url=>url==='/api/auth/me'?Response.json({email:'fixture-owner@example.com',role:'owner'}):Response.json({detail:'Fixture data failure'},{status:failureStatus}),
  error:e=>errors.push(e)
 });
 vm.runInContext(apiFunction+'\n'+loginFunction+'\n'+enterFunction+'\nasync function refresh(){return api("/admin/overview");}',context);
 await vm.runInContext(startup,context);
 assert.equal(element('#workspace').hidden,failureStatus===401);
 assert.equal(element('#login').hidden,failureStatus!==401);
 assert.equal(errors[0].status,failureStatus);
 if(failureStatus===502)assert.equal(context.account.role,'owner');else assert.equal(context.account,null);
}
console.log('Startup regression passed: backend failures retain the signed-in dashboard; actual authentication failures require sign-in.');
const pageText=await (await module.default.fetch(new Request('https://admin.example/'),env)).text();
const enrollmentForm=pageText.match(/<form id="enrollment-form">[\s\S]*?<\/form>/)[0];
assert.deepEqual([...enrollmentForm.matchAll(/<input name="([^"]+)"/g)].map(x=>x[1]),['name','phone']);
assert.ok(!enrollmentForm.includes('<fieldset>'));
const handler=client.match(/^\$\('#enrollment-form'\)\.onsubmit=.*$/m)[0];
const fields={name:{value:'Customer fixture'},phone:{value:'0630000000'}};
const elements=new Map();const select=s=>{if(!elements.has(s))elements.set(s,{elements:{},hidden:false,value:'',textContent:'',attrs:{},classList:{values:new Set(),toggle(k,on){if(on)this.values.add(k);else this.values.delete(k);}},setAttribute(k,v){this.attrs[k]=v;},removeAttribute(k){delete this.attrs[k];}});return elements.get(s);};
let payload;let pending;
const ctx=vm.createContext({$ :select,JSON,Object,enrollment:null,refresh:async()=>{},busy:(_f,fn)=>{pending=fn();},api:async(path,options)=>{if(path==='/admin/enrollments'){payload=JSON.parse(options.body);return {reference:'FIXTURE',server_url:'https://fixture.example',enrollment_token:'fixture-token',public_key:'fixture-public'};}return {available:false};}});
const enrollmentSteps=client.match(/function setEnrollmentStep\(step\)\{[^\n]+\}/)?.[0];assert.ok(enrollmentSteps);vm.runInContext(enrollmentSteps+'\n'+handler,ctx);select('#enrollment-form').onsubmit({preventDefault(){},currentTarget:{elements:fields}});await pending;
assert.deepEqual(payload,{name:'Customer fixture',phone:'0630000000'});assert.equal(select('#enrollment-step-phone').attrs['aria-current'],'step');assert.equal(select('#enrollment-title').textContent,'Phone setup');
console.log('Enrollment UI passed: only name and phone are required or sent; policies and identity fields are absent.');

// Device-health alerts must distinguish stale evidence from a confirmed lock.
const alertFunction=client.slice(client.indexOf('function deviceAlerts('),client.indexOf('function renderHealthAlerts('));
assert.ok(alertFunction.length>100);
const alertContext=vm.createContext({Number,JSON,Error});vm.runInContext(alertFunction,alertContext);
const clock=1700100000;
const healthy={id:'fixture',name:'Customer fixture',reference:'FIXTURE',enforcement_status:'unrestricted',last_seen:clock,due:clock+86400,revision:2,acknowledged_revision:2,released:false,policy:JSON.stringify({enforcement_enabled:true,grace_hours:24})};
const codes=(changes={})=>Array.from(alertContext.deviceAlerts({...healthy,...changes},clock),item=>item.code);
assert.deepEqual(codes(),[]);
assert.deepEqual(codes({enforcement_status:'not_enrolled',last_seen:0}),[]);
assert.ok(codes({last_seen:clock-901}).includes('contact'));
assert.ok(!codes({last_seen:clock-900}).includes('contact'));
assert.ok(codes({enforcement_status:'policy_error'}).includes('enforcement'));
assert.ok(codes({enforcement_status:'unsupported'}).includes('ownership'));
assert.ok(codes({enforcement_status:'needs_online_check'}).includes('clock'));
assert.ok(codes({enforcement_status:'restricted_banking_unavailable'}).includes('banking'));
assert.ok(codes({due:clock-86400}).includes('overdue'));
assert.ok(!codes({due:clock-86399}).includes('overdue'),'Grace has not ended');
assert.ok(!codes({due:clock-86400,last_seen:clock-1}).includes('overdue'),'No post-deadline report yet');
assert.ok(!codes({due:clock-90000,last_seen:clock-901}).includes('overdue'),'Stale unrestricted report is not current proof');
assert.ok(!codes({due:clock-86400,acknowledged_revision:1}).includes('overdue'),'Latest policy not acknowledged');
assert.ok(codes({acknowledged_revision:1}).includes('delivery'));
assert.ok(!codes({due:clock-86400,enforcement_status:'restricted'}).includes('overdue'));
assert.ok(!codes({due:clock-86400,policy:JSON.stringify({enforcement_enabled:false,grace_hours:24})}).includes('overdue'));
assert.ok(codes({policy:'invalid'}).includes('policy'));
assert.ok(codes({released:true,enforcement_status:'unrestricted'}).includes('release'));
assert.deepEqual(codes({released:true,enforcement_status:'released'}),[]);
assert.deepEqual(Array.from(alertContext.deviceAlerts(healthy,NaN)),[]);
console.log('Device-health alert checks passed: contact age, grace boundaries, fresh acknowledged reports, release, banking and unavailable management.');
// Everyday controls use generated request keys and require no typed policy or reason fields.
const quickPanel=pageText.match(/<div id="device-quick-controls">[\s\S]*?<\/div>/)[0];
assert.deepEqual([...quickPanel.matchAll(/data-control="([^"]+)"/g)].map(item=>item[1]),['lock','unlock','grace']);
assert.ok(!/<input|<textarea|<select/.test(quickPanel));
assert.ok(pageText.includes('<details id="advanced-policy">'));
assert.ok(codes({policy:JSON.stringify({enforcement_enabled:true,manual_lock:true,grace_hours:24})}).includes('manual_lock'));
assert.ok(!codes({last_seen:clock-901,policy:JSON.stringify({enforcement_enabled:true,manual_lock:true,grace_hours:24})}).includes('manual_lock'));
assert.ok(!codes({due:clock-172800,policy:JSON.stringify({enforcement_enabled:true,grace_hours:24,grace_until:clock+3600})}).includes('overdue'));
const controlStart=client.indexOf('async function controlDevice(');
const controlFunction=client.slice(controlStart,client.indexOf('\n}\n',controlStart)+3);
const controlButtons=[{disabled:false},{disabled:false},{disabled:false}],controlElements=new Map();
const controlSelect=s=>{if(!controlElements.has(s))controlElements.set(s,{textContent:''});return controlElements.get(s);};
let command;
const controlFixture={id:'fixture-device',released:false,enforcement_status:'unrestricted'};
const controlContext=vm.createContext({selectedDevice:controlFixture,state:{devices:[controlFixture]},controlRequest:null,$:controlSelect,document:{querySelectorAll:()=>controlButtons},crypto:{randomUUID:()=>'fixture-generated-key-123456'},JSON,api:async(path,options)=>{command={path,body:JSON.parse(options.body)};return {delivery:'pending_next_sync'};},refresh:async()=>{}});
vm.runInContext(controlFunction,controlContext);await vm.runInContext('controlDevice("lock")',controlContext);
assert.deepEqual(command,{path:'/admin/devices/fixture-device/control',body:{action:'lock',request_key:'fixture-generated-key-123456'}});
assert.ok(controlSelect('#control-status').textContent.includes('Waiting for the phone'));
assert.ok(controlButtons.every(button=>!button.disabled));
console.log('One-click controls passed: lock/unlock/grace buttons, no typed fields, idempotency key and honest pending confirmation.');
// Admin connection must be discoverable even when browser authentication drops query parameters.
assert.ok(pageText.includes('id="connect-admin-app"'));
assert.ok(pageText.includes('id="admin-connection"'));
assert.ok(pageText.includes('id="pending-admin-requests"'));
const pairingStart=client.indexOf('async function connectNativeAdmin(){');
const pairingSource=client.slice(pairingStart,client.indexOf("\n$('#connect-admin-app')",pairingStart));
const pairEls=new Map();const pairSelect=s=>{if(!pairEls.has(s))pairEls.set(s,{hidden:true,textContent:'',innerHTML:'',scrollIntoView(){}});return pairEls.get(s);};
const pairButton={dataset:{approvePair:'00000000-0000-0000-0000-000000000001'},disabled:false};
const pairCalls=[];const pairCtx=vm.createContext({URLSearchParams,location:{search:'',pathname:'/'},Promise,JSON,$:pairSelect,document:{querySelectorAll:()=>[pairButton]},history:{replaceState(){}},esc:v=>String(v).replaceAll('<','&lt;').replaceAll('>','&gt;'),error:e=>{throw e;},api:async(path,options)=>{pairCalls.push({path,options});if(path.endsWith('/installer'))return {available:true,apk_url:'https://download.example/admin.apk'};if(path.endsWith('/pending'))return {requests:[{ticket:pairButton.dataset.approvePair,name:'Owner phone',code:'12AB34CD'}]};return {status:'approved'};}});
vm.runInContext(pairingSource,pairCtx);await vm.runInContext('connectNativeAdmin()',pairCtx);
assert.equal(pairSelect('#admin-app-download').hidden,false);
assert.ok(pairSelect('#pending-admin-requests').innerHTML.includes('12AB34CD'));
assert.equal(pairCalls.filter(c=>c.path.endsWith('/approve')).length,0,'Owner must explicitly approve');
await pairButton.onclick();
assert.deepEqual(JSON.parse(pairCalls.at(-1).options.body),{ticket:pairButton.dataset.approvePair});
assert.ok(pairSelect('#admin-connect-status').textContent.startsWith('Connected.'));
console.log('Persistent admin connection checks passed: visible entry, download link, query-free pending request discovery and explicit owner approval.');

// Dark Loop Secure direction: local typography, purposeful controls and accessible motion.
const ledgerCss=await (await module.default.fetch(new Request('https://admin.example/static/styles.css'),env)).text();
assert.match(ledgerCss,/font-family:'DM Sans'/);assert.match(ledgerCss,/font-family:'Fraunces'/);
assert.doesNotMatch(ledgerCss,/\b(?:Inter|Roboto|Arial|Space Grotesk)\b/i);
assert.doesNotMatch(ledgerCss,/linear-gradient|radial-gradient|backdrop-filter|glassmorphism/i);
assert.ok(!pageText.includes('class="eyebrow"'));assert.ok(!pageText.includes('hero-art'));assert.ok(!pageText.includes('fleet-health-ring'));
assert.ok(pageText.includes('summary-strip'));assert.ok(pageText.includes('operations-desk'));assert.ok(pageText.includes('setup-steps'));assert.ok(pageText.includes('setup-methods'));assert.ok(ledgerCss.includes('prefers-reduced-motion'));assert.ok(pageText.includes('id="fleet-health-meter"'));
const fontPaths=[...ledgerCss.matchAll(/url\('([^']+\.woff2)'\)/g)].map(match=>match[1]);
assert.equal(fontPaths.length,2);
for(const path of fontPaths){const response=await module.default.fetch(new Request('https://admin.example'+path),env);assert.equal(response.status,200);assert.equal(response.headers.get('content-type'),'font/woff2');const bytes=new Uint8Array(await response.arrayBuffer());assert.equal(String.fromCharCode(...bytes.slice(0,4)),'wOF2');assert.match(response.headers.get('cache-control'),/immutable/);}
assert.equal((await module.default.fetch(new Request('https://admin.example/static/fonts/missing.woff2'),env)).status,404);
const fontLicense=await (await module.default.fetch(new Request('https://admin.example/static/fonts/LICENSES.txt'),env)).text();assert.match(fontLicense,/SIL OPEN FONT LICENSE/);
const ledgerIds=[...pageText.matchAll(/\bid="([^"]+)"/g)].map(match=>match[1]);assert.equal(new Set(ledgerIds).size,ledgerIds.length);
const fleetStart=client.indexOf('function renderFleetHealth(){');const fleetEnd=client.indexOf('\n',fleetStart);const fleetSource=client.slice(fleetStart,fleetEnd<0?client.length:fleetEnd);
const ledgerElements=new Map();const ledgerSelect=s=>{if(!ledgerElements.has(s))ledgerElements.set(s,{textContent:'',innerHTML:'',value:0});return ledgerElements.get(s);};
const ledgerState={devices:[{enforcement_status:'unrestricted',last_seen:1700000000},{enforcement_status:'restricted',last_seen:1699998000},{enforcement_status:'not_enrolled',last_seen:0}],audit:[],server_time:1700000100};
const ledgerContext=vm.createContext({state:ledgerState,$:ledgerSelect,Math,esc:v=>String(v),date:v=>String(v)});
vm.runInContext(fleetSource,ledgerContext);vm.runInContext('renderFleetHealth()',ledgerContext);
assert.equal(ledgerSelect('#fleet-health-meter').value,50);assert.equal(ledgerSelect('#fleet-health-percent').textContent,'50%');
ledgerState.devices=[];vm.runInContext('renderFleetHealth()',ledgerContext);assert.equal(ledgerSelect('#fleet-health-percent').textContent,'—');
console.log('Loop Secure design checks passed: self-hosted licensed fonts, local asset bytes, banned-default removal, unique IDs and real contact-meter calculations.');
ledgerState.devices=[{enforcement_status:'released',released:true,last_seen:1700000000}];vm.runInContext('renderFleetHealth()',ledgerContext);assert.equal(ledgerSelect('#fleet-health-percent').textContent,'—','Released phones must not dilute the managed-device contact rate');

const snapshotSource=client.match(/function paymentSnapshot\(\)\{[^\n]+\}/)?.[0];assert.ok(snapshotSource);
const receiptState={claims:[{state:'approved',amount:300},{state:'pending',amount:300}],payment_summary:{approved_total:2100,approved_count:7,pending_count:4}};
const receiptContext=vm.createContext({state:receiptState,Number});vm.runInContext(snapshotSource,receiptContext);
let receipt=vm.runInContext('paymentSnapshot()',receiptContext);assert.equal(receipt.total,2100);assert.equal(receipt.count,7);assert.equal(receipt.pending,4);assert.equal(receipt.allTime,true);
receiptState.payment_summary=null;receipt=vm.runInContext('paymentSnapshot()',receiptContext);assert.equal(receipt.total,300);assert.equal(receipt.allTime,false);
console.log('Payment receipt summaries passed: lifetime totals and explicit bounded-history fallback.');

const brandResponse=await module.default.fetch(new Request('https://admin.example/static/brand/loop-secure-mark.png'),env);assert.equal(brandResponse.status,200);assert.equal(brandResponse.headers.get('content-type'),'image/png');const logoBytes=new Uint8Array(await brandResponse.arrayBuffer());assert.deepEqual([...logoBytes.slice(0,8)],[137,80,78,71,13,10,26,10]);assert.equal(logoBytes[25],6);assert.ok(pageText.includes('/static/brand/loop-secure-mark.png'));assert.equal((await module.default.fetch(new Request('https://admin.example/static/brand/missing.png'),env)).status,404);assert.match(ledgerCss,/color-scheme:light/);console.log('White setup and logo checks passed: live PNG alpha asset, new layout and actual enrollment step state.');
