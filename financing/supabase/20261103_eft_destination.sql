-- EFT destination is entered privately by the finance manager in Supabase.
-- It must be VERIFIED before being made visible to customers.
create table if not exists public.eft_destinations (
  id smallint primary key default 1 check (id = 1),
  bank_name text not null check (char_length(bank_name) between 2 and 60),
  account_holder text not null check (char_length(account_holder) between 2 and 120),
  account_number text not null check (char_length(account_number) between 6 and 40),
  branch_code text,
  verified boolean not null default false,
  updated_at timestamptz not null default now()
);
alter table public.eft_destinations enable row level security;
drop policy if exists destination_verified_read on public.eft_destinations;
create policy destination_verified_read on public.eft_destinations
  for select to authenticated
  using (
    public.is_finance_manager()
    or (verified = true and exists (
      select 1 from public.finance_accounts a
      where a.customer_user_id = auth.uid()
    ))
  );
revoke all on public.eft_destinations from anon;
revoke insert, update, delete on public.eft_destinations from authenticated;
grant select on public.eft_destinations to authenticated;
-- Example of manual setup in private Supabase SQL editor:
-- insert into public.eft_destinations
--   (id, bank_name, account_holder, account_number, branch_code, verified)
-- values (1, 'Capitec', '<VERIFIED HOLDER>', '<VERIFIED ACCOUNT>', null, true)
-- on conflict (id) do update
-- set bank_name=excluded.bank_name, account_holder=excluded.account_holder,
--     account_number=excluded.account_number, branch_code=excluded.branch_code,
--     verified=excluded.verified, updated_at=now();
