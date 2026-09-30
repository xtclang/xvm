package org.xvm.lsp.server

import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeActionCapabilities
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKindCapabilities
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionResolveSupportCapabilities
import org.eclipse.lsp4j.CompletionCapabilities
import org.eclipse.lsp4j.CompletionItemCapabilities
import org.eclipse.lsp4j.CompletionItemResolveSupportCapabilities
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceEditCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkResolveProtocolTest {
    @TempDir lateinit var directory: Path

    private val uri: String
        get() = directory.resolve("Resolve.x").toUri().toString()

    @Test
    fun `completion resolves documentation without changing insertion and rejects an old snapshot`() {
        val source =
            "module Resolve { class Box { /** Reads a value. */ Int read() = 1; } Int run(Box box) = box.re; }"
        session(true, source) { server ->
            val service = server.textDocumentService
            service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, source)))
            val item =
                service
                    .completion(
                        CompletionParams(
                            TextDocumentIdentifier(uri),
                            Position(0, source.lastIndexOf("re;") + 2),
                        )
                    )
                    .get(30, SECONDS)
                    .left
                    .single { it.label == "read" }
            assertThat(item.documentation).isNull()
            assertThat(item.data).isNotNull()
            val insertion = item.textEdit
            val resolved = service.resolveCompletionItem(item).get(10, SECONDS)
            assertThat(resolved.documentation.left).contains("Reads a value")
            assertThat(resolved.textEdit).isEqualTo(insertion)
            service.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(uri)))
            service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, source)))
            assertThatThrownBy { service.resolveCompletionItem(item).get(10, SECONDS) }
                .hasMessageContaining("expired or changed")
        }
    }

    @Test
    fun `code action lazily converts versioned edits and refuses changed inputs`() {
        val source = "module Resolve { import ecstasy.text.StringBuffer; }"
        session(true, source) { server ->
            val service = server.textDocumentService
            service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 7, source)))
            val action =
                service
                    .codeAction(
                        CodeActionParams(
                            TextDocumentIdentifier(uri),
                            Range(Position(0, 0), Position(0, source.length)),
                            CodeActionContext(emptyList()),
                        )
                    )
                    .get(30, SECONDS)
                    .single()
                    .right
            assertThat(action.edit).isNull()
            assertThat(action.data).isNotNull()
            val resolved = service.resolveCodeAction(action).get(10, SECONDS)
            assertThat(resolved.edit.documentChanges.single().left.textDocument.version)
                .isEqualTo(7)
            assertThat(resolved.edit.documentChanges.single().left.edits.single().left.newText)
                .isEmpty()
            service.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri, 8),
                    listOf(TextDocumentContentChangeEvent("\n$source")),
                )
            )
            assertThatThrownBy { service.resolveCodeAction(action).get(10, SECONDS) }
                .hasMessageContaining("expired or changed")
        }
    }

    @Test
    fun `clients without data and edit resolve support receive eager actions`() {
        val source = "module Resolve { import ecstasy.text.StringBuffer; }"
        session(false, source) { server ->
            val service = server.textDocumentService
            service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, source)))
            val action =
                service
                    .codeAction(
                        CodeActionParams(
                            TextDocumentIdentifier(uri),
                            Range(Position(0, 0), Position(0, source.length)),
                            CodeActionContext(emptyList()),
                        )
                    )
                    .get(30, SECONDS)
                    .single()
                    .right
            assertThat(action.edit).isNotNull()
            assertThat(action.data).isNull()
            assertThatThrownBy { service.resolveCodeAction(action).get(10, SECONDS) }
                .hasMessageContaining("not negotiated")
        }
    }

    private fun session(resolve: Boolean, source: String, body: (XtcLanguageServer) -> Unit) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        directory.resolve("Resolve.x").toFile().writeText(source)
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(mock(LanguageClient::class.java))
            val capabilities =
                server
                    .initialize(
                        InitializeParams().apply {
                            workspaceFolders =
                                listOf(WorkspaceFolder(directory.toUri().toString(), "Resolve"))
                            this.capabilities =
                                ClientCapabilities().apply {
                                    workspace =
                                        WorkspaceClientCapabilities().apply {
                                            workspaceEdit =
                                                WorkspaceEditCapabilities().apply {
                                                    documentChanges = true
                                                }
                                        }
                                    textDocument =
                                        TextDocumentClientCapabilities().apply {
                                            completion =
                                                CompletionCapabilities().apply {
                                                    completionItem =
                                                        CompletionItemCapabilities().apply {
                                                            if (resolve)
                                                                resolveSupport =
                                                                    CompletionItemResolveSupportCapabilities(
                                                                        listOf("documentation")
                                                                    )
                                                        }
                                                }
                                            codeAction =
                                                CodeActionCapabilities().apply {
                                                    codeActionLiteralSupport =
                                                        CodeActionLiteralSupportCapabilities(
                                                            CodeActionKindCapabilities(listOf(""))
                                                        )
                                                    dataSupport = resolve
                                                    resolveSupport =
                                                        CodeActionResolveSupportCapabilities(
                                                            listOf("edit")
                                                        )
                                                }
                                        }
                                }
                        }
                    )
                    .get(30, SECONDS)
                    .capabilities
            assertThat(capabilities.completionProvider.resolveProvider).isEqualTo(resolve)
            assertThat(capabilities.codeActionProvider.isRight).isEqualTo(resolve)
            body(server)
        }
    }
}
