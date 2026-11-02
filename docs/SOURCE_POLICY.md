# Source Policy

[English](SOURCE_POLICY.md) | [한국어](SOURCE_POLICY.ko.md)

## Purpose
Sigak should prioritize source quality over source volume.

The product is not a general crawler. It combines practical engineering blogs with important AI, software development, and computer science updates, then adds on-demand summaries and relationship-based insight. Concrete engineering career experiences may be included; generic career tips and job listings are not a dedicated collection stream.

## Source Inclusion Rules
Prefer sources that are reliable, attributable, and likely to represent meaningful technical change.

Include:
- attributable engineering problem-solving, architecture, incident and performance writeups
- concrete developer career/team experiences with enough context to learn from
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
DISCOVER -> FETCH_FEED -> EXTRACT -> NORMALIZE -> PUBLISH_SOURCE -> INDEX
NEW_SUPPORTED_ARTICLE -> PREPARE_FULL_BODY (free)
DETAIL_VISIT -> PREPARE_IF_MISSING -> ENRICH_WITH_LLM -> SAVE_SHARED_SUMMARY
```

Ordinary-body support is configured per approved source in PostgreSQL. Source registration
finds an RSS/Atom feed, checks three original/body samples and requires review before activation.
A verified full feed can prepare the body during publication; otherwise a free worker uses a
common or configured page selector. RSS/Atom excerpts, source HTML and complete text are kept separate.
Site-specific exclusions also apply to feed bodies when that markup can be selected.

Discovery/feed/body requests permit exact approved HTTPS hosts, pin validated public IPs,
follow at most three same-host redirects and enforce2MiB/15second limits. No login/paywall/bot
bypass, script execution or public full-text republication is added. Missing, ambiguous,
blocked or oversized inputs do not silently fall back to excerpts or prefixes. arXiv full
papers remain unsupported. Stored errors/bodies/summaries are not bulk replayed by registration.
Only an actual first detail visit can request paid generation within the existing cumulative budget.

The 2026-11-02 preview verified Toss Tech, Woowahan Tech Blog and NAVER D2 samples. This is
source/body evidence, not proof of every article's completeness, personalized relevance or AI
summary quality. D2 event/video posts provide their article text, not a video transcript.
See [source operations](SOURCE_OPERATIONS.ko.md) for registration, preview, selectors and modes.

Saved articles should be reprocessed for embeddings, concept extraction, and Graph RAG enrichment instead of being scraped again.

The PostgreSQL source registry is the operational source of truth. Code keeps format-level RSS/Atom and arXiv connectors; source URLs, exact hosts and body/exclusion selectors are data.

Initial source types:
- official RSS/Atom feeds for AI, developer tools, engineering, security, and infrastructure updates
- arXiv API queries for selected research categories such as `cs.AI`, `cs.LG`, and `cs.CL`
- manual or newsletter import for curated links that do not have stable feeds

Hacker News should not be part of the first automated collector. Community aggregators can be useful discovery or ranking signals later, but they are not original sources and can blur attribution if used too early.

## Quality Principles
- Prefer fewer high-signal items over many low-signal items.
- Keep source attribution visible.
- Do not treat AI-generated enrichment as source truth.
- Preserve enough original context to regenerate summaries and graph relationships later.
- Avoid adding automated sources until there is a clear rule for why those sources belong in Sigak.
