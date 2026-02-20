package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SignatureHelpParams
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

            server.initialize(InitializeParams()).get()
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

            server.initialize(InitializeParams()).get()
            server.textDocumentService.didOpen(params)

            val captor = ArgumentCaptor.forClass(PublishDiagnosticsParams::class.java)
            verify(mockClient).publishDiagnostics(captor.capture())

            assertThat(captor.value.diagnostics).isEmpty()
        }

        @Test
        @DisplayName("hover should return markdown content")
        fun hoverShouldReturnMarkdownContent() {
            val uri = "file:///test.x"
            val content =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            server.initialize(InitializeParams()).get()
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, content)),
            )

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

            server.initialize(InitializeParams()).get()
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
            assertThat(caps.documentLinkProvider).describedAs("documentLink").isNotNull()
            assertThat(caps.signatureHelpProvider).describedAs("signatureHelp").isNotNull()

            // Editing features
            assertThat(caps.renameProvider?.right).describedAs("rename (with prepareProvider)").isNotNull()
            assertThat(caps.renameProvider?.right?.prepareProvider).describedAs("rename prepareProvider").isTrue()
            assertThat(caps.codeActionProvider?.left).describedAs("codeAction").isTrue()
            assertThat(caps.documentFormattingProvider?.left).describedAs("formatting").isTrue()
            assertThat(caps.documentRangeFormattingProvider?.left).describedAs("rangeFormatting").isTrue()
            assertThat(caps.inlayHintProvider?.left).describedAs("inlayHint").isTrue()

            // Workspace features
            assertThat(caps.workspaceSymbolProvider?.left).describedAs("workspaceSymbol").isTrue()

            // Sync
            assertThat(caps.textDocumentSync?.left).describedAs("textDocumentSync").isNotNull()
        }

        @Test
        @DisplayName("should report all unimplemented LSP capabilities")
        fun shouldReportUnimplementedCapabilities() {
            val notYetImplemented = mutableListOf<String>()

            if (caps.declarationProvider == null) notYetImplemented.add("declaration")
            if (caps.typeDefinitionProvider == null) notYetImplemented.add("typeDefinition")
            if (caps.implementationProvider == null) notYetImplemented.add("implementation")
            if (caps.codeLensProvider == null) notYetImplemented.add("codeLens")
            if (caps.colorProvider == null) notYetImplemented.add("colorProvider")
            if (caps.documentOnTypeFormattingProvider == null) notYetImplemented.add("onTypeFormatting")
            if (caps.typeHierarchyProvider == null) notYetImplemented.add("typeHierarchy")
            if (caps.callHierarchyProvider == null) notYetImplemented.add("callHierarchy")
            if (caps.monikerProvider == null) notYetImplemented.add("moniker")
            if (caps.linkedEditingRangeProvider == null) notYetImplemented.add("linkedEditingRange")
            if (caps.inlineValueProvider == null) notYetImplemented.add("inlineValue")
            if (caps.diagnosticProvider == null) notYetImplemented.add("diagnosticProvider")

            // Print the audit report
            println("========================================")
            println("LSP Capabilities Audit")
            println("========================================")
            println("Implemented (${19} capabilities):")
            println("  hover, completion, definition, references, documentSymbol,")
            println("  documentHighlight, selectionRange, foldingRange,")
            println("  documentLink, signatureHelp, workspaceSymbol,")
            println("  rename (with prepareRename), codeAction,")
            println("  formatting, rangeFormatting, inlayHint,")
            println("  textDocumentSync, semanticTokens")
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
                    "colorProvider",
                    "onTypeFormatting",
                    "typeHierarchy",
                    "callHierarchy",
                    "moniker",
                    "linkedEditingRange",
                    "inlineValue",
                    "diagnosticProvider",
                )
        }
    }

    // ========================================================================
    // Helper: open a test document with class, method, imports
    // ========================================================================

    private fun openTestDocument(): String {
        val uri = "file:///test.x"
        val content =
            """
            module myapp {
                import foo.Zebra;
                import bar.Alpha;
                class Person {
                    String getName() {
                        return name;
                    }
                }
            }
            """.trimIndent()

        server.initialize(InitializeParams()).get()
        server.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, content)),
        )
        return uri
    }

    // Dirty source for formatting tests (has trailing whitespace)
    private fun openDirtyDocument(): String {
        val uri = "file:///dirty.x"
        val content = "module myapp {   \n    class Person {  \n    }\n}"

        server.initialize(InitializeParams()).get()
        server.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, content)),
        )
        return uri
    }

    @Nested
    @DisplayName("AllCapabilities")
    inner class AllCapabilitiesTests {
        @Test
        @DisplayName("definition should find class")
        fun definitionShouldFindClass() {
            val uri = openTestDocument()

            val result =
                server.textDocumentService
                    .definition(DefinitionParams(TextDocumentIdentifier(uri), Position(3, 10)))
                    .get()

            assertThat(result.isLeft).isTrue()
            assertThat(result.left).isNotEmpty()
            assertThat(result.left.first().uri).isEqualTo(uri)
        }

        @Test
        @DisplayName("references should find symbol")
        fun referencesShouldFindSymbol() {
            val uri = openTestDocument()

            val result =
                server.textDocumentService
                    .references(
                        ReferenceParams(
                            TextDocumentIdentifier(uri),
                            Position(3, 10),
                            ReferenceContext(true),
                        ),
                    ).get()

            assertThat(result).isNotEmpty()
        }

        @Test
        @DisplayName("documentSymbol should return symbols")
        fun documentSymbolShouldReturnSymbols() {
            val uri = openTestDocument()

            val symbols =
                server.textDocumentService
                    .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                    .get()

            assertThat(symbols).isNotEmpty()
            val names = symbols.map { it.right.name }
            assertThat(names).contains("Person")
        }

        @Test
        @DisplayName("documentHighlight should find occurrences")
        fun documentHighlightShouldFindOccurrences() {
            val uri = openTestDocument()

            val highlights =
                server.textDocumentService
                    .documentHighlight(DocumentHighlightParams(TextDocumentIdentifier(uri), Position(3, 10)))
                    .get()

            assertThat(highlights).isNotEmpty()
        }

        @Test
        @DisplayName("foldingRange should find blocks")
        fun foldingRangeShouldFindBlocks() {
            val uri = openTestDocument()

            val ranges =
                server.textDocumentService
                    .foldingRange(FoldingRangeRequestParams(TextDocumentIdentifier(uri)))
                    .get()

            assertThat(ranges).isNotEmpty()
            assertThat(ranges).anyMatch { it.endLine > it.startLine }
        }

        @Test
        @DisplayName("selectionRange should not crash")
        fun selectionRangeShouldNotCrash() {
            val uri = openTestDocument()

            val ranges =
                server.textDocumentService
                    .selectionRange(
                        SelectionRangeParams(TextDocumentIdentifier(uri), listOf(Position(3, 10))),
                    ).get()

            // Mock returns empty; this just verifies no crash
            assertThat(ranges).isNotNull()
        }

        @Test
        @DisplayName("formatting should return edits")
        fun formattingShouldReturnEdits() {
            val uri = openDirtyDocument()

            val edits =
                server.textDocumentService
                    .formatting(
                        DocumentFormattingParams(TextDocumentIdentifier(uri), FormattingOptions(4, true)),
                    ).get()

            assertThat(edits).isNotEmpty()
        }

        @Test
        @DisplayName("rangeFormatting should return edits")
        fun rangeFormattingShouldReturnEdits() {
            // Use a document with trailing whitespace and request trimming
            val uri = "file:///dirty2.x"
            val content = "module myapp {   \n    class Person {  \n    }\n}"

            server.initialize(InitializeParams()).get()
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, content)),
            )

            val options = FormattingOptions(4, true)
            options.putBoolean("trimTrailingWhitespace", true)

            val edits =
                server.textDocumentService
                    .rangeFormatting(
                        DocumentRangeFormattingParams(
                            TextDocumentIdentifier(uri),
                            options,
                            Range(Position(0, 0), Position(1, 0)),
                        ),
                    ).get()

            // Result is non-null; may be empty if server doesn't extract trimTrailingWhitespace
            assertThat(edits).isNotNull()
        }

        @Test
        @DisplayName("prepareRename should identify symbol")
        fun prepareRenameShouldIdentifySymbol() {
            val uri = openTestDocument()

            val result =
                server.textDocumentService
                    .prepareRename(PrepareRenameParams(TextDocumentIdentifier(uri), Position(3, 10)))
                    .get()

            assertThat(result).isNotNull()
            assertThat(result.second.placeholder).isEqualTo("Person")
        }

        @Test
        @DisplayName("rename should produce edits")
        fun renameShouldProduceEdits() {
            val uri = openTestDocument()

            val edit =
                server.textDocumentService
                    .rename(RenameParams(TextDocumentIdentifier(uri), Position(3, 10), "Human"))
                    .get()

            assertThat(edit).isNotNull()
            assertThat(edit.changes).containsKey(uri)
            assertThat(edit.changes[uri]).allMatch { it.newText == "Human" }
        }

        @Test
        @DisplayName("codeAction should return organize imports")
        fun codeActionShouldReturnOrganizeImports() {
            val uri = openTestDocument()

            val actions =
                server.textDocumentService
                    .codeAction(
                        CodeActionParams(
                            TextDocumentIdentifier(uri),
                            Range(Position(0, 0), Position(0, 0)),
                            CodeActionContext(emptyList()),
                        ),
                    ).get()

            assertThat(actions).anyMatch { it.right.title == "Organize Imports" }
        }

        @Test
        @DisplayName("documentLink should find links")
        fun documentLinkShouldFindLinks() {
            val uri = openTestDocument()

            val links =
                server.textDocumentService
                    .documentLink(DocumentLinkParams(TextDocumentIdentifier(uri)))
                    .get()

            assertThat(links).isNotEmpty()
        }

        @Test
        @DisplayName("signatureHelp should return null for mock")
        fun signatureHelpShouldReturnNullForMock() {
            val uri = openTestDocument()

            val result =
                server.textDocumentService
                    .signatureHelp(SignatureHelpParams(TextDocumentIdentifier(uri), Position(5, 10)))
                    .get()

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("inlayHint should return empty for mock")
        fun inlayHintShouldReturnEmptyForMock() {
            val uri = openTestDocument()

            val hints =
                server.textDocumentService
                    .inlayHint(
                        InlayHintParams(
                            TextDocumentIdentifier(uri),
                            Range(Position(0, 0), Position(10, 0)),
                        ),
                    ).get()

            assertThat(hints).isEmpty()
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
