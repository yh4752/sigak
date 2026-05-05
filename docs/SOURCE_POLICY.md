# Source Policy

## Purpose
Sigak should prioritize source quality over source volume.

The product is not a general crawler. It curates important AI, software development, and computer science updates, then enriches them with summaries, importance signals, and relationship-based insight.

## Source Inclusion Rules
Prefer sources that are reliable, attributable, and likely to represent meaningful technical change.

Include:
- official announcements from companies, labs, standards bodies, or major open source projects
- reputable AI, CS, and software development news sources
- security advisories, CVE records, incident reports, and severe vulnerability writeups
- influential research papers or research announcements
- major release notes for important languages, frameworks, platforms, infrastructure, or developer tools

## Source Exclusion Rules
Exclude sources that make the MVP noisy or difficult to trust.

Exclude:
- general personal technical blog posts
- simple tutorials
- promotional content or vendor marketing without substantial technical change
- low-impact patch releases
- rumors or unverified community discussions
- duplicate reposts that add no original context

Personal technical blogs may be reconsidered later only when source quality rules are clear enough to keep the feed reliable.

## Seed Data Rules
Curated seed data should prove the product shape before automated collection.

Seed data should:
- cover multiple event types: `NEWS`, `OFFICIAL_ANNOUNCEMENT`, `RESEARCH`, `SECURITY`, `RELEASE`
- cover multiple primary categories: `AI`, `SECURITY`, `SOFTWARE_ENGINEERING`, `BACKEND`, `FRONTEND`, `DATA`, `INFRA_CLOUD`, `DEVTOOLS`, `CS_RESEARCH`
- include `summary`, `whyItMatters`, `topics`, and related items
- preserve source URL, source name, published date, and enough raw text or source metadata for future reprocessing
- include examples that can later become graph nodes and relationships

## Collection Strategy
Start with manually curated data, then add selected RSS/API collection after the article model and insight format are stable.

Collection should support this processing flow:

```txt
COLLECTED -> EXTRACTED -> ENRICHED -> INDEXED
```

Saved articles should be reprocessed for embeddings, concept extraction, and Graph RAG enrichment instead of being scraped again.

## Quality Principles
- Prefer fewer high-signal items over many low-signal items.
- Keep source attribution visible.
- Do not treat AI-generated enrichment as source truth.
- Preserve enough original context to regenerate summaries and graph relationships later.
- Avoid adding automated sources until there is a clear rule for why those sources belong in Sigak.
