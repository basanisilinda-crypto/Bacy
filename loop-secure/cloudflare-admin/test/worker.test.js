import assert from 'node:assert/strict';
import test from 'node:test';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {generateKeyPair,exportJWK,SignJWT} from 'jose';
import {createWorker} from '../src/worker.js';

const API='https://ourxezawbfclmhbggskv.supabase.co/functions/v1/loop-manager';
const env={CF_ACCESS_TEAM_DOMAIN:'loop-team.cloudflareaccess.com',CF_ACCESS_AUD:'fixture-audience-1234567890',LOOP_ADMIN_EMAIL:'owner@example.com',LOOP_ADMIN_TOKEN:'PRIVATE_TEST_TOKEN_DO_NOT_EMBED',LOOP_API_URL:API};
const {privateKey,publicKey}=await generateKeyPair('RS256');
const publicJwk={...await exportJWK(publicKey),kid:'fixture-rsa-key',alg:'RS256',use:'sig'};
const {privateKey:wrongPrivate}=await generateKeyPair('RS256');
const now=Math.floor(Date.now()/1000);
async function token(overrides={},key=privateKey){return new SignJWT({sub:'owner-subject',email:env.LOOP_ADMIN_EMAIL,iss:'https://'+env.CF_ACCESS_TEAM_DOMAIN,aud:env.CF_ACCESS_AUD,iat:now,exp:now+300,...overrides}).setProtectedHeader({alg:'RS256',kid:publicJwk.kid}).sign(key);}
const valid=await token();
function fixture(mode='ok'){
 const calls=[];
 const worker=createWorker(async(input,options={})=>{
  const url=String(input);
  if(url==='https://'+env.CF_ACCESS_TEAM_DOMAIN+'/cdn-cgi/access/certs')return Response.json({keys:[publicJwk]});
  assert.ok(url.startsWith(API+'/api/admin/'),'Only exact production API prefix may receive backend credentials');
  calls.push({url,options});
  assert.equal(options.redirect,'manual');
  assert.deepEqual(Object.keys(options.headers).sort(),options.headers['Content-Type']?['Authorization','Content-Type']:['Authorization']);
  assert.equal(options.headers.Authorization,'Bearer '+env.LOOP_ADMIN_TOKEN);
  if(mode==='redirect')return new Response(null,{status:302,headers:{location:'https://attacker.example/'}});
  if(mode==='failure')throw new TypeError('No fixture network');
  if(mode==='unauthorized')return Response.json({detail:'upstream denied'}, {status:401});
  if(mode==='binary')return new Response(new Uint8Array([1,2,3,255]),{headers:{'content-type':'application/zip'}});
  return Response.json({devices:[],claims:[],audit:[]});
 });
 return{worker,calls};
}
function request(path='/',options={},jwt=valid){return new Request('https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev'+path,{...options,headers:{...(jwt?{'Cf-Access-Jwt-Assertion':jwt}:{}),...options.headers}});}

test('missing configuration and spoofed identity fail closed without upstream requests',async()=>{
 const {worker,calls}=fixture();
 assert.equal((await worker.fetch(request('/'),{})).status,503);
 for(const bad of [undefined,'https://wrong.example',API+'/'])assert.equal((await worker.fetch(request('/'),{...env,LOOP_API_URL:bad})).status,503);
 assert.equal((await worker.fetch(request('/api/admin/overview',{headers:{'oai-authenticated-user-id':'owner-subject','oai-authenticated-user-email':env.LOOP_ADMIN_EMAIL,'cf-access-authenticated-user-email':env.LOOP_ADMIN_EMAIL,Authorization:'Bearer '+env.LOOP_ADMIN_TOKEN}},null),env)).status,401);
 assert.equal(calls.length,0);
});

test('real RS256 JWT grants only exact configured owner with expected issuer and audience',async()=>{
 const {worker}=fixture();
 const me=await worker.fetch(request('/api/auth/me'),env);assert.equal(me.status,200);assert.deepEqual(await me.json(),{id:'owner-subject',email:env.LOOP_ADMIN_EMAIL,role:'owner'});
 for(const claims of [{email:'someone@example.com'},{iss:'https://attacker.example'},{aud:'another-application'},{exp:now-100},{iat:now,nbf:now+600},{sub:''},{sub:undefined},{email:undefined}]){
  assert.equal((await worker.fetch(request('/api/auth/me',{},await token(claims)),env)).status,401,JSON.stringify(claims));
 }
 assert.equal((await worker.fetch(request('/api/auth/me',{},await token({},wrongPrivate)),env)).status,401);
 const hs=await new SignJWT({sub:'owner-subject',email:env.LOOP_ADMIN_EMAIL,iss:'https://'+env.CF_ACCESS_TEAM_DOMAIN,aud:env.CF_ACCESS_AUD,iat:now,exp:now+300}).setProtectedHeader({alg:'HS256'}).sign(new TextEncoder().encode('a'.repeat(32)));
 assert.equal((await worker.fetch(request('/api/auth/me',{},hs),env)).status,401);
 assert.equal((await worker.fetch(request('/api/auth/me',{},valid.slice(0,-12)),env)).status,401);
});

test('browser writes require exact Origin and never forward client credentials',async()=>{
 const {worker,calls}=fixture();
 for(const origin of [undefined,'null','https://attacker.example']){
  const headers={'content-type':'application/json'};if(origin)headers.origin=origin;
  assert.equal((await worker.fetch(request('/api/admin/messages',{method:'POST',headers,body:'{}'}),env)).status,403);
 }
 assert.equal(calls.length,0);
 const headers={origin:'https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev','content-type':'application/json',Authorization:'Bearer client-should-never-forward',cookie:'private-cookie=not-forwarded','oai-authenticated-user-email':'spoof@example.com'};
 const result=await worker.fetch(request('/api/admin/messages',{method:'POST',headers,body:'{}'}),env);
 assert.equal(result.status,200);assert.equal(calls.length,1);
 assert.equal((await worker.fetch(request('/api/admin/messages',{method:'POST',headers:{...headers,'sec-fetch-site':'cross-site'},body:'{}'}),env)).status,403);
});

test('upstream redirects, authorization failure and network failure preserve Access login',async()=>{
 for(const mode of ['redirect','failure','unauthorized']){
  const {worker}=fixture(mode);assert.equal((await worker.fetch(request('/api/admin/overview'),env)).status,502);
  assert.equal((await worker.fetch(request('/api/auth/me'),env)).status,200);
 }
 const {worker,calls}=fixture('binary');
 const result=await worker.fetch(request('/api/admin/provisioning/usb-bundle',{method:'POST',headers:{origin:'https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev','content-type':'application/json'},body:'{}'}),env);
 assert.equal(result.headers.get('content-type'),'application/zip');assert.deepEqual(new Uint8Array(await result.arrayBuffer()),new Uint8Array([1,2,3,255]));assert.equal(calls.length,1);
});

test('generated interface preserves pairing and exact assets without backend secrets',async()=>{
 const {worker,calls}=fixture();
 const page=await worker.fetch(request('/?connect_admin=fixture-ticket'),env);const html=await page.text();
 assert.match(html,/bacyadmin:\/\/signin/);assert.match(html,/id="login-form"/);assert.doesNotMatch(html,/name="password"/);
 const client=await (await worker.fetch(request('/static/app.js'),env)).text();
 assert.match(client,/connect_admin/);assert.match(client,/\/cdn-cgi\/access\/login/);assert.match(client,/\/cdn-cgi\/access\/logout/);
 assert.doesNotMatch(client,/Sign in with ChatGPT|signin-with-chatgpt|\/auth\/login'\,\{method/);
 for(const value of [html,client,await(await worker.fetch(request('/static/styles.css'),env)).text()])assert.ok(!value.includes(env.LOOP_ADMIN_TOKEN));
 for(const path of ['brand/loop-secure-mark.png','fonts/dm-sans-9fea608a947e.woff2','fonts/fraunces-7234ed860a9c.woff2']){
  const asset=await worker.fetch(request('/static/'+path),env);assert.equal(asset.status,200);
  const bytes=Buffer.from(await asset.arrayBuffer());
  try{assert.deepEqual(bytes,await readFile(new URL('../../backend/static/'+path,import.meta.url)));}catch(error){if(error.code!=='ENOENT')throw error;}
  const manifest=JSON.parse(await readFile(new URL('../asset-manifest.json',import.meta.url),'utf8'));
  assert.equal(createHash('sha256').update(bytes).digest('hex'),manifest[path]);
 }
 assert.equal(calls.length,0,'Static content and health must not query privileged customer data');
 assert.equal((await worker.fetch(request('/health'),env)).status,200);assert.equal(calls.length,0);
 assert.equal((await worker.fetch(request('/api/auth/login',{method:'POST',headers:{origin:'https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev'},body:'{}'}),env)).status,405);
});


test('website activation requires verified owner and successful real overview, then caches success',async()=>{
 let checks=0,writes=0,overviewStatus=500;
 const worker=createWorker(async(input,options={})=>{
  const url=String(input);
  if(url.endsWith('/cdn-cgi/access/certs'))return Response.json({keys:[publicJwk]});
  assert.ok(url.startsWith(API+'/api/admin/'));assert.equal(options.headers.Authorization,'Bearer '+env.LOOP_ADMIN_TOKEN);assert.equal(options.redirect,'manual');
  if(url.endsWith('/overview'))return Response.json({devices:[],claims:[],audit:[]},{status:overviewStatus});
  assert.ok(options.signal instanceof AbortSignal);
  if(options.method==='GET'){checks++;return Response.json({url:'https://phone-finance-manager-bc.bacanyclinda.chatgpt.site'});}
  writes++;const data=JSON.parse(options.body);assert.deepEqual(data,{url:'https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev'});return Response.json(data);
 });
 assert.equal((await worker.fetch(request('/api/admin/overview',{},await token({aud:'wrong'})),env)).status,401);assert.equal(checks,0);assert.equal(writes,0);
 assert.equal((await worker.fetch(request('/api/admin/overview'),env)).status,500);assert.equal(checks,0);assert.equal(writes,0);
 overviewStatus=200;const good=await worker.fetch(request('/api/admin/overview'),env);assert.equal(good.status,200);assert.deepEqual(await good.json(),{devices:[],claims:[],audit:[]});assert.equal(checks,1);assert.equal(writes,1);
 assert.equal((await worker.fetch(request('/api/admin/overview'),env)).status,200);assert.equal(checks,1);assert.equal(writes,1);
});

test('failed activation retries without changing customer response or login',async()=>{
 let writes=0,checks=0;
 const worker=createWorker(async(input,options={})=>{
  const url=String(input);if(url.endsWith('/cdn-cgi/access/certs'))return Response.json({keys:[publicJwk]});
  if(url.endsWith('/overview'))return Response.json({devices:[],claims:[],audit:[]});
  if(options.method==='GET'){checks++;return Response.json({url:'https://phone-finance-manager-bc.bacanyclinda.chatgpt.site'});}
  writes++;return writes===1?Response.json({detail:'temporary failure'},{status:503}):Response.json(JSON.parse(options.body));
 });
 for(let i=0;i<3;i++){assert.equal((await worker.fetch(request('/api/admin/overview'),env)).status,200);assert.equal((await worker.fetch(request('/api/auth/me'),env)).status,200);}
 assert.equal(checks,2);assert.equal(writes,2);
});

test('activation checks existing configuration and ignores unapproved hostnames',async()=>{
 let checks=0,writes=0;
 const worker=createWorker(async(input,options={})=>{
  const url=String(input);if(url.endsWith('/cdn-cgi/access/certs'))return Response.json({keys:[publicJwk]});
  if(url.endsWith('/overview'))return Response.json({devices:[],claims:[],audit:[]});
  if(options.method==='GET'){checks++;return Response.json({url:'https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev'});}
  writes++;throw new Error('No write expected');
 });
 const other=new Request('https://unapproved.example.workers.dev/api/admin/overview',{headers:{'Cf-Access-Jwt-Assertion':valid}});
 assert.equal((await worker.fetch(other,env)).status,200);assert.equal(checks,0);
 assert.equal((await worker.fetch(request('/api/admin/overview'),env)).status,200);
 assert.equal((await worker.fetch(request('/api/admin/overview'),env)).status,200);assert.equal(checks,1);assert.equal(writes,0);
});
