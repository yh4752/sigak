package com.sigak.search.vector

import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleVectorSearchMetricsController::class)
class ArticleVectorSearchMetricsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var metricsRecorder: ArticleVectorSearchMetricsRecorder

    @Test
    fun returnsVectorSearchMetrics() {
        `when`(metricsRecorder.summarize())
            .thenReturn(
                ArticleVectorSearchMetricsResponse(
                    totalSearchCount = 2,
                    averageTotalElapsedMs = 15.0,
                    p50TotalElapsedMs = 10,
                    p95TotalElapsedMs = 20,
                    averageEmbeddingElapsedMs = 6.0,
                    averageQdrantElapsedMs = 5.0,
                    averageArticleLoadElapsedMs = 4.0,
                    lastSearch = ArticleVectorSearchMetricSnapshotResponse(
                        queryLength = 12,
                        resultCount = 3,
                        embeddingElapsedMs = 8,
                        qdrantElapsedMs = 7,
                        articleLoadElapsedMs = 5,
                        totalElapsedMs = 20
                    )
                )
            )

        mockMvc.perform(get("/api/internal/search-metrics/article-vectors"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalSearchCount").value(2))
            .andExpect(jsonPath("$.averageTotalElapsedMs").value(15.0))
            .andExpect(jsonPath("$.p95TotalElapsedMs").value(20))
            .andExpect(jsonPath("$.averageEmbeddingElapsedMs").value(6.0))
            .andExpect(jsonPath("$.lastSearch.totalElapsedMs").value(20))
    }
}
