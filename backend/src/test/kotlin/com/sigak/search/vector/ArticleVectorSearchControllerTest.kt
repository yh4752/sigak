package com.sigak.search.vector

import com.sigak.article.dto.ArticleResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleVectorSearchController::class)
class ArticleVectorSearchControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: ArticleVectorSearchService

    @Test
    fun searchArticlesReturnsVectorSearchResponse() {
        `when`(service.search(ArticleVectorSearchRequest(query = "AI security", limit = 3)))
            .thenReturn(
                ArticleVectorSearchResponse(
                    query = "AI security",
                    collectionName = "sigak-article-vectors-test",
                    embeddingProvider = "local",
                    embeddingModelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                    embeddingDimension = 384,
                    results = listOf(
                        ArticleVectorSearchResult(
                            article = article(id = 7, title = "AI Supply Chain Security Playbook"),
                            score = 0.91
                        )
                    ),
                    timings = ArticleVectorSearchTimings(
                        embeddingElapsedMs = 11,
                        qdrantElapsedMs = 22,
                        articleLoadElapsedMs = 33,
                        totalElapsedMs = 66
                    )
                )
            )

        mockMvc.perform(
            post("/api/internal/vector-search/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query":"AI security","limit":3}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.query").value("AI security"))
            .andExpect(jsonPath("$.collectionName").value("sigak-article-vectors-test"))
            .andExpect(jsonPath("$.embeddingProvider").value("local"))
            .andExpect(jsonPath("$.embeddingModelName").value("sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"))
            .andExpect(jsonPath("$.embeddingDimension").value(384))
            .andExpect(jsonPath("$.results[0].article.id").value(7))
            .andExpect(jsonPath("$.results[0].article.title").value("AI Supply Chain Security Playbook"))
            .andExpect(jsonPath("$.results[0].score").value(0.91))
            .andExpect(jsonPath("$.timings.embeddingElapsedMs").value(11))
            .andExpect(jsonPath("$.timings.qdrantElapsedMs").value(22))
            .andExpect(jsonPath("$.timings.articleLoadElapsedMs").value(33))
            .andExpect(jsonPath("$.timings.totalElapsedMs").value(66))
    }

    @Test
    fun searchArticlesReturnsBadRequestWhenServiceRejectsRequest() {
        `when`(service.search(ArticleVectorSearchRequest(query = "", limit = null)))
            .thenThrow(IllegalArgumentException("Vector search query must not be blank."))

        mockMvc.perform(
            post("/api/internal/vector-search/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query":""}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Vector search query must not be blank."))
    }

    private fun article(id: Long, title: String): ArticleResponse =
        ArticleResponse(
            id = id,
            title = title,
            source = "Sigak Research",
            url = "https://example.com/articles/$id",
            publishedAt = "2026-05-29T09:00:00Z",
            eventType = "SECURITY",
            primaryCategory = "AI",
            topics = listOf("AI", "security"),
            summary = "A report explains AI system security controls.",
            whyItMatters = "AI systems combine model, data, and software supply chains.",
            importanceScore = 91,
            relatedArticleIds = listOf(1, 2)
        )
}
