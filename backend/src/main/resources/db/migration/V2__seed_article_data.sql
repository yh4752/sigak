insert into news_sources (id, source_key, name, type, url, category_hint) values
    (1, 'openai', 'OpenAI', 'RSS_ATOM', 'https://openai.com/news/rss.xml', 'AI'),
    (2, 'postgresql-weekly', 'PostgreSQL Weekly', 'RSS_ATOM', 'https://example.com/postgresql-weekly/feed.xml', 'DATA'),
    (3, 'security-advisory-board', 'Security Advisory Board', 'RSS_ATOM', 'https://example.com/security-advisory-board/feed.xml', 'SECURITY'),
    (4, 'arxiv', 'arXiv', 'ARXIV', 'https://export.arxiv.org/api/query', 'CS_RESEARCH'),
    (5, 'cncf', 'Cloud Native Computing Foundation', 'RSS_ATOM', 'https://www.cncf.io/feed/', 'INFRA_CLOUD');

insert into articles (
    id, source_id, external_id, title, url, canonical_url, published_at,
    event_type, primary_category, importance_score, processing_status,
    created_at, updated_at
) values
    (1, 1, 'openai-agent-evals', 'OpenAI Releases Agent Evaluation Toolkit', 'https://example.com/articles/openai-agent-evals', 'https://example.com/articles/openai-agent-evals', '2026-05-01T09:00:00Z', 'OFFICIAL_ANNOUNCEMENT', 'AI', 88, 'PUBLISHED', '2026-05-06T00:00:00Z', '2026-05-06T00:00:00Z'),
    (2, 2, 'postgres-vector-indexes', 'PostgreSQL Adds Native Vector Index Improvements', 'https://example.com/articles/postgres-vector-indexes', 'https://example.com/articles/postgres-vector-indexes', '2026-05-02T11:30:00Z', 'RELEASE', 'DATA', 82, 'PUBLISHED', '2026-05-06T00:00:00Z', '2026-05-06T00:00:00Z'),
    (3, 3, 'ai-toolchain-package-attack', 'Critical Package Registry Attack Targets AI Toolchains', 'https://example.com/articles/ai-toolchain-package-attack', 'https://example.com/articles/ai-toolchain-package-attack', '2026-05-03T15:45:00Z', 'SECURITY', 'SECURITY', 93, 'PUBLISHED', '2026-05-06T00:00:00Z', '2026-05-06T00:00:00Z'),
    (4, 4, 'graph-rag-failure-modes', 'New Research Maps Failure Modes in Graph RAG Systems', 'https://example.com/articles/graph-rag-failure-modes', 'https://example.com/articles/graph-rag-failure-modes', '2026-05-04T08:20:00Z', 'RESEARCH', 'CS_RESEARCH', 86, 'PUBLISHED', '2026-05-06T00:00:00Z', '2026-05-06T00:00:00Z'),
    (5, 5, 'kubernetes-lts-policy', 'Kubernetes Project Updates Long-Term Support Policy', 'https://example.com/articles/kubernetes-lts-policy', 'https://example.com/articles/kubernetes-lts-policy', '2026-05-05T10:10:00Z', 'NEWS', 'INFRA_CLOUD', 78, 'PUBLISHED', '2026-05-06T00:00:00Z', '2026-05-06T00:00:00Z');

insert into article_raw_contents (article_id, raw_content, extracted_text, collected_at) values
    (1, 'OpenAI introduced a toolkit for evaluating agent behavior in multi-step workflows.', 'OpenAI introduced a toolkit for evaluating agent behavior in multi-step workflows.', '2026-05-06T00:00:00Z'),
    (2, 'A PostgreSQL release improved vector index performance for retrieval-heavy workloads.', 'A PostgreSQL release improved vector index performance for retrieval-heavy workloads.', '2026-05-06T00:00:00Z'),
    (3, 'A coordinated package registry attack targeted developer environments that install AI tooling.', 'A coordinated package registry attack targeted developer environments that install AI tooling.', '2026-05-06T00:00:00Z'),
    (4, 'Researchers categorized common failure modes in Graph RAG systems and proposed evaluation criteria.', 'Researchers categorized common failure modes in Graph RAG systems and proposed evaluation criteria.', '2026-05-06T00:00:00Z'),
    (5, 'The Kubernetes project updated its support policy for production operators managing long-lived clusters.', 'The Kubernetes project updated its support policy for production operators managing long-lived clusters.', '2026-05-06T00:00:00Z');

insert into article_enrichments (
    article_id, summary, why_it_matters, suggested_primary_category,
    suggested_importance_score, model_name, prompt_version, is_current, enriched_at
) values
    (1, 'OpenAI introduced a toolkit for evaluating agent behavior in multi-step workflows.', 'Agent evaluation is becoming a practical requirement as teams move from demos to production workflows.', 'AI', 88, 'curated-seed', 'seed-v1', true, '2026-05-06T00:00:00Z'),
    (2, 'A PostgreSQL release improved vector index performance for retrieval-heavy workloads.', 'Better vector indexing makes it easier to build search and RAG features without adding infrastructure too early.', 'DATA', 82, 'curated-seed', 'seed-v1', true, '2026-05-06T00:00:00Z'),
    (3, 'A coordinated package registry attack targeted developer environments that install AI tooling.', 'AI development stacks often combine fast-moving packages, credentials, and automation, which raises the blast radius of supply chain attacks.', 'SECURITY', 93, 'curated-seed', 'seed-v1', true, '2026-05-06T00:00:00Z'),
    (4, 'Researchers categorized common failure modes in Graph RAG systems and proposed evaluation criteria.', 'Understanding graph retrieval failures helps teams design relationship-aware insight features with better evidence quality.', 'CS_RESEARCH', 86, 'curated-seed', 'seed-v1', true, '2026-05-06T00:00:00Z'),
    (5, 'The Kubernetes project updated its support policy for production operators managing long-lived clusters.', 'Support windows shape upgrade planning, security posture, and operational cost for infrastructure teams.', 'INFRA_CLOUD', 78, 'curated-seed', 'seed-v1', true, '2026-05-06T00:00:00Z');

insert into article_topics (article_id, topic, position) values
    (1, 'LLM agents', 0),
    (1, 'evaluation', 1),
    (1, 'production AI', 2),
    (2, 'PostgreSQL', 0),
    (2, 'vector search', 1),
    (2, 'database indexing', 2),
    (3, 'supply chain security', 0),
    (3, 'package registry', 1),
    (3, 'AI tooling', 2),
    (4, 'Graph RAG', 0),
    (4, 'knowledge graphs', 1),
    (4, 'retrieval quality', 2),
    (5, 'Kubernetes', 0),
    (5, 'release policy', 1),
    (5, 'platform operations', 2);

insert into article_relations (source_article_id, target_article_id, relation_type, reason) values
    (1, 3, 'RELATED', 'Both articles affect production AI development workflows.'),
    (1, 5, 'RELATED', 'Both articles are relevant to production engineering practices.'),
    (2, 1, 'RELATED', 'Vector indexing supports retrieval-heavy AI systems.'),
    (2, 5, 'RELATED', 'Both articles influence infrastructure choices for technical teams.'),
    (3, 1, 'RELATED', 'AI tooling increases the security impact of package registry attacks.'),
    (3, 4, 'RELATED', 'Both articles concern risks in AI-oriented technical systems.'),
    (4, 1, 'RELATED', 'Graph RAG evaluation connects to agent and retrieval evaluation.'),
    (4, 5, 'RELATED', 'Both articles affect reliability planning for technical systems.'),
    (5, 2, 'RELATED', 'Infrastructure support policy and database indexing both shape platform operations.'),
    (5, 3, 'RELATED', 'Long-term operations and supply-chain security both affect production risk.');

select setval('news_sources_id_seq', (select max(id) from news_sources));
select setval('articles_id_seq', (select max(id) from articles));
