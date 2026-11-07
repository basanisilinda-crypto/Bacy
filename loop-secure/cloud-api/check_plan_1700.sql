-- All financial acceptance fixtures are rolled back explicitly.
begin;
do $$
declare e jsonb;d uuid;online_device uuid;intent_id uuid;stale_id uuid;claim_id uuid;p jsonb;s jsonb;res jsonb;t bigint:=floor(extract(epoch from now()));
begin
 e:=public.loop_dispatch('enroll_create','rollback-test',null,jsonb_build_object('name','Plan rollback fixture','identity','','phone','0000000000','reference','PLAN-QA-'||gen_random_uuid(),'consent',true,'identity_verified',false,'verification_note','Rolled back only','policy','{"manual_lock":true}'::jsonb,'enrollment_hash','test-'||gen_random_uuid()));d:=(e->>'device_id')::uuid;
 insert into public.loop_claims(device_id,request_key,amount,bank_reference,state,submitted) values(d,'seed',1500,'fixture-only','approved',t);
 s:=public.loop_plan_summary(d);if (s->>'contract_total')::int<>1700 or (s->>'remaining_balance')::int<>200 or (s->>'next_payment')::int<>200 or (s->>'payments_remaining')::int<>1 or (s->>'progress_percent')::numeric<>88.2 then raise exception 'Incorrect final balance/progress';end if;
 s:=public.loop_dispatch('sync','device',d,'{"revision":0,"status":"unrestricted"}');if (s->>'amount')::int<>200 or (s->'payment_summary'->>'contract_total')::int<>1700 then raise exception 'Signed-sync plan data incorrect';end if;
 e:=public.loop_dispatch('claim','device',d,jsonb_build_object('request_key','final-'||gen_random_uuid(),'bank_reference','FINAL-QA'));claim_id:=(e->>'id')::uuid;if not exists(select 1 from public.loop_claims where id=claim_id and amount=200 and state='pending') then raise exception 'Final EFT claim not R200';end if;
 perform public.loop_dispatch('review','rollback-test',null,jsonb_build_object('id',claim_id,'decision','approve','receipt_reference','QA-'||gen_random_uuid(),'reason','Rollback settlement'));
 if not exists(select 1 from public.loop_devices where id=d and released=true and (policy->>'manual_lock')::boolean=false) then raise exception 'Final EFT did not finish plan';end if;
 if (public.loop_plan_summary(d)->>'approved_total')::int<>1700 then raise exception 'Incorrect total after final EFT';end if;
 -- Online path: stale R300 checkout may not over-credit a R200 balance.
 e:=public.loop_dispatch('enroll_create','rollback-test',null,jsonb_build_object('name','Online plan rollback fixture','identity','','phone','0000000000','reference','PLAN-QA-'||gen_random_uuid(),'consent',true,'identity_verified',false,'verification_note','Rolled back only','policy','{"manual_lock":true}'::jsonb,'enrollment_hash','test-'||gen_random_uuid()));online_device:=(e->>'device_id')::uuid;
 insert into public.loop_claims(device_id,request_key,amount,bank_reference,state,submitted) values(online_device,'seed',1500,'fixture-only','approved',t);
 stale_id:=gen_random_uuid();p:=jsonb_build_object('id',stale_id,'device_id',online_device,'state','pending','paylink_id','stale-'||stale_id,'amount_minor',30000,'currency','ZAR');insert into public.loop_config(key,value) values('ikpay-intent:'||stale_id,p);
 res:=public.loop_confirm_ikpay_payment(stale_id,p->>'paylink_id',30000,'ZAR');if res->>'state'<>'review_required' or (public.loop_plan_summary(online_device)->>'approved_total')::int<>1500 then raise exception 'Overpayment was incorrectly credited';end if;
 intent_id:=gen_random_uuid();p:=jsonb_build_object('id',intent_id,'device_id',online_device,'state','pending','paylink_id','final-'||intent_id,'amount_minor',20000,'currency','ZAR');insert into public.loop_config(key,value) values('ikpay-intent:'||intent_id,p);
 res:=public.loop_confirm_ikpay_payment(intent_id,p->>'paylink_id',20000,'ZAR');if res->>'state'<>'paid' then raise exception 'Final online payment not accepted';end if;
 res:=public.loop_confirm_ikpay_payment(intent_id,p->>'paylink_id',20000,'ZAR');if res->>'duplicate'<>'true' then raise exception 'Final duplicate not idempotent';end if;
 if not exists(select 1 from public.loop_devices where id=online_device and released=true) or (public.loop_plan_summary(online_device)->>'approved_total')::int<>1700 or (public.loop_plan_summary(online_device)->>'remaining_balance')::int<>0 then raise exception 'Final online payoff incorrect';end if;
end $$;
rollback;
