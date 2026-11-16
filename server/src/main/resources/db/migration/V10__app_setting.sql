-- System settings changed in the admin pages. A missing row means the default from application.yml.
create table app_setting (
    key        text primary key,
    value      text        not null,
    updated_at timestamptz not null default now()
);
