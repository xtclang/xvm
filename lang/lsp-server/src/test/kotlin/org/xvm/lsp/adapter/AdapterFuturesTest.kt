package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

/**
 * Verifies cancellation propagation through analysis and dependent proof futures. A canceled request must not
 * start later proof work, and cancellation during future construction must reach the newly created work.
 */
class AdapterFuturesTest {
    @Test
    fun `cancellation before analysis completes does not start proof`() {
        val analysis = CompletableFuture<String>()
        val proof = CompletableFuture<Int>()
        val result = analysis.composeCancellable { proof }
        result.cancel(false)
        assertThat(analysis).isCancelled()
        assertThat(proof).isNotDone()
    }

    @Test
    fun `cancellation follows a running proof`() {
        val proof = CompletableFuture<Int>()
        val result = CompletableFuture.completedFuture("analysis").composeCancellable { proof }
        result.cancel(false)
        assertThat(proof).isCancelled()
    }

    @Test
    fun `cancellation during proof creation is delivered to the new future`() {
        val analysis = CompletableFuture<String>()
        val proof = CompletableFuture<Int>()
        val cancelled = CompletableFuture<Unit>()
        val result =
            analysis.composeCancellable {
                cancelled.complete(Unit)
                proof
            }
        cancelled.thenRun { result.cancel(false) }
        analysis.complete("analysis")
        assertThat(proof).isCancelled()
    }

    @Test
    fun `a converted result asks its work to stop, also after the work answered`() {
        // work that records each cancellation it is asked, as a backend that ignores it does
        val asked = mutableListOf<Boolean>()

        fun work() =
            object : CompletableFuture<Int>() {
                override fun cancel(mayInterruptIfRunning: Boolean): Boolean = super.cancel(mayInterruptIfRunning).also { asked += it }
            }
        val running = work()
        val converted = running.mapCancellable { it + 1 }
        assertThat(converted.cancel(false)).isTrue()
        assertThat(running).isCancelled()

        // an edit can make the request stale just after its work answered: the work is asked all the same
        val answered = work()
        val mapped = answered.mapCancellable { it + 1 }
        val failed = work().mapFailureCancellable { it }
        answered.complete(1)
        assertThat(mapped.join()).isEqualTo(2)
        mapped.cancel(false)
        failed.cancel(false)
        assertThat(asked).containsExactly(true, false, true)
    }

    @Test
    fun `proof values and construction failures complete the outer request`() {
        val value =
            CompletableFuture.completedFuture("analysis").composeCancellable {
                CompletableFuture.completedFuture(42)
            }
        assertThat(value.join()).isEqualTo(42)
        val failure = AssertionError("proof failed")
        val failed =
            CompletableFuture.completedFuture("analysis").composeCancellable<String, Int> {
                throw failure
            }
        assertThat(failed.handle { _, error -> error }.join()).isSameAs(failure)
    }
}
