package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

/** Workspace rescans run off the JSON-RPC thread: a burst of file events costs one or two walks, never one each. */
class CoalescingTaskTest {
    @Test
    fun `requests during a run queue exactly one more run`() {
        val runs = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        CoalescingTask("test") {
            if (runs.incrementAndGet() == 1) {
                started.countDown()
                check(release.await(10, SECONDS))
            }
        }.use { task ->
            task.request()
            assertThat(started.await(10, SECONDS)).isTrue()
            repeat(50) { task.request() }
            release.countDown()
            task.idle().get(10, SECONDS)
            assertThat(runs.get()).isEqualTo(2)
        }
    }

    @Test
    fun `a failing run does not stop later requests`() {
        val runs = AtomicInteger()
        CoalescingTask("test") {
            if (runs.incrementAndGet() == 1) error("first walk fails")
        }.use { task ->
            task.request()
            task.idle().get(10, SECONDS)
            task.request()
            task.idle().get(10, SECONDS)
            assertThat(runs.get()).isEqualTo(2)
        }
    }

    @Test
    fun `a closed task ignores requests`() {
        val runs = AtomicInteger()
        val task = CoalescingTask("test") { runs.incrementAndGet() }
        task.close()
        task.request()
        task.idle().get(10, SECONDS)
        assertThat(runs.get()).isZero()
    }
}
