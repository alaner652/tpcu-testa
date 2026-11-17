-- Login codes are kept only as bcrypt hashes. A code is shown once when it is issued, so slips are printed then;
-- printing again means issuing a new code. Existing codes are hashed in place, so slips already printed keep working.
-- Candidates without a hash (imported but not yet issued a code) cannot sign in.
create extension if not exists pgcrypto;

alter table candidate add column login_code_hash varchar(100);
update candidate set login_code_hash = crypt(login_code, gen_salt('bf', 8));
alter table candidate drop column login_code;

drop extension pgcrypto;
