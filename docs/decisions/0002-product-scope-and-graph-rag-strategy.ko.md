# 0002: 제품 범위와 Graph RAG 전략

[English](0002-product-scope-and-graph-rag-strategy.md) | [한국어](0002-product-scope-and-graph-rag-strategy.ko.md)

## 상태
승인됨

## 날짜
2026-05-05

## 맥락
Sigak은 백엔드, 프론트엔드, AI 기능을 더 추가하기 전에 명확한 제품 정체성이 필요합니다.

이 프로젝트는 일반 뉴스 리더나 단순 article summarizer가 되면 안 됩니다. 의도한 1.0.0 제품은 AI, 소프트웨어 개발, 컴퓨터 과학에 집중한 기술 뉴스 인사이트 플랫폼입니다.

또한 팀은 Graph RAG를 1.0.0 방향에 포함하고 싶지만, 핵심 article/insight workflow가 안정되기 전에 큰 Obsidian-style graph explorer를 만들고 싶지는 않습니다.

## 결정
Sigak 1.0.0은 중요한 기술 변화를 맥락과 관계로 설명하는 데 집중합니다.

제품은 다음을 포함합니다.
- 큐레이션된 기술 뉴스와 업데이트
- summary, importance, why-it-matters insight를 제공하는 article detail page
- article마다 하나의 `primaryCategory`와 여러 `topics`
- Graph RAG 준비가 가능한 article/concept metadata
- 제한된 relationship-based insight 또는 graph-backed retrieval

제품은 다음을 미룹니다.
- Obsidian-style full graph exploration
- 광범위한 automated crawling
- 고급 personalized recommendation
- saved article과 user account

Data collection은 curated seed data에서 시작하고, article model과 insight format이 안정된 뒤 selected RSS/API source를 추가합니다.

## 결과
- 첫 API는 나중에 enrichment와 graph relationship을 지원할 수 있도록 article metadata를 모델링해야 합니다.
- Curated data는 title과 URL뿐 아니라 insight quality를 검증할 충분한 field를 포함해야 합니다.
- Search는 keyword matching으로 시작할 수 있지만, API와 data model은 semantic 또는 graph-aware retrieval을 막으면 안 됩니다.
- 광범위한 automated collection 전에 source quality rule이 필요합니다.
- Full graph visualization은 1.0.0 MVP를 늦추지 않고 나중의 차별화 요소로 남깁니다.

## 검토한 대안
- 1.0.0에 full graph explorer 포함: 시각적으로 매력적이지만 frontend complexity, graph data quality, MVP 일정 측면에서 비용이 큽니다.
- Graph RAG를 1.0.0 이후로만 미루기: 단순하지만 제품의 기술적 차별성을 약하게 만들고 중요한 data-model 결정을 늦춥니다.
- Article마다 multi-category classification 사용: 유연하지만 초기 UI와 filtering에는 하나의 `primaryCategory`와 여러 `topics`보다 복잡합니다.
- Broad RSS/API collection 먼저 구현: 빠르게 volume을 만들 수 있지만 source quality와 enrichment rule이 명확해지기 전에 noisy data가 생길 위험이 큽니다.
