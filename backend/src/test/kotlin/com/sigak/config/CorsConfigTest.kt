package com.sigak.config

import com.sigak.SigakBackendApplication
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(classes = [SigakBackendApplication::class])
@AutoConfigureMockMvc
class CorsConfigTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

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
