package org.xvm.lsp.server

import java.util.concurrent.ConcurrentHashMap
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.xvm.lsp.util.ExecutionTrace

/**
 * Server-side receive-to-write latency, including asynchronous waits and response serialization.
 */
internal class ProtocolTrace(private val clientTrace: ClientTrace? = null) : AutoCloseable {
    private val incoming = ConcurrentHashMap<Either<String, Number>, ExecutionTrace.Span>()
    private val outgoing = ConcurrentHashMap<Either<String, Number>, ExecutionTrace.Span>()

    fun wrap(
        next: MessageConsumer,
        received: Boolean,
    ): MessageConsumer = MessageConsumer { message ->
        val requests = if (received) incoming else outgoing
        val replies = if (received) outgoing else incoming
        when (message) {
            is RequestMessage -> {
                val span = ExecutionTrace.span("lsp-request", message.method)
                requests[message.rawId] = span
                ExecutionTrace.event(
                    span,
                    "start",
                    mapOf(
                        "rpcId" to message.id,
                        "direction" to if (received) "from-client" else "to-client",
                    ),
                )
                try {
                    ExecutionTrace.within(span) { next.consume(message) }
                } catch (failure: Throwable) {
                    requests.remove(message.rawId, span)
                    ExecutionTrace.event(
                        span,
                        "end",
                        mapOf("outcome" to "transport-failed", "failure" to failure.javaClass.name),
                    )
                    throw failure
                }
            }

            is ResponseMessage -> {
                val span = replies.remove(message.rawId)
                try {
                    next.consume(message)
                    span?.let {
                        if (!received) clientTrace?.completed(it, message.error?.code)
                        ExecutionTrace.event(
                            it,
                            "end",
                            mapOf(
                                "rpcId" to message.id,
                                "outcome" to if (message.error == null) "replied" else "error",
                                "errorCode" to message.error?.code,
                                "boundary" to
                                    if (received) "client-reply-received"
                                    else "server-reply-written",
                            ),
                        )
                    }
                } catch (failure: Throwable) {
                    span?.let {
                        ExecutionTrace.event(
                            it,
                            "end",
                            mapOf(
                                "outcome" to "transport-failed",
                                "failure" to failure.javaClass.name,
                            ),
                        )
                    }
                    throw failure
                }
            }

            is NotificationMessage -> {
                val metadata =
                    when (val params = message.params) {
                        is DidOpenTextDocumentParams -> {
                            mapOf(
                                "uri" to params.textDocument.uri,
                                "version" to params.textDocument.version,
                            )
                        }

                        is DidChangeTextDocumentParams -> {
                            mapOf(
                                "uri" to params.textDocument.uri,
                                "version" to params.textDocument.version,
                            )
                        }

                        is DidCloseTextDocumentParams -> {
                            mapOf("uri" to params.textDocument.uri)
                        }

                        is PublishDiagnosticsParams -> {
                            mapOf(
                                "uri" to params.uri,
                                "version" to params.version,
                                "diagnostics" to params.diagnostics.size,
                            )
                        }

                        else -> {
                            emptyMap()
                        }
                    }
                val span =
                    ExecutionTrace.span(
                        "lsp-notification",
                        message.method,
                        metadata["uri"] as? String,
                    )
                ExecutionTrace.event(span, if (received) "received" else "sending", metadata)
                try {
                    ExecutionTrace.within(span) { next.consume(message) }
                    ExecutionTrace.event(span, "dispatched")
                } catch (failure: Throwable) {
                    ExecutionTrace.event(span, "failed", mapOf("failure" to failure.javaClass.name))
                    throw failure
                }
            }

            else -> {
                next.consume(message)
            }
        }
    }

    override fun close() {
        listOf(incoming, outgoing).forEach { requests ->
            requests.forEach { (id, span) ->
                if (requests.remove(id, span))
                    ExecutionTrace.event(span, "end", mapOf("outcome" to "transport-closed"))
            }
        }
    }
}
