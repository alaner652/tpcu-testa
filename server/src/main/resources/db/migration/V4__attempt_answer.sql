-- 第 4 步：考生登入、作答、交卷、自動批改

alter table candidate
    add column session_version int not null default 0,   -- 每次登入加一，舊的連線會被踢掉
    add column leave_count     int not null default 0,   -- 作答中離開考試畫面的次數
    add column last_login_at   timestamptz;

create table attempt (
    id             bigint generated always as identity primary key,
    candidate_id   bigint      not null unique references candidate (id) on delete cascade,
    exam_id        bigint      not null references exam (id) on delete cascade,
    started_at     timestamptz not null,
    -- 截止時間由伺服器決定，過了就拒收
    deadline_at    timestamptz not null,
    submitted_at   timestamptz,
    auto_submitted boolean     not null default false,   -- 時間到或考試結束時由系統交卷
    anon_id        varchar(12) not null unique,          -- 評分時顯示的匿名編號
    auto_score     numeric(7, 2),                        -- 選擇、填空的自動批改得分
    final_score    numeric(7, 2)                         -- 加上申論平均後的總分（評分完成後才有）
);
create index attempt_open_idx on attempt (deadline_at) where submitted_at is null;

create table answer (
    id          bigint generated always as identity primary key,
    attempt_id  bigint      not null references attempt (id) on delete cascade,
    question_id varchar(40) not null,                    -- 試卷快照裡的題目代號
    content     jsonb       not null,
    auto_score  numeric(6, 2),
    updated_at  timestamptz not null default now(),
    unique (attempt_id, question_id)
);
