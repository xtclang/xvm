package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.mock.MockAdapter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

/**
 * A synchronous query that waits, as the compiler adapter's hover waits for a cursor analysis, must not hold
 * the document lock that `didChange` needs: the JSON-RPC reader would stall behind it, with every later
 * message, including cancellation.
 */
class DocumentLockTest {
    /** A hover that blocks until released, inside the adapter call, like a join on compiler work. */
    private class Backend(
        override val concurrentQueries: Boolean,
        private val delegate: MockAdapter = MockAdapter(),
    ) : Adapter by delegate {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun getHoverInfo(
            uri: String,
            line: Int,
            column: Int,
        ): String? {
            entered.countDown()
            check(release.await(30, SECONDS)) { "hover was never released" }
            return "Int value"
        }
    }

    private class Session(
        val backend: Backend,
    ) : AutoCloseable {
        val server = XtcLanguageServer(backend)
        val documents = server.textDocumentService

        init {
            server.connect(mock(LanguageClient::class.java))
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", 1, "module Editing {}")))
        }

        fun hover(): CompletableFuture<*> = documents.hover(HoverParams(TextDocumentIdentifier(URI), Position(0, 0)))

        /** Sends didChange on its own thread, as the JSON-RPC reader would; [returned] completes when it returns. */
        inner class Change {
            val returned = CompletableFuture<Unit>()
            val thread =
                Thread({
                    try {
                        documents.didChange(
                            DidChangeTextDocumentParams(
                                VersionedTextDocumentIdentifier(URI, 2),
                                listOf(TextDocumentContentChangeEvent("module Changed {}")),
                            ),
                        )
                        returned.complete(Unit)
                    } catch (failure: Throwable) {
                        returned.completeExceptionally(failure)
                    }
                }, "didChange").apply {
                    isDaemon = true
                    start()
                }
        }

        fun change() = Change()

        override fun close() {
            backend.release.countDown()
            server.shutdown().get(10, SECONDS)
        }
    }

    @Test
    fun `a waiting query does not block didChange and its stale result is rejected`() {
        Session(Backend(concurrentQueries = true)).use { session ->
            val hover = session.hover()
            assertThat(session.backend.entered.await(10, SECONDS)).isTrue()
            // returns while the hover is still blocked inside the adapter
            session.change().returned.get(5, SECONDS)
            session.backend.release.countDown()
            assertThatThrownBy { hover.get(10, SECONDS) }
                .isInstanceOf(ExecutionException::class.java)
                .cause()
                .isInstanceOfSatisfying(ResponseErrorException::class.java) {
                    assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.ContentModified.value)
                }
        }
    }

    @Test
    fun `a current result is still published without the lock`() {
        Session(Backend(concurrentQueries = true)).use { session ->
            val hover = session.hover()
            assertThat(session.backend.entered.await(10, SECONDS)).isTrue()
            session.backend.release.countDown()
            assertThat(hover.get(10, SECONDS)).isNotNull()
        }
    }

    @Test
    fun `adapters without concurrent queries keep updates serialized with the query`() {
        Session(Backend(concurrentQueries = false)).use { session ->
            val sequence = AtomicInteger()
            val hover = session.hover()
            // the server completes a hover under the document lock, so this runs before any later update is applied
            val answered = hover.handle { _, _ -> sequence.incrementAndGet() }
            assertThat(session.backend.entered.await(10, SECONDS)).isTrue()
            val change = session.change()
            // the update waits for the query, so the backend never sees them interleaved
            await().atMost(10, SECONDS).until { change.thread.state == Thread.State.BLOCKED }
            assertThat(change.returned).isNotDone()
            val applied = change.returned.thenApply { sequence.incrementAndGet() }
            session.backend.release.countDown()
            applied.get(10, SECONDS)
            // once released, the hover's publication and the update compete for the lock: a hover published
            // first answers for the version it was sent against, one published after the update is stale
            val outcome = runCatching { hover.get(10, SECONDS) }
            if (outcome.isSuccess) {
                assertThat(outcome.getOrNull()).isNotNull()
                assertThat(answered.get(10, SECONDS)).describedAs("A hover answered after the update").isLessThan(applied.get())
            } else {
                assertThat(outcome.exceptionOrNull())
                    .isInstanceOf(ExecutionException::class.java)
                    .cause()
                    .isInstanceOfSatisfying(ResponseErrorException::class.java) {
                        assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.ContentModified.value)
                    }
            }
        }
    }

    private companion object {
        const val URI = "file:///workspace/Editing.x"
    }
}
