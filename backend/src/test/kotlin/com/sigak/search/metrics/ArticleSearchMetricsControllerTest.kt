package com.sigak.search.metrics

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
                    elasticsearchSearchCount = 1,
                    fallbackSearchCount = 1,
                    fallbackRate = 0.5,
                    averageElapsedMs = 20.0,
                    p50ElapsedMs = 10,
                    p95ElapsedMs = 30,
                    lastSearch = ArticleSearchMetricSnapshotResponse(
                        queryLength = 6,
                        resultCount = 2,
                        fallback = true,
                        elapsedMs = 30
                    )
                )
            )

        mockMvc.perform(get("/api/internal/search-metrics/articles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalSearchCount").value(2))
            .andExpect(jsonPath("$.elasticsearchSearchCount").value(1))
            .andExpect(jsonPath("$.fallbackSearchCount").value(1))
            .andExpect(jsonPath("$.fallbackRate").value(0.5))
            .andExpect(jsonPath("$.averageElapsedMs").value(20.0))
            .andExpect(jsonPath("$.p50ElapsedMs").value(10))
            .andExpect(jsonPath("$.p95ElapsedMs").value(30))
            .andExpect(jsonPath("$.lastSearch.fallback").value(true))
    }
}
