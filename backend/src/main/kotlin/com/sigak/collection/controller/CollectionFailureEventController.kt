package com.sigak.collection.controller

import com.sigak.collection.dto.CollectionFailureEventSearchRequest
import com.sigak.collection.dto.CollectionFailureEventSearchResponse
import com.sigak.collection.service.CollectionFailureEventQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/collections/failure-events")
@Tag(name = "Internal Collections", description = "Internal diagnostics for selected-source collection runs.")
class CollectionFailureEventController(
    private val service: CollectionFailureEventQueryService
) {

    @GetMapping
    @Operation(
        summary = "List collection failure events",
        description = "Lists persisted collection failure evidence for local MVP diagnostics."
    )
    fun listFailureEvents(
        @RequestParam(required = false)
        sourceId: String?,
        @RequestParam(required = false)
        runId: UUID?,
        @RequestParam(required = false)
        retryable: Boolean?,
        @RequestParam(defaultValue = "20")
        limit: Int
    ): CollectionFailureEventSearchResponse =
        service.search(
            CollectionFailureEventSearchRequest(
                sourceId = sourceId,
                runId = runId,
                retryable = retryable,
                limit = limit
            )
        )

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgumentException(exception: IllegalArgumentException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())
}
