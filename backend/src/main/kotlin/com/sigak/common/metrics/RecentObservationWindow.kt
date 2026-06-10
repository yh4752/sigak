package com.sigak.common.metrics

// MVP 단계 메트릭은 외부 저장소 없이 프로세스 메모리에 최근 관측치만 보관한다.
class RecentObservationWindow<T>(
    private val maxSize: Int
) {

    private val observations = ArrayDeque<T>()

    @Synchronized
    fun record(observation: T) {
        observations.addLast(observation)
        if (observations.size > maxSize) {
            observations.removeFirst()
        }
    }

    @Synchronized
    fun reset() {
        observations.clear()
    }

    @Synchronized
    fun snapshot(): List<T> =
        observations.toList()
}

fun percentileOf(sortedValues: List<Long>, percentile: Double): Long {
    val index = kotlin.math.ceil(sortedValues.size * percentile).toInt().coerceAtLeast(1) - 1

    return sortedValues[index.coerceAtMost(sortedValues.lastIndex)]
}
