package com.sigak.search.evaluation.retrieval

import java.time.Instant
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleRetrievalEvaluationController::class)
@TestPropertySource(properties = ["sigak.internal.search-evaluation.enabled=true"])
class ArticleRetrievalEvaluationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: ArticleRetrievalEvaluationService

    @Test
    fun createRunsReturnsStrictEvaluationRuns() {
        val request = ArticleRetrievalRunRequest(
            queries = listOf("graph rag failure"),
            systems = listOf(
                ArticleRetrievalEvaluationSystem.KEYWORD,
                ArticleRetrievalEvaluationSystem.VECTOR,
                ArticleRetrievalEvaluationSystem.HYBRID
            ),
            limit = 20
        )
        `when`(service.createRuns(request))
            .thenReturn(
                ArticleRetrievalRunResponse(
                    generatedAt = Instant.parse("2026-06-02T00:00:00Z"),
                    limit = 20,
                    runs = listOf(completedRun(system = ArticleRetrievalEvaluationSystem.KEYWORD))
                )
            )

        mockMvc.perform(
            post("/api/internal/search-evaluation/retrieval-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"queries":["graph rag failure"],"systems":["KEYWORD","VECTOR","HYBRID"],"limit":20}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runs[0].system").value("KEYWORD"))
            .andExpect(jsonPath("$.runs[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.runs[0].rankedArticleIds[0]").value(4))
            .andExpect(jsonPath("$.runs[0].degraded").value(false))
    }

    @Test
    fun createRunsRejectsBlankQueries() {
        val request = ArticleRetrievalRunRequest(
            queries = listOf("   "),
            systems = listOf(ArticleRetrievalEvaluationSystem.KEYWORD),
            limit = 20
        )
        `when`(service.createRuns(request))
            .thenThrow(IllegalArgumentException("queries must not contain blank values"))

        mockMvc.perform(
            post("/api/internal/search-evaluation/retrieval-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"queries":["   "],"systems":["KEYWORD"],"limit":20}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("queries must not contain blank values"))
    }

    @Test
    fun createRunsRejectsPublicSystem() {
        mockMvc.perform(
            post("/api/internal/search-evaluation/retrieval-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"queries":["graph"],"systems":["PUBLIC"],"limit":20}""")
        )
            .andExpect(status().is4xxClientError)
    }

    @Test
    fun createRunsReturnsNotFoundWhenEvaluationIsDisabled() {
        val request = ArticleRetrievalRunRequest(
            queries = listOf("graph"),
            systems = listOf(ArticleRetrievalEvaluationSystem.KEYWORD),
            limit = 20
        )
        `when`(service.createRuns(request))
            .thenThrow(IllegalStateException("Internal search evaluation endpoint is disabled."))

        mockMvc.perform(
            post("/api/internal/search-evaluation/retrieval-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"queries":["graph"],"systems":["KEYWORD"],"limit":20}""")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Internal search evaluation endpoint is disabled."))
    }

    @Test
    fun createRunsDoesNotExposeSensitiveMetadata() {
        val request = ArticleRetrievalRunRequest(
            queries = listOf("graph"),
            systems = listOf(ArticleRetrievalEvaluationSystem.VECTOR),
            limit = 20
        )
        `when`(service.createRuns(request))
            .thenReturn(
                ArticleRetrievalRunResponse(
                    generatedAt = Instant.parse("2026-06-02T00:00:00Z"),
                    limit = 20,
                    runs = listOf(completedRun(system = ArticleRetrievalEvaluationSystem.VECTOR))
                )
            )

        mockMvc.perform(
            post("/api/internal/search-evaluation/retrieval-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"queries":["graph"],"systems":["VECTOR"],"limit":20}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runs[0].metadata.embeddingProvider").value("local"))
            .andExpect(
                jsonPath("$.runs[0].metadata.embeddingModelName")
                    .value("sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2")
            )
            .andExpect(jsonPath("$.runs[0].metadata.embeddingDimension").value(384))
            .andExpect(jsonPath("$.runs[0].metadata.apiKey").doesNotExist())
            .andExpect(jsonPath("$.runs[0].metadata.headers").doesNotExist())
            .andExpect(jsonPath("$.runs[0].metadata.environment").doesNotExist())
            .andExpect(jsonPath("$.runs[0].metadata.embedding").doesNotExist())
            .andExpect(jsonPath("$.runs[0].metadata.serviceUrl").doesNotExist())
    }

    private fun completedRun(system: ArticleRetrievalEvaluationSystem): ArticleRetrievalRunItemResponse =
        ArticleRetrievalRunItemResponse(
            query = "graph",
            system = system,
            status = ArticleRetrievalRunStatus.COMPLETED,
            rankedArticleIds = listOf(4L, 1L),
            candidateCount = 2,
            staleCandidateCount = 0,
            failureReason = null,
            degraded = false,
            resolvedMode = system.name,
            timings = ArticleRetrievalRunTimingsResponse(
                keywordElapsedMs = 2,
                embeddingElapsedMs = 3,
                vectorElapsedMs = 2,
                fusionElapsedMs = 1,
                articleReloadElapsedMs = 4,
                totalElapsedMs = 12
            ),
            metadata = ArticleRetrievalRunMetadataResponse(
                embeddingProvider = "local",
                embeddingModelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                embeddingDimension = 384
            )
        )
}
