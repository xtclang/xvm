package org.xvm.lsp.server

import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeLensCapabilities
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.CodeLensResolveSupportCapabilities
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentLinkCapabilities
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InlayHintCapabilities
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.InlayHintResolveSupportCapabilities
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.WorkspaceSymbolResolveSupportCapabilities
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkFeatureResolveProtocolTest {
    @TempDir lateinit var root: Path
    private val source =
        """
        module Resolve {
            /** Read a number. */
            Int read(Int value) = value;
            void run() { var result = read(1); }
            // https://xtclang.org/
        }
        """
            .trimIndent()

    @Test
    fun `lens link hint and symbol resolve preserve identity and expire together`() {
        session(true) { server, uri ->
            val documents = server.textDocumentService
            val id = TextDocumentIdentifier(uri)
            val lens = documents.codeLens(CodeLensParams(id)).get(30, SECONDS).single()
            val link = documents.documentLink(DocumentLinkParams(id)).get(30, SECONDS).single()
            val hint =
                documents
                    .inlayHint(InlayHintParams(id, Range(Position(0, 0), Position(6, 0))))
                    .get(30, SECONDS)
                    .first { it.data != null }
            val symbol =
                server.workspaceService
                    .symbol(WorkspaceSymbolParams("Resolve"))
                    .get(30, SECONDS)
                    .right
                    .first()
            assertThat(lens.command).isNull()
            assertThat(link.target).isNull()
            assertThat(hint.tooltip).isNull()
            assertThat(symbol.location.isRight).isTrue()
            val range = lens.range
            val label = hint.label
            assertThat(documents.resolveCodeLens(lens).get(10, SECONDS).command.arguments)
                .containsExactly(uri, "Resolve")
            assertThat(lens.range).isEqualTo(range)
            assertThat(documents.documentLinkResolve(link).get(10, SECONDS).target)
                .isEqualTo("https://xtclang.org/")
            assertThat(documents.resolveInlayHint(hint).get(10, SECONDS).tooltip.right.value)
                .isNotBlank()
            assertThat(hint.label).isEqualTo(label)
            assertThat(
                    server.workspaceService
                        .resolveWorkspaceSymbol(symbol)
                        .get(10, SECONDS)
                        .location
                        .isLeft
                )
                .isTrue()
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri, 2),
                    listOf(TextDocumentContentChangeEvent("\n$source")),
                )
            )
            listOf<() -> Any>(
                    { documents.resolveCodeLens(lens).get(10, SECONDS) },
                    { documents.documentLinkResolve(link).get(10, SECONDS) },
                    { documents.resolveInlayHint(hint).get(10, SECONDS) },
                    { server.workspaceService.resolveWorkspaceSymbol(symbol).get(10, SECONDS) },
                )
                .forEach { call ->
                    assertThatThrownBy { call() }.hasMessageContaining("expired or changed")
                }
        }
    }

    @Test
    fun `clients without resolve capabilities receive complete eager results`() {
        session(false) { server, uri ->
            val documents = server.textDocumentService
            val id = TextDocumentIdentifier(uri)
            val lens = documents.codeLens(CodeLensParams(id)).get(30, SECONDS).single()
            assertThat(lens.command.arguments).containsExactly(uri, "Resolve")
            assertThat(lens.data).isNull()
            assertThat(
                    documents.documentLink(DocumentLinkParams(id)).get(30, SECONDS).single().target
                )
                .isNotBlank()
            assertThat(
                    documents
                        .inlayHint(InlayHintParams(id, Range(Position(0, 0), Position(6, 0))))
                        .get(30, SECONDS)
                )
                .anyMatch { it.tooltip != null }
            assertThat(
                    server.workspaceService
                        .symbol(WorkspaceSymbolParams("Resolve"))
                        .get(30, SECONDS)
                        .left
                )
                .allMatch { it.location.range != null }
            assertThatThrownBy { documents.resolveCodeLens(lens).get(10, SECONDS) }
                .hasMessageContaining("not negotiated")
        }
    }

    private fun session(lazy: Boolean, body: (XtcLanguageServer, String) -> Unit) {
        CompilerTestSupport.configure()
        val file = root.resolve("Resolve.x").toFile().canonicalFile.apply { writeText(source) }
        val uri = file.toURI().toString()
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(mock(LanguageClient::class.java))
            server
                .initialize(
                    InitializeParams().apply {
                        workspaceFolders =
                            listOf(WorkspaceFolder(root.toUri().toString(), "Resolve"))
                        capabilities =
                            ClientCapabilities().apply {
                                textDocument =
                                    TextDocumentClientCapabilities().apply {
                                        if (lazy) {
                                            codeLens =
                                                CodeLensCapabilities().apply {
                                                    resolveSupport =
                                                        CodeLensResolveSupportCapabilities(
                                                            listOf("command")
                                                        )
                                                }
                                            documentLink = DocumentLinkCapabilities()
                                            inlayHint =
                                                InlayHintCapabilities().apply {
                                                    resolveSupport =
                                                        InlayHintResolveSupportCapabilities(
                                                            listOf("tooltip")
                                                        )
                                                }
                                        }
                                    }
                                workspace =
                                    WorkspaceClientCapabilities().apply {
                                        if (lazy)
                                            symbol =
                                                SymbolCapabilities().apply {
                                                    resolveSupport =
                                                        WorkspaceSymbolResolveSupportCapabilities(
                                                            listOf("location.range")
                                                        )
                                                }
                                    }
                            }
                    }
                )
                .get(30, SECONDS)
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, source))
            )
            body(server, uri)
        }
    }
}
