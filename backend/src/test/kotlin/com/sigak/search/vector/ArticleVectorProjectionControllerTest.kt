package com.sigak.search.vector

import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleVectorProjectionController::class)
class ArticleVectorProjectionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var rebuildService: ArticleVectorProjectionRebuildService

    @Test
    fun rebuildArticleVectorProjectionReturnsIndexingMetrics() {
        `when`(rebuildService.rebuild())
            .thenReturn(
                ArticleVectorProjectionRebuildResponse(
                    status = "completed",
                    collectionName = "sigak-article-vectors-v1",
                    indexedCount = 5,
                    embeddingProvider = "local",
                    embeddingModelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                    embeddingDimension = 384,
                    durationMs = 42,
                    failedReason = null
                )
            )

        mockMvc.perform(post("/api/internal/search-projections/article-vectors/rebuild"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.collectionName").value("sigak-article-vectors-v1"))
            .andExpect(jsonPath("$.indexedCount").value(5))
            .andExpect(jsonPath("$.embeddingProvider").value("local"))
            .andExpect(jsonPath("$.embeddingDimension").value(384))
            .andExpect(jsonPath("$.durationMs").value(42))
            .andExpect(jsonPath("$.failedReason").doesNotExist())
    }
}
