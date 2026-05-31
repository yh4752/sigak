package com.sigak.collection.repository

import com.sigak.collection.domain.CollectionFailureEventEntity
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CollectionFailureEventRepository : JpaRepository<CollectionFailureEventEntity, Long> {
    fun findByRunId(runId: UUID): List<CollectionFailureEventEntity>

    @Query(
        """
        select event
        from CollectionFailureEventEntity event
        where (:sourceKey is null or event.sourceKey = :sourceKey)
          and (:runId is null or event.runId = :runId)
          and (:retryable is null or event.retryable = :retryable)
        order by event.occurredAt desc, event.id desc
        """
    )
    fun searchLatest(
        @Param("sourceKey") sourceKey: String?,
        @Param("runId") runId: UUID?,
        @Param("retryable") retryable: Boolean?,
        pageable: Pageable
    ): List<CollectionFailureEventEntity>
}
