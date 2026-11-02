package com.sigak.source.service

import com.sigak.common.http.ExternalFetchException
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.jdbc.core.JdbcTemplate

class SourceNetworkLockTest {
    @Test
    fun `concurrent sessions reject same source while another source can proceed without an open transaction`() {
        ownerPool().use { pool ->
            val owner = SourceNetworkLock(pool)
            val contenderDataSource = SourceContractDatabase.dataSource()
            val contender = SourceNetworkLock(contenderDataSource)
            val key = "lock-${UUID.randomUUID()}"
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val worker = Executors.newSingleThreadExecutor()
            val ownerPid = JdbcTemplate(pool).queryForObject("select pg_backend_pid()", Int::class.java)!!
            try {
                val held = worker.submit<String> {
                    owner.withPermit(key) {
                        entered.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        "completed"
                    }
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                var duplicateEntered = false
                val rejection = assertFailsWith<ExternalFetchException> {
                    contender.withPermit(key) { duplicateEntered = true }
                }
                assertEquals("SOURCE_BUSY", rejection.code)
                assertEquals(false, duplicateEntered)
                assertEquals("independent", contender.withPermit("$key-other") { "independent" })
                val activity = JdbcTemplate(contenderDataSource).queryForMap(
                    "select state,xact_start from pg_stat_activity where pid=?", ownerPid)
                assertEquals("idle", activity["state"])
                assertNull(activity["xact_start"])
                release.countDown()
                assertEquals("completed", held.get(5, TimeUnit.SECONDS))
                assertEquals("released", contender.withPermit(key) { "released" })
            } finally {
                release.countDown()
                worker.shutdownNow()
                worker.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }

    @Test
    fun `action exception unlocks a still pooled physical connection for an independent session`() {
        ownerPool().use { pool ->
            val owner = SourceNetworkLock(pool)
            val contender = SourceNetworkLock(SourceContractDatabase.dataSource())
            val key = "exception-${UUID.randomUUID()}"
            val pid = JdbcTemplate(pool).queryForObject("select pg_backend_pid()", Int::class.java)
            val failure = assertFailsWith<IllegalStateException> {
                owner.withPermit(key) { throw IllegalStateException("fixture action failed") }
            }
            assertEquals("fixture action failed", failure.message)
            assertEquals(pid, JdbcTemplate(pool).queryForObject("select pg_backend_pid()", Int::class.java))
            assertEquals("released", contender.withPermit(key) { "released" })
        }
    }

    private fun ownerPool(): HikariDataSource {
        val dataSource = SourceContractDatabase.dataSource()
        return HikariDataSource(HikariConfig().apply {
            jdbcUrl = dataSource.url
            username = dataSource.username
            password = dataSource.password
            minimumIdle = 1
            maximumPoolSize = 1
            connectionTimeout = 3000
            poolName = "source-lock-${UUID.randomUUID()}"
        })
    }
}
