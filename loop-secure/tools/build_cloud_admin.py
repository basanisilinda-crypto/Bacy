from pathlib import Path
import json,base64
root=Path(__file__).resolve().parents[1]
import os
site=Path(os.environ.get('LOOP_SITE_CHECKOUT',str(root/'cloud-admin')))
for folder in ['worker','scripts','.openai']: (site/folder).mkdir(parents=True,exist_ok=True)
html=(root/'backend/static/index.html').read_text()
# Hosted administrator authentication is supplied by the private Sites boundary.
js=(root/'backend/static/app.js').read_text()
js+='''\n// Hosted owner-only sign-in; passwords and device tokens are never stored in this browser.
function showLogin(){account=null;$('#workspace').hidden=true;$('#login').hidden=false;$('#login-form').innerHTML='<h2>Sign in to your workspace.</h2><p>Use the ChatGPT account that owns this site.</p><a href="/signin-with-chatgpt" target="_top">Sign in with ChatGPT</a>';document.querySelectorAll('dialog[open]').forEach(d=>d.close());}
\n$('#logout').onclick=()=>{window.top.location.href='/signout-with-chatgpt';};\n'''
css=(root/'backend/static/styles.css').read_text()
fonts={p.name:base64.b64encode(p.read_bytes()).decode() for p in (root/'backend/static/fonts').glob('*.woff2')}
brand={p.name:base64.b64encode(p.read_bytes()).decode() for p in (root/'backend/static/brand').glob('*.png')}
font_licenses='\n\n'.join(p.read_text() for p in sorted((root/'backend/static/fonts').glob('*-OFL.txt')))
(site/'FONT-LICENSES.txt').write_text(font_licenses)
script='''const HTML=__HTML__;\nconst JS=__JS__;\nconst CSS=__CSS__;
const FONTS=__FONTS__;
const BRAND=__BRAND__;
const FONT_LICENSES=__FONT_LICENSES__;
function response(body,status=200,type='text/html; charset=utf-8'){
 return new Response(body,{status,headers:{'content-type':type,'cache-control':'no-store','x-content-type-options':'nosniff','referrer-policy':'no-referrer','content-security-policy':"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; object-src 'none'; base-uri 'none'; frame-ancestors 'self' https://chatgpt.com; form-action 'self'"}});
}
function safeFailure(error,env){
 let detail=String(error?.message||'Request failed');
 for(const secret of [env.LOOP_ADMIN_TOKEN,env.LOOP_ADMIN_EMAIL])if(secret)detail=detail.split(String(secret)).join('[redacted]');
 console.error('loop_backend_connection_failed',JSON.stringify({name:error?.name||'Error',detail:detail.slice(0,350)}));
}
const externalFetch=globalThis.fetch.bind(globalThis);
export default {async fetch(request,env){
 const url=new URL(request.url);
 if(url.pathname==='/health'){
  if(!env.LOOP_API_URL||!env.LOOP_ADMIN_TOKEN)return response(JSON.stringify({status:'degraded',backend:'not_configured'}),503,'application/json');
  try{
   const check=await externalFetch(env.LOOP_API_URL+'/api/admin/overview',{headers:{Authorization:'Bearer '+env.LOOP_ADMIN_TOKEN},redirect:'manual'});
   await check.arrayBuffer();
   return response(JSON.stringify({status:check.ok?'ok':'degraded',backend_status:check.status}),check.ok?200:503,'application/json');
  }catch(e){safeFailure(e,env);return response(JSON.stringify({status:'degraded',backend:'unreachable'}),503,'application/json');}
 }
 if(url.pathname==='/static/fonts/LICENSES.txt')return response(FONT_LICENSES,200,'text/plain; charset=utf-8');
 if(url.pathname.startsWith('/static/fonts/')){
  const font=FONTS[url.pathname.slice('/static/fonts/'.length)];
  if(!font)return response('Not found',404,'text/plain');
  return new Response(Uint8Array.from(atob(font),c=>c.charCodeAt(0)),{headers:{'content-type':'font/woff2','cache-control':'public, max-age=31536000, immutable','x-content-type-options':'nosniff'}});
 }
 if(url.pathname.startsWith('/static/brand/')){
  const logo=BRAND[url.pathname.slice('/static/brand/'.length)];
  if(!logo)return response('Not found',404,'text/plain');
  return new Response(Uint8Array.from(atob(logo),c=>c.charCodeAt(0)),{headers:{'content-type':'image/png','cache-control':'no-cache','x-content-type-options':'nosniff'}});
 }
 if(url.pathname==='/static/app.js')return response(JS,200,'text/javascript; charset=utf-8');
 if(url.pathname==='/static/styles.css')return response(CSS,200,'text/css; charset=utf-8');
 if(url.pathname==='/')return response(HTML);
 if(!url.pathname.startsWith('/api/'))return response('Not found',404,'text/plain');
 const id=request.headers.get('oai-authenticated-user-id');
 const email=request.headers.get('oai-authenticated-user-email')||'';
 if(!id||!env.LOOP_ADMIN_EMAIL||email.toLowerCase()!==env.LOOP_ADMIN_EMAIL.toLowerCase())return response(JSON.stringify({detail:'Sign in to the owner account for this private administrator workspace'}),401,'application/json');
 if(!['GET','HEAD','OPTIONS'].includes(request.method)&&request.headers.get('origin')&&request.headers.get('origin')!==url.origin)return response(JSON.stringify({detail:'Cross-origin writes are not permitted'}),403,'application/json');
 if(url.pathname==='/api/auth/me')return response(JSON.stringify({id,email,role:'owner'}),200,'application/json');
 if(url.pathname==='/api/auth/logout')return response(JSON.stringify({ok:true}),200,'application/json');
 if(!url.pathname.startsWith('/api/admin/'))return response(JSON.stringify({detail:'Administrator route required'}),404,'application/json');
 if(!env.LOOP_API_URL||!env.LOOP_ADMIN_TOKEN)return response(JSON.stringify({detail:'Cloud connection is not configured'}),503,'application/json');
 try{
  const headers={'Authorization':'Bearer '+env.LOOP_ADMIN_TOKEN};
  if(request.headers.has('content-type'))headers['Content-Type']=request.headers.get('content-type');
  const upstream=await externalFetch(env.LOOP_API_URL+url.pathname+url.search,{method:request.method,headers,body:['GET','HEAD'].includes(request.method)?undefined:request.body,redirect:'manual'});
  if(upstream.status>=300&&upstream.status<400)return response(JSON.stringify({detail:'Unexpected redirect from the cloud service. Your sign-in remains active.'}),502,'application/json');
  return response(upstream.body,upstream.status,upstream.headers.get('content-type')||'application/json');
 }catch(e){safeFailure(e,env);return response(JSON.stringify({detail:'The cloud service could not be reached; use Refresh to retry. Your sign-in remains active.'}),502,'application/json');}
}};
'''
script=script.replace('__HTML__',json.dumps(html)).replace('__JS__',json.dumps(js)).replace('__CSS__',json.dumps(css)).replace('__FONTS__',json.dumps(fonts)).replace('__BRAND__',json.dumps(brand)).replace('__FONT_LICENSES__',json.dumps(font_licenses))
(site/'worker/index.js').write_text(script)
(site/'package.json').write_text('{"name":"phone-finance-manager-admin","private":true,"type":"module"}\n')
(site/'scripts/build.sh').write_text('''#!/usr/bin/env bash
set -euo pipefail
project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
dist_root="$project_root/dist"
rm -rf "$dist_root"
mkdir -p "$dist_root/server" "$dist_root/.openai"
cp "$project_root/worker/index.js" "$dist_root/server/index.js"
cp "$project_root/.openai/hosting.json" "$dist_root/.openai/hosting.json"
''')
(site/'.gitignore').write_text('dist/\n*.tar.gz\n')
(site/'README.md').write_text('Private owner-only Phone Finance Manager administrator site. The platform supplies authenticated identity; every API route checks the owner email. Runtime backend credentials are stored in Sites secrets and excluded from source. Customer device APIs and the signed APK are served by the separately authenticated cloud function.\n')
print('Built owner-only administrator Worker from the existing tested interface.')
