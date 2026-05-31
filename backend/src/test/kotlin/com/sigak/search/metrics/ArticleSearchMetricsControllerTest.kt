package com.sigak.search.metrics

import com.sigak.search.hybrid.ArticlePublicSearchMode
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleSearchMetricsController::class)
class ArticleSearchMetricsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var metricsRecorder: ArticleSearchMetricsRecorder

    @Test
    fun getArticleSearchMetricsReturnsCurrentSummary() {
        `when`(metricsRecorder.summarize())
            .thenReturn(
                ArticleSearchMetricsResponse(
                    totalSearchCount = 2,
                    hybridSearchCount = 1,
                    keywordOnlySearchCount = 0,
                    vectorOnlySearchCount = 0,
                    postgresFallbackSearchCount = 1,
                    fallbackRate = 0.5,
                    averageTotalElapsedMs = 30.0,
                    p50TotalElapsedMs = 20,
                    p95TotalElapsedMs = 40,
                    lastSearch = ArticleSearchMetricSnapshotResponse(
                        queryLength = 6,
                        resultCount = 2,
                        mode = ArticlePublicSearchMode.POSTGRES_FALLBACK,
                        keywordCandidateCount = 2,
                        vectorCandidateCount = 2,
                        fusedCandidateCount = 0,
                        staleCandidateCount = 0,
                        keywordFailed = true,
                        vectorFailed = true,
                        fallbackReason = "KEYWORD_SEARCH_FAILED; QDRANT_SEARCH_FAILED",
                        keywordElapsedMs = 3,
                        embeddingElapsedMs = 4,
                        vectorElapsedMs = 5,
                        fusionElapsedMs = 1,
                        articleReloadElapsedMs = 2,
                        totalElapsedMs = 40
                    )
                )
            )

        mockMvc.perform(get("/api/internal/search-metrics/articles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalSearchCount").value(2))
            .andExpect(jsonPath("$.hybridSearchCount").value(1))
            .andExpect(jsonPath("$.keywordOnlySearchCount").value(0))
            .andExpect(jsonPath("$.vectorOnlySearchCount").value(0))
            .andExpect(jsonPath("$.postgresFallbackSearchCount").value(1))
            .andExpect(jsonPath("$.fallbackRate").value(0.5))
            .andExpect(jsonPath("$.averageTotalElapsedMs").value(30.0))
            .andExpect(jsonPath("$.p50TotalElapsedMs").value(20))
            .andExpect(jsonPath("$.p95TotalElapsedMs").value(40))
            .andExpect(jsonPath("$.lastSearch.mode").value("POSTGRES_FALLBACK"))
            .andExpect(jsonPath("$.lastSearch.keywordFailed").value(true))
            .andExpect(jsonPath("$.lastSearch.vectorFailed").value(true))
    }
}
