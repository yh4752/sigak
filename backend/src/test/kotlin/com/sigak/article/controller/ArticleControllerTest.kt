package com.sigak.article.controller

import com.sigak.SigakBackendApplication
import com.sigak.search.hybrid.ArticlePublicSearchMode
import com.sigak.search.hybrid.ArticlePublicSearchResult
import com.sigak.search.hybrid.ArticlePublicSearchService
import com.sigak.support.PostgresIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.mock.mockito.MockBean
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

    @MockBean
    private lateinit var articlePublicSearchService: ArticlePublicSearchService

    @BeforeEach
    fun resetArticlePublicSearchService() {
        Mockito.reset(articlePublicSearchService)
        Mockito.doReturn(postgresFallbackResult())
            .`when`(articlePublicSearchService)
            .search(anyString())
    }

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

    private fun postgresFallbackResult(): ArticlePublicSearchResult =
        ArticlePublicSearchResult(
            articleIds = emptyList(),
            mode = ArticlePublicSearchMode.POSTGRES_FALLBACK,
            keywordCandidateCount = 0,
            vectorCandidateCount = 0,
            fusedCandidateCount = 0,
            keywordFailed = true,
            vectorFailed = true,
            fallbackReason = "KEYWORD_SEARCH_FAILED; VECTOR_SEARCH_FAILED",
            keywordElapsedMs = 0,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0,
            fusionElapsedMs = 0,
            totalElapsedMs = 0
        )
}
