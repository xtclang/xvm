package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.MonikerKind
import org.eclipse.lsp4j.MonikerParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.UniquenessLevel
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.SymbolMoniker
import org.xvm.lsp.adapter.mock.MockAdapter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.SECONDS

class MonikerProtocolTest {
    @Test
    fun `monikers use protocol kinds scheme uniqueness and optional partial results`() {
        val values = SymbolMoniker.Kind.entries.map { SymbolMoniker("test", it.name, it) }
        val adapter =
            object : Adapter by MockAdapter() {
                override fun findMonikersAsync(
                    uri: String,
                    line: Int,
                    column: Int,
                ) = CompletableFuture.completedFuture(values)
            }
        val client = mock(LanguageClient::class.java)
        val events = CopyOnWriteArrayList<ProgressParams>()
        doAnswer {
            events.add(it.getArgument(0))
            null
        }.`when`(client).notifyProgress(any())
        XtcLanguageServer(adapter).use { server ->
            server.connect(client)
            server.initialize(editorInitializeParams()).join()
            val params = MonikerParams(TextDocumentIdentifier(URI), Position(0, 0))
            val expected = server.textDocumentService.moniker(params).get(5, SECONDS)
            assertThat(expected.map { it.kind }).containsExactly(MonikerKind.Import, MonikerKind.Export, MonikerKind.Local)
            assertThat(expected.map { it.unique }).containsOnly(UniquenessLevel.Scheme)
            params.partialResultToken = Either.forLeft("monikers")
            assertThat(server.textDocumentService.moniker(params).get(5, SECONDS)).isEmpty()
            assertThat(events.single().token).isEqualTo(params.partialResultToken)
            assertThat(events.single().value.right as List<*>).containsExactlyElementsOf(expected)
        }
    }

    @Test
    fun `edited and canceled moniker requests retire their backend and publish no partial IDs`() {
        listOf(false, true).forEach { cancel ->
            val entered = CompletableFuture<Unit>()
            val pending = CompletableFuture<List<SymbolMoniker>>()
            val adapter =
                object : Adapter by MockAdapter() {
                    override fun findMonikersAsync(
                        uri: String,
                        line: Int,
                        column: Int,
                    ): CompletableFuture<List<SymbolMoniker>> {
                        entered.complete(Unit)
                        return pending
                    }
                }
            val client = mock(LanguageClient::class.java)
            val events = CopyOnWriteArrayList<ProgressParams>()
            doAnswer {
                events.add(it.getArgument(0))
                null
            }.`when`(client).notifyProgress(any())
            XtcLanguageServer(adapter).use { server ->
                server.connect(client)
                server.initialize(editorInitializeParams()).join()
                val documents = server.textDocumentService
                documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", 1, "module App {}")))
                val params =
                    MonikerParams(TextDocumentIdentifier(URI), Position(0, 7)).apply {
                        partialResultToken = Either.forLeft("monikers")
                    }
                val result = documents.moniker(params)
                entered.get(5, SECONDS)
                if (cancel) {
                    result.cancel(false)
                } else {
                    documents.didChange(
                        DidChangeTextDocumentParams(
                            VersionedTextDocumentIdentifier(URI, 2),
                            listOf(TextDocumentContentChangeEvent("module Changed {}")),
                        ),
                    )
                }
                assertThatThrownBy { result.get(5, SECONDS) }.isInstanceOf(Exception::class.java)
                pending.complete(listOf(SymbolMoniker("test", "stale", SymbolMoniker.Kind.EXPORT)))
                assertThat(events).isEmpty()
            }
        }
    }

    companion object {
        private const val URI = "file:///App.x"
    }
}
