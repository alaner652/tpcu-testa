-- 監考補時：個別考生因設備問題損失的時間，由監考加回去
alter table attempt
    add column extra_minutes int not null default 0;   -- 累計補了幾分鐘（deadline_at 已經包含）
