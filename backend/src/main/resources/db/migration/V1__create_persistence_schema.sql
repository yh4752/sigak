create table news_sources (
    id bigserial primary key,
    source_key varchar(120) not null unique,
    name varchar(255) not null,
    type varchar(40) not null,
    url text not null,
    category_hint varchar(80)
);

create table articles (
    id bigserial primary key,
    source_id bigint not null references news_sources(id) on delete restrict,
    external_id varchar(255),
    title varchar(500) not null,
    url text not null unique,
    canonical_url text not null,
    published_at timestamptz not null,
    event_type varchar(60) not null,
    primary_category varchar(80) not null,
    importance_score integer not null check (importance_score between 0 and 100),
    processing_status varchar(60) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table article_raw_contents (
    id bigserial primary key,
    article_id bigint not null unique references articles(id) on delete cascade,
    raw_content text not null,
    extracted_text text not null,
    collected_at timestamptz not null
);

create table article_enrichments (
    id bigserial primary key,
    article_id bigint not null references articles(id) on delete cascade,
    summary text not null,
    why_it_matters text not null,
    suggested_primary_category varchar(80),
    suggested_importance_score integer check (
        suggested_importance_score is null or suggested_importance_score between 0 and 100
    ),
    model_name varchar(120) not null,
    prompt_version varchar(80) not null,
    is_current boolean not null default false,
    enriched_at timestamptz not null
);

create table article_topics (
    id bigserial primary key,
    article_id bigint not null references articles(id) on delete cascade,
    topic varchar(160) not null,
    position integer not null,
    unique (article_id, position),
    unique (article_id, topic)
);

create table article_relations (
    id bigserial primary key,
    source_article_id bigint not null references articles(id) on delete cascade,
    target_article_id bigint not null references articles(id) on delete cascade,
    relation_type varchar(80) not null,
    reason text,
    check (source_article_id <> target_article_id),
    unique (source_article_id, target_article_id, relation_type)
);

create unique index ux_article_enrichments_current
    on article_enrichments (article_id)
    where is_current = true;

create index ix_articles_source_id on articles(source_id);
create index ix_articles_published_at on articles(published_at desc);
create index ix_articles_importance_score on articles(importance_score desc);
create index ix_article_topics_topic_lower on article_topics(lower(topic));
create index ix_article_relations_source on article_relations(source_article_id);
create index ix_article_relations_target on article_relations(target_article_id);
