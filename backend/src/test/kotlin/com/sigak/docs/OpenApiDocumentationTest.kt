package com.sigak.docs

import com.sigak.SigakBackendApplication
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(classes = [SigakBackendApplication::class])
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun openApiSpecDocumentsArticleEndpoints() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.info.title").value("Sigak API"))
            .andExpect(jsonPath("$.info.version").value("0.1.0"))
            .andExpect(jsonPath("$.paths['/api/articles'].get.summary").value("List or search articles"))
            .andExpect(jsonPath("$.paths['/api/articles/{id}'].get.summary").value("Get article detail"))
            .andExpect(jsonPath("$.components.schemas.ArticleResponse.properties.whyItMatters.description").value("Explanation of why this article matters for technical readers."))
    }

    @Test
    fun swaggerUiIsAvailable() {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Swagger UI")))
    }
}
