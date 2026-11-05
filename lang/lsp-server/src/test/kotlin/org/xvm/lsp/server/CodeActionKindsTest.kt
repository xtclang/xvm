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
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.CodeAction
import org.xvm.lsp.adapter.CodeAction.CodeActionKind
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.awaited
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.xvm.lsp.adapter.Range as AdapterRange

/** A code action request's `context.only` reaches the adapter as the kinds to compute, so it computes no other. */
class CodeActionKindsTest {
    /**
     * Records the kinds each request asks for, and the budget of one that waits for its answer only so long, null for
     * one that waits, and offers one action of each of them. A document's analysis completes once [analyzed] does.
     */
    private class Backend(
        val analyzed: CompletableFuture<Unit> = CompletableFuture.completedFuture(Unit),
        private val delegate: MockAdapter = MockAdapter(),
    ) : Adapter by delegate {
        val asked = CopyOnWriteArrayList<Set<CodeActionKind>>()
        val budgets = CopyOnWriteArrayList<Duration?>()

        override fun compileAsync(
            uri: String,
            content: String,
        ): CompletableFuture<CompilationResult> = analyzed.thenApply { CompilationResult.success(uri, emptyList()) }

        override fun getCodeActionsAsync(
            uri: String,
            range: AdapterRange,
            diagnostics: List<Diagnostic>,
            kinds: Set<CodeActionKind>,
        ): CompletableFuture<List<CodeAction>> {
            asked += kinds
            budgets += null
            return CompletableFuture.completedFuture(kinds.map { CodeAction(it.name, it) })
        }

        override fun getCodeActionsWithinAsync(
            uri: String,
            range: AdapterRange,
            diagnostics: List<Diagnostic>,
            kinds: Set<CodeActionKind>,
            budget: Duration,
        ): CompletableFuture<List<CodeAction>> {
            asked += kinds
            budgets += budget
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

    // TODO LSP4IJ: UP74 — the IntelliJ plugin says that LSP4IJ waits on the EDT for the answer to an Automatic request,
    // so such a request waits for its answer only a budget, the server's or the option's, and not for the document's
    // analysis; any other request waits
    @Test
    fun `an automatic request of a client that waits for it waits only its budget, and not for an analysis`() {
        mapOf(
            Request(FROM_CACHE, CodeActionTriggerKind.Automatic, null) to ClientPresentation.AUTOMATIC_WAIT,
            Request(FROM_CACHE, CodeActionTriggerKind.Automatic, listOf("quickfix")) to ClientPresentation.AUTOMATIC_WAIT,
            Request(within(0), CodeActionTriggerKind.Automatic, null) to Duration.ZERO,
            Request(within(1234), CodeActionTriggerKind.Automatic, null) to 1234.milliseconds,
            Request(FROM_CACHE, CodeActionTriggerKind.Invoked, null) to null,
            Request(FROM_CACHE, null, null) to null,
            Request(PER_DIAGNOSTIC, CodeActionTriggerKind.Automatic, null) to null,
            Request(null, CodeActionTriggerKind.Automatic, null) to null,
        ).forEach { (request, budget) ->
            // the document's analysis is held, so a request that waits for it cannot answer before it is released
            val analysis = CompletableFuture<Unit>()
            session(request.options, Backend(analysis)) { backend, documents ->
                val answer = request(documents, request.trigger, request.only)
                if (budget != null) {
                    assertThat(answer.awaited()).describedAs("%s", request).isNotEmpty()
                } else {
                    assertThat(answer).describedAs("%s", request).isNotDone()
                    analysis.complete(Unit)
                    assertThat(answer.awaited()).describedAs("%s", request).isNotEmpty()
                }
                assertThat(backend.budgets.single()).describedAs("%s", request).isEqualTo(budget)
            }
        }
    }

    private fun titles(
        documents: TextDocumentService,
        trigger: CodeActionTriggerKind?,
        only: List<String>?,
    ): List<String> = request(documents, trigger, only).awaited().map { it.right.title }

    private fun request(
        documents: TextDocumentService,
        trigger: CodeActionTriggerKind?,
        only: List<String>?,
    ) = documents.codeAction(
        CodeActionParams(
            TextDocumentIdentifier(URI),
            Range(Position(0, 0), Position(0, 0)),
            CodeActionContext(emptyList()).apply {
                this.only = only
                triggerKind = trigger
            },
        ),
    )

    private fun session(
        options: Any?,
        backend: Backend = Backend(),
        body: (Backend, TextDocumentService) -> Unit,
    ) {
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
                ).awaited()
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", 1, "module Kinds {}")))
            body(backend, documents)
        } finally {
            server.shutdown().awaited()
        }
    }

    private companion object {
        const val URI = "file:///workspace/Kinds.x"

        /** What the IntelliJ plugin's initialization options say of LSP4IJ's code action requests, one at a time. */
        val PER_DIAGNOSTIC = mapOf("xtcCodeActions" to mapOf("quickFixesPerDiagnostic" to true))
        val FROM_CACHE = mapOf("xtcCodeActions" to mapOf("automaticFromCache" to true))

        /** The option, with a budget of [millis] in place of the server's. */
        fun within(millis: Int) = mapOf("xtcCodeActions" to mapOf("automaticFromCache" to true, "automaticWaitMillis" to millis))
    }
}
