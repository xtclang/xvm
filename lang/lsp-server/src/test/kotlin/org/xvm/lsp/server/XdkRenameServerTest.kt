package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameFile
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceEditCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
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
    fun `configured graph references and rename include closed consumers with correct edit versions`() {
        directory = directory.toRealPath()
        val library = directory.resolve("Library.x").toFile()
        val consumer = directory.resolve("Consumer.x").toFile()
        val text = "module Library { class Box { Int pick(Int value)=value; } }"
        val use = "module Consumer { package lib import Library; Int run(lib.Box box)=box.pick(1); }"
        library.writeText(text)
        consumer.writeText(use)
        val uri = library.toURI().toString()
        val consumerUri = consumer.toURI().toString()
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(mock(LanguageClient::class.java))
        try {
            server.initialize(parameters()).get(20, SECONDS)
            server.replaceCompilerSourceModules(
                listOf(XdkSourceModule("Library", uri), XdkSourceModule("Consumer", consumerUri, setOf("Library"))),
            )
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 7, text)))
            val document = TextDocumentIdentifier(uri)
            val at = Position(0, text.indexOf("pick"))
            val references = documents.references(ReferenceParams(document, at, ReferenceContext(true))).get(30, SECONDS)
            assertThat(references.map { it.uri }).containsExactlyInAnyOrder(uri, consumerUri)
            val params = RenameParams(document, at, "choose")
            val edit = requireNotNull(documents.rename(params).get(30, SECONDS))
            assertThat(edit.changes).isNull()
            val changes = edit.documentChanges.associate { it.left.textDocument.uri to it.left }
            assertThat(changes.keys).containsExactlyInAnyOrder(uri, consumerUri)
            assertThat(changes.getValue(uri).textDocument.version).isEqualTo(7)
            assertThat<Int?>(changes.getValue(consumerUri).textDocument.version).isNull()
            val overlay = use.replace("box.pick(1)", "box.pick(1)+box.pick(2)")
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(consumerUri, "xtc", 3, overlay)))
            documents.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(consumerUri))).get(30, SECONDS)
            val openEdit = requireNotNull(documents.rename(params).get(30, SECONDS))
            val consumerEdit = openEdit.documentChanges.single { it.left.textDocument.uri == consumerUri }.left
            assertThat(consumerEdit.textDocument.version).isEqualTo(3)
            assertThat(consumerEdit.edits).hasSize(2)
            assertThat(consumer.readText()).isEqualTo(use)
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

    @Test
    fun `compiler import actions preserve the document version on the wire`() {
        val text = "module Imports { import ecstasy.text.StringBuffer; }"
        val file = directory.resolve("Imports.x").toFile().apply { writeText(text) }
        val uri = file.toURI().toString()
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(mock(LanguageClient::class.java))
        try {
            server.initialize(parameters()).get(20, SECONDS)
            server.replaceCompilerSourceModules(listOf(XdkSourceModule("Imports", uri)))
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 7, text)))
            val actions =
                documents
                    .codeAction(
                        CodeActionParams(
                            TextDocumentIdentifier(uri),
                            Range(Position(0, 0), Position(0, text.length)),
                            CodeActionContext(emptyList()),
                        ),
                    ).get(30, SECONDS)
            val edit = actions.single().right.edit
            assertThat(edit.changes).isNull()
            assertThat(
                edit.documentChanges
                    .single()
                    .left.textDocument.version,
            ).isEqualTo(7)
            assertThat(
                edit.documentChanges
                    .single()
                    .left.edits
                    .single()
                    .left.newText,
            ).isEmpty()
        } finally {
            server.shutdown().get(20, SECONDS)
        }
    }

    @Test
    fun `member file rename requires resource operation support and follows versioned text edits`() {
        directory = directory.toRealPath()
        val root = directory.resolve("App.x").toFile().also { it.writeText("module App { Item make()=new Item(); }") }
        val member =
            directory.resolve("App/Item.x").toFile().also {
                it.parentFile.mkdirs()
                it.writeText("class Item {}")
            }
        listOf(false, true).forEach { enabled ->
            val server = XtcLanguageServer(XdkAdapter())
            server.connect(mock(LanguageClient::class.java))
            try {
                val params =
                    parameters().apply {
                        workspaceFolders = listOf(WorkspaceFolder(directory.toUri().toString(), "workspace"))
                        capabilities.workspace.workspaceEdit.resourceOperations = if (enabled) listOf("rename") else emptyList()
                    }
                server.initialize(params).get(20, SECONDS)
                val documents = server.textDocumentService
                val uri = root.toURI().toString()
                documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 7, root.readText())))
                documents.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri))).get(30, SECONDS)
                val edit =
                    documents
                        .rename(
                            RenameParams(TextDocumentIdentifier(uri), Position(0, root.readText().indexOf("Item")), "Renamed"),
                        ).get(30, SECONDS)
                if (!enabled) {
                    assertThat(edit).isNull()
                } else {
                    val changes = requireNotNull(edit).documentChanges
                    assertThat(changes.dropLast(1)).allMatch { it.isLeft }
                    val move = changes.last().right as RenameFile
                    assertThat(move.oldUri).isEqualTo(member.toURI().toString())
                    assertThat(move.newUri).endsWith("/Renamed.x")
                    assertThat(move.options.overwrite).isFalse()
                }
                assertThat(member.isFile).isTrue()
            } finally {
                server.shutdown().get(20, SECONDS)
            }
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
