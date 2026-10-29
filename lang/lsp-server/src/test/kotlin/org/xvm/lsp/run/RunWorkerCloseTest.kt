package org.xvm.lsp.run

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/** How a worker whose input has ended closes its runs before it exits. */
class RunWorkerCloseTest {
    @Test
    fun `closing waits for each run that closes in time, and gives up on one that never does`() {
        val closed = AtomicInteger()
        val never = CountDownLatch(1)
        val quick = AutoCloseable { closed.incrementAndGet() }
        val stuck = AutoCloseable { never.await() }
        try {
            // long enough for the quick one's thread to run on a loaded machine, short of the bound below
            val started = System.nanoTime()
            assertThat(closeAll(listOf(quick, stuck), 2.seconds)).isFalse()
            assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(5_000)
            assertThat(closed.get()).isEqualTo(1)

            assertThat(closeAll(listOf(quick, quick), 5.seconds)).isTrue()
            assertThat(closed.get()).isEqualTo(3)
        } finally {
            never.countDown()
        }
    }
}
