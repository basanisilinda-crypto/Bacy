-- Administrative courtesy access is separate from paid access and creates no payment.
create or replace function public.loop_issue_temporary_code(p_device uuid,p_hash text,p_actor text) returns jsonb language plpgsql security invoker set search_path='' as $$
declare d public.loop_devices%rowtype;c jsonb;t bigint:=floor(extract(epoch from clock_timestamp()));start_at bigint;n integer;
begin
 if p_hash is null or p_hash !~ '^[a-f0-9]{64}$' then raise exception '422:Invalid code authorization';end if;
 select * into d from public.loop_devices where id=p_device for update;
 if not found or d.token is null or d.released then raise exception '409:An active enrolled finance phone is required';end if;
 if not coalesce((d.policy->>'enforcement_enabled')::boolean,false) then raise exception '409:This phone has no enabled finance restriction';end if;
 if not coalesce((d.policy->>'manual_lock')::boolean,false) and t<greatest(d.due+coalesce((d.policy->>'grace_hours')::integer,24)*3600,coalesce((d.policy->>'grace_until')::bigint,0)) then raise exception '409:This phone already has normal paid or grace access';end if;
 select value into c from public.loop_config where key='temporary-code:'||p_device::text for update;
 start_at:=coalesce((c->>'issue_window')::bigint,t);n:=coalesce((c->>'issued_count')::integer,0);if t-start_at>=3600 then start_at:=t;n:=0;end if;
 if n>=5 then raise exception '429:Too many temporary codes this hour';end if;
 insert into public.loop_config(key,value) values('temporary-code:'||p_device::text,jsonb_build_object('hash',p_hash,'device_token',d.token,'revision',d.revision,'due',d.due,'expires',t+1800,'attempts',0,'used',false,'issue_window',start_at,'issued_count',n+1)) on conflict(key) do update set value=excluded.value;
 insert into public.loop_audit(at,actor,action,subject,detail) values(t,p_actor,'temporary_code_created',p_device::text,'One-use code for five minutes of courtesy access. No payment or paid deadline added.');
 return jsonb_build_object('expires',t+1800,'duration_seconds',300,'one_use',true,'requires_internet',true,'device_id',p_device);
end $$;
revoke all on function public.loop_issue_temporary_code(uuid,text,text) from public,anon,authenticated;
grant execute on function public.loop_issue_temporary_code(uuid,text,text) to service_role;

create or replace function public.loop_redeem_temporary_code(p_device uuid,p_hash text,p_request text) returns jsonb language plpgsql security invoker set search_path='' as $$
declare d public.loop_devices%rowtype;c jsonb;t bigint:=floor(extract(epoch from clock_timestamp()));attempts integer;
begin
 if p_hash is null or p_hash !~ '^[a-f0-9]{64}$' or p_request is null or length(p_request)<16 or length(p_request)>100 then return jsonb_build_object('ok',false);end if;
 select * into d from public.loop_devices where id=p_device for update;if not found or d.released then return jsonb_build_object('ok',false);end if;
 select value into c from public.loop_config where key='temporary-code:'||p_device::text for update;
 if c is null or c->>'device_token' is distinct from d.token then return jsonb_build_object('ok',false);end if;
 if coalesce((c->>'used')::boolean,false) then return jsonb_build_object('ok',c->>'hash'=p_hash and c->>'request_key'=p_request,'duplicate',true);end if;
 attempts:=coalesce((c->>'attempts')::integer,0);
 if attempts>=5 or (c->>'expires')::bigint<=t then return jsonb_build_object('ok',false);end if;
 if c->>'hash' is distinct from p_hash then update public.loop_config set value=c||jsonb_build_object('attempts',attempts+1) where key='temporary-code:'||p_device::text;return jsonb_build_object('ok',false);end if;
 if (c->>'revision')::integer<>d.revision or (c->>'due')::bigint<>d.due or not coalesce((d.policy->>'enforcement_enabled')::boolean,false) then return jsonb_build_object('ok',false);end if;
 update public.loop_devices set policy=policy||jsonb_build_object('temporary_unlock_started',t,'temporary_unlock_until',t+300),revision=revision+1 where id=p_device;
 update public.loop_config set value=c||jsonb_build_object('used',true,'used_at',t,'request_key',p_request) where key='temporary-code:'||p_device::text;
 insert into public.loop_audit(at,actor,action,subject,detail) values(t,'customer-code','temporary_code_redeemed',p_device::text,'Five-minute courtesy access started. Original payment deadline and finance lock retained.');
 return jsonb_build_object('ok',true,'duplicate',false,'duration_seconds',300,'until',t+300);
end $$;
revoke all on function public.loop_redeem_temporary_code(uuid,text,text) from public,anon,authenticated;
grant execute on function public.loop_redeem_temporary_code(uuid,text,text) to service_role;

-- Preserve the live dispatcher while adding revocation to explicit admin controls.
do $patch$
declare definition text;old_part text:='''enforcement_enabled'',true,''manual_lock'',p_data->>''action''=''lock''';new_part text:='''temporary_unlock_until'',0,''temporary_unlock_started'',0,'||old_part;
begin
 select pg_get_functiondef('public.loop_dispatch(text,text,uuid,jsonb)'::regprocedure) into definition;
 if position(new_part in definition)>0 then return;end if;
 if position(old_part in definition)=0 then raise exception 'Dispatcher control shape changed; review before applying courtesy access';end if;
 execute replace(definition,old_part,new_part);
end $patch$;
