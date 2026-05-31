package com.sigak.collection.repository

import com.sigak.collection.domain.CollectionFailureEventEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface CollectionFailureEventRepository : JpaRepository<CollectionFailureEventEntity, Long> {
    fun findByRunId(runId: UUID): List<CollectionFailureEventEntity>
}
