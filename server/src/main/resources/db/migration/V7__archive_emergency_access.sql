-- 學校的決定（2026-11-14）：
-- 1. 考試資料保留到期後，由負責人封存、再人工刪除。
-- 2. 系統管理員可以緊急檢視考試，但要單位負責人授權，並留下紀錄。

-- 封存：考試變成唯讀，考生不能再登入查成績
alter table exam add column archived_at timestamptz;

create table exam_emergency_access (
    id             bigint generated always as identity primary key,
    exam_id        bigint       not null references exam (id) on delete cascade,
    staff_id       bigint       not null references staff (id),   -- 被授權的系統管理員
    granted_by     bigint       not null references staff (id),   -- 授權的單位負責人
    reason         varchar(500) not null,
    granted_at     timestamptz  not null default now(),
    expires_at     timestamptz  not null,
    revoked_at     timestamptz,
    view_count     int          not null default 0,
    last_viewed_at timestamptz
);
create index exam_emergency_access_staff_idx on exam_emergency_access (staff_id);
