package com.sigak.collection.automation

import com.sigak.collection.controller.CollectionAutomationController
import com.sigak.collection.dto.CollectionAutomationStatus
import com.sigak.collection.service.CollectionAutomationService
import java.time.Instant
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(CollectionAutomationController::class)
class CollectionAutomationControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: CollectionAutomationService

    @Test
    fun exposesReadOnlyAutomationSnapshotInternally() {
        `when`(service.status()).thenReturn(CollectionAutomationStatus(
            enabled = true, state = "COMPLETED", fixedDelaySeconds = 600,
            attemptedRunCount = 2, lastSuccessfulAt = Instant.parse("2026-10-30T01:00:00Z"),
            projectionRefreshPending = false
        ))
        mockMvc.perform(get("/api/internal/collections/automation"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.state").value("COMPLETED"))
            .andExpect(jsonPath("$.attemptedRunCount").value(2))
            .andExpect(jsonPath("$.lastSuccessfulAt").value("2026-10-30T01:00:00Z"))
            .andExpect(jsonPath("$.projectionRefreshPending").value(false))
    }
}
