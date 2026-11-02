# 출처 정책

[English](SOURCE_POLICY.md) | [한국어](SOURCE_POLICY.ko.md)

## 목적
Sigak은 출처의 양보다 품질을 우선합니다.

이 제품은 범용 크롤러가 아닙니다. 실무 엔지니어링 글과 AI·소프트웨어·컴퓨터 과학 분야의 중요한 업데이트를 함께 다룹니다. 구체적인 개발자 커리어 경험도 포함할 수 있지만, 일반 취업 팁이나 채용 공고 전용 수집은 구현하지 않았습니다.

## 출처 포함 기준
신뢰할 수 있고, attribution이 가능하며, 의미 있는 기술 변화를 나타낼 가능성이 높은 출처를 우선합니다.

포함:
- 출처가 명확한 문제 해결·설계·장애·성능 개선 경험
- 배울 맥락이 충분한 개발자의 커리어·협업 경험
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

2026-11-02 현재 자동 수집은 토스·우아한형제들·NAVER D2만 활성화합니다. 이전
OpenAI·Google·GitHub·arXiv6개는 중지했으며 기사·본문·요약 작업 이력을 보관합니다.
홈은 활성 출처의 실제 글만 표시하고 데모는 홈·홈 검색에서 숨깁니다. 보관한 실제
글은 검색·직접 링크로 읽을 수 있습니다. 화면 코드의 출처 목록 대신 registry 상태를 따릅니다.
먼저 수동 큐레이션 데이터로 시작하고, article model과 insight format이 안정된 뒤 선별 RSS/API collection을 추가합니다.

수집은 다음 처리 흐름을 지원해야 합니다.

```txt
DISCOVER -> FETCH_FEED -> EXTRACT -> NORMALIZE -> PUBLISH_SOURCE -> INDEX
NEW_SUPPORTED_ARTICLE -> PREPARE_FULL_BODY (free)
DETAIL_VISIT -> PREPARE_IF_MISSING -> ENRICH_WITH_LLM -> SAVE_SHARED_SUMMARY
```

일반 기사 지원은 PostgreSQL의 승인된 출처별 설정으로 관리합니다. URL에서 RSS/Atom을
찾고 최근3개 원문·본문을 검증해 검토 후 활성화합니다. 전문 피드는 기사 저장 때 본문을
준비하고, 나머지는 무료 worker가 공통/설정된 페이지 본문을 읽습니다. 소개글·원본 HTML·
전문을 구별하며 선택 가능한 제외 규칙은 피드에도 적용합니다.

탐색·피드·본문 요청 모두 정확한 HTTPS 호스트와 검증한 공개 IP를 사용합니다. 같은 호스트
redirect3회,2MiB/15초 제한을 적용하고 로그인·유료벽·봇 차단을 우회하거나 script를 실행하지
않습니다. 공개 API로 전문을 재배포하지 않습니다. 본문 누락·모호성·차단·입력 초과를
소개글이나 앞부분으로 대체하지 않습니다. arXiv 논문 전문은 계속 미지원이며 출처 등록으로
과거 실패·본문·요약을 일괄 재실행하지 않습니다. 유료 생성은 실제 첫 상세 방문과 기존 누적
예산 한도 안에서만 요청합니다.

2026-11-02 preview는 토스 테크·우아한형제들·NAVER D2의 원문 표본을 확인했습니다.
모든 글의 완전성·개인 적합성·AI 요약 품질을 증명한 결과는 아닙니다. D2의 행사·동영상 소개는
게시글 텍스트를 수집하며 영상 transcript를 가져오지는 않습니다. 등록·검증·추출 설정은
[출처 운영 안내](SOURCE_OPERATIONS.ko.md)에 정리합니다.

저장된 article은 다시 scraping하지 않고, embedding, concept extraction, Graph RAG enrichment를 위해 재처리할 수 있어야 합니다.

운영 출처 정본은 PostgreSQL입니다. 코드는 RSS/Atom·arXiv 형식별 connector를 유지하고 출처 URL·허용 호스트·본문/제외 규칙은 데이터로 관리합니다.

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
