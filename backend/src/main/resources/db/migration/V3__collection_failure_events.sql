create table collection_failure_events (
    id bigserial primary key,
    run_id uuid not null,
    source_key varchar(120) not null,
    stage varchar(60) not null,
    failure_kind varchar(80) not null,
    retryable boolean not null,
    message text not null,
    fingerprint varchar(160) not null,
    article_external_id varchar(255),
    article_url text,
    article_title varchar(500),
    occurred_at timestamptz not null
);

create index ix_collection_failure_events_source_occurred
    on collection_failure_events(source_key, occurred_at desc);

create index ix_collection_failure_events_run_id
    on collection_failure_events(run_id);

create index ix_collection_failure_events_fingerprint_occurred
    on collection_failure_events(fingerprint, occurred_at desc);

create index ix_collection_failure_events_retryable_occurred
    on collection_failure_events(retryable, occurred_at desc);
