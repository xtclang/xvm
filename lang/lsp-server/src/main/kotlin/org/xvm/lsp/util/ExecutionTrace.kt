package org.xvm.lsp.util

import com.google.gson.Gson
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Timing metadata only: never logs source buffers, protocol payloads or compiler objects. */
internal object ExecutionTrace {
    data class Span(
        val id: Long,
        val parent: Long?,
        val kind: String,
        val operation: String,
        val uri: String?,
        val created: Long = System.nanoTime(),
    )

    private data class Context(
        val span: Span,
        val apiDepth: Int,
    )

    private val logger = LoggerFactory.getLogger("org.xvm.lsp.trace")
    private val gson = Gson()
    private val ids = AtomicLong()
    private val events = AtomicLong()
    private val context = ThreadLocal<Context>()
    private val apiThreads = AtomicInteger()
    private val pid = ProcessHandle.current().pid()

    fun span(
        kind: String,
        operation: String,
        uri: String? = null,
    ): Span = Span(ids.incrementAndGet(), context.get()?.span?.id, kind, operation, uri)

    fun event(
        span: Span,
        event: String,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        if (!logger.isInfoEnabled) return
        val thread = Thread.currentThread()
        logger.info(
            "{}",
            gson.toJson(
                mapOf(
                    "sequence" to events.incrementAndGet(),
                    "time" to Instant.now().toString(),
                    "pid" to pid,
                    "thread" to thread.name,
                    "threadId" to thread.threadId(),
                    "id" to span.id,
                    "parent" to span.parent,
                    "kind" to span.kind,
                    "operation" to span.operation,
                    "uri" to span.uri,
                    "event" to event,
                    "elapsedMs" to elapsed(span.created),
                ) + fields,
            ),
        )
    }

    fun elapsed(start: Long): Double = (System.nanoTime() - start) / 1_000_000.0

    fun current(): Span? = context.get()?.span

    fun <T> within(
        span: Span?,
        action: () -> T,
    ): T {
        if (span == null) return action()
        val previous = context.get()
        context.set(Context(span, previous?.apiDepth ?: 0))
        return try {
            action()
        } finally {
            if (previous == null) context.remove() else context.set(previous)
        }
    }

    /** Counts threads inside compiler APIs, rather than counting nested calls as parallelism. */
    fun <T> api(
        operation: String,
        uri: String? = null,
        action: () -> T,
    ): T {
        val span = span("javatools", operation, uri)
        val previous = context.get()
        val depth = previous?.apiDepth ?: 0
        context.set(Context(span, depth + 1))
        val active = if (depth == 0) apiThreads.incrementAndGet() else apiThreads.get()
        event(span, "start", mapOf("activeApiThreads" to active, "depth" to depth))
        return try {
            action().also {
                event(
                    span,
                    "end",
                    mapOf("outcome" to "returned", "activeApiThreads" to apiThreads.get()),
                )
            }
        } catch (failure: Throwable) {
            event(
                span,
                "end",
                mapOf(
                    "outcome" to if (failure is CancellationException) "cancelled" else "failed",
                    "failure" to failure.javaClass.name,
                ),
            )
            throw failure
        } finally {
            if (depth == 0) apiThreads.decrementAndGet()
            if (previous == null) context.remove() else context.set(previous)
        }
    }
}
