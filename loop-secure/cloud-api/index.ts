import {zipSync,strToU8} from 'npm:fflate@0.8.2';
import windowsEnroll from './Enroll-Windows.cmd.ts';
import windowsUpdate from './Update-Windows.cmd.ts';
import usbReadme from './READ-ME.txt.ts';
import {createClient} from 'npm:@supabase/supabase-js@2.57.0';
import {z} from 'npm:zod@3.25.76';
import QRCode from 'npm:qrcode@1.5.4';
import {APK,APK_SHA256} from './installer.ts';
import enrollmentDefaults from './enrollment-defaults.json' with {type:'json'};

const projectUrl=Deno.env.get('SUPABASE_URL')!;
const serviceKey=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
const db=createClient(projectUrl,serviceKey,{auth:{persistSession:false,autoRefreshToken:false}});
const baseUrl=projectUrl+'/functions/v1/loop-manager';
const enc=new TextEncoder();
const now=()=>Math.floor(Date.now()/1000);
const b64=(bytes:Uint8Array)=>{let text='';for(const byte of bytes)text+=String.fromCharCode(byte);return btoa(text);};
const unb64=(value:string)=>Uint8Array.from(atob(value),v=>v.charCodeAt(0));
const sha=async(value:string)=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',enc.encode(value)))).map(v=>v.toString(16).padStart(2,'0')).join('');
const random=()=>b64(crypto.getRandomValues(new Uint8Array(32))).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
function fail(status:number,message:string):never{throw Object.assign(new Error(message),{status});}
function check(error:any){if(!error)return;const match=String(error.message).match(/^(\d{3}):(.*)$/s);if(match)fail(+match[1],match[2]);if(error.code==='23505')fail(409,'This reference or request has already been used');if(error.code==='22P02')fail(422,'Invalid record identifier');throw error;}
async function rpc(action:string,data:any={},device:string|null=null,actor='site-owner'){const result=await db.rpc('loop_dispatch',{p_action:action,p_actor:actor,p_device:device,p_data:data});check(result.error);return result.data;}
async function configuration(key:string){const result=await db.from('loop_config').select('value').eq('key',key).maybeSingle();check(result.error);return result.data?.value;}
const defaultAdminSite='https://phone-finance-manager-bc.bacanyclinda.chatgpt.site';
function checkedAdminSite(value:string){
 let url:URL;try{url=new URL(value);}catch{fail(422,'Use the administrator website HTTPS address');}
 if(url.protocol!=='https:'||url.username||url.password||url.port||url.pathname!=='/'||url.search||url.hash||(url.origin!==defaultAdminSite&&!/^[a-z0-9-]+\.[a-z0-9-]+\.workers\.dev$/.test(url.hostname)))fail(422,'Use the current administrator site or a production workers.dev address without a path');
 return url.origin;
}
async function administratorSite(){const stored=await configuration('admin_site_url');return stored?checkedAdminSite(stored.url):defaultAdminSite;}
let aesPromise:Promise<CryptoKey>|null=null;
function aes(){return aesPromise??=(async()=>crypto.subtle.importKey('raw',await crypto.subtle.digest('SHA-256',enc.encode(serviceKey)),{name:'AES-GCM'},false,['encrypt','decrypt']))();}
async function encrypt(value:string){const iv=crypto.getRandomValues(new Uint8Array(12));const ciphertext=new Uint8Array(await crypto.subtle.encrypt({name:'AES-GCM',iv},await aes(),enc.encode(value)));const packed=new Uint8Array(iv.length+ciphertext.length);packed.set(iv);packed.set(ciphertext,iv.length);return b64(packed);}
async function decrypt(value:string){const packed=unb64(value);return new TextDecoder().decode(await crypto.subtle.decrypt({name:'AES-GCM',iv:packed.slice(0,12)},await aes(),packed.slice(12)));}
let cryptoPromise:Promise<any>|null=null;
function cryptoConfiguration(){return cryptoPromise??=(async()=>{
 let config=await configuration('crypto');
 if(!config){const keys=await crypto.subtle.generateKey({name:'Ed25519'},true,['sign','verify']) as CryptoKeyPair;const privateBytes=new Uint8Array(await crypto.subtle.exportKey('pkcs8',keys.privateKey));const publicBytes=new Uint8Array(await crypto.subtle.exportKey('spki',keys.publicKey));const proposed={private_encrypted:await encrypt(b64(privateBytes)),public_key:b64(publicBytes)};const inserted=await db.from('loop_config').insert({key:'crypto',value:proposed});if(inserted.error&&inserted.error.code!=='23505')check(inserted.error);config=await configuration('crypto');}
 const key=await crypto.subtle.importKey('pkcs8',unb64(await decrypt(config.private_encrypted)),{name:'Ed25519'},false,['sign']);
 return {public_key:config.public_key,key};
})();}
async function signed(payload:any){const bytes=enc.encode(JSON.stringify(payload));const keys=await cryptoConfiguration();return {payload:b64(bytes),signature:b64(new Uint8Array(await crypto.subtle.sign({name:'Ed25519'},keys.key,bytes)))};}
function equal(a:string,b:string){if(a.length!==b.length)return false;let different=0;for(let i=0;i<a.length;i++)different|=a.charCodeAt(i)^b.charCodeAt(i);return different===0;}
async function administrator(req:Request){const token=req.headers.get('authorization')?.replace(/^Bearer /,'')||'';const config=await configuration('admin');if(config&&equal(await sha(token),config.token_hash))return 'site-owner';const mobile=token?await configuration('mobile_session:'+await sha(token)):null;if(mobile&&mobile.expires>now()&&!mobile.revoked)return 'mobile-owner';fail(401,'Administrator authentication required');}
async function device(req:Request){const auth=req.headers.get('authorization')||'';if(!auth.startsWith('Bearer '))fail(401,'Device authentication required');const result=await db.from('loop_devices').select('id,token').eq('token',await sha(auth.slice(7))).maybeSingle();check(result.error);if(!result.data)fail(401,'Invalid device credentials');return result.data;}
async function body(req:Request){if(Number(req.headers.get('content-length')||0)>65536)fail(413,'Request too large');const reader=req.body?.getReader();if(!reader)fail(400,'JSON body required');const chunks:Uint8Array[]=[];let size=0;while(true){const {value,done}=await reader.read();if(done)break;size+=value.length;if(size>65536){await reader.cancel();fail(413,'Request too large');}chunks.push(value);}const bytes=new Uint8Array(size);let at=0;for(const chunk of chunks){bytes.set(chunk,at);at+=chunk.length;}try{return JSON.parse(new TextDecoder().decode(bytes));}catch{fail(400,'Invalid JSON');}}
const text=(min:number,max:number)=>z.string().trim().min(min).max(max);
const policy=z.object({manual_lock:z.boolean().default(false),grace_until:z.number().int().min(0).default(0),enforcement_enabled:z.boolean().default(false),block_factory_reset:z.boolean().default(false),block_uninstall:z.boolean().default(false),block_wifi_changes:z.boolean().default(false),banking_packages:z.array(z.string().regex(/^[A-Za-z]\w*(\.[A-Za-z]\w*)+$/)).max(20).default([]),grace_hours:z.number().int().min(0).max(168).default(24),block_notification_center:z.boolean().default(true),block_navigation:z.boolean().default(true),block_outgoing_calls:z.boolean().default(true),update_window_start:z.number().int().min(0).max(1439).default(120),update_window_end:z.number().int().min(0).max(1439).default(240)}).transform(p=>({...p,banking_packages:[...new Set(p.banking_packages)]}));
function checkedPolicy(value:any){const p=policy.parse(value);if(p.enforcement_enabled&&!p.banking_packages.length)fail(422,'Verify and configure banking apps before enabling restrictions');return p;}
const keySchema=text(16,100);
async function currentInstaller(){return await configuration('installer_current')||{available:true,version:'0.3.0',apk_url:baseUrl+'/downloads/client.apk',apk_sha256:APK_SHA256};}
async function activationToken(code:string,nonce:string){const key=await crypto.subtle.importKey('raw',enc.encode(serviceKey),{name:'HMAC',hash:'SHA-256'},false,['sign']);return b64(new Uint8Array(await crypto.subtle.sign('HMAC',key,enc.encode('loop-enrollment:'+code+':'+nonce)))).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');}
const acknowledgment=z.object({revision:z.number().int().min(0),status:z.enum(['unrestricted','restricted','released','unsupported','needs_online_check','policy_error','restricted_banking_unavailable'])});
const identifiers=z.object({imeis:z.array(z.string().regex(/^\d{14,17}$/)).max(4).default([]),sim_operator:text(0,100).default(''),sim_country:text(0,10).default(''),status:z.enum(['available','permission_denied','unsupported']).default('unsupported')});
function json(value:any,status=200){return new Response(JSON.stringify(value),{status,headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store','x-content-type-options':'nosniff'}});}
async function route(req:Request,path:string){
 const method=req.method;
 if(path==='/health'&&method==='GET'){const keys=await cryptoConfiguration();return json({status:'ok',version:'0.5.0',public_key:keys.public_key});}
 if(path==='/downloads/usb-helper.zip'&&method==='GET'){
  const zip=zipSync({'Enroll-Windows.cmd':strToU8(windowsEnroll.replaceAll('\r\n','\n').replaceAll('\n','\r\n')),'READ-ME.txt':strToU8(usbReadme)},{level:6});
  return new Response(zip,{headers:{'content-type':'application/zip','cache-control':'no-store','content-disposition':'attachment; filename="Bacy-USB-helper-update.zip"','x-content-type-options':'nosniff'}});
 }
 if(path==='/downloads/client-update.zip'&&method==='GET'){
  const installer=await currentInstaller();
  if(!installer.apk_url.startsWith(projectUrl+'/storage/v1/object/public/loop-phone-installers/client-'))fail(409,'Managed client update is unavailable');
  const response=await fetch(installer.apk_url,{redirect:'manual'});if(!response.ok)fail(502,'Managed installer download failed');
  const apk=new Uint8Array(await response.arrayBuffer());if(apk.length>30*1024*1024)fail(502,'Unexpected installer size');
  const checksum=b64(new Uint8Array(await crypto.subtle.digest('SHA-256',apk))).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
  if(checksum!==installer.apk_sha256)fail(502,'Managed installer checksum mismatch');
  const readme='BACY CLIENT UPDATE\r\nExtract these files into their own folder. The helper finds Google Platform Tools or asks you to select adb.exe once. Connect the enrolled phone by USB, approve USB debugging, then run Update-Windows.cmd. Keep the phone online afterward. This is an app update: no new customer enrollment or code is needed. Your existing account data is retained.\r\n';
  const zip=zipSync({'Bacy-Client.apk':apk,'Update-Windows.cmd':strToU8(windowsUpdate.replaceAll('\r\n','\n').replaceAll('\n','\r\n')),'READ-ME.txt':strToU8(readme)},{level:0});
  return new Response(zip,{headers:{'content-type':'application/zip','cache-control':'no-store','content-disposition':'attachment; filename="Bacy-Client-Update.zip"','x-content-type-options':'nosniff'}});
 }
 if(path.startsWith('/a/')&&method==='GET'){
  const tag=path.match(/^\/a\/([0-9.]+-[A-Za-z0-9_-]{12})\.apk$/)?.[1];if(!tag)fail(404,'Installer not found');
  const release=await configuration('apk_release:'+tag);if(!release)fail(404,'Installer not found');
  const downloaded=await fetch(release.apk_url,{redirect:'manual'});if(!downloaded.ok)fail(502,'Installer unavailable');
  return new Response(downloaded.body,{headers:{'content-type':'application/vnd.android.package-archive','content-length':String(release.bytes),'cache-control':'public, max-age=31536000, immutable'}});
 }
 if(path==='/downloads/client.apk'&&method==='GET')return new Response(unb64(APK),{headers:{'content-type':'application/vnd.android.package-archive','content-disposition':'attachment; filename="PhoneFinanceManager.apk"','cache-control':'public, max-age=300','x-content-type-options':'nosniff'}});
 if(path==='/api/device/enroll'&&method==='POST'){
  const data=z.object({enrollment_token:text(20,100),model:text(1,150),consent:z.literal(true),consent_source:z.enum(['on_device','administrator_recorded']).default('on_device'),exchange_nonce:text(16,100).optional(),identifiers:identifiers.default({})}).parse(await body(req));
  const token=data.exchange_nonce?await activationToken(data.enrollment_token,data.exchange_nonce):random();const retry=data.exchange_nonce?{request_key:'enrollment:'+await sha(data.enrollment_token+':'+data.exchange_nonce)}:{};const result=await rpc('enroll_exchange',{...retry,enrollment_hash:await sha(data.enrollment_token),token_hash:await sha(token),model:data.model,consent_source:data.consent_source,identifiers:await encrypt(JSON.stringify(data.identifiers))},null,'device');
  return json({...result,device_token:token,public_key:(await cryptoConfiguration()).public_key});
 }
 if(path==='/api/admin-app/connect'&&method==='POST'){
  const data=z.object({name:text(1,60)}).parse(await body(req));
  const ticket=crypto.randomUUID(),secret=random(),session=random(),expires=now()+600;
  const cleanup=await db.from('loop_config').delete().like('key','mobile_pair:%').lt('value->>expires',String(now()));check(cleanup.error);
  const count=await db.from('loop_config').select('key',{count:'exact',head:true}).like('key','mobile_pair:%');check(count.error);if((count.count||0)>500)fail(429,'Connection requests are busy. Try again later.');
  const stored=await db.from('loop_config').insert({key:'mobile_pair:'+ticket,value:{secret_hash:await sha(secret),session_hash:await sha(session),session_encrypted:await encrypt(session),name:data.name,expires,approved:false}});check(stored.error);
  return json({ticket,device_secret:secret,expires,approval_url:(await administratorSite())+'/?connect_admin='+ticket});
 }
 if(path==='/api/admin-app/exchange'&&method==='POST'){
  const data=z.object({ticket:z.string().uuid(),device_secret:text(20,100)}).parse(await body(req));const pairing=await configuration('mobile_pair:'+data.ticket);
  if(!pairing||pairing.expires<=now()||!equal(pairing.secret_hash,await sha(data.device_secret)))fail(401,'App connection expired. Start again.');
  if(!pairing.approved)return json({status:'pending'});
  const session=await configuration('mobile_session:'+pairing.session_hash);if(!session||session.revoked||session.expires<=now())fail(401,'App session is no longer active');
  return json({status:'connected',access_token:await decrypt(pairing.session_encrypted),expires:session.expires});
 }
 if(path.startsWith('/api/device/')){
  const dev=await device(req);
  if(path==='/api/device/sync'&&method==='POST')return json(await signed(await rpc('sync',acknowledgment.parse(await body(req)),dev.id,'device')));
  if(path==='/api/device/claims'&&method==='POST'){const data=z.object({request_key:keySchema,bank_reference:text(3,100)}).parse(await body(req));return json(await rpc('claim',data,dev.id,'device'));}
  if(path==='/api/device/support'&&method==='POST')return json(await rpc('support',z.object({message:text(5,2000)}).parse(await body(req)),dev.id,'device'));
  if(path==='/api/device/messages/receipt'&&method==='POST')return json(await rpc('receipt',z.object({ids:z.array(z.number().int().positive()).min(1).max(100),state:z.enum(['delivered','read'])}).parse(await body(req)),dev.id,'device'));
  if(path==='/api/device/events'&&method==='POST'){
   const data=z.object({cursor:text(0,100).default(''),wait_seconds:z.number().int().min(0).max(20).default(10)}).parse(await body(req));
   const end=Date.now()+data.wait_seconds*1000;let event;
   do{event=await rpc('events',{},dev.id,'device');if(event.cursor!==data.cursor||Date.now()>=end)break;await new Promise(resolve=>setTimeout(resolve,1000));}while(!req.signal.aborted);
   return json({...event,changed:event.cursor!==data.cursor});
  }
  fail(404,'Unknown device endpoint');
 }
 if(path.startsWith('/api/admin/')){
  const actor=await administrator(req);
  if(path==='/api/admin/site'){
   if(actor!=='site-owner')fail(403,'Only the website owner can change its hosting address');
   if(method==='GET')return json({url:await administratorSite()});
   if(method==='PUT'){
    const data=z.object({url:text(10,250)}).parse(await body(req));const url=checkedAdminSite(data.url);
    const stored=await db.from('loop_config').upsert({key:'admin_site_url',value:{url}});check(stored.error);
    return json({url});
   }
  }
  if(path==='/api/admin/mobile/pending'&&method==='GET'){
   const result=await db.from('loop_config').select('key,value').like('key','mobile_pair:%');check(result.error);
   return json({requests:(result.data||[]).filter(row=>!row.value.approved&&row.value.expires>now()).map(row=>({ticket:row.key.slice('mobile_pair:'.length),name:row.value.name,expires:row.value.expires,code:row.key.slice('mobile_pair:'.length,'mobile_pair:'.length+8).toUpperCase()}))});
  }
  if(path==='/api/admin/mobile/installer'&&method==='GET')return json(await configuration('admin_installer_current')||{available:false});
  if(path==='/api/admin/mobile/approve'&&method==='POST'){
   const data=z.object({ticket:z.string().uuid()}).parse(await body(req));const pairing=await configuration('mobile_pair:'+data.ticket);if(!pairing||pairing.expires<=now())fail(410,'Connection request expired');
   if(!pairing.approved){const stored=await db.from('loop_config').upsert({key:'mobile_session:'+pairing.session_hash,value:{name:pairing.name,expires:now()+7*86400,revoked:false}});check(stored.error);const updated=await db.from('loop_config').update({value:{...pairing,approved:true}}).eq('key','mobile_pair:'+data.ticket);check(updated.error);}
   return json({status:'approved'});
  }
  if(path==='/api/admin/mobile/disconnect'&&method==='POST'){
   const token=req.headers.get('authorization')?.replace(/^Bearer /,'')||'';const removed=await db.from('loop_config').delete().eq('key','mobile_session:'+await sha(token));check(removed.error);return json({ok:true});
  }
  if(path==='/api/admin/mobile/revoke-all'&&method==='POST'){const removed=await db.from('loop_config').delete().like('key','mobile_session:%');check(removed.error);return json({ok:true});}
  if((path==='/api/admin/installer'||path==='/api/admin/preview-installer'||path==='/api/admin/admin-installer')&&method==='PUT'){
   const isPreview=path==='/api/admin/preview-installer',isAdmin=path==='/api/admin/admin-installer';
   const version=req.headers.get('x-app-version')||'';if(!/^\d+\.\d+\.\d+$/.test(version))fail(422,'A release version is required');
   const max=30*1024*1024;if(Number(req.headers.get('content-length')||0)>max)fail(413,'Installer too large');
   const reader=req.body?.getReader();if(!reader)fail(400,'Installer bytes required');const chunks:Uint8Array[]=[];let size=0;
   while(true){const {value,done}=await reader.read();if(done)break;size+=value.length;if(size>max){await reader.cancel();fail(413,'Installer too large');}chunks.push(value);}
   const bytes=new Uint8Array(size);let at=0;for(const chunk of chunks){bytes.set(chunk,at);at+=chunk.length;}
   if(bytes.length<1024||bytes[0]!==80||bytes[1]!==75)fail(422,'An Android APK archive is required');
   const digest=new Uint8Array(await crypto.subtle.digest('SHA-256',bytes));const checksum=b64(digest).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
   if(req.headers.get('x-apk-sha256')!==checksum)fail(422,'Installer checksum mismatch');
   const hex=Array.from(digest).map(v=>v.toString(16).padStart(2,'0')).join('');const bucket='loop-phone-installers';
   const existing=await db.storage.getBucket(bucket);
   if(existing.error){const created=await db.storage.createBucket(bucket,{public:true,fileSizeLimit:max,allowedMimeTypes:['application/vnd.android.package-archive']});if(created.error&&!String(created.error.message).toLowerCase().includes('already exists'))check(created.error);}
   const object=(isAdmin?'admin-':isPreview?'preview-':'client-')+version+'-'+hex+'.apk';
   const uploaded=await db.storage.from(bucket).upload(object,bytes,{contentType:'application/vnd.android.package-archive',cacheControl:'31536000',upsert:false});
   if(uploaded.error&&!String(uploaded.error.message).toLowerCase().includes('already exists'))check(uploaded.error);
   const apkUrl=db.storage.from(bucket).getPublicUrl(object).data.publicUrl;
   const tag=version+'-'+checksum.slice(0,12);const metadata={available:true,version,apk_url:apkUrl,apk_sha256:checksum,bytes:size,...(!isPreview&&!isAdmin?{download_url:baseUrl+'/a/'+tag+'.apk'}:{})};
   if(!isPreview&&!isAdmin){const release=await db.from('loop_config').upsert({key:'apk_release:'+tag,value:metadata});check(release.error);}
   const stored=await db.from('loop_config').upsert({key:isAdmin?'admin_installer_current':isPreview?'preview_installer_current':'installer_current',value:metadata});check(stored.error);
   return json(metadata);
  }
  if(path==='/api/admin/provisioning/usb-bundle'&&method==='POST'){
   const data=z.object({enrollment_token:text(20,100)}).parse(await body(req));
   await rpc('qr_check',{enrollment_hash:await sha(data.enrollment_token)},null,actor);
   const installer=await currentInstaller();
   if(!installer.apk_url.startsWith(projectUrl+'/storage/v1/object/public/loop-phone-installers/client-'))fail(409,'Publish the USB-capable managed client before downloading USB enrollment');
   const response=await fetch(installer.apk_url,{redirect:'manual'});if(!response.ok)fail(502,'Managed installer download failed');
   const apk=new Uint8Array(await response.arrayBuffer());if(apk.length>30*1024*1024)fail(502,'Unexpected installer size');
   const checksum=b64(new Uint8Array(await crypto.subtle.digest('SHA-256',apk))).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
   if(checksum!==installer.apk_sha256)fail(502,'Managed installer checksum mismatch');
   const zip=zipSync({'Bacy-Client.apk':apk,'Enroll-Windows.cmd':strToU8(windowsEnroll.replaceAll('\r\n','\n').replaceAll('\n','\r\n')),'enrollment-code.txt':strToU8(data.enrollment_token+'\r\n'),'READ-ME.txt':strToU8(usbReadme)},{level:0});
   return new Response(zip,{headers:{'content-type':'application/zip','cache-control':'no-store','content-disposition':'attachment; filename="Bacy-Windows-Enrollment.zip"'}});
  }
  if(path==='/api/admin/overview'&&method==='GET')return json(await rpc('overview',{},null,actor));
  if(path==='/api/admin/enrollments'&&method==='POST'){
   const data=z.object({name:text(3,150),identity:text(0,40).default(''),phone:text(8,25),consent:z.literal(true).default(true),identity_verified:z.boolean().default(false),verification_note:text(0,1000).default('Identity not collected or verified'),policy:z.unknown().optional()}).parse(await body(req));
   if(data.identity_verified&&(data.identity.length<3||data.verification_note.length<10))fail(422,'Evidence is required to mark identity verified');
   const token=random();const p=checkedPolicy(data.policy??enrollmentDefaults);const reference='LOOP-'+random().slice(0,10).toUpperCase();
   const result=await rpc('enroll_create',{...data,identity:await encrypt(data.identity),policy:p,reference,enrollment_hash:await sha(token)},null,actor);
   return json({...result,enrollment_token:token,expires_in:1800,server_url:baseUrl,public_key:(await cryptoConfiguration()).public_key});
  }
  if(path==='/api/admin/provisioning/apk'&&method==='GET'){const png=await QRCode.toDataURL(baseUrl+'/health',{type:'image/png',margin:6,scale:8});return json({...await currentInstaller(),qr_renderer_ready:png.startsWith('data:image/png;base64,')});}
  if(path==='/api/admin/provisioning/qr'&&method==='POST'){
   const data=z.object({enrollment_token:text(20,100),apk_url:text(10,1000),apk_sha256:z.string(),format:z.enum(['svg','png']).default('svg')}).parse(await body(req));
   const installer=await currentInstaller();
   if(data.apk_url!==installer.apk_url||data.apk_sha256.replaceAll('=','')!==installer.apk_sha256)fail(422,'Use the verified hosted client APK');
   await rpc('qr_check',{enrollment_hash:await sha(data.enrollment_token)},null,actor);
   const version=String(installer.version).split('.').map(Number);const compact=version[0]>0||version[1]>9||(version[1]===9&&version[2]>=2);
   const extras=compact?{enrollment_token:data.enrollment_token}:{server_url:baseUrl,enrollment_token:data.enrollment_token,public_key:(await cryptoConfiguration()).public_key};
   const payload={'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME':'za.co.loopmanager/.LoopAdminReceiver','android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION':installer.download_url||data.apk_url,'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM':installer.apk_sha256,'android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED':true,'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE':extras};
   if(data.format==='svg'){const svg=await QRCode.toString(JSON.stringify(payload),{type:'svg',errorCorrectionLevel:'M',margin:6,width:900});return new Response(svg,{headers:{'content-type':'image/svg+xml','cache-control':'no-store','x-content-type-options':'nosniff'}});}
   const png=await QRCode.toDataURL(JSON.stringify(payload),{type:'image/png',errorCorrectionLevel:'M',margin:6,scale:10});
   return new Response(unb64(png.split(',')[1]),{headers:{'content-type':'image/png','cache-control':'no-store','x-content-type-options':'nosniff'}});
  }
  if(path==='/api/admin/messages'&&method==='POST'){
   const data=z.object({request_key:keySchema,title:text(3,100),message:text(3,2000),device_ids:z.array(z.string().uuid()).max(500).default([]),all_active:z.boolean().default(false)}).parse(await body(req));
   if(data.all_active&&data.device_ids.length||!data.all_active&&!data.device_ids.length)fail(422,'Select individual recipients or all active phones');
   return json(await rpc('message',data,null,actor));
  }
  if(path==='/api/admin/fleet/policy'&&method==='POST'){
   const data=z.object({request_key:keySchema,policy:z.unknown(),apply_to_all_active:z.literal(true)}).parse(await body(req));
   return json(await rpc('fleet',{...data,policy:checkedPolicy(data.policy)},null,actor));
  }
  if(path==='/api/admin/banking'&&method==='GET')return json(await rpc('banking_get',{},null,actor));
  if(path==='/api/admin/banking'&&method==='PUT'){
   const data=z.object({bank:text(2,100),account_holder:text(2,150),account_number:z.string().regex(/^\d{6,20}$/),branch_code:z.string().regex(/^\d{6}$/),details_verified:z.literal(true),verification_note:text(10,1000)}).parse(await body(req));return json(await rpc('banking_put',data,null,actor));
  }
  const enrollmentMatch=path.match(/^\/api\/admin\/devices\/([^/]+)\/enrollment$/);
  if(enrollmentMatch&&method==='POST'){
   const token=random();const result=await rpc('enroll_reissue',{id:z.string().uuid().parse(enrollmentMatch[1]),enrollment_hash:await sha(token),policy:checkedPolicy(enrollmentDefaults)},null,actor);
   return json({...result,enrollment_token:token,expires_in:1800,server_url:baseUrl,public_key:(await cryptoConfiguration()).public_key});
  }
  let match=path.match(/^\/api\/admin\/claims\/([^/]+)\/review$/);
  if(match&&method==='POST')return json(await rpc('review',{...z.object({decision:z.enum(['approve','reject']),reason:text(5,1000),receipt_reference:text(0,100).default('')}).parse(await body(req)),id:z.string().uuid().parse(match[1])},null,actor));
  match=path.match(/^\/api\/admin\/devices\/([^/]+)\/control$/);
  if(match&&method==='POST')return json(await rpc('control',{...z.object({action:z.enum(['lock','unlock','grace']),request_key:keySchema}).parse(await body(req)),id:z.string().uuid().parse(match[1]),banking_packages:enrollmentDefaults.banking_packages},null,actor));
  match=path.match(/^\/api\/admin\/devices\/([^/]+)\/policy$/);
  if(match&&method==='PUT')return json(await rpc('policy',{id:z.string().uuid().parse(match[1]),policy:checkedPolicy(await body(req))},null,actor));
  match=path.match(/^\/api\/admin\/devices\/([^/]+)\/release$/);
  if(match&&method==='POST')return json(await rpc('release',{...z.object({reason:text(10,1000),full_payoff_confirmed:z.literal(true)}).parse(await body(req)),id:z.string().uuid().parse(match[1])},null,actor));
  match=path.match(/^\/api\/admin\/devices\/([^/]+)\/identifiers$/);
  if(match&&method==='GET'){const result=await rpc('identifiers',{id:z.string().uuid().parse(match[1])},null,actor);return json(result.encrypted?JSON.parse(await decrypt(result.encrypted)):{status:'unsupported',imeis:[]});}
  match=path.match(/^\/api\/admin\/support\/([^/]+)\/resolve$/);
  if(match&&method==='POST')return json(await rpc('support_resolve',{id:z.string().uuid().parse(match[1])},null,actor));
  fail(404,'Unknown administrator endpoint');
 }
 fail(404,'Endpoint not found');
}
Deno.serve(async(req)=>{
 try{
  const path=new URL(req.url).pathname.replace(/^\/(?:functions\/v1\/)?loop-manager/,'')||'/';
  // Browser writes go through the private administrator Site. No public CORS grant.
  if(req.headers.get('origin'))return json({detail:'Use the private administrator dashboard'},403);
  return await route(req,path);
 }catch(error:any){if(error instanceof z.ZodError)return json({detail:'Check all required fields and their formats'},422);if(error.status)return json({detail:error.message},error.status);console.error('Loop API operation failed',String(error.code||error.name||'error'));return json({detail:'Cloud service could not complete this request'},500);}
});
