package com.sigak.source.repository

import com.sigak.source.domain.NewsSourceEntity
import org.springframework.data.jpa.repository.JpaRepository

interface NewsSourceRepository : JpaRepository<NewsSourceEntity, Long> {
    fun findBySourceKey(sourceKey: String): NewsSourceEntity?
}
