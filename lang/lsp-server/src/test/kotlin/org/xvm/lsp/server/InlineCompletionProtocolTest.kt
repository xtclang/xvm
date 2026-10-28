package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InlineCompletionContext
import org.eclipse.lsp4j.InlineCompletionParams
import org.eclipse.lsp4j.InlineCompletionTriggerKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SelectedCompletionInfo
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.AdapterCapability
import org.xvm.lsp.adapter.TextEdit
import org.xvm.lsp.adapter.mock.MockAdapter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit.SECONDS
import org.xvm.lsp.adapter.InlineCompletionContext as AdapterContext
import org.xvm.lsp.adapter.Position as AdapterPosition
import org.xvm.lsp.adapter.Range as AdapterRange

class InlineCompletionProtocolTest {
    @Test
    fun `inline completion negotiates independently and round trips selection trigger and plain edits`() {
        val received = CompletableFuture<AdapterContext>()
        val adapter =
            inlineAdapter { context ->
                received.complete(context)
                CompletableFuture.completedFuture(listOf(TextEdit(AdapterRange(AdapterPosition(0, 0), AdapterPosition(0, 2)), "answer")))
            }
        XtcLanguageServer(adapter).use { server ->
            server.connect(mock(LanguageClient::class.java))
            val capabilities = server.initialize(editorInitializeParams()).join().capabilities
            assertThat(capabilities.inlineCompletionProvider.left).isTrue()
            val request =
                params().apply {
                    context.selectedCompletionInfo = SelectedCompletionInfo(Range(Position(0, 0), Position(0, 2)), "ans")
                }
            val item =
                server.textDocumentService
                    .inlineCompletion(request)
                    .get(5, SECONDS)
                    .right.items
                    .single()
            assertThat(item.insertText.left).isEqualTo("answer")
            assertThat(item.filterText).isEqualTo("answer")
            assertThat(item.range).isEqualTo(request.context.selectedCompletionInfo.range)
            assertThat(item.command).isNull()
            assertThat(received.join()).isEqualTo(
                AdapterContext(false, TextEdit(AdapterRange(AdapterPosition(0, 0), AdapterPosition(0, 2)), "ans")),
            )
        }
    }

    @Test
    fun `unnegotiated clients and unsupported adapters cannot invoke inline completion`() {
        listOf(false, true).forEach { advertised ->
            val adapter = if (advertised) inlineAdapter { error("Unnegotiated request reached adapter") } else MockAdapter()
            XtcLanguageServer(adapter).use { server ->
                val initialize = editorInitializeParams().apply { if (advertised) capabilities.textDocument.inlineCompletion = null }
                assertThat(
                    server
                        .initialize(initialize)
                        .join()
                        .capabilities.inlineCompletionProvider,
                ).isNull()
                val failure =
                    assertThrows(CompletionException::class.java) {
                        server.textDocumentService.inlineCompletion(params()).join()
                    }
                assertThat(failure.cause).isInstanceOf(ResponseErrorException::class.java)
                assertThat((failure.cause as ResponseErrorException).responseError.code)
                    .isEqualTo(ResponseErrorCode.MethodNotFound.value)
            }
        }
    }

    @Test
    fun `typing or cancellation retires an inline query before it can publish`() {
        listOf(false, true).forEach { cancel ->
            val entered = CompletableFuture<Unit>()
            val canceled = CompletableFuture<Unit>()
            val pending =
                CompletableFuture<List<TextEdit>>().apply {
                    whenComplete { _, _ -> if (isCancelled) canceled.complete(Unit) }
                }
            val adapter =
                inlineAdapter {
                    entered.complete(Unit)
                    pending
                }
            XtcLanguageServer(adapter).use { server ->
                server.connect(mock(LanguageClient::class.java))
                server.initialize(editorInitializeParams()).join()
                val documents = server.textDocumentService
                documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", 1, "module App {}")))
                val result = documents.inlineCompletion(params())
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
                canceled.get(5, SECONDS)
                assertThat(pending.isCancelled).isTrue()
            }
        }
    }

    private fun inlineAdapter(query: (AdapterContext) -> CompletableFuture<List<TextEdit>>) =
        object : Adapter by MockAdapter() {
            override val capabilities = setOf(AdapterCapability.INLINE_COMPLETION)

            override fun getInlineCompletionsAsync(
                uri: String,
                position: AdapterPosition,
                context: AdapterContext,
            ) = query(context)
        }

    private fun params() =
        InlineCompletionParams(
            TextDocumentIdentifier(URI),
            Position(0, 2),
            InlineCompletionContext(InlineCompletionTriggerKind.Invoked),
        )

    companion object {
        private const val URI = "file:///App.x"
    }
}
