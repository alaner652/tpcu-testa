-- 第 3 步：考試與考生名單（白名單）

create table exam (
    id          bigint generated always as identity primary key,
    unit_id     bigint       not null references unit (id),
    -- 題庫被刪除時變成 null；已發布的考試用 snapshot，不受影響
    bank_id     bigint       references bank (id) on delete set null,
    created_by  bigint       not null references staff (id),
    name        varchar(200) not null,
    start_at    timestamptz,
    duration    int          not null check (duration between 1 and 600),   -- 分鐘
    mode        varchar(20)  not null check (mode in ('ONLINE', 'CLASSROOM')),
    status      varchar(20)  not null default 'DRAFT' check (status in ('DRAFT', 'PUBLISHED', 'ENDED')),
    -- 發布時凍結的題目。之後題庫再改也不影響這場考試
    snapshot    jsonb,
    published_at timestamptz,
    ended_at    timestamptz,
    created_at  timestamptz  not null default now()
);
create index exam_unit_idx on exam (unit_id);

create table candidate (
    id            bigint generated always as identity primary key,
    exam_id       bigint       not null references exam (id) on delete cascade,
    ticket        varchar(40)  not null,          -- 准考證號，一律大寫
    name          varchar(100) not null,
    dept          varchar(100) not null default '',
    seat          int          not null,
    -- 6 碼登入碼。登入單要能重印，所以存原文；只有該單位的負責人與監考讀得到
    login_code    varchar(6)   not null,
    status        varchar(20)  not null default 'IDLE' check (status in ('IDLE', 'IN', 'DONE')),
    failed_logins int          not null default 0,
    locked_until  timestamptz,
    created_at    timestamptz  not null default now(),
    unique (exam_id, ticket)
);
