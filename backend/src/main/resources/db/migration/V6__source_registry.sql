alter table news_sources
    add column site_url text,
    add column feed_url text,
    add column collection_enabled boolean not null default false,
    add column validation_status varchar(24) not null default 'UNVERIFIED'
        check (validation_status in ('UNVERIFIED','PASSED','NEEDS_CONFIG','BLOCKED')),
    add column allowed_hosts jsonb not null default '[]',
    add column body_mode varchar(16) not null default 'AUTO' check(body_mode in ('AUTO','PAGE','FEED_FULL')),
    add column feed_content_verified boolean not null default false,
    add column body_selector text,
    add column exclude_selectors jsonb not null default '[]',
    add column config_version bigint not null default 1,
    add column validated_config_version bigint,
    add column validation_report jsonb not null default '{}',
    add column validated_at timestamptz,
    add column last_collection_at timestamptz,
    add column last_collection_error varchar(80);

-- 수동 seed의 URL을 운영 feed로 오인하지 않고 검증한 여섯 항목만 명시적으로 이관한다.
create temporary table legacy_source_registry (
    source_key text, name text, type text, feed_url text, category text, host text, body_selector text
);
insert into legacy_source_registry values
('openai-blog','OpenAI Blog','RSS_ATOM','https://openai.com/news/rss.xml','AI','openai.com','article [data-toc-content],article:not(:has([data-toc-content]))'),
('google-ai-blog','Google AI Blog','RSS_ATOM','https://blog.google/technology/ai/rss/','AI','blog.google','article .article-container__content,article:not(:has(.article-container__content))'),
('github-blog','GitHub Blog','RSS_ATOM','https://github.blog/feed/','DEVTOOLS','github.blog','section.post__content'),
('arxiv-cs-ai','arXiv cs.AI','ARXIV','https://export.arxiv.org/api/query?search_query=cat:cs.AI&sortBy=submittedDate&sortOrder=descending&max_results=10','CS_RESEARCH','export.arxiv.org',null),
('arxiv-cs-lg','arXiv cs.LG','ARXIV','https://export.arxiv.org/api/query?search_query=cat:cs.LG&sortBy=submittedDate&sortOrder=descending&max_results=10','CS_RESEARCH','export.arxiv.org',null),
('arxiv-cs-cl','arXiv cs.CL','ARXIV','https://export.arxiv.org/api/query?search_query=cat:cs.CL&sortBy=submittedDate&sortOrder=descending&max_results=10','CS_RESEARCH','export.arxiv.org',null);

do $$ begin
    if exists(select 1 from news_sources s join legacy_source_registry r using(source_key)
        where s.type<>r.type or not(s.url like 'https://' || r.host || '/%' or
        (r.type='ARXIV' and (s.url like 'http://arxiv.org/%' or s.url like 'https://arxiv.org/%')))) then
        raise exception 'SOURCE_REGISTRY_MIGRATION_CONFLICT';
    end if;
end $$;

insert into news_sources(source_key,name,type,url,category_hint)
select source_key,name,type,feed_url,category from legacy_source_registry
on conflict(source_key) do nothing;

update news_sources s set site_url='https://' || r.host || '/', feed_url=r.feed_url,
    collection_enabled=true, validation_status='PASSED', validated_config_version=1,
    allowed_hosts=jsonb_build_array(r.host), body_mode='PAGE', body_selector=r.body_selector,
    exclude_selectors=case when r.source_key='github-blog' then
        '["section:has(>h2:matchesOwn(^Tags:$))","div:has(>h2:matchesOwn(^Written by$))"]'::jsonb else '[]'::jsonb end,
    validation_report='{"reason":"LEGACY_MIGRATION"}', validated_at=now()
from legacy_source_registry r where s.source_key=r.source_key;

drop table legacy_source_registry;
create unique index ux_source_feed_url on news_sources(feed_url) where feed_url is not null;
create index ix_source_collection_enabled on news_sources(collection_enabled);

alter table article_full_texts
    add column body_origin varchar(8) not null default 'PAGE' check(body_origin in ('FEED','PAGE')),
    add column source_config_version bigint;

alter table article_raw_contents add column source_excerpt text;
