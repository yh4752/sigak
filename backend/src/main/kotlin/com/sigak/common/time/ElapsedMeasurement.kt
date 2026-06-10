package com.sigak.common.time

data class Measured<T>(
    val value: T,
    val elapsedMs: Long
)

fun elapsedMillis(startedAt: Long): Long =
    (System.nanoTime() - startedAt) / 1_000_000

fun <T> measureElapsed(block: () -> T): Measured<T> {
    val startedAt = System.nanoTime()
    val value = block()

    return Measured(value = value, elapsedMs = elapsedMillis(startedAt))
}

data class MeasuredAttempt<T>(
    val value: T?,
    val exception: Exception?,
    val elapsedMs: Long
)

fun <T> runCatchingMeasured(block: () -> T): MeasuredAttempt<T> {
    val startedAt = System.nanoTime()

    return try {
        MeasuredAttempt(value = block(), exception = null, elapsedMs = elapsedMillis(startedAt))
    } catch (exception: Exception) {
        // 검색 projection client는 실패 타입이 달라질 수 있어 Exception까지 잡아 실패와 소요 시간을 함께 기록한다.
        MeasuredAttempt(value = null, exception = exception, elapsedMs = elapsedMillis(startedAt))
    }
}
