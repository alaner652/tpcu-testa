-- 第 5、6 步：評分指派、評分紀錄、公布成績

-- 每場考試指派 1 到多位評分者
create table exam_grader (
    id         bigint generated always as identity primary key,
    exam_id    bigint      not null references exam (id) on delete cascade,
    staff_id   bigint      not null references staff (id),
    created_at timestamptz not null default now(),
    unique (exam_id, staff_id)
);

-- 一位評分者對一個申論答案的評分。送出即定案，只有承辦人能「重開」（刪掉重評，留操作紀錄）
create table grading (
    id           bigint generated always as identity primary key,
    attempt_id   bigint        not null references attempt (id) on delete cascade,
    question_id  varchar(40)   not null,
    staff_id     bigint        not null references staff (id),
    score        numeric(5, 1) not null check (score >= 0),
    comment      text          not null default '',
    submitted_at timestamptz   not null default now(),
    unique (attempt_id, question_id, staff_id)
);

alter table exam add column results_published_at timestamptz;
