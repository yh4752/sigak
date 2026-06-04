package com.sigak.search.graph

import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleGraphContextController::class)
class ArticleGraphContextControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: ArticleGraphContextService

    @Test
    fun getContextReturnsGraphContext() {
        `when`(service.getContext(4L)).thenReturn(
            ArticleGraphContextResponse(
                articleId = 4L,
                topics = listOf(
                    ArticleGraphTopicContextResponse(
                        name = "graph rag",
                        displayName = "Graph RAG",
                        relatedArticleIds = listOf(1L)
                    )
                ),
                relatedArticles = listOf(
                    ArticleGraphRelatedArticleResponse(
                        articleId = 1L,
                        title = "OpenAI Releases Agent Evaluation Toolkit",
                        relationType = "RELATED",
                        reason = "Graph RAG evaluation connects to agent and retrieval evaluation.",
                        sharedTopics = listOf("evaluation")
                    )
                ),
                timings = ArticleGraphContextTimingsResponse(
                    neo4jElapsedMs = 8,
                    totalElapsedMs = 8
                )
            )
        )

        mockMvc.perform(get("/api/internal/graph/articles/4/context"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.articleId").value(4))
            .andExpect(jsonPath("$.topics[0].name").value("graph rag"))
            .andExpect(jsonPath("$.topics[0].displayName").value("Graph RAG"))
            .andExpect(jsonPath("$.topics[0].relatedArticleIds[0]").value(1))
            .andExpect(jsonPath("$.relatedArticles[0].articleId").value(1))
            .andExpect(jsonPath("$.relatedArticles[0].title").value("OpenAI Releases Agent Evaluation Toolkit"))
            .andExpect(jsonPath("$.relatedArticles[0].relationType").value("RELATED"))
            .andExpect(jsonPath("$.relatedArticles[0].reason").value("Graph RAG evaluation connects to agent and retrieval evaluation."))
            .andExpect(jsonPath("$.relatedArticles[0].sharedTopics[0]").value("evaluation"))
            .andExpect(jsonPath("$.timings.neo4jElapsedMs").value(8))
            .andExpect(jsonPath("$.timings.totalElapsedMs").value(8))
    }

    @Test
    fun getContextReturnsBadRequestForInvalidId() {
        `when`(service.getContext(0L)).thenThrow(IllegalArgumentException("articleId must be positive."))

        mockMvc.perform(get("/api/internal/graph/articles/0/context"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("articleId must be positive."))
    }

    @Test
    fun getContextReturnsNotFoundWhenProjectionArticleIsMissing() {
        `when`(service.getContext(99L)).thenThrow(ArticleGraphContextNotFoundException(99L))

        mockMvc.perform(get("/api/internal/graph/articles/99/context"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Article graph context not found: articleId=99"))
    }
}
