package com.sigak.collection.service

import com.sigak.SigakBackendApplication
import com.sigak.collection.domain.CollectionFailureKind
import com.sigak.collection.dto.CollectionFailureStage
import com.sigak.collection.repository.CollectionFailureEventRepository
import com.sigak.support.PostgresIntegrationTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [SigakBackendApplication::class])
class CollectionFailureEventRecorderTest : PostgresIntegrationTest() {

    @Autowired
    private lateinit var recorder: CollectionFailureEventRecorder

    @Autowired
    private lateinit var repository: CollectionFailureEventRepository

    @Test
    fun recordPersistsFailureEvidenceWithFingerprintAndArticleHints() {
        val runId = UUID.randomUUID()

        val event = recorder.record(
            CollectionFailureEventRecordRequest(
                runId = runId,
                sourceId = "github-blog",
                stage = CollectionFailureStage.PUBLISH_ARTICLE,
                failureKind = CollectionFailureKind.INVALID_ARTICLE,
                retryable = false,
                message = "IllegalArgumentException: title must not be blank",
                articleExternalId = "gh-1",
                articleUrl = "https://github.blog/example",
                articleTitle = "Broken article"
            )
        )

        val saved = repository.findById(requireNotNull(event.id)).orElseThrow()
        assertEquals(runId, saved.runId)
        assertEquals("github-blog", saved.sourceKey)
        assertEquals(CollectionFailureStage.PUBLISH_ARTICLE, saved.stage)
        assertEquals(CollectionFailureKind.INVALID_ARTICLE, saved.failureKind)
        assertEquals(false, saved.retryable)
        assertEquals("IllegalArgumentException: title must not be blank", saved.message)
        assertEquals("gh-1", saved.articleExternalId)
        assertEquals("https://github.blog/example", saved.articleUrl)
        assertEquals("Broken article", saved.articleTitle)
        assertNotNull(saved.occurredAt)
        assertEquals(true, saved.fingerprint.isNotBlank())
    }
}
