package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.DiagnosticCapabilities
import org.eclipse.lsp4j.DiagnosticWorkspaceCapabilities
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.PreviousResultId
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceDiagnosticParams
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkLibrarySources
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.awaitUntil
import org.xvm.lsp.awaited
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Checks negotiated document/workspace pull diagnostics alongside the push fallback. Closed roots can be
 * diagnosed without becoming editor buffers, and result IDs must reflect repaired or changed analyses.
 */
class XdkPullDiagnosticsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `opening closed rename consumers and editing their dependency keeps analyses available`() {
        val sources =
            mapOf(
                "Contracts" to "module Contracts { interface Mapper<T> { T map(T value); } }",
                "Uses" to
                    "module Uses { package api import Contracts; class Mapper implements api.Mapper<String> { @Override String map(String value) = value; } String run(api.Mapper<String> mapper) = mapper.map(\"a\"); }",
                "Dormant" to
                    "module Dormant { package api import Contracts; String run(api.Mapper<String> mapper) = mapper.map(\"b\"); }",
            )
        val uris =
            sources.mapValues { (name, source) ->
                directory
                    .resolve("$name.x")
                    .toFile()
                    .apply { writeText(source) }
                    .toPath()
                    .toUri()
                    .toString()
            }
        Session().use { session ->
            session.server.replaceCompilerSourceModules(
                uris.map { (name, uri) ->
                    XdkSourceModule(
                        name,
                        uri,
                        if (name == "Contracts") emptySet() else setOf("Contracts"),
                    )
                },
            )
            session.open(uris.getValue("Contracts"), sources.getValue("Contracts"), 1)
            assertThat(session.pull(uris.getValue("Contracts")).left.items).isEmpty()
            sources
                .filterKeys { it != "Contracts" }
                .forEach { (name, source) ->
                    session.open(uris.getValue(name), source, 1)
                }
            sources.forEach { (name, source) ->
                session.change(uris.getValue(name), source.replace("map(", "convert("), 2)
            }
            uris.values.forEach { uri ->
                assertThat(session.pull(uri).left.items).isEmpty()
                assertThat(
                    session.server.textDocumentService
                        .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                        .awaited(),
                ).isNotEmpty()
            }
        }
    }

    @Test
    fun `callers in a module's other files, opened one after another, pull the error that their callee's edit made`() {
        CompilerTestSupport.configure()
        Path
            .of(System.getProperty("xtc.composite.root"), "lang/test-fixtures/tour/cardGame/src/main/x")
            .toFile()
            .copyRecursively(directory.toFile())

        // the files' real paths, as an editor has them: the temporary directory can be a link
        fun uri(name: String) =
            directory
                .resolve(name)
                .toFile()
                .canonicalFile
                .toURI()
                .toString()

        fun text(name: String) = directory.resolve(name).toFile().readText()
        Session().use { session ->
            session.server.replaceCompilerSourceModules(listOf(XdkSourceModule("cardGame", uri("cardGame.x"))))
            val player = "cardGame/Player.x"
            session.open(uri(player), text(player), 1)
            assertThat(session.pull(uri(player)).left.items).isEmpty()
            val changed =
                text(player).replace(
                    "static Boolean cardMatches(Card card1, Card card2)",
                    "static Boolean cardMatches(Card card1, Card card2, Boolean strict)",
                )
            session.change(uri(player), changed, 2)
            assertThat(
                session
                    .pull(uri(player))
                    .left.items
                    .map { it.message.left },
            ).anyMatch { "cardMatches" in it }
            // as the IntelliJ tour opens them: one, its pull, then the next
            listOf("cardGame/FirstMatch.x", "cardGame/SaveEights.x").forEach { caller ->
                session.open(uri(caller), text(caller), 1)
                assertThat(
                    session
                        .pull(uri(caller))
                        .left.items
                        .map { it.message.left },
                ).`as`(caller)
                    .anyMatch { "cardMatches" in it }
            }
        }
    }

    @Test
    fun `pull negotiates one channel and unchanged reports become full after repair`() {
        Session().use { session ->
            assertThat(session.capabilities.diagnosticProvider.identifier).isEqualTo("xtc")
            session.open(URI, BROKEN, 1)
            val first = session.pull(URI).left
            assertThat(first.items).isNotEmpty()
            assertThat(first.resultId).isNotBlank()
            assertThat(session.pull(URI, first.resultId).right.resultId).isEqualTo(first.resultId)
            session.change(URI, VALID, 2)
            val repaired = session.pull(URI, first.resultId).left
            assertThat(repaired.items).isEmpty()
            assertThat(repaired.resultId).isNotEqualTo(first.resultId)
            awaitUntil { session.refreshes.get() > 0 }
            assertThat(session.published).isEmpty()
        }
    }

    @Test
    fun `unnegotiated clients keep push diagnostics and cannot pull`() {
        Session(pull = false).use { session ->
            assertThat(session.capabilities.diagnosticProvider).isNull()
            session.open(URI, BROKEN, 1)
            awaitUntil { session.published.isNotEmpty() }
            assertThat(session.published.last().diagnostics).isNotEmpty()
            assertThatThrownBy { session.pull(URI) }.hasMessageContaining("not negotiated")
            assertThat(session.refreshes.get()).isZero()
        }
    }

    @Test
    fun `a library source that the debugger opens has no diagnostics, pushed or pulled, and keeps its outline`() {
        CompilerTestSupport.configure()
        // the read-only copy that a stack frame in the XDK names, which the client opens as a step goes into it: on its
        // own its file include, $/implicit.x, and its imports of the library's own names do not resolve
        val copy = Path.of(requireNotNull(XdkLibrarySources.file("ecstasy.xtclang.org", "ecstasy/reflect/TypeSystem.x")))
        val uri = copy.toUri().toString()
        val text = copy.toFile().readText()

        // its compile on its own still gives its outline
        fun outline(session: Session) =
            assertThat(
                session.server.textDocumentService
                    .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                    .awaited(),
            ).isNotEmpty()
        Session(pull = false).use { session ->
            session.open(uri, text, 1)
            awaitUntil("the open library source's diagnostics are published") { session.published.any { it.uri == uri } }
            assertThat(session.published.filter { it.uri == uri }.flatMap { it.diagnostics }).isEmpty()
            outline(session)
        }
        Session().use { session ->
            session.open(uri, text, 1)
            assertThat(session.pull(uri).left.items).isEmpty()
            assertThat(
                session
                    .workspace()
                    .items
                    .mapNotNull { it.left }
                    .filter { it.uri == uri }
                    .flatMap { it.items },
            ).isEmpty()
            assertThat(session.published.filter { it.uri == uri }.flatMap { it.diagnostics }).isEmpty()
            outline(session)
        }
    }

    @Test
    fun `workspace pull diagnoses unopened roots and does not install editor buffers`() {
        val file = directory.resolve("Pull.x").toFile().apply { writeText(BROKEN) }
        Session().use { session ->
            session.server.replaceCompilerSourceModules(
                listOf(XdkSourceModule("Pull", file.toURI().toString())),
            )
            val first =
                session
                    .workspace()
                    .items
                    .single()
                    .left
            assertThat(first.items).isNotEmpty()
            assertThat(first.version == null).isTrue()
            assertThat(session.adapter.getCachedResult(first.uri)).isNull()
            assertThat(
                session
                    .workspace(listOf(PreviousResultId(first.uri, first.resultId)))
                    .items
                    .single()
                    .right
                    .resultId,
            ).isEqualTo(first.resultId)
            // No watcher notification: a read-only query still recaptures and compares disk inputs.
            file.writeText(VALID)
            val fixed =
                session
                    .workspace(listOf(PreviousResultId(first.uri, first.resultId)))
                    .items
                    .single()
                    .left
            assertThat(fixed.items).isEmpty()
            assertThat(fixed.resultId).isNotEqualTo(first.resultId)
        }
    }

    @Test
    fun `idle workspace pulls of an unchanged graph lex, parse and queue nothing after the first`() {
        val lib = directory.resolve("Lib.x").toFile().apply { writeText("module Lib {\n    static Int value() = Util.ONE;\n}\n") }
        directory.resolve("Lib").toFile().mkdirs()
        val util = directory.resolve("Lib/Util.x").toFile().apply { writeText("class Util {\n    static Int ONE = 1;\n}\n") }
        val app =
            directory.resolve("App.x").toFile().apply {
                writeText("module App {\n    package lib import Lib;\n\n    Int run() = lib.value() / 1;\n}\n")
            }
        Session().use { session ->
            session.server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Lib", lib.toURI().toString()),
                    XdkSourceModule("App", app.toURI().toString(), setOf("Lib")),
                ),
            )
            val first = session.workspace().items.map { it.left }
            assertThat(first.map { it.uri.substringAfterLast('/') }).containsExactlyInAnyOrder("App.x", "Lib.x", "Util.x")
            val previous = first.map { PreviousResultId(it.uri, it.resultId) }
            TraceRecording().use { trace ->
                // VS Code pulls again every 2 s with the result IDs it has, and IntelliJ's closed-file bridge with none
                repeat(3) {
                    assertThat(session.workspace(previous).items.map { it.right.resultId })
                        .containsExactlyInAnyOrderElementsOf(first.map { it.resultId })
                }
                repeat(2) {
                    assertThat(session.workspace().items.map { it.left }).containsExactlyInAnyOrderElementsOf(first)
                }
                assertThat(trace.count("Lexer.lex(presentation)")).isZero()
                assertThat(trace.count("Parser.parseModuleName(snapshot)")).isZero()
                assertThat(trace.count("project-DIAGNOSTICS", "submitted")).isZero()
            }
            // a change on disk with no watcher notification still reaches the next pull, which then queues its job
            TraceRecording().use { trace ->
                util.writeText("class Util {\n    static Int ONE = missing;\n}\n")
                val changed =
                    session
                        .workspace(previous)
                        .items
                        .filter { it.isLeft }
                        .map { it.left }
                assertThat(changed.single { it.uri.endsWith("Util.x") }.items).isNotEmpty()
                assertThat(trace.count("project-DIAGNOSTICS", "submitted")).isEqualTo(1)
            }
        }
    }

    @Test
    fun `a closed test module's tests are listed from the workspace pull's compile, and follow its file`() {
        val lib = directory.resolve("Lib.x").toFile().apply { writeText("module Lib {\n    Int one() = 1;\n}\n") }
        val tests = directory.resolve("LibTest.x").toFile().apply { writeText(libTest("adds")) }
        Session().use { session ->
            session.server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Lib", lib.toURI().toString()),
                    XdkSourceModule("LibTest", tests.toURI().toString(), setOf("Lib")),
                ),
            )

            fun listed() =
                session.server
                    .tests()
                    .awaited()
                    .single()
                    .tests.children
                    .map { it.selector to it.location?.startLine }

            val first =
                TraceRecording().use { trace ->
                    // no editor has had its file open: the listing compiles the graph, as a workspace pull would, once
                    assertThat(listed()).containsExactly("adds" to 4)
                    val pulled = session.workspace().items.map { it.left }
                    assertThat(trace.count("EmbeddingSupport.compileModule(tree)")).isEqualTo(2)
                    pulled
                }
            assertThat(session.adapter.getCachedResult(tests.toURI().toString())).isNull()

            // a test added to the closed file, which no watcher reports, is in the next listing, and the next pull
            // reports the file anew, with the same diagnostics, which is what a client lists its tests again on
            tests.writeText(libTest("adds", "subtracts"))
            assertThat(listed()).containsExactly("adds" to 4, "subtracts" to 9)
            val again = session.workspace(first.map { PreviousResultId(it.uri, it.resultId) }).items
            val changed = again.single { it.isLeft && it.left.uri.endsWith("LibTest.x") }.left
            assertThat(changed.items).isEmpty()
            assertThat(changed.resultId).isNotEqualTo(first.single { it.uri.endsWith("LibTest.x") }.resultId)
            assertThat(again.single { it.isRight }.right.uri).endsWith("Lib.x")
        }
    }

    /** A test module of Lib's with a test, each [names]d, that calls it. */
    private fun libTest(vararg names: String) =
        "module LibTest {\n    package lib import Lib;\n" +
            names.joinToString("") { "\n    @Test\n    void $it() {\n        assert lib.one() == 1;\n    }\n" } + "}\n"

    /** The trace's [event]s of [operation]: the compiler's lexes and parses, and the compiler queue's jobs. */
    private fun TraceRecording.count(
        operation: String,
        event: String = "start",
    ): Int = entries.count { it["operation"]?.asString == operation && it["event"]?.asString == event }

    @Test
    fun `closed member pulls retain standalone module errors and follow watched file changes`() {
        val root = directory.resolve("Pull.x").toFile().apply { writeText(VALID) }
        val members = directory.resolve("Pull").toFile().apply { mkdirs() }
        val member = members.resolve("Bad.x")
        val uri = member.toPath().toUri().toString()
        Session().use { session ->
            session.server.replaceCompilerSourceModules(emptyList())
            session.open(root.toURI().toString(), VALID, 1)
            assertThat(session.pull(root.toURI().toString()).left.items).isEmpty()

            fun watched(type: FileChangeType) =
                session.server.workspaceService.didChangeWatchedFiles(
                    DidChangeWatchedFilesParams(listOf(FileEvent(uri, type))),
                )

            member.writeText("class Bad extends Missing {}")
            watched(FileChangeType.Created)
            val broken = session.pull(uri).left
            assertThat(broken.items).isNotEmpty()
            assertThat(
                broken.items
                    .first()
                    .range.start.character,
            ).isEqualTo(18)
            assertThat(session.pull(member.toURI().toString(), broken.resultId).right.resultId)
                .isEqualTo(broken.resultId)

            member.writeText("class Bad {}")
            watched(FileChangeType.Changed)
            val repaired = session.pull(uri, broken.resultId).left
            assertThat(repaired.items).isEmpty()
            assertThat(repaired.resultId).isNotEqualTo(broken.resultId)

            member.writeText("class Bad extends Missing {}")
            watched(FileChangeType.Changed)
            val brokenAgain = session.pull(uri).left
            assertThat(brokenAgain.items).isNotEmpty()
            assertThat(member.delete()).isTrue()
            watched(FileChangeType.Deleted)
            assertThat(session.pull(uri, brokenAgain.resultId).left.items).isEmpty()
        }
    }

    @Test
    fun `related closed documents and failed dependencies survive document pull`() {
        val lib =
            directory.resolve("Library.x").toFile().apply {
                writeText("module Library { MissingType value; }")
            }
        val app =
            directory.resolve("Pull.x").toFile().apply {
                writeText("module Pull { package lib import Library; }")
            }
        Session().use { session ->
            session.server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Library", lib.toURI().toString()),
                    XdkSourceModule("Pull", app.toURI().toString(), setOf("Library")),
                ),
            )
            session.open(app.toURI().toString(), app.readText(), 7)
            val report = session.pull(app.toURI().toString()).left
            assertThat(report.items.map { it.code.left }).contains("DEPENDENCY-FAILED")
            assertThat(
                report.relatedDocuments
                    .getValue(lib.canonicalFile.toURI().toString())
                    .left
                    .items,
            ).isNotEmpty()
            val workspace = session.workspace().items.map { it.left }
            assertThat(workspace.single { it.uri == app.toURI().toString() }.version).isEqualTo(7)
            assertThat(
                workspace.single { it.uri == app.toURI().toString() }.items.map { it.code.left },
            ).contains("DEPENDENCY-FAILED")
            assertThat(
                workspace.single { it.uri == lib.canonicalFile.toURI().toString() }.version ==
                    null,
            ).isTrue()
        }
    }

    @Test
    fun `closing an unsaved buffer restores disk diagnostics and removed roots clear old reports`() {
        val file = directory.resolve("Pull.x").toFile().apply { writeText(VALID) }
        val uri = file.toURI().toString()
        Session().use { session ->
            session.server.replaceCompilerSourceModules(listOf(XdkSourceModule("Pull", uri)))
            session.open(uri, BROKEN, 3)
            val broken = session.pull(uri).left
            session.server.textDocumentService.didClose(
                DidCloseTextDocumentParams(TextDocumentIdentifier(uri)),
            )
            assertThat(session.pull(uri, broken.resultId).left.items).isEmpty()
            file.writeText(BROKEN)
            val previous =
                session
                    .workspace()
                    .items
                    .single()
                    .left
            session.server.replaceCompilerSourceModules(emptyList())
            val removed =
                session
                    .workspace(listOf(PreviousResultId(uri, previous.resultId)))
                    .items
                    .single()
                    .left
            assertThat(removed.items).isEmpty()
            assertThat(removed.version == null).isTrue()
        }
    }

    @Test
    fun `a deleted file whose diagnostics came from its own pulls is cleared by the next workspace pull`() {
        val root = directory.resolve("Pull.x").toFile().apply { writeText(VALID) }
        val member =
            directory.resolve("Pull/Bad.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Bad extends Missing {}")
            }
        val uri = member.toURI().toString()
        Session().use { session ->
            session.server.replaceCompilerSourceModules(listOf(XdkSourceModule("Pull", root.toURI().toString())))
            session.open(uri, member.readText(), 1)
            assertThat(session.pull(uri).left.items).isNotEmpty()
            // as VS Code's client closes a document: one last pull of it, then it forgets the document's result IDs,
            // so that a later workspace pull names none for it
            session.server.textDocumentService.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(uri)))
            assertThat(session.pull(uri).left.items).isNotEmpty()
            assertThat(member.delete()).isTrue()
            session.server.workspaceService.didChangeWatchedFiles(
                DidChangeWatchedFilesParams(listOf(FileEvent(uri, FileChangeType.Deleted))),
            )
            val reports =
                session
                    .workspace()
                    .items
                    .map { it.left }
                    .filter { it.uri.endsWith("/Bad.x") }
            assertThat(reports).describedAs("the deleted file's report").hasSize(1)
            assertThat(reports.single().items).isEmpty()
            // once: the report is the client's last of it
            assertThat(
                session
                    .workspace()
                    .items
                    .map { it.left?.uri ?: it.right.uri },
            ).noneMatch { it.endsWith("/Bad.x") }
        }
    }

    @Test
    fun `a moved file's old report is cleared once, and is unchanged in the pulls that name its result ID`() {
        val root = directory.resolve("Pull.x").toFile().apply { writeText(VALID) }
        val member =
            directory.resolve("Pull/Bad.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Bad extends Missing {}")
            }
        val moved = File(member.parentFile, "Moved.x")
        Session().use { session ->
            session.server.replaceCompilerSourceModules(listOf(XdkSourceModule("Pull", root.toURI().toString())))
            // as VS Code's client pulls: each pull names every document's result ID that the reports so far gave it
            val known = linkedMapOf<String, String>()

            fun pull() =
                session.workspace(known.map { (uri, id) -> PreviousResultId(uri, id) }).items.onEach { report ->
                    report.left?.let { known[it.uri] = it.resultId }
                    report.right?.let { known[it.uri] = it.resultId }
                }

            fun full(
                items: List<WorkspaceDocumentDiagnosticReport>,
                name: String,
            ) = items.mapNotNull { it.left }.filter { it.uri.endsWith("/$name") }
            assertThat(full(pull(), "Bad.x").single().items).isNotEmpty()
            // as VS Code renames a closed file: the file moves, and the watcher reports the old one deleted and the
            // new one created
            assertThat(member.renameTo(moved)).isTrue()
            session.server.workspaceService.didChangeWatchedFiles(
                DidChangeWatchedFilesParams(
                    listOf(
                        FileEvent(member.toURI().toString(), FileChangeType.Deleted),
                        FileEvent(moved.toURI().toString(), FileChangeType.Created),
                    ),
                ),
            )
            val cleared = full(pull(), "Bad.x")
            assertThat(cleared).describedAs("the old file's report").hasSize(1)
            assertThat(cleared.single().items).isEmpty()
            // the client names the cleared report's result ID in each pull after it: nothing has changed since
            repeat(3) { round ->
                assertThat(pull().filter { it.isLeft }.map { it.left.uri })
                    .describedAs("the full reports of pull ${round + 3}")
                    .isEmpty()
            }
        }
    }

    @Test
    fun `result identifiers are scoped to a document and a server`() {
        val id =
            Session().use { session ->
                session.open(URI, VALID, 1)
                val first = session.pull(URI).left.resultId
                assertThat(session.pull("file:///Elsewhere.x", first).isLeft).isTrue()
                first
            }
        Session(related = false).use { session ->
            session.open(URI, VALID, 1)
            val report = session.pull(URI, id).left
            assertThat(report.resultId).isNotEqualTo(id)
            assertThat(report.relatedDocuments).isNull()
            assertThatThrownBy { session.pull(URI, identifier = "unknown") }
                .hasMessageContaining("not negotiated")
        }
    }

    @Test
    fun `canceling a waiting pull preserves shared compilation and an edit rejects late reports`() {
        CompilerTestSupport.configure()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter =
            XdkAdapter { source, errors ->
                started.countDown()
                check(release.awaited())
                EmbeddingSupport.instance().compileModule(source, null, errors)
            }
        try {
            Session(adapter = adapter).use { session ->
                session.open(URI, BROKEN, 1)
                assertThat(started.awaited()).isTrue()
                val params = DocumentDiagnosticParams(TextDocumentIdentifier(URI))
                val canceled = session.server.textDocumentService.diagnostic(params)
                assertThat(canceled.cancel(false)).isTrue()
                val stale = session.server.textDocumentService.diagnostic(params)
                session.change(URI, VALID, 2)
                release.countDown()
                assertThatThrownBy { stale.awaited() }
                    .hasMessageContaining("Document changed")
                assertThat(session.pull(URI).left.items).isEmpty()
                assertThat(canceled.isCancelled).isTrue()
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `closed document pulls preserve error ranges across equivalent file URI spellings`() {
        val file = directory.resolve("Pull.x").toFile().apply { writeText(BROKEN) }
        Session().use { session ->
            session.server.replaceCompilerSourceModules(
                listOf(XdkSourceModule("Pull", file.toURI().toString())),
            )
            val first = session.pull(file.toPath().toUri().toString()).left
            assertThat(first.items).isNotEmpty()
            assertThat(
                first.items
                    .first()
                    .range.start.character,
            ).isEqualTo(BROKEN.indexOf("missing"))
            assertThat(first.items.first().relatedInformation).isNull()
            val second = session.pull(file.canonicalFile.toURI().toString(), first.resultId)
            assertThat(second.right.resultId).isEqualTo(first.resultId)
        }
    }

    /** Negotiates pull capabilities and records push reports and refreshes for comparison. */
    private class Session(
        pull: Boolean = true,
        related: Boolean = true,
        val adapter: XdkAdapter = XdkAdapter(),
    ) : AutoCloseable {
        val server = XtcLanguageServer(adapter)
        val published = CopyOnWriteArrayList<PublishDiagnosticsParams>()
        val refreshes = AtomicInteger()
        val capabilities: org.eclipse.lsp4j.ServerCapabilities

        init {
            val client = mock(LanguageClient::class.java)
            doAnswer {
                published += it.getArgument<PublishDiagnosticsParams>(0)
                null
            }.`when`(client)
                .publishDiagnostics(any())
            doAnswer {
                refreshes.incrementAndGet()
                CompletableFuture.completedFuture<Void>(null)
            }.`when`(client)
                .refreshDiagnostics()
            server.connect(client)
            capabilities =
                server
                    .initialize(
                        InitializeParams().apply {
                            capabilities =
                                ClientCapabilities().apply {
                                    textDocument =
                                        TextDocumentClientCapabilities().apply {
                                            if (pull) {
                                                diagnostic =
                                                    DiagnosticCapabilities().apply {
                                                        relatedDocumentSupport = related
                                                    }
                                            }
                                        }
                                    workspace =
                                        WorkspaceClientCapabilities().apply {
                                            diagnostics =
                                                DiagnosticWorkspaceCapabilities().apply {
                                                    refreshSupport = true
                                                }
                                        }
                                }
                        },
                    ).awaited()
                    .capabilities
            server.initialized(InitializedParams())
        }

        fun open(
            uri: String,
            text: String,
            version: Int,
        ) = server.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", version, text)),
        )

        fun change(
            uri: String,
            text: String,
            version: Int,
        ) = server.textDocumentService.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, version),
                listOf(TextDocumentContentChangeEvent(text)),
            ),
        )

        fun pull(
            uri: String,
            previous: String? = null,
            identifier: String? = null,
        ) = server.textDocumentService
            .diagnostic(
                DocumentDiagnosticParams(TextDocumentIdentifier(uri)).apply {
                    previousResultId = previous
                    this.identifier = identifier
                },
            ).awaited()

        fun workspace(previous: List<PreviousResultId> = emptyList()) =
            server.workspaceService.diagnostic(WorkspaceDiagnosticParams(previous)).awaited()

        override fun close() = server.close()
    }

    private companion object {
        const val URI = "file:///Pull.x"
        const val VALID = "module Pull { Int run() = 1; }"
        const val BROKEN = "module Pull { Int run() = missing; }"
    }
}
