# Phase 6 Persistence MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the backend's in-memory article list with PostgreSQL-backed persistence while preserving the current public article API.

**Architecture:** Spring Boot remains the public API boundary. Flyway owns schema and seed data, Spring Data JPA reads persisted article rows, and `ArticleService` maps entities into the existing `ArticleResponse` DTO. The schema keeps article metadata, raw content, enrichment output, topics, and related article links relationally separate.

**Tech Stack:** Kotlin, Spring Boot 3.3.5, Spring Data JPA, PostgreSQL, Flyway, Testcontainers, Docker Compose, JUnit 5, MockMvc.

---

## Scope Check

This plan implements one subsystem: Phase 6 backend persistence. It intentionally avoids Elasticsearch, Qdrant, embeddings, full Graph RAG concepts, admin review flows, user accounts, and frontend response changes.

## File Structure

Create or modify these files:

- Modify: `backend/build.gradle`
  - Add JPA, Flyway, PostgreSQL, Kotlin JPA plugin, and Testcontainers dependencies.
- Create: `backend/src/main/resources/application.yml`
  - Configure datasource, Flyway, and Hibernate validation with environment-variable defaults.
- Create: `backend/src/main/resources/db/migration/V1__create_persistence_schema.sql`
  - Create relational schema and indexes.
- Create: `backend/src/main/resources/db/migration/V2__seed_article_data.sql`
  - Move the five current mock articles into SQL seed data.
- Modify: `infra/docker-compose.yml`
  - Add local PostgreSQL service.
- Create: `backend/src/test/kotlin/com/sigak/support/PostgresIntegrationTest.kt`
  - Share Testcontainers PostgreSQL configuration.
- Create: `backend/src/test/kotlin/com/sigak/persistence/PersistenceSmokeTest.kt`
  - Verify Flyway migrations and seed data.
- Create: `backend/src/main/kotlin/com/sigak/source/domain/NewsSourceEntity.kt`
  - Persist source metadata.
- Create: `backend/src/main/kotlin/com/sigak/article/domain/EventType.kt`
  - Article event type enum.
- Create: `backend/src/main/kotlin/com/sigak/article/domain/PrimaryCategory.kt`
  - Article primary category enum.
- Create: `backend/src/main/kotlin/com/sigak/article/domain/ProcessingStatus.kt`
  - Article processing status enum.
- Create: `backend/src/main/kotlin/com/sigak/article/domain/RelationType.kt`
  - Article relation type enum.
- Create: `backend/src/main/kotlin/com/sigak/article/domain/ArticleEntity.kt`
  - Persist article metadata and relationships.
- Create: `backend/src/main/kotlin/com/sigak/article/domain/ArticleRawContentEntity.kt`
  - Persist raw and extracted source text.
- Create: `backend/src/main/kotlin/com/sigak/article/domain/ArticleEnrichmentEntity.kt`
  - Persist enrichment history.
- Create: `backend/src/main/kotlin/com/sigak/article/domain/ArticleTopicEntity.kt`
  - Persist ordered topics.
- Create: `backend/src/main/kotlin/com/sigak/article/domain/ArticleRelationEntity.kt`
  - Persist related article links.
- Create: `backend/src/main/kotlin/com/sigak/article/repository/ArticleRepository.kt`
  - Read persisted articles.
- Modify: `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`
  - Replace in-memory list with repository reads and DTO mapping.
- Modify: `backend/src/main/kotlin/com/sigak/article/controller/ArticleController.kt`
  - Update OpenAPI description so it no longer says in-memory.
- Modify: `backend/src/test/kotlin/com/sigak/article/service/ArticleServiceTest.kt`
  - Convert to PostgreSQL-backed Spring integration test.
- Modify: `backend/src/test/kotlin/com/sigak/article/controller/ArticleControllerTest.kt`
  - Reuse Testcontainers PostgreSQL and keep API shape assertions.
- Create: `docs/decisions/0004-persistence-mvp.md`
  - Record the persistence decision.
- Modify: `README.md`
  - Add PostgreSQL run notes.
- Modify: `backend/README.md`
  - Add backend database setup.
- Modify: `infra/README.md`
  - Document Docker Compose PostgreSQL usage.
- Modify: `docs/API_SPEC.md`
  - Note that article data is persisted while the API shape remains stable.
- Modify: `docs/ROADMAP.md`
  - Mark Phase 6 items complete after implementation and verification.

---

### Task 1: Add Persistence Dependencies, Runtime Configuration, and Docker Compose PostgreSQL

**Files:**
- Modify: `backend/build.gradle`
- Create: `backend/src/main/resources/application.yml`
- Modify: `infra/docker-compose.yml`

- [ ] **Step 1: Update Gradle plugins and dependencies**

Modify `backend/build.gradle` to include the Kotlin JPA plugin and persistence dependencies:

```groovy
plugins {
    id 'org.jetbrains.kotlin.jvm' version '1.9.25'
    id 'org.jetbrains.kotlin.plugin.spring' version '1.9.25'
    id 'org.jetbrains.kotlin.plugin.jpa' version '1.9.25'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.sigak'
version = '0.0.1-SNAPSHOT'

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'com.fasterxml.jackson.module:jackson-module-kotlin'
    implementation 'org.jetbrains.kotlin:kotlin-reflect'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
    runtimeOnly 'org.postgresql:postgresql'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.jetbrains.kotlin:kotlin-test-junit5'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Add backend application configuration**

Create `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: ${SIGAK_DB_URL:jdbc:postgresql://localhost:5432/sigak}
    username: ${SIGAK_DB_USERNAME:sigak}
    password: ${SIGAK_DB_PASSWORD:sigak}
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
```

- [ ] **Step 3: Add PostgreSQL service to Docker Compose**

Replace `infra/docker-compose.yml` with:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: sigak-postgres
    environment:
      POSTGRES_DB: sigak
      POSTGRES_USER: sigak
      POSTGRES_PASSWORD: sigak
    ports:
      - "5432:5432"
    volumes:
      - sigak-postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U sigak -d sigak"]
      interval: 5s
      timeout: 5s
      retries: 10

volumes:
  sigak-postgres-data:
```

- [ ] **Step 4: Run dependency resolution**

Run:

```bash
cd backend
./gradlew dependencies --configuration runtimeClasspath
```

Expected: command exits with code `0` and shows Spring Data JPA, Flyway, and PostgreSQL dependencies.

- [ ] **Step 5: Commit configuration changes**

```bash
git add backend/build.gradle backend/src/main/resources/application.yml infra/docker-compose.yml
git commit -m "chore: add postgres persistence configuration"
```

---

### Task 2: Add Flyway Schema and Seed Data

**Files:**
- Create: `backend/src/test/kotlin/com/sigak/support/PostgresIntegrationTest.kt`
- Create: `backend/src/test/kotlin/com/sigak/persistence/PersistenceSmokeTest.kt`
- Create: `backend/src/main/resources/db/migration/V1__create_persistence_schema.sql`
- Create: `backend/src/main/resources/db/migration/V2__seed_article_data.sql`

- [ ] **Step 1: Create shared PostgreSQL Testcontainers base**

Create `backend/src/test/kotlin/com/sigak/support/PostgresIntegrationTest.kt`:

```kotlin
package com.sigak.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
abstract class PostgresIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        private val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("sigak_test")
            withUsername("sigak")
            withPassword("sigak")
        }

        @DynamicPropertySource
        @JvmStatic
        fun registerPostgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("spring.flyway.enabled") { "true" }
        }
    }
}
```

- [ ] **Step 2: Write failing persistence smoke test**

Create `backend/src/test/kotlin/com/sigak/persistence/PersistenceSmokeTest.kt`:

```kotlin
package com.sigak.persistence

import com.sigak.SigakBackendApplication
import com.sigak.support.PostgresIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(classes = [SigakBackendApplication::class])
class PersistenceSmokeTest : PostgresIntegrationTest() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun flywayCreatesSchemaAndSeedsArticles() {
        val articleCount = jdbcTemplate.queryForObject(
            "select count(*) from articles",
            Int::class.java
        )
        val sourceCount = jdbcTemplate.queryForObject(
            "select count(*) from news_sources",
            Int::class.java
        )
        val currentEnrichmentCount = jdbcTemplate.queryForObject(
            "select count(*) from article_enrichments where is_current = true",
            Int::class.java
        )

        assertEquals(5, articleCount)
        assertEquals(5, sourceCount)
        assertEquals(5, currentEnrichmentCount)
    }
}
```

- [ ] **Step 3: Run smoke test and verify it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.persistence.PersistenceSmokeTest
```

Expected: FAIL because the `articles` and related tables do not exist yet.

- [ ] **Step 4: Add Flyway schema migration**

Create `backend/src/main/resources/db/migration/V1__create_persistence_schema.sql`:

```sql
create table news_sources (
    id bigserial primary key,
    source_key varchar(120) not null unique,
    name varchar(255) not null,
    type varchar(40) not null,
    url text not null,
    category_hint varchar(80)
);

create table articles (
    id bigserial primary key,
    source_id bigint not null references news_sources(id) on delete restrict,
    external_id varchar(255),
    title varchar(500) not null,
    url text not null unique,
    canonical_url text not null,
    published_at timestamptz not null,
    event_type varchar(60) not null,
    primary_category varchar(80) not null,
    importance_score integer not null check (importance_score between 0 and 100),
    processing_status varchar(60) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table article_raw_contents (
    id bigserial primary key,
    article_id bigint not null unique references articles(id) on delete cascade,
    raw_content text not null,
    extracted_text text not null,
    collected_at timestamptz not null
);

create table article_enrichments (
    id bigserial primary key,
    article_id bigint not null references articles(id) on delete cascade,
    summary text not null,
    why_it_matters text not null,
    suggested_primary_category varchar(80),
    suggested_importance_score integer check (
        suggested_importance_score is null or suggested_importance_score between 0 and 100
    ),
    model_name varchar(120) not null,
    prompt_version varchar(80) not null,
    is_current boolean not null default false,
    enriched_at timestamptz not null
);

create table article_topics (
    id bigserial primary key,
    article_id bigint not null references articles(id) on delete cascade,
    topic varchar(160) not null,
    position integer not null,
    unique (article_id, position),
    unique (article_id, topic)
);

create table article_relations (
    id bigserial primary key,
    source_article_id bigint not null references articles(id) on delete cascade,
    target_article_id bigint not null references articles(id) on delete cascade,
    relation_type varchar(80) not null,
    reason text,
    check (source_article_id <> target_article_id),
    unique (source_article_id, target_article_id, relation_type)
);

create unique index ux_article_enrichments_current
    on article_enrichments (article_id)
    where is_current = true;

create index ix_articles_source_id on articles(source_id);
create index ix_articles_published_at on articles(published_at desc);
create index ix_articles_importance_score on articles(importance_score desc);
create index ix_article_topics_topic_lower on article_topics(lower(topic));
create index ix_article_relations_source on article_relations(source_article_id);
create index ix_article_relations_target on article_relations(target_article_id);
```

- [ ] **Step 5: Add seed data migration**

Create `backend/src/main/resources/db/migration/V2__seed_article_data.sql`:

```sql
insert into news_sources (id, source_key, name, type, url, category_hint) values
    (1, 'openai', 'OpenAI', 'RSS_ATOM', 'https://openai.com/news/rss.xml', 'AI'),
    (2, 'postgresql-weekly', 'PostgreSQL Weekly', 'RSS_ATOM', 'https://example.com/postgresql-weekly/feed.xml', 'DATA'),
    (3, 'security-advisory-board', 'Security Advisory Board', 'RSS_ATOM', 'https://example.com/security-advisory-board/feed.xml', 'SECURITY'),
    (4, 'arxiv', 'arXiv', 'ARXIV', 'https://export.arxiv.org/api/query', 'CS_RESEARCH'),
    (5, 'cncf', 'Cloud Native Computing Foundation', 'RSS_ATOM', 'https://www.cncf.io/feed/', 'INFRA_CLOUD');

insert into articles (
    id, source_id, external_id, title, url, canonical_url, published_at,
    event_type, primary_category, importance_score, processing_status,
    created_at, updated_at
) values
    (1, 1, 'openai-agent-evals', 'OpenAI Releases Agent Evaluation Toolkit', 'https://example.com/articles/openai-agent-evals', 'https://example.com/articles/openai-agent-evals', '2026-05-01T09:00:00Z', 'OFFICIAL_ANNOUNCEMENT', 'AI', 88, 'PUBLISHED', '2026-05-06T00:00:00Z', '2026-05-06T00:00:00Z'),
    (2, 2, 'postgres-vector-indexes', 'PostgreSQL Adds Native Vector Index Improvements', 'https://example.com/articles/postgres-vector-indexes', 'https://example.com/articles/postgres-vector-indexes', '2026-05-02T11:30:00Z', 'RELEASE', 'DATA', 82, 'PUBLISHED', '2026-05-06T00:00:00Z', '2026-05-06T00:00:00Z'),
    (3, 3, 'ai-toolchain-package-attack', 'Critical Package Registry Attack Targets AI Toolchains', 'https://example.com/articles/ai-toolchain-package-attack', 'https://example.com/articles/ai-toolchain-package-attack', '2026-05-03T15:45:00Z', 'SECURITY', 'SECURITY', 93, 'PUBLISHED', '2026-05-06T00:00:00Z', '2026-05-06T00:00:00Z'),
    (4, 4, 'graph-rag-failure-modes', 'New Research Maps Failure Modes in Graph RAG Systems', 'https://example.com/articles/graph-rag-failure-modes', 'https://example.com/articles/graph-rag-failure-modes', '2026-05-04T08:20:00Z', 'RESEARCH', 'CS_RESEARCH', 86, 'PUBLISHED', '2026-05-06T00:00:00Z', '2026-05-06T00:00:00Z'),
    (5, 5, 'kubernetes-lts-policy', 'Kubernetes Project Updates Long-Term Support Policy', 'https://example.com/articles/kubernetes-lts-policy', 'https://example.com/articles/kubernetes-lts-policy', '2026-05-05T10:10:00Z', 'NEWS', 'INFRA_CLOUD', 78, 'PUBLISHED', '2026-05-06T00:00:00Z', '2026-05-06T00:00:00Z');

insert into article_raw_contents (article_id, raw_content, extracted_text, collected_at) values
    (1, 'OpenAI introduced a toolkit for evaluating agent behavior in multi-step workflows.', 'OpenAI introduced a toolkit for evaluating agent behavior in multi-step workflows.', '2026-05-06T00:00:00Z'),
    (2, 'A PostgreSQL release improved vector index performance for retrieval-heavy workloads.', 'A PostgreSQL release improved vector index performance for retrieval-heavy workloads.', '2026-05-06T00:00:00Z'),
    (3, 'A coordinated package registry attack targeted developer environments that install AI tooling.', 'A coordinated package registry attack targeted developer environments that install AI tooling.', '2026-05-06T00:00:00Z'),
    (4, 'Researchers categorized common failure modes in Graph RAG systems and proposed evaluation criteria.', 'Researchers categorized common failure modes in Graph RAG systems and proposed evaluation criteria.', '2026-05-06T00:00:00Z'),
    (5, 'The Kubernetes project updated its support policy for production operators managing long-lived clusters.', 'The Kubernetes project updated its support policy for production operators managing long-lived clusters.', '2026-05-06T00:00:00Z');

insert into article_enrichments (
    article_id, summary, why_it_matters, suggested_primary_category,
    suggested_importance_score, model_name, prompt_version, is_current, enriched_at
) values
    (1, 'OpenAI introduced a toolkit for evaluating agent behavior in multi-step workflows.', 'Agent evaluation is becoming a practical requirement as teams move from demos to production workflows.', 'AI', 88, 'curated-seed', 'seed-v1', true, '2026-05-06T00:00:00Z'),
    (2, 'A PostgreSQL release improved vector index performance for retrieval-heavy workloads.', 'Better vector indexing makes it easier to build search and RAG features without adding infrastructure too early.', 'DATA', 82, 'curated-seed', 'seed-v1', true, '2026-05-06T00:00:00Z'),
    (3, 'A coordinated package registry attack targeted developer environments that install AI tooling.', 'AI development stacks often combine fast-moving packages, credentials, and automation, which raises the blast radius of supply chain attacks.', 'SECURITY', 93, 'curated-seed', 'seed-v1', true, '2026-05-06T00:00:00Z'),
    (4, 'Researchers categorized common failure modes in Graph RAG systems and proposed evaluation criteria.', 'Understanding graph retrieval failures helps teams design relationship-aware insight features with better evidence quality.', 'CS_RESEARCH', 86, 'curated-seed', 'seed-v1', true, '2026-05-06T00:00:00Z'),
    (5, 'The Kubernetes project updated its support policy for production operators managing long-lived clusters.', 'Support windows shape upgrade planning, security posture, and operational cost for infrastructure teams.', 'INFRA_CLOUD', 78, 'curated-seed', 'seed-v1', true, '2026-05-06T00:00:00Z');

insert into article_topics (article_id, topic, position) values
    (1, 'LLM agents', 0),
    (1, 'evaluation', 1),
    (1, 'production AI', 2),
    (2, 'PostgreSQL', 0),
    (2, 'vector search', 1),
    (2, 'database indexing', 2),
    (3, 'supply chain security', 0),
    (3, 'package registry', 1),
    (3, 'AI tooling', 2),
    (4, 'Graph RAG', 0),
    (4, 'knowledge graphs', 1),
    (4, 'retrieval quality', 2),
    (5, 'Kubernetes', 0),
    (5, 'release policy', 1),
    (5, 'platform operations', 2);

insert into article_relations (source_article_id, target_article_id, relation_type, reason) values
    (1, 3, 'RELATED', 'Both articles affect production AI development workflows.'),
    (1, 5, 'RELATED', 'Both articles are relevant to production engineering practices.'),
    (2, 1, 'RELATED', 'Vector indexing supports retrieval-heavy AI systems.'),
    (2, 5, 'RELATED', 'Both articles influence infrastructure choices for technical teams.'),
    (3, 1, 'RELATED', 'AI tooling increases the security impact of package registry attacks.'),
    (3, 4, 'RELATED', 'Both articles concern risks in AI-oriented technical systems.'),
    (4, 1, 'RELATED', 'Graph RAG evaluation connects to agent and retrieval evaluation.'),
    (4, 5, 'RELATED', 'Both articles affect reliability planning for technical systems.'),
    (5, 2, 'RELATED', 'Infrastructure support policy and database indexing both shape platform operations.'),
    (5, 3, 'RELATED', 'Long-term operations and supply-chain security both affect production risk.');

select setval('news_sources_id_seq', (select max(id) from news_sources));
select setval('articles_id_seq', (select max(id) from articles));
```

- [ ] **Step 6: Run smoke test and verify it passes**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.persistence.PersistenceSmokeTest
```

Expected: PASS. The test should count 5 articles, 5 sources, and 5 current enrichments.

- [ ] **Step 7: Commit migrations and smoke test**

```bash
git add backend/src/test/kotlin/com/sigak/support/PostgresIntegrationTest.kt backend/src/test/kotlin/com/sigak/persistence/PersistenceSmokeTest.kt backend/src/main/resources/db/migration/V1__create_persistence_schema.sql backend/src/main/resources/db/migration/V2__seed_article_data.sql
git commit -m "feat: add article persistence schema"
```

---

### Task 3: Add JPA Entities and Repository

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/source/domain/NewsSourceEntity.kt`
- Create: `backend/src/main/kotlin/com/sigak/article/domain/EventType.kt`
- Create: `backend/src/main/kotlin/com/sigak/article/domain/PrimaryCategory.kt`
- Create: `backend/src/main/kotlin/com/sigak/article/domain/ProcessingStatus.kt`
- Create: `backend/src/main/kotlin/com/sigak/article/domain/RelationType.kt`
- Create: `backend/src/main/kotlin/com/sigak/article/domain/ArticleEntity.kt`
- Create: `backend/src/main/kotlin/com/sigak/article/domain/ArticleRawContentEntity.kt`
- Create: `backend/src/main/kotlin/com/sigak/article/domain/ArticleEnrichmentEntity.kt`
- Create: `backend/src/main/kotlin/com/sigak/article/domain/ArticleTopicEntity.kt`
- Create: `backend/src/main/kotlin/com/sigak/article/domain/ArticleRelationEntity.kt`
- Create: `backend/src/main/kotlin/com/sigak/article/repository/ArticleRepository.kt`

- [ ] **Step 1: Add source entity**

Create `backend/src/main/kotlin/com/sigak/source/domain/NewsSourceEntity.kt`:

```kotlin
package com.sigak.source.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "news_sources")
class NewsSourceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "source_key", nullable = false, unique = true, length = 120)
    var sourceKey: String = "",

    @Column(nullable = false, length = 255)
    var name: String = "",

    @Column(nullable = false, length = 40)
    var type: String = "",

    @Column(nullable = false, columnDefinition = "text")
    var url: String = "",

    @Column(name = "category_hint", length = 80)
    var categoryHint: String? = null
)
```

- [ ] **Step 2: Add article enums**

Create `backend/src/main/kotlin/com/sigak/article/domain/EventType.kt`:

```kotlin
package com.sigak.article.domain

enum class EventType {
    NEWS,
    OFFICIAL_ANNOUNCEMENT,
    RESEARCH,
    SECURITY,
    RELEASE
}
```

Create `backend/src/main/kotlin/com/sigak/article/domain/PrimaryCategory.kt`:

```kotlin
package com.sigak.article.domain

enum class PrimaryCategory {
    AI,
    SECURITY,
    SOFTWARE_ENGINEERING,
    BACKEND,
    FRONTEND,
    DATA,
    INFRA_CLOUD,
    DEVTOOLS,
    CS_RESEARCH
}
```

Create `backend/src/main/kotlin/com/sigak/article/domain/ProcessingStatus.kt`:

```kotlin
package com.sigak.article.domain

enum class ProcessingStatus {
    DISCOVERED,
    FETCHED,
    EXTRACTED,
    NORMALIZED,
    ENRICHED,
    PUBLISHED,
    FAILED
}
```

Create `backend/src/main/kotlin/com/sigak/article/domain/RelationType.kt`:

```kotlin
package com.sigak.article.domain

enum class RelationType {
    RELATED
}
```

- [ ] **Step 3: Add article entity**

Create `backend/src/main/kotlin/com/sigak/article/domain/ArticleEntity.kt`:

```kotlin
package com.sigak.article.domain

import com.sigak.source.domain.NewsSourceEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "articles")
class ArticleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    var source: NewsSourceEntity = NewsSourceEntity(),

    @Column(name = "external_id", length = 255)
    var externalId: String? = null,

    @Column(nullable = false, length = 500)
    var title: String = "",

    @Column(nullable = false, columnDefinition = "text")
    var url: String = "",

    @Column(name = "canonical_url", nullable = false, columnDefinition = "text")
    var canonicalUrl: String = "",

    @Column(name = "published_at", nullable = false)
    var publishedAt: Instant = Instant.EPOCH,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    var eventType: EventType = EventType.NEWS,

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_category", nullable = false, length = 80)
    var primaryCategory: PrimaryCategory = PrimaryCategory.AI,

    @Column(name = "importance_score", nullable = false)
    var importanceScore: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 60)
    var processingStatus: ProcessingStatus = ProcessingStatus.DISCOVERED,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
) {
    @OneToOne(mappedBy = "article", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var rawContent: ArticleRawContentEntity? = null

    @OneToMany(mappedBy = "article", cascade = [CascadeType.ALL], orphanRemoval = true)
    var enrichments: MutableList<ArticleEnrichmentEntity> = mutableListOf()

    @OneToMany(mappedBy = "article", cascade = [CascadeType.ALL], orphanRemoval = true)
    var topics: MutableList<ArticleTopicEntity> = mutableListOf()

    @OneToMany(mappedBy = "sourceArticle", cascade = [CascadeType.ALL], orphanRemoval = true)
    var outgoingRelations: MutableList<ArticleRelationEntity> = mutableListOf()
}
```

- [ ] **Step 4: Add related article child entities**

Create `backend/src/main/kotlin/com/sigak/article/domain/ArticleRawContentEntity.kt`:

```kotlin
package com.sigak.article.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "article_raw_contents")
class ArticleRawContentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false, unique = true)
    var article: ArticleEntity = ArticleEntity(),

    @Column(name = "raw_content", nullable = false, columnDefinition = "text")
    var rawContent: String = "",

    @Column(name = "extracted_text", nullable = false, columnDefinition = "text")
    var extractedText: String = "",

    @Column(name = "collected_at", nullable = false)
    var collectedAt: Instant = Instant.EPOCH
)
```

Create `backend/src/main/kotlin/com/sigak/article/domain/ArticleEnrichmentEntity.kt`:

```kotlin
package com.sigak.article.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "article_enrichments")
class ArticleEnrichmentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    var article: ArticleEntity = ArticleEntity(),

    @Column(nullable = false, columnDefinition = "text")
    var summary: String = "",

    @Column(name = "why_it_matters", nullable = false, columnDefinition = "text")
    var whyItMatters: String = "",

    @Column(name = "suggested_primary_category", length = 80)
    var suggestedPrimaryCategory: String? = null,

    @Column(name = "suggested_importance_score")
    var suggestedImportanceScore: Int? = null,

    @Column(name = "model_name", nullable = false, length = 120)
    var modelName: String = "",

    @Column(name = "prompt_version", nullable = false, length = 80)
    var promptVersion: String = "",

    @Column(name = "is_current", nullable = false)
    var current: Boolean = false,

    @Column(name = "enriched_at", nullable = false)
    var enrichedAt: Instant = Instant.EPOCH
)
```

Create `backend/src/main/kotlin/com/sigak/article/domain/ArticleTopicEntity.kt`:

```kotlin
package com.sigak.article.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "article_topics")
class ArticleTopicEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    var article: ArticleEntity = ArticleEntity(),

    @Column(nullable = false, length = 160)
    var topic: String = "",

    @Column(nullable = false)
    var position: Int = 0
)
```

Create `backend/src/main/kotlin/com/sigak/article/domain/ArticleRelationEntity.kt`:

```kotlin
package com.sigak.article.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "article_relations")
class ArticleRelationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_article_id", nullable = false)
    var sourceArticle: ArticleEntity = ArticleEntity(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_article_id", nullable = false)
    var targetArticle: ArticleEntity = ArticleEntity(),

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 80)
    var relationType: RelationType = RelationType.RELATED,

    @Column(columnDefinition = "text")
    var reason: String? = null
)
```

- [ ] **Step 5: Add article repository**

Create `backend/src/main/kotlin/com/sigak/article/repository/ArticleRepository.kt`:

```kotlin
package com.sigak.article.repository

import com.sigak.article.domain.ArticleEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ArticleRepository : JpaRepository<ArticleEntity, Long> {
    fun findAllByOrderByImportanceScoreDescPublishedAtDescIdAsc(): List<ArticleEntity>
}
```

- [ ] **Step 6: Run persistence smoke test to validate entity mappings**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.persistence.PersistenceSmokeTest
```

Expected: PASS. Hibernate `ddl-auto=validate` should accept the Flyway schema.

- [ ] **Step 7: Commit entity and repository changes**

```bash
git add backend/src/main/kotlin/com/sigak/source/domain backend/src/main/kotlin/com/sigak/article/domain backend/src/main/kotlin/com/sigak/article/repository
git commit -m "feat: add persisted article entities"
```

---

### Task 4: Replace ArticleService Mock Data with Repository Mapping

**Files:**
- Modify: `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`
- Modify: `backend/src/test/kotlin/com/sigak/article/service/ArticleServiceTest.kt`

- [ ] **Step 1: Replace service test with PostgreSQL-backed expectations**

Replace `backend/src/test/kotlin/com/sigak/article/service/ArticleServiceTest.kt` with:

```kotlin
package com.sigak.article.service

import com.sigak.SigakBackendApplication
import com.sigak.support.PostgresIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [SigakBackendApplication::class])
class ArticleServiceTest : PostgresIntegrationTest() {

    @Autowired
    private lateinit var articleService: ArticleService

    @Test
    fun getArticlesReturnsAllArticlesWhenQueryIsBlank() {
        val articles = articleService.getArticles("   ")

        assertEquals(5, articles.size)
        assertEquals(listOf(3L, 1L, 4L, 2L, 5L), articles.map { it.id })
    }

    @Test
    fun getArticlesFiltersByTitleIgnoringCase() {
        val articles = articleService.getArticles("VECTOR")

        assertEquals(listOf(2L), articles.map { it.id })
    }

    @Test
    fun getArticlesFiltersBySummaryIgnoringCase() {
        val articles = articleService.getArticles("multi-step")

        assertEquals(listOf(1L), articles.map { it.id })
    }

    @Test
    fun getArticlesFiltersByPrimaryCategoryIgnoringCase() {
        val articles = articleService.getArticles("cs_research")

        assertEquals(listOf(4L), articles.map { it.id })
    }

    @Test
    fun getArticlesFiltersByTopicIgnoringCase() {
        val articles = articleService.getArticles("supply chain")

        assertEquals(listOf(3L), articles.map { it.id })
    }

    @Test
    fun getArticlesReturnsEmptyListWhenKeywordDoesNotMatch() {
        val articles = articleService.getArticles("nonexistent")

        assertEquals(emptyList(), articles)
    }

    @Test
    fun getArticleReturnsArticleById() {
        val article = articleService.getArticle(3L)

        assertNotNull(article)
        assertEquals("Critical Package Registry Attack Targets AI Toolchains", article.title)
        assertEquals("SECURITY", article.eventType)
        assertEquals(listOf("supply chain security", "package registry", "AI tooling"), article.topics)
        assertEquals(listOf(1L, 4L), article.relatedArticleIds)
    }

    @Test
    fun getArticleReturnsNullForUnknownId() {
        val article = articleService.getArticle(999L)

        assertEquals(null, article)
    }
}
```

- [ ] **Step 2: Run service test and verify it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.article.service.ArticleServiceTest
```

Expected: FAIL because `ArticleService` still constructs in-memory mock data and has no repository constructor dependency.

- [ ] **Step 3: Replace ArticleService implementation**

Replace `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt` with:

```kotlin
package com.sigak.article.service

import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ArticleEnrichmentEntity
import com.sigak.article.dto.ArticleResponse
import com.sigak.article.repository.ArticleRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleService(
    private val articleRepository: ArticleRepository
) {

    @Transactional(readOnly = true)
    fun getArticles(query: String? = null): List<ArticleResponse> {
        val articles = articleRepository.findAllByOrderByImportanceScoreDescPublishedAtDescIdAsc()
            .map { article -> article.toResponse() }

        val normalizedQuery = query?.trim()
        if (normalizedQuery.isNullOrBlank()) {
            return articles
        }

        return articles.filter { article -> article.matches(normalizedQuery) }
    }

    @Transactional(readOnly = true)
    fun getArticle(id: Long): ArticleResponse? =
        articleRepository.findByIdOrNull(id)?.toResponse()

    private fun ArticleEntity.toResponse(): ArticleResponse {
        val currentEnrichment = currentEnrichment()

        return ArticleResponse(
            id = requireNotNull(id),
            title = title,
            source = source.name,
            url = url,
            publishedAt = publishedAt.toString(),
            eventType = eventType.name,
            primaryCategory = primaryCategory.name,
            topics = topics
                .sortedBy { topic -> topic.position }
                .map { topic -> topic.topic },
            summary = currentEnrichment.summary,
            whyItMatters = currentEnrichment.whyItMatters,
            importanceScore = importanceScore,
            relatedArticleIds = outgoingRelations
                .sortedBy { relation -> relation.id ?: Long.MAX_VALUE }
                .map { relation -> requireNotNull(relation.targetArticle.id) }
        )
    }

    private fun ArticleEntity.currentEnrichment(): ArticleEnrichmentEntity =
        enrichments.firstOrNull { enrichment -> enrichment.current }
            ?: error("Article $id has no current enrichment")

    private fun ArticleResponse.matches(query: String): Boolean =
        title.contains(query, ignoreCase = true) ||
            summary.contains(query, ignoreCase = true) ||
            primaryCategory.contains(query, ignoreCase = true) ||
            topics.any { topic -> topic.contains(query, ignoreCase = true) }
}
```

- [ ] **Step 4: Run service test and verify it passes**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.article.service.ArticleServiceTest
```

Expected: PASS.

- [ ] **Step 5: Commit service conversion**

```bash
git add backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt backend/src/test/kotlin/com/sigak/article/service/ArticleServiceTest.kt
git commit -m "feat: read articles from postgres"
```

---

### Task 5: Update Controller Test Wiring and OpenAPI Description

**Files:**
- Modify: `backend/src/main/kotlin/com/sigak/article/controller/ArticleController.kt`
- Modify: `backend/src/test/kotlin/com/sigak/article/controller/ArticleControllerTest.kt`

- [ ] **Step 1: Update controller test to use PostgreSQL Testcontainers**

Modify the class declaration in `backend/src/test/kotlin/com/sigak/article/controller/ArticleControllerTest.kt`:

```kotlin
package com.sigak.article.controller

import com.sigak.SigakBackendApplication
import com.sigak.support.PostgresIntegrationTest
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(classes = [SigakBackendApplication::class])
@AutoConfigureMockMvc
class ArticleControllerTest : PostgresIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun getArticlesReturnsPersistedArticleList() {
        mockMvc.perform(get("/api/articles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(5)))
            .andExpect(jsonPath("$[0].id").value(3))
            .andExpect(jsonPath("$[0].title").value("Critical Package Registry Attack Targets AI Toolchains"))
            .andExpect(jsonPath("$[0].source").value("Security Advisory Board"))
            .andExpect(jsonPath("$[0].url").value("https://example.com/articles/ai-toolchain-package-attack"))
            .andExpect(jsonPath("$[0].publishedAt").value("2026-05-03T15:45:00Z"))
            .andExpect(jsonPath("$[0].eventType").value("SECURITY"))
            .andExpect(jsonPath("$[0].primaryCategory").value("SECURITY"))
            .andExpect(jsonPath("$[0].topics", hasSize<Any>(3)))
            .andExpect(jsonPath("$[0].topics[0]").value("supply chain security"))
            .andExpect(jsonPath("$[0].summary").value("A coordinated package registry attack targeted developer environments that install AI tooling."))
            .andExpect(jsonPath("$[0].whyItMatters").value("AI development stacks often combine fast-moving packages, credentials, and automation, which raises the blast radius of supply chain attacks."))
            .andExpect(jsonPath("$[0].importanceScore").value(93))
            .andExpect(jsonPath("$[0].relatedArticleIds", hasSize<Any>(2)))
    }

    @Test
    fun getArticlesFiltersByQueryIgnoringCaseAndOuterWhitespace() {
        mockMvc.perform(get("/api/articles").param("query", "  graph  "))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].id").value(4))
            .andExpect(jsonPath("$[0].title").value("New Research Maps Failure Modes in Graph RAG Systems"))
            .andExpect(jsonPath("$[0].primaryCategory").value("CS_RESEARCH"))
    }

    @Test
    fun getArticlesReturnsEmptyListWhenQueryDoesNotMatch() {
        mockMvc.perform(get("/api/articles").param("query", "nonexistent"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
    }

    @Test
    fun getArticleReturnsArticleDetail() {
        mockMvc.perform(get("/api/articles/3"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(3))
            .andExpect(jsonPath("$.eventType").value("SECURITY"))
            .andExpect(jsonPath("$.primaryCategory").value("SECURITY"))
            .andExpect(jsonPath("$.topics[0]").value("supply chain security"))
            .andExpect(jsonPath("$.importanceScore").value(93))
            .andExpect(jsonPath("$.relatedArticleIds[0]").value(1))
    }

    @Test
    fun getArticleReturnsNotFoundForUnknownId() {
        mockMvc.perform(get("/api/articles/999"))
            .andExpect(status().isNotFound)
    }
}
```

- [ ] **Step 2: Update controller OpenAPI description**

In `backend/src/main/kotlin/com/sigak/article/controller/ArticleController.kt`, replace the list endpoint description with:

```kotlin
description = "Returns persisted curated articles. When query is provided, filters articles by title, summary, primary category, and topics while keeping the public response shape stable for a later Elasticsearch-backed search implementation."
```

- [ ] **Step 3: Run controller tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.article.controller.ArticleControllerTest
```

Expected: PASS.

- [ ] **Step 4: Commit controller updates**

```bash
git add backend/src/main/kotlin/com/sigak/article/controller/ArticleController.kt backend/src/test/kotlin/com/sigak/article/controller/ArticleControllerTest.kt
git commit -m "test: verify persisted article api"
```

---

### Task 6: Update Documentation and Roadmap

**Files:**
- Create: `docs/decisions/0004-persistence-mvp.md`
- Modify: `README.md`
- Modify: `backend/README.md`
- Modify: `infra/README.md`
- Modify: `docs/API_SPEC.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Add persistence ADR**

Create `docs/decisions/0004-persistence-mvp.md`:

```markdown
# 0004: Persistence MVP

## Status
Accepted

## Date
2026-05-06

## Context
Sigak's article API started with curated in-memory mock data. Phase 6 needs real persistence so article metadata, raw content, enrichment output, topics, and related article links can be reviewed and reused for future search and Graph RAG work.

## Decision
Use PostgreSQL as the local relational database, Docker Compose for local database setup, Flyway for schema and seed migrations, and Spring Data JPA for backend persistence.

The public article API remains stable. The backend maps persisted article rows into the existing `ArticleResponse` DTO.

The Phase 6 relational model includes:
- `news_sources`
- `articles`
- `article_raw_contents`
- `article_enrichments`
- `article_topics`
- `article_relations`

Raw source text and enrichment output are stored separately so articles can be reprocessed with new prompts, models, category rules, embedding models, or graph extraction steps without fetching the source again.

## Consequences
- The backend demonstrates a real relational persistence layer.
- Flyway makes schema history explicit and reviewable.
- Testcontainers verifies migrations against PostgreSQL instead of an in-memory substitute.
- The model is ready for Phase 8 concepts and relationship-aware insight without implementing the full graph schema in Phase 6.
- Local development now requires PostgreSQL for the backend article API.

## Alternatives Considered
- Single `articles` table: faster, but weak for raw-content separation, enrichment history, and relationship visualization.
- Hibernate automatic DDL: convenient, but weaker for portfolio-grade schema review and migration history.
- H2 for tests: faster, but less faithful to PostgreSQL and Flyway behavior.
- Full graph schema in Phase 6: useful eventually, but too much before the persistence MVP is stable.
```

- [ ] **Step 2: Update root README current status and run instructions**

In `README.md`, update the current status paragraph so it says the article API is PostgreSQL-backed after Phase 6. Add this before backend startup:

````markdown
Start PostgreSQL:

```bash
cd infra
docker compose up -d postgres
```
````

Keep the existing backend and frontend commands.

- [ ] **Step 3: Update backend README**

In `backend/README.md`, replace mock-status language with:

````markdown
The backend exposes PostgreSQL-backed news article APIs:

```http
GET /api/articles
GET /api/articles?query={query}
GET /api/articles/{id}
```

Article responses include product-planning fields such as `eventType`, `primaryCategory`, `topics`, `summary`, `whyItMatters`, `importanceScore`, and `relatedArticleIds`.

Keyword search currently runs through the backend service over persisted article fields. Elasticsearch, vector search, and external AI integration are not implemented yet.
````

Add database requirement:

````markdown
Requirements:
- Java 17
- Docker
- PostgreSQL from `infra/docker-compose.yml`

Start the database from the repository root:

```bash
cd infra
docker compose up -d postgres
```
````

- [ ] **Step 4: Update infra README**

Replace `infra/README.md` with:

````markdown
# Infra

Infrastructure files for local Sigak development.

## Responsibilities
- Docker Compose setup
- Local service wiring
- PostgreSQL for the Phase 6 persistence MVP
- Future search and vector service configuration

## PostgreSQL

Start the local database:

```bash
docker compose up -d postgres
```

Stop the local database:

```bash
docker compose down
```

Remove the local PostgreSQL volume when a clean database is needed:

```bash
docker compose down -v
```

Default local values:

```txt
database: sigak
username: sigak
password: sigak
port: 5432
```
````

- [ ] **Step 5: Update API spec persistence note**

In `docs/API_SPEC.md`, update the search section with:

```markdown
Current search behavior is implemented by the Spring Boot service over persisted PostgreSQL article data. Elasticsearch-backed indexing remains a later enhancement.
```

Keep all endpoint and response JSON examples unchanged.

- [ ] **Step 6: Update roadmap Phase 6**

In `docs/ROADMAP.md`, change Phase 6 to:

```markdown
## Phase 6: Persistence MVP
- [x] Add a relational database when mock data no longer fits the workflow.
- [x] Add JPA entities, repositories, and service logic for persisted articles.
- [x] Preserve raw article text and source metadata for future reprocessing.
- [x] Keep enriched fields separate enough to regenerate later.
- [x] Continue using Docker Compose for local development.
```

- [ ] **Step 7: Commit documentation updates**

```bash
git add README.md backend/README.md infra/README.md docs/API_SPEC.md docs/ROADMAP.md docs/decisions/0004-persistence-mvp.md
git commit -m "docs: document persistence mvp"
```

---

### Task 7: Full Verification

**Files:**
- No new files.

- [ ] **Step 1: Run backend test suite**

Run:

```bash
cd backend
./gradlew test
```

Expected: PASS for all backend tests.

- [ ] **Step 2: Start local PostgreSQL**

Run:

```bash
cd infra
docker compose up -d postgres
docker compose ps
```

Expected: `postgres` service is running and healthy.

- [ ] **Step 3: Start backend locally**

Run:

```bash
cd backend
./gradlew bootRun
```

Expected: backend starts on port `8080`; Flyway reports successful migrations; Hibernate schema validation passes.

- [ ] **Step 4: Verify article list endpoint**

In another terminal, run:

```bash
curl -s http://localhost:8080/api/articles
```

Expected: JSON array with 5 articles. The first article should have `"id":3` because default ordering is `importanceScore desc`, `publishedAt desc`, `id asc`.

- [ ] **Step 5: Verify search endpoint**

Run:

```bash
curl -s "http://localhost:8080/api/articles?query=graph"
```

Expected: JSON array with article `4`, title `New Research Maps Failure Modes in Graph RAG Systems`.

- [ ] **Step 6: Verify detail endpoint**

Run:

```bash
curl -i http://localhost:8080/api/articles/3
```

Expected: `HTTP/1.1 200` and article `3` with `relatedArticleIds` containing `1` and `4`.

- [ ] **Step 7: Verify unknown article endpoint**

Run:

```bash
curl -i http://localhost:8080/api/articles/999
```

Expected: `HTTP/1.1 404`.

- [ ] **Step 8: Check git state**

Run:

```bash
git status --short
```

Expected: no uncommitted changes.

---

## Self-Review Checklist

- Spec coverage:
  - PostgreSQL and Docker Compose are covered in Task 1 and Task 7.
  - Flyway schema and seed data are covered in Task 2.
  - JPA entities and repositories are covered in Task 3.
  - Persisted service logic is covered in Task 4.
  - Public API shape is covered in Task 5 and Task 7.
  - Documentation and ADR updates are covered in Task 6.
  - Testcontainers-backed verification is covered in Task 2, Task 4, Task 5, and Task 7.
- Type consistency:
  - Database enum strings match Kotlin enum names.
  - `ArticleEntity.currentEnrichment()` uses `ArticleEnrichmentEntity.current`, mapped to `is_current`.
  - `ArticleResponse.publishedAt` remains an ISO-8601 string through `Instant.toString()`.
  - `ArticleRepository.findAllByOrderByImportanceScoreDescPublishedAtDescIdAsc()` matches the default ordering in tests.
- Scope control:
  - No Elasticsearch, Qdrant, embedding, concept-node, account, or frontend work is included.
