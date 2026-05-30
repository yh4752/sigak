package com.sigak.search.service

import com.sigak.search.dto.SearchInfrastructureHealthResponse
import com.sigak.search.dto.SearchStoreHealthResponse
import org.neo4j.driver.Driver
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class SearchInfrastructureHealthService(
    @Qualifier("elasticsearchRestClient")
    private val elasticsearchClient: RestClient,
    @Qualifier("qdrantRestClient")
    private val qdrantClient: RestClient,
    private val neo4jDriver: Driver
) {

    fun checkHealth(): SearchInfrastructureHealthResponse {
        val elasticsearch = checkHttpStore(
            name = "elasticsearch",
            request = {
                elasticsearchClient.get()
                    .uri("/_cluster/health")
                    .retrieve()
                    .toBodilessEntity()
            }
        )
        val qdrant = checkHttpStore(
            name = "qdrant",
            request = {
                qdrantClient.get()
                    .uri("/healthz")
                    .retrieve()
                    .toBodilessEntity()
            }
        )
        val neo4j = checkNeo4j()

        val overallStatus = if (
            listOf(elasticsearch, qdrant, neo4j).all { health -> health.status == READY }
        ) {
            READY
        } else {
            UNAVAILABLE
        }

        return SearchInfrastructureHealthResponse(
            overallStatus = overallStatus,
            elasticsearch = elasticsearch,
            qdrant = qdrant,
            neo4j = neo4j
        )
    }

    private fun checkHttpStore(name: String, request: () -> Unit): SearchStoreHealthResponse =
        try {
            request()
            SearchStoreHealthResponse(status = READY, detail = "$name health check succeeded")
        } catch (exception: Exception) {
            // MVP 단계에서는 readiness endpoint가 부분 실패 원인을 보여주는 데 집중하고 요청 자체는 실패시키지 않는다.
            SearchStoreHealthResponse(status = UNAVAILABLE, detail = exception.message ?: "$name health check failed")
        }

    private fun checkNeo4j(): SearchStoreHealthResponse =
        try {
            neo4jDriver.verifyConnectivity()
            SearchStoreHealthResponse(status = READY, detail = "neo4j connectivity check succeeded")
        } catch (exception: Exception) {
            SearchStoreHealthResponse(status = UNAVAILABLE, detail = exception.message ?: "neo4j connectivity check failed")
        }

    private companion object {
        const val READY = "ready"
        const val UNAVAILABLE = "unavailable"
    }
}
