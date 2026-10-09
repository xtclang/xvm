package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

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
