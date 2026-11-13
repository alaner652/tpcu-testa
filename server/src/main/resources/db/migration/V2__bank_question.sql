-- 第 2 步：題庫與題目

create table bank (
    id         bigint generated always as identity primary key,
    unit_id    bigint       not null references unit (id),
    created_by bigint       not null references staff (id),
    name       varchar(200) not null default '',
    subject    varchar(50)  not null default '',
    duration   int          not null default 60 check (duration between 1 and 600),  -- 作答時間（分鐘）
    -- 每次儲存加一。前端送回它讀到的版本，對不上代表別人先改過了
    version    int          not null default 0,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);
create index bank_unit_idx on bank (unit_id);

create table question (
    id        bigint generated always as identity primary key,
    bank_id   bigint        not null references bank (id) on delete cascade,
    -- 前端產生的題目代號，只在同一份題庫內唯一
    client_id varchar(40)   not null,
    position  int           not null,
    type      varchar(20)   not null check (type in ('single', 'multiple', 'blank', 'essay')),
    stem      text          not null default '',
    points    numeric(5, 1) not null default 0 check (points between 0 and 100),
    -- 選項、正確答案、填空答案、評分規準等，依題型而異
    content   jsonb         not null,
    unique (bank_id, client_id)
);
create index question_bank_idx on question (bank_id, position);
