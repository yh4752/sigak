# superpowers/ — Spec & Plan 작업 문서

이 디렉터리는 기능 단위 **설계(spec)** 와 **실행 계획(plan)** 을 보관한다.
개발 생애주기(`AGENTS.md` §3)의 "스펙 → 계획" 단계가 가리키는 실제 위치다.

이 문서들은 `superpowers` 스킬의 도움으로 작성되지만, **디렉터리와 커밋된 `.md`
파일은 이 저장소의 소유**다. 자유롭게 읽고, 수정하고, 추가해도 된다.

> 참고: 스킬의 런타임 작업 상태는 저장소 루트의 숨김 폴더 `.superpowers/`에 따로
> 있고 일부는 `.gitignore` 처리돼 있다. 그쪽은 스킬이 관리하므로 손대지 않는다.
> 이 `docs/superpowers/`는 사람이 읽고 리뷰하는 영구 문서다.

## 구조

```txt
docs/superpowers/
├── specs/   설계 문서 — "무엇을, 왜, 어떤 계약으로" 만들 것인가
└── plans/   실행 계획 — spec을 체크박스 task로 쪼갠 구현 단계
```

한 기능은 보통 spec 1개와 plan 1개가 짝을 이룬다. plan은 첫머리에서 자신의
spec을 참조한다.

## 파일명 규칙

```txt
specs/YYYY-MM-DD-<topic>-design.md
plans/YYYY-MM-DD-<topic>.md
```

날짜는 작업 시작일 기준. 같은 topic이면 spec과 plan의 `<topic>`을 맞춘다.
(예: `2026-05-30-hybrid-search-design.md` ↔ `2026-05-30-hybrid-search.md`)

## Spec 권장 구조

설계 의도를 고정하는 문서. 구현 전에 쓰고, 구현 중에는 기준으로 삼는다.

```md
# <기능> Design

## Summary          무엇을 바꾸는가 (1~2문단)
## Current State    지금은 어떻게 동작하는가
## Design Goal      무엇을 달성하고 무엇을 깨지 않을 것인가 (공개 계약 명시)
## (설계 세부)       데이터 흐름, 경계, 응답 형태, 트레이드오프
```

핵심 원칙: **공개 계약(public contract)이 무엇이고 내부 구현이 무엇인지** 구분해
적는다. 내부는 바뀌어도 계약은 유지된다는 것을 명시한다.

## Plan 권장 구조

spec을 실제 구현 단계로 쪼갠 문서. 에이전트가 task-by-task로 실행한다.

```md
# <기능> Implementation Plan

> 실행 방식 안내(어떤 sub-skill로 task별 실행할지)

**Goal:**        한 줄 목표
**Architecture:** 핵심 구조 결정 요약
**Tech Stack:**   사용할 기술

## Reference Spec   먼저 읽을 spec 경로
## File Structure   Create / Modify 할 파일 목록과 책임
## Tasks            - [ ] 체크박스로 추적 가능한 작업 단위
```

각 task는 검증 가능한 단위로 쪼갠다. plan을 따라 구현한 뒤에는 `AGENTS.md` §4의
Definition of Done 게이트로 검증하고, §6의 세션 마무리 루프로 닫는다.

## 생애주기에서의 위치

```txt
ADR(docs/decisions/) -> spec(여기) -> plan(여기) -> 구현 -> 검증 -> STATUS 갱신 -> dev-log
```

구조적 결정은 ADR로, 기능 설계는 spec으로, 실행은 plan으로 분리한다. 셋의 역할이
겹치면 ADR=왜(결정), spec=무엇(설계), plan=어떻게(단계) 기준으로 나눈다.
