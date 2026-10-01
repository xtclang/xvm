package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

class PartialResultsTest {
    @Test
    fun `partial batches precede an empty final response and preserve every item once`() {
        val client = mock(LanguageClient::class.java)
        val events = CopyOnWriteArrayList<ProgressParams>()
        doAnswer {
            events.add(it.getArgument(0))
            null
        }.`when`(client)
            .notifyProgress(any())
        PartialResults({ client }).use { publisher ->
            val values = (0..129).toList()
            val source = CompletableFuture<List<Int>>()
            val token = Either.forRight<String, Int>(12)
            val result = publisher.publish(token, source, {}, PartialResults::list)
            source.complete(values)
            assertThat(result.get(5, SECONDS)).isEmpty()
            assertThat(events.map { it.token }).containsOnly(token)
            assertThat(events.map { (it.value.right as List<*>).size }).containsExactly(64, 64, 2)
            assertThat(events.flatMap { it.value.right as List<*> })
                .containsExactlyElementsOf(values)
            val normal = CompletableFuture.completedFuture(values)
            assertThat(
                publisher.publish(
                    null,
                    normal,
                    { error("No partial publication") },
                    PartialResults::list,
                ),
            ).isSameAs(normal)
        }
    }

    @Test
    fun `canceled stale and disconnected requests stop after the in flight batch`() {
        listOf("cancel", "stale", "close").forEach { mode ->
            val client = mock(LanguageClient::class.java)
            val events = CopyOnWriteArrayList<ProgressParams>()
            val entered = CompletableFuture<Void>()
            val release = CompletableFuture<Void>()
            val current = AtomicBoolean(true)
            val dispatcher = Executors.newSingleThreadExecutor()
            doAnswer {
                events.add(it.getArgument(0))
                entered.complete(null)
                release.get(5, SECONDS)
                null
            }.`when`(client)
                .notifyProgress(any())
            PartialResults({ client }, dispatcher).use { publisher ->
                val result =
                    publisher.publish(
                        Either.forLeft("partial"),
                        CompletableFuture.completedFuture((0..129).toList()),
                        { check(current.get()) { "Snapshot changed" } },
                        PartialResults::list,
                    )
                try {
                    entered.get(5, SECONDS)
                    when (mode) {
                        "cancel" -> result.cancel(false)
                        "stale" -> current.set(false)
                        "close" -> publisher.close()
                    }
                } finally {
                    release.complete(null)
                }
                assertThatThrownBy { result.get(5, SECONDS) }.isInstanceOf(Exception::class.java)
                if (mode != "close") {
                    dispatcher.submit {}.get(5, SECONDS)
                } else {
                    assertThat(dispatcher.awaitTermination(5, SECONDS)).isTrue()
                }
                assertThat(events).hasSize(1)
            }
        }
    }

    @Test
    fun `failed backend requests publish no partial payload`() {
        val client = mock(LanguageClient::class.java)
        val events = CopyOnWriteArrayList<ProgressParams>()
        doAnswer {
            events.add(it.getArgument(0))
            null
        }.`when`(client)
            .notifyProgress(any())
        PartialResults({ client }).use { publisher ->
            val result =
                publisher.publish(
                    Either.forLeft("failed"),
                    CompletableFuture.failedFuture<List<Int>>(IllegalStateException("failed")),
                    {},
                    PartialResults::list,
                )
            assertThatThrownBy { result.get(5, SECONDS) }.hasMessageContaining("failed")
            assertThat(events).isEmpty()
        }
    }

    @Test
    fun `workspace symbol endpoint uses protocol lists and keeps normal results when no token is supplied`() {
        val symbols =
            (0..129).map {
                SymbolInfo.of(
                    "Value$it",
                    SymbolInfo.SymbolKind.CLASS,
                    Location.of("file:///Values.x", it, 0),
                )
            }
        val adapter =
            object : Adapter by MockAdapter() {
                override fun findWorkspaceSymbols(query: String) = symbols
            }
        val client = mock(LanguageClient::class.java)
        val events = CopyOnWriteArrayList<ProgressParams>()
        doAnswer {
            events.add(it.getArgument(0))
            null
        }.`when`(client)
            .notifyProgress(any())
        XtcLanguageServer(adapter).use { server ->
            server.connect(client)
            server.initialize(editorInitializeParams()).join()
            val query =
                WorkspaceSymbolParams("Value").apply {
                    partialResultToken = Either.forLeft("symbols")
                }
            assertThat(
                server.workspaceService
                    .symbol(query)
                    .get(5, SECONDS)
                    .left,
            ).isEmpty()
            assertThat(events.flatMap { it.value.right as List<*> }).hasSize(130)
            query.partialResultToken = null
            assertThat(
                server.workspaceService
                    .symbol(query)
                    .get(5, SECONDS)
                    .left,
            ).hasSize(130)
            assertThat(events).hasSize(3)
        }
    }
}
