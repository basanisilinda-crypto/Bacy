"""Generate portable admin assets; reads only public application source/assets, never credentials."""
from pathlib import Path
import base64
import hashlib
import json
import re

root=Path(__file__).resolve().parents[1]
site=root/'cloudflare-admin'
static=root/'backend/static'
html=(static/'index.html').read_text()
form='''<form id="login-form" class="login-form"><h2>Administrator sign-in</h2><p>Use your authorized account through Cloudflare Access.</p><a id="cloudflare-signin" href="/cdn-cgi/access/login" target="_top">Sign in to Loop Secure</a><p id="login-error" role="alert"></p><small>Only the configured business owner can open this workspace.</small></form>'''
html,count=re.subn(r'<form id="login-form"[\s\S]*?</form>',lambda _:form,html,count=1)
assert count==1
html=html.replace('Staff sign-in with an authenticator code','Protected administrator sign-in')
js=(static/'app.js').read_text()
login="function showLogin(){$('#login').hidden=false;$('#workspace').hidden=true;account=null;const link=$('#cloudflare-signin');const signin=new URL('/cdn-cgi/access/login',location.origin);signin.searchParams.set('redirect_url',location.href);link.href=signin.href;document.querySelectorAll('dialog[open]').forEach(d=>d.close());}"
js,count=re.subn(r'^function showLogin\(\).*$',lambda _:login,js,count=1,flags=re.M);assert count==1
submit="$('#login-form').addEventListener('submit',e=>{e.preventDefault();showLogin();window.top.location.href=$('#cloudflare-signin').href;});"
js,count=re.subn(r"^\$\('#login-form'\)\.addEventListener.*$",lambda _:submit,js,count=1,flags=re.M);assert count==1
logout="$('#logout').onclick=()=>{window.top.location.href='/cdn-cgi/access/logout';};"
js,count=re.subn(r"^\$\('#logout'\)\.onclick=.*$",lambda _:logout,js,count=1,flags=re.M);assert count==1
values={'HTML':html,'JS':js,'CSS':(static/'styles.css').read_text(),'FONTS':{p.name:base64.b64encode(p.read_bytes()).decode() for p in sorted((static/'fonts').glob('*.woff2'))},'BRAND':{p.name:base64.b64encode(p.read_bytes()).decode() for p in sorted((static/'brand').glob('*.png'))},'FONT_LICENSES':'\n\n'.join(p.read_text() for p in sorted((static/'fonts').glob('*-OFL.txt')))}
(site/'src').mkdir(parents=True,exist_ok=True)
(site/'src/assets.js').write_text('\n'.join('export const '+name+'='+json.dumps(value)+';' for name,value in values.items())+'\n')
(site/'FONT-LICENSES.txt').write_text(values['FONT_LICENSES'])
manifest={str(p.relative_to(static)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(static.rglob('*')) if p.is_file() and p.suffix in ('.png','.woff2')}
(site/'asset-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Generated Cloudflare admin assets from existing interface; private runtime configuration is separate.')
