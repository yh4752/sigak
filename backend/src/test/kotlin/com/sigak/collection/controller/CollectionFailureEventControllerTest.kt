package com.sigak.collection.controller

import com.sigak.collection.domain.CollectionFailureKind
import com.sigak.collection.dto.CollectionFailureEventItemResponse
import com.sigak.collection.dto.CollectionFailureEventSearchRequest
import com.sigak.collection.dto.CollectionFailureEventSearchResponse
import com.sigak.collection.dto.CollectionFailureStage
import com.sigak.collection.service.CollectionFailureEventQueryService
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(CollectionFailureEventController::class)
class CollectionFailureEventControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: CollectionFailureEventQueryService

    @Test
    fun listFailureEventsReturnsPersistedFailureEvidence() {
        val runId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        `when`(
            service.search(
                CollectionFailureEventSearchRequest(
                    sourceId = "github-blog",
                    runId = runId,
                    retryable = false,
                    limit = 10
                )
            )
        ).thenReturn(
            CollectionFailureEventSearchResponse(
                returnedCount = 1,
                events = listOf(
                    CollectionFailureEventItemResponse(
                        id = 9L,
                        runId = runId,
                        sourceId = "github-blog",
                        stage = CollectionFailureStage.PUBLISH_ARTICLE,
                        failureKind = CollectionFailureKind.INVALID_ARTICLE,
                        retryable = false,
                        message = "IllegalArgumentException: title must not be blank",
                        fingerprint = "github-blog:PUBLISH_ARTICLE:INVALID_ARTICLE:title",
                        articleExternalId = "gh-1",
                        articleUrl = "https://github.blog/example",
                        articleTitle = "Broken article",
                        occurredAt = Instant.parse("2026-05-31T12:00:00Z")
                    )
                )
            )
        )

        mockMvc.perform(
            get("/api/internal/collections/failure-events")
                .param("sourceId", "github-blog")
                .param("runId", runId.toString())
                .param("retryable", "false")
                .param("limit", "10")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.returnedCount").value(1))
            .andExpect(jsonPath("$.events[0].id").value(9))
            .andExpect(jsonPath("$.events[0].runId").value(runId.toString()))
            .andExpect(jsonPath("$.events[0].sourceId").value("github-blog"))
            .andExpect(jsonPath("$.events[0].stage").value("PUBLISH_ARTICLE"))
            .andExpect(jsonPath("$.events[0].failureKind").value("INVALID_ARTICLE"))
            .andExpect(jsonPath("$.events[0].retryable").value(false))
            .andExpect(jsonPath("$.events[0].message").value("IllegalArgumentException: title must not be blank"))
            .andExpect(jsonPath("$.events[0].fingerprint").value("github-blog:PUBLISH_ARTICLE:INVALID_ARTICLE:title"))
            .andExpect(jsonPath("$.events[0].articleExternalId").value("gh-1"))
            .andExpect(jsonPath("$.events[0].articleUrl").value("https://github.blog/example"))
            .andExpect(jsonPath("$.events[0].articleTitle").value("Broken article"))
            .andExpect(jsonPath("$.events[0].occurredAt").value("2026-05-31T12:00:00Z"))
    }

    @Test
    fun listFailureEventsReturnsBadRequestWhenLimitIsInvalid() {
        `when`(service.search(CollectionFailureEventSearchRequest(limit = 101)))
            .thenThrow(IllegalArgumentException("limit must be between 1 and 100"))

        mockMvc.perform(
            get("/api/internal/collections/failure-events")
                .param("limit", "101")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("limit must be between 1 and 100"))
    }
}
