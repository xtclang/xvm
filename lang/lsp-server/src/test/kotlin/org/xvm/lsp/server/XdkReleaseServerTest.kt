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
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ProgressParams
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
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
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
 * Checks, at the server boundary, the requests that a close which changes nothing they read must not end: the editor's
 * release of a document outside the graph, with its text as the file has it, as VS Code releases one that no editor
 * shows some minutes after an extension or a navigation opened it, and a second didClose of a document that is not
 * open, as the language client sends for one that the playbook's cleanup closed itself. Each request's graph query
 * queues behind the graph's held compile, the close comes, and then the compile: by ordering, not timing.
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

    /** A close as it comes while the requests wait, which they outlast unless it [overtakes] them. */
    enum class Close(
        val overtakes: Boolean = false,
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
        ;

        /** Readies the close's document, before the requests are asked. */
        open fun prepare(session: Session) {}

        abstract fun of(session: Session): String
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class)
    fun `a workspace request outlasts the release of a document outside the graph`(request: Request) = run(request, Close.RELEASE)

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class)
    fun `a workspace request outlasts the release of a library's source`(request: Request) = run(request, Close.LIBRARY)

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class)
    fun `a workspace request outlasts a didClose of a document that is not open`(request: Request) = run(request, Close.NOT_OPEN)

    @ParameterizedTest(name = "{0}")
    @EnumSource(Request::class)
    fun `the close of a document with unsaved text overtakes a workspace request`(request: Request) = run(request, Close.UNSAVED)

    private fun run(
        request: Request,
        close: Close,
    ) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        Session(directory).use { session ->
            close.prepare(session)
            // the graph's diagnostics, which an editor pulls, compile the closed consumer first, and the compile holds
            session.hold.set(true)
            session.adapter.workspaceDiagnosticsAsync()
            assertThat(session.entered.awaited()).isTrue()
            val answer = request.ask(session)
            awaitUntil("$request queues its graph query behind the held compile") {
                @Suppress("UNCHECKED_CAST")
                (session.adapter.compilerQueueSnapshot()["queuedJobs"] as List<String>).any { "project-" in it && "DIAGNOSTICS" !in it }
            }
            assertThat(answer).describedAs("$request waits for the held compile").isNotDone()
            session.server.textDocumentService.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(close.of(session))))
            session.release.countDown()
            if (close.overtakes) {
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
     * [library], String's source, as VS Code does after Go to Definition on Other's String.
     */
    class Session(
        private val directory: Path,
    ) : AutoCloseable {
        val hold = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val progress = CopyOnWriteArrayList<ProgressParams>()
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
            listOf("Node0", "Other").forEach { name ->
                server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri(name), "xtc", 1, SOURCES.getValue(name))))
                server.textDocumentService.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri(name)))).awaited()
            }
        }

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

        /** Gives [name]'s document text that its file does not have, and waits for its analysis. */
        fun edit(name: String) {
            server.textDocumentService.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri(name), 2),
                    listOf(TextDocumentContentChangeEvent(SOURCES.getValue(name).replace("}\n}", "}\n    class Unsaved {}\n}"))),
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
                "Consumer" to "module Consumer {\n    package lib import Node0;\n    class Child extends lib.Box {}\n}\n",
                "Other" to "module Other {\n    class Extract {\n        String text = \"value\";\n    }\n}\n",
            )
    }
}
