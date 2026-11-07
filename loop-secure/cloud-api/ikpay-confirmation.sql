-- Additive, isolated Loop Secure payment confirmation. Existing finance data is retained.
create or replace function public.loop_confirm_ikpay_payment(p_id uuid,p_paylink text,p_amount integer,p_currency text)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare
 intent jsonb; d public.loop_devices%rowtype; claim_id uuid;
 t bigint:=floor(extract(epoch from clock_timestamp()));
begin
 select value into intent from public.loop_config where key='ikpay-intent:'||p_id::text for update;
 if not found then raise exception '404:Payment intent not found';end if;
 if intent->>'paylink_id' is distinct from p_paylink or (intent->>'amount_minor')::integer is distinct from p_amount or intent->>'currency' is distinct from p_currency or p_amount is distinct from 30000 or p_currency is distinct from 'ZAR' then raise exception '409:Payment does not match the intended account and amount';end if;
 if intent->>'state' in ('paid','review_required') then return jsonb_build_object('state',intent->>'state','duplicate',true);end if;
 if intent->>'state' is distinct from 'pending' then raise exception '409:Payment is not ready for confirmation';end if;
 select * into d from public.loop_devices where id=(intent->>'device_id')::uuid for update;
 if not found then raise exception '404:Customer device not found';end if;
 if d.released then
  update public.loop_config set value=intent||jsonb_build_object('state','review_required','confirmed_at',t,'note','Payment arrived after permanent payoff release; review settlement/refund with the provider') where key='ikpay-intent:'||p_id::text;
  insert into public.loop_audit(at,actor,action,subject,detail) values(t,'ikpay-verified','payment_review_required',d.id::text,'Verified payment received after permanent release');
  return jsonb_build_object('state','review_required');
 end if;
 insert into public.loop_claims(device_id,request_key,amount,bank_reference,state,submitted,reviewed,reviewer,reason,receipt_reference)
 values(d.id,'ikpay:'||p_id::text,p_amount/100,'iK Pay '||p_paylink,'approved',t,t,'ikpay-verified','Payment verified directly with iKhokha','ikpay:'||p_paylink) returning id into claim_id;
 update public.loop_devices set due=greatest(due,t)+2592000,policy=policy||'{"manual_lock":false,"grace_until":0}'::jsonb,revision=revision+1 where id=d.id;
 update public.loop_config set value=intent||jsonb_build_object('state','paid','confirmed_at',t,'claim_id',claim_id) where key='ikpay-intent:'||p_id::text;
 insert into public.loop_audit(at,actor,action,subject,detail) values(t,'ikpay-verified','payment_auto_confirmed',d.id::text,'Verified iK Pay receipt: '||p_paylink);
 return jsonb_build_object('state','paid','device_delivery','pending_next_sync');
end;
$$;
revoke all on function public.loop_confirm_ikpay_payment(uuid,text,integer,text) from public,anon,authenticated;
grant execute on function public.loop_confirm_ikpay_payment(uuid,text,integer,text) to service_role;
create index if not exists loop_ikpay_pending_device on public.loop_config ((value->>'device_id')) where key like 'ikpay-intent:%' and value->>'state'='pending';
