package org.xvm.lsp.run

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.xvm.lsp.HANG_SECONDS
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
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
            // the stuck one never closes, so the limit ends the wait however fast the machine is
            val gaveUp = CompletableFuture.supplyAsync { closeAll(listOf(quick, stuck), 2.seconds) }
            assertThat(gaveUp.get(HANG_SECONDS, SECONDS)).isFalse()
            await().atMost(HANG_SECONDS, SECONDS).until { closed.get() == 1 }

            // with a limit that only a hang reaches, closing returns once both have closed
            assertThat(closeAll(listOf(quick, quick), HANG_SECONDS.seconds)).isTrue()
            assertThat(closed.get()).isEqualTo(3)
        } finally {
            never.countDown()
        }
    }
}
