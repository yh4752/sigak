package com.sigak.search.projection

import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleSearchProjectionController::class)
class ArticleSearchProjectionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var rebuildService: ArticleSearchProjectionRebuildService

    @Test
    fun rebuildArticleSearchProjectionReturnsIndexingMetrics() {
        `when`(rebuildService.rebuild())
            .thenReturn(
                ArticleSearchProjectionRebuildResponse(
                    status = "completed",
                    indexName = "sigak-articles-v1",
                    indexedCount = 5,
                    durationMs = 42,
                    failedReason = null
                )
            )

        mockMvc.perform(post("/api/internal/search-projections/articles/rebuild"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.indexName").value("sigak-articles-v1"))
            .andExpect(jsonPath("$.indexedCount").value(5))
            .andExpect(jsonPath("$.durationMs").value(42))
            .andExpect(jsonPath("$.failedReason").doesNotExist())
    }
}
