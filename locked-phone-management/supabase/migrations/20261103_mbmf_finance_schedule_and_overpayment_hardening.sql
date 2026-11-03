-- MBMF: source mirror of live Supabase migrations applied 2026-11-03.
-- Existing finance_contracts and finance_overpayments had zero rows when applied.
-- This script is intended for a new environment and has NOT been independently tested there.
-- Apply in the recorded order, not as an ad hoc repeated hotfix.

alter table public.finance_contracts
  add column if not exists first_due_date date,
  add column if not exists repayment_cadence text not null default 'thirty_day';
update public.finance_contracts set first_due_date=next_due_date where first_due_date is null;
alter table public.finance_contracts
  add constraint finance_contracts_repayment_cadence_chk
  check (repayment_cadence in ('thirty_day','calendar_month'));

create table if not exists public.finance_overpayments (
  claim_id uuid primary key references public.finance_contract_claims(id),
  contract_id uuid not null references public.finance_contracts(id),
  amount_cents bigint not null check(amount_cents>0),
  refund_status text not null default 'pending_review'
    check(refund_status in ('pending_review','verified_refunded')),
  recorded_at timestamptz not null default now()
);
alter table public.finance_overpayments enable row level security;
revoke all on public.finance_overpayments from anon,authenticated;
grant select on public.finance_overpayments to authenticated;
create policy "admins read finance overpayments"
 on public.finance_overpayments for select to authenticated
 using (exists (select 1 from public.finance_admins a where a.user_id=(select auth.uid())));

-- The two live privileged functions below were fetched from pg_get_functiondef.
-- Every admin RPC checks the authenticated ID against finance_admins.

CREATE OR REPLACE FUNCTION public.finance_decide_contract_claim(p_claim_id uuid, p_approve boolean)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
 v_actor uuid:=auth.uid();
 v_claim public.finance_contract_claims%rowtype;
 v_contract public.finance_contracts%rowtype;
 v_balance bigint; v_due date;
 v_paid bigint; v_completed bigint; v_overpay bigint:=0;
 v_anchor date;
begin
 if v_actor is null or not exists(select 1 from public.finance_admins a where a.user_id=v_actor) then
   raise exception 'Administrator authorization required' using errcode='42501';
 end if;
 if p_approve is null then raise exception 'Decision required'; end if;
 select * into v_claim from public.finance_contract_claims where id=p_claim_id for update;
 if not found or v_claim.status<>'pending' then raise exception 'Claim not pending'; end if;
 select * into v_contract from public.finance_contracts where id=v_claim.contract_id for update;
 if not found then raise exception 'Finance contract missing'; end if;
 v_balance:=v_contract.balance_cents;
 v_due:=v_contract.next_due_date;
 if p_approve then
   if v_contract.status='settled' then raise exception 'Contract already settled'; end if;
   v_overpay:=greatest(0::bigint,v_claim.amount_cents-v_balance);
   v_balance:=greatest(0::bigint,v_balance-v_claim.amount_cents);
   if v_balance=0 then
     v_due:=null;
   else
     v_paid:=v_contract.price_cents-v_contract.deposit_cents-v_balance;
     v_completed:=v_paid / v_contract.installment_cents;
     v_anchor:=coalesce(v_contract.first_due_date,v_contract.next_due_date);
     if v_contract.repayment_cadence='calendar_month' then
       v_due:=(v_anchor+make_interval(months=>v_completed::integer))::date;
     else
       v_due:=v_anchor+(v_completed::integer*30);
     end if;
   end if;
   update public.finance_contracts
     set balance_cents=v_balance,next_due_date=v_due,
       status=case when v_balance=0 then 'settled' else 'active' end
     where id=v_contract.id;
   if v_overpay>0 then
     insert into public.finance_overpayments(claim_id,contract_id,amount_cents)
       values(p_claim_id,v_contract.id,v_overpay);
   end if;
 end if;
 update public.finance_contract_claims
   set status=case when p_approve then 'approved' else 'rejected' end,
       decided_at=now(), decided_by=v_actor
   where id=p_claim_id;
 insert into public.finance_audit(actor_id,event_type,target_id)
 values(v_actor,case when p_approve then 'contract_eft_approved' else 'contract_eft_rejected' end,p_claim_id);
 return jsonb_build_object('claim_id',p_claim_id,
   'status',case when p_approve then 'approved' else 'rejected' end,
   'balance_cents',v_balance,'next_due_date',v_due,'overpayment_cents',v_overpay,
   'release_state',case when v_balance=0 and p_approve then 'awaiting_device_ack' else 'not_requested' end);
end $function$;

CREATE OR REPLACE FUNCTION public.finance_register_contract(p_customer_name text, p_customer_phone text, p_device_model text, p_price_cents bigint, p_deposit_cents bigint, p_installment_cents bigint, p_first_due_date date)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_actor uuid := auth.uid(); v_token text; v_id uuid; v_balance bigint;
begin
 if v_actor is null or not exists(select 1 from public.finance_admins a where a.user_id=v_actor) then
   raise exception 'Administrator authorization required' using errcode='42501';
 end if;
 if p_customer_name is null or char_length(btrim(p_customer_name)) not between 2 and 120
 or p_customer_phone is null or char_length(btrim(p_customer_phone)) not between 7 and 25
 or p_device_model is null or char_length(btrim(p_device_model)) not between 2 and 120
 or p_price_cents is null or p_price_cents<=0 or p_price_cents>10000000000
 or p_deposit_cents is null or p_deposit_cents<0 or p_deposit_cents>=p_price_cents
 or p_installment_cents is null or p_installment_cents<=0
 or p_first_due_date is null then
   raise exception 'Invalid finance contract details';
 end if;
 v_balance := p_price_cents-p_deposit_cents;
 if (v_balance-1)/p_installment_cents >= 1200 then
   raise exception 'Repayment schedule too long';
 end if;
 v_token := encode(extensions.gen_random_bytes(32),'hex');
 insert into public.finance_contracts(
 customer_name,customer_phone,device_model,price_cents,deposit_cents,
 installment_cents,balance_cents,first_due_date,next_due_date,status,
 created_by,enrollment_digest,enrollment_expires_at)
 values (btrim(p_customer_name),btrim(p_customer_phone),btrim(p_device_model),
 p_price_cents,p_deposit_cents,p_installment_cents,v_balance,p_first_due_date,p_first_due_date,
 'pending',v_actor,extensions.digest(v_token,'sha256'),now()+interval '24 hours')
 returning id into v_id;
 insert into public.finance_audit(actor_id,event_type,target_id)
 values(v_actor,'admin_finance_contract_created',v_id);
 return jsonb_build_object('contract_id',v_id,'enrollment_code',v_token,
 'expires_at',now()+interval '24 hours');
end $function$;
