package org.xvm.lsp.server

import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.services.LanguageClient
import org.xvm.lsp.util.ExecutionTrace
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runtime client trace preferences affect protocol timing only, never expose source or payloads.
 */
internal class ClientTrace(
    private val client: () -> LanguageClient?,
) : AutoCloseable {
    private enum class Level(
        val value: String,
    ) {
        OFF("off"),
        MESSAGES("messages"),
        VERBOSE("verbose"),
    }

    private val level = AtomicReference(Level.OFF)
    private val closed = AtomicBoolean()

    fun configure(value: String?) {
        Level.entries.firstOrNull { it.value == value }?.let(level::set)
    }

    fun completed(
        span: ExecutionTrace.Span,
        errorCode: Int?,
    ) {
        if (closed.get() || level.get() == Level.OFF) return
        val elapsed = ExecutionTrace.elapsed(span.created)
        CompletableFuture.runAsync {
            val current = level.get()
            if (!closed.get() && current != Level.OFF) {
                runCatching {
                    client()
                        ?.logTrace(
                            LogTraceParams(
                                "${span.operation}: ${if (errorCode == null) "replied" else "error $errorCode"} in ${elapsed.toLong()} ms",
                                if (current == Level.VERBOSE) {
                                    "request=${span.id}; parent=${span.parent}; boundary=response-written"
                                } else {
                                    null
                                },
                            ),
                        )
                }
            }
        }
    }

    override fun close() {
        closed.set(true)
    }
}
