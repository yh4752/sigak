package com.sigak.article.summary

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SummaryWorkAvailable

// 클릭마다 스레드를 만들지 않고 한 실행기에서 작업을 모아 처리한다.
class CoalescingWorkLoop(name: String, private val runOne: () -> Boolean) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, name).apply { isDaemon = true } }
    private val running = AtomicBoolean(false)
    private val requested = AtomicBoolean(false)
    fun wake() {
        requested.set(true)
        if (!running.compareAndSet(false, true)) return
        executor.submit {
            try {
                do {
                    requested.set(false)
                    while (runOne()) { /* 영속 큐를 비울 때까지 같은 실행기를 사용한다. */ }
                } while (requested.get())
            } finally {
                running.set(false)
                if (requested.get() && !executor.isShutdown) wake()
            }
        }
    }
    override fun close() { executor.shutdownNow() }
}
