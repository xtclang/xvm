package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.HANG_SECONDS
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * On-type formatting answers from the text that a change sent, while the change still compiles: LSP4IJ waits for the
 * answer 500 ms in the editor's write action and drops a later one, and a compile can take longer. The change's
 * compile is held with a hand-off, not timed, and the compiler's work is counted, not clocked.
 */
class OnTypeFormattingDuringCompileTest {
    /** The module compiles and the cursor analyses so far. */
    private val compiles = AtomicInteger()
    private val analyses = AtomicInteger()

    /** Whether the next module compile waits for [release], once it has signaled [entered]. */
    private val holding = AtomicBoolean()
    private val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)

    private fun <T> compile(work: () -> T): T {
        compiles.incrementAndGet()
        if (holding.compareAndSet(true, false)) {
            entered.countDown()
            check(release.await(HANG_SECONDS, SECONDS))
        }
        return work()
    }

    @Test
    fun `an Enter's indentation comes while its change compiles, and neither waits for a compile nor starts one`() {
        CompilerTestSupport.configure()
        val support = EmbeddingSupport.instance()
        val adapter =
            XdkAdapter(
                { source, repository, errors -> compile { support.compileModule(source, repository, errors) } },
                { sources, repository, errors -> compile { support.compileModule(sources, repository, errors) } },
                { source, _, cursor, repository, errors ->
                    analyses.incrementAndGet()
                    support.analyzeIncomplete(source, cursor, repository, errors)
                },
            )
        XtcLanguageServer(adapter).use { server ->
            server.connect(mock(LanguageClient::class.java))
            server.initialize(InitializeParams()).get(HANG_SECONDS, SECONDS)
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", 1, BEFORE)))
            // a query waits for the open's analysis, so the change's compile is the next one
            documents.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(URI))).get(HANG_SECONDS, SECONDS)
            holding.set(true)
            // Enter after "Int x = 1;", from an editor that leaves the new line unindented
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(URI, 2),
                    listOf(TextDocumentContentChangeEvent(BEFORE.replace("Int x = 1;\n", "Int x = 1;\n\n"))),
                ),
            )
            assertThat(entered.await(HANG_SECONDS, SECONDS)).isTrue()
            val work = compiles.get() to analyses.get()
            val edits =
                documents
                    .onTypeFormatting(
                        DocumentOnTypeFormattingParams(TextDocumentIdentifier(URI), FormattingOptions(4, true), Position(3, 0), "\n"),
                    ).get(HANG_SECONDS, SECONDS)
            // the new line's indentation, which the text before the change, with "    }" on that line, would not give
            assertThat(edits).containsExactly(TextEdit(Range(Position(3, 0), Position(3, 0)), "        "))
            assertThat(compiles.get() to analyses.get()).isEqualTo(work)
            assertThat(release.count).isEqualTo(1)
            release.countDown()
        }
    }

    private companion object {
        const val URI = "untitled:Typing.x"
        const val BEFORE = "module Typing {\n    void run() {\n        Int x = 1;\n    }\n}\n"
    }
}
