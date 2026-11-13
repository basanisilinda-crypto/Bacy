import {createRemoteJWKSet, customFetch, jwtVerify} from 'jose';
import {HTML, JS, CSS, FONTS, BRAND, FONT_LICENSES} from './assets.js';

const API_URL='https://ourxezawbfclmhbggskv.supabase.co/functions/v1/loop-manager';
const ACTIVATION_ORIGIN='https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev';
const CSP="default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'";
function response(body,status=200,type='application/json; charset=utf-8',extra={}){
 return new Response(body,{status,headers:{'content-type':type,'cache-control':'no-store','x-content-type-options':'nosniff','referrer-policy':'no-referrer','content-security-policy':CSP,...extra}});
}
const json=(value,status=200)=>response(JSON.stringify(value),status);
// Decode public images/fonts once at startup, outside the Free plan's per-request CPU budget.
const binaryAssets=new Map();
for(const [prefix,assets,type]of [['/static/fonts/',FONTS,'font/woff2'],['/static/brand/',BRAND,'image/png']]){
 for(const [name,value]of Object.entries(assets)){
  const raw=atob(value),bytes=new Uint8Array(raw.length);
  for(let i=0;i<raw.length;i++)bytes[i]=raw.charCodeAt(i);
  binaryAssets.set(prefix+name,{bytes,type});
 }
}
function configured(env){
 return typeof env.CF_ACCESS_TEAM_DOMAIN==='string'&&/^[a-z0-9][a-z0-9-]*\.cloudflareaccess\.com$/.test(env.CF_ACCESS_TEAM_DOMAIN)&&
 typeof env.CF_ACCESS_AUD==='string'&&/^[A-Za-z0-9_-]{16,256}$/.test(env.CF_ACCESS_AUD)&&
 typeof env.LOOP_ADMIN_EMAIL==='string'&&/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(env.LOOP_ADMIN_EMAIL)&&
 typeof env.LOOP_ADMIN_TOKEN==='string'&&env.LOOP_ADMIN_TOKEN.length>=20&&env.LOOP_API_URL===API_URL;
}
/** Factory permits transport fixtures; production always uses the platform HTTPS implementation. */
export function createWorker(outbound=globalThis.fetch.bind(globalThis)){
 const keysets=new Map();
 const activated=new Set(),activating=new Map();
 function activateWebsite(origin,env){
  if(origin!==ACTIVATION_ORIGIN||activated.has(origin))return Promise.resolve();
  if(activating.has(origin))return activating.get(origin);
  const operation=(async()=>{
   const controller=new AbortController();
   const timer=setTimeout(()=>controller.abort(),4000);
   try{
    const headers={Authorization:'Bearer '+env.LOOP_ADMIN_TOKEN};
    const current=await outbound(API_URL+'/api/admin/site',{method:'GET',headers,redirect:'manual',signal:controller.signal});
    if(!current.ok)return;
    const configuration=await current.json();
    if(typeof configuration.url!=='string')return;
    if(configuration.url===origin){activated.add(origin);return;}
    const saved=await outbound(API_URL+'/api/admin/site',{method:'PUT',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify({url:origin}),redirect:'manual',signal:controller.signal});
    if(saved.ok&&(await saved.json()).url===origin)activated.add(origin);
   }catch{/* Activation failure must not interrupt the authenticated customer register. */}
   finally{clearTimeout(timer);activating.delete(origin);}
  })();
  activating.set(origin,operation);
  return operation;
 }
 async function identity(request,env){
  const token=request.headers.get('Cf-Access-Jwt-Assertion');
  if(!token||token.length>16384)throw new Error('No Access token');
  const issuer='https://'+env.CF_ACCESS_TEAM_DOMAIN;
  if(!keysets.has(issuer))keysets.set(issuer,createRemoteJWKSet(new URL(issuer+'/cdn-cgi/access/certs'),{[customFetch]:outbound,timeoutDuration:5000,cooldownDuration:30000,cacheMaxAge:300000}));
  const {payload}=await jwtVerify(token,keysets.get(issuer),{issuer,audience:env.CF_ACCESS_AUD,algorithms:['RS256'],requiredClaims:['exp','iat','sub','email'],clockTolerance:5});
  if(typeof payload.sub!=='string'||!payload.sub.trim()||typeof payload.email!=='string'||payload.email.toLowerCase()!==env.LOOP_ADMIN_EMAIL.toLowerCase())throw new Error('Owner identity required');
  return{id:payload.sub,email:payload.email,role:'owner'};
 }
 return{async fetch(request,env,ctx){
  const url=new URL(request.url);
  if(!configured(env))return json({detail:'Cloudflare Access and backend connection must be configured before this workspace can open.'},503);
  let owner;try{owner=await identity(request,env);}catch{return json({detail:'Sign in to the authorized administrator account through Cloudflare Access.'},401);}
  if(!['GET','HEAD','POST','PUT','DELETE','PATCH'].includes(request.method))return json({detail:'Method not allowed'},405);
  if(!['GET','HEAD'].includes(request.method)&&(request.headers.get('origin')!==url.origin||request.headers.get('sec-fetch-site')==='cross-site'))return json({detail:'Same-origin browser request required'},403);
  if(url.pathname==='/api/auth/me'&&request.method==='GET')return json(owner);
  if(url.pathname==='/api/auth/logout'&&request.method==='POST')return json({ok:true,signout:'/cdn-cgi/access/logout'});
  if(url.pathname==='/api/auth/login')return json({detail:'Use Cloudflare Access sign-in.'},405);
  if(url.pathname==='/health'&&request.method==='GET')return json({status:'ok',authentication:'cloudflare_access',backend:'configured'});
  if(url.pathname.startsWith('/api/admin/')){
   try{
    const headers={Authorization:'Bearer '+env.LOOP_ADMIN_TOKEN};
    if(request.headers.has('content-type'))headers['Content-Type']=request.headers.get('content-type');
    const upstream=await outbound((url.pathname==='/api/admin/orders'?API_URL.replace('loop-manager','loop-order-history'):API_URL)+url.pathname+url.search,{method:request.method,headers,body:['GET','HEAD'].includes(request.method)?undefined:request.body,redirect:'manual'});
    if(upstream.status>=300&&upstream.status<400)return json({detail:'The cloud service returned an unexpected redirect. Your administrator sign-in remains active.'},502);
    if(upstream.status===401||upstream.status===403)return json({detail:'The cloud service connection needs administrator attention. Your sign-in remains active.'},502);
    if(request.method==='GET'&&url.pathname==='/api/admin/overview'&&upstream.status===200&&(upstream.headers.get('content-type')||'').includes('application/json')){
     const activation=activateWebsite(url.origin,env);
     if(ctx?.waitUntil)ctx.waitUntil(activation);else await activation;
    }
    return response(request.method==='HEAD'?null:upstream.body,upstream.status,upstream.headers.get('content-type')||'application/json');
   }catch{return json({detail:'The cloud service could not be reached. Refresh to retry; your sign-in remains active.'},502);}
  }
  if(!['GET','HEAD'].includes(request.method))return json({detail:'Method not allowed'},405);
  if(url.pathname==='/')return response(request.method==='HEAD'?null:HTML,200,'text/html; charset=utf-8');
  if(url.pathname==='/static/app.js')return response(request.method==='HEAD'?null:JS,200,'text/javascript; charset=utf-8');
  if(url.pathname==='/static/styles.css')return response(request.method==='HEAD'?null:CSS,200,'text/css; charset=utf-8');
  if(url.pathname==='/static/fonts/LICENSES.txt')return response(request.method==='HEAD'?null:FONT_LICENSES,200,'text/plain; charset=utf-8');
  const asset=binaryAssets.get(url.pathname);
  if(asset)return response(request.method==='HEAD'?null:asset.bytes,200,asset.type);
  return json({detail:'Not found'},404);
 }};
}
export default createWorker();
