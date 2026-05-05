package com.sigak.article.controller

import com.sigak.SigakBackendApplication
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(classes = [SigakBackendApplication::class])
@AutoConfigureMockMvc
class ArticleControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun getArticlesReturnsMockArticleList() {
        mockMvc.perform(get("/api/articles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(5)))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].title").value("OpenAI Releases Agent Evaluation Toolkit"))
            .andExpect(jsonPath("$[0].source").value("OpenAI"))
            .andExpect(jsonPath("$[0].url").value("https://example.com/articles/openai-agent-evals"))
            .andExpect(jsonPath("$[0].publishedAt").value("2026-05-01T09:00:00Z"))
            .andExpect(jsonPath("$[0].eventType").value("OFFICIAL_ANNOUNCEMENT"))
            .andExpect(jsonPath("$[0].primaryCategory").value("AI"))
            .andExpect(jsonPath("$[0].topics", hasSize<Any>(3)))
            .andExpect(jsonPath("$[0].topics[0]").value("LLM agents"))
            .andExpect(jsonPath("$[0].summary").value("OpenAI introduced a toolkit for evaluating agent behavior in multi-step workflows."))
            .andExpect(jsonPath("$[0].whyItMatters").value("Agent evaluation is becoming a practical requirement as teams move from demos to production workflows."))
            .andExpect(jsonPath("$[0].importanceScore").value(88))
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
}
