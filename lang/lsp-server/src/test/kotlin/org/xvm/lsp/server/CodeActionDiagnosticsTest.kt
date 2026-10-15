package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeActionCapabilities
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKindCapabilities
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.DiagnosticSeverity
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
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.CodeAction
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.model.Diagnostic
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import org.eclipse.lsp4j.Diagnostic as LspDiagnostic
import org.xvm.lsp.adapter.Range as AdapterRange

/** A quick fix names the diagnostics it resolves, as the very objects the client sent in the request. */
class CodeActionDiagnosticsTest {
    /** Resolves exactly the first diagnostic of the request, as a compiler quick fix would. */
    private class Backend(
        private val delegate: MockAdapter = MockAdapter(),
    ) : Adapter by delegate {
        override fun getCodeActionsAsync(
            uri: String,
            range: AdapterRange,
            diagnostics: List<Diagnostic>,
        ): CompletableFuture<List<CodeAction>> =
            CompletableFuture.completedFuture(
                listOf(
                    CodeAction("Fix it", CodeAction.CodeActionKind.QUICKFIX, diagnostics = diagnostics.take(1)),
                    CodeAction("Unrelated refactoring", CodeAction.CodeActionKind.REFACTOR),
                ),
            )
    }

    @Test
    fun `a quick fix returns the client's diagnostic it resolves`() {
        val server = XtcLanguageServer(Backend())
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
                                                        CodeActionKindCapabilities(listOf("quickfix", "refactor")),
                                                    )
                                            }
                                    }
                            }
                    },
                ).get(10, SECONDS)
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", 1, "module Links {}")))
            val fixed = LspDiagnostic(Range(Position(0, 7), Position(0, 12)), "first", DiagnosticSeverity.Error, "Ecstasy", "COMPILER-1")
            val other = LspDiagnostic(Range(Position(0, 0), Position(0, 6)), "second", DiagnosticSeverity.Warning, "Ecstasy", "COMPILER-2")
            val actions =
                documents
                    .codeAction(
                        CodeActionParams(
                            TextDocumentIdentifier(URI),
                            Range(Position(0, 0), Position(0, 12)),
                            CodeActionContext(listOf(fixed, other)),
                        ),
                    ).get(10, SECONDS)
                    .map { it.right }
            assertThat(actions.single { it.title == "Fix it" }.diagnostics).containsExactly(fixed)
            assertThat(actions.single { it.title == "Unrelated refactoring" }.diagnostics).isNull()
        } finally {
            server.shutdown().get(10, SECONDS)
        }
    }

    private companion object {
        const val URI = "file:///workspace/Links.x"
    }
}
