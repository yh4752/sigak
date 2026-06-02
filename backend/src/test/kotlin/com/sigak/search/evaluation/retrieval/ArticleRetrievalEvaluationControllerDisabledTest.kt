package com.sigak.search.evaluation.retrieval

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleRetrievalEvaluationController::class)
@TestPropertySource(properties = ["sigak.internal.search-evaluation.enabled=false"])
class ArticleRetrievalEvaluationControllerDisabledTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: ArticleRetrievalEvaluationService

    @Test
    fun disabledEvaluationEndpointIsNotRegistered() {
        mockMvc.perform(
            post("/api/internal/search-evaluation/retrieval-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"queries":["graph"],"systems":["KEYWORD"],"limit":20}""")
        )
            .andExpect(status().isNotFound)
    }
}
