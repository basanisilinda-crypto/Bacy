/** Admin-only projection. Never exposes tokens, signing secrets or checkout URLs. */
export function orderHistory(rows:any[],devices:any[]){
 const people=new Map(devices.map(d=>[d.id,d]));
 return rows.flatMap(row=>{const p=row?.value,d=people.get(p?.device_id);
  if(!p||!d||!/^([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})$/i.test(p.id||''))return [];
  return [{order_id:p.id,device_id:p.device_id,name:d.name,reference:d.reference,
   amount_minor:Number.isSafeInteger(p.amount_minor)&&p.amount_minor>0?p.amount_minor:null,currency:p.currency==='ZAR'?'ZAR':'Unknown',
   state:['creating','pending','paid','failed','review_required'].includes(p.state)?p.state:'pending',
   checkout_outcome:['failure','attempt_limit','cancel','success'].includes(p.last_checkout_outcome)?p.last_checkout_outcome:null,
   created:Number.isSafeInteger(p.created)?p.created:null,confirmed_at:Number.isSafeInteger(p.confirmed_at)?p.confirmed_at:null}];
 });
}
