-- Explicit rollback: no fixture account, code or financial change is committed.
begin;
do $$
declare e jsonb;d uuid;other uuid;t bigint:=floor(extract(epoch from clock_timestamp()));before_due bigint;rev integer;r jsonb;attempt integer;
begin
 e:=public.loop_dispatch('enroll_create','rollback-test',null,jsonb_build_object('name','Unlock rollback fixture','identity','','phone','0000000000','reference','UNLOCK-QA-'||gen_random_uuid(),'consent',true,'identity_verified',false,'verification_note','Rolled back only','policy','{"manual_lock":true,"enforcement_enabled":true}'::jsonb,'enrollment_hash','test-'||gen_random_uuid()));d:=(e->>'device_id')::uuid;
 update public.loop_devices set token='test-'||gen_random_uuid(),due=t+86400 where id=d;
 begin perform public.loop_issue_unlock_code(d,repeat('a',64),'rollback-test');raise exception 'Unpaid account received code';exception when sqlstate 'P0001' then if sqlerrm not like '409:%' then raise;end if;end;
 insert into public.loop_claims(device_id,request_key,amount,bank_reference,state,submitted,reviewed) values(d,'seed',300,'fixture-only','approved',t,t);
 select due into before_due from public.loop_devices where id=d;
 r:=public.loop_issue_unlock_code(d,repeat('a',64),'rollback-test');if (r->>'expires')::bigint>t+1800 or (r->>'one_use')::boolean is distinct from true then raise exception 'Invalid expiry/use policy';end if;
 if (public.loop_redeem_unlock_code(gen_random_uuid(),repeat('a',64),'request-000000000001')->>'ok')::boolean then raise exception 'Wrong phone accepted';end if;
 if (public.loop_redeem_unlock_code(d,repeat('f',64),'request-000000000001')->>'ok')::boolean then raise exception 'Wrong code accepted';end if;
 r:=public.loop_redeem_unlock_code(d,repeat('a',64),'request-000000000001');if (r->>'ok')::boolean is distinct from true then raise exception 'Correct code rejected';end if;
 select revision into rev from public.loop_devices where id=d;
 if exists(select 1 from public.loop_devices where id=d and ((policy->>'manual_lock')::boolean or due<>before_due)) or (public.loop_plan_summary(d)->>'approved_total')::int<>300 then raise exception 'Unlock changed paid time/amount or failed to clear lock';end if;
 r:=public.loop_redeem_unlock_code(d,repeat('a',64),'request-000000000001');if (r->>'ok')::boolean is distinct from true or (r->>'duplicate')::boolean is distinct from true or (select revision from public.loop_devices where id=d)<>rev then raise exception 'Network retry was not idempotent';end if;
 if (public.loop_redeem_unlock_code(d,repeat('a',64),'request-000000000002')->>'ok')::boolean then raise exception 'Consumed code reused';end if;
 perform public.loop_issue_unlock_code(d,repeat('b',64),'rollback-test');update public.loop_devices set revision=revision+1,policy=policy||'{"manual_lock":true}'::jsonb where id=d;
 if (public.loop_redeem_unlock_code(d,repeat('b',64),'request-000000000003')->>'ok')::boolean then raise exception 'Code bypassed a newer lock';end if;
 perform public.loop_issue_unlock_code(d,repeat('c',64),'rollback-test');update public.loop_config set value=value||jsonb_build_object('expires',t-1) where key='unlock-code:'||d::text;
 if (public.loop_redeem_unlock_code(d,repeat('c',64),'request-000000000004')->>'ok')::boolean then raise exception 'Expired code accepted';end if;
 perform public.loop_issue_unlock_code(d,repeat('d',64),'rollback-test');for attempt in 1..5 loop perform public.loop_redeem_unlock_code(d,repeat('f',64),'request-000000000005');end loop;
 if (public.loop_redeem_unlock_code(d,repeat('d',64),'request-000000000006')->>'ok')::boolean then raise exception 'Attempt lockout bypassed';end if;
 perform public.loop_issue_unlock_code(d,repeat('e',64),'rollback-test');
 if (public.loop_redeem_unlock_code(d,repeat('d',64),'request-000000000007')->>'ok')::boolean then raise exception 'Replaced code accepted';end if;
 begin perform public.loop_issue_unlock_code(d,repeat('f',64),'rollback-test');raise exception 'Issue limit bypassed';exception when sqlstate 'P0001' then if sqlerrm not like '429:%' then raise;end if;end;
 if (select count(*) from public.loop_claims where device_id=d)<>1 or (select due from public.loop_devices where id=d)<>before_due then raise exception 'Recovery added payment records or days';end if;
end $$;
rollback;
