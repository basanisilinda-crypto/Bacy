import {ikRequest,isPaidForIntent,trustedCheckoutUrl,verifyIkCallback} from './ikpay.ts';
export const PAYMENT_SITE='https://loop-secure-pay.loopsecure-bacanyclinda.workers.dev';
export class IkPayGateway {
 db:any;read:(key:string)=>Promise<any>;encrypt:(s:string)=>Promise<string>;decrypt:(s:string)=>Promise<string>;base:string;
 constructor(db:any,read:(key:string)=>Promise<any>,encrypt:(s:string)=>Promise<string>,decrypt:(s:string)=>Promise<string>,base:string){this.db=db;this.read=read;this.encrypt=encrypt;this.decrypt=decrypt;this.base=base;}
 private failure(status:number,message:string):never{throw Object.assign(new Error(message),{status});}
 private checked(error:any){if(error)throw new Error('Payment storage operation could not complete');}
 private async config(){const c=await this.read('ikpay-provider');if(!c?.enabled||!c.secret_encrypted)this.failure(503,'Online payment setup is not complete. Bank EFT remains available.');return {...c,secret:await this.decrypt(c.secret_encrypted)};}
 async status(){const c=await this.read('ikpay-provider');return {provider:'iKhokha',configured:!!c?.secret_encrypted,enabled:!!c?.enabled,app_id_hint:c?.app_id?'…'+c.app_id.slice(-4):'',callback_url:this.base+'/payments/ikhokha/callback',return_url:PAYMENT_SITE+'/return',amount_minor:30000,currency:'ZAR'};}
 async configure(data:any){
  const old=await this.read('ikpay-provider');const appId=String(data.app_id||old?.app_id||'').trim(),secret=String(data.app_secret||'').trim();
  if(!/^[A-Za-z0-9_-]{16,128}$/.test(appId)||(!secret&&!old?.secret_encrypted)||secret.length>256||(secret&&secret.length<16))this.failure(422,'Enter the application ID and its matching signing secret.');
  if(!secret&&old?.app_id!==appId)this.failure(422,'Changing the application ID also requires its matching secret.');
  const value={app_id:appId,secret_encrypted:secret?await this.encrypt(secret):old.secret_encrypted,enabled:data.enabled===true,updated:Math.floor(Date.now()/1000)};
  const r=await this.db.from('loop_config').upsert({key:'ikpay-provider',value});this.checked(r.error);return this.status();
 }
 private async save(id:string,value:any,onlyPending=false){let q=this.db.from('loop_config').update({value}).eq('key','ikpay-intent:'+id);if(onlyPending)q=q.eq('value->>state','pending');const r=await q;this.checked(r.error);}
 private publicIntent(p:any){return {id:p.id,state:p.state,amount_minor:p.amount_minor,currency:p.currency,checkout_url:p.state==='pending'?p.checkout_url||null:null,created:p.created,confirmed_at:p.confirmed_at||null};}
 async create(deviceId:string){
  const cfg=await this.config(),now=Math.floor(Date.now()/1000);const pointerKey='ikpay-active:'+deviceId;
  const pointer=await this.read(pointerKey);
  if(pointer){const p=await this.read('ikpay-intent:'+pointer.id);if(p?.state==='review_required')return this.publicIntent(p);if(p?.state==='paid'&&now-(p.confirmed_at||p.created)<120)return this.publicIntent(p);if(p?.state==='pending'&&now-p.created<86400&&trustedCheckoutUrl(p.checkout_url)){try{const checked=await this.verifyAndCredit(p.id);if(checked?.state==='paid'||checked?.state==='review_required')return this.publicIntent(checked);}catch{/* Existing checkout remains usable during a temporary status-service error. */}return this.publicIntent(p);}if(p?.state==='creating'&&now-p.created<120)this.failure(409,'A payment link is being prepared. Please try again shortly.');if(p?.state==='failed'&&now-p.created<60)this.failure(429,'The provider could not create the link. Wait one minute before retrying, or use EFT.');const removed=await this.db.from('loop_config').delete().eq('key',pointerKey).eq('value->>id',pointer.id);this.checked(removed.error);}
  const deviceResult=await this.db.from('loop_devices').select('id,customer_id,released,token').eq('id',deviceId).maybeSingle();this.checked(deviceResult.error);const device=deviceResult.data;
  if(!device?.token||device.released)this.failure(409,'Online payments require an active enrolled customer account.');
  const count=await this.db.from('loop_config').select('key',{count:'exact',head:true}).like('key','ikpay-intent:%').eq('value->>device_id',deviceId).gte('value->>created',String(now-86400));this.checked(count.error);if((count.count||0)>=10)this.failure(429,'Too many new payment links today. Use your existing link or EFT.');
  const id=crypto.randomUUID();const hold=await this.db.from('loop_config').insert({key:pointerKey,value:{id,created:now}});if(hold.error){if(hold.error.code==='23505')this.failure(409,'Another payment link is being prepared. Please try again shortly.');this.checked(hold.error);}
  const intent:any={id,device_id:deviceId,amount_minor:30000,currency:'ZAR',app_id:cfg.app_id,state:'creating',created:now,last_checked:0};
  const stored=await this.db.from('loop_config').insert({key:'ikpay-intent:'+id,value:intent});this.checked(stored.error);
  try{
   const customer=await this.db.from('loop_customers').select('reference').eq('id',device.customer_id).single();this.checked(customer.error);
   const result=await ikRequest(cfg,'/public-api/v1/api/payment',{entityID:cfg.app_id,externalEntityID:deviceId,amount:intent.amount_minor,currency:'ZAR',requesterUrl:PAYMENT_SITE,mode:'live',description:'Loop Secure '+customer.data.reference+' · 30-day payment',externalTransactionID:id,urls:{callbackUrl:this.base+'/payments/ikhokha/callback',successPageUrl:PAYMENT_SITE+'/return?result=success',failurePageUrl:PAYMENT_SITE+'/return?result=failure',cancelUrl:PAYMENT_SITE+'/return?result=cancel'}});
   if(result.responseCode!=='00'||result.externalTransactionID!==id||!trustedCheckoutUrl(result.paylinkUrl)||typeof result.paylinkID!=='string'||!/^[A-Za-z0-9_-]{3,100}$/.test(result.paylinkID))throw new Error('iKhokha did not return a valid payment link. Check the merchant API configuration.');
   Object.assign(intent,{paylink_id:result.paylinkID,checkout_url:result.paylinkUrl,state:'pending'});await this.save(id,intent);return this.publicIntent(intent);
  }catch(error:any){await this.save(id,{...intent,state:'failed'});this.failure(502,error.message||'The payment link could not be created. Bank EFT remains available.');}
 }
 async verifyAndCredit(id:string,force=false){
  let intent=await this.read('ikpay-intent:'+id);if(!intent||intent.state!=='pending')return intent;
  const now=Math.floor(Date.now()/1000);if(!force&&now-intent.last_checked<45)return intent;
  const lease=await this.db.from('loop_config').update({value:{...intent,last_checked:now}}).eq('key','ikpay-intent:'+id).eq('value->>state','pending').eq('value->>last_checked',String(intent.last_checked||0)).select('key');this.checked(lease.error);if(!lease.data?.length)return this.read('ikpay-intent:'+id);
  const cfg=await this.config();if(intent.app_id!==cfg.app_id)this.failure(409,'This payment belongs to a different merchant application.');const result=await ikRequest(cfg,'/public-api/v1/api/getStatus/'+intent.paylink_id);
  if(!isPaidForIntent(intent,result)){if(result.status==='PAID')await this.save(id,{...intent,last_checked:now,state:'review_required',note:'Provider payment fields did not match the expected link, amount or currency'},true);return this.read('ikpay-intent:'+id);}
  const paid=await this.db.rpc('loop_confirm_ikpay_payment',{p_id:id,p_paylink:intent.paylink_id,p_amount:result.amount,p_currency:'ZAR'});this.checked(paid.error);return this.read('ikpay-intent:'+id);
 }
 async reconcile(deviceId?:string){
  const cfg=await this.read('ikpay-provider');if(!cfg?.enabled)return;
  let q=this.db.from('loop_config').select('value').like('key','ikpay-intent:%').eq('value->>state','pending').gte('value->>created',String(Math.floor(Date.now()/1000)-7*86400));if(deviceId)q=q.eq('value->>device_id',deviceId);
  const rows=await q.order('value->>last_checked',{ascending:true}).limit(deviceId?5:10);this.checked(rows.error);
  await Promise.allSettled((rows.data||[]).map((r:any)=>this.verifyAndCredit(r.value.id)));
 }
 async current(deviceId:string){const active=await this.read('ikpay-active:'+deviceId);if(!active)return {state:'none'};const intent=await this.read('ikpay-intent:'+active.id);return intent?.device_id===deviceId?this.publicIntent(intent):{state:'none'};}
 async callback(headers:Headers,raw:string){
  const cfg=await this.config();if(headers.get('ik-appid')!==cfg.app_id||!await verifyIkCallback(new URL(this.base).pathname+'/payments/ikhokha/callback',raw,headers.get('ik-sign')||'',cfg.secret))this.failure(403,'Invalid payment callback signature');
  let payload:any;try{payload=JSON.parse(raw);}catch{this.failure(400,'Invalid payment callback');}
  if(!/^[0-9a-f-]{36}$/i.test(payload.externalTransactionID||'')||typeof payload.paylinkID!=='string'||payload.responseCode!=='00'||!['SUCCESS','FAILURE'].includes(payload.status))this.failure(400,'Invalid payment callback fields');
  const intent=await this.read('ikpay-intent:'+payload.externalTransactionID);
  if(!intent||intent.paylink_id!==payload.paylinkID)this.failure(409,'Payment callback does not match a stored payment intent');
  if(payload.status==='FAILURE')return {received:true,state:'payment_not_confirmed'};
  const result=await this.verifyAndCredit(intent.id,true);return {received:true,state:result?.state||'pending'};
 }
}
