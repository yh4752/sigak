package com.sigak.article.summary

import org.springframework.beans.factory.annotation.Value
import jakarta.servlet.http.HttpServletRequest
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/articles/{id}/summary")
class ArticleSummaryController(
    private val service: ArticleSummaryService,
    @Value("\${sigak.public-origin:}") private val publicOrigin: String = ""
) {
    private data class Window(val started: Long, var count: Int)
    private val windows=ConcurrentHashMap<String, Window>()

    @GetMapping
    fun get(@PathVariable id: Long): ArticleSummaryResponse = service.get(id)

    @PostMapping
    fun request(@PathVariable id: Long, request: HttpServletRequest): ArticleSummaryResponse {
        requireOrigin(request)
        limit(request.remoteAddr)
        return service.request(id)
    }

    private fun requireOrigin(request: HttpServletRequest) {
        // 실제 UI의 요청만 허용하되 GET이나 crawler의 읽기로는 생성하지 않는다.
        if(request.getHeader("Sec-Fetch-Site") == "cross-site") throw ResponseStatusException(HttpStatus.FORBIDDEN)
        val origin=request.getHeader("Origin") ?: return
        val parsed=runCatching { URI(origin) }.getOrNull() ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
        val allowed=origin == publicOrigin || parsed.rawAuthority == request.getHeader("Host") ||
            origin in setOf("http://localhost:5173","http://127.0.0.1:5173")
        if(!allowed || parsed.scheme !in setOf("http","https")) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    @Synchronized
    private fun limit(address: String) {
        val now=System.currentTimeMillis()
        windows.entries.removeIf { now-it.value.started > 600000 }
        if(windows.size >= 10000 && !windows.containsKey(address)) throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)
        val window=windows.computeIfAbsent(address) { Window(now,0) }
        if(window.count >= 30) throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)
        window.count++
    }
}

@RestController
class SummaryBudgetController(private val store: SummaryJobStore) {
    @GetMapping("/api/internal/summaries/budget")
    fun budget(): SummaryBudgetResponse = store.budget()
}
