package com.sigak.article.summary

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoalescingWorkLoopTest {
    @Test fun `wake starts immediately and concurrent wake calls keep a single runner`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val drained = CountDownLatch(1)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val count = AtomicInteger()
        val loop = CoalescingWorkLoop("fixture") {
            val simultaneous = active.incrementAndGet()
            maximum.accumulateAndGet(simultaneous, ::maxOf)
            if (count.incrementAndGet() == 1) {
                started.countDown()
                release.await(2,TimeUnit.SECONDS)
            } else drained.countDown()
            active.decrementAndGet()
            false
        }
        try {
            loop.wake()
            assertTrue(started.await(1,TimeUnit.SECONDS))
            repeat(100) { loop.wake() }
            release.countDown()
            assertTrue(drained.await(1,TimeUnit.SECONDS))
            assertEquals(1,maximum.get())
        } finally { release.countDown(); loop.close() }
    }
}
