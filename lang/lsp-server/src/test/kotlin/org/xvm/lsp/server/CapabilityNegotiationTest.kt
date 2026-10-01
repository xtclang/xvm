package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CompletionCapabilities
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionItemKindCapabilities
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentLinkCapabilities
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SignatureHelpCapabilities
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SignatureInformationCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceEditCapabilities
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.ParameterInfo
import org.xvm.lsp.adapter.SignatureInfo
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.model.Diagnostic
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import org.xvm.lsp.adapter.CodeAction as Action
import org.xvm.lsp.adapter.DocumentLink as AdapterLink
import org.xvm.lsp.adapter.Position as AdapterPosition
import org.xvm.lsp.adapter.Range as AdapterRange
import org.xvm.lsp.adapter.SignatureHelp as AdapterSignatureHelp
import org.xvm.lsp.adapter.TextEdit as AdapterEdit
import org.xvm.lsp.adapter.WorkspaceEdit as AdapterWorkspaceEdit

class CapabilityNegotiationTest {
    @Test
    fun `link tooltips honor missing false and true support on eager and resolved results`() {
        listOf(null, false, true).forEach { support ->
            val adapter =
                object : Adapter by MockAdapter() {
                    override fun getDocumentLinks(
                        uri: String,
                        content: String,
                    ) = listOf(
                        AdapterLink(
                            AdapterRange(AdapterPosition(0, 0), AdapterPosition(0, 6)),
                            URI,
                            "Open source",
                        ),
                    )
                }
            XtcLanguageServer(adapter).use { server ->
                val params =
                    InitializeParams().apply {
                        capabilities =
                            ClientCapabilities().apply {
                                textDocument =
                                    TextDocumentClientCapabilities().apply {
                                        if (support != null) {
                                            documentLink =
                                                DocumentLinkCapabilities().apply {
                                                    tooltipSupport = support
                                                }
                                        }
                                    }
                            }
                    }
                val options =
                    server
                        .initialize(params)
                        .join()
                        .capabilities.documentLinkProvider
                assertThat(options.resolveProvider).isEqualTo(support != null)
                open(server)
                val documents = server.textDocumentService
                val link =
                    documents
                        .documentLink(DocumentLinkParams(TextDocumentIdentifier(URI)))
                        .join()
                        .single()
                if (support != null) {
                    assertThat(link.tooltip).isNull()
                    assertThat(link.target).isNull()
                    documents.documentLinkResolve(link).join()
                }
                assertThat(link.target).isEqualTo(URI)
                assertThat(link.tooltip).isEqualTo("Open source".takeIf { support == true })
            }
        }
    }

    @Test
    fun `signature metadata is negotiated while legacy active parameter follows selected overload`() {
        listOf(null, false, true).forEach { support ->
            val adapter =
                object : Adapter by MockAdapter() {
                    override fun getSignatureHelpAsync(
                        uri: String,
                        line: Int,
                        column: Int,
                    ) = CompletableFuture.completedFuture(
                        AdapterSignatureHelp(
                            listOf(
                                SignatureInfo(
                                    "read(Int first)",
                                    parameters = listOf(ParameterInfo("Int first")),
                                    activeParameter = 0,
                                ),
                                SignatureInfo(
                                    "read(Int first, Int second)",
                                    parameters =
                                        listOf(
                                            ParameterInfo("Int first"),
                                            ParameterInfo("Int second"),
                                        ),
                                    activeParameter = 1,
                                ),
                            ),
                            activeSignature = 1,
                            activeParameter = 0,
                        ),
                    )
                }
            XtcLanguageServer(adapter).use { server ->
                val params =
                    InitializeParams().apply {
                        capabilities =
                            ClientCapabilities().apply {
                                textDocument =
                                    TextDocumentClientCapabilities().apply {
                                        if (support != null) {
                                            signatureHelp =
                                                SignatureHelpCapabilities().apply {
                                                    signatureInformation =
                                                        SignatureInformationCapabilities().apply {
                                                            activeParameterSupport = support
                                                        }
                                                }
                                        }
                                    }
                            }
                    }
                server.initialize(params).join()
                open(server)
                val help =
                    server.textDocumentService
                        .signatureHelp(
                            SignatureHelpParams(TextDocumentIdentifier(URI), Position(0, 0)),
                        ).join()!!
                assertThat(help.activeSignature).isEqualTo(1)
                assertThat(help.activeParameter).isEqualTo(1)
                assertThat(help.signatures.map { it.activeParameter })
                    .containsExactlyElementsOf(
                        if (support == true) listOf(0, 1) else listOf(null, null),
                    )
                assertThat(
                    help.signatures
                        .last()
                        .parameters
                        .map { it.label.left },
                ).containsExactly("Int first", "Int second")
            }
        }
    }

    @Test
    fun `completion kinds fall back while action kinds preserve hierarchical and empty filtering`() {
        val params = editorInitializeParams()
        params.capabilities.textDocument.completion =
            CompletionCapabilities().apply {
                completionItemKind =
                    CompletionItemKindCapabilities(listOf(CompletionItemKind.Variable))
            }
        val presentation = ClientPresentation.read(params)
        assertThat(presentation.completionKind(CompletionItemKind.Method))
            .isEqualTo(CompletionItemKind.Variable)
        assertThat(ClientPresentation.matchesActionKind("source.organizeImports", listOf("source")))
            .isTrue()
        assertThat(ClientPresentation.matchesActionKind("source.organizeImports", listOf("")))
            .isTrue()
        assertThat(ClientPresentation.matchesActionKind("source.organizeImports", emptyList()))
            .isFalse()
        assertThat(
            ClientPresentation.matchesActionKind("source.organizeImports", listOf("quickfix")),
        ).isFalse()
    }

    @Test
    fun `literal actions omit unnegotiated preferred metadata and keep supported edits eager`() {
        XtcLanguageServer(adapter()).use { server ->
            val params =
                editorInitializeParams().apply {
                    capabilities.textDocument.codeAction.isPreferredSupport = false
                    capabilities.workspace.workspaceEdit =
                        WorkspaceEditCapabilities().apply { documentChanges = true }
                }
            server.initialize(params).join()
            open(server)
            val action =
                server.textDocumentService
                    .codeAction(actions())
                    .join()
                    .single()
                    .right
            assertThat<Any?>(action.isPreferred).isNull()
            assertThat(action.data).isNull()
            assertThat(
                action.edit.documentChanges
                    .single()
                    .left.textDocument.version,
            ).isEqualTo(1)
            assertThat(action.kind).isEqualTo("quickfix")
        }
    }

    @Test
    fun `legacy commands own one bounded handle and applying edits tolerates its change notification`() {
        XtcLanguageServer(adapter()).use { server ->
            val client = mock(LanguageClient::class.java)
            server.connect(client)
            val capabilities = server.initialize(legacy()).join().capabilities
            assertThat(capabilities.executeCommandProvider.commands)
                .containsExactly(ClientPresentation.APPLY_CODE_ACTION)
            open(server)
            val action =
                server.textDocumentService
                    .codeAction(actions())
                    .join()
                    .single()
                    .left
            doAnswer {
                change(server)
                CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(true))
            }.`when`(client)
                .applyEdit(any())
            val command = ExecuteCommandParams(action.command, action.arguments)
            assertThat(server.workspaceService.executeCommand(command).join())
                .isInstanceOf(ApplyWorkspaceEditResponse::class.java)
            assertThatThrownBy { server.workspaceService.executeCommand(command).join() }
                .hasMessageContaining("expired or changed")
            verify(client, times(1)).applyEdit(any())
        }
    }

    @Test
    fun `stale commands and clients without either action form cannot apply edits`() {
        XtcLanguageServer(adapter()).use { server ->
            val client = mock(LanguageClient::class.java)
            server.connect(client)
            server.initialize(legacy()).join()
            open(server)
            val action =
                server.textDocumentService
                    .codeAction(actions())
                    .join()
                    .single()
                    .left
            change(server)
            assertThatThrownBy {
                server.workspaceService
                    .executeCommand(ExecuteCommandParams(action.command, action.arguments))
                    .join()
            }.hasMessageContaining("expired or changed")
            verify(client, times(0)).applyEdit(any())
        }
        XtcLanguageServer(adapter()).use { server ->
            assertThat(
                server
                    .initialize(InitializeParams())
                    .join()
                    .capabilities
                    .codeActionProvider
                    .left,
            ).isFalse()
            open(server)
            assertThat(server.textDocumentService.codeAction(actions()).join()).isEmpty()
        }
    }

    @Test
    fun `client refusal is reported and disconnect retires an unanswered edit`() {
        listOf(false, true).forEach { stalled ->
            XtcLanguageServer(adapter()).use { server ->
                val client = mock(LanguageClient::class.java)
                val sent = CompletableFuture<Void>()
                doAnswer {
                    sent.complete(null)
                    if (stalled) {
                        CompletableFuture<ApplyWorkspaceEditResponse>()
                    } else {
                        CompletableFuture.completedFuture(
                            ApplyWorkspaceEditResponse(false).apply {
                                failureReason = "Read-only document"
                            },
                        )
                    }
                }.`when`(client)
                    .applyEdit(any())
                server.connect(client)
                server.initialize(legacy()).join()
                open(server)
                val command =
                    server.textDocumentService
                        .codeAction(actions())
                        .join()
                        .single()
                        .left
                val applying =
                    server.workspaceService.executeCommand(
                        ExecuteCommandParams(command.command, command.arguments),
                    )
                sent.get(5, SECONDS)
                if (stalled) server.close()
                assertThatThrownBy { applying.get(5, SECONDS) }
                    .hasMessageContaining(if (stalled) "changed" else "Read-only document")
            }
        }
    }

    private fun legacy() =
        InitializeParams().apply {
            capabilities =
                ClientCapabilities().apply {
                    workspace =
                        WorkspaceClientCapabilities().apply {
                            applyEdit = true
                            workspaceEdit =
                                WorkspaceEditCapabilities().apply { documentChanges = true }
                        }
                }
        }

    private fun open(server: XtcLanguageServer) =
        server.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", 1, "module Action {}")),
        )

    private fun change(server: XtcLanguageServer) =
        server.textDocumentService.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(URI, 2),
                listOf(TextDocumentContentChangeEvent("// fixed\nmodule Action {}")),
            ),
        )

    private fun actions() =
        CodeActionParams(
            TextDocumentIdentifier(URI),
            Range(Position(0, 0), Position(0, 0)),
            CodeActionContext(emptyList()).apply { only = listOf("") },
        )

    private fun adapter(): Adapter =
        object : Adapter by MockAdapter() {
            override fun getCodeActionsAsync(
                uri: String,
                range: AdapterRange,
                diagnostics: List<Diagnostic>,
            ) = CompletableFuture.completedFuture(
                listOf(
                    Action(
                        "Fix",
                        Action.CodeActionKind.QUICKFIX,
                        edit =
                            AdapterWorkspaceEdit(
                                mapOf(
                                    uri to
                                        listOf(
                                            AdapterEdit(
                                                AdapterRange(
                                                    AdapterPosition(0, 0),
                                                    AdapterPosition(0, 0),
                                                ),
                                                "// fixed\n",
                                            ),
                                        ),
                                ),
                                versioned = true,
                            ),
                        isPreferred = true,
                    ),
                ),
            )
        }

    private companion object {
        const val URI = "file:///Action.x"
    }
}
