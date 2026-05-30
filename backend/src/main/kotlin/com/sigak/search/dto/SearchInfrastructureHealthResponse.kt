package com.sigak.search.dto

data class SearchInfrastructureHealthResponse(
    val overallStatus: String,
    val elasticsearch: SearchStoreHealthResponse,
    val qdrant: SearchStoreHealthResponse,
    val neo4j: SearchStoreHealthResponse
)

data class SearchStoreHealthResponse(
    val status: String,
    val detail: String
)
