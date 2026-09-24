package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.CompletionItem
import org.xvm.lsp.adapter.ParameterInfo
import org.xvm.lsp.adapter.SignatureHelp
import org.xvm.lsp.adapter.SignatureInfo
import org.xvm.lsp.adapter.WorkspaceEdit
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.model.CompilationResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

class XdkCursorServerTest {
    enum class Feature { COMPLETION, SIGNATURE, RENAME }

    private class Pending(
        val future: CompletableFuture<*>,
        val finish: () -> Unit,
    )

    private class Backend(
        val gate: CompletableFuture<Unit> = CompletableFuture.completedFuture(Unit),
        val ignoreCancellation: Boolean = false,
        val module: Boolean = false,
        private val delegate: MockAdapter = MockAdapter(),
    ) : Adapter by delegate {
        val pending = LinkedBlockingQueue<Pending>()
        val cancellations = AtomicInteger()
        val canceled = LinkedBlockingQueue<Boolean>()
        val analyses = CopyOnWriteArrayList<CompletableFuture<CompilationResult>>()

        override fun analysisScope(uri: String): String = if (module) "module" else uri

        override fun compileAsync(
            uri: String,
            content: String,
        ): CompletableFuture<CompilationResult> = gate.thenApply { delegate.compile(uri, content) }.also { analyses.add(it) }

        private fun <T> query(value: T): CompletableFuture<T> {
            val result =
                object : CompletableFuture<T>() {
                    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                        cancellations.incrementAndGet()
                        val changed = !ignoreCancellation && super.cancel(mayInterruptIfRunning)
                        canceled.add(changed)
                        return changed
                    }
                }
            pending.add(Pending(result) { result.complete(value) })
            return result
        }

        override fun getCompletionsAsync(
            uri: String,
            line: Int,
            column: Int,
            triggerCharacter: String?,
        ): CompletableFuture<List<CompletionItem>> =
            query(listOf(CompletionItem("member", CompletionItem.CompletionKind.PROPERTY, "Int member", "member")))

        override fun getSignatureHelpAsync(
            uri: String,
            line: Int,
            column: Int,
        ): CompletableFuture<SignatureHelp?> =
            query(
                SignatureHelp(
                    listOf(
                        SignatureInfo(
                            "Int call(Int a, Int b)",
                            parameters = listOf(ParameterInfo("Int a"), ParameterInfo("Int b")),
                            activeParameter = 1,
                        ),
                    ),
                    activeParameter = 1,
                ),
            )

        fun next(): Pending = checkNotNull(pending.poll(10, SECONDS)) { "query did not reach backend" }

        override fun renameAsync(
            uri: String,
            line: Int,
            column: Int,
            newName: String,
        ): CompletableFuture<WorkspaceEdit?> = query(WorkspaceEdit(emptyMap()))
    }

    private class Session(
        val backend: Backend,
    ) : AutoCloseable {
        val server = XtcLanguageServer(backend)
        val documents = server.textDocumentService

        init {
            server.connect(mock(LanguageClient::class.java))
        }

        fun open(uri: String = URI) = documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, "module Editing {}")))

        fun change(uri: String = URI) =
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri, 2),
                    listOf(TextDocumentContentChangeEvent("module Changed {}")),
                ),
            )

        fun request(
            feature: Feature,
            uri: String = URI,
        ): CompletableFuture<*> =
            when (feature) {
                Feature.COMPLETION -> documents.completion(CompletionParams(TextDocumentIdentifier(uri), Position(0, 0)))
                Feature.SIGNATURE -> documents.signatureHelp(SignatureHelpParams(TextDocumentIdentifier(uri), Position(0, 0)))
                Feature.RENAME -> documents.rename(RenameParams(TextDocumentIdentifier(uri), Position(0, 0), "renamed"))
            }

        override fun close() {
            server.shutdown().get(10, SECONDS)
        }
    }

    @ParameterizedTest
    @EnumSource(Feature::class)
    fun `filesystem notifications for an open overlay preserve pending queries`(feature: Feature) {
        val backend = Backend()
        Session(backend).use { session ->
            session.open()
            val response = session.request(feature)
            val work = backend.next()
            // Created, changed and deleted disk files are all masked by the open buffer.
            for (kind in FileChangeType.values()) {
                session.server.workspaceService.didChangeWatchedFiles(
                    DidChangeWatchedFilesParams(listOf(FileEvent(URI, kind))),
                )
            }
            session.documents.didSave(DidSaveTextDocumentParams(TextDocumentIdentifier(URI)))
            assertThat(backend.analyses).hasSize(1)
            assertThat(work.future.isCancelled).isFalse()
            work.finish()
            assertThat(response.get(10, SECONDS)).isNotNull()
        }
    }

    @ParameterizedTest
    @EnumSource(Feature::class)
    fun `client cancellation reaches the cursor backend without canceling shared analysis`(feature: Feature) {
        val backend = Backend()
        Session(backend).use { session ->
            session.open()
            val response = session.request(feature)
            val work = backend.next()
            assertThat(response.cancel(false)).isTrue()
            assertThat(backend.canceled.poll(10, SECONDS)).isTrue()
            assertThat(work.future.isCancelled).isTrue()
            assertThat(backend.analyses.single().isCancelled).isFalse()
            val latest = session.request(feature)
            backend.next().finish()
            assertThat(latest.get(10, SECONDS)).isNotNull()
        }
    }

    @ParameterizedTest
    @EnumSource(Feature::class)
    fun `cancellation while waiting for analysis does not start cursor work`(feature: Feature) {
        val backend = Backend(gate = CompletableFuture())
        Session(backend).use { session ->
            session.open()
            val canceled = session.request(feature)
            assertThat(canceled.cancel(false)).isTrue()
            assertThat(backend.analyses.single().isCancelled).isFalse()
            backend.gate.complete(Unit)
            val latest = session.request(feature)
            backend.next().finish()
            assertThat(latest.get(10, SECONDS)).isNotNull()
            assertThat(backend.pending).isEmpty()
        }
    }

    @ParameterizedTest
    @EnumSource(Feature::class)
    fun `an edit rejects late facts even when the backend ignores cancellation`(feature: Feature) {
        val backend = Backend(ignoreCancellation = true)
        Session(backend).use { session ->
            session.open()
            val old = session.request(feature)
            val work = backend.next()
            session.change()
            assertModified(old)
            work.finish()
            assertModified(old)
            assertThat(backend.canceled.poll(10, SECONDS)).isFalse()
            assertThat(backend.cancellations.get()).isPositive()
        }
    }

    @ParameterizedTest
    @EnumSource(Feature::class)
    fun `a module edit invalidates member queries without changing their document version`(feature: Feature) {
        val backend = Backend(ignoreCancellation = true, module = true)
        Session(backend).use { session ->
            session.open()
            val member = "file:///Editing/Child.x"
            session.open(member)
            val old = session.request(feature, member)
            val work = backend.next()
            session.change()
            work.finish()
            assertModified(old)
        }
    }

    @Test
    fun `close and reopen with the same version cannot publish old cursor facts`() {
        val backend = Backend(ignoreCancellation = true)
        Session(backend).use { session ->
            session.open()
            val old = session.request(Feature.SIGNATURE)
            val work = backend.next()
            session.documents.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(URI)))
            session.open()
            work.finish()
            assertModified(old)
            val latest = session.request(Feature.SIGNATURE)
            backend.next().finish()
            assertThat(latest.get(10, SECONDS)).isNotNull()
        }
    }

    @Test
    fun `shutdown retires cursor work even when the backend ignores cancellation`() {
        val backend = Backend(ignoreCancellation = true)
        Session(backend).use { session ->
            session.open()
            val old = session.request(Feature.COMPLETION)
            val work = backend.next()
            session.close()
            work.finish()
            assertModified(old)
        }
    }

    @Test
    fun `signature conversion preserves per-signature parameter mapping`() {
        val backend = Backend()
        Session(backend).use { session ->
            session.open()
            val response = session.documents.signatureHelp(SignatureHelpParams(TextDocumentIdentifier(URI), Position(0, 0)))
            backend.next().finish()
            val help = response.get(10, SECONDS)!!
            assertThat(help.activeParameter).isEqualTo(1)
            assertThat(help.signatures.single().activeParameter).isEqualTo(1)
            assertThat(
                help.signatures
                    .single()
                    .parameters
                    .map { it.label.left },
            ).containsExactly("Int a", "Int b")
        }
    }

    private fun assertModified(result: CompletableFuture<*>) {
        val failure = assertThrows<ExecutionException> { result.get(10, SECONDS) }
        val error = failure.cause as ResponseErrorException
        assertThat(error.responseError.code).isEqualTo(ResponseErrorCode.ContentModified.value)
    }

    private companion object {
        const val URI = "file:///Editing.x"
    }
}
