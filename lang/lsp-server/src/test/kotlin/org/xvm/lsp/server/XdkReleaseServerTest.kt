package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentContentCapabilities
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentContentParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceEditCapabilities
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.hookedAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.protocol.RenameProposal
import org.xvm.testing.awaitUntil
import org.xvm.testing.awaited
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks, at the server boundary, the requests that a close or an open which changes nothing they read must not end: the
 * editor's release of a document with its text as the file has it, outside the graph or a member of it, as VS Code
 * releases one that no editor shows some minutes after an extension or a navigation opened it, a second didClose of a
 * document that is not open, as the language client sends for one that the playbook's cleanup closed itself, and the open
 * of a member of the graph with its file's text, as VS Code opens one for a peek or a search. Each request's graph query
 * queues behind the graph's held compile, the event comes, and then the compile: by ordering, not timing.
 */
class XdkReleaseServerTest {
    @TempDir lateinit var directory: Path

    /** A workspace-wide request, as a playbook case makes it, and what its answer must be when nothing it reads changed. */
    enum class Request {
        /** X143's ordinary workspace symbols. */
        SYMBOLS {
            override fun ask(session: Session) = session.server.workspaceService.symbol(WorkspaceSymbolParams("Box"))

            override fun check(
                session: Session,
                answer: Any?,
            ) {
                @Suppress("UNCHECKED_CAST")
                val symbols = (answer as Either<List<SymbolInformation>, *>).left
                assertThat(symbols.map { it.name }).contains("Box")
            }
        },

        /** X143's partial workspace symbols: the batches, and an empty final answer. */
        PARTIAL_SYMBOLS {
            override fun ask(session: Session) =
                session.server.workspaceService.symbol(
                    WorkspaceSymbolParams("Box").apply { partialResultToken = Either.forLeft("symbols") },
                )

            override fun check(
                session: Session,
                answer: Any?,
            ) {
                @Suppress("UNCHECKED_CAST")
                assertThat((answer as Either<List<SymbolInformation>, *>).left).isEmpty()
                assertThat(session.progress.flatMap { it.value.right as List<*> }.map { (it as SymbolInformation).name })
                    .contains("Box")
            }
        },

        /** X357's Organize Imports, of the root's unused import. */
        ORGANIZE_IMPORTS {
            override fun ask(session: Session) =
                session.server.textDocumentService.codeAction(
                    CodeActionParams(
                        TextDocumentIdentifier(session.uri("Node0")),
                        Range(Position(0, 0), Position(0, 0)),
                        CodeActionContext(emptyList(), listOf(CodeActionKind.SourceOrganizeImports)),
                    ),
                )

            override fun check(
                session: Session,
                answer: Any?,
            ) {
                @Suppress("UNCHECKED_CAST")
                val actions = (answer as List<Either<*, CodeAction>>).map { it.right.kind }
                assertThat(actions).containsExactly(CodeActionKind.SourceOrganizeImports)
            }
        },

        /** X352's rename, as VS Code's extension asks for it with xtc/rename. */
        RENAME {
            override fun ask(session: Session): CompletableFuture<*> {
                val root = SOURCES.getValue("Node0").lines()
                val line = root.indexOfFirst { "class Box" in it }
                return session.server.renameProposal(
                    RenameParams(TextDocumentIdentifier(session.uri("Node0")), Position(line, root[line].indexOf("Box")), "Crate"),
                )
            }

            override fun check(
                session: Session,
                answer: Any?,
            ) {
                assertThat((answer as RenameProposal).edit.documentChanges).isNotEmpty()
            }
        },

        /** X172's move proposal, which xtc/renameFiles asks for: the server answers it, here with the move's edits. */
        MOVE {
            override fun ask(session: Session) =
                session.server.renameFilesProposal(
                    RenameFilesParams(listOf(FileRename(session.uri("Node0/Part"), session.uri("Node0/Crate")))),
                )

            override fun check(
                session: Session,
                answer: Any?,
            ) {
                assertThat((answer as RenameProposal).edit.documentChanges).isNotEmpty()
            }
        },
        ;

        abstract fun ask(session: Session): CompletableFuture<*>

        abstract fun check(
            session: Session,
            answer: Any?,
        )
    }

    /**
     * A close or an open as it comes while the requests wait, which they outlast unless it [overtakes] them; one that
     * [compilesNothing] submits no compile, and one that [publishesNothing] publishes no diagnostics.
     */
    enum class Event(
        val overtakes: Boolean = false,
        val compilesNothing: Boolean = false,
        val publishesNothing: Boolean = false,
    ) {
        /** VS Code's release of a document outside the graph, with its text as the file has it. */
        RELEASE {
            override fun of(session: Session) = session.uri("Other")
        },

        /** VS Code's release of the ecstasy-library content of String's source, which Go to Definition opened. */
        LIBRARY {
            override fun prepare(session: Session) = session.openLibrary()

            override fun of(session: Session) = session.library
        },

        /** A second didClose: of the graph's consumer, which no editor has open, as of one the playbook closed itself. */
        NOT_OPEN {
            override fun of(session: Session) = session.uri("Consumer")
        },

        /** The close of a document outside the graph with unsaved text, which gives the analyses its file's text again. */
        UNSAVED(overtakes = true) {
            override fun prepare(session: Session) = session.edit("Other")

            override fun of(session: Session) = session.uri("Other")
        },

        /**
         * VS Code's release of Lid, a member of the graph's module Node0, whose root the editor has open, with its text as
         * the file has it: as of an earlier case's file of a discovered graph. Node0's analysis serves its root still.
         */
        MEMBER(compilesNothing = true, publishesNothing = true) {
            override fun prepare(session: Session) = session.open("Node0/Lid")

            override fun of(session: Session) = session.uri("Node0/Lid")

            override fun after(session: Session) {
                assertThat(session.adapter.getCachedResult(of(session))).describedAs("Node0's analysis").isNotNull()
            }
        },

        /**
         * VS Code's release of Consumer, the last open document of its module, with its text as the file has it: its
         * module's analysis goes, and its diagnostics, as no open document publishes them, but nothing else.
         */
        LAST_MEMBER(compilesNothing = true) {
            override fun prepare(session: Session) = session.open("Consumer")

            override fun of(session: Session) = session.uri("Consumer")

            override fun after(session: Session) {
                assertThat(session.adapter.getCachedResult(of(session))).describedAs("Consumer's analysis").isNull()
            }
        },

        /** The open of Lid with its text as the file has it, as VS Code opens a document for a peek or a search. */
        OPEN {
            override fun of(session: Session) = session.uri("Node0/Lid")

            override fun act(session: Session) = session.server.textDocumentService.didOpen(session.item("Node0/Lid"))
        },

        /** VS Code's release of Lid, its open again with its file's text, and its release again, as of an editor's tab. */
        RELEASED_TWICE {
            override fun prepare(session: Session) = session.open("Node0/Lid")

            override fun of(session: Session) = session.uri("Node0/Lid")

            override fun act(session: Session) {
                super.act(session)
                session.server.textDocumentService.didOpen(session.item("Node0/Lid"))
                super.act(session)
            }
        },

        /** VS Code's release of Lid, and its open again, as the language client opens a document VS Code holds. */
        REOPEN {
            override fun prepare(session: Session) = session.open("Node0/Lid")

            override fun of(session: Session) = session.uri("Node0/Lid")

            override fun act(session: Session) {
                super.act(session)
                session.server.textDocumentService.didOpen(session.item("Node0/Lid"))
            }
        },

        /** The close of Lid with unsaved text, which gives the analyses its file's text again. */
        MEMBER_UNSAVED(overtakes = true) {
            override fun prepare(session: Session) {
                session.open("Node0/Lid")
                session.edit("Node0/Lid", "class Lid {\n    Int depth = 2;\n}\n")
            }

            override fun of(session: Session) = session.uri("Node0/Lid")
        },

        /** The close of Lid with the text that its file had, which changed on disk without a watcher's event since. */
        MEMBER_CHANGED(overtakes = true) {
            override fun prepare(session: Session) {
                session.open("Node0/Lid")
                session.write("Node0/Lid", "class Lid {\n    Int depth = 1;\n}\n")
            }

            override fun of(session: Session) = session.uri("Node0/Lid")
        },

        /**
         * VS Code's release of Consumer with its file's text, whose file then changes on disk, which no watcher reports:
         * the requests' graph queries read the document's text, which the file no longer has.
         */
        RELEASED_CHANGED(overtakes = true) {
            override fun prepare(session: Session) = session.open("Consumer")

            override fun of(session: Session) = session.uri("Consumer")

            override fun act(session: Session) {
                super.act(session)
                session.write("Consumer", "\n" + SOURCES.getValue("Consumer"))
            }
        },

        /** As [RELEASED_CHANGED], with the watcher's report of the change. */
        RELEASED_WATCHED(overtakes = true) {
            override fun prepare(session: Session) = session.open("Consumer")

            override fun of(session: Session) = session.uri("Consumer")

            override fun act(session: Session) {
                RELEASED_CHANGED.act(session)
                session.watched("Consumer")
            }
        },

        /** VS Code's release of Consumer, whose file then changes on disk, and its open again with the file's new text. */
        REOPENED_CHANGED(overtakes = true) {
            override fun prepare(session: Session) = session.open("Consumer")

            override fun of(session: Session) = session.uri("Consumer")

            override fun act(session: Session) {
                RELEASED_CHANGED.act(session)
                session.server.textDocumentService.didOpen(
                    DidOpenTextDocumentParams(TextDocumentItem(of(session), "xtc", 2, "\n" + SOURCES.getValue("Consumer"))),
                )
            }
        },

        /** The open of Lid with text that its file does not have. */
        OPEN_UNSAVED(overtakes = true) {
            override fun of(session: Session) = session.uri("Node0/Lid")

            override fun act(session: Session) =
                session.server.textDocumentService.didOpen(
                    DidOpenTextDocumentParams(TextDocumentItem(of(session), "xtc", 1, "class Lid {\n    class Unsaved {}\n}\n")),
                )
        },
        ;

        /** Readies the event's document, before the requests are asked. */
        open fun prepare(session: Session) {}

        abstract fun of(session: Session): String

        /** Checks what the event leaves of the analyses, as it has come. */
        open fun after(session: Session) {}

        /** The event itself: by default the close of [of]'s document. */
        open fun act(session: Session) =
            session.server.textDocumentService.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(of(session))))
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class)
    fun `a workspace request outlasts the release of a document outside the graph`(request: Request) = run(request, Event.RELEASE)

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class)
    fun `a workspace request outlasts the release of a library's source`(request: Request) = run(request, Event.LIBRARY)

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class)
    fun `a workspace request outlasts a didClose of a document that is not open`(request: Request) = run(request, Event.NOT_OPEN)

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class)
    fun `the close of a document with unsaved text overtakes a workspace request`(request: Request) = run(request, Event.UNSAVED)

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class)
    fun `a workspace request outlasts the release of a member of the graph, which retires nothing`(request: Request) =
        run(request, Event.MEMBER)

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class)
    fun `a workspace request outlasts the release of a module's last open document, which retires nothing`(request: Request) =
        run(request, Event.LAST_MEMBER)

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class)
    fun `a workspace request outlasts the open of a member of the graph with its file's text`(request: Request) = run(request, Event.OPEN)

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class)
    fun `a workspace request outlasts a member's release and its open again with its file's text`(request: Request) =
        run(request, Event.REOPEN)

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class, names = ["SYMBOLS", "ORGANIZE_IMPORTS", "RENAME", "MOVE"])
    fun `a workspace request asked after a member opened with its file's text, while another waits, answers`(request: Request) =
        run(request, Event.OPEN, again = true)

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class)
    fun `a workspace request outlasts a member's release, its open again and its release again, with its file's text`(request: Request) =
        run(request, Event.RELEASED_TWICE)

    @Test
    fun `a module's analysis publishes for its open documents though a member closed with its file's text during it`() {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        Session(directory).use { session ->
            session.open("Node0/Lid")
            // Node0's analysis of its edit holds, and Lid closes as it does, which analyzes nothing again
            session.hold.set(true)
            session.server.textDocumentService.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(session.uri("Node0"), 2),
                    listOf(
                        TextDocumentContentChangeEvent(
                            SOURCES.getValue("Node0").replace("class Box {}", "class Box {}\n    class Crate {}"),
                        ),
                    ),
                ),
            )
            assertThat(session.entered.awaited()).isTrue()
            Event.MEMBER.act(session)
            session.release.countDown()
            awaitUntil("Node0's analysis of its edit publishes") {
                session.published.any { it.uri == session.uri("Node0") && it.version == 2 }
            }
        }
    }

    @ParameterizedTest(name = "{1} {0}")
    @CsvSource(
        "SYMBOLS,MEMBER_UNSAVED",
        "RENAME,MEMBER_UNSAVED",
        "SYMBOLS,MEMBER_CHANGED",
        "MOVE,MEMBER_CHANGED",
        "SYMBOLS,OPEN_UNSAVED",
        "ORGANIZE_IMPORTS,OPEN_UNSAVED",
        "RENAME,RELEASED_CHANGED",
        "SYMBOLS,RELEASED_WATCHED",
        "SYMBOLS,REOPENED_CHANGED",
        "ORGANIZE_IMPORTS,REOPENED_CHANGED",
    )
    fun `a member's close or open with text that its file does not have, or a released member's change, overtakes a workspace request`(
        request: Request,
        event: Event,
    ) = run(request, event)

    /**
     * [request], asked as the graph's held compile holds its graph query, then [event]; or, [again], the same request
     * asked once more after the event, while the first one waits and so keeps the event remembered: its graph query takes
     * the first one's place, and the event's document is one of those it starts with.
     */
    private fun run(
        request: Request,
        event: Event,
        again: Boolean = false,
    ) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        Session(directory).use { session ->
            event.prepare(session)
            // the graph's diagnostics, which an editor pulls, compile the closed consumer first, and the compile holds
            session.hold.set(true)
            session.adapter.workspaceDiagnosticsAsync()
            assertThat(session.entered.awaited()).isTrue()
            val first = request.ask(session)
            awaitUntil("$request queues its graph query behind the held compile") {
                @Suppress("UNCHECKED_CAST")
                (session.adapter.compilerQueueSnapshot()["queuedJobs"] as List<String>).any { "project-" in it && "DIAGNOSTICS" !in it }
            }
            assertThat(first).describedAs("$request waits for the held compile").isNotDone()
            val submitted = session.adapter.compilerQueueSnapshot()["submittedTotal"]
            val published = session.published.size
            event.act(session)
            event.after(session)
            // no analysis of the module again, and no diagnostics of it again, which its root still publishes
            if (event.compilesNothing) assertThat(session.adapter.compilerQueueSnapshot()["submittedTotal"]).isEqualTo(submitted)
            if (event.publishesNothing) assertThat(session.published.drop(published)).isEmpty()
            val answer = if (again) request.ask(session) else first
            session.release.countDown()
            if (event.overtakes) {
                assertThatThrownBy { answer.awaited() }
                    .rootCause()
                    .isInstanceOfSatisfying(ResponseErrorException::class.java) {
                        assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.ContentModified.value)
                    }
            } else {
                request.check(session, answer.awaited())
            }
        }
    }

    /**
     * A server with the graph of [SOURCES]' Node0 and its consumer Consumer, whose root Node0 the editor has open, and
     * Other, a module outside the graph, as an earlier case's file that the editor still has open, both with their
     * text as the files have it; the next module compile after [hold] is set holds until [release]. [openLibrary] opens
     * [library], String's source, as VS Code does after Go to Definition on Other's String. [published] has the
     * diagnostics that the server published, in order.
     */
    class Session(
        private val directory: Path,
    ) : AutoCloseable {
        val hold = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val progress = CopyOnWriteArrayList<ProgressParams>()
        val published = CopyOnWriteArrayList<PublishDiagnosticsParams>()
        val adapter =
            hookedAdapter {
                if (hold.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.awaited())
                }
            }
        val server = XtcLanguageServer(adapter)
        lateinit var library: String

        init {
            SOURCES.forEach { (name, text) ->
                directory
                    .resolve("$name.x")
                    .toFile()
                    .apply { parentFile.mkdirs() }
                    .writeText(text)
            }
            val client = mock(LanguageClient::class.java)
            doAnswer {
                progress += it.getArgument<ProgressParams>(0)
                null
            }.`when`(client)
                .notifyProgress(any())
            doAnswer {
                published += it.getArgument<PublishDiagnosticsParams>(0)
                null
            }.`when`(client)
                .publishDiagnostics(any())
            server.connect(client)
            server
                .initialize(
                    InitializeParams().apply {
                        capabilities =
                            ClientCapabilities().apply {
                                textDocument = editorInitializeParams().capabilities.textDocument
                                workspace =
                                    WorkspaceClientCapabilities().apply {
                                        workspaceEdit =
                                            WorkspaceEditCapabilities().apply {
                                                documentChanges = true
                                                resourceOperations = listOf("rename")
                                            }
                                        fileOperations = FileOperationsWorkspaceCapabilities().apply { willRename = true }
                                        textDocumentContent = TextDocumentContentCapabilities()
                                    }
                            }
                    },
                ).awaited()
            server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Node0", uri("Node0")),
                    XdkSourceModule("Consumer", uri("Consumer"), setOf("Node0")),
                ),
            )
            listOf("Node0", "Other").forEach(::open)
        }

        /** The open of [name]'s document with its text as the file has it, as the editor sends it. */
        fun item(name: String) = DidOpenTextDocumentParams(TextDocumentItem(uri(name), "xtc", 1, SOURCES.getValue(name)))

        /** Opens [name]'s document with its file's text, and waits for its analysis. */
        fun open(name: String) {
            server.textDocumentService.didOpen(item(name))
            server.textDocumentService.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri(name)))).awaited()
        }

        /** The watcher's report that [name]'s file changed. */
        fun watched(name: String) =
            server.workspaceService.didChangeWatchedFiles(
                DidChangeWatchedFilesParams(listOf(FileEvent(uri(name), FileChangeType.Changed))),
            )

        /** Writes [text] to [name]'s file, as an editor of another tool does, which no watcher reports. */
        fun write(
            name: String,
            text: String,
        ) = directory.resolve("$name.x").toFile().writeText(text)

        fun openLibrary() {
            val other = SOURCES.getValue("Other")
            library =
                server.textDocumentService
                    .definition(DefinitionParams(TextDocumentIdentifier(uri("Other")), position(other, "String")))
                    .awaited()
                    .left
                    .single()
                    .uri
            val text =
                server.workspaceService
                    .textDocumentContent(TextDocumentContentParams(library))
                    .awaited()
                    .text
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(library, "xtc", 1, text)))
            server.textDocumentService.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(library))).awaited()
        }

        /** Gives [name]'s document [text], which its file does not have, and waits for its analysis. */
        fun edit(
            name: String,
            text: String = SOURCES.getValue(name).replace("}\n}", "}\n    class Unsaved {}\n}"),
        ) {
            server.textDocumentService.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri(name), 2),
                    listOf(TextDocumentContentChangeEvent(text)),
                ),
            )
            server.textDocumentService.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri(name)))).awaited()
        }

        private fun position(
            text: String,
            word: String,
        ): Position {
            val lines = text.lines()
            val line = lines.indexOfFirst { word in it }
            return Position(line, lines[line].indexOf(word))
        }

        fun uri(name: String): String =
            directory
                .resolve("$name.x")
                .toFile()
                .toURI()
                .toString()

        override fun close() {
            release.countDown()
            server.shutdown().awaited()
        }
    }

    private companion object {
        val SOURCES =
            mapOf(
                "Node0" to "module Node0 {\n    import ecstasy.collections.ReverseList;\n\n    class Box {}\n}\n",
                "Node0/Part" to "class Part {}\n",
                "Node0/Lid" to "class Lid {\n    Int depth = 0;\n}\n",
                "Consumer" to "module Consumer {\n    package lib import Node0;\n    class Child extends lib.Box {}\n}\n",
                "Other" to "module Other {\n    class Extract {\n        String text = \"value\";\n    }\n}\n",
            )
    }
}
