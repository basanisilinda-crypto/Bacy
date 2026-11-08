begin;
do $$ declare e jsonb;d uuid;s jsonb;t bigint:=extract(epoch from now());begin
 e:=public.loop_dispatch('enroll_create','rollback-only',null,jsonb_build_object('name','Custom plan fixture','identity','','phone','0000000000','reference','CUSTOM-QA-'||gen_random_uuid(),'consent',true,'identity_verified',false,'verification_note','Rolled back','policy','{}'::jsonb,'enrollment_hash','qa-'||gen_random_uuid(),'contract_total',2400,'monthly_payment',700));d:=(e->>'device_id')::uuid;
 s:=public.loop_plan_summary(d);if (s->>'contract_total')::int<>2400 or (s->>'next_payment')::int<>700 or (s->>'payments_remaining')::int<>4 then raise exception 'Custom agreement not retained';end if;
 insert into public.loop_claims(device_id,request_key,amount,bank_reference,state,submitted) values(d,'custom-seed',2100,'rollback','approved',t);
 s:=public.loop_plan_summary(d);if (s->>'next_payment')::int<>300 or (s->>'remaining_balance')::int<>300 then raise exception 'Incorrect custom final amount';end if;
 if public.loop_finish_paid_plan(d) then raise exception 'Premature release at old threshold';end if;
 insert into public.loop_claims(device_id,request_key,amount,bank_reference,state,submitted) values(d,'custom-final',300,'rollback','approved',t);
 if not public.loop_finish_paid_plan(d) then raise exception 'Custom payoff did not release';end if;
 end; $$;
rollback;
