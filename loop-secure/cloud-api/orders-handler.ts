import {orderHistory} from './order-history.ts';
export function createOrderHandler(db:any){return async(req:Request)=>{
 const reply=(data:any,status=200)=>new Response(JSON.stringify(data),{status,headers:{'content-type':'application/json','cache-control':'no-store','x-content-type-options':'nosniff'}});
 if(req.method!=='GET')return reply({detail:'Read-only endpoint'},405);
 const token=req.headers.get('authorization')?.match(/^Bearer (\S{20,512})$/)?.[1];if(!token)return reply({detail:'Administrator authentication required'},401);
 try{
  const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(token)))).map(v=>v.toString(16).padStart(2,'0')).join('');
  const admin=await db.from('loop_config').select('value').eq('key','admin').maybeSingle();if(admin.error)throw new Error();
  if(admin.data?.value?.token_hash!==hash){const session=await db.from('loop_config').select('value').eq('key','mobile_session:'+hash).maybeSingle();if(session.error)throw new Error();const s=session.data?.value;if(!s||s.revoked||!Number.isSafeInteger(s.expires)||s.expires<=Date.now()/1000)return reply({detail:'Administrator authentication required'},401);}
  const orders=await db.from('loop_config').select('value').like('key','ikpay-intent:%').order('value->>created',{ascending:false}).limit(200);
  if(orders.error)throw new Error();
  const ids=[...new Set((orders.data||[]).map((r:any)=>r.value?.device_id).filter((id:any)=>typeof id==='string'))];
  if(!ids.length)return reply({transactions:[],server_time:Math.floor(Date.now()/1000)});
  const devices=await db.from('loop_devices').select('id,customer_id').in('id',ids);if(devices.error)throw new Error();
  const customerIds=[...new Set((devices.data||[]).map((d:any)=>d.customer_id))];
  if(!customerIds.length)return reply({transactions:[],server_time:Math.floor(Date.now()/1000)});
  const customers=await db.from('loop_customers').select('id,name,reference').in('id',customerIds);if(customers.error)throw new Error();
  const names=new Map((customers.data||[]).map((c:any)=>[c.id,c]));const people=(devices.data||[]).map((d:any)=>{const c:any=names.get(d.customer_id);return {id:d.id,name:c?.name||'Customer',reference:c?.reference||''};});
  return reply({transactions:orderHistory(orders.data||[],people),server_time:Math.floor(Date.now()/1000)});
 }catch{return reply({detail:'Online order history is temporarily unavailable'},503);}
};}
