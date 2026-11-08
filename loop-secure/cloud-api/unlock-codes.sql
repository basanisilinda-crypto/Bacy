-- One-use, device-bound paid-access recovery. No payment or paid deadline is added.
create or replace function public.loop_issue_unlock_code(p_device uuid,p_hash text,p_actor text) returns jsonb language plpgsql security invoker set search_path='' as $$
declare d public.loop_devices%rowtype;c jsonb;claim uuid;total_paid integer;t bigint:=floor(extract(epoch from clock_timestamp()));start_at bigint;n integer;expires bigint;
begin
 if p_hash is null or p_hash !~ '^[a-f0-9]{64}$' then raise exception '422:Invalid code authorization';end if;
 select * into d from public.loop_devices where id=p_device for update;if not found or d.token is null then raise exception '409:Enroll this phone first';end if;
 select coalesce(sum(amount),0) into total_paid from public.loop_claims where device_id=p_device and state='approved';
 select id into claim from public.loop_claims where device_id=p_device and state='approved' order by coalesce(reviewed,submitted) desc,id desc limit 1;
 if claim is null or total_paid<=0 or (not d.released and d.due<=t) or (d.released and total_paid<1700) then raise exception '409:Approve the actual settled payment first; this account has no verified paid access to recover';end if;
 select value into c from public.loop_config where key='unlock-code:'||p_device::text for update;
 start_at:=coalesce((c->>'issue_window')::bigint,t);n:=coalesce((c->>'issued_count')::integer,0);if t-start_at>=3600 then start_at:=t;n:=0;end if;
 if n>=5 then raise exception '429:Too many recovery codes this hour; use the existing code or wait';end if;
 expires:=least(t+1800,case when d.released then t+1800 else d.due end);
 insert into public.loop_config(key,value) values('unlock-code:'||p_device::text,jsonb_build_object('hash',p_hash,'device_token',d.token,'revision',d.revision,'due',d.due,'released',d.released,'approved_claim',claim,'expires',expires,'attempts',0,'used',false,'issue_window',start_at,'issued_count',n+1)) on conflict(key) do update set value=excluded.value;
 insert into public.loop_audit(at,actor,action,subject,detail) values(t,p_actor,'unlock_code_created',p_device::text,'One-use paid-access recovery code created; expires within 30 minutes. No paid time added.');
 return jsonb_build_object('expires',expires,'device_id',p_device,'one_use',true,'requires_internet',true);
end $$;
revoke all on function public.loop_issue_unlock_code(uuid,text,text) from public,anon,authenticated;
grant execute on function public.loop_issue_unlock_code(uuid,text,text) to service_role;

create or replace function public.loop_redeem_unlock_code(p_device uuid,p_hash text,p_request text) returns jsonb language plpgsql security invoker set search_path='' as $$
declare d public.loop_devices%rowtype;c jsonb;t bigint:=floor(extract(epoch from clock_timestamp()));attempts integer;
begin
 if p_hash is null or p_hash !~ '^[a-f0-9]{64}$' or p_request is null or length(p_request)<16 or length(p_request)>100 then return jsonb_build_object('ok',false);end if;
 select * into d from public.loop_devices where id=p_device for update;if not found then return jsonb_build_object('ok',false);end if;
 select value into c from public.loop_config where key='unlock-code:'||p_device::text for update;
 if c is null or c->>'device_token' is distinct from d.token then return jsonb_build_object('ok',false);end if;
 if coalesce((c->>'used')::boolean,false) then return jsonb_build_object('ok',c->>'hash'=p_hash and c->>'request_key'=p_request,'duplicate',true);end if;
 attempts:=coalesce((c->>'attempts')::integer,0);
 if attempts>=5 or (c->>'expires')::bigint<=t then return jsonb_build_object('ok',false);end if;
 if c->>'hash' is distinct from p_hash then update public.loop_config set value=c||jsonb_build_object('attempts',attempts+1) where key='unlock-code:'||p_device::text;return jsonb_build_object('ok',false);end if;
 if (c->>'revision')::integer<>d.revision or (c->>'due')::bigint<>d.due or (c->>'released')::boolean<>d.released or (not d.released and d.due<=t) or not exists(select 1 from public.loop_claims where id=(c->>'approved_claim')::uuid and device_id=p_device and state='approved') then return jsonb_build_object('ok',false);end if;
 if not d.released then update public.loop_devices set policy=policy||'{"manual_lock":false,"grace_until":0}'::jsonb,revision=revision+1 where id=p_device;end if;
 update public.loop_config set value=c||jsonb_build_object('used',true,'used_at',t,'request_key',p_request) where key='unlock-code:'||p_device::text;
 insert into public.loop_audit(at,actor,action,subject,detail) values(t,'customer-code','unlock_code_redeemed',p_device::text,'Verified paid access recovered using a one-use code. No payment or paid deadline added.');
 return jsonb_build_object('ok',true,'duplicate',false);
end $$;
revoke all on function public.loop_redeem_unlock_code(uuid,text,text) from public,anon,authenticated;
grant execute on function public.loop_redeem_unlock_code(uuid,text,text) to service_role;
