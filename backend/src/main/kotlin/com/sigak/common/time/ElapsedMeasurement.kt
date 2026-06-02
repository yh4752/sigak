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
