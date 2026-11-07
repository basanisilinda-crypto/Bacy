-- Isolated cloud tables. Existing MBMF finance tables/functions are unchanged.
create table public.loop_config (key text primary key,value jsonb not null);
create table public.loop_customers(id uuid primary key default gen_random_uuid(),name text not null,identity text not null,phone text not null,reference text unique not null,verified boolean not null,verification_note text not null,consent boolean not null,created bigint not null);
create table public.loop_devices(id uuid primary key default gen_random_uuid(),customer_id uuid unique not null references public.loop_customers(id),token text unique,enrollment text unique,enrollment_expires bigint,due bigint not null,revision integer not null default 1,released boolean not null default false,policy jsonb not null,model text,last_seen bigint,acknowledged_revision integer not null default 0,enforcement_status text not null default 'not_enrolled',identifiers text);
create table public.loop_claims(id uuid primary key default gen_random_uuid(),device_id uuid not null references public.loop_devices(id),request_key text not null,amount integer not null default 300,bank_reference text not null,state text not null default 'pending' check(state in ('pending','approved','rejected')),submitted bigint not null,reviewed bigint,reviewer text,reason text,receipt_reference text,unique(device_id,request_key));
create unique index loop_one_pending on public.loop_claims(device_id) where state='pending';
create unique index loop_unique_receipt on public.loop_claims(receipt_reference) where receipt_reference is not null;
create index loop_claim_device on public.loop_claims(device_id,submitted desc);
create table public.loop_audit(id bigint generated always as identity primary key,at bigint not null,actor text not null,action text not null,subject text not null,detail text not null default '');
create table public.loop_messages(id uuid primary key default gen_random_uuid(),request_key text unique not null,title text not null,body text not null,created bigint not null,sender text not null);
create table public.loop_deliveries(id bigint generated always as identity primary key,message_id uuid not null references public.loop_messages(id),device_id uuid not null references public.loop_devices(id),delivered bigint,read_at bigint,unique(message_id,device_id));
create index loop_delivery_device on public.loop_deliveries(device_id,id);
create table public.loop_support(id uuid primary key default gen_random_uuid(),device_id uuid not null references public.loop_devices(id),message text not null,created bigint not null,state text not null default 'open');
create index loop_support_device on public.loop_support(device_id,created);
create table public.loop_requests(request_key text primary key,payload jsonb not null,result jsonb not null);

-- No direct browser/device database access. The authenticated Edge API owns access.
alter table public.loop_config enable row level security;
alter table public.loop_customers enable row level security;
alter table public.loop_devices enable row level security;
alter table public.loop_claims enable row level security;
alter table public.loop_audit enable row level security;
alter table public.loop_messages enable row level security;
alter table public.loop_deliveries enable row level security;
alter table public.loop_support enable row level security;
alter table public.loop_requests enable row level security;
revoke all on public.loop_config,public.loop_customers,public.loop_devices,public.loop_claims,public.loop_audit,public.loop_messages,public.loop_deliveries,public.loop_support,public.loop_requests from anon,authenticated;
grant all on public.loop_config,public.loop_customers,public.loop_devices,public.loop_claims,public.loop_audit,public.loop_messages,public.loop_deliveries,public.loop_support,public.loop_requests to service_role;
grant usage,select on sequence public.loop_audit_id_seq,public.loop_deliveries_id_seq to service_role;

create function public.loop_dispatch(p_action text,p_actor text default '',p_device uuid default null,p_data jsonb default '{}'::jsonb) returns jsonb language plpgsql security invoker set search_path='' as $$
declare
 t bigint:=floor(extract(epoch from clock_timestamp()));
 d public.loop_devices%rowtype; c public.loop_customers%rowtype; cl public.loop_claims%rowtype;
 msg public.loop_messages%rowtype; saved public.loop_requests%rowtype;
 result jsonb; people jsonb; payments jsonb; events jsonb; tickets jsonb; inbox jsonb; banking jsonb;
 token_hash text; did uuid; cid uuid; mid uuid; total integer; item text; status text;
begin
 if p_action='enroll_create' then
  if p_data->>'consent'<>'true' or p_data->>'identity_verified'<>'true' then raise exception '422:Identity check and recorded consent required'; end if;
  insert into public.loop_customers(name,identity,phone,reference,verified,verification_note,consent,created) values(p_data->>'name',p_data->>'identity',p_data->>'phone',p_data->>'reference',true,p_data->>'verification_note',true,t) returning * into c;
  insert into public.loop_devices(customer_id,enrollment,enrollment_expires,due,policy) values(c.id,p_data->>'enrollment_hash',t+1800,t+2592000,p_data->'policy') returning * into d;
  result:=jsonb_build_object('device_id',d.id,'reference',c.reference);
 elsif p_action='enroll_exchange' then
  select * into d from public.loop_devices where enrollment=p_data->>'enrollment_hash' and enrollment_expires>t for update;
  if not found then raise exception '401:Enrollment code expired or already used'; end if;
  update public.loop_devices set token=p_data->>'token_hash',enrollment=null,enrollment_expires=null,model=p_data->>'model',due=t+2592000,last_seen=t,enforcement_status='awaiting_device_report',identifiers=p_data->>'identifiers' where id=d.id;
  result:=jsonb_build_object('device_id',d.id);
 elsif p_action='overview' then
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into people from (select d.id,c.name,c.phone,c.reference,c.verified,d.due,d.revision,d.released,d.model,d.last_seen,d.acknowledged_revision,d.enforcement_status,d.policy::text as policy from public.loop_devices d join public.loop_customers c on c.id=d.customer_id order by c.created desc) x;
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into payments from (select p.*,c.name,c.reference from public.loop_claims p join public.loop_devices d on d.id=p.device_id join public.loop_customers c on c.id=d.customer_id order by p.submitted desc limit 200) x;
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into events from (select * from public.loop_audit order by id desc limit 100) x;
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into tickets from (select s.*,c.name,c.reference from public.loop_support s join public.loop_devices d on d.id=s.device_id join public.loop_customers c on c.id=d.customer_id order by s.created desc limit 100) x;
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into inbox from (select m.id,m.title,m.body,m.created,count(x.id) as recipients,count(x.delivered) as delivered,count(x.read_at) as read_count from public.loop_messages m left join public.loop_deliveries x on x.message_id=m.id group by m.id order by m.created desc limit 100) x;
  return jsonb_build_object('devices',people,'claims',payments,'audit',events,'support',tickets,'messages',inbox,'server_time',t);
 elsif p_action='sync' then
  select * into d from public.loop_devices where id=p_device for update;
  if not found then raise exception '401:Device not found'; end if;
  if (p_data->>'revision')::integer>d.revision then raise exception '409:Acknowledgement ahead of server'; end if;
  update public.loop_devices set last_seen=t,acknowledged_revision=greatest(acknowledged_revision,(p_data->>'revision')::integer),enforcement_status=case when (p_data->>'revision')::integer>=acknowledged_revision then p_data->>'status' else enforcement_status end where id=d.id;
  select * into c from public.loop_customers where id=d.customer_id;
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into payments from (select id,amount,state,submitted,reviewed,reason from public.loop_claims where device_id=d.id order by submitted desc limit 30) x;
  select coalesce(jsonb_agg(to_jsonb(x)),'[]') into inbox from (select x.id,m.title,m.body,m.created,x.delivered,x.read_at from public.loop_deliveries x join public.loop_messages m on m.id=x.message_id where x.device_id=d.id order by (x.delivered is not null),case when x.delivered is null then x.id else -x.id end limit 100) x;
  select value into banking from public.loop_config where key='banking';
  return jsonb_build_object('device_id',d.id,'revision',d.revision,'server_time',t,'due',d.due,'released',d.released,'policy',d.policy,'customer_name',c.name,'reference',c.reference,'amount',300,'currency','ZAR','support_email','basanisilinda@gmail.com','claims',payments,'messages',inbox,'eft',coalesce(banking,'{}'));
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
   update public.loop_devices set due=greatest(due,t)+2592000,revision=revision+1 where id=d.id;
   update public.loop_claims set state='approved',receipt_reference=p_data->>'receipt_reference',reviewed=t,reviewer=p_actor,reason=p_data->>'reason' where id=cl.id;
   status:='approved';
  else
   update public.loop_devices set revision=revision+1 where id=d.id;
   update public.loop_claims set state='rejected',reviewed=t,reviewer=p_actor,reason=p_data->>'reason' where id=cl.id;status:='rejected';
  end if;
  result:=jsonb_build_object('state',status,'device_delivery','pending_next_sync');
 elsif p_action='policy' then
  select * into d from public.loop_devices where id=(p_data->>'id')::uuid for update;
  if not found then raise exception '404:Device not found'; end if;
  if d.released then raise exception '409:Released device cannot be restricted'; end if;
  update public.loop_devices set policy=p_data->'policy',revision=revision+1 where id=d.id;
  result:=jsonb_build_object('delivery','pending_next_sync');
 elsif p_action='fleet' then
  perform pg_advisory_xact_lock(hashtext('loop-fleet'));
  select * into saved from public.loop_requests where request_key=p_data->>'request_key';
  if found then if saved.payload<>p_data then raise exception '409:Request key reused for different policy'; end if;return saved.result;end if;
  perform id from public.loop_devices where released=false order by id for update;
  update public.loop_devices set policy=p_data->'policy',revision=revision+1 where released=false;
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
 insert into public.loop_audit(at,actor,action,subject,detail) values(t,coalesce(nullif(p_actor,''),p_device::text,'device'),p_action,coalesce(p_data->>'id',p_device::text,result->>'device_id',result->>'id','fleet'),coalesce(p_data->>'reason',p_data->>'verification_note',''));
 return result;
end;$$;
revoke all on function public.loop_dispatch(text,text,uuid,jsonb) from public,anon,authenticated;
grant execute on function public.loop_dispatch(text,text,uuid,jsonb) to service_role;
