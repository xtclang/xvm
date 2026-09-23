package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceEditCapabilities
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS

class XdkRenameServerTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `rename negotiates document changes and includes open and closed file versions`() {
        directory = directory.toRealPath()
        val root = directory.resolve("Rename.x").toFile()
        val member = directory.resolve("Rename/Child.x").toFile()
        val text = "module Rename { Int run() { Int local=1; return local; } }"
        val child = "class Child { private Int pick(Int input)=input; Int run()=pick(input=1); }"
        root.writeText(text)
        member.parentFile.mkdirs()
        member.writeText(child)
        val uri = root.toURI().toString()
        val memberUri = member.toURI().toString()
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(mock(LanguageClient::class.java))
        try {
            val capabilities = server.initialize(parameters()).get(20, SECONDS).capabilities
            assertThat(capabilities.renameProvider.right.prepareProvider).isTrue()
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 7, text)))
            documents.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri))).get(30, SECONDS)
            val params = RenameParams(TextDocumentIdentifier(memberUri), Position(0, child.indexOf("input")), "value")
            val edit = requireNotNull(documents.rename(params).get(30, SECONDS))
            assertThat(edit.changes).isNull()
            val changes = edit.documentChanges.associate { it.left.textDocument.uri to it.left }
            assertThat(changes.keys).containsExactly(memberUri)
            val rootEdit =
                requireNotNull(
                    documents
                        .rename(
                            RenameParams(TextDocumentIdentifier(uri), Position(0, text.indexOf("local")), "value"),
                        ).get(30, SECONDS),
                )
            assertThat(
                rootEdit.documentChanges
                    .single()
                    .left.textDocument.version,
            ).isEqualTo(7)
            assertThat<Int?>(changes.getValue(memberUri).textDocument.version).isNull()
            assertThat(
                changes
                    .getValue(memberUri)
                    .edits
                    .first()
                    .left.newText,
            ).isEqualTo("value")
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(memberUri, "xtc", 3, child)))
            val openEdit = requireNotNull(documents.rename(params).get(30, SECONDS))
            assertThat(
                openEdit.documentChanges
                    .first { it.left.textDocument.uri == memberUri }
                    .left.textDocument.version,
            ).isEqualTo(3)
        } finally {
            server.shutdown().get(20, SECONDS)
        }
    }

    @Test
    fun `clients without versioned edits receive no compiler rename capability or unsafe fallback`() {
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(mock(LanguageClient::class.java))
        try {
            assertThat(
                server
                    .initialize(InitializeParams())
                    .get(20, SECONDS)
                    .capabilities.renameProvider,
            ).isNull()
            val text = "module Rename { Int run() { Int local=1; return local; } }"
            val uri = "file:///Rename.x"
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))
            assertThat(
                server.textDocumentService
                    .rename(
                        RenameParams(TextDocumentIdentifier(uri), Position(0, text.indexOf("local")), "value"),
                    ).get(30, SECONDS),
            ).isNull()
        } finally {
            server.shutdown().get(20, SECONDS)
        }
    }

    private fun parameters() =
        InitializeParams().apply {
            capabilities =
                ClientCapabilities().apply {
                    workspace =
                        WorkspaceClientCapabilities().apply {
                            workspaceEdit = WorkspaceEditCapabilities().apply { documentChanges = true }
                        }
                }
        }
}
