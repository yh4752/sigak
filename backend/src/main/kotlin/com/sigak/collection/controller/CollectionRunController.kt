package com.sigak.collection.controller

import com.sigak.collection.dto.CollectionRunRequest
import com.sigak.collection.dto.CollectionRunResponse
import com.sigak.collection.service.CollectionRunService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/collections/runs")
@Tag(name = "Internal Collections", description = "Controlled local triggers for selected-source collection runs.")
class CollectionRunController(
    private val service: CollectionRunService
) {

    @PostMapping
    @Operation(
        summary = "Run selected-source collection",
        description = "Runs registered collection sources sequentially and returns source/article counts for local MVP operations."
    )
    fun runCollection(
        @RequestBody(required = false)
        request: CollectionRunRequest?
    ): CollectionRunResponse =
        service.run(request ?: CollectionRunRequest())

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgumentException(exception: IllegalArgumentException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())
}
