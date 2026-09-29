package org.xvm.lsp.server

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.DiagnosticCapabilities
import org.eclipse.lsp4j.DiagnosticWorkspaceCapabilities
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.InitializeParams
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
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule

class XdkPullDiagnosticsTest {
    @TempDir lateinit var directory: Path

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
            await().atMost(10, SECONDS).until { session.refreshes.get() > 0 }
            assertThat(session.published).isEmpty()
        }
    }

    @Test
    fun `unnegotiated clients keep push diagnostics and cannot pull`() {
        Session(pull = false).use { session ->
            assertThat(session.capabilities.diagnosticProvider).isNull()
            session.open(URI, BROKEN, 1)
            await().atMost(10, SECONDS).until { session.published.isNotEmpty() }
            assertThat(session.published.last().diagnostics).isNotEmpty()
            assertThatThrownBy { session.pull(URI) }.hasMessageContaining("not negotiated")
            assertThat(session.refreshes.get()).isZero()
        }
    }

    @Test
    fun `workspace pull diagnoses unopened roots and does not install editor buffers`() {
        val file = directory.resolve("Pull.x").toFile().apply { writeText(BROKEN) }
        Session().use { session ->
            session.server.replaceCompilerSourceModules(
                listOf(XdkSourceModule("Pull", file.toURI().toString()))
            )
            val first = session.workspace().items.single().left
            assertThat(first.items).isNotEmpty()
            assertThat(first.version == null).isTrue()
            assertThat(session.adapter.getCachedResult(first.uri)).isNull()
            assertThat(
                    session
                        .workspace(listOf(PreviousResultId(first.uri, first.resultId)))
                        .items
                        .single()
                        .right
                        .resultId
                )
                .isEqualTo(first.resultId)
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
                )
            )
            session.open(app.toURI().toString(), app.readText(), 7)
            val report = session.pull(app.toURI().toString()).left
            assertThat(report.items.map { it.code.left }).contains("DEPENDENCY-FAILED")
            assertThat(
                    report.relatedDocuments
                        .getValue(lib.canonicalFile.toURI().toString())
                        .left
                        .items
                )
                .isNotEmpty()
            val workspace = session.workspace().items.map { it.left }
            assertThat(workspace.single { it.uri == app.toURI().toString() }.version).isEqualTo(7)
            assertThat(
                    workspace.single { it.uri == lib.canonicalFile.toURI().toString() }.version ==
                        null
                )
                .isTrue()
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
                DidCloseTextDocumentParams(TextDocumentIdentifier(uri))
            )
            assertThat(session.pull(uri, broken.resultId).left.items).isEmpty()
            file.writeText(BROKEN)
            val previous = session.workspace().items.single().left
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
        val adapter = XdkAdapter { source, errors ->
            started.countDown()
            check(release.await(20, SECONDS))
            EmbeddingSupport.instance().compileModule(source, null, errors)
        }
        try {
            Session(adapter = adapter).use { session ->
                session.open(URI, BROKEN, 1)
                assertThat(started.await(10, SECONDS)).isTrue()
                val params = DocumentDiagnosticParams(TextDocumentIdentifier(URI))
                val canceled = session.server.textDocumentService.diagnostic(params)
                assertThat(canceled.cancel(false)).isTrue()
                val stale = session.server.textDocumentService.diagnostic(params)
                session.change(URI, VALID, 2)
                release.countDown()
                assertThatThrownBy { stale.get(10, SECONDS) }
                    .hasMessageContaining("Document changed")
                assertThat(session.pull(URI).left.items).isEmpty()
                assertThat(canceled.isCancelled).isTrue()
            }
        } finally {
            release.countDown()
        }
    }

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
                }
                .`when`(client)
                .publishDiagnostics(any())
            doAnswer {
                    refreshes.incrementAndGet()
                    CompletableFuture.completedFuture<Void>(null)
                }
                .`when`(client)
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
                                            if (pull)
                                                diagnostic =
                                                    DiagnosticCapabilities().apply {
                                                        relatedDocumentSupport = related
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
                        }
                    )
                    .get(10, SECONDS)
                    .capabilities
        }

        fun open(uri: String, text: String, version: Int) =
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", version, text))
            )

        fun change(uri: String, text: String, version: Int) =
            server.textDocumentService.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri, version),
                    listOf(TextDocumentContentChangeEvent(text)),
                )
            )

        fun pull(uri: String, previous: String? = null, identifier: String? = null) =
            server.textDocumentService
                .diagnostic(
                    DocumentDiagnosticParams(TextDocumentIdentifier(uri)).apply {
                        previousResultId = previous
                        this.identifier = identifier
                    }
                )
                .get(30, SECONDS)

        fun workspace(previous: List<PreviousResultId> = emptyList()) =
            server.workspaceService.diagnostic(WorkspaceDiagnosticParams(previous)).get(30, SECONDS)

        override fun close() = server.close()
    }

    private companion object {
        const val URI = "file:///Pull.x"
        const val VALID = "module Pull { Int run() = 1; }"
        const val BROKEN = "module Pull { Int run() = missing; }"
    }
}
