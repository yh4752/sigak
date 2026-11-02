# 수치와 측정 조건을 함께 요약하기

작성: 2026-11-02
상태: 사용자가 측정 조건 보존 개선과 기존5달러 한도 유지를 요청했다. 이 승인 범위에서 구현한다.

## 문제와 목표

우아한형제들221의 v2 요약은 LCP37% 개선을 담았으나 Galaxy S22+/Wi-Fi 측정 조건을
생략했다. 원문에는 별도로 실사용자25–35% 결과가 있다. 핵심 수치를 요약할 때 결과의
범위를 바꾸는 조건·비교 기준도 같은 문장에서 보존하고 실험/실사용/추정을 구분한다.

## 선택과 범위

기존 prompt 강화가 가장 작은 변경이다. 별도 구조화 metric 필드는 API/화면을 늘리고,
두 번째 검토 모델은 매번 비용을 추가하므로 이번에는 도입하지 않는다.
기존 gpt-5.6-luna·2048 출력 token·64KiB 입력·cache write 금지·모든 누적 한도를 유지한다.
최대3문장 안에서 주요 수치에 필요한 조건만 고르고, 근거가 없는 조건은 만들지 않는다.
조건 없는 수치가 오해를 낳으면 수치 자체를 생략한다. 규칙은 summary와 whyItMatters 모두에 적용한다.

## 버전과 비용 보존

새 생성은 article-summary-v3-metric-context로 식별한다. 이미 저장된 v2 FULL_TEXT 요약은
계속 READY로 재사용한다. 과거 UNKNOWN은 prompt가 달라도 재호출하지 않는다.
본문 worker도 v2 작업을 완료된 입력으로 인식해 복구 후보가 앞에서 막히지 않게 한다.
공개 DTO는 v2/v3 모두 FULL_TEXT이며 API schema·UI는 바꾸지 않는다.

기존 요약 전체를 업그레이드하지 않는다. 이번 명시 요청의 품질 확인에 한해서221을1회
V3 PENDING으로 예약한다. 기존 current 결과는 성공할 때까지 표시하고 실패해도 지우지 않는다.
동일 body/input hash·이전 READY·UNKNOWN 없음·새 V3 없음·budget 사전 확인과 행 잠금을
검사하는 일회성 운영 SQL만 사용하며 공개 refresh endpoint는 추가하지 않는다.
이 예약도 기존 Spring claim/차감/provider/complete를 통과한다. 추가 보수 차감은 최대0.02달러,
호출 결과가 불명확하면 재전송하지 않는다. 기존 총5달러/앱4달러·총200회/일20회는 불변이다.

## 검증과 배포

무료 fixture에서 trusted instructions와 전체 본문·원문 조건 보존·엄격 출력·기존 입력/비용
한도를 확인한다. 모델 의미 준수는 fixture 통과만으로 주장하지 않는다.
Spring 통합 테스트는 v2 READY 재사용, 구버전 UNKNOWN 유지, body recovery의 구버전 제외,
v3 FULL_TEXT 응답을 검사한다. AI pytest와 backend test/check/bootJar를 실행한다.

양 서비스가 같은 v3 계약을 쓰도록 빌드를 먼저 마치고, 배포 직전 PENDING/DISPATCHED가
없음을 확인한 뒤 backend를 잠시 멈춘 동안 AI→backend를 교체한다. DB·키·예산은 보존한다.
221 실제1회에서 37%와 장비/네트워크 조건, 또는 실사용25–35%와 범위가 정확히 함께
표현되는지 읽고 근거/입력/시간/비용·재사용을 확인한다. 한 표본의 성공은 보편적 품질 보증이 아니다.

## 근거

[OpenAI 공식 prompt engineering 문서](https://developers.openai.com/api/docs/guides/prompt-engineering)는
명확한 instructions와 prompt 변경의 평가를 설명한다. 이번 변경은 그 원칙을 기존 서비스의
확인된 조건 누락 사례에 적용하며 모델·API 호출 방식은 교체하지 않는다.
