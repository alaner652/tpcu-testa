-- 第 1 步：教職員帳號、單位、單位角色、操作紀錄

create table unit (
    id         bigint generated always as identity primary key,
    name       varchar(100) not null unique,
    type       varchar(20)  not null check (type in ('ADMISSION', 'CLASS')),
    active     boolean      not null default true,
    created_at timestamptz  not null default now()
);

create table staff (
    id                   bigint generated always as identity primary key,
    email                varchar(254) not null unique,   -- 一律小寫
    password_hash        varchar(200) not null,
    name                 varchar(100) not null,
    is_system_admin      boolean      not null default false,
    active               boolean      not null default true,
    must_change_password boolean      not null default false,
    failed_logins        int          not null default 0,
    locked_until         timestamptz,
    -- 每次改密碼、停用就加一；Session 裡的版本對不上就強制登出
    session_version      int          not null default 0,
    last_login_at        timestamptz,
    created_at           timestamptz  not null default now()
);

-- 角色跟著單位走：同一個人在不同單位可以有不同角色
create table unit_role (
    id         bigint generated always as identity primary key,
    unit_id    bigint      not null references unit (id) on delete cascade,
    staff_id   bigint      not null references staff (id) on delete cascade,
    role       varchar(20) not null check (role in ('OWNER', 'AUTHOR', 'PROCTOR', 'GRADER')),
    created_at timestamptz not null default now(),
    unique (unit_id, staff_id, role)
);
create index unit_role_staff_idx on unit_role (staff_id);

-- 操作紀錄：只能新增，不能改
create table audit_log (
    id          bigint generated always as identity primary key,
    at          timestamptz not null default now(),
    actor_type  varchar(20) not null,          -- STAFF / CANDIDATE / SYSTEM / ANONYMOUS
    actor_id    bigint,
    actor_label varchar(254),
    action      varchar(60) not null,
    target_type varchar(40),
    target_id   varchar(64),
    unit_id     bigint,
    reason      text,
    detail      text,
    ip          varchar(45)
);
create index audit_log_at_idx on audit_log (at desc);
create index audit_log_unit_idx on audit_log (unit_id, at desc);

create function audit_log_immutable() returns trigger as $$
begin
    raise exception 'audit_log 只能新增，不能修改或刪除';
end;
$$ language plpgsql;

create trigger audit_log_no_change
    before update or delete on audit_log
    for each row execute function audit_log_immutable();

create trigger audit_log_no_truncate
    before truncate on audit_log
    for each statement execute function audit_log_immutable();
