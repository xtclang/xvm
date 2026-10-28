package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.model.CompilationResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

class RequestOwnershipTest {
    @Test
    fun `canceling a request awaiting analysis does not cancel another reader or shared analysis`() {
        val analysis = CompletableFuture<CompilationResult>()
        val queries = AtomicInteger()
        val adapter =
            object : Adapter by MockAdapter() {
                override fun compileAsync(
                    uri: String,
                    content: String,
                ) = analysis

                override fun getHoverInfo(
                    uri: String,
                    line: Int,
                    column: Int,
                ): String {
                    queries.incrementAndGet()
                    return "Int"
                }
            }
        XtcLanguageServer(adapter).use { server ->
            val uri = "file:///Owned.x"
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, "module Owned {}")),
            )
            val params = HoverParams(TextDocumentIdentifier(uri), Position(0, 0))
            val canceled = server.textDocumentService.hover(params)
            val surviving = server.textDocumentService.hover(params)
            canceled.cancel(false)
            assertThat(analysis.isDone).isFalse()
            analysis.complete(MockAdapter().compile(uri, "module Owned {}"))
            assertThat(
                surviving
                    .get(5, SECONDS)
                    .contents.right.value,
            ).isEqualTo("Int")
            assertThat(queries.get()).isEqualTo(1)
            assertThat(canceled.isCancelled).isTrue()
        }
    }

    @Test
    fun `close immediately retires a reader even when analysis ignores cancellation`() {
        val analysis =
            object : CompletableFuture<CompilationResult>() {
                override fun cancel(mayInterruptIfRunning: Boolean) = false
            }
        val adapter =
            object : Adapter by MockAdapter() {
                override fun compileAsync(
                    uri: String,
                    content: String,
                ) = analysis
            }
        XtcLanguageServer(adapter).use { server ->
            val uri = "file:///Owned.x"
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, "module Owned {}")),
            )
            val reader =
                server.textDocumentService.hover(
                    HoverParams(TextDocumentIdentifier(uri), Position(0, 0)),
                )
            server.close()
            assertThat(reader.isCompletedExceptionally).isTrue()
            assertThat(analysis.isDone).isFalse()
        }
    }
}
