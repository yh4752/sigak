package com.sigak.collection.controller

import com.sigak.collection.dto.CollectionRunRequest
import com.sigak.collection.dto.CollectionRunResponse
import com.sigak.collection.dto.CollectionRunStatus
import com.sigak.collection.dto.CollectionSourceRunResult
import com.sigak.collection.service.CollectionRunService
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

@WebMvcTest(CollectionRunController::class)
class CollectionRunControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: CollectionRunService

    @Test
    fun runCollectionReturnsAggregatedRunResponse() {
        `when`(service.run(CollectionRunRequest(sourceIds = listOf("openai-blog"), maxArticlesPerSource = 3)))
            .thenReturn(
                CollectionRunResponse(
                    status = CollectionRunStatus.COMPLETED,
                    requestedSourceIds = listOf("openai-blog"),
                    selectedSourceCount = 1,
                    fetchedSourceCount = 1,
                    failedSourceCount = 0,
                    discoveredArticleCount = 3,
                    publishedArticleCount = 2,
                    skippedArticleCount = 1,
                    failedArticleCount = 0,
                    publishedArticleIds = listOf(101L, 102L),
                    skippedArticleIds = listOf(1L),
                    durationMs = 42,
                    sourceResults = listOf(
                        CollectionSourceRunResult(
                            sourceId = "openai-blog",
                            status = CollectionRunStatus.COMPLETED,
                            fetched = true,
                            discoveredArticleCount = 3,
                            publishedArticleCount = 2,
                            skippedArticleCount = 1,
                            failedArticleCount = 0,
                            publishedArticleIds = listOf(101L, 102L),
                            skippedArticleIds = listOf(1L),
                            failureSummaries = emptyList(),
                            durationMs = 40
                        )
                    )
                )
            )

        mockMvc.perform(
            post("/api/internal/collections/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sourceIds":["openai-blog"],"maxArticlesPerSource":3}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.requestedSourceIds[0]").value("openai-blog"))
            .andExpect(jsonPath("$.selectedSourceCount").value(1))
            .andExpect(jsonPath("$.fetchedSourceCount").value(1))
            .andExpect(jsonPath("$.publishedArticleCount").value(2))
            .andExpect(jsonPath("$.skippedArticleCount").value(1))
            .andExpect(jsonPath("$.publishedArticleIds[0]").value(101))
            .andExpect(jsonPath("$.skippedArticleIds[0]").value(1))
            .andExpect(jsonPath("$.durationMs").value(42))
            .andExpect(jsonPath("$.sourceResults[0].sourceId").value("openai-blog"))
            .andExpect(jsonPath("$.sourceResults[0].fetched").value(true))
    }

    @Test
    fun runCollectionAcceptsMissingRequestBodyAsDefaultRequest() {
        `when`(service.run(CollectionRunRequest()))
            .thenReturn(
                CollectionRunResponse(
                    status = CollectionRunStatus.COMPLETED,
                    requestedSourceIds = listOf("openai-blog"),
                    selectedSourceCount = 1,
                    fetchedSourceCount = 1,
                    failedSourceCount = 0,
                    discoveredArticleCount = 0,
                    publishedArticleCount = 0,
                    skippedArticleCount = 0,
                    failedArticleCount = 0,
                    publishedArticleIds = emptyList(),
                    skippedArticleIds = emptyList(),
                    durationMs = 1,
                    sourceResults = emptyList()
                )
            )

        mockMvc.perform(post("/api/internal/collections/runs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.selectedSourceCount").value(1))
    }

    @Test
    fun runCollectionReturnsBadRequestWhenServiceRejectsRequest() {
        `when`(service.run(CollectionRunRequest(sourceIds = listOf("missing-source"), maxArticlesPerSource = null)))
            .thenThrow(IllegalArgumentException("Unknown collection source IDs: missing-source"))

        mockMvc.perform(
            post("/api/internal/collections/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sourceIds":["missing-source"]}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Unknown collection source IDs: missing-source"))
    }
}
