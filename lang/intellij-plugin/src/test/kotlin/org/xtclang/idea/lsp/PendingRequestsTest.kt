package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.MessageProducer
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

/** A connection's requests that still wait when the server's messages end are cancelled, not left waiting forever. */
class PendingRequestsTest {
    @Test
    fun `a request still waiting when the server's messages end is cancelled, through the production launcher`() {
        val fromServer = PipedOutputStream()
        val input = PipedInputStream(fromServer)
        Executors.newCachedThreadPool().use { workers ->
            val launcher =
                XtcClientFeatures()
                    .createLauncherBuilder<XtcLanguageServer>()
                    .setLocalService(Any())
                    .setRemoteInterface(XtcLanguageServer::class.java)
                    .setInput(input)
                    .setOutput(ByteArrayOutputStream())
                    .setExecutorService(workers)
                    .create()
            val listening = launcher.startListening()
            val hover =
                launcher.remoteProxy.textDocumentService.hover(
                    HoverParams(TextDocumentIdentifier("file:///project/App.x"), Position(0, 0)),
                )
            assertThat(hover).isNotDone()

            // the server's output ends, as when its process exits or is stopped
            fromServer.close()
            listening.get(10, SECONDS)
            assertThat(hover).isCancelled()
        }
    }

    @Test
    fun `an answered request keeps its answer, and one sent after the end is cancelled at once`() {
        val replies = ArrayDeque(listOf(CompletableFuture<Any?>(), CompletableFuture<Any?>()))
        val pending = PendingRequests()
        val endpoint = pending.endpoint(fakeEndpoint { replies.removeFirst() })
        val answered = endpoint.request("textDocument/hover", null)
        (answered as CompletableFuture<Any?>).complete("hover")

        pending.producer(MessageProducer { }).listen { }
        assertThat(answered).isCompletedWithValue("hover")
        assertThat(endpoint.request("textDocument/hover", null)).isCancelled()
    }

    @Test
    fun `closing the producer closes the server's stream, which stops its listening when the connection is cancelled`() {
        var closed = false
        val delegate =
            object : MessageProducer, Closeable {
                override fun listen(messageConsumer: MessageConsumer) {}

                override fun close() {
                    closed = true
                }
            }
        (PendingRequests().producer(delegate) as Closeable).close()
        assertThat(closed).isTrue()
    }

    private fun fakeEndpoint(reply: () -> CompletableFuture<Any?>) =
        object : Endpoint {
            override fun request(
                method: String,
                parameter: Any?,
            ): CompletableFuture<*> = reply()

            override fun notify(
                method: String,
                parameter: Any?,
            ) {}
        }
}
