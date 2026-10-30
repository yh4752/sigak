package com.sigak.collection.controller

import com.sigak.collection.dto.CollectionAutomationStatus
import com.sigak.collection.service.CollectionAutomationService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/collections/automation")
class CollectionAutomationController(private val service: CollectionAutomationService) {
    @GetMapping
    @Operation(summary = "Inspect the current automation process snapshot")
    fun status(): CollectionAutomationStatus = service.status()
}
