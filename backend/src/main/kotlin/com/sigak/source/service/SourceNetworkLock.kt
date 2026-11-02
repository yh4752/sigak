package com.sigak.source.service

import com.sigak.common.http.ExternalFetchException
import javax.sql.DataSource
import org.springframework.stereotype.Component

interface SourceNetworkGate {
    fun <T> withPermit(key: String, action: () -> T): T
}

@Component
class SourceNetworkLock(private val dataSource: DataSource) : SourceNetworkGate {
    override fun <T> withPermit(key: String, action: () -> T): T {
        // session 잠금은 network I/O 중 transaction 없이 운영 명령과 기존 worker를 함께 제한한다.
        dataSource.connection.use { connection ->
            connection.prepareStatement("select pg_try_advisory_lock(hashtextextended(?,1))").use { statement ->
                statement.setString(1,key)
                statement.executeQuery().use { result ->
                    if (!result.next() || !result.getBoolean(1)) throw ExternalFetchException("SOURCE_BUSY")
                }
            }
            try { return action() }
            finally {
                connection.prepareStatement("select pg_advisory_unlock(hashtextextended(?,1))").use { statement ->
                    statement.setString(1,key)
                    statement.execute()
                }
            }
        }
    }
}
