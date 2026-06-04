package com.sigak.config

import com.sigak.article.controller.ArticleController
import com.sigak.article.service.ArticlePublicGraphContextService
import com.sigak.article.service.ArticleService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleController::class)
@Import(CorsConfig::class)
class CorsConfigTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var articleService: ArticleService

    @MockBean
    private lateinit var articlePublicGraphContextService: ArticlePublicGraphContextService

    @BeforeEach
    fun setUp() {
        given(articleService.getArticles(null)).willReturn(emptyList())
    }

    @Test
    fun allowsLocalViteFrontendOrigin() {
        mockMvc.perform(
            get("/api/articles")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
    }

    @Test
    fun allowsLocalViteFrontendIpOrigin() {
        mockMvc.perform(
            get("/api/articles")
                .header(HttpHeaders.ORIGIN, "http://127.0.0.1:5173")
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:5173"))
    }
}
