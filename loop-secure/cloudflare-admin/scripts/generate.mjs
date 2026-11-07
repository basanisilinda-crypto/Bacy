import {access,readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
const generator=new URL('../../tools/build_cloudflare_admin.py',import.meta.url);
let sourceCheckout=true;
try{await access(generator);await access(new URL('../../backend/static/index.html',import.meta.url));}catch{sourceCheckout=false;}
if(sourceCheckout)execFileSync('python',[fileURLToPath(generator)],{stdio:'inherit'});
else{
 await readFile(new URL('../src/assets.js',import.meta.url));
 JSON.parse(await readFile(new URL('../asset-manifest.json',import.meta.url),'utf8'));
 console.log('Using included generated interface assets. Regenerate in the full Loop Secure source checkout to change the UI.');
}
