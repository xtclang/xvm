package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionResolveSupportCapabilities
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameParams
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
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.RenameRefused
import org.xvm.lsp.adapter.hookedAdapter
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.uncompiledRename
import org.xvm.lsp.logged
import org.xvm.lsp.protocol.DiscoverConsumersParams
import org.xvm.lsp.protocol.SourceModuleConfiguration
import org.xvm.testing.awaitUntil
import org.xvm.testing.awaited
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks versioned rename and move proposals at the server boundary. Captured graph and resource inputs
 * authorize a proposal but do not apply it, and later changes must invalidate stale proof.
 */
class XdkRenameServerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `every position inside a project method name prepares the same complete rename`() {
        val text =
            "module App { (String, String) getPassword(String? realm = Null, Boolean force = False) { " +
                "return \"name\", \"password\"; } void run() { getPassword(); } }"
        val root = directory.resolve("App.x").toFile().apply { writeText(text) }
        val uri = root.toURI().toString()
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(mock(LanguageClient::class.java))
            server.initialize(parameters()).awaited()
            server.replaceCompilerSourceModules(listOf(XdkSourceModule("App", uri)))
            val documents = server.textDocumentService
            val document = TextDocumentIdentifier(uri)
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))
            val name = "getPassword"
            val starts = listOf(text.indexOf(name), text.lastIndexOf(name))
            starts.forEach { start ->
                name.indices.forEach { offset ->
                    val prepared =
                        documents
                            .prepareRename(
                                PrepareRenameParams(document, Position(0, start + offset)),
                            ).awaited()
                            .second
                    assertThat(prepared.placeholder).isEqualTo(name)
                    assertThat(prepared.range).isEqualTo(Range(Position(0, start), Position(0, start + name.length)))
                }
            }
            val edit =
                requireNotNull(
                    documents.rename(RenameParams(document, Position(0, starts.first() + 5), "readPassword")).awaited(),
                )
            val changes = edit.documentChanges.single().left
            assertThat(changes.textDocument.uri).isEqualTo(uri)
            assertThat(changes.edits.map { it.left.newText }).containsExactly("readPassword", "readPassword")
            assertThat(changes.edits.map { it.left.range }).containsExactlyInAnyOrderElementsOf(
                starts.map { Range(Position(0, it), Position(0, it + name.length)) },
            )
            assertThat(root.readText()).isEqualTo(text)
        }
    }

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
        val adapter =
            hookedAdapter {
                if (hold.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.awaited())
                }
            }
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
                ).awaited()
            val uri = root.toURI().toString()
            server.replaceCompilerSourceModules(listOf(XdkSourceModule("App", uri)))
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, root.readText())),
            )
            server.textDocumentService
                .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                .awaited()
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
            assertThat(entered.awaited()).isTrue()
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
                assertThatThrownBy { proof.awaited() }
                    .hasCauseInstanceOf(ResponseErrorException::class.java)
            } else {
                assertThat(proof.awaited()?.documentChanges).isNotEmpty()
            }
            assertThat(root.readText()).contains("new Box()")
        } finally {
            release.countDown()
            server.shutdown().awaited()
        }
    }

    @Test
    fun `a file event reaches the module graph before the next request, without waiting for the workspace walk`() {
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
        val walking = CountDownLatch(1)
        val release = CountDownLatch(1)
        val server =
            XtcLanguageServer(XdkAdapter(), rescans = { rescan ->
                if (hold.get()) {
                    walking.countDown()
                    check(release.awaited())
                }
                rescan()
            })
        server.connect(mock(LanguageClient::class.java))
        try {
            server
                .initialize(
                    parameters().apply {
                        workspaceFolders = listOf(WorkspaceFolder(directory.toUri().toString(), "workspace"))
                        capabilities.workspace.workspaceEdit.resourceOperations = listOf("rename")
                        capabilities.workspace.fileOperations =
                            FileOperationsWorkspaceCapabilities().apply { willRename = true }
                    },
                ).awaited()
            val uri = root.toURI().toString()
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, root.readText())))
            server.textDocumentService
                .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                .awaited()

            // as when IntelliJ undoes a rename: a watcher event, then at once willRenameFiles
            hold.set(true)
            val other = directory.resolve("Other.x").toFile().apply { writeText("module Other { package app import App; }") }
            server.workspaceService.didChangeWatchedFiles(
                DidChangeWatchedFilesParams(listOf(FileEvent(other.toURI().toString(), FileChangeType.Created))),
            )
            assertThat(walking.awaited()).isTrue()
            assertThat(server.compilerSourceModules().awaited().map { it.name })
                .describedAs("The event is in the graph while the workspace walk is held")
                .contains("Other")
            val rename =
                server.willRenameFiles(
                    RenameFilesParams(
                        listOf(FileRename(member.toURI().toString(), directory.resolve("App/Crate.x").toUri().toString())),
                    ),
                )
            // the walk finds nothing the event did not already apply, so it retires nothing
            release.countDown()
            assertThat(rename.awaited()?.documentChanges).isNotEmpty()
            server.discoveryIdle().awaited()
            assertThat(server.compilerSourceModules().awaited().map { it.name }).contains("App", "Other")
        } finally {
            release.countDown()
            server.shutdown().awaited()
        }
    }

    @Test
    fun `consumer discovery reports importers under a folder and refuses roots that are not folders`() {
        directory = directory.toRealPath()
        val library =
            directory.resolve("workspace/Library.x").toFile().apply {
                parentFile.mkdirs()
                writeText("module Library {}")
            }
        val consumer =
            directory.resolve("external/Consumer.x").toFile().apply {
                parentFile.mkdirs()
                writeText("module Consumer { package lib import Library; }")
            }
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(mock(LanguageClient::class.java))
        try {
            server.initialize(parameters()).awaited()
            server.replaceCompilerSourceModules(listOf(XdkSourceModule("Library", library.toURI().toString())))
            val found =
                server
                    .discoverConsumers(
                        DiscoverConsumersParams(listOf(consumer.parentFile.toURI().toString())),
                    ).awaited()
            assertThat(found.modules).containsExactly(
                SourceModuleConfiguration("Consumer", consumer.toURI().toString(), listOf("Library")),
            )
            assertThat(found.conflicts).isEmpty()
            assertThat(server.compilerSourceModules().awaited().map { it.name }).containsExactly("Library")
            listOf(emptyList(), listOf(consumer.toURI().toString()), listOf("untitled:Folder")).forEach { roots ->
                val discovered = server.discoverConsumers(DiscoverConsumersParams(roots))
                awaitUntil { discovered.isDone }
                assertThat(discovered)
                    .failsWithin(0, SECONDS)
                    .withThrowableOfType(ExecutionException::class.java)
                    .satisfies({
                        assertThat((it.cause as ResponseErrorException).responseError.code)
                            .isEqualTo(ResponseErrorCode.InvalidParams.value)
                    })
            }
        } finally {
            server.shutdown().awaited()
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
                ).awaited()
            server.replaceCompilerSourceModules(listOf(XdkSourceModule("App", root.toURI().toString(), resourceRoots = emptyList())))
            val params = RenameFilesParams(listOf(FileRename(root.parentFile.toURI().toString(), target.toURI().toString())))
            assertThat(server.willRenameFiles(params).awaited()).isNull()
            val proposal = requireNotNull(server.renameFilesProposal(params).awaited())
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
                    .awaited()
                    .single()
                    .uri,
            ).isEqualTo(root.toURI().toString())
        } finally {
            server.shutdown().awaited()
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
                ).awaited()
            server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("App", root.toURI().toString()),
                    XdkSourceModule("Other", other.toURI().toString()),
                ),
            )
            val params = RenameFilesParams(listOf(FileRename(member.toURI().toString(), target.toURI().toString())))
            assertThat(server.willRenameFiles(params).awaited()).isNull()
            val proposal = requireNotNull(server.renameFilesProposal(params).awaited())
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
                    .awaited()
                    .single { it.name == "App" }
                    .dependencies,
            ).isEmpty()
            assertThat(member).exists()
            assertThat(target).doesNotExist()
        } finally {
            server.shutdown().awaited()
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
                ).awaited()
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
                .awaited()
            val params =
                RenameParams(
                    TextDocumentIdentifier(uri),
                    Position(0, text.indexOf("Library")),
                    "Renamed",
                )
            val proposal = requireNotNull(server.renameProposal(params).awaited())
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
            // the plain request can't carry the graph's change: it is refused with the reason, not answered with no edit
            assertThatThrownBy { documents.rename(params).awaited() }
                .rootCause()
                .isInstanceOfSatisfying(ResponseErrorException::class.java) {
                    assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.RequestFailed.value)
                    assertThat(it.responseError.message).contains("xtc.compiler.sourceModules")
                }
            assertThat(library.readText()).isEqualTo(text)
            assertThat(consumer.readText()).isEqualTo(use)
        } finally {
            server.shutdown().awaited()
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
            val capabilities = server.initialize(parameters()).awaited().capabilities
            assertThat(capabilities.renameProvider.right.prepareProvider).isTrue()
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 7, text)))
            documents
                .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                .awaited()
            val params =
                RenameParams(
                    TextDocumentIdentifier(memberUri),
                    Position(0, child.indexOf("input")),
                    "value",
                )
            val edit = requireNotNull(documents.rename(params).awaited())
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
                        ).awaited(),
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
            val openEdit = requireNotNull(documents.rename(params).awaited())
            assertThat(
                openEdit.documentChanges
                    .first { it.left.textDocument.uri == memberUri }
                    .left
                    .textDocument
                    .version,
            ).isEqualTo(3)
        } finally {
            server.shutdown().awaited()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["textDocument/rename", "xtc/rename"])
    fun `a rename that the client cancels stops its proof at the adapter`(method: String) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        val text = "module Library { class Box { Int pick(Int value) = value; } }"
        val library = directory.resolve("Library.x").toFile().apply { writeText(text) }
        val consumer =
            directory.resolve("Consumer.x").toFile().apply {
                writeText("module Consumer { package lib import Library; Int run(lib.Box box) = box.pick(1); }")
            }
        val uri = library.toURI().toString()
        val hold = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter =
            hookedAdapter {
                if (hold.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.awaited())
                }
            }
        val lines = CopyOnWriteArrayList<String>()
        logged(lines) {
            val server = XtcLanguageServer(adapter)
            server.connect(mock(LanguageClient::class.java))
            try {
                server.initialize(parameters()).awaited()
                server.replaceCompilerSourceModules(
                    listOf(
                        XdkSourceModule("Library", uri),
                        XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")),
                    ),
                )
                val documents = server.textDocumentService
                documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))
                documents.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri))).awaited()
                hold.set(true)
                val params = RenameParams(TextDocumentIdentifier(uri), Position(0, text.indexOf("pick")), "choose")
                val renamed = if (method == "xtc/rename") server.renameProposal(params) else documents.rename(params)
                // the proof's first compile, Library's before the edit
                assertThat(entered.awaited()).isTrue()
                // as the client's cancellation does
                assertThat(renamed.cancel(false)).isTrue()
                release.countDown()
                // the proof stops after the compile it was in, where it would go on to compile Consumer, and both again
                // after the edit
                val kind = if (method == "xtc/rename") "rename_proposal" else "rename"
                awaitUntil { lines.any { it.startsWith("project query: kind=$kind ") } }
                assertThat(lines.single { it.startsWith("project query: kind=$kind ") })
                    .contains("outcome=cancelled")
                    .contains("compiles=1 in")
            } finally {
                release.countDown()
                server.shutdown().awaited()
            }
        }
    }

    @ParameterizedTest
    @CsvSource("textDocument/rename,false", "textDocument/rename,true", "xtc/rename,false", "xtc/rename,true")
    fun `a rename whose project changed while it was proven answers ContentModified, not a refusal`(
        method: String,
        watched: Boolean,
    ) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        val text = "module Library { class Box { Int pick(Int value) = value; } }"
        val library = directory.resolve("Library.x").toFile().apply { writeText(text) }
        val consumerText = "module Consumer { package lib import Library; Int run(lib.Box box) = box.pick(1); }"
        val consumer = directory.resolve("Consumer.x").toFile().apply { writeText(consumerText) }
        val uri = library.toURI().toString()
        val hold = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter =
            hookedAdapter {
                if (hold.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.awaited())
                }
            }
        val server = XtcLanguageServer(adapter)
        server.connect(mock(LanguageClient::class.java))
        try {
            server.initialize(parameters()).awaited()
            server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Library", uri),
                    XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")),
                ),
            )
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))
            documents.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri))).awaited()
            hold.set(true)
            val params = RenameParams(TextDocumentIdentifier(uri), Position(0, text.indexOf("pick")), "choose")
            val renamed = if (method == "xtc/rename") server.renameProposal(params) else documents.rename(params)
            // the proof's first compile, Library's before the edit
            assertThat(entered.awaited()).isTrue()
            // Consumer, which no editor has open, changes on disk: a watcher's event retires the proof, and without one
            // the proof finds the change once it has compiled
            consumer.writeText("$consumerText // changed")
            if (watched) {
                server.workspaceService.didChangeWatchedFiles(
                    DidChangeWatchedFilesParams(listOf(FileEvent(consumer.toURI().toString(), FileChangeType.Changed))),
                )
            }
            release.countDown()
            assertThatThrownBy { renamed.awaited() }
                .rootCause()
                .isInstanceOfSatisfying(ResponseErrorException::class.java) {
                    assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.ContentModified.value)
                }
        } finally {
            release.countDown()
            server.shutdown().awaited()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["textDocument/rename", "xtc/rename"])
    fun `a rename while a module of the graph does not compile says which, for the client to ask again`(method: String) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        val text = "module Library { class Box { Int pick(Int value) = value; } }"
        val library = directory.resolve("Library.x").toFile().apply { writeText(text) }
        val consumer =
            directory.resolve("Consumer.x").toFile().apply {
                writeText("module Consumer { package lib import Library; Int run(lib.Box box) = box.pick(1) }")
            }
        val uri = library.toURI().toString()
        val server = XtcLanguageServer(hookedAdapter {})
        server.connect(mock(LanguageClient::class.java))
        try {
            server.initialize(parameters()).awaited()
            server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Library", uri),
                    XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")),
                ),
            )
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))
            documents.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri))).awaited()
            val params = RenameParams(TextDocumentIdentifier(uri), Position(0, text.indexOf("pick")), "choose")
            val renamed = if (method == "xtc/rename") server.renameProposal(params) else documents.rename(params)
            assertThatThrownBy { renamed.awaited() }
                .rootCause()
                .isInstanceOfSatisfying(ResponseErrorException::class.java) {
                    assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.RequestFailed.value)
                    assertThat(it.responseError.message).isEqualTo(uncompiledRename(listOf("Consumer")))
                    assertThat(it.responseError.data).isEqualTo(RenameRefused.UNPROVEN)
                }
        } finally {
            server.shutdown().awaited()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["xtc/renameFiles", "workspace/willRenameFiles"])
    fun `a move whose project changed while it was proven answers ContentModified, not no edit`(method: String) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        val text = "module App { Box make() = new Box(); }"
        val root = directory.resolve("App.x").toFile().apply { writeText(text) }
        val member =
            directory.resolve("App/Box.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Box {}")
            }
        val other = directory.resolve("App/Other.x").toFile().apply { writeText("class Other {}") }
        val hold = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter =
            hookedAdapter {
                if (hold.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.awaited())
                }
            }
        val server = XtcLanguageServer(adapter)
        server.connect(mock(LanguageClient::class.java))
        try {
            server.initialize(moves()).awaited()
            val uri = root.toURI().toString()
            server.replaceCompilerSourceModules(listOf(XdkSourceModule("App", uri)))
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))
            server.textDocumentService.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri))).awaited()
            hold.set(true)
            val params =
                RenameFilesParams(listOf(FileRename(member.toURI().toString(), directory.resolve("App/Crate.x").toUri().toString())))
            val moved = if (method == "xtc/renameFiles") server.renameFilesProposal(params) else server.willRenameFiles(params)
            // the proof's compile of the sources before the move
            assertThat(entered.awaited()).isTrue()
            // Other, which no editor has open, changes on disk, which no watcher says: the proof finds it once it has compiled
            other.writeText("class Other { Int extra = 1; }")
            release.countDown()
            assertThatThrownBy { moved.awaited() }
                .rootCause()
                .isInstanceOfSatisfying(ResponseErrorException::class.java) {
                    assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.ContentModified.value)
                }
            assertThat(member).exists()
        } finally {
            release.countDown()
            server.shutdown().awaited()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["xtc/renameFiles", "workspace/willRenameFiles"])
    fun `a move while a module of the graph does not compile says which, for the client to ask again`(method: String) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        val text = "module Library {}"
        val library = directory.resolve("Library.x").toFile().apply { writeText(text) }
        val member =
            directory.resolve("Library/Box.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Box {}")
            }
        directory.resolve("Library/tools").toFile().mkdirs()
        val consumer =
            directory.resolve("Consumer.x").toFile().apply {
                writeText("module Consumer { package lib import Library; Int run(lib.Box box) = 1 }")
            }
        val uri = library.toURI().toString()
        val server = XtcLanguageServer(hookedAdapter {})
        server.connect(mock(LanguageClient::class.java))
        try {
            server.initialize(moves()).awaited()
            server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Library", uri),
                    XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")),
                ),
            )
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))
            server.textDocumentService.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri))).awaited()
            val to = directory.resolve("Library/tools/Box.x").toUri().toString()
            val params = RenameFilesParams(listOf(FileRename(member.toURI().toString(), to)))
            val moved = if (method == "xtc/renameFiles") server.renameFilesProposal(params) else server.willRenameFiles(params)
            assertThatThrownBy { moved.awaited() }
                .rootCause()
                .isInstanceOfSatisfying(ResponseErrorException::class.java) {
                    assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.RequestFailed.value)
                    assertThat(it.responseError.message).isEqualTo(uncompiledRename(listOf("Consumer"), "move"))
                    assertThat(it.responseError.data).isEqualTo(RenameRefused.UNPROVEN)
                }
        } finally {
            server.shutdown().awaited()
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
            server.initialize(parameters()).awaited()
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
                    .awaited()
            assertThat(references.map { it.uri }).containsExactlyInAnyOrder(uri, consumerUri)
            val params = RenameParams(document, at, "choose")
            val edit = requireNotNull(documents.rename(params).awaited())
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
                .awaited()
            val openEdit = requireNotNull(documents.rename(params).awaited())
            val consumerEdit =
                openEdit.documentChanges.single { it.left.textDocument.uri == consumerUri }.left
            assertThat(consumerEdit.textDocument.version).isEqualTo(3)
            assertThat(consumerEdit.edits).hasSize(2)
            assertThat(consumer.readText()).isEqualTo(use)
        } finally {
            server.shutdown().awaited()
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
                    .awaited()
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
                    ).awaited(),
            ).isNull()
        } finally {
            server.shutdown().awaited()
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
            server.initialize(parameters()).awaited()
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
                    .awaited()
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
            server.shutdown().awaited()
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
            server.initialize(parameters()).awaited()
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
                    ).awaited()
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
            server.shutdown().awaited()
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
                server.initialize(params).awaited()
                val documents = server.textDocumentService
                val uri = root.toURI().toString()
                documents.didOpen(
                    DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 7, root.readText())),
                )
                documents
                    .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                    .awaited()
                val edit =
                    documents
                        .rename(
                            RenameParams(
                                TextDocumentIdentifier(uri),
                                Position(0, root.readText().indexOf("Item")),
                                "Renamed",
                            ),
                        ).awaited()
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
                server.shutdown().awaited()
            }
        }
    }

    @Test
    fun `a public rename says modules outside the workspace are not changed, unless one of them is configured`() {
        directory = directory.toRealPath()
        val library = directory.resolve("library/Library.x").toFile().apply { parentFile.mkdirs() }
        val inside = directory.resolve("library/Consumer.x").toFile()
        val outside = directory.resolve("app/App.x").toFile().apply { parentFile.mkdirs() }
        val text = "module Library { class Box { Int pick(Int value) = value + hidden(); private Int hidden() = 1; } }"
        library.writeText(text)
        inside.writeText("module Consumer { package lib import Library; Int run(lib.Box box) = box.pick(1); }")
        outside.writeText("module App { package lib import Library; Int run(lib.Box box) = box.pick(value = 2); }")
        val uri = library.toURI().toString()
        val modules =
            listOf(
                XdkSourceModule("Library", uri),
                XdkSourceModule("Consumer", inside.toURI().toString(), setOf("Library")),
            )
        val client = mock(LanguageClient::class.java)
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(client)
        try {
            server.initialize(workspace(library.parentFile)).awaited()
            server.replaceCompilerSourceModules(modules)
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))

            fun notes(
                anchor: String,
                name: String,
            ): List<MessageParams> {
                val before = messages(client).size
                requireNotNull(
                    server.renameProposal(RenameParams(TextDocumentIdentifier(uri), Position(0, text.indexOf(anchor)), name)).awaited(),
                )
                return messages(client).drop(before)
            }
            // sent before the answer, so the client has it as it applies the edit: none came after
            assertThat(notes("pick", "choose")).containsExactly(
                MessageParams(
                    MessageType.Info,
                    "Ecstasy: modules outside the workspace that use 'pick' are not changed by this rename. To include them, $ADD_CONSUMERS.",
                ),
            )
            // a named argument elsewhere may use a public method's parameter
            assertThat(notes("value", "count").map { it.message }).singleElement().asString().contains("use 'value' are not changed")
            assertThat(notes("hidden", "secret")).isEmpty()
            // the consumer that Add consumers from folders finds outside the workspace is renamed too
            server.replaceCompilerSourceModules(modules + XdkSourceModule("App", outside.toURI().toString(), setOf("Library")))
            assertThat(notes("pick", "choose")).isEmpty()
            assertThat(library.readText()).isEqualTo(text)
        } finally {
            server.shutdown().awaited()
        }
    }

    @Test
    fun `a rename says which string literals and comments of the modules it changes spell the old name`() {
        directory = directory.toRealPath()
        val library = directory.resolve("library/Library.x").toFile().apply { parentFile.mkdirs() }
        val consumer = directory.resolve("library/Consumer.x").toFile()
        val other = directory.resolve("library/Other.x").toFile()
        val text = "module Library {\n    /** [pick] picks, unlike repick. */\n    class Box {\n        String pick() = \"pick\";\n    }\n}"
        library.writeText(text)
        consumer.writeText(
            "module Consumer {\n    package lib import Library;\n    // pick the box\n    String run(lib.Box box) = box.pick();\n}",
        )
        // a module the rename does not change, whose comments it does not count
        other.writeText("module Other {\n    package lib import Library;\n    // pick, which Other does not call\n}")
        val uri = library.toURI().toString()
        val client = mock(LanguageClient::class.java)
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(client)
        try {
            server.initialize(workspace(library.parentFile)).awaited()
            server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Library", uri),
                    XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")),
                    XdkSourceModule("Other", other.toURI().toString(), setOf("Library")),
                ),
            )
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))
            val params = RenameParams(TextDocumentIdentifier(uri), Position(3, text.lines()[3].indexOf("pick")), "choose")

            // what each answer sent before it: the client has it as it applies the edit
            fun notes(request: () -> Any?): List<String> {
                val before = messages(client).size
                requireNotNull(request())
                return messages(client).drop(before).map { it.message }.filterNot { "modules outside the workspace" in it }
            }
            val note =
                "Ecstasy: 1 string literal and 2 comments in the modules this rename changes spell 'pick' and are not " +
                    "changed by it: Consumer.x:3, Library.x:2, Library.x:4."
            assertThat(notes { server.renameProposal(params).awaited() }).containsExactly(note)
            assertThat(notes { server.textDocumentService.rename(params).awaited() }).containsExactly(note)
            assertThat(library.readText()).isEqualTo(text)
        } finally {
            server.shutdown().awaited()
        }
    }

    @Test
    fun `a plain rename, a move and a file rename say modules outside the workspace are not changed`() {
        directory = directory.toRealPath()
        val root = directory.resolve("App.x").toFile().apply { writeText("module App { tools.Box make() = new tools.Box(); }") }
        val box = directory.resolve("App/tools/Box.x").toFile().apply { parentFile.mkdirs() }
        val text = "class Box { Int pick(Int value) = value; private Int hidden() = 1; }"
        box.writeText(text)
        directory.resolve("App/util/Marker.x").toFile().apply {
            parentFile.mkdirs()
            writeText("class Marker {}")
        }
        val uri = box.toURI().toString()
        val client = mock(LanguageClient::class.java)
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(client)
        try {
            server
                .initialize(
                    workspace(directory.toFile()).apply {
                        capabilities.workspace.workspaceEdit.resourceOperations = listOf("rename")
                        capabilities.workspace.fileOperations = FileOperationsWorkspaceCapabilities().apply { willRename = true }
                    },
                ).awaited()
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))

            fun message(
                operation: String,
                name: String,
            ) = MessageParams(
                MessageType.Info,
                "Ecstasy: modules outside the workspace that use '$name' are not changed by this $operation. To include them, " +
                    "$ADD_CONSUMERS.",
            )

            // what each answer sent before it: the client has it as it applies the edit
            fun notes(request: () -> Any?): List<MessageParams> {
                val before = messages(client).size
                requireNotNull(request())
                return messages(client).drop(before)
            }

            fun renamed(anchor: String) = RenameParams(TextDocumentIdentifier(uri), Position(0, text.indexOf(anchor)), "chosen")
            // textDocument/rename, which neither editor sends, says so as xtc/rename does
            assertThat(notes { server.textDocumentService.rename(renamed("pick")).awaited() }).containsExactly(message("rename", "pick"))
            assertThat(notes { server.textDocumentService.rename(renamed("hidden")).awaited() }).isEmpty()
            val moved = directory.resolve("App/util/Box.x").toFile()
            val move = RenameFilesParams(listOf(FileRename(uri, moved.toURI().toString())))
            assertThat(notes { server.renameFilesProposal(move).awaited() }).containsExactly(message("move", "Box"))
            assertThat(notes { server.willRenameFiles(move).awaited() }).containsExactly(message("move", "Box"))
            val rename = RenameFilesParams(listOf(FileRename(uri, directory.resolve("App/tools/Crate.x").toUri().toString())))
            assertThat(notes { server.renameFilesProposal(rename).awaited() }).containsExactly(message("rename", "Box"))
            assertThat(root.readText()).contains("tools.Box")
            assertThat(moved).doesNotExist()
        } finally {
            server.shutdown().awaited()
        }
    }

    @Test
    fun `a public safe delete runs the command that says modules outside the workspace are not changed`() {
        directory = directory.toRealPath()
        val text = "module Delete { class Box { Int unused() { return 1; } private Int hidden() { return 2; } } }"
        val file = directory.resolve("Delete.x").toFile().apply { writeText(text) }
        val uri = file.toURI().toString()
        val client = mock(LanguageClient::class.java)
        val server = XtcLanguageServer(XdkAdapter())
        server.connect(client)
        try {
            // a client that resolves an action's edit when it is chosen, as LSP4IJ and VS Code do
            val parameters =
                workspace(directory.toFile()).apply {
                    capabilities.textDocument.codeAction.apply {
                        dataSupport = true
                        resolveSupport = CodeActionResolveSupportCapabilities(listOf("edit"))
                    }
                }
            val capabilities = server.initialize(parameters).awaited().capabilities
            assertThat(capabilities.executeCommandProvider.commands).containsExactly(ClientPresentation.OUTSIDE_USES)
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))

            fun deletion(name: String): CodeAction {
                val at = Position(0, text.indexOf(name))
                return server.textDocumentService
                    .codeAction(CodeActionParams(TextDocumentIdentifier(uri), Range(at, at), CodeActionContext(emptyList())))
                    .awaited()
                    .map { it.right }
                    .single { it.title.startsWith("Safely delete") }
            }
            val publicMember = deletion("unused")
            val command = requireNotNull(publicMember.command)
            assertThat(command.command).isEqualTo(ClientPresentation.OUTSIDE_USES)
            val message =
                "Ecstasy: modules outside the workspace that use 'unused' are not changed by this safe delete. To include them, $ADD_CONSUMERS."
            assertThat(command.arguments).containsExactly(message)
            // LSP4IJ resolves no action with a command (UP85): it carries its edit, which a private member's leaves for resolve
            assertThat(publicMember.edit).isNotNull()
            assertThat(publicMember.data).isNull()
            val privateMember = deletion("hidden")
            assertThat(privateMember.command).isNull()
            assertThat(privateMember.edit).isNull()
            assertThat(privateMember.data).isNotNull()
            // listing it said nothing; its command, which the client runs after the edit, does
            assertThat(messages(client)).isEmpty()
            server.workspaceService.executeCommand(ExecuteCommandParams(command.command, command.arguments)).awaited()
            assertThat(messages(client)).containsExactly(MessageParams(MessageType.Info, message))
            assertThat(file.readText()).isEqualTo(text)
        } finally {
            server.shutdown().awaited()
        }
    }

    /** The messages that [client] was shown, in order. */
    private fun messages(client: LanguageClient): List<MessageParams> =
        mockingDetails(client)
            .invocations
            .filter { it.method.name == "showMessage" }
            .map { it.arguments.single() as MessageParams }

    /** An editor's parameters with the one workspace [folder], whose client adds consumers as [ADD_CONSUMERS] says. */
    private fun workspace(folder: File) =
        parameters().apply {
            workspaceFolders = listOf(WorkspaceFolder(folder.toURI().toString(), folder.name))
            initializationOptions = mapOf("xtcAddConsumers" to ADD_CONSUMERS)
        }

    /** [parameters] of a client that moves files with their edits, as both editors do. */
    private fun moves() =
        parameters().apply {
            capabilities.workspace.workspaceEdit.resourceOperations = listOf("rename")
            capabilities.workspace.fileOperations = FileOperationsWorkspaceCapabilities().apply { willRename = true }
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

    private companion object {
        /** How the test's client says its user adds consumers. */
        const val ADD_CONSUMERS = "use Add consumers from folders"
    }
}
