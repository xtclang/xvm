package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeActionCapabilities
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKindCapabilities
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.xvm.lsp.HANG_SECONDS
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.CodeAction
import org.xvm.lsp.adapter.CodeAction.CodeActionKind
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.model.Diagnostic
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.SECONDS
import org.xvm.lsp.adapter.Range as AdapterRange

/** A code action request's `context.only` reaches the adapter as the kinds to compute, so it computes no other. */
class CodeActionKindsTest {
    /** Records the kinds each request asks for and offers one action of each of them. */
    private class Backend(
        private val delegate: MockAdapter = MockAdapter(),
    ) : Adapter by delegate {
        val asked = CopyOnWriteArrayList<Set<CodeActionKind>>()

        override fun getCodeActionsAsync(
            uri: String,
            range: AdapterRange,
            diagnostics: List<Diagnostic>,
            kinds: Set<CodeActionKind>,
        ): CompletableFuture<List<CodeAction>> {
            asked += kinds
            return CompletableFuture.completedFuture(kinds.map { CodeAction(it.name, it) })
        }
    }

    @Test
    fun `the kinds that only names, with their subkinds, are the ones the adapter computes`() {
        val backend = Backend()
        val server = XtcLanguageServer(backend)
        try {
            server.connect(mock(LanguageClient::class.java))
            server
                .initialize(
                    InitializeParams().apply {
                        capabilities =
                            ClientCapabilities().apply {
                                textDocument =
                                    TextDocumentClientCapabilities().apply {
                                        codeAction =
                                            CodeActionCapabilities().apply {
                                                codeActionLiteralSupport =
                                                    CodeActionLiteralSupportCapabilities(
                                                        CodeActionKindCapabilities(listOf("quickfix", "refactor", "source")),
                                                    )
                                            }
                                    }
                            }
                    },
                ).get(HANG_SECONDS, SECONDS)
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", 1, "module Kinds {}")))
            mapOf(
                listOf("source.organizeImports") to setOf(CodeActionKind.SOURCE_ORGANIZE_IMPORTS),
                listOf("refactor") to
                    setOf(
                        CodeActionKind.REFACTOR,
                        CodeActionKind.REFACTOR_EXTRACT,
                        CodeActionKind.REFACTOR_INLINE,
                        CodeActionKind.REFACTOR_REWRITE,
                    ),
                listOf("quickfix", "source") to
                    setOf(CodeActionKind.QUICKFIX, CodeActionKind.SOURCE, CodeActionKind.SOURCE_ORGANIZE_IMPORTS),
                null to CodeActionKind.entries.toSet(),
            ).forEach { (only, expected) ->
                val titles =
                    documents
                        .codeAction(
                            CodeActionParams(
                                TextDocumentIdentifier(URI),
                                Range(Position(0, 0), Position(0, 0)),
                                CodeActionContext(emptyList()).apply { this.only = only },
                            ),
                        ).get(HANG_SECONDS, SECONDS)
                        .map { it.right.title }
                assertThat(backend.asked.last()).describedAs("only %s", only).isEqualTo(expected)
                assertThat(titles).describedAs("only %s", only).containsExactlyInAnyOrderElementsOf(expected.map { it.name })
            }
        } finally {
            server.shutdown().get(HANG_SECONDS, SECONDS)
        }
    }

    private companion object {
        const val URI = "file:///workspace/Kinds.x"
    }
}
