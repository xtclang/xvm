package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.CompletionItem
import org.xvm.lsp.adapter.TypeHierarchyItem
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.awaitUntil
import org.xvm.lsp.awaited
import org.xvm.lsp.model.CompilationResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Forces compiler completion and transport cancellation around document locks. Callbacks must release the
 * compiler worker before publication so cancellation cannot deadlock behind a reader.
 */
class XdkCallbackServerTest {
    /** Selects analysis or query completion when reproducing compiler callback races. */
    enum class Callback {
        ANALYSIS,
        QUERY,
    }

    @Test
    fun `transport cancellation returns while a navigation request holds the document lock`() {
        val navigationEntered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pending = CompletableFuture<List<CompletionItem>>()
        val backend =
            object : Adapter by MockAdapter() {
                override fun getCompletionsAsync(
                    uri: String,
                    line: Int,
                    column: Int,
                    triggerCharacter: String?,
                ) = pending

                override fun prepareTypeHierarchy(
                    uri: String,
                    line: Int,
                    column: Int,
                ): List<TypeHierarchyItem> {
                    navigationEntered.countDown()
                    check(release.awaited())
                    return emptyList()
                }
            }
        val server = XtcLanguageServer(backend)
        server.connect(mock(LanguageClient::class.java))
        val documents = server.textDocumentService
        val document = TextDocumentIdentifier("file:///Healthy.x")
        try {
            documents.didOpen(
                DidOpenTextDocumentParams(
                    TextDocumentItem(document.uri, "xtc", 1, "module Healthy {}"),
                ),
            )
            val response = documents.completion(CompletionParams(document, Position(0, 0)))
            awaitUntil { pending.numberOfDependents > 0 }
            val navigation =
                documents.prepareTypeHierarchy(TypeHierarchyPrepareParams(document, Position(0, 0)))
            assertThat(navigationEntered.awaited()).isTrue()
            // LSP4J cancels while holding its request-map lock. Completion of a different
            // response can need that lock, so cancellation must return without waiting here.
            val canceled = CompletableFuture.supplyAsync { response.cancel(false) }
            assertThat(canceled.awaited()).isTrue()
            release.countDown()
            assertThat(navigation.awaited()).isEmpty()
            awaitUntil { pending.isCancelled }
        } finally {
            release.countDown()
            server.shutdown().awaited()
        }
    }

    @ParameterizedTest
    @EnumSource(Callback::class)
    fun `compiler completion releases its worker before publishing under the document lock`(callback: Callback) {
        val release = CountDownLatch(1)
        val navigationEntered = CountDownLatch(1)
        val callbackQueued = CompletableFuture<CompletableFuture<*>>()
        val uri = "file:///Healthy.x"
        val source = "module Healthy {}"
        Executors.newSingleThreadExecutor().use { worker ->
            val delegate = MockAdapter()
            val backend =
                object : Adapter by delegate {
                    override fun compileAsync(
                        uri: String,
                        content: String,
                    ): CompletableFuture<CompilationResult> =
                        if (uri.endsWith("Neighbor.x")) {
                            CompletableFuture
                                .supplyAsync(
                                    {
                                        check(release.awaited())
                                        delegate.compile(uri, content)
                                    },
                                    worker,
                                ).also { callbackQueued.complete(it) }
                        } else {
                            CompletableFuture.completedFuture(delegate.compile(uri, content))
                        }

                    override fun getCompletionsAsync(
                        uri: String,
                        line: Int,
                        column: Int,
                        triggerCharacter: String?,
                    ): CompletableFuture<List<CompletionItem>> =
                        CompletableFuture
                            .supplyAsync(
                                {
                                    check(release.awaited())
                                    emptyList<CompletionItem>()
                                },
                                worker,
                            ).also { callbackQueued.complete(it) }

                    override fun prepareTypeHierarchy(
                        uri: String,
                        line: Int,
                        column: Int,
                    ): List<TypeHierarchyItem> {
                        navigationEntered.countDown()
                        // Model the compiler adapter's synchronous bridge onto its serial worker.
                        // The bound also releases the lock if this regression reintroduces the
                        // cycle.
                        return worker
                            .submit<List<TypeHierarchyItem>> { emptyList() }
                            .awaited()
                    }
                }
            val server = XtcLanguageServer(backend)
            server.connect(mock(LanguageClient::class.java))
            val documents = server.textDocumentService
            try {
                documents.didOpen(
                    DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, source)),
                )
                when (callback) {
                    Callback.ANALYSIS -> {
                        documents.didOpen(
                            DidOpenTextDocumentParams(
                                TextDocumentItem("file:///Neighbor.x", "xtc", 1, source),
                            ),
                        )
                    }

                    Callback.QUERY -> {
                        documents.completion(
                            CompletionParams(TextDocumentIdentifier(uri), Position(0, 0)),
                        )
                    }
                }
                val pending = callbackQueued.awaited()
                awaitUntil { pending.numberOfDependents > 0 }
                // A barrier behind the pending work proves that its callback precedes navigation.
                val queued = CountDownLatch(1)
                worker.execute { queued.countDown() }
                val result =
                    documents.prepareTypeHierarchy(
                        TypeHierarchyPrepareParams(TextDocumentIdentifier(uri), Position(0, 0)),
                    )
                assertThat(navigationEntered.awaited()).isTrue()
                release.countDown()
                assertThat(queued.awaited()).isTrue()
                assertThat(result.awaited()).isEmpty()
            } finally {
                release.countDown()
                server.shutdown().awaited()
            }
        }
    }
}
