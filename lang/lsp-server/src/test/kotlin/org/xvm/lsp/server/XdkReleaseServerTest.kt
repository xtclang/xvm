package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticCapabilities
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
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
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceDiagnosticParams
import org.eclipse.lsp4j.WorkspaceEdit
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
import org.xvm.lsp.adapter.RenameOvertaken
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
import java.util.concurrent.atomic.AtomicReference

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

    /**
     * A workspace-wide request, as a playbook case makes it, asked [of] a document, and what its answer must be when
     * nothing it reads changed.
     */
    enum class Request(
        val of: String? = null,
    ) {
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
        ORGANIZE_IMPORTS("Node0") {
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
        RENAME("Node0") {
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
        MOVE("Node0/Part") {
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
     * A close or an open as it comes while the requests wait, which they outlast unless it [overtakes] them, as their
     * ContentModified then says; one that [compilesNothing] submits no compile, and one that [publishesNothing] publishes
     * no diagnostics.
     */
    enum class Event(
        val overtakes: String? = null,
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
        UNSAVED(overtakes = "Other.x closed with text that its file does not have") {
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
        MEMBER_UNSAVED(overtakes = "Lid.x closed with text that its file does not have") {
            override fun prepare(session: Session) {
                session.open("Node0/Lid")
                session.edit("Node0/Lid", "class Lid {\n    Int depth = 2;\n}\n")
            }

            override fun of(session: Session) = session.uri("Node0/Lid")
        },

        /** The close of Lid with the text that its file had, which changed on disk without a watcher's event since. */
        MEMBER_CHANGED(overtakes = "Lid.x closed with text that its file does not have") {
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
        RELEASED_CHANGED(overtakes = RenameOvertaken.REASON) {
            override fun prepare(session: Session) = session.open("Consumer")

            override fun of(session: Session) = session.uri("Consumer")

            override fun act(session: Session) {
                super.act(session)
                session.write("Consumer", "\n" + SOURCES.getValue("Consumer"))
            }
        },

        /** As [RELEASED_CHANGED], with the watcher's report of the change. */
        RELEASED_WATCHED(overtakes = "Consumer.x changed on disk") {
            override fun prepare(session: Session) = session.open("Consumer")

            override fun of(session: Session) = session.uri("Consumer")

            override fun act(session: Session) {
                RELEASED_CHANGED.act(session)
                session.watched("Consumer")
            }
        },

        /** VS Code's release of Consumer, whose file then changes on disk, and its open again with the file's new text. */
        REOPENED_CHANGED(overtakes = "Consumer.x opened with text that its module's analyses did not read") {
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
        OPEN_UNSAVED(overtakes = "Lid.x opened with text that its file does not have") {
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
        "MOVE,RELEASED_CHANGED",
        "SYMBOLS,RELEASED_WATCHED",
        "SYMBOLS,REOPENED_CHANGED",
        "ORGANIZE_IMPORTS,REOPENED_CHANGED",
    )
    fun `a member's close or open with text that its file does not have, or a released member's change, overtakes a workspace request`(
        request: Request,
        event: Event,
    ) = run(request, event)

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class, names = ["SYMBOLS", "PARTIAL_SYMBOLS", "ORGANIZE_IMPORTS"])
    fun `a workspace request whose graph query a new graph retires answers ContentModified, not a cancellation`(request: Request) =
        waiting(request) { session, answer ->
            // a module that no editor has open joins the graph: the graph's queries go, and no open document changes,
            // which the request's own checks would find
            session.join("Extra", "module Extra {}\n")
            session.release.countDown()
            assertContentModified(answer, XtcTextDocumentService.GRAPH_CHANGED)
        }

    @Test
    fun `a request whose graph query a newer one of the same kind takes the place of says so`() =
        waiting(Request.ORGANIZE_IMPORTS) { session, first ->
            // Organize Imports again, of a diagnostic: another question of the same range and kinds
            val second =
                session.server.textDocumentService.codeAction(
                    CodeActionParams(
                        TextDocumentIdentifier(session.uri("Node0")),
                        Range(Position(0, 0), Position(0, 0)),
                        CodeActionContext(
                            listOf(Diagnostic(Range(Position(1, 4), Position(1, 10)), "unused")),
                            listOf(CodeActionKind.SourceOrganizeImports),
                        ),
                    ),
                )
            assertContentModified(first, "A newer textDocument/codeAction request took the place of its work")
            session.release.countDown()
            Request.ORGANIZE_IMPORTS.check(session, second.awaited())
        }

    /**
     * A close or an open of the document that a request is asked of, [name], as it comes while the request waits, which
     * the request outlasts unless the event [overtakes] it, as its ContentModified then says of the document's file.
     */
    enum class Own(
        val overtakes: ((file: String) -> String)? = null,
    ) {
        /** VS Code's release of the document with its file's text, as of a peek's, while another member stays open. */
        RELEASE {
            override fun prepare(
                session: Session,
                name: String,
            ) = session.open(if (name == "Node0") "Node0/Lid" else name)

            override fun act(
                session: Session,
                name: String,
            ) = session.close(name)
        },

        /** The document's open with its file's text, as VS Code opens a file that it moves, which no editor had open. */
        OPEN {
            override fun prepare(
                session: Session,
                name: String,
            ) {
                if (name == "Node0") session.close(name)
            }

            override fun act(
                session: Session,
                name: String,
            ) = session.server.textDocumentService.didOpen(session.item(name))
        },

        /** Its release and its open again, with its file's text, as the language client opens a document VS Code holds. */
        REOPEN {
            override fun prepare(
                session: Session,
                name: String,
            ) = RELEASE.prepare(session, name)

            override fun act(
                session: Session,
                name: String,
            ) {
                RELEASE.act(session, name)
                OPEN.act(session, name)
            }
        },

        /** Its close with unsaved text, which gives the analyses its file's text again. */
        UNSAVED({ "$it closed with text that its file does not have" }) {
            override fun prepare(
                session: Session,
                name: String,
            ) {
                if (name != "Node0") session.open(name)
                session.edit(name, SOURCES.getValue(name).replace(Regex("}\n$"), "    class Unsaved {}\n}\n"))
            }

            override fun act(
                session: Session,
                name: String,
            ) = session.close(name)
        },

        /** Its open with text that its file does not have. */
        OPEN_UNSAVED({ "$it opened with text that its file does not have" }) {
            override fun act(
                session: Session,
                name: String,
            ) = session.server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(session.uri(name), "xtc", 1, "class Part {\n    class Unsaved {}\n}\n")),
            )
        },
        ;

        /** Readies the document [name], before the request is asked of it. */
        open fun prepare(
            session: Session,
            name: String,
        ) {}

        abstract fun act(
            session: Session,
            name: String,
        )
    }

    @ParameterizedTest(name = "{1} {0}")
    @CsvSource(
        "ORGANIZE_IMPORTS,RELEASE",
        "RENAME,RELEASE",
        "MOVE,RELEASE",
        "ORGANIZE_IMPORTS,OPEN",
        "MOVE,OPEN",
        "ORGANIZE_IMPORTS,REOPEN",
        "RENAME,REOPEN",
        "MOVE,REOPEN",
        "ORGANIZE_IMPORTS,UNSAVED",
        "MOVE,UNSAVED",
        "MOVE,OPEN_UNSAVED",
    )
    fun `a request outlasts the release or the open of the document it is asked of, unless its text is not its file's`(
        request: Request,
        event: Own,
    ) {
        val name = checkNotNull(request.of)
        waiting(request, { event.prepare(it, name) }) { session, answer ->
            event.act(session, name)
            session.release.countDown()
            event.overtakes?.let { assertContentModified(answer, it("${name.substringAfterLast('/')}.x")) }
                ?: request.check(session, answer.awaited())
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("Node0/Lid,depth", "Consumer,Child")
    fun `a query of a member outlasts its release with its file's text, also as its module's last open document`(
        name: String,
        word: String,
    ) = releasing(name, word) { session, hover ->
        session.close(name)
        session.release.countDown()
        assertThat(
            hover
                .awaited()
                ?.contents
                ?.right
                ?.value,
        ).contains(word)
    }

    @Test
    fun `a query of a released member ends as its module's text changes, and says so`() =
        releasing("Node0/Lid", "depth") { session, hover ->
            session.close("Node0/Lid")
            session.change("Node0", SOURCES.getValue("Node0").replace("class Box {}", "class Box {}\n    class Crate {}"))
            assertContentModified(hover, "The text of Node0.x changed")
        }

    @Test
    fun `a query of a document outside the graph ends as it is released, which leaves it no analysis, and says so`() =
        releasing("Other", "Extract", reopen = true) { session, hover ->
            session.close("Other")
            assertContentModified(hover, "Other.x closed")
        }

    @Test
    fun `a completion outlasts another member's open with its file's text, which replaces their module's compile`() =
        waiting(
            "the completion",
            ask = { session ->
                val root = SOURCES.getValue("Node0").lines()
                val line = root.indexOfFirst { "class Box" in it }
                session.server.textDocumentService.completion(
                    CompletionParams(TextDocumentIdentifier(session.uri("Node0")), Position(line, root[line].indexOf('{') + 1)),
                )
            },
            queued = "cursor-",
        ) { session, completion ->
            // Lid's open with its file's text compiles Node0 again and ends the compile that the completion's probe, queued
            // behind the held compile, was made on: the probe ended with it, and the completion answered ContentModified
            session.server.textDocumentService.didOpen(session.item("Node0/Lid"))
            session.release.countDown()
            assertThat((completion.awaited() as Either<*, *>).left as List<*>).isNotEmpty()
        }

    @Test
    fun `a query of a released member outlasts the release of its module's last open document, another one`() =
        releasing("Node0/Lid", "depth") { session, hover ->
            session.close("Node0/Lid")
            // Node0 has no query of its own: the analysis that Lid's hover waits for went with it
            session.close("Node0")
            session.release.countDown()
            assertThat(
                hover
                    .awaited()
                    ?.contents
                    ?.right
                    ?.value,
            ).contains("depth")
        }

    @Test
    fun `a rename of a local of its module's last open document outlasts the document's release`() =
        waiting(
            "the rename",
            prepare = { session ->
                session.close("Node0")
                session.open("Node0/Lid")
            },
            ask = { session ->
                session.server.textDocumentService.rename(
                    RenameParams(
                        TextDocumentIdentifier(session.uri("Node0/Lid")),
                        Session.position(SOURCES.getValue("Node0/Lid"), "more"),
                        "most",
                    ),
                )
            },
            queued = "rename-proof",
        ) { session, rename ->
            // a workspace request, whose proof the module's analysis that the release dropped was made on
            session.close("Node0/Lid")
            session.release.countDown()
            val edit = rename.awaited() as WorkspaceEdit
            assertThat(edit.documentChanges.orEmpty().size + edit.changes.orEmpty().size).isPositive()
        }

    @Test
    fun `the analysis kept for a released member's query goes as the query ends`() =
        releasing("Consumer", "Child") { session, hover ->
            session.close("Consumer")
            session.release.countDown()
            assertThat(hover.awaited()).isNotNull()
            awaitUntil("Consumer's analysis, which no document has open, goes") {
                session.adapter.getCachedResult(session.uri("Consumer")) == null
            }
        }

    @Test
    fun `a closed member's query answers its file's text once the file changed on disk, as the watcher reports`() {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        Session(directory).use { session ->
            // no document of Node0 is open: a hover of Part asks for the analysis of its closed files
            session.close("Node0")
            assertThat(session.hover("Node0/Part", "Part").awaited()).isNotNull()
            val text = "class Part {\n    Int size = 1;\n}\n"
            session.write("Node0/Part", text)
            session.watched("Node0/Part")
            assertThat(
                session
                    .hover("Node0/Part", "size", text)
                    .awaited()
                    ?.contents
                    ?.right
                    ?.value,
            ).contains("size")
        }
    }

    @Test
    fun `a workspace request of a document outside the graph ends as it is released, and says so`() {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        Session(directory).use { session ->
            // Other opens again with its file's text, and its analysis holds, which the request waits for: no analysis
            // under way reads Other's file, so its release analyzes no open document again
            session.close("Other")
            session.hold.set(true)
            session.server.textDocumentService.didOpen(session.item("Other"))
            assertThat(session.entered.awaited()).isTrue()
            val actions =
                session.server.textDocumentService.codeAction(
                    CodeActionParams(
                        TextDocumentIdentifier(session.uri("Other")),
                        Range(Position(0, 0), Position(0, 0)),
                        CodeActionContext(emptyList(), listOf(CodeActionKind.SourceOrganizeImports)),
                    ),
                )
            assertThat(actions).isNotDone()
            // its analysis goes as it closes, which its Organize Imports reads, as it is not a member of the graph
            session.close("Other")
            assertContentModified(actions, "Other.x closed")
        }
    }

    @Test
    fun `a workspace pull that newer diagnostics overtake says what first made them, not a later event`() {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        Session(directory, pull = true).use { session ->
            // the analysis of Other's edit holds, which the pull waits for; Other is outside the graph, so no file of the
            // graph is one that it may read
            session.hold.set(true)
            session.change("Other", SOURCES.getValue("Other").replace("class Extract {", "class Crate {}\n    class Extract {"))
            assertThat(session.entered.awaited()).isTrue()
            val pull = session.server.workspaceDiagnostics(WorkspaceDiagnosticParams(emptyList()))
            assertThat(pull).isNotDone()
            // Consumer, which no editor has open, changes on disk: its diagnostics are new, and nothing that the pull waits
            // for changes; then the host gives the graph again as it is, which changes nothing, and a new file of Consumer
            // makes the diagnostics new again
            session.write("Consumer", "\n" + SOURCES.getValue("Consumer"))
            session.watched("Consumer")
            session.regraph()
            directory.resolve("Consumer").toFile().mkdirs()
            session.write("Consumer/Spare", "class Spare {}\n")
            session.watched("Consumer/Spare")
            session.release.countDown()
            assertContentModified(pull, "Consumer.x changed on disk")
        }
    }

    @Test
    fun `a closed file's query outlasts the end of the released member's query that its module's analysis was kept for`() =
        releasing("Node0/Lid", "depth") { session, hover ->
            session.close("Node0/Lid")
            session.close("Node0")
            // a query of Part, a closed file of Node0, which reads the analysis that Lid's hover kept, and was released by
            // no editor
            val part = session.hover("Node0/Part", "Part")
            assertThat(part).describedAs("the hover of Part waits for Node0's held compile").isNotDone()
            val submitted = session.adapter.compilerQueueSnapshot()["submittedTotal"]
            hover.cancel(true)
            // a change, as which the server lets go of the analyses that it kept for queries that have ended
            session.regraph()
            session.release.countDown()
            assertThat(
                part
                    .awaited()
                    ?.contents
                    ?.right
                    ?.value,
            ).contains("Part")
            assertThat(
                session.adapter.compilerQueueSnapshot()["submittedTotal"],
            ).describedAs("compiles of Node0 from disk").isEqualTo(submitted)
        }

    @Test
    fun `a closed file's query outlasts the release of its module's last open document, which has no query of its own`() {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        Session(directory).use { session ->
            // Lid's open with its file's text compiles Node0 again, which holds; Lid closes, and Node0 stays open
            session.hold.set(true)
            session.server.textDocumentService.didOpen(session.item("Node0/Lid"))
            assertThat(session.entered.awaited()).isTrue()
            session.close("Node0/Lid")
            val part = session.hover("Node0/Part", "Part")
            assertThat(part).describedAs("the hover of Part waits for Node0's held compile").isNotDone()
            val submitted = session.adapter.compilerQueueSnapshot()["submittedTotal"]
            session.close("Node0")
            session.release.countDown()
            assertThat(
                part
                    .awaited()
                    ?.contents
                    ?.right
                    ?.value,
            ).contains("Part")
            assertThat(
                session.adapter.compilerQueueSnapshot()["submittedTotal"],
            ).describedAs("compiles of Node0 from disk").isEqualTo(submitted)
        }
    }

    @Test
    fun `a closed file's query under way as its module's analysis is retired ends, and says what retired it`() =
        waiting(
            "the type hierarchy",
            prepare = { session ->
                // no document of Node0 is open: a hover of Part leaves the analysis of Node0's closed files
                session.close("Node0")
                assertThat(session.hover("Node0/Part", "Part").awaited()).isNotNull()
            },
            ask = { session ->
                session.server.textDocumentService.prepareTypeHierarchy(
                    TypeHierarchyPrepareParams(
                        TextDocumentIdentifier(session.uri("Node0/Part")),
                        Session.position(SOURCES.getValue("Node0/Part"), "Part"),
                    ),
                )
            },
            queued = "project-NAVIGATION",
        ) { session, hierarchy ->
            session.write("Node0/Part", "class Part {\n    Int size = 1;\n}\n")
            session.watched("Node0/Part")
            // its graph query would run now, of the file's new text, and answer
            session.release.countDown()
            assertContentModified(hierarchy, "Part.x changed on disk")
        }

    @Test
    fun `a closed file's query that went on to a member's open compile ends as that compile is retired, and says so`() {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        Session(directory).use { session ->
            // no document of Node0 is open: Part's type hierarchy asks for the analysis of Node0's closed files, whose
            // compile holds
            session.close("Node0")
            session.hold.set(true)
            val hierarchy =
                session.server.textDocumentService.prepareTypeHierarchy(
                    TypeHierarchyPrepareParams(
                        TextDocumentIdentifier(session.uri("Node0/Part")),
                        Session.position(SOURCES.getValue("Node0/Part"), "Part"),
                    ),
                )
            assertThat(session.entered.awaited()).isTrue()
            // Part opens with its file's text, whose compile of Node0 takes the held one's place: the hierarchy goes on to
            // it, as Part is open
            session.server.textDocumentService.didOpen(session.item("Node0/Part"))
            awaitUntil("Part's compile of Node0 queues behind the held one") {
                @Suppress("UNCHECKED_CAST")
                (session.adapter.compilerQueueSnapshot()["queuedJobs"] as List<String>).any { " compile " in it && it.endsWith("/Node0.x") }
            }
            // the graph's diagnostics, queued behind it, hold as they compile Consumer: the hierarchy, whose compile ends,
            // queues its graph query behind them
            session.holdModule.set("Consumer")
            session.adapter.workspaceDiagnosticsAsync()
            session.release.countDown()
            assertThat(session.moduleEntered.awaited()).isTrue()
            awaitUntil("the hierarchy queues its graph query behind the held diagnostics") {
                @Suppress("UNCHECKED_CAST")
                (session.adapter.compilerQueueSnapshot()["queuedJobs"] as List<String>).any { "project-NAVIGATION" in it }
            }
            // Part closes with its file's text, which keeps that compile for the hierarchy, and its file then changes
            session.close("Node0/Part")
            session.write("Node0/Part", "class Part {\n    Int size = 1;\n}\n")
            session.watched("Node0/Part")
            // its graph query would run now, of the file's new text, and answer
            session.moduleRelease.countDown()
            assertContentModified(hierarchy, "Part.x changed on disk")
        }
    }

    @Test
    fun `the release of an analysis kept for a query drops no compile that took its place`() =
        waiting(
            "the type hierarchy",
            prepare = { session -> session.open("Node0/Lid") },
            ask = { session ->
                session.server.textDocumentService.prepareTypeHierarchy(
                    TypeHierarchyPrepareParams(
                        TextDocumentIdentifier(session.uri("Node0/Lid")),
                        Session.position(SOURCES.getValue("Node0/Lid"), "Lid"),
                    ),
                )
            },
            queued = "project-NAVIGATION",
        ) { session, hierarchy ->
            // Lid's hierarchy, whose graph query waits behind the held compile, keeps Node0's analysis as both close
            session.close("Node0/Lid")
            session.close("Node0")
            // the kept compile goes, and a compile from disk takes its place, which a query of the adapter asks for
            session.adapter.retireClosed(setOf(checkNotNull(session.adapter.graphScope(session.uri("Node0/Part")))))
            val later = checkNotNull(session.adapter.closedAnalysisAsync(session.uri("Node0/Part")))
            // the hierarchy ends, and a change lets go of the kept analysis
            hierarchy.cancel(true)
            session.regraph()
            session.release.countDown()
            assertThat(later.awaited()).isNotNull()
        }

    @Test
    fun `two code lens requests of a document share its pending graph query, and both answer`() =
        waiting(
            "the code lenses",
            ask = { session -> session.server.textDocumentService.codeLens(CodeLensParams(TextDocumentIdentifier(session.uri("Node0")))) },
            queued = "project-CODE_LENSES",
        ) { session, first ->
            // as LSP4IJ asks a document's code lenses while the playbook's X131 does: the second asks what the first does,
            // of the same documents, and took the first's place, which answered ContentModified
            val second = session.server.textDocumentService.codeLens(CodeLensParams(TextDocumentIdentifier(session.uri("Node0"))))
            session.release.countDown()
            assertThat(first.awaited() as List<*>).isNotEmpty()
            assertThat(second.awaited()).isNotEmpty()
        }

    @Test
    fun `two Organize Imports requests of a document share its pending graph query, and both answer`() =
        waiting(Request.ORGANIZE_IMPORTS) { session, first ->
            val second = Request.ORGANIZE_IMPORTS.ask(session)
            session.release.countDown()
            Request.ORGANIZE_IMPORTS.check(session, first.awaited())
            Request.ORGANIZE_IMPORTS.check(session, second.awaited())
        }

    @Test
    fun `a request that asks a pending graph query something else, or of other text, takes its place`() {
        val root = SOURCES.getValue("Node0").lines()
        val line = root.indexOfFirst { "class Box" in it }
        val column = root[line].indexOf("Box")
        waiting(
            "the references",
            ask = { session -> session.adapter.findReferencesAsync(session.uri("Node0"), line, column, true) },
            queued = "project-REFERENCES",
        ) { session, first ->
            // the same references without the declaration: another question
            val other = session.adapter.findReferencesAsync(session.uri("Node0"), line, column, false)
            assertThat(first).describedAs("the references that another question took the place of").isCancelled()
            // the same question once a document's text changed
            session.adapter.updateDocument(session.uri("Node0/Lid"), "class Lid {}\n")
            val again = session.adapter.findReferencesAsync(session.uri("Node0"), line, column, false)
            assertThat(other).describedAs("the references that a question of other text took the place of").isCancelled()
            session.release.countDown()
            assertThat(again.awaited()).isNotEmpty()
        }
    }

    /**
     * [then] with a hover of the document [name] at [word], which waits for its module's compile: the document opens with
     * its file's text, again if it was open ([reopen]), as the next compile holds.
     */
    private fun releasing(
        name: String,
        word: String,
        reopen: Boolean = false,
        then: (Session, CompletableFuture<Hover?>) -> Unit,
    ) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        Session(directory).use { session ->
            if (reopen) session.close(name)
            session.hold.set(true)
            session.server.textDocumentService.didOpen(session.item(name))
            assertThat(session.entered.awaited()).isTrue()
            val hover = session.hover(name, word)
            assertThat(hover).describedAs("the hover of $name waits for its module's compile").isNotDone()
            then(session, hover)
        }
    }

    /**
     * [request], asked as the graph's held compile holds its graph query, then [event]; or, [again], the same request
     * asked once more after the event, while the first one waits and so keeps the event remembered: its graph query takes
     * the first one's place, and the event's document is one of those it starts with.
     */
    private fun run(
        request: Request,
        event: Event,
        again: Boolean = false,
    ) = waiting(request, event::prepare) { session, first ->
        val submitted = session.adapter.compilerQueueSnapshot()["submittedTotal"]
        val published = session.published.size
        event.act(session)
        event.after(session)
        // no analysis of the module again, and no diagnostics of it again, which its root still publishes
        if (event.compilesNothing) assertThat(session.adapter.compilerQueueSnapshot()["submittedTotal"]).isEqualTo(submitted)
        if (event.publishesNothing) assertThat(session.published.drop(published)).isEmpty()
        val answer = if (again) request.ask(session) else first
        session.release.countDown()
        event.overtakes?.let { assertContentModified(answer, it) } ?: request.check(session, answer.awaited())
    }

    /**
     * [then] with the [answer][then] to [request], once its graph query queues behind the graph's held compile, after
     * [prepare] readied the session.
     */
    private fun waiting(
        request: Request,
        prepare: (Session) -> Unit = {},
        then: (Session, CompletableFuture<*>) -> Unit,
    ) = waiting("$request", prepare, request::ask, then = then)

    /**
     * [then] with the [answer][then] that [ask] asks for, [what], once its compiler work, of a kind that [queued] names,
     * queues behind the graph's held compile, after [prepare] readied the session.
     */
    private fun waiting(
        what: String,
        prepare: (Session) -> Unit = {},
        ask: (Session) -> CompletableFuture<*>,
        queued: String = "project-",
        then: (Session, CompletableFuture<*>) -> Unit,
    ) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        Session(directory).use { session ->
            prepare(session)
            // the graph's diagnostics, which an editor pulls, compile the closed consumer first, and the compile holds
            session.hold.set(true)
            session.adapter.workspaceDiagnosticsAsync()
            assertThat(session.entered.awaited()).isTrue()
            val answer = ask(session)
            awaitUntil("$what queues its work behind the held compile") {
                @Suppress("UNCHECKED_CAST")
                (session.adapter.compilerQueueSnapshot()["queuedJobs"] as List<String>).any { queued in it && "DIAGNOSTICS" !in it }
            }
            assertThat(answer).describedAs("$what waits for the held compile").isNotDone()
            then(session, answer)
        }
    }

    /** Checks that [answer] is ContentModified, which says what overtook it, [reason]. */
    private fun assertContentModified(
        answer: CompletableFuture<*>,
        reason: String,
    ) {
        assertThatThrownBy { answer.awaited() }
            .rootCause()
            .isInstanceOfSatisfying(ResponseErrorException::class.java) {
                assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.ContentModified.value)
                assertThat(it.responseError.message).isEqualTo(reason)
            }
    }

    /**
     * A server with the graph of [SOURCES]' Node0 and its consumer Consumer, whose root Node0 the editor has open, and
     * Other, a module outside the graph, as an earlier case's file that the editor still has open, both with their
     * text as the files have it; the next module compile after [hold] is set holds until [release]. [openLibrary] opens
     * [library], String's source, as VS Code does after Go to Definition on Other's String. [published] has the
     * diagnostics that the server published, in order; a client that [pull]s them gets them so instead.
     */
    class Session(
        private val directory: Path,
        pull: Boolean = false,
    ) : AutoCloseable {
        val hold = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        /** The module whose next compile holds, also within a graph query, until [moduleRelease]. */
        val holdModule = AtomicReference<String?>()
        val moduleEntered = CountDownLatch(1)
        val moduleRelease = CountDownLatch(1)
        val progress = CopyOnWriteArrayList<ProgressParams>()
        val published = CopyOnWriteArrayList<PublishDiagnosticsParams>()
        val adapter =
            hookedAdapter { module ->
                if (hold.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.awaited())
                } else if (holdModule.get() == module.simpleModuleName && holdModule.getAndSet(null) != null) {
                    moduleEntered.countDown()
                    check(moduleRelease.awaited())
                }
            }
        val server = XtcLanguageServer(adapter)
        lateinit var library: String
        private val graph = listOf(XdkSourceModule("Node0", uri("Node0")), XdkSourceModule("Consumer", uri("Consumer"), setOf("Node0")))

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
                                textDocument =
                                    editorInitializeParams().capabilities.textDocument.apply {
                                        if (pull) diagnostic = DiagnosticCapabilities()
                                    }
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
            server.replaceCompilerSourceModules(graph)
            listOf("Node0", "Other").forEach(::open)
        }

        /** Gives the server the graph again, as it is. */
        fun regraph() = server.replaceCompilerSourceModules(graph)

        /** Writes [text] to [name]'s file, a module that no editor has open, and adds it to the graph. */
        fun join(
            name: String,
            text: String,
        ) {
            write(name, text)
            server.replaceCompilerSourceModules(graph + XdkSourceModule(name, uri(name)))
        }

        /** The open of [name]'s document with its text as the file has it, as the editor sends it. */
        fun item(name: String) = DidOpenTextDocumentParams(TextDocumentItem(uri(name), "xtc", 1, SOURCES.getValue(name)))

        /** Opens [name]'s document with its file's text, and waits for its analysis. */
        fun open(name: String) {
            server.textDocumentService.didOpen(item(name))
            server.textDocumentService.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri(name)))).awaited()
        }

        /** The close of [name]'s document. */
        fun close(name: String) = server.textDocumentService.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(uri(name))))

        /** Gives [name]'s document [text], as an edit does, without waiting for its analysis. */
        fun change(
            name: String,
            text: String,
        ) = server.textDocumentService.didChange(
            DidChangeTextDocumentParams(VersionedTextDocumentIdentifier(uri(name), 2), listOf(TextDocumentContentChangeEvent(text))),
        )

        /** A hover of [name]'s document at [word]. */
        fun hover(
            name: String,
            word: String,
            text: String = SOURCES.getValue(name),
        ): CompletableFuture<Hover?> =
            server.textDocumentService.hover(HoverParams(TextDocumentIdentifier(uri(name)), position(text, word)))

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

        companion object {
            /** The position of [word]'s first occurrence in [text]. */
            fun position(
                text: String,
                word: String,
            ): Position {
                val lines = text.lines()
                val line = lines.indexOfFirst { word in it }
                return Position(line, lines[line].indexOf(word))
            }
        }

        fun uri(name: String): String =
            directory
                .resolve("$name.x")
                .toFile()
                .toURI()
                .toString()

        override fun close() {
            release.countDown()
            moduleRelease.countDown()
            server.shutdown().awaited()
        }
    }

    private companion object {
        val SOURCES =
            mapOf(
                "Node0" to "module Node0 {\n    import ecstasy.collections.ReverseList;\n\n    class Box {}\n}\n",
                "Node0/Part" to "class Part {}\n",
                "Node0/Lid" to
                    "class Lid {\n    Int depth = 0;\n\n" +
                    "    Int deeper() {\n        Int more = depth + 1;\n        return more;\n    }\n}\n",
                "Consumer" to "module Consumer {\n    package lib import Node0;\n    class Child extends lib.Box {}\n}\n",
                "Other" to "module Other {\n    class Extract {\n        String text = \"value\";\n    }\n}\n",
            )
    }
}
