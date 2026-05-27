package com.sigak.search.controller

import com.sigak.search.dto.SearchInfrastructureHealthResponse
import com.sigak.search.dto.SearchStoreHealthResponse
import com.sigak.search.service.SearchInfrastructureHealthService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(SearchInfrastructureHealthController::class)
class SearchInfrastructureHealthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var searchInfrastructureHealthService: SearchInfrastructureHealthService

    @Test
    fun getSearchInfrastructureHealthReturnsReadinessStatus() {
        `when`(searchInfrastructureHealthService.checkHealth())
            .thenReturn(
                SearchInfrastructureHealthResponse(
                    overallStatus = "ready",
                    elasticsearch = SearchStoreHealthResponse(status = "ready", detail = "elasticsearch health check succeeded"),
                    qdrant = SearchStoreHealthResponse(status = "ready", detail = "qdrant health check succeeded"),
                    neo4j = SearchStoreHealthResponse(status = "ready", detail = "neo4j connectivity check succeeded")
                )
            )

        mockMvc.perform(get("/api/internal/search-infrastructure/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.overallStatus").value("ready"))
            .andExpect(jsonPath("$.elasticsearch.status").value("ready"))
            .andExpect(jsonPath("$.qdrant.status").value("ready"))
            .andExpect(jsonPath("$.neo4j.status").value("ready"))
    }
}
