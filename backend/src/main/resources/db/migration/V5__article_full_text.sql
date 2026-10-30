create table article_full_texts (
    article_id bigint primary key references articles(id) on delete cascade,
    status varchar(20) not null check(status in ('PENDING','FETCHING','READY','FAILED','UNSUPPORTED','TOO_LONG')),
    full_text text,
    final_url text,
    content_hash varchar(64),
    extractor_version varchar(80) not null default 'ordinary-body-v1',
    requested_summary boolean not null default false,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    fetched_at timestamptz,
    fetch_duration_ms bigint,
    error_code varchar(80)
);
create index ix_full_text_pending on article_full_texts(status,requested_summary,created_at);
