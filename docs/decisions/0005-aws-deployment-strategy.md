# 0005: AWS Deployment Strategy

[English](0005-aws-deployment-strategy.md) | [한국어](0005-aws-deployment-strategy.ko.md)

## Status
Accepted

## Date
2026-05-27

## Context
Sigak v0.1 is being developed as a public AI news search MVP. The local MVP now targets PostgreSQL, Elasticsearch, Qdrant, Neo4j, FastAPI, Spring Boot, and React.

Deploying every service as a managed AWS service would make the architecture look more cloud-native, but it would also add cost, IAM, networking, service limits, logging, and operations work before the core search and graph workflow is stable.

The portfolio goal is to prove that Sigak can be run and reviewed, not to build a production-grade AWS platform during the MVP window.

## Decision
Use local Docker Compose as the primary development and reproduction path.

After the core v0.1 flow is stable, add a small AWS deployment path using a single EC2 instance running Docker Compose.

Target AWS deployment shape:

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

AWS is not part of the first search infrastructure milestone. It belongs near the end of the v0.1 cycle, after collection, indexing, hybrid search, graph-aware detail, and metrics can run locally.

Before creating AWS resources:

- configure AWS Budgets and billing alerts
- choose a low-cost region and instance type
- document expected monthly cost and teardown steps
- keep all credentials out of source control
- verify that the local Docker Compose path still works without AWS

## Consequences
- The project keeps a simple, reproducible local path for reviewers.
- AWS deployment becomes a portfolio polish layer, not a blocker for MVP feature work.
- The same Compose topology can be used locally and on EC2 with minimal drift.
- The initial AWS cost surface is easier to understand than ECS, RDS, Amazon OpenSearch Service, and separate managed graph/vector services.
- The EC2 instance will not provide production-grade reliability, backups, autoscaling, or managed database operations.
- A future production version can split services into managed AWS offerings after the MVP proves the product and retrieval workflow.

## Alternatives Considered
- ECS/Fargate for all containers: cleaner managed container orchestration, but more networking, IAM, logging, and cost complexity for the MVP.
- RDS PostgreSQL plus Amazon OpenSearch Service: stronger managed operations, but expensive and unnecessary before the local search workflow is stable.
- App Runner for backend and AI server: simpler application deployment, but it does not solve the multi-store local search stack by itself.
- Lightsail containers: simpler than ECS, but still less aligned with the current Docker Compose setup than a single EC2 host.
- No AWS deployment: simplest and cheapest, but weaker as a portfolio demonstration of deployability.

## Timing
Recommended v0.1 timing:

| Date | Work |
| --- | --- |
| 2026-06-13 | Decide AWS instance shape, set AWS Budgets, and write deployment checklist. |
| 2026-06-14 | Provision EC2, install Docker, configure `.env`, and run Docker Compose. |
| 2026-06-15 | Add security group rules, optional demo URL, and deployment documentation. |
| 2026-06-16 | Run final smoke tests and include AWS deployment notes in the release summary. |

## References
- AWS Budgets: `https://aws.amazon.com/aws-cost-management/aws-budgets/pricing/`
- AWS Fargate pricing: `https://aws.amazon.com/fargate/pricing/`
- AWS App Runner pricing: `https://aws.amazon.com/apprunner/pricing/`
- Amazon RDS pricing: `https://aws.amazon.com/rds/pricing/`
- Amazon Lightsail billing: `https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-frequently-asked-questions-faq-billing-and-account-management.html`
