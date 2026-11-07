CREATE OR REPLACE FUNCTION public.loop_dispatch(p_action text, p_actor text DEFAULT ''::text, p_device uuid DEFAULT NULL::uuid, p_data jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare
 t bigint:=floor(extract(epoch from clock_timestamp()));
 d public.loop_devices%rowtype; c public.loop_customers%rowtype; cl public.loop_claims%rowtype;
 msg public.loop_messages%rowtype; saved public.loop_requests%rowtype;
 result jsonb; people jsonb; payments jsonb; events jsonb; tickets jsonb; inbox jsonb; banking jsonb;
 token_hash text; did uuid; cid uuid; mid uuid; total integer; item text; status text;
begin
 if p_action='enroll_create' then
  if coalesce(p_data->>'consent','false')<>'true' then raise exception '422:Administrator confirmation of customer agreement is required'; end if;
  insert into public.loop_customers(name,identity,phone,reference,verified,verification_note,consent,created) values(p_data->>'name',p_data->>'identity',p_data->>'phone',p_data->>'reference',coalesce((p_data->>'identity_verified')::boolean,false),p_data->>'verification_note',true,t) returning * into c;
  insert into public.loop_devices(customer_id,enrollment,enrollment_expires,due,policy) values(c.id,p_data->>'enrollment_hash',t+1800,t+2592000,p_data->'policy') returning * into d;
  result:=jsonb_build_object('device_id',d.id,'reference',c.reference);
 elsif p_action='enroll_exchange' then
  if p_data ? 'request_key' then
   select * into saved from public.loop_requests where request_key=p_data->>'request_key';
   if found then
    if (saved.result->>'retry_until')::bigint<t or saved.payload->>'token_hash'<>p_data->>'token_hash' then raise exception '401:Enrollment retry expired';end if;
    return saved.result-'retry_until';
   end if;
  end if;
  select * into d from public.loop_devices where enrollment=p_data->>'enrollment_hash' and enrollment_expires>t for update;
  if not found then
   if p_data ? 'request_key' then
    select * into saved from public.loop_requests where request_key=p_data->>'request_key';
    if found and (saved.result->>'retry_until')::bigint>=t and saved.payload->>'token_hash'=p_data->>'token_hash' then return saved.result-'retry_until';end if;
   end if;
   raise exception '401:Enrollment code expired or already used';
  end if;
  update public.loop_devices set token=p_data->>'token_hash',enrollment=null,enrollment_expires=null,model=p_data->>'model',due=t+2592000,last_seen=t,enforcement_status='awaiting_device_report',identifiers=p_data->>'identifiers' where id=d.id;
  result:=jsonb_build_object('device_id',d.id);
  if p_data ? 'request_key' then
   insert into public.loop_requests(request_key,payload,result) values(p_data->>'request_key',jsonb_build_object('token_hash',p_data->>'token_hash','enrollment_hash',p_data->>'enrollment_hash'),result||jsonb_build_object('retry_until',d.enrollment_expires));
  end if;
 elsif p_action='enroll_reissue' then
  select * into d from public.loop_devices where id=(p_data->>'id')::uuid for update;
  if not found then raise exception '404:Device not found';end if;
  if d.token is not null or d.released then raise exception '409:This phone is already enrolled; update its installed app instead';end if;
  update public.loop_devices set enrollment=p_data->>'enrollment_hash',enrollment_expires=t+1800,policy=p_data->'policy',revision=revision+1 where id=d.id;
  select * into c from public.loop_customers where id=d.customer_id;
  result:=jsonb_build_object('device_id',d.id,'reference',c.reference);
 elsif p_action='overview' then
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into people from (select dv.id,cu.name,cu.phone,cu.reference,cu.verified,dv.due,dv.revision,dv.released,dv.model,dv.last_seen,dv.acknowledged_revision,dv.enforcement_status,dv.policy::text as policy,(select coalesce(sum(amount),0) from public.loop_claims pay where pay.device_id=dv.id and pay.state='approved') as approved_total,(select count(*) from public.loop_claims pay where pay.device_id=dv.id and pay.state='approved') as approved_count from public.loop_devices dv join public.loop_customers cu on cu.id=dv.customer_id order by cu.created desc) x;
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into payments from (select p.*,cu.name,cu.reference from public.loop_claims p join public.loop_devices dv on dv.id=p.device_id join public.loop_customers cu on cu.id=dv.customer_id where p.state='pending' or p.id in (select id from public.loop_claims order by submitted desc limit 200) order by p.submitted desc) x;
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into events from (select * from public.loop_audit order by id desc limit 100) x;
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into tickets from (select s.*,cu.name,cu.reference from public.loop_support s join public.loop_devices dv on dv.id=s.device_id join public.loop_customers cu on cu.id=dv.customer_id order by s.created desc limit 100) x;
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into inbox from (select m.id,m.title,m.body,m.created,count(x.id) as recipients,count(x.delivered) as delivered,count(x.read_at) as read_count from public.loop_messages m left join public.loop_deliveries x on x.message_id=m.id group by m.id order by m.created desc limit 100) x;
  return jsonb_build_object('devices',people,'claims',payments,'audit',events,'support',tickets,'messages',inbox,'payment_summary',(select jsonb_build_object('approved_total',coalesce(sum(amount) filter (where state='approved'),0),'approved_count',count(*) filter (where state='approved'),'pending_count',count(*) filter (where state='pending'),'currency','ZAR','contract_total',null,'progress_percent',null) from public.loop_claims),'server_time',t);
 elsif p_action='sync' then
  select * into d from public.loop_devices where id=p_device for update;
  if not found then raise exception '401:Device not found'; end if;
  if (p_data->>'revision')::integer>d.revision then raise exception '409:Acknowledgement ahead of server'; end if;
  update public.loop_devices set last_seen=t,acknowledged_revision=greatest(acknowledged_revision,(p_data->>'revision')::integer),enforcement_status=case when (p_data->>'revision')::integer>=acknowledged_revision then p_data->>'status' else enforcement_status end where id=d.id;
  select * into c from public.loop_customers where id=d.customer_id;
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into payments from (select id,amount,state,submitted,reviewed,reason from public.loop_claims where device_id=d.id order by submitted desc limit 30) x;
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into inbox from (select x.id,m.title,m.body,m.created,x.delivered,x.read_at from public.loop_deliveries x join public.loop_messages m on m.id=x.message_id where x.device_id=d.id order by (x.delivered is not null),case when x.delivered is null then x.id else -x.id end limit 100) x;
  select value into banking from public.loop_config where key='banking';
  return jsonb_build_object('device_id',d.id,'revision',d.revision,'server_time',t,'due',d.due,'released',d.released,'policy',d.policy,'customer_name',c.name,'customer_phone',c.phone,'account_created',c.created,'reference',c.reference,'amount',300,'currency','ZAR','support_email','basanisilinda@gmail.com','payment_summary',(select jsonb_build_object('approved_total',coalesce(sum(amount) filter (where state='approved'),0),'approved_count',count(*) filter (where state='approved'),'pending_count',count(*) filter (where state='pending'),'currency','ZAR','contract_total',null,'progress_percent',null) from public.loop_claims where device_id=d.id),'claims',payments,'messages',inbox,'eft',coalesce(banking,'{}'));
 elsif p_action='events' then
  select * into d from public.loop_devices where id=p_device;
  select jsonb_build_object('cursor',d.revision::text||':'||coalesce(max(id),0)::text||':'||coalesce(min(case when delivered is null then id end),0)::text) into result from public.loop_deliveries where device_id=p_device;
  return result;
 elsif p_action='claim' then
  select * into d from public.loop_devices where id=p_device for update;
  if d.released then raise exception '409:Account released'; end if;
  select * into cl from public.loop_claims where device_id=d.id and (request_key=p_data->>'request_key' or state='pending') order by (request_key=p_data->>'request_key') desc limit 1;
  if found then return jsonb_build_object('id',cl.id,'state',cl.state); end if;
  insert into public.loop_claims(device_id,request_key,amount,bank_reference,submitted) values(d.id,p_data->>'request_key',300,p_data->>'bank_reference',t) returning * into cl;
  result:=jsonb_build_object('id',cl.id,'state','pending');
 elsif p_action='review' then
  select * into cl from public.loop_claims where id=(p_data->>'id')::uuid for update;
  if not found then raise exception '404:Claim not found'; end if;
  if cl.state<>'pending' then raise exception '409:Already reviewed'; end if;
  select * into d from public.loop_devices where id=cl.device_id for update;
  if d.released then raise exception '409:Account released'; end if;
  if p_data->>'decision'='approve' then
   if char_length(p_data->>'receipt_reference')<5 then raise exception '422:Settled bank transaction reference required'; end if;
   if exists(select 1 from public.loop_claims where receipt_reference=p_data->>'receipt_reference') then raise exception '409:Bank transaction already used'; end if;
   update public.loop_devices set due=greatest(due,t)+2592000,policy=policy||'{"manual_lock":false,"grace_until":0}'::jsonb,revision=revision+1 where id=d.id;
   update public.loop_claims set state='approved',receipt_reference=p_data->>'receipt_reference',reviewed=t,reviewer=p_actor,reason=p_data->>'reason' where id=cl.id;
   status:='approved';
  else
   update public.loop_devices set revision=revision+1 where id=d.id;
   update public.loop_claims set state='rejected',reviewed=t,reviewer=p_actor,reason=p_data->>'reason' where id=cl.id;status:='rejected';
  end if;
  result:=jsonb_build_object('state',status,'device_delivery','pending_next_sync');
 elsif p_action='control' then
  if p_data->>'action' not in ('lock','unlock','grace') then raise exception '422:Unknown device control';end if;
  perform pg_advisory_xact_lock(hashtext('control:'||(p_data->>'request_key')));
  select * into saved from public.loop_requests where request_key='control:'||(p_data->>'request_key');
  if found then if saved.payload<>p_data then raise exception '409:Request key reused for another command';end if;return saved.result;end if;
  select * into d from public.loop_devices where id=(p_data->>'id')::uuid for update;
  if not found then raise exception '404:Device not found';end if;
  if d.released then raise exception '409:Released device cannot be restricted';end if;
  if d.token is null then raise exception '409:Enroll the phone before sending a control';end if;
  if p_data->>'action'='unlock' and t>=greatest(d.due+coalesce((d.policy->>'grace_hours')::integer,24)*3600,coalesce((d.policy->>'grace_until')::bigint,0)) then raise exception '409:Payment is overdue. Approve the settled EFT or give 24-hour grace.';end if;
  update public.loop_devices set policy=policy||jsonb_build_object('enforcement_enabled',true,'manual_lock',p_data->>'action'='lock','grace_until',case when p_data->>'action'='grace' then greatest(coalesce((policy->>'grace_until')::bigint,0),t+86400) when p_data->>'action'='lock' then 0 else coalesce((policy->>'grace_until')::bigint,0) end,'banking_packages',case when jsonb_array_length(coalesce(policy->'banking_packages','[]'))=0 then p_data->'banking_packages' else policy->'banking_packages' end),revision=revision+1 where id=d.id;
  result:=jsonb_build_object('action',p_data->>'action','delivery','pending_next_sync','grace_until',(select policy->'grace_until' from public.loop_devices where id=d.id));
  insert into public.loop_requests(request_key,payload,result) values('control:'||(p_data->>'request_key'),p_data,result);
 elsif p_action='policy' then
  select * into d from public.loop_devices where id=(p_data->>'id')::uuid for update;
  if not found then raise exception '404:Device not found'; end if;
  if d.released then raise exception '409:Released device cannot be restricted'; end if;
  update public.loop_devices set policy=(p_data->'policy')||jsonb_build_object('manual_lock',coalesce((policy->>'manual_lock')::boolean,false),'grace_until',coalesce((policy->>'grace_until')::bigint,0)),revision=revision+1 where id=d.id;
  result:=jsonb_build_object('delivery','pending_next_sync');
 elsif p_action='fleet' then
  perform pg_advisory_xact_lock(hashtext('loop-fleet'));
  select * into saved from public.loop_requests where request_key=p_data->>'request_key';
  if found then if saved.payload<>p_data then raise exception '409:Request key reused for different policy'; end if;return saved.result;end if;
  perform id from public.loop_devices where released=false order by id for update;
  update public.loop_devices set policy=(p_data->'policy')||jsonb_build_object('manual_lock',coalesce((policy->>'manual_lock')::boolean,false),'grace_until',coalesce((policy->>'grace_until')::bigint,0)),revision=revision+1 where released=false;
  get diagnostics total=row_count;
  result:=jsonb_build_object('affected',total,'delivery','pending_device_acknowledgements');
  insert into public.loop_requests values(p_data->>'request_key',p_data,result);
 elsif p_action='release' then
  select * into d from public.loop_devices where id=(p_data->>'id')::uuid for update;
  if not found then raise exception '404:Device not found'; end if;
  if not d.released then
   update public.loop_devices set released=true,revision=revision+1 where id=d.id;
   update public.loop_claims set state='rejected',reviewed=t,reviewer=p_actor,reason='Fully paid and released' where device_id=d.id and state='pending';
  end if;
  result:=jsonb_build_object('delivery','pending_next_sync');
 elsif p_action='message' then
  perform pg_advisory_xact_lock(hashtext(p_data->>'request_key'));
  select * into msg from public.loop_messages where request_key=p_data->>'request_key';
  if found then
   if msg.title<>p_data->>'title' or msg.body<>p_data->>'message' then raise exception '409:Request key already used'; end if;
   return jsonb_build_object('id',msg.id,'recipients',(select count(*) from public.loop_deliveries where message_id=msg.id),'delivery','queued');
  end if;
  insert into public.loop_messages(request_key,title,body,created,sender) values(p_data->>'request_key',p_data->>'title',p_data->>'message',t,p_actor) returning * into msg;
  if p_data->>'all_active'='true' then
   insert into public.loop_deliveries(message_id,device_id) select msg.id,id from public.loop_devices where released=false and token is not null;
  else
   for item in select distinct jsonb_array_elements_text(p_data->'device_ids') loop
    select * into d from public.loop_devices where id=item::uuid and released=false and token is not null;
    if not found then raise exception '422:Recipient is not enrolled and active'; end if;
    insert into public.loop_deliveries(message_id,device_id) values(msg.id,d.id);
   end loop;
  end if;
  select count(*) into total from public.loop_deliveries where message_id=msg.id;
  if total=0 then raise exception '409:No enrolled active phones to message'; end if;
  result:=jsonb_build_object('id',msg.id,'recipients',total,'delivery','queued');
 elsif p_action='receipt' then
  update public.loop_deliveries set delivered=coalesce(delivered,t),read_at=case when p_data->>'state'='read' then coalesce(read_at,t) else read_at end where device_id=p_device and id in (select jsonb_array_elements_text(p_data->'ids')::bigint);
  return jsonb_build_object('ok',true);
 elsif p_action='support' then
  if (select count(*) from public.loop_support where device_id=p_device and created>t-3600)>=10 then raise exception '429:Too many messages; please wait';end if;
  insert into public.loop_support(device_id,message,created) values(p_device,p_data->>'message',t) returning id into mid;
  result:=jsonb_build_object('id',mid,'state','open');
 elsif p_action='support_resolve' then
  update public.loop_support set state='resolved' where id=(p_data->>'id')::uuid;
  if not found then raise exception '404:Support message not found';end if;
  result:=jsonb_build_object('state','resolved');
 elsif p_action='identifiers' then
  select * into d from public.loop_devices where id=(p_data->>'id')::uuid;
  if not found then raise exception '404:Device not found';end if;
  result:=jsonb_build_object('encrypted',d.identifiers);
 elsif p_action='banking_get' then
  select value into result from public.loop_config where key='banking';return coalesce(result,'{}');
 elsif p_action='banking_put' then
  if p_data->>'details_verified'<>'true' then raise exception '422:Verify banking details';end if;
  insert into public.loop_config values('banking',p_data-'verification_note'-'details_verified') on conflict(key) do update set value=excluded.value;
  update public.loop_devices set revision=revision+1 where released=false;
  result:=jsonb_build_object('delivery','pending_device_acknowledgements');
 elsif p_action='qr_check' then
  select * into d from public.loop_devices where enrollment=p_data->>'enrollment_hash' and enrollment_expires>t;
  if not found then raise exception '401:Enrollment token expired or used';end if;
  return jsonb_build_object('valid',true);
 else raise exception '404:Unknown cloud operation';
 end if;
 insert into public.loop_audit(at,actor,action,subject,detail) values(t,coalesce(nullif(p_actor,''),p_device::text,'device'),p_action,coalesce(p_data->>'id',p_device::text,result->>'device_id',result->>'id','fleet'),coalesce(p_data->>'reason',p_data->>'verification_note',p_data->>'consent_source',''));
 return result;
end;$function$
