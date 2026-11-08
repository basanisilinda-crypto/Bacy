-- Retain accounting, financial idempotency and all customer/device records.
create or replace function public.loop_cleanup_expired() returns integer
language plpgsql security invoker set search_path='' as $$
declare removed integer;
begin
 delete from public.loop_config where
 (key like 'mobile_pair:%' or key like 'mobile_session:%' or key like 'unlock-code:%' or key like 'temporary-code:%')
 and case when value->>'expires' ~ '^[0-9]{1,12}$' then (value->>'expires')::bigint < extract(epoch from clock_timestamp())-86400 else false end;
 get diagnostics removed=row_count;
 return removed;
end;
$$;
revoke all on function public.loop_cleanup_expired() from public,anon,authenticated;
grant execute on function public.loop_cleanup_expired() to service_role;
