package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeActionCapabilities
import org.eclipse.lsp4j.CodeActionKindCapabilities
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolCapabilities
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.HoverCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InlineCompletionCapabilities
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.PublishDiagnosticsCapabilities
import org.eclipse.lsp4j.SignatureHelpCapabilities
import org.eclipse.lsp4j.SignatureInformationCapabilities
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.SymbolKindCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo

class ClientPresentationTest {
    @Test
    fun `minimal client receives no unnegotiated configuration or refresh requests`() {
        XtcLanguageServer(MockAdapter()).use { server ->
            val client = mock(LanguageClient::class.java)
            server.connect(client)
            server.initialize(InitializeParams()).join()
            server.initialized(InitializedParams())
            server.refreshPresentation()
            verifyNoInteractions(client)
        }
    }

    @Test
    fun `server advertises UTF16 and serves flat symbols without hierarchical support`() {
        val uri = "file:///Presentation.x"
        val child = SymbolInfo.of("Child", SymbolInfo.SymbolKind.CLASS, Location(uri, 1, 4, 1, 18))
        val module =
            SymbolInfo
                .of("Presentation", SymbolInfo.SymbolKind.MODULE, Location(uri, 0, 0, 2, 1))
                .withChildren(listOf(child))
        val adapter =
            object : Adapter by MockAdapter() {
                override fun getCachedResult(uri: String) = CompilationResult.success(uri, listOf(module))
            }
        XtcLanguageServer(adapter).use { server ->
            assertThat(
                server
                    .initialize(InitializeParams())
                    .join()
                    .capabilities.positionEncoding,
            ).isEqualTo("utf-16")
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(
                    TextDocumentItem(uri, "xtc", 1, "module Presentation {\n    class Child {}\n}"),
                ),
            )
            val symbols =
                server.textDocumentService
                    .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                    .join()
            assertThat(symbols).isNotEmpty().allSatisfy { assertThat(it.isLeft).isTrue() }
            assertThat(symbols.map { it.left.name }).contains("Presentation", "Child")
            assertThat(symbols.single { it.left.name == "Child" }.left.containerName)
                .isEqualTo("Presentation")
        }
    }

    @Test
    fun `minimal clients get plain hover legacy symbol kinds and no optional diagnostics`() {
        val policy = ClientPresentation.read(InitializeParams())
        val hover = policy.hover("### Value\n```xtc\nInt value\n```\nUse `value`.")
        assertThat(hover.kind).isEqualTo(MarkupKind.PLAINTEXT)
        assertThat(hover.value).isEqualTo("Value\nInt value\nUse value.")
        assertThat(policy.hierarchicalSymbols).isFalse()
        assertThat(policy.symbolKind(SymbolKind.TypeParameter)).isEqualTo(SymbolKind.Variable)
        assertThat(policy.diagnosticVersions).isFalse()
        assertThat(policy.diagnosticRelatedInformation).isFalse()
    }

    @Test
    fun `client preference order and advertised kind set govern presentation`() {
        val params =
            editorInitializeParams().apply {
                capabilities.textDocument.hover =
                    HoverCapabilities().apply {
                        contentFormat = listOf(MarkupKind.PLAINTEXT, MarkupKind.MARKDOWN)
                    }
                capabilities.textDocument.documentSymbol.symbolKind =
                    SymbolKindCapabilities(listOf(SymbolKind.Class))
            }
        val policy = ClientPresentation.read(params)
        assertThat(policy.markdownHover).isFalse()
        assertThat(policy.hierarchicalSymbols).isTrue()
        assertThat(policy.symbolKind(SymbolKind.TypeParameter)).isEqualTo(SymbolKind.Class)
        assertThat(policy.diagnosticVersions).isTrue()
        params.capabilities.textDocument.hover.contentFormat = listOf(MarkupKind.MARKDOWN)
        assertThat(ClientPresentation.read(params).hover("`Int`").value).isEqualTo("`Int`")
    }

    @Test
    fun `workspace folder list overrides legacy roots including explicitly empty folders`() {
        // Older LSP clients may still send these protocol-deprecated fields.
        val params = InitializeParams().apply { rootPath = "/tmp/old" }
        assertThat(ClientPresentation.workspaceUris(params)).containsExactly("file:///tmp/old")
        params.rootUri = "file:///tmp/new"
        assertThat(ClientPresentation.workspaceUris(params)).containsExactly("file:///tmp/new")
        params.workspaceFolders = listOf(WorkspaceFolder("file:///tmp/folder", "folder"))
        assertThat(ClientPresentation.workspaceUris(params)).containsExactly("file:///tmp/folder")
        params.workspaceFolders = emptyList()
        assertThat(ClientPresentation.workspaceUris(params)).isEmpty()
    }
}

/** Real editor capabilities for tests that assert rich outlines and versioned publications. */
internal fun editorInitializeParams() =
    InitializeParams().apply {
        capabilities =
            ClientCapabilities().apply {
                workspace = WorkspaceClientCapabilities().apply { configuration = true }
                textDocument =
                    TextDocumentClientCapabilities().apply {
                        inlineCompletion = InlineCompletionCapabilities()
                        signatureHelp =
                            SignatureHelpCapabilities().apply {
                                signatureInformation =
                                    SignatureInformationCapabilities().apply {
                                        activeParameterSupport = true
                                    }
                            }
                        codeAction =
                            CodeActionCapabilities().apply {
                                codeActionLiteralSupport =
                                    CodeActionLiteralSupportCapabilities(
                                        CodeActionKindCapabilities(listOf("")),
                                    )
                                isPreferredSupport = true
                            }
                        documentSymbol =
                            DocumentSymbolCapabilities().apply {
                                hierarchicalDocumentSymbolSupport = true
                            }
                        publishDiagnostics =
                            PublishDiagnosticsCapabilities().apply {
                                versionSupport = true
                                relatedInformation = true
                            }
                    }
            }
    }
