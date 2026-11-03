-- Manager-only operations for native BACY FLEX admin Android app.
-- Customer must already have a verified Supabase Auth identity and a signed agreement.
create or replace function public.create_finance_account(
  p_customer_email text, p_customer_name text, p_device_name text,
  p_total_cents integer, p_instalment_cents integer,
  p_signed_agreement_confirmed boolean
) returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  customer_uuid uuid;
  new_account uuid;
begin
  if not public.is_finance_manager() then raise exception 'Manager access required'; end if;
  if p_signed_agreement_confirmed is distinct from true then
    raise exception 'Verify customer agreement before registration';
  end if;
  if p_total_cents is null or p_total_cents < 100 or p_total_cents > 100000000
     or p_instalment_cents is null or p_instalment_cents < 100
     or p_instalment_cents > p_total_cents then
    raise exception 'Invalid finance amounts';
  end if;
  if char_length(btrim(p_customer_name)) not between 2 and 120 or
     char_length(btrim(p_device_name)) not between 2 and 120 then
    raise exception 'Customer name or device name is invalid';
  end if;
  select id into customer_uuid from auth.users
    where lower(email)=lower(btrim(p_customer_email))
      and email_confirmed_at is not null;
  if customer_uuid is null then raise exception 'Customer email is not registered and verified'; end if;
  insert into public.finance_accounts
    (customer_user_id, customer_name, device_name, total_cents,
      remaining_cents, instalment_cents, next_due_on)
  values (customer_uuid, btrim(p_customer_name), btrim(p_device_name),
    p_total_cents,p_total_cents,p_instalment_cents,current_date+30)
  returning id into new_account;
  return new_account;
exception
  when unique_violation then raise exception 'This customer already has a finance account';
end; $$;

create or replace function public.set_eft_destination(
  p_bank_name text, p_account_holder text,
  p_account_number text, p_branch_code text,
  p_verified boolean
) returns void
language plpgsql security definer set search_path = ''
as $$
begin
  if not public.is_finance_manager() then raise exception 'Manager access required'; end if;
  if char_length(btrim(p_bank_name)) not between 2 and 60 or
     char_length(btrim(p_account_holder)) not between 2 and 120 or
     char_length(btrim(p_account_number)) not between 6 and 40 or
     btrim(p_account_number) !~ '^[0-9]+$' then
    raise exception 'Bank destination details are invalid';
  end if;
  insert into public.eft_destinations
    (id,bank_name,account_holder,account_number,branch_code,verified)
  values (1,btrim(p_bank_name),btrim(p_account_holder),
    btrim(p_account_number),nullif(btrim(p_branch_code),''),p_verified)
  on conflict (id) do update set
    bank_name=excluded.bank_name,account_holder=excluded.account_holder,
    account_number=excluded.account_number,branch_code=excluded.branch_code,
    verified=excluded.verified,updated_at=now();
end; $$;

revoke all on function public.create_finance_account(text,text,text,integer,integer,boolean) from public;
revoke all on function public.set_eft_destination(text,text,text,text,boolean) from public;
grant execute on function public.create_finance_account(text,text,text,integer,integer,boolean) to authenticated;
grant execute on function public.set_eft_destination(text,text,text,text,boolean) to authenticated;
