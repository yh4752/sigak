# 0005: AWS Deployment Strategy

[English](0005-aws-deployment-strategy.md) | [한국어](0005-aws-deployment-strategy.ko.md)

## 상태
승인됨

## 날짜
2026-05-27

## 맥락
Sigak v0.1은 공개 가능한 AI news search MVP로 개발하고 있습니다. 로컬 MVP는 PostgreSQL, Elasticsearch, Qdrant, Neo4j, FastAPI, Spring Boot, React를 대상으로 합니다.

모든 서비스를 AWS managed service로 나누어 배포하면 더 cloud-native하게 보일 수 있습니다. 하지만 core search와 graph workflow가 안정화되기 전에 비용, IAM, network, service limit, logging, 운영 부담이 크게 늘어납니다.

포트폴리오 목표는 Sigak이 실행 가능하고 검토 가능한 프로젝트임을 증명하는 것입니다. MVP 기간에 production-grade AWS platform을 만드는 것이 목표는 아닙니다.

## 결정
Local Docker Compose를 기본 개발 및 재현 경로로 사용합니다.

Core v0.1 흐름이 안정화된 뒤에는 단일 EC2 instance에서 Docker Compose를 실행하는 작은 AWS 배포 경로를 추가합니다.

목표 AWS 배포 형태:

```txt
EC2 instance
-> Docker Compose
-> Spring Boot backend
-> FastAPI AI server
-> PostgreSQL
-> Elasticsearch
-> Qdrant
-> Neo4j
-> frontend static build or Vite preview only for demo
```

AWS 배포는 첫 search infrastructure milestone에 포함하지 않습니다. Collection, indexing, hybrid search, graph-aware detail, metrics가 로컬에서 동작한 뒤 v0.1 후반부에 추가합니다.

AWS resource를 만들기 전에 다음을 먼저 수행합니다.

- AWS Budgets와 billing alert 설정
- 저비용 region과 instance type 선택
- 예상 월 비용과 teardown step 문서화
- credential을 source control에 넣지 않기
- AWS 없이도 local Docker Compose 경로가 계속 동작하는지 확인

## 결과
- 리뷰어가 실행할 수 있는 단순하고 재현 가능한 local path를 유지합니다.
- AWS 배포는 MVP 기능 개발을 막는 요소가 아니라 포트폴리오 polish layer가 됩니다.
- 같은 Compose topology를 로컬과 EC2에서 큰 차이 없이 사용할 수 있습니다.
- ECS, RDS, Amazon OpenSearch Service, 별도 managed graph/vector service를 조합하는 것보다 초기 AWS 비용 구조를 이해하기 쉽습니다.
- 단일 EC2 배포는 production-grade reliability, backup, autoscaling, managed database 운영을 제공하지 않습니다.
- 향후 production version에서는 MVP가 제품과 retrieval workflow를 증명한 뒤 managed AWS service로 분리할 수 있습니다.

## 검토한 대안
- 모든 container를 ECS/Fargate에 배포: managed container orchestration에는 좋지만 MVP에는 networking, IAM, logging, 비용 복잡도가 큽니다.
- RDS PostgreSQL과 Amazon OpenSearch Service 사용: managed operation에는 좋지만 local search workflow가 안정화되기 전에는 비용과 범위가 큽니다.
- Backend와 AI server를 App Runner에 배포: application 배포는 단순하지만 multi-store search stack 문제를 해결하지는 않습니다.
- Lightsail containers 사용: ECS보다 단순하지만 현재 Docker Compose setup과는 단일 EC2 host만큼 직접적으로 맞지 않습니다.
- AWS 배포를 하지 않음: 가장 단순하고 저렴하지만 포트폴리오에서 deployability를 보여주는 힘이 약합니다.

## 시점
권장 v0.1 시점:

| 날짜 | 작업 |
| --- | --- |
| 2026-06-13 | AWS instance 형태 결정, AWS Budgets 설정, 배포 checklist 작성 |
| 2026-06-14 | EC2 provision, Docker 설치, `.env` 구성, Docker Compose 실행 |
| 2026-06-15 | Security group, 선택적 demo URL, 배포 문서 추가 |
| 2026-06-16 | 최종 smoke test와 release summary에 AWS 배포 note 포함 |

## 참고
- AWS Budgets: `https://aws.amazon.com/aws-cost-management/aws-budgets/pricing/`
- AWS Fargate pricing: `https://aws.amazon.com/fargate/pricing/`
- AWS App Runner pricing: `https://aws.amazon.com/apprunner/pricing/`
- Amazon RDS pricing: `https://aws.amazon.com/rds/pricing/`
- Amazon Lightsail billing: `https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-frequently-asked-questions-faq-billing-and-account-management.html`
