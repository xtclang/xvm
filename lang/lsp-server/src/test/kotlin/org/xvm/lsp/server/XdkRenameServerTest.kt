package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameFile
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceEditCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

class XdkRenameServerTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @CsvSource("false,false", "false,true", "true,false", "true,true")
    fun `late watched creation preserves a rename proof only while its inputs are unchanged`(
        changed: Boolean,
        proposal: Boolean,
    ) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        val root =
            directory.resolve("App.x").toFile().apply {
                writeText("module App { Box make() = new Box(); }")
            }
        val member =
            directory.resolve("App/Box.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Box {}")
            }
        val hold = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val support = EmbeddingSupport.instance()
        val adapter =
            XdkAdapter(
                { source, repository, errors -> support.compileModule(source, repository, errors) },
                { sources, repository, errors ->
                    if (hold.compareAndSet(true, false)) {
                        entered.countDown()
                        check(release.await(20, SECONDS))
                    }
                    support.compileModule(sources, repository, errors)
                },
                { source, _, cursor, repository, errors ->
                    support.analyzeIncomplete(source, cursor, repository, errors)
                },
            )
        val server = XtcLanguageServer(adapter)
        server.connect(mock(LanguageClient::class.java))
        try {
            server
                .initialize(
                    parameters().apply {
                        capabilities.workspace.workspaceEdit.resourceOperations = listOf("rename")
                        capabilities.workspace.fileOperations =
                            FileOperationsWorkspaceCapabilities().apply { willRename = true }
                    },
                ).get(20, SECONDS)
            val uri = root.toURI().toString()
            server.replaceCompilerSourceModules(listOf(XdkSourceModule("App", uri)))
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, root.readText())),
            )
            server.textDocumentService
                .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                .get(30, SECONDS)
            hold.set(true)
            val params =
                RenameFilesParams(
                    listOf(
                        FileRename(
                            member.toURI().toString(),
                            directory.resolve("App/Crate.x").toUri().toString(),
                        ),
                    ),
                )
            val proof = if (proposal) server.renameFilesProposal(params).thenApply { it?.edit } else server.willRenameFiles(params)
            assertThat(entered.await(20, SECONDS)).isTrue()
            if (changed) member.writeText("class Box { Int extra = 1; }")
            server.workspaceService.didChangeWatchedFiles(
                DidChangeWatchedFilesParams(
                    listOf(root.parentFile, member).map {
                        FileEvent(it.toURI().toString(), FileChangeType.Created)
                    },
                ),
            )
            release.countDown()
            if (changed) {
                assertThatThrownBy { proof.get(30, SECONDS) }
                    .hasCauseInstanceOf(ResponseErrorException::class.java)
            } else {
                assertThat(proof.get(30, SECONDS)?.documentChanges).isNotEmpty()
            }
            assertThat(root.readText()).contains("new Box()")
        } finally {
            release.countDown()
            server.shutdown().get(20, SECONDS)
        }
    }

    @Test
    fun `file move proposal includes the configured graph and all resource operations without applying them`() {
        directory = directory.toRealPath()
        val root =
            directory.resolve("old/App.x").toFile().apply {
                parentFile.mkdirs()
                writeText("module App {}")
            }
        val target = directory.resolve("moved").toFile()
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(mock(LanguageClient::class.java))
        try {
            server
                .initialize(
                    parameters().apply {
                        capabilities.workspace.workspaceEdit.resourceOperations = listOf("rename")
                        capabilities.workspace.fileOperations = FileOperationsWorkspaceCapabilities().apply { willRename = true }
                    },
                ).get(20, SECONDS)
            server.replaceCompilerSourceModules(listOf(XdkSourceModule("App", root.toURI().toString(), resourceRoots = emptyList())))
            val params = RenameFilesParams(listOf(FileRename(root.parentFile.toURI().toString(), target.toURI().toString())))
            assertThat(server.willRenameFiles(params).get(30, SECONDS)).isNull()
            val proposal = requireNotNull(server.renameFilesProposal(params).get(30, SECONDS))
            assertThat(
                proposal.graph!!
                    .before
                    .single()
                    .uri,
            ).isEqualTo(root.toURI().toString())
            assertThat(
                proposal.graph!!
                    .after
                    .single()
                    .uri,
            ).isEqualTo(
                directory
                    .resolve("moved/App.x")
                    .toFile()
                    .toURI()
                    .toString(),
            )
            assertThat(
                proposal.graph!!
                    .after
                    .single()
                    .resourceRoots,
            ).isEmpty()
            assertThat(
                proposal.edit.documentChanges
                    .single()
                    .right,
            ).isInstanceOf(RenameFile::class.java)
            assertThat(proposal.scope!!.boundary).isEqualTo("CONFIGURED_GRAPH")
            assertThat(root).exists()
            assertThat(target).doesNotExist()
            assertThat(
                server
                    .compilerSourceModules()
                    .get(30, SECONDS)
                    .single()
                    .uri,
            ).isEqualTo(root.toURI().toString())
        } finally {
            server.shutdown().get(20, SECONDS)
        }
    }

    @Test
    fun `cross module type proposal carries dependency replacement and versioned source edits`() {
        directory = directory.toRealPath()
        val root = directory.resolve("App.x").toFile().apply { writeText("module App { Box make() = new Box(); }") }
        val other = directory.resolve("Other.x").toFile().apply { writeText("module Other {}") }
        val member =
            directory.resolve("App/Box.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Box {}")
            }
        val target = directory.resolve("Other/Box.x").toFile().apply { parentFile.mkdirs() }
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(mock(LanguageClient::class.java))
        try {
            server
                .initialize(
                    parameters().apply {
                        capabilities.workspace.workspaceEdit.resourceOperations = listOf("rename")
                        capabilities.workspace.fileOperations = FileOperationsWorkspaceCapabilities().apply { willRename = true }
                    },
                ).get(20, SECONDS)
            server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("App", root.toURI().toString()),
                    XdkSourceModule("Other", other.toURI().toString()),
                ),
            )
            val params = RenameFilesParams(listOf(FileRename(member.toURI().toString(), target.toURI().toString())))
            assertThat(server.willRenameFiles(params).get(30, SECONDS)).isNull()
            val proposal = requireNotNull(server.renameFilesProposal(params).get(30, SECONDS))
            assertThat(
                proposal.graph!!
                    .before
                    .single { it.name == "App" }
                    .dependencies,
            ).isEmpty()
            assertThat(
                proposal.graph!!
                    .after
                    .single { it.name == "App" }
                    .dependencies,
            ).containsExactly("Other")
            assertThat(proposal.edit.documentChanges.filter { it.isLeft }).isNotEmpty()
            assertThat(
                proposal.edit.documentChanges
                    .filter { it.isRight }
                    .single()
                    .right,
            ).isInstanceOf(RenameFile::class.java)
            assertThat(
                server
                    .compilerSourceModules()
                    .get(30, SECONDS)
                    .single { it.name == "App" }
                    .dependencies,
            ).isEmpty()
            assertThat(member).exists()
            assertThat(target).doesNotExist()
        } finally {
            server.shutdown().get(20, SECONDS)
        }
    }

    @Test
    fun `host rename carries the expected graph and replacement without applying either`() {
        directory = directory.toRealPath()
        val library = directory.resolve("Library.x").toFile()
        val consumer = directory.resolve("Consumer.x").toFile()
        val text = "module Library.example.org { class Box {} }"
        val use =
            "module Consumer { package lib import Library.example.org; lib.Box make() = new lib.Box(); }"
        library.writeText(text)
        consumer.writeText(use)
        val uri = library.toURI().toString()
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(mock(LanguageClient::class.java))
        try {
            server
                .initialize(
                    parameters().apply {
                        capabilities.workspace.workspaceEdit.resourceOperations = listOf("rename")
                    },
                ).get(20, SECONDS)
            server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Library.example.org", uri),
                    XdkSourceModule(
                        "Consumer",
                        consumer.toURI().toString(),
                        setOf("Library.example.org"),
                    ),
                ),
            )
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 7, text)))
            documents
                .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                .get(30, SECONDS)
            val params =
                RenameParams(
                    TextDocumentIdentifier(uri),
                    Position(0, text.indexOf("Library")),
                    "Renamed",
                )
            val proposal = requireNotNull(server.renameProposal(params).get(30, SECONDS))
            val scope = requireNotNull(proposal.scope)
            assertThat(scope.boundary).isEqualTo("CONFIGURED_GRAPH")
            assertThat(scope.modules.map { it.name })
                .containsExactlyInAnyOrder("Library.example.org", "Consumer")
            assertThat(scope.sourceUris).containsExactlyInAnyOrder(uri, consumer.toURI().toString())
            assertThat(scope.revision).startsWith("graph:")
            val graph = requireNotNull(proposal.graph)
            assertThat(graph.before.map { it.name })
                .containsExactlyInAnyOrder("Library.example.org", "Consumer")
            assertThat(graph.after.map { it.name })
                .containsExactlyInAnyOrder("Renamed.example.org", "Consumer")
            assertThat(graph.after.single { it.name == "Consumer" }.dependencies)
                .containsExactly("Renamed.example.org")
            assertThat(
                proposal.edit.documentChanges
                    .first()
                    .left.textDocument.version,
            ).isEqualTo(7)
            assertThat(
                proposal.edit.documentChanges
                    .last()
                    .right,
            ).isInstanceOf(RenameFile::class.java)
            assertThat(documents.rename(params).get(30, SECONDS)).isNull()
            assertThat(library.readText()).isEqualTo(text)
            assertThat(consumer.readText()).isEqualTo(use)
        } finally {
            server.shutdown().get(20, SECONDS)
        }
    }

    @Test
    fun `rename negotiates document changes and includes open and closed file versions`() {
        directory = directory.toRealPath()
        val root = directory.resolve("Rename.x").toFile()
        val member = directory.resolve("Rename/Child.x").toFile()
        val text = "module Rename { Int run() { Int local = 1; return local; } }"
        val child =
            "class Child { private Int pick(Int input) = input; Int run() = pick(input = 1); }"
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
            documents
                .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                .get(30, SECONDS)
            val params =
                RenameParams(
                    TextDocumentIdentifier(memberUri),
                    Position(0, child.indexOf("input")),
                    "value",
                )
            val edit = requireNotNull(documents.rename(params).get(30, SECONDS))
            assertThat(edit.changes).isNull()
            val changes = edit.documentChanges.associate { it.left.textDocument.uri to it.left }
            assertThat(changes.keys).containsExactly(memberUri)
            val rootEdit =
                requireNotNull(
                    documents
                        .rename(
                            RenameParams(
                                TextDocumentIdentifier(uri),
                                Position(0, text.indexOf("local")),
                                "value",
                            ),
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
            documents.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(memberUri, "xtc", 3, child)),
            )
            val openEdit = requireNotNull(documents.rename(params).get(30, SECONDS))
            assertThat(
                openEdit.documentChanges
                    .first { it.left.textDocument.uri == memberUri }
                    .left
                    .textDocument
                    .version,
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
        val text = "module Library { class Box { Int pick(Int value) = value; } }"
        val use =
            "module Consumer { package lib import Library; Int run(lib.Box box) = box.pick(1); }"
        library.writeText(text)
        consumer.writeText(use)
        val uri = library.toURI().toString()
        val consumerUri = consumer.toURI().toString()
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(mock(LanguageClient::class.java))
        try {
            server.initialize(parameters()).get(20, SECONDS)
            server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Library", uri),
                    XdkSourceModule("Consumer", consumerUri, setOf("Library")),
                ),
            )
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 7, text)))
            val document = TextDocumentIdentifier(uri)
            val at = Position(0, text.indexOf("pick"))
            val references =
                documents
                    .references(ReferenceParams(document, at, ReferenceContext(true)))
                    .get(30, SECONDS)
            assertThat(references.map { it.uri }).containsExactlyInAnyOrder(uri, consumerUri)
            val params = RenameParams(document, at, "choose")
            val edit = requireNotNull(documents.rename(params).get(30, SECONDS))
            assertThat(edit.changes).isNull()
            val changes = edit.documentChanges.associate { it.left.textDocument.uri to it.left }
            assertThat(changes.keys).containsExactlyInAnyOrder(uri, consumerUri)
            assertThat(changes.getValue(uri).textDocument.version).isEqualTo(7)
            assertThat<Int?>(changes.getValue(consumerUri).textDocument.version).isNull()
            val overlay = use.replace("box.pick(1)", "box.pick(1) + box.pick(2)")
            documents.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(consumerUri, "xtc", 3, overlay)),
            )
            documents
                .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(consumerUri)))
                .get(30, SECONDS)
            val openEdit = requireNotNull(documents.rename(params).get(30, SECONDS))
            val consumerEdit =
                openEdit.documentChanges.single { it.left.textDocument.uri == consumerUri }.left
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
                    .capabilities
                    .renameProvider,
            ).isNull()
            val text = "module Rename { Int run() { Int local = 1; return local; } }"
            val uri = "file:///Rename.x"
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)),
            )
            assertThat(
                server.textDocumentService
                    .rename(
                        RenameParams(
                            TextDocumentIdentifier(uri),
                            Position(0, text.indexOf("local")),
                            "value",
                        ),
                    ).get(30, SECONDS),
            ).isNull()
        } finally {
            server.shutdown().get(20, SECONDS)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "false, false, false, false",
        "true, false, false, false",
        "false, true, false, false",
        "true, true, false, false",
        "false, true, true, false",
        "true, true, true, false",
        "false, true, true, true",
        "true, true, true, true",
    )
    fun `missing method quick fix targets source destination with its own document version`(
        open: Boolean,
        separateModule: Boolean,
        requiresImport: Boolean,
        companion: Boolean,
    ) {
        directory = directory.toRealPath()
        val type = if (requiresImport) "shared.Value" else "Int"
        val text =
            """
            module Missing {
                ${if (separateModule) "package lib import Library;" else ""}
                ${if (requiresImport) "package shared import Types;" else ""}
                $type read(${if (separateModule) "lib.Other" else "Other"} peer, $type value) {
                    return peer.missing(value);
                }
            }
            """.trimIndent()
        val source = directory.resolve("Missing.x").toFile().apply { writeText(text) }
        val target =
            directory
                .resolve(
                    if (companion) {
                        "Library/Other.x"
                    } else if (separateModule) {
                        "Library.x"
                    } else {
                        "Missing/Other.x"
                    },
                ).toFile()
                .apply {
                    parentFile.mkdirs()
                    writeText(if (separateModule && !companion) "module Library { class Other {} }" else "class Other {}")
                }
        val root = if (companion) directory.resolve("Library.x").toFile().apply { writeText("module Library {}") } else target
        val rootOriginal = root.readText()
        val types = directory.resolve("Types.x").toFile().apply { writeText("module Types { class Value {} }") }
        val targetOriginal = target.readText()
        val uri = source.toURI().toString()
        val targetUri = target.toURI().toString()
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(mock(LanguageClient::class.java))
        try {
            server.initialize(parameters()).get(20, SECONDS)
            server.replaceCompilerSourceModules(
                buildList {
                    if (requiresImport) add(XdkSourceModule("Types", types.toURI().toString()))
                    val typeDependency = if (requiresImport) setOf("Types") else emptySet()
                    if (separateModule) add(XdkSourceModule("Library", root.toURI().toString(), typeDependency))
                    add(XdkSourceModule("Missing", uri, (if (separateModule) setOf("Library") else emptySet()) + typeDependency))
                },
            )
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 7, text)))
            if (open) documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(targetUri, "xtc", 13, target.readText())))
            if (open && companion) {
                documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(root.toURI().toString(), "xtc", 17, rootOriginal)))
            }
            val line = text.lines().indexOfFirst { "peer.missing" in it }
            val at = Position(line, text.lines()[line].indexOf("missing"))
            val actions =
                documents
                    .codeAction(CodeActionParams(TextDocumentIdentifier(uri), Range(at, at), CodeActionContext(emptyList())))
                    .get(30, SECONDS)
            val edit = actions.single { it.isRight && it.right.title == "Create public method 'missing' in 'Other'" }.right.edit
            assertThat(edit.changes).isNull()
            assertThat(edit.documentChanges).hasSize(if (companion) 2 else 1)
            val change = edit.documentChanges.map { it.left }.single { URI(it.textDocument.uri) == URI(targetUri) }
            if (companion) {
                val rootChange = edit.documentChanges.map { it.left }.single { URI(it.textDocument.uri) == root.toURI() }
                assertThat<Int?>(rootChange.textDocument.version).isEqualTo(if (open) 17 else null)
                assertThat(rootChange.edits).hasSize(1)
                assertThat(
                    rootChange.edits
                        .single()
                        .left.newText,
                ).contains("package types import Types;")
            }
            assertThat(URI(change.textDocument.uri)).isEqualTo(URI(targetUri))
            assertThat<Int?>(change.textDocument.version).isEqualTo(if (open) 13 else null)
            assertThat(change.edits).hasSize(if (requiresImport && !companion) 2 else 1)
            val generated = change.edits.joinToString("\n") { it.left.newText }
            if (requiresImport) {
                assertThat(generated).contains("public types.Value missing(types.Value arg1)")
                if (!companion) assertThat(generated).contains("package types import Types;")
            } else {
                assertThat(generated).contains("public Int64 missing(Int64 arg1)")
            }
            assertThat(source.readText()).isEqualTo(text)
            assertThat(target.readText()).isEqualTo(targetOriginal)
            assertThat(root.readText()).isEqualTo(rootOriginal)
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
            val edit = actions.single { it.isRight && it.right.title == "Remove unused import 'StringBuffer'" }.right.edit
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
        val root =
            directory.resolve("App.x").toFile().also {
                it.writeText("module App { Item make() = new Item(); }")
            }
        val member =
            directory.resolve("App/Item.x").toFile().also {
                it.parentFile.mkdirs()
                it.writeText("class Item {}")
            }
        directory.resolve("App/Item/Nested.x").toFile().also {
            it.parentFile.mkdirs()
            it.writeText("class Nested {}")
        }
        listOf(false, true).forEach { enabled ->
            val server = XtcLanguageServer(XdkAdapter())
            server.connect(mock(LanguageClient::class.java))
            try {
                val params =
                    parameters().apply {
                        workspaceFolders =
                            listOf(WorkspaceFolder(directory.toUri().toString(), "workspace"))
                        capabilities.workspace.workspaceEdit.resourceOperations =
                            if (enabled) listOf("rename") else emptyList()
                    }
                server.initialize(params).get(20, SECONDS)
                val documents = server.textDocumentService
                val uri = root.toURI().toString()
                documents.didOpen(
                    DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 7, root.readText())),
                )
                documents
                    .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                    .get(30, SECONDS)
                val edit =
                    documents
                        .rename(
                            RenameParams(
                                TextDocumentIdentifier(uri),
                                Position(0, root.readText().indexOf("Item")),
                                "Renamed",
                            ),
                        ).get(30, SECONDS)
                if (!enabled) {
                    assertThat(edit).isNull()
                } else {
                    val changes = requireNotNull(edit).documentChanges
                    assertThat(changes.takeWhile { it.isLeft }).hasSize(2)
                    val moves = changes.dropWhile { it.isLeft }.map { it.right as RenameFile }
                    assertThat(moves).hasSize(2)
                    val move = moves.first()
                    assertThat(move.oldUri).isEqualTo(member.toURI().toString())
                    assertThat(move.newUri).endsWith("/Renamed.x")
                    assertThat(move.options.overwrite).isFalse()
                    assertThat(moves.last().oldUri).endsWith("/App/Item")
                    assertThat(moves.last().newUri).endsWith("/App/Renamed")
                    assertThat(moves).allMatch {
                        it.options.overwrite == false && it.options.ignoreIfExists == false
                    }
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
                    textDocument = editorInitializeParams().capabilities.textDocument
                    workspace =
                        WorkspaceClientCapabilities().apply {
                            workspaceEdit =
                                WorkspaceEditCapabilities().apply { documentChanges = true }
                        }
                }
        }
}
