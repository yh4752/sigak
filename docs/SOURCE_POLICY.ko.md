# 출처 정책

[English](SOURCE_POLICY.md) | [한국어](SOURCE_POLICY.ko.md)

## 목적
Sigak은 출처의 양보다 품질을 우선합니다.

이 제품은 범용 크롤러가 아닙니다. AI, 소프트웨어 개발, 컴퓨터 과학 분야의 중요한 업데이트를 선별하고, 요약, 중요도 신호, 관계 기반 인사이트로 보강합니다.

## 출처 포함 기준
신뢰할 수 있고, attribution이 가능하며, 의미 있는 기술 변화를 나타낼 가능성이 높은 출처를 우선합니다.

포함:
- 회사, 연구소, 표준 단체, 주요 오픈소스 프로젝트의 공식 발표
- 신뢰할 만한 AI, CS, 소프트웨어 개발 뉴스 출처
- 보안 권고, CVE 기록, 사고 보고서, 심각한 취약점 분석
- 영향력 있는 연구 논문 또는 연구 발표
- 중요한 언어, 프레임워크, 플랫폼, 인프라, 개발 도구의 주요 release note

## 출처 제외 기준
MVP를 시끄럽게 만들거나 신뢰하기 어렵게 만드는 출처는 제외합니다.

제외:
- 일반 개인 기술 블로그 글
- 단순 tutorial
- 실질적인 기술 변화 없이 홍보 성격이 강한 content 또는 vendor marketing
- 영향이 작은 patch release
- rumor 또는 검증되지 않은 community discussion
- 원본 맥락을 추가하지 않는 중복 repost

개인 기술 블로그는 feed 신뢰도를 유지할 만큼 source quality rule이 명확해진 뒤에만 다시 검토합니다.

## Seed Data 기준
큐레이션된 seed data는 자동 수집 전에 제품 모양을 증명해야 합니다.

Seed data는 다음을 만족해야 합니다.
- 여러 event type을 포함: `NEWS`, `OFFICIAL_ANNOUNCEMENT`, `RESEARCH`, `SECURITY`, `RELEASE`
- 여러 primary category를 포함: `AI`, `SECURITY`, `SOFTWARE_ENGINEERING`, `BACKEND`, `FRONTEND`, `DATA`, `INFRA_CLOUD`, `DEVTOOLS`, `CS_RESEARCH`
- `summary`, `whyItMatters`, `topics`, related item 포함
- 향후 재처리를 위해 source URL, source name, published date, 충분한 raw text 또는 source metadata 보존
- 나중에 graph node와 relationship으로 확장할 수 있는 예시 포함

## 수집 전략
먼저 수동 큐레이션 데이터로 시작하고, article model과 insight format이 안정된 뒤 선별 RSS/API collection을 추가합니다.

수집은 다음 처리 흐름을 지원해야 합니다.

```txt
DISCOVER -> FETCH -> EXTRACT -> NORMALIZE -> ENRICH_WITH_LLM -> REVIEW_OR_PUBLISH -> INDEX
```

저장된 article은 다시 scraping하지 않고, embedding, concept extraction, Graph RAG enrichment를 위해 재처리할 수 있어야 합니다.

초기 자동 수집 전략은 명시적 source registry와 source-specific connector를 사용합니다.

초기 source type:
- AI, developer tools, engineering, security, infrastructure update를 위한 공식 RSS/Atom feed
- `cs.AI`, `cs.LG`, `cs.CL` 같은 선별 연구 카테고리를 위한 arXiv API query
- 안정적인 feed가 없는 curated link를 위한 manual 또는 newsletter import

Hacker News는 첫 자동 collector에 포함하지 않습니다. Community aggregator는 나중에 discovery 또는 ranking signal로 유용할 수 있지만, 원문 출처가 아니며 너무 일찍 사용하면 attribution이 흐려질 수 있습니다.

## 품질 원칙
- 낮은 signal의 많은 항목보다 높은 signal의 적은 항목을 우선합니다.
- 출처 attribution을 명확히 보여줍니다.
- AI-generated enrichment를 source truth로 취급하지 않습니다.
- 나중에 summary와 graph relationship을 재생성할 수 있을 만큼 원본 맥락을 보존합니다.
- 왜 Sigak에 포함되어야 하는지 명확한 규칙이 없으면 자동 출처를 추가하지 않습니다.
