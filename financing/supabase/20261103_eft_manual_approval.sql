-- BACY FLEX: live EFT claims and manual approval.
-- Apply in Supabase SQL editor after creating a project. NO customer or bank account data belongs in Git.
create extension if not exists pgcrypto;

create table if not exists public.finance_managers (
  user_id uuid primary key references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

create table if not exists public.finance_accounts (
  id uuid primary key default gen_random_uuid(),
  customer_user_id uuid not null unique references auth.users(id) on delete restrict,
  customer_name text not null check (char_length(trim(customer_name)) between 2 and 120),
  device_name text not null default 'Android phone',
  total_cents integer not null check (total_cents > 0),
  remaining_cents integer not null check (remaining_cents >= 0 and remaining_cents <= total_cents),
  instalment_cents integer not null check (instalment_cents > 0),
  next_due_on date,
  access_valid_until date,
  status text not null default 'active' check (status in ('active','paid_in_full')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint paid_in_full_consistent check (
    (status = 'paid_in_full' and remaining_cents = 0 and next_due_on is null)
    or (status = 'active' and remaining_cents > 0 and next_due_on is not null)
  )
);

create table if not exists public.eft_claims (
  id uuid primary key default gen_random_uuid(),
  account_id uuid not null references public.finance_accounts(id) on delete restrict,
  submitted_by uuid not null references auth.users(id) on delete restrict,
  reference text not null check (char_length(reference) between 4 and 80),
  amount_cents integer not null check (amount_cents > 0),
  state text not null default 'pending' check (state in ('pending','approved','rejected')),
  created_at timestamptz not null default now(),
  decided_at timestamptz,
  decided_by uuid references auth.users(id),
  decision_note text,
  unique (account_id, reference)
);
create index if not exists eft_claims_pending_idx on public.eft_claims(state, created_at desc);

alter table public.finance_managers enable row level security;
alter table public.finance_accounts enable row level security;
alter table public.eft_claims enable row level security;

create or replace function public.is_finance_manager()
returns boolean language sql stable security definer set search_path = ''
as $$ select exists(select 1 from public.finance_managers where user_id = auth.uid()); $$;
revoke all on function public.is_finance_manager() from public;
grant execute on function public.is_finance_manager() to authenticated;

drop policy if exists finance_manager_self on public.finance_managers;
create policy finance_manager_self on public.finance_managers
for select to authenticated using (user_id = auth.uid());

drop policy if exists finance_customer_read on public.finance_accounts;
create policy finance_customer_read on public.finance_accounts
for select to authenticated
using (customer_user_id = auth.uid() or public.is_finance_manager());

drop policy if exists eft_claim_read on public.eft_claims;
create policy eft_claim_read on public.eft_claims
for select to authenticated
using (submitted_by = auth.uid() or public.is_finance_manager());

-- Mutations are *only* exposed through two checked, server-side SQL functions.
revoke all on public.finance_managers, public.finance_accounts, public.eft_claims from anon;
revoke insert, update, delete on public.finance_managers, public.finance_accounts, public.eft_claims from authenticated;
grant select on public.finance_managers, public.finance_accounts, public.eft_claims to authenticated;

create or replace function public.submit_eft_claim(
  p_account_id uuid, p_reference text, p_amount_cents integer
) returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  a public.finance_accounts%rowtype;
  claim_id uuid;
  clean_ref text := btrim(p_reference);
begin
  if auth.uid() is null then raise exception 'Sign in is required'; end if;
  select * into a from public.finance_accounts
    where id = p_account_id and customer_user_id = auth.uid() and status = 'active';
  if not found then raise exception 'Finance account not found'; end if;
  if clean_ref is null or char_length(clean_ref) < 4 or char_length(clean_ref) > 80 or
     clean_ref ~ '[[:cntrl:]]' then
    raise exception 'Enter a valid EFT reference (4-80 characters)';
  end if;
  if p_amount_cents is null or p_amount_cents != least(a.instalment_cents, a.remaining_cents) then
    raise exception 'Amount must equal your scheduled instalment';
  end if;
  insert into public.eft_claims(account_id, submitted_by, reference, amount_cents)
  values (p_account_id, auth.uid(), clean_ref, p_amount_cents)
  returning id into claim_id;
  return claim_id;
exception
  when unique_violation then raise exception 'This reference has already been submitted';
end; $$;

create or replace function public.approve_eft_claim(p_claim_id uuid)
returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  c public.eft_claims%rowtype;
  a public.finance_accounts%rowtype;
  remaining integer;
  through_date date;
begin
  if not public.is_finance_manager() then raise exception 'Manager access required'; end if;
  select * into c from public.eft_claims where id=p_claim_id for update;
  if not found then raise exception 'Payment claim not found'; end if;
  if c.state != 'pending' then raise exception 'Claim already decided'; end if;
  select * into a from public.finance_accounts where id=c.account_id for update;
  if a.status != 'active' or c.amount_cents > a.remaining_cents then
    raise exception 'Account is paid or amount exceeds outstanding balance';
  end if;
  remaining := a.remaining_cents - c.amount_cents;
  through_date := current_date + 30;
  update public.finance_accounts set
    remaining_cents = remaining,
    access_valid_until = case when remaining=0 then null else through_date end,
    next_due_on = case when remaining=0 then null else through_date end,
    status = case when remaining=0 then 'paid_in_full' else 'active' end,
    updated_at = now()
  where id = a.id;
  update public.eft_claims set
    state='approved', decided_at=now(), decided_by=auth.uid()
  where id=c.id;
  return jsonb_build_object('account_id', a.id, 'remaining_cents', remaining,
    'paid_in_full', remaining=0, 'next_due_on',
    case when remaining=0 then null else through_date::text end);
end; $$;

create or replace function public.reject_eft_claim(p_claim_id uuid, p_reason text default null)
returns void
language plpgsql security definer set search_path = ''
as $$
begin
  if not public.is_finance_manager() then raise exception 'Manager access required'; end if;
  update public.eft_claims set state='rejected', decided_at=now(),
    decided_by=auth.uid(), decision_note=left(p_reason,200)
  where id=p_claim_id and state='pending';
  if not found then raise exception 'Pending claim not found'; end if;
end; $$;

revoke all on function public.submit_eft_claim(uuid,text,integer) from public;
revoke all on function public.approve_eft_claim(uuid) from public;
revoke all on function public.reject_eft_claim(uuid,text) from public;
grant execute on function public.submit_eft_claim(uuid,text,integer) to authenticated;
grant execute on function public.approve_eft_claim(uuid) to authenticated;
grant execute on function public.reject_eft_claim(uuid,text) to authenticated;

-- Onboarding is an *administrator-only manual SQL operation* at this stage:
-- INSERT INTO public.finance_managers(user_id) VALUES ('<YOUR_AUTH_USER_UUID>');
-- INSERT INTO public.finance_accounts
--   (customer_user_id, customer_name, device_name, total_cents,
--    remaining_cents, instalment_cents, next_due_on)
-- VALUES ('<CUSTOMER_AUTH_USER_UUID>', 'Customer', 'Android',
--         170000, 170000, 30000, current_date + 30);
-- Never paste a bank account number or service_role key into this file.
