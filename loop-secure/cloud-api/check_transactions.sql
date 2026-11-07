-- This block rolls back all fixture writes before returning, including on failure.
do $$
declare
 enrollment jsonb; other jsonb; state jsonb; result jsonb; claim jsonb;
 device_id uuid; other_id uuid; claim_id uuid; delivery_id bigint;enrollment_hash text; paid bigint; revision integer;
 p jsonb:='{"enforcement_enabled":false,"block_factory_reset":false,"block_uninstall":false,"block_wifi_changes":false,"banking_packages":[],"grace_hours":24,"update_window_start":120,"update_window_end":240}'::jsonb;
begin
 begin
  enrollment:=public.loop_dispatch('enroll_create','transaction-test',null,jsonb_build_object('name','Transactional validation only','identity','encrypted-fixture','phone','0000000000','reference','TX-TEST-'||gen_random_uuid(),'consent',true,'identity_verified',false,'verification_note','Fixture rolled back within transaction','policy',p,'enrollment_hash','test-one-'||gen_random_uuid()));
  device_id:=(enrollment->>'device_id')::uuid;
  if (select c.verified from public.loop_customers c join public.loop_devices dv on dv.customer_id=c.id where dv.id=device_id) then raise exception 'Name and phone enrollment must not imply identity verification';end if;
  perform public.loop_dispatch('enroll_reissue','transaction-test',null,jsonb_build_object('id',device_id,'enrollment_hash','refreshed-code-'||device_id,'policy',p));
  select dv.enrollment into enrollment_hash from public.loop_devices dv where dv.id=device_id;
  result:=public.loop_dispatch('enroll_exchange','transaction-test',null,jsonb_build_object('enrollment_hash',enrollment_hash,'model','Transaction fixture','token_hash','test-hash-'||device_id,'identifiers','encrypted-fixture','request_key','retry-'||device_id,'consent_source','administrator_recorded'));
  result:=public.loop_dispatch('enroll_exchange','transaction-test',null,jsonb_build_object('enrollment_hash',enrollment_hash,'model','Transaction fixture','token_hash','test-hash-'||device_id,'identifiers','encrypted-fixture','request_key','retry-'||device_id,'consent_source','administrator_recorded'));
  if (result->>'device_id')::uuid<>device_id then raise exception 'Activation retry returned another device';end if;
  state:=public.loop_dispatch('sync','device',device_id,'{"revision":0,"status":"unsupported"}');
  paid:=(state->>'due')::bigint;
  result:=public.loop_dispatch('control','transaction-test',null,jsonb_build_object('id',device_id,'action','lock','request_key','control-lock-'||device_id,'banking_packages',jsonb_build_array('capitec.acuity.mobile.prod')));
  state:=public.loop_dispatch('sync','device',device_id,'{"revision":0,"status":"unsupported"}');
  revision:=(state->>'revision')::integer;
  if not (state->'policy'->>'manual_lock')::boolean then raise exception 'Manual lock missing from signed policy';end if;
  perform public.loop_dispatch('control','transaction-test',null,jsonb_build_object('id',device_id,'action','lock','request_key','control-lock-'||device_id,'banking_packages',jsonb_build_array('capitec.acuity.mobile.prod')));
  if (select dv.revision from public.loop_devices dv where dv.id=device_id)<>revision then raise exception 'Retry changed revision';end if;
  update public.loop_devices set due=extract(epoch from now())::bigint-172800 where id=device_id;
  begin
   perform public.loop_dispatch('control','transaction-test',null,jsonb_build_object('id',device_id,'action','unlock','request_key','control-unlock-denied-'||device_id,'banking_packages',jsonb_build_array('capitec.acuity.mobile.prod')));
   raise exception 'Unpaid unlock unexpectedly succeeded';
  exception when sqlstate 'P0001' then if sqlerrm not like '409:%' then raise;end if;end;
  perform public.loop_dispatch('control','transaction-test',null,jsonb_build_object('id',device_id,'action','grace','request_key','control-grace-'||device_id,'banking_packages',jsonb_build_array('capitec.acuity.mobile.prod')));
  if (select (dv.policy->>'grace_until')::bigint from public.loop_devices dv where dv.id=device_id)<>extract(epoch from now())::bigint+86400 then raise exception 'Grace must be 24 hours';end if;
  perform public.loop_dispatch('control','transaction-test',null,jsonb_build_object('id',device_id,'action','unlock','request_key','control-unlock-grace-'||device_id,'banking_packages',jsonb_build_array('capitec.acuity.mobile.prod')));
  update public.loop_devices set due=paid where id=device_id;
  perform public.loop_dispatch('control','transaction-test',null,jsonb_build_object('id',device_id,'action','lock','request_key','control-before-approval-'||device_id,'banking_packages',jsonb_build_array('capitec.acuity.mobile.prod')));
  claim:=public.loop_dispatch('claim','device',device_id,'{"request_key":"transaction-test-request","bank_reference":"transaction-test-reference"}');
  claim_id:=(claim->>'id')::uuid;
  result:=public.loop_dispatch('claim','device',device_id,'{"request_key":"transaction-test-retry","bank_reference":"transaction-test-reference"}');
  if result->>'id'<>claim->>'id' then raise exception 'Duplicate pending claim was not idempotent';end if;
  perform public.loop_dispatch('review','transaction-test',null,jsonb_build_object('id',claim_id,'decision','approve','reason','Settled fixture in rolled-back test','receipt_reference','TX-'||gen_random_uuid()));
  state:=public.loop_dispatch('sync','device',device_id,'{"revision":1,"status":"unsupported"}');
  if coalesce((state->'policy'->>'manual_lock')::boolean,true) then raise exception 'Approval must clear manual lock';end if;
  if (state->>'due')::bigint<>paid+2592000 then raise exception 'Approval did not grant exactly 30 days';end if;
  begin
   perform public.loop_dispatch('review','transaction-test',null,jsonb_build_object('id',claim_id,'decision','approve','reason','Duplicate review rejected','receipt_reference','TX-'||gen_random_uuid()));
   raise exception 'Duplicate review unexpectedly succeeded';
  exception when sqlstate 'P0001' then if sqlerrm not like '409:%' then raise;end if;end;
  result:=public.loop_dispatch('message','transaction-test',null,jsonb_build_object('request_key','tx-message-'||gen_random_uuid(),'title','Transaction validation','message','This message exists only within a rolled-back transaction','all_active',false,'device_ids',jsonb_build_array(device_id)));
  state:=public.loop_dispatch('sync','device',device_id,'{"revision":1,"status":"unsupported"}');
  if jsonb_array_length(state->'messages')<>1 then raise exception 'Message not in signed payload source';end if;
  delivery_id:=(state->'messages'->0->>'id')::bigint;
  perform public.loop_dispatch('receipt','device',device_id,jsonb_build_object('ids',jsonb_build_array(delivery_id),'state','read'));
  if not exists(select 1 from public.loop_deliveries where id=delivery_id and read_at is not null and delivered is not null) then raise exception 'Read receipt missing';end if;
  revision:=(state->>'revision')::integer;
  result:=public.loop_dispatch('fleet','transaction-test',null,jsonb_build_object('request_key','tx-fleet-'||gen_random_uuid(),'policy',p||'{"grace_hours":48}'::jsonb,'apply_to_all_active',true));
  state:=public.loop_dispatch('sync','device',device_id,jsonb_build_object('revision',revision,'status','unsupported'));
  if (state->'policy'->>'grace_hours')::integer<>48 then raise exception 'Fleet policy not applied';end if;
  perform public.loop_dispatch('banking_put','transaction-test',null,'{"bank":"Transaction validation","account_holder":"Rollback fixture","account_number":"0000000000","branch_code":"000000","details_verified":true,"verification_note":"Only inside this rolled-back transaction"}');
  perform public.loop_dispatch('support','device',device_id,'{"message":"Transactional support validation"}');
  perform public.loop_dispatch('identifiers','transaction-test',null,jsonb_build_object('id',device_id));
  result:=public.loop_dispatch('release','transaction-test',null,jsonb_build_object('id',device_id,'reason','Full-payoff transaction validation','full_payoff_confirmed',true));
  state:=public.loop_dispatch('sync','device',device_id,jsonb_build_object('revision',revision,'status','unsupported'));
  if state->>'released'<>'true' then raise exception 'Release not permanent';end if;
  perform public.loop_dispatch('overview');
  raise exception 'LOOP_TRANSACTION_CHECK_PASSED_ROLLBACK';
 exception when sqlstate 'P0001' then
  if sqlerrm<>'LOOP_TRANSACTION_CHECK_PASSED_ROLLBACK' then raise;end if;
  raise notice 'Transaction checks passed; all test fixture writes rolled back';
 end;
end $$;
select count(*) as committed_customers from public.loop_customers;
