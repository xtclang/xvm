package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.model.CompilationResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

class XdkLanguageServerTest {
    private class Session(
        adapter: Adapter,
    ) : AutoCloseable {
        val published = LinkedBlockingQueue<PublishDiagnosticsParams>()
        val server = XtcLanguageServer(adapter)
        val documents = server.textDocumentService

        init {
            val client = mock(LanguageClient::class.java)
            doAnswer { call ->
                published.add(call.getArgument(0))
                null
            }.`when`(client).publishDiagnostics(org.mockito.ArgumentMatchers.any())
            server.connect(client)
        }

        fun open(
            content: String,
            version: Int = 1,
        ) = documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", version, content)))

        fun change(
            content: String,
            version: Int,
        ) = documents.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(URI, version),
                listOf(TextDocumentContentChangeEvent(content)),
            ),
        )

        fun closeDocument() = documents.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(URI)))

        fun next(): PublishDiagnosticsParams = checkNotNull(published.poll(30, SECONDS)) { "no diagnostic publication" }

        override fun close() {
            server.shutdown().get(10, SECONDS)
        }
    }

    /** An uncooperative backend can finish after cancellation; publication still must be guarded. */
    private class PendingAdapter(
        private val delegate: MockAdapter = MockAdapter(),
    ) : Adapter by delegate {
        private data class Pending(
            val uri: String,
            val content: String,
            val result: CompletableFuture<CompilationResult>,
        )

        private val pending = mutableListOf<Pending>()
        val count: Int get() = pending.size

        override fun compileAsync(
            uri: String,
            content: String,
        ): CompletableFuture<CompilationResult> {
            val result =
                object : CompletableFuture<CompilationResult>() {
                    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
                }
            pending.add(Pending(uri, content, result))
            return result
        }

        fun finish(index: Int) {
            val item = pending[index]
            item.result.complete(delegate.compile(item.uri, item.content))
        }

        fun fail(index: Int) {
            pending[index].result.completeExceptionally(IllegalArgumentException("compiler failure"))
        }
    }

    @Test
    fun `only the newest version publishes even if old analysis finishes last`() {
        val adapter = PendingAdapter()
        Session(adapter).use { session ->
            session.open("// ERROR: old")
            session.change("module Latest {}", 2)
            session.change("// ERROR: out of order", 1)
            assertThat(adapter.count).isEqualTo(2)
            adapter.finish(1)
            val latest = session.next()
            assertThat(latest.version).isEqualTo(2)
            assertThat(latest.diagnostics).isEmpty()
            adapter.finish(0)
            assertThat(session.published).isEmpty()
        }
    }

    @Test
    fun `closed and reopened documents reject results from the previous lifetime`() {
        val adapter = PendingAdapter()
        Session(adapter).use { session ->
            session.open("// ERROR: old", 10)
            session.closeDocument()
            assertThat(session.next().diagnostics).isEmpty()
            session.open("module Reopened {}")
            adapter.finish(0)
            assertThat(session.published).isEmpty()
            adapter.finish(1)
            assertThat(session.next().version).isEqualTo(1)
            session.close()
            session.change("// ERROR: after shutdown", 2)
            assertThat(adapter.count).isEqualTo(2)
        }
    }

    @Test
    fun `shutdown prevents pending analysis from publishing`() {
        val adapter = PendingAdapter()
        Session(adapter).use { session ->
            session.open("// ERROR: pending")
            session.close()
            adapter.finish(0)
            assertThat(session.published).isEmpty()
        }
    }

    @Test
    fun `document features wait for analysis without recompiling`() {
        val adapter = PendingAdapter()
        Session(adapter).use { session ->
            session.open("module Waiting {}")
            val outline = session.documents.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(URI)))
            assertThat(outline.isDone).isFalse()
            adapter.finish(0)
            assertThat(outline.get(10, SECONDS).map { it.right.name }).contains("Waiting")
            assertThat(adapter.count).isEqualTo(1)
        }
    }

    @Test
    fun `a feature request cannot use a different document version`() {
        val adapter = PendingAdapter()
        Session(adapter).use { session ->
            session.open("module Old {}")
            val outline = session.documents.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(URI)))
            session.change("module New {}", 2)
            adapter.finish(0)
            assertThatThrownBy { outline.get(10, SECONDS) }.hasMessageContaining("Document changed during analysis")
        }
    }

    @Test
    fun `unexpected analysis failure is visible and a later edit recovers`() {
        val adapter = PendingAdapter()
        Session(adapter).use { session ->
            session.open("module Failed {}")
            adapter.fail(0)
            assertThat(
                session
                    .next()
                    .diagnostics
                    .single()
                    .code.left,
            ).isEqualTo("ANALYSIS-FAILED")
            session.change("module Recovered {}", 2)
            adapter.finish(1)
            assertThat(session.next().diagnostics).isEmpty()
        }
    }

    @Test
    fun `compiler diagnostics travel through LSP with severity codes spans and versions`() {
        CompilerTestSupport.configure()
        Session(XdkAdapter()).use { session ->
            session.open("module Broken {\n    void run() {\n        Int n = ;\n    }\n}")
            val syntax = session.next()
            assertThat(syntax.uri).isEqualTo(URI)
            assertThat(syntax.version).isEqualTo(1)
            assertThat(syntax.diagnostics).anySatisfy { diagnostic ->
                assertThat(diagnostic.severity).isEqualTo(DiagnosticSeverity.Error)
                assertThat(diagnostic.code.left).startsWith("PARSER-")
                assertThat(diagnostic.range.start.line).isEqualTo(2)
                assertThat(diagnostic.source).isEqualTo("xtc")
            }
            session.change("module Semantic { void run() { NoSuchTypeAnywhere x = 1; } }", 2)
            val semantic = session.next()
            assertThat(semantic.version).isEqualTo(2)
            assertThat(semantic.diagnostics).anySatisfy {
                assertThat(it.message.left).contains("NoSuchTypeAnywhere")
                assertThat(it.severity).isEqualTo(DiagnosticSeverity.Error)
            }
            session.change(
                "module Warning { class Base { @Atomic Int x = 1; } class Derived extends Base { @Atomic @Override Int x = 2; } }",
                3,
            )
            val warning = session.next()
            assertThat(warning.version).isEqualTo(3)
            assertThat(warning.diagnostics).anySatisfy {
                assertThat(it.code.left).isEqualTo("VERIFY-75")
                assertThat(it.severity).isEqualTo(DiagnosticSeverity.Warning)
            }
            session.change("module Clean {}", 4)
            assertThat(session.next().diagnostics).isEmpty()
            session.closeDocument()
            assertThat(session.next().diagnostics).isEmpty()
        }
    }

    @Test
    fun `compiler mode advertises only its implemented features`() {
        Session(XdkAdapter()).use { session ->
            val capabilities =
                session.server
                    .initialize(InitializeParams())
                    .get()
                    .capabilities
            assertThat(capabilities.hoverProvider.left).isTrue()
            assertThat(capabilities.definitionProvider.left).isTrue()
            assertThat(capabilities.documentSymbolProvider.left).isTrue()
            assertThat(capabilities.foldingRangeProvider.left).isTrue()
            assertThat(capabilities.completionProvider).isNull()
            assertThat(capabilities.renameProvider).isNull()
            assertThat(capabilities.documentFormattingProvider).isNull()
            assertThat(capabilities.signatureHelpProvider).isNull()
            assertThat(capabilities.semanticTokensProvider).isNull()
        }
    }

    private companion object {
        const val URI = "file:///Protocol.x"
    }
}
