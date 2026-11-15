-- Whether candidates can sign in to see their own published scores. Off by default:
-- admission results are usually announced through the school's own channels.
alter table exam
    add column candidate_results boolean not null default false;
