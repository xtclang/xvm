package org.xtclang.idea.lsp

import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.MessageProducer
import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * The requests of one connection that wait for the server's reply. LSP4J keeps a request waiting when its connection
 * ends, as when the server stops or its project closes, so anything waiting for that reply would wait forever; the
 * requests still waiting are cancelled when the server's messages end, as VS Code's client does.
 */
internal class PendingRequests {
    private val waiting = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()

    @Volatile
    private var ended = false

    /** The endpoint whose requests are kept until they are answered or the connection ends. */
    fun endpoint(delegate: Endpoint): Endpoint =
        object : Endpoint {
            override fun request(
                method: String,
                parameter: Any?,
            ): CompletableFuture<*> = track(delegate.request(method, parameter))

            override fun notify(
                method: String,
                parameter: Any?,
            ) = delegate.notify(method, parameter)
        }

    /** The producer of the server's messages; when it stops listening, the connection has ended. */
    fun producer(delegate: MessageProducer): MessageProducer =
        object : MessageProducer, Closeable {
            override fun listen(messageConsumer: MessageConsumer) {
                try {
                    delegate.listen(messageConsumer)
                } finally {
                    end()
                }
            }

            // LSP4J closes the producer to stop its listening when the connection is cancelled
            override fun close() {
                (delegate as? Closeable)?.close()
            }
        }

    /** Cancels the requests still waiting, and any sent after the connection ended. */
    fun end() {
        ended = true
        waiting.toList().forEach { it.completeExceptionally(CancellationException(ENDED)) }
    }

    private fun track(future: CompletableFuture<*>): CompletableFuture<*> {
        if (!future.isDone) {
            waiting += future
            future.whenComplete { _, _ -> waiting -= future }
            // a request sent while the connection ended is not left waiting either
            if (ended) end()
        }
        return future
    }

    companion object {
        /** Why a request still waiting when its connection ended was cancelled. */
        const val ENDED = "The Ecstasy language server's connection ended before it replied."
    }
}
