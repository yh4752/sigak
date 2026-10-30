package com.sigak.article.summary

import com.sigak.config.CorsConfig
import org.springframework.context.annotation.Import
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(controllers=[ArticleSummaryController::class], properties=["sigak.public-origin=https://sigak.example"])
@Import(CorsConfig::class)
class ArticleSummaryControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockBean lateinit var service: ArticleSummaryService

    @Test fun `read status is read only`() {
        Mockito.`when`(service.get(6)).thenReturn(ArticleSummaryResponse(6,"NOT_REQUESTED"))
        mvc.perform(get("/api/articles/6/summary")).andExpect(status().isOk).andExpect(jsonPath("$.status").value("NOT_REQUESTED"))
        Mockito.verify(service).get(6)
        Mockito.verifyNoMoreInteractions(service)
    }
    @Test fun `same origin post requests shared summary`() {
        Mockito.`when`(service.request(6)).thenReturn(ArticleSummaryResponse(6,"PENDING"))
        mvc.perform(post("/api/articles/6/summary").header("Origin","http://localhost").header("Host","localhost"))
            .andExpect(status().isOk).andExpect(jsonPath("$.status").value("PENDING"))
    }
    @Test fun `configured HTTPS public origin works behind HTTP reverse proxy`() {
        Mockito.`when`(service.request(6)).thenReturn(ArticleSummaryResponse(6,"READY"))
        mvc.perform(post("/api/articles/6/summary").header("Origin","https://sigak.example").header("Host","sigak.example"))
            .andExpect(status().isOk)
    }
    @Test fun `cross origin post cannot spend budget`() {
        mvc.perform(post("/api/articles/6/summary").header("Origin","https://other.example").header("Host","localhost"))
            .andExpect(status().isForbidden)
        Mockito.verifyNoInteractions(service)
    }
}
