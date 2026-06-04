package com.sigak.search.graph

import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleGraphProjectionController::class)
class ArticleGraphProjectionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var rebuildService: ArticleGraphProjectionRebuildService

    @Test
    fun rebuildArticleGraphProjectionReturnsGraphMetrics() {
        `when`(rebuildService.rebuild())
            .thenReturn(
                ArticleGraphProjectionRebuildResponse(
                    status = "completed",
                    rebuiltAt = "2026-06-03T09:00:00Z",
                    articleNodeCount = 6,
                    topicNodeCount = 18,
                    hasTopicRelationshipCount = 18,
                    relatedToRelationshipCount = 10,
                    durationMs = 120,
                    failedReason = null
                )
            )

        mockMvc.perform(post("/api/internal/graph-projections/articles/rebuild"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.rebuiltAt").value("2026-06-03T09:00:00Z"))
            .andExpect(jsonPath("$.articleNodeCount").value(6))
            .andExpect(jsonPath("$.topicNodeCount").value(18))
            .andExpect(jsonPath("$.hasTopicRelationshipCount").value(18))
            .andExpect(jsonPath("$.relatedToRelationshipCount").value(10))
            .andExpect(jsonPath("$.durationMs").value(120))
            .andExpect(jsonPath("$.failedReason").doesNotExist())
    }
}
