package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressCancelParams
import org.eclipse.lsp4j.WorkDoneProgressKind
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.mock.MockAdapter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

class IndexingProgressTest {
    @Test
    fun `query progress identifies the requested source or workspace`() {
        val client = mock(LanguageClient::class.java)
        val events = LinkedBlockingQueue<ProgressParams>()
        doAnswer {
            events.add(it.getArgument(0))
            null
        }.`when`(client).notifyProgress(any())
        XtcLanguageServer(MockAdapter()).use { server ->
            server.connect(client)
            server
                .initialize(
                    InitializeParams().apply {
                        workspaceFolders = listOf(WorkspaceFolder("file:///code/platform/", "platform"))
                    },
                ).get(5, SECONDS)
            listOf(
                Triple("textDocument/references", "file:///code/platform/src/Consumer.x", "src/Consumer.x"),
                Triple("workspace/diagnostic", "", "Workspace: configured source graph"),
            ).forEach { (method, uri, expected) ->
                val work = CompletableFuture<Unit>()
                val params = ReferenceParams().apply { workDoneToken = Either.forLeft(method) }
                server.observeQuery(method, params, work, uri)
                val begin = events.poll(5, SECONDS).value.left as WorkDoneProgressBegin
                assertThat(begin.message).startsWith(expected).doesNotContain("Waiting for analysis or running compiler query")
                work.complete(Unit)
                assertThat(
                    events
                        .poll(5, SECONDS)
                        .value.left.kind,
                ).isEqualTo(WorkDoneProgressKind.end)
            }
        }
    }

    @Test
    fun `initialization reports the actual background scan lifetime and owns cancellation`() {
        listOf(false, true).forEach { cancel ->
            val scan = CompletableFuture<Unit>()
            val adapter =
                object : Adapter by MockAdapter() {
                    override fun initializeWorkspaceAsync(
                        workspaceFolders: List<String>,
                        progressReporter: ((String, Int) -> Unit)?,
                    ) = scan
                }
            val client = mock(LanguageClient::class.java)
            val events = LinkedBlockingQueue<ProgressParams>()
            doAnswer {
                events.add(it.getArgument(0))
                null
            }.`when`(client)
                .notifyProgress(any())
            XtcLanguageServer(adapter).use { server ->
                server.connect(client)
                val token = Either.forLeft<String, Int>("indexing")
                server
                    .initialize(
                        InitializeParams().apply {
                            workspaceFolders =
                                listOf(WorkspaceFolder("file:///workspace/", "workspace"))
                            workDoneToken = token
                        },
                    ).get(5, SECONDS)
                assertThat(
                    events
                        .poll(5, SECONDS)
                        .value.left.kind,
                ).isEqualTo(WorkDoneProgressKind.begin)
                assertThat(scan.isDone).isFalse()
                assertThat(events).isEmpty()
                if (cancel) {
                    server.cancelProgress(WorkDoneProgressCancelParams(token))
                } else {
                    scan.complete(Unit)
                }
                assertThat(
                    events
                        .poll(5, SECONDS)
                        .value.left.kind,
                ).isEqualTo(WorkDoneProgressKind.end)
                assertThat(scan.isCancelled).isEqualTo(cancel)
            }
        }
    }
}
