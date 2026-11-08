-- Every fixture and code is rolled back. Run after temporary-unlock.sql and dispatch_current.sql.
begin;
do $$
declare
 e jsonb;d uuid;t bigint:=floor(extract(epoch from clock_timestamp()));r jsonb;
 before_due bigint;first_until bigint;first_started bigint;rev integer;n integer;
 request_one text:='temporary-request-00000001';
begin
 if has_function_privilege('anon','public.loop_issue_temporary_code(uuid,text,text)','execute')
    or has_function_privilege('authenticated','public.loop_issue_temporary_code(uuid,text,text)','execute')
    or has_function_privilege('anon','public.loop_redeem_temporary_code(uuid,text,text)','execute')
    or has_function_privilege('authenticated','public.loop_redeem_temporary_code(uuid,text,text)','execute') then
  raise exception 'Courtesy code RPC unexpectedly has public execution privileges';
 end if;
 e:=public.loop_dispatch('enroll_create','rollback-test',null,jsonb_build_object('name','Temporary access rollback fixture','identity','','phone','0000000000','reference','TEMP-QA-'||gen_random_uuid(),'consent',true,'identity_verified',false,'verification_note','Rolled back only','policy','{"manual_lock":true,"enforcement_enabled":true,"grace_hours":0,"banking_packages":[]}'::jsonb,'enrollment_hash','test-'||gen_random_uuid()));
 d:=(e->>'device_id')::uuid;
 update public.loop_devices set token='test-'||gen_random_uuid(),due=t-3600 where id=d;
 select due into before_due from public.loop_devices where id=d;
 if (select count(*) from public.loop_claims where device_id=d)<>0 then raise exception 'Courtesy fixture unexpectedly has a payment';end if;

 -- An unpaid but already-restricted phone can receive courtesy access.
 r:=public.loop_issue_temporary_code(d,repeat('a',64),'rollback-test');
 if (r->>'duration_seconds')::int<>300 or (r->>'one_use')::boolean is distinct from true
    or (r->>'requires_internet')::boolean is distinct from true
    or (r->>'expires')::bigint<t+1800 or (r->>'expires')::bigint>floor(extract(epoch from clock_timestamp()))+1800 then
  raise exception 'Incorrect courtesy issue duration or expiry';
 end if;
 if (public.loop_redeem_temporary_code(d,repeat('a',64),'short')->>'ok')::boolean then raise exception 'Malformed idempotency request accepted';end if;
 if (public.loop_redeem_temporary_code(gen_random_uuid(),repeat('a',64),request_one)->>'ok')::boolean then raise exception 'Wrong phone accepted';end if;
 if (public.loop_redeem_temporary_code(d,repeat('f',64),request_one)->>'ok')::boolean then raise exception 'Wrong code accepted';end if;
 if (select (value->>'attempts')::int from public.loop_config where key='temporary-code:'||d::text)<>1 then raise exception 'Wrong attempt was not counted';end if;
 r:=public.loop_redeem_temporary_code(d,repeat('a',64),request_one);
 if (r->>'ok')::boolean is distinct from true or (r->>'duplicate')::boolean is distinct from false then raise exception 'Valid courtesy code rejected';end if;
 select (policy->>'temporary_unlock_until')::bigint,(policy->>'temporary_unlock_started')::bigint,revision into first_until,first_started,rev from public.loop_devices where id=d;
 if first_until-first_started<>300 or (r->>'until')::bigint<>first_until then raise exception 'Courtesy access was not exactly 300 seconds';end if;
 if (select due from public.loop_devices where id=d)<>before_due
    or (select (policy->>'manual_lock')::boolean from public.loop_devices where id=d) is distinct from true
    or (select count(*) from public.loop_claims where device_id=d)<>0 then
  raise exception 'Courtesy access changed payment, deadline or original manual lock';
 end if;

 -- Retrying the same request does not advance either time or revision.
 r:=public.loop_redeem_temporary_code(d,repeat('a',64),request_one);
 if (r->>'ok')::boolean is distinct from true or (r->>'duplicate')::boolean is distinct from true
    or (select revision from public.loop_devices where id=d)<>rev
    or (select (policy->>'temporary_unlock_until')::bigint from public.loop_devices where id=d)<>first_until then
  raise exception 'Network retry extended or reissued courtesy access';
 end if;
 if (public.loop_redeem_temporary_code(d,repeat('a',64),'temporary-request-00000002')->>'ok')::boolean then raise exception 'Consumed courtesy code reused';end if;

 -- A new explicit lock cancels an active grant. Even a duplicate redemption cannot restore it.
 perform public.loop_dispatch('control','rollback-test',null,jsonb_build_object('id',d,'action','lock','request_key','temporary-control-'||gen_random_uuid(),'banking_packages','[]'::jsonb));
 if (select coalesce((policy->>'temporary_unlock_until')::bigint,0) from public.loop_devices where id=d)<>0 then raise exception 'New lock failed to revoke active courtesy grant';end if;
 select revision into rev from public.loop_devices where id=d;
 perform public.loop_redeem_temporary_code(d,repeat('a',64),request_one);
 if (select revision from public.loop_devices where id=d)<>rev
    or (select coalesce((policy->>'temporary_unlock_until')::bigint,0) from public.loop_devices where id=d)<>0 then
  raise exception 'Duplicate redemption bypassed a later lock';
 end if;

 -- An unused code also cannot override a newer control revision.
 perform public.loop_issue_temporary_code(d,repeat('b',64),'rollback-test');
 perform public.loop_dispatch('control','rollback-test',null,jsonb_build_object('id',d,'action','lock','request_key','temporary-control-'||gen_random_uuid(),'banking_packages','[]'::jsonb));
 if (public.loop_redeem_temporary_code(d,repeat('b',64),'temporary-request-00000003')->>'ok')::boolean then raise exception 'Unused code bypassed newer lock';end if;

 perform public.loop_issue_temporary_code(d,repeat('c',64),'rollback-test');
 update public.loop_config set value=value||jsonb_build_object('expires',t-1) where key='temporary-code:'||d::text;
 if (public.loop_redeem_temporary_code(d,repeat('c',64),'temporary-request-00000004')->>'ok')::boolean then raise exception 'Expired courtesy code accepted';end if;

 perform public.loop_issue_temporary_code(d,repeat('d',64),'rollback-test');
 for n in 1..5 loop perform public.loop_redeem_temporary_code(d,repeat('f',64),'temporary-request-00000005');end loop;
 if (public.loop_redeem_temporary_code(d,repeat('d',64),'temporary-request-00000006')->>'ok')::boolean then raise exception 'Five-attempt lockout bypassed';end if;
 perform public.loop_issue_temporary_code(d,repeat('e',64),'rollback-test');
 begin
  perform public.loop_issue_temporary_code(d,repeat('f',64),'rollback-test');
  raise exception 'Hourly courtesy code issue limit bypassed';
 exception when sqlstate 'P0001' then if sqlerrm not like '429:%' then raise;end if;end;

 -- Reset the fixture-only issue window, then check old-code replacement and token binding.
 update public.loop_config set value=value||jsonb_build_object('issue_window',t-3601) where key='temporary-code:'||d::text;
 perform public.loop_issue_temporary_code(d,repeat('1',64),'rollback-test');
 perform public.loop_issue_temporary_code(d,repeat('2',64),'rollback-test');
 if (public.loop_redeem_temporary_code(d,repeat('1',64),'temporary-request-00000007')->>'ok')::boolean then raise exception 'Replaced courtesy code accepted';end if;
 update public.loop_devices set token='replacement-'||gen_random_uuid() where id=d;
 if (public.loop_redeem_temporary_code(d,repeat('2',64),'temporary-request-00000008')->>'ok')::boolean then raise exception 'Code survived replacement device credentials';end if;

 -- An overdue finance deadline also qualifies without a manual lock.
 update public.loop_devices set policy=policy||'{"manual_lock":false,"grace_hours":0,"grace_until":0}'::jsonb where id=d;
 r:=public.loop_issue_temporary_code(d,repeat('4',64),'rollback-test');
 if (r->>'duration_seconds')::int<>300 then raise exception 'Overdue phone without manual lock was not eligible';end if;

 -- Active paid access, disabled enforcement, released and unenrolled phones cannot be granted courtesy codes.
 update public.loop_devices set due=t+86400,policy=policy||'{"manual_lock":false,"enforcement_enabled":true}'::jsonb where id=d;
 begin perform public.loop_issue_temporary_code(d,repeat('3',64),'rollback-test');raise exception 'Normally unlocked paid/grace account received courtesy code';exception when sqlstate 'P0001' then if sqlerrm not like '409:%' then raise;end if;end;
 update public.loop_devices set policy=policy||'{"manual_lock":true,"enforcement_enabled":false}'::jsonb where id=d;
 begin perform public.loop_issue_temporary_code(d,repeat('3',64),'rollback-test');raise exception 'Disabled enforcement received courtesy code';exception when sqlstate 'P0001' then if sqlerrm not like '409:%' then raise;end if;end;
 update public.loop_devices set released=true,policy=policy||'{"enforcement_enabled":true}'::jsonb where id=d;
 begin perform public.loop_issue_temporary_code(d,repeat('3',64),'rollback-test');raise exception 'Released phone received courtesy code';exception when sqlstate 'P0001' then if sqlerrm not like '409:%' then raise;end if;end;
 update public.loop_devices set released=false,token=null where id=d;
 begin perform public.loop_issue_temporary_code(d,repeat('3',64),'rollback-test');raise exception 'Unenrolled phone received courtesy code';exception when sqlstate 'P0001' then if sqlerrm not like '409:%' then raise;end if;end;
 if (select count(*) from public.loop_claims where device_id=d)<>0 then raise exception 'Courtesy flow created a financial ledger entry';end if;
end $$;
rollback;
