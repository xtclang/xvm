package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.xvm.lsp.adapter.MockXtcCompilerAdapter

@DisplayName("XtcLanguageServer")
class XtcLanguageServerTest {
    private lateinit var server: XtcLanguageServer
    private lateinit var mockClient: LanguageClient

    @BeforeEach
    fun setUp() {
        server = XtcLanguageServer(MockXtcCompilerAdapter())
        mockClient = mock(LanguageClient::class.java)
        server.connect(mockClient)
    }

    @Nested
    @DisplayName("initialize()")
    inner class InitializeTests {
        @Test
        @DisplayName("should return server capabilities")
        fun shouldReturnServerCapabilities() {
            val params = InitializeParams()

            val future = server.initialize(params)
            val result = future.get()

            assertThat(result.capabilities).isNotNull()
            assertThat(result.capabilities.hoverProvider.left).isTrue()
            assertThat(result.capabilities.completionProvider).isNotNull()
            assertThat(result.capabilities.definitionProvider.left).isTrue()
            assertThat(result.capabilities.referencesProvider.left).isTrue()
            assertThat(result.capabilities.documentSymbolProvider.left).isTrue()
        }
    }

    @Nested
    @DisplayName("TextDocumentService")
    inner class TextDocumentServiceTests {
        @Test
        @DisplayName("didOpen should compile and publish diagnostics")
        fun didOpenShouldCompileAndPublishDiagnostics() {
            val uri = "file:///test.x"
            val content =
                """
                module myapp {
                    // ERROR: test error
                }
                """.trimIndent()

            val params = DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, content))

            server.textDocumentService.didOpen(params)

            val captor = ArgumentCaptor.forClass(PublishDiagnosticsParams::class.java)
            verify(mockClient).publishDiagnostics(captor.capture())

            val published = captor.value
            assertThat(published.uri).isEqualTo(uri)
            assertThat(published.diagnostics)
                .hasSize(1)
                .allMatch { it.message.contains("test error") }
        }

        @Test
        @DisplayName("didOpen with valid code should have no diagnostics")
        fun didOpenWithValidCodeShouldHaveNoDiagnostics() {
            val uri = "file:///test.x"
            val content =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            val params = DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, content))

            server.textDocumentService.didOpen(params)

            val captor = ArgumentCaptor.forClass(PublishDiagnosticsParams::class.java)
            verify(mockClient).publishDiagnostics(captor.capture())

            assertThat(captor.value.diagnostics).isEmpty()
        }

        @Test
        @DisplayName("hover should return markdown content")
        fun hoverShouldReturnMarkdownContent() {
            // First, open the document
            val uri = "file:///test.x"
            val content =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, content)),
            )

            // Now request hover
            val params = HoverParams(TextDocumentIdentifier(uri), Position(1, 10))

            val future = server.textDocumentService.hover(params)
            val hover = future.get()

            assertThat(hover).isNotNull()
            assertThat(hover.contents.right.value).contains("class Person")
        }

        @Test
        @DisplayName("completion should return items")
        fun completionShouldReturnItems() {
            val uri = "file:///test.x"
            val content =
                """
                module myapp {
                }
                """.trimIndent()

            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, content)),
            )

            val params = CompletionParams(TextDocumentIdentifier(uri), Position(1, 0))

            val future = server.textDocumentService.completion(params)
            val result = future.get()

            // Result is Either<List, CompletionList>
            assertThat(result.isLeft).isTrue()
            assertThat(result.left)
                .isNotEmpty()
                .anyMatch { it.label == "class" }
                .anyMatch { it.label == "String" }
        }
    }

    @Nested
    @DisplayName("capabilities audit")
    inner class CapabilitiesAuditTests {
        private lateinit var caps: ServerCapabilities

        @BeforeEach
        fun initServer() {
            val result = server.initialize(InitializeParams()).get()
            caps = result.capabilities
        }

        @Test
        @DisplayName("should advertise all implemented capabilities")
        fun shouldAdvertiseImplementedCapabilities() {
            // Core navigation
            assertThat(caps.hoverProvider?.left).describedAs("hover").isTrue()
            assertThat(caps.completionProvider).describedAs("completion").isNotNull()
            assertThat(caps.definitionProvider?.left).describedAs("definition").isTrue()
            assertThat(caps.referencesProvider?.left).describedAs("references").isTrue()
            assertThat(caps.documentSymbolProvider?.left).describedAs("documentSymbol").isTrue()

            // Tree-sitter features
            assertThat(caps.documentHighlightProvider?.left).describedAs("documentHighlight").isTrue()
            assertThat(caps.selectionRangeProvider?.left).describedAs("selectionRange").isTrue()
            assertThat(caps.foldingRangeProvider?.left).describedAs("foldingRange").isTrue()

            // Editing features
            assertThat(caps.renameProvider?.left).describedAs("rename").isTrue()
            assertThat(caps.codeActionProvider?.left).describedAs("codeAction").isTrue()
            assertThat(caps.documentFormattingProvider?.left).describedAs("formatting").isTrue()
            assertThat(caps.documentRangeFormattingProvider?.left).describedAs("rangeFormatting").isTrue()
            assertThat(caps.inlayHintProvider?.left).describedAs("inlayHint").isTrue()

            // Sync
            assertThat(caps.textDocumentSync?.left).describedAs("textDocumentSync").isNotNull()
        }

        @Test
        @DisplayName("should report all unimplemented LSP capabilities")
        fun shouldReportUnimplementedCapabilities() {
            // Full LSP spec capabilities and their current status in this server.
            // When a capability is implemented, move it from "not yet" to "implemented" above.
            val notYetImplemented = mutableListOf<String>()

            if (caps.declarationProvider == null) notYetImplemented.add("declaration")
            if (caps.typeDefinitionProvider == null) notYetImplemented.add("typeDefinition")
            if (caps.implementationProvider == null) notYetImplemented.add("implementation")
            if (caps.codeLensProvider == null) notYetImplemented.add("codeLens")
            if (caps.documentLinkProvider == null) notYetImplemented.add("documentLink")
            if (caps.colorProvider == null) notYetImplemented.add("colorProvider")
            if (caps.signatureHelpProvider == null) notYetImplemented.add("signatureHelp")
            if (caps.documentOnTypeFormattingProvider == null) notYetImplemented.add("onTypeFormatting")
            if (caps.typeHierarchyProvider == null) notYetImplemented.add("typeHierarchy")
            if (caps.callHierarchyProvider == null) notYetImplemented.add("callHierarchy")
            if (caps.semanticTokensProvider == null) notYetImplemented.add("semanticTokens")
            if (caps.monikerProvider == null) notYetImplemented.add("moniker")
            if (caps.linkedEditingRangeProvider == null) notYetImplemented.add("linkedEditingRange")
            if (caps.inlineValueProvider == null) notYetImplemented.add("inlineValue")
            if (caps.diagnosticProvider == null) notYetImplemented.add("diagnosticProvider")
            if (caps.workspaceSymbolProvider == null) notYetImplemented.add("workspaceSymbol")

            // Print the audit report
            println("========================================")
            println("LSP Capabilities Audit")
            println("========================================")
            println("Implemented (${14 - 0} capabilities):")
            println("  hover, completion, definition, references, documentSymbol,")
            println("  documentHighlight, selectionRange, foldingRange,")
            println("  rename, codeAction, formatting, rangeFormatting, inlayHint,")
            println("  textDocumentSync")
            println()
            println("Not yet implemented (${notYetImplemented.size} capabilities):")
            for (cap in notYetImplemented) {
                println("  - $cap")
            }
            println("========================================")

            // This assertion documents the gap - update as capabilities are added
            assertThat(notYetImplemented)
                .describedAs("Unimplemented LSP capabilities")
                .containsExactlyInAnyOrder(
                    "declaration",
                    "typeDefinition",
                    "implementation",
                    "codeLens",
                    "documentLink",
                    "colorProvider",
                    "signatureHelp",
                    "onTypeFormatting",
                    "typeHierarchy",
                    "callHierarchy",
                    "semanticTokens",
                    "moniker",
                    "linkedEditingRange",
                    "inlineValue",
                    "diagnosticProvider",
                    "workspaceSymbol",
                )
        }
    }

    @Nested
    @DisplayName("shutdown()")
    inner class ShutdownTests {
        @Test
        @DisplayName("should complete without error")
        fun shouldCompleteWithoutError() {
            server.initialize(InitializeParams()).get()

            val future = server.shutdown()

            assertThat(future.get()).isNull()
        }
    }
}
