package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkDoneProgressKind
import org.eclipse.lsp4j.WorkDoneProgressReport
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

class ConnectionProgressTest {
    private class Session : AutoCloseable {
        val dispatcher = Executors.newSingleThreadExecutor()
        val client = mock(LanguageClient::class.java)
        val created = LinkedBlockingQueue<WorkDoneProgressCreateParams>()
        val events = LinkedBlockingQueue<ProgressParams>()
        val ack = CompletableFuture<Void>()
        val progress = ConnectionProgress({ client }, 0, dispatcher)

        init {
            doAnswer { call ->
                created.add(call.getArgument(0))
                ack
            }.`when`(client)
                .createProgress(any())
            doAnswer { call ->
                events.add(call.getArgument(0))
                null
            }.`when`(client)
                .notifyProgress(any())
        }

        fun event() = requireNotNull(events.poll(5, SECONDS)) { "Missing progress notification" }

        fun flush() {
            dispatcher.submit {}.get(5, SECONDS)
        }

        override fun close() {
            progress.close()
            assertThat(dispatcher.awaitTermination(5, SECONDS)).isTrue()
        }
    }

    @Test
    fun `index progress reports belong to the live operation and finish after its future`() {
        Session().use { session ->
            val work = CompletableFuture<Unit>()
            session.progress.track("Indexing", Either.forLeft("index"), work)
            session.event()
            session.progress.report(work, "Indexing: 50/100 files", 50)
            val report = session.event().value.left as WorkDoneProgressReport
            assertThat(report.percentage).isEqualTo(50)
            assertThat(report.message).contains("50/100")
            assertThat(work.isDone).isFalse()
            work.complete(Unit)
            assertThat(
                session
                    .event()
                    .value.left.kind,
            ).isEqualTo(WorkDoneProgressKind.end)
            session.progress.report(work, "Late report", 100)
            session.flush()
            assertThat(session.events).isEmpty()
        }
    }

    @Test
    fun `supplied tokens work before initialized and end once`() {
        Session().use { session ->
            val work = CompletableFuture<String>()
            val token = Either.forRight<String, Int>(7)
            session.progress.track("Indexing", token, work)
            assertThat(
                session
                    .event()
                    .value.left.kind,
            ).isEqualTo(WorkDoneProgressKind.begin)
            assertThat(session.created).isEmpty()
            work.complete("done")
            assertThat(
                session
                    .event()
                    .value.left.kind,
            ).isEqualTo(WorkDoneProgressKind.end)
            session.progress.cancel(token)
            session.flush()
            assertThat(work.join()).isEqualTo("done")
            assertThat(session.events).isEmpty()
        }
    }

    @Test
    fun `server tokens wait for handshake and create acknowledgement and cancel only owned work`() {
        Session().use { session ->
            val unreported = CompletableFuture<String>()
            session.progress.track("Before handshake", null, unreported)
            session.flush()
            assertThat(session.created).isEmpty()
            session.progress.initialized(true)
            val work = CompletableFuture<String>()
            session.progress.track("Rename", null, work)
            val token = requireNotNull(session.created.poll(5, SECONDS)).token
            assertThat(session.events).isEmpty()
            session.ack.complete(null)
            assertThat(session.event().token).isEqualTo(token)
            session.progress.cancel(token)
            assertThat(
                session
                    .event()
                    .value.left.kind,
            ).isEqualTo(WorkDoneProgressKind.end)
            assertThat(work.isCancelled).isTrue()
            assertThat(unreported.isDone).isFalse()
        }
    }

    @Test
    fun `late creation and rejected creation cannot resurrect completed work`() {
        listOf(false, true).forEach { reject ->
            Session().use { session ->
                session.progress.initialized(true)
                val work = CompletableFuture<String>()
                session.progress.track("References", null, work)
                assertThat(session.created.poll(5, SECONDS)).isNotNull()
                work.complete("done")
                session.flush()
                if (reject) {
                    session.ack.completeExceptionally(IllegalStateException("unsupported"))
                } else {
                    session.ack.complete(null)
                }
                session.flush()
                if (!reject) {
                    assertThat(
                        session
                            .event()
                            .value.left.kind,
                    ).isEqualTo(WorkDoneProgressKind.begin)
                    assertThat(
                        session
                            .event()
                            .value.left.kind,
                    ).isEqualTo(WorkDoneProgressKind.end)
                }
                assertThat(session.events).isEmpty()
                assertThat(work.join()).isEqualTo("done")
            }
        }
    }

    @Test
    fun `completion cancellation and close racing still end each started token once`() {
        Session().use { session ->
            val work = List(40) { CompletableFuture<String>() }
            work.forEachIndexed { index, result ->
                session.progress.track("Query $index", Either.forRight(index), result)
            }
            val started = work.map { session.event() }
            assertThat(started).allSatisfy {
                assertThat(it.value.left.kind).isEqualTo(WorkDoneProgressKind.begin)
            }
            val completions =
                work.mapIndexed { index, result ->
                    CompletableFuture.runAsync {
                        if (index % 2 == 0) {
                            result.complete("done")
                        } else {
                            session.progress.cancel(Either.forRight(index))
                        }
                    }
                } + CompletableFuture.runAsync { session.progress.close() }
            CompletableFuture.allOf(*completions.toTypedArray()).get(5, SECONDS)
            val ended = work.map { session.event() }
            assertThat(ended).allSatisfy {
                assertThat(it.value.left.kind).isEqualTo(WorkDoneProgressKind.end)
            }
            assertThat(ended.map { it.token })
                .containsExactlyInAnyOrderElementsOf(started.map { it.token })
            assertThat(work).allSatisfy { assertThat(it.isDone).isTrue() }
        }
    }

    @Test
    fun `disconnect ends and cancels active progress`() {
        Session().use { session ->
            val work = CompletableFuture<String>()
            session.progress.track("Checking workspace", Either.forLeft("owned"), work)
            session.event()
            session.progress.close()
            assertThat(
                session
                    .event()
                    .value.left.kind,
            ).isEqualTo(WorkDoneProgressKind.end)
            assertThat(work.isCancelled).isTrue()
        }
    }
}
