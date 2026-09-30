package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolCapabilities
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.HoverCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.PublishDiagnosticsCapabilities
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.SymbolKindCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.mock.MockAdapter

class ClientPresentationTest {
    @Test
    fun `server advertises UTF16 and serves flat symbols without hierarchical support`() {
        XtcLanguageServer(MockAdapter()).use { server ->
            assertThat(server.initialize(InitializeParams()).join().capabilities.positionEncoding)
                .isEqualTo("utf-16")
            val uri = "file:///Presentation.x"
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(
                    TextDocumentItem(uri, "xtc", 1, "module Presentation { class Child {} }")
                )
            )
            val symbols =
                server.textDocumentService
                    .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                    .join()
            assertThat(symbols).isNotEmpty().allSatisfy { assertThat(it.isLeft).isTrue() }
            assertThat(symbols.map { it.left.name }).contains("Presentation", "Child")
        }
    }

    @Test
    fun `minimal clients get plain hover legacy symbol kinds and no optional diagnostics`() {
        val policy = ClientPresentation.read(InitializeParams())
        assertThat(policy.hover("### Value\n```xtc\nInt value\n```\nUse `value`.")).satisfies {
            assertThat(it.kind).isEqualTo(MarkupKind.PLAINTEXT)
            assertThat(it.value).isEqualTo("Value\nInt value\nUse value.")
        }
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
                textDocument =
                    TextDocumentClientCapabilities().apply {
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
