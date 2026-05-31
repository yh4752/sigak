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
            .andExpect(jsonPath("$.info.description").value("Public Spring Boot REST API for Sigak. Collection and AI enrichment are internal foundation work until persistence and indexing are added."))
            .andExpect(jsonPath("$.info.version").value("0.1.0"))
            .andExpect(jsonPath("$.paths['/api/articles'].get.summary").value("List or search articles"))
            .andExpect(jsonPath("$.paths['/api/articles'].get.description").value("Returns persisted curated articles. When query is provided, Elasticsearch keyword candidates and Qdrant vector candidates are fused with reciprocal rank fusion by default, then PostgreSQL reloads the final public responses. If both projection paths fail, PostgreSQL field filtering is used as the fallback while keeping the public response shape stable."))
            .andExpect(jsonPath("$.paths['/api/articles/{id}'].get.summary").value("Get article detail"))
            .andExpect(jsonPath("$.paths['/api/internal/search-metrics/articles'].get.summary").value("Get article search metrics"))
            .andExpect(jsonPath("$.components.schemas.ArticleResponse.properties.whyItMatters.description").value("Explanation of why this article matters for technical readers."))
            .andExpect(jsonPath("$.paths['/api/enrichment/article']").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/collection']").doesNotExist())
    }

    @Test
    fun swaggerUiIsAvailable() {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Swagger UI")))
    }
}
