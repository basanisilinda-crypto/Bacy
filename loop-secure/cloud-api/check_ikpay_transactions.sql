-- Explicit transaction: no fixture records or financial changes are committed.
begin;
do $$
declare e jsonb; d uuid; intent_id uuid:=gen_random_uuid(); p jsonb; due_before bigint; revision_before integer; result jsonb; n integer;
begin
 begin
  e:=public.loop_dispatch('enroll_create','transaction-test',null,jsonb_build_object('name','Payment rollback fixture','identity','encrypted-fixture','phone','0000000000','reference','IK-TEST-'||gen_random_uuid(),'consent',true,'identity_verified',false,'verification_note','Rolled back payment acceptance fixture','policy','{"manual_lock":true}'::jsonb,'enrollment_hash','test-'||gen_random_uuid()));
  d:=(e->>'device_id')::uuid;
  update public.loop_devices set due=floor(extract(epoch from now()))::bigint+3600,policy=policy||'{"manual_lock":true}'::jsonb where id=d;
  select due,revision into due_before,revision_before from public.loop_devices where id=d;
  p:=jsonb_build_object('id',intent_id,'device_id',d,'state','pending','paylink_id','rollback-'||intent_id,'amount_minor',30000,'currency','ZAR');
  insert into public.loop_config(key,value) values('ikpay-intent:'||intent_id,p);
  begin
   perform public.loop_confirm_ikpay_payment(intent_id,p->>'paylink_id',29999,'ZAR');
   raise exception 'Incorrect amount accepted';
  exception when sqlstate 'P0001' then if sqlerrm not like '409:%' then raise;end if;end;
  begin
   perform public.loop_confirm_ikpay_payment(intent_id,null,30000,'ZAR');
   raise exception 'Missing link accepted';
  exception when sqlstate 'P0001' then if sqlerrm not like '409:%' then raise;end if;end;
  result:=public.loop_confirm_ikpay_payment(intent_id,p->>'paylink_id',30000,'ZAR');
  if result->>'state'<>'paid' then raise exception 'Payment was not approved';end if;
  result:=public.loop_confirm_ikpay_payment(intent_id,p->>'paylink_id',30000,'ZAR');
  if result->>'duplicate'<>'true' then raise exception 'Duplicate payment not detected';end if;
  if not exists(select 1 from public.loop_devices where id=d and due=due_before+2592000 and revision=revision_before+1 and (policy->>'manual_lock')::boolean=false) then raise exception 'Payment did not grant exactly one paid period and clear finance lock';end if;
  select count(*) into n from public.loop_claims where device_id=d and request_key='ikpay:'||intent_id;
  if n<>1 then raise exception 'Duplicate receipt credited';end if;
  update public.loop_devices set released=true where id=d;
  intent_id:=gen_random_uuid();p:=p||jsonb_build_object('id',intent_id,'state','pending','paylink_id','released-'||intent_id);
  insert into public.loop_config(key,value) values('ikpay-intent:'||intent_id,p);
  result:=public.loop_confirm_ikpay_payment(intent_id,p->>'paylink_id',30000,'ZAR');
  if result->>'state'<>'review_required' then raise exception 'Released phone payment did not require settlement review';end if;
  if has_function_privilege('anon','public.loop_confirm_ikpay_payment(uuid,text,integer,text)','execute') or has_function_privilege('authenticated','public.loop_confirm_ikpay_payment(uuid,text,integer,text)','execute') then raise exception 'Public financial confirmation permission';end if;
  raise notice 'Payment acceptance checks passed';
 end;
end $$;
rollback;
