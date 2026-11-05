package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeActionCapabilities
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKindCapabilities
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
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

    /** One code action request: the client's initialization options, then the request's trigger and only. */
    private data class Request(
        val options: Any?,
        val trigger: CodeActionTriggerKind?,
        val only: List<String>?,
    )

    @Test
    fun `the kinds that only names, with their subkinds, are the ones the adapter computes`() {
        session(null) { backend, documents ->
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
                val titles = titles(documents, null, only)
                assertThat(backend.asked.last()).describedAs("only %s", only).isEqualTo(expected)
                assertThat(titles).describedAs("only %s", only).containsExactlyInAnyOrderElementsOf(expected.map { it.name })
            }
        }
    }

    // TODO LSP4IJ: UP68 — the IntelliJ plugin says that LSP4IJ asks for quick fixes per diagnostic, and its intention
    // list, an Automatic request with no only, hides them; any other request still gets them
    @Test
    fun `a client that asks for quick fixes per diagnostic gets none in an automatic request without only`() {
        val every = CodeActionKind.entries.toSet()
        mapOf(
            Request(PER_DIAGNOSTIC, CodeActionTriggerKind.Automatic, null) to every - CodeActionKind.QUICKFIX,
            Request(PER_DIAGNOSTIC, CodeActionTriggerKind.Automatic, listOf("quickfix")) to setOf(CodeActionKind.QUICKFIX),
            Request(PER_DIAGNOSTIC, CodeActionTriggerKind.Automatic, listOf("")) to every,
            Request(PER_DIAGNOSTIC, CodeActionTriggerKind.Invoked, null) to every,
            Request(PER_DIAGNOSTIC, null, null) to every,
            Request(null, CodeActionTriggerKind.Automatic, null) to every,
            Request(mapOf("xtcCodeActions" to mapOf("quickFixesPerDiagnostic" to false)), CodeActionTriggerKind.Automatic, null) to every,
        ).forEach { (request, expected) ->
            session(request.options) { backend, documents ->
                val titles = titles(documents, request.trigger, request.only)
                assertThat(backend.asked.single()).describedAs("%s", request).isEqualTo(expected)
                assertThat(titles).describedAs("%s", request).containsExactlyInAnyOrderElementsOf(expected.map { it.name })
            }
        }
    }

    private fun titles(
        documents: TextDocumentService,
        trigger: CodeActionTriggerKind?,
        only: List<String>?,
    ): List<String> =
        documents
            .codeAction(
                CodeActionParams(
                    TextDocumentIdentifier(URI),
                    Range(Position(0, 0), Position(0, 0)),
                    CodeActionContext(emptyList()).apply {
                        this.only = only
                        triggerKind = trigger
                    },
                ),
            ).get(HANG_SECONDS, SECONDS)
            .map { it.right.title }

    private fun session(
        options: Any?,
        body: (Backend, TextDocumentService) -> Unit,
    ) {
        val backend = Backend()
        val server = XtcLanguageServer(backend)
        try {
            server.connect(mock(LanguageClient::class.java))
            server
                .initialize(
                    InitializeParams().apply {
                        initializationOptions = options
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
            body(backend, documents)
        } finally {
            server.shutdown().get(HANG_SECONDS, SECONDS)
        }
    }

    private companion object {
        const val URI = "file:///workspace/Kinds.x"

        /** What the IntelliJ plugin's initialization options say of LSP4IJ's code action requests. */
        val PER_DIAGNOSTIC = mapOf("xtcCodeActions" to mapOf("quickFixesPerDiagnostic" to true))
    }
}
