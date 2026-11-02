create table summary_budget (
    id integer primary key check (id = 1),
    charged_micros bigint not null default 0 check (charged_micros between 0 and 4000000),
    attempt_count integer not null default 0 check (attempt_count between 0 and 200),
    daily_count integer not null default 0,
    dispatch_day date not null default current_date,
    stopped_reason varchar(80)
);
insert into summary_budget(id) values(1);

create table article_summary_jobs (
    id bigserial primary key,
    article_id bigint not null references articles(id) on delete cascade,
    input_hash varchar(64) not null,
    prompt_version varchar(80) not null,
    model_name varchar(120) not null,
    request_json text not null,
    status varchar(40) not null check(status in ('PENDING','DISPATCHED','READY','FAILED','UNKNOWN','BUDGET_EXHAUSTED')),
    result_json text,
    error_code varchar(80),
    reserved_micros bigint not null default 0,
    actual_cost_micros bigint,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz,
    unique(article_id,input_hash,prompt_version,model_name)
);
create index ix_summary_jobs_pending on article_summary_jobs(status,created_at);
