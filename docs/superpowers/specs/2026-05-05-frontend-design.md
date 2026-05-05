# Sigak Frontend Design Spec

**Date:** 2026-05-05
**Scope:** 홈 화면 + 아티클 디테일 페이지 시각 디자인

---

## 1. 디자인 방향

**톤:** 전문적이고 미니멀 — Bloomberg, Financial Times 스타일
**모드:** 라이트 모드 (기본)
**정보 원칙:** 최대한 간략하게. 필요한 정보만 노출.

### 핵심 결정
- `importanceScore`는 UI에 노출하지 않는다. 큐레이션 품질 자체가 신뢰감을 준다.
- 타이포그래피 중심: 색상이나 아이콘보다 폰트 계층으로 정보 구조를 전달한다.
- 카테고리는 텍스트 태그로만 표시한다.

---

## 2. 색상 시스템

| 역할 | 값 | 용도 |
|---|---|---|
| 베이스 | `#FFFFFF` | 페이지 배경 |
| 텍스트 | `#111111` | 제목, 주요 텍스트 |
| 서브 텍스트 | `#374151` | 본문 |
| 메타 텍스트 | `#6B7280`, `#9CA3AF` | 출처, 날짜, 라벨 |
| 보더 | `#E5E7EB`, `#F3F4F6` | 구분선 |
| 악센트 | `#4F46E5` (인디고) | 링크, 카테고리 태그, 강조 |
| 악센트 배경 | `#EEF2FF` | 카테고리 태그 배경 |
| 악센트 서피스 | `#F8F7FF` | Why It Matters 박스 배경 |

---

## 3. 타이포그래피

| 역할 | 폰트 | 크기 | 비고 |
|---|---|---|---|
| 로고 | Georgia (serif) | 16px, bold | 신뢰감과 정체성 |
| 기사 제목 (리스트) | Georgia (serif) | 13–14px | 신문 목록 느낌 |
| 기사 제목 (디테일) | Georgia (serif) | 24px, bold | 크고 무게감 있게 |
| 본문 (summary, whyItMatters) | Georgia (serif) | 14px | 읽기 편한 행간 1.7 |
| UI 크롬 (라벨, 메타, 태그) | -apple-system / sans-serif | 10–12px | 현대적, 기능적 |

---

## 4. 컴포넌트 스펙

### 4.1 네비게이션 바
- 왼쪽: 로고 `SIGAK` (Georgia, 16px, bold)
- 오른쪽: 카테고리 힌트 텍스트 `AI · Security · Engineering` (그레이)
- 하단 보더: `2px solid #111`

### 4.2 검색바 (홈)
- 위치: 페이지 중앙 배치
- 스타일: 둥근 검색바 (`border-radius: 20px`)
- 보더: `1.5px solid #E0E7FF`
- 배경: `#F8F7FF` (연한 인디고)
- 너비: 약 280px

### 4.3 기사 리스트 아이템 (홈)
표시 정보: **제목 + 카테고리 태그 + 출처 + 발행일**

```
[기사 제목 (Georgia, 14px)]          [출처 · 날짜]
[카테고리 태그]
─────────────────────────────────────
```

- 행 사이 구분: `1px solid #F3F4F6` (연한 보더)
- 출처/날짜: 오른쪽 정렬, `#9CA3AF`
- 카테고리 태그: `color: #4F46E5`, `background: #EEF2FF`, `border-radius: 3px`

### 4.4 섹션 헤더 (홈)
- 텍스트: 대문자, `10px`, `letter-spacing: 0.1em`, `color: #6B7280`
- 하단: `1px solid #E5E7EB`
- 예: `TODAY'S IMPORTANT NEWS`, `POPULAR NEWS`

### 4.5 검색 결과 화면
- 별도 페이지 없음. 홈 화면에서 검색바 입력 시 리스트만 필터링 결과로 교체
- 섹션 헤더: `SEARCH RESULTS FOR "query"`
- 기사 리스트 아이템 스타일: 홈과 동일 (컴포넌트 재사용)
- 결과 없을 때: 간단한 텍스트 메시지 (`No results found.`)

### 4.6 네비게이션 바 — 디테일 페이지
- 홈과 동일 구조
- 오른쪽 힌트 텍스트 대신: `← Back to News` (인디고 링크, `color: #4F46E5`)

---

## 5. 아티클 디테일 페이지

### 구조 (위 → 아래)

```
[카테고리 태그] [이벤트 타입 태그]

[기사 제목 — Georgia, 24px, bold]

[출처] · [날짜] · [원문 링크 ↗]
─────────────────────────────

SUMMARY
[본문 텍스트 — Georgia, 14px, line-height 1.7]

WHY IT MATTERS
┃ [인디고 왼쪽 보더 박스 — 배경 #F8F7FF]
┃ [본문 텍스트]

─────────────────────────────

TOPICS
[칩1] [칩2] [칩3] ...

─────────────────────────────

RELATED ARTICLES
[관련 기사 제목]                [카테고리 태그]
[관련 기사 제목]                [카테고리 태그]
```

### 세부 스펙
- **카테고리 태그**: 인디고 배경 (`#EEF2FF`)
- **이벤트 타입 태그**: 그레이 배경 (`#F3F4F6`, `color: #6B7280`)
- **바이라인 링크** (출처, 원문): `color: #4F46E5`
- **Why It Matters 박스**: `border-left: 3px solid #4F46E5`, `background: #F8F7FF`
- **Topic 칩**: `background: #F3F4F6`, `border: 1px solid #E5E7EB`, `border-radius: 12px`
- **Related 리스트**: 홈 리스트와 동일한 패턴 (일관성 유지)

---

## 6. 레이아웃

- **홈**: 최대 너비 `800px` 중앙 정렬. 검색바 → 섹션 → 리스트
- **디테일**: 최대 너비 `680px` 중앙 정렬. 단일 컬럼
- **모바일**: 단일 컬럼 그대로 유지 (복잡한 반응형 MVP 범위 밖)

---

## 7. MVP 범위 밖

- 다크 모드
- 카테고리별 필터 탭 (나중)
- Graph RAG 시각화 (나중)
- 반응형 모바일 최적화 (나중)
- 이미지/썸네일

---

## 8. 참고 Mockup

브레인스토밍 세션 mockup 파일 위치:
`.superpowers/brainstorm/64144-1777971800/content/`
- `design-directions.html` — 3가지 초기 방향 비교
- `design-ab-mix.html` — Mix 1 / Mix 2 비교
- `design-detail.html` — 아티클 디테일 확정 mockup
