-- Run once in the SQL editor of a dedicated Supabase project.
-- The client uses only the public RPCs below. Tables live in a private schema.
create schema if not exists app_private;
revoke all on schema app_private from public, anon, authenticated;

create table if not exists app_private.people (
  id uuid primary key default gen_random_uuid(),
  display_name text not null check (char_length(display_name) between 1 and 40),
  recovery_hash bytea not null unique
);
create table if not exists app_private.devices (
  user_id uuid primary key references auth.users(id) on delete cascade,
  person_id uuid not null references app_private.people(id) on delete cascade
);
create table if not exists app_private.workspaces (
  id uuid primary key default gen_random_uuid(),
  invite_hash bytea not null unique
);
create table if not exists app_private.memberships (
  workspace_id uuid not null references app_private.workspaces(id) on delete cascade,
  person_id uuid not null unique references app_private.people(id) on delete cascade,
  primary key (workspace_id, person_id)
);
create table if not exists app_private.routines (
  workspace_id uuid not null references app_private.workspaces(id) on delete cascade,
  id uuid not null,
  title text not null check (char_length(title) between 1 and 80),
  note text not null default '' check (char_length(note) <= 500),
  days integer[] not null check (array_length(days, 1) between 1 and 7),
  hour integer not null check (hour between 0 and 23),
  minute integer not null check (minute between 0 and 59),
  reminder_enabled boolean not null,
  active boolean not null,
  created_on date not null,
  revision integer not null default 1,
  primary key (workspace_id, id)
);
create table if not exists app_private.completions (
  workspace_id uuid not null,
  routine_id uuid not null,
  person_id uuid not null,
  done_on date not null,
  primary key (workspace_id, routine_id, person_id, done_on),
  foreign key (workspace_id, routine_id) references app_private.routines(workspace_id, id) on delete cascade,
  foreign key (workspace_id, person_id) references app_private.memberships(workspace_id, person_id) on delete cascade
);
alter table app_private.people enable row level security;
alter table app_private.devices enable row level security;
alter table app_private.workspaces enable row level security;
alter table app_private.memberships enable row level security;
alter table app_private.routines enable row level security;
alter table app_private.completions enable row level security;
revoke all on all tables in schema app_private from public, anon, authenticated;

create or replace function public.gzz_create_workspace(p_name text, p_invite_code text, p_recovery_code text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_person uuid; v_workspace uuid;
begin
  if auth.uid() is null then raise exception '로그인이 필요해요.'; end if;
  if char_length(trim(p_name)) not between 1 and 40 or
     p_invite_code !~ '^gz1_[A-Za-z0-9_-]{22}$' or p_recovery_code !~ '^gz1_[A-Za-z0-9_-]{22}$' then
    raise exception '이름 또는 연결 코드 형식이 올바르지 않아요.';
  end if;
  if exists (select 1 from app_private.devices where user_id = auth.uid()) then
    raise exception '이미 공유 공간에 연결되어 있어요.';
  end if;
  insert into app_private.people(display_name, recovery_hash)
    values (trim(p_name), pg_catalog.sha256(pg_catalog.convert_to(p_recovery_code, 'UTF8'))) returning id into v_person;
  insert into app_private.workspaces(invite_hash)
    values (pg_catalog.sha256(pg_catalog.convert_to(p_invite_code, 'UTF8'))) returning id into v_workspace;
  insert into app_private.memberships(workspace_id, person_id) values (v_workspace, v_person);
  insert into app_private.devices(user_id, person_id) values (auth.uid(), v_person);
  return pg_catalog.jsonb_build_object('workspaceId', v_workspace, 'personId', v_person);
end $$;

create or replace function public.gzz_join_workspace(p_name text, p_invite_code text, p_recovery_code text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_person uuid; v_workspace uuid;
begin
  if auth.uid() is null then raise exception '로그인이 필요해요.'; end if;
  if char_length(trim(p_name)) not between 1 and 40 or
     p_invite_code !~ '^gz1_[A-Za-z0-9_-]{22}$' or p_recovery_code !~ '^gz1_[A-Za-z0-9_-]{22}$' then
    raise exception '이름 또는 연결 코드 형식이 올바르지 않아요.';
  end if;
  if exists (select 1 from app_private.devices where user_id = auth.uid()) then
    raise exception '이미 공유 공간에 연결되어 있어요.';
  end if;
  select id into v_workspace from app_private.workspaces
    where invite_hash = pg_catalog.sha256(pg_catalog.convert_to(p_invite_code, 'UTF8')) for update;
  if v_workspace is null then raise exception '초대 코드를 찾지 못했어요.'; end if;
  if (select count(*) from app_private.memberships where workspace_id = v_workspace) >= 2 then
    raise exception '이 공유 공간은 이미 두 명이 사용 중이에요.';
  end if;
  insert into app_private.people(display_name, recovery_hash)
    values (trim(p_name), pg_catalog.sha256(pg_catalog.convert_to(p_recovery_code, 'UTF8'))) returning id into v_person;
  insert into app_private.memberships(workspace_id, person_id) values (v_workspace, v_person);
  insert into app_private.devices(user_id, person_id) values (auth.uid(), v_person);
  return pg_catalog.jsonb_build_object('workspaceId', v_workspace, 'personId', v_person);
end $$;

create or replace function public.gzz_restore_person(p_recovery_code text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_person uuid; v_workspace uuid;
begin
  if auth.uid() is null then raise exception '로그인이 필요해요.'; end if;
  if p_recovery_code !~ '^gz1_[A-Za-z0-9_-]{22}$' then raise exception '복구 코드 형식을 확인해주세요.'; end if;
  select person_id into v_person from app_private.devices where user_id = auth.uid();
  if v_person is null then
    select id into v_person from app_private.people
      where recovery_hash = pg_catalog.sha256(pg_catalog.convert_to(p_recovery_code, 'UTF8'));
    if v_person is null then raise exception '복구 코드를 찾지 못했어요.'; end if;
    insert into app_private.devices(user_id, person_id) values (auth.uid(), v_person);
  end if;
  select workspace_id into v_workspace from app_private.memberships where person_id = v_person;
  return pg_catalog.jsonb_build_object('workspaceId', v_workspace, 'personId', v_person);
end $$;

create or replace function public.gzz_snapshot()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare v_person uuid; v_workspace uuid;
begin
  select d.person_id, m.workspace_id into v_person, v_workspace
    from app_private.devices d join app_private.memberships m on m.person_id = d.person_id
    where d.user_id = auth.uid();
  if v_workspace is null then raise exception '공유 공간에 연결되지 않았어요.'; end if;
  return pg_catalog.jsonb_build_object(
    'workspaceId', v_workspace, 'personId', v_person,
    'members', (select coalesce(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
      'personId', p.id, 'displayName', p.display_name)), '[]'::jsonb)
      from app_private.memberships m join app_private.people p on p.id = m.person_id
      where m.workspace_id = v_workspace),
    'routines', (select coalesce(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
      'id', r.id, 'title', r.title, 'note', r.note, 'days', r.days,
      'hour', r.hour, 'minute', r.minute, 'reminderEnabled', r.reminder_enabled,
      'active', r.active, 'createdOn', r.created_on, 'revision', r.revision)), '[]'::jsonb)
      from app_private.routines r where r.workspace_id = v_workspace),
    'completions', (select coalesce(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
      'routineId', c.routine_id, 'personId', c.person_id, 'date', c.done_on)), '[]'::jsonb)
      from app_private.completions c where c.workspace_id = v_workspace)
  );
end $$;

create or replace function public.gzz_put_routine(p_data jsonb, p_revision integer)
returns integer language plpgsql security definer set search_path = '' as $$
declare v_person uuid; v_workspace uuid; v_id uuid; v_days integer[]; v_new_revision integer;
begin
  select d.person_id, m.workspace_id into v_person, v_workspace
    from app_private.devices d join app_private.memberships m on m.person_id = d.person_id
    where d.user_id = auth.uid();
  if v_workspace is null then raise exception '공유 공간에 연결되지 않았어요.'; end if;
  if p_revision is null or p_revision < 0 then raise exception '루틴 버전이 올바르지 않아요.'; end if;
  v_id := (p_data->>'id')::uuid;
  select pg_catalog.array_agg(distinct day order by day) into v_days
    from (select value::integer as day from pg_catalog.jsonb_array_elements_text(p_data->'days')) days;
  if pg_catalog.array_length(v_days, 1) not between 1 and 7 or
     exists (select 1 from pg_catalog.unnest(v_days) day where day not between 1 and 7) or
     char_length(trim(p_data->>'title')) not between 1 and 80 or
     char_length(coalesce(p_data->>'note', '')) > 500 then
    raise exception '루틴 이름·메모·요일을 확인해주세요.';
  end if;
  if p_revision = 0 then
    if (select count(*) from app_private.routines where workspace_id = v_workspace) >= 500 then
      raise exception '공유 루틴은 500개까지 사용할 수 있어요.';
    end if;
    insert into app_private.routines(workspace_id,id,title,note,days,hour,minute,reminder_enabled,active,created_on)
      values(v_workspace,v_id,trim(p_data->>'title'),coalesce(p_data->>'note',''),v_days,
        (p_data->>'hour')::integer,(p_data->>'minute')::integer,
        (p_data->>'reminderEnabled')::boolean,(p_data->>'active')::boolean,(p_data->>'createdOn')::date)
      on conflict do nothing returning revision into v_new_revision;
  else
    update app_private.routines set title=trim(p_data->>'title'),note=coalesce(p_data->>'note',''),
      days=v_days,hour=(p_data->>'hour')::integer,minute=(p_data->>'minute')::integer,
      reminder_enabled=(p_data->>'reminderEnabled')::boolean,active=(p_data->>'active')::boolean,
      revision=revision+1
      where workspace_id=v_workspace and id=v_id and revision=p_revision returning revision into v_new_revision;
  end if;
  if v_new_revision is null then raise exception '친구가 먼저 수정했어요. 새로고침 후 다시 시도해주세요.'; end if;
  return v_new_revision;
end $$;

create or replace function public.gzz_delete_routine(p_id uuid, p_revision integer)
returns boolean language plpgsql security definer set search_path = '' as $$
declare v_workspace uuid; v_deleted uuid;
begin
  select m.workspace_id into v_workspace from app_private.devices d
    join app_private.memberships m on m.person_id=d.person_id where d.user_id=auth.uid();
  if v_workspace is null then raise exception '공유 공간에 연결되지 않았어요.'; end if;
  delete from app_private.routines where workspace_id=v_workspace and id=p_id and revision=p_revision
    returning id into v_deleted;
  if v_deleted is null then raise exception '친구가 먼저 수정했어요. 새로고침 후 다시 시도해주세요.'; end if;
  return true;
end $$;

create or replace function public.gzz_set_completion(p_id uuid, p_date date, p_done boolean)
returns boolean language plpgsql security definer set search_path = '' as $$
declare v_person uuid; v_workspace uuid;
begin
  select d.person_id, m.workspace_id into v_person, v_workspace
    from app_private.devices d join app_private.memberships m on m.person_id=d.person_id
    where d.user_id=auth.uid();
  if v_workspace is null then raise exception '공유 공간에 연결되지 않았어요.'; end if;
  if p_date > current_date + 1 then raise exception '미래 완료 기록은 저장할 수 없어요.'; end if;
  if not exists (select 1 from app_private.routines where workspace_id=v_workspace and id=p_id and created_on<=p_date) then
    raise exception '공유 루틴을 찾지 못했어요.';
  end if;
  if p_done then
    insert into app_private.completions(workspace_id,routine_id,person_id,done_on)
      values(v_workspace,p_id,v_person,p_date) on conflict do nothing;
  else
    delete from app_private.completions
      where workspace_id=v_workspace and routine_id=p_id and person_id=v_person and done_on=p_date;
  end if;
  return true;
end $$;

create or replace function public.gzz_share_routine(p_data jsonb, p_dates text[])
returns boolean language plpgsql security definer set search_path = '' as $$
declare v_date text;
begin
  if coalesce(pg_catalog.array_length(p_dates, 1), 0) > 2000 then
    raise exception '완료 기록이 너무 많아요.';
  end if;
  perform public.gzz_put_routine(p_data, 0);
  foreach v_date in array p_dates loop
    perform public.gzz_set_completion((p_data->>'id')::uuid, v_date::date, true);
  end loop;
  return true;
end $$;

revoke execute on function public.gzz_create_workspace(text,text,text), public.gzz_join_workspace(text,text,text),
  public.gzz_restore_person(text), public.gzz_snapshot(), public.gzz_put_routine(jsonb,integer),
  public.gzz_delete_routine(uuid,integer), public.gzz_set_completion(uuid,date,boolean),
  public.gzz_share_routine(jsonb,text[]) from public, anon;
grant execute on function public.gzz_create_workspace(text,text,text), public.gzz_join_workspace(text,text,text),
  public.gzz_restore_person(text), public.gzz_snapshot(), public.gzz_put_routine(jsonb,integer),
  public.gzz_delete_routine(uuid,integer), public.gzz_set_completion(uuid,date,boolean),
  public.gzz_share_routine(jsonb,text[]) to authenticated;
