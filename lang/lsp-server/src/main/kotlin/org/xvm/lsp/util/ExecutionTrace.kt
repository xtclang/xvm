package org.xvm.lsp.util

import com.google.gson.Gson
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Timing metadata, and at DEBUG [details], such as what a diagnostic report answered by result IDs and item codes:
 * never logs source buffers, protocol payloads or compiler objects.
 */
internal object ExecutionTrace {
    private val logger = LoggerFactory.getLogger("org.xvm.lsp.trace")
    private val gson = Gson()
    private val ids = AtomicLong()
    private val events = AtomicLong()
    private val context = ThreadLocal<Context>()
    private val apiThreads = AtomicInteger()
    private val pid = ProcessHandle.current().pid()

    /** The capital letters of a phase's name, each of which starts a word after its first. */
    private val capital = Regex("[A-Z]")

    /** Operation identity, parent and monotonic start time propagated across asynchronous work. */
    data class Span(
        val id: Long,
        val parent: Long?,
        val kind: String,
        val operation: String,
        val uri: String?,
        val created: Long = System.nanoTime(),
    )

    /**
     * When a span reached each of its phases, in milliseconds from its start, which its end event carries as
     * `<phase>Ms` fields, so that at INFO a span is its start and its end, and its start alone shows one that never
     * ends. At DEBUG, as the IntelliJ playbook's runs have it, each phase is also an event when it is reached, named as
     * the phase in kebab case (`analysis-ready` for `analysisReady`), so that one that never ends shows how far it came.
     */
    class Phases(
        private val span: Span,
    ) {
        private val reached = ConcurrentHashMap<String, Double>()

        /** Notes that the span has reached [phase], and writes it at DEBUG; only the first time counts. */
        fun reached(phase: String) {
            if (reached.putIfAbsent(phase, elapsed(span.created)) == null && logger.isDebugEnabled) {
                event(span, phase.replace(capital) { "-${it.value.lowercase()}" })
            }
        }

        /** The phases reached so far, in the order reached, as the end event's fields. */
        fun fields(): Map<String, Double> = reached.entries.sortedBy { it.value }.associate { (phase, ms) -> "${phase}Ms" to ms }
    }

    /** Active trace span and API nesting depth for the current scoped execution. */
    private data class Context(
        val span: Span,
        val apiDepth: Int,
    )

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
                    "thread" to thread.name.ifEmpty { "${if (thread.isVirtual) "virtual" else "platform"}-${thread.threadId()}" },
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

    /**
     * What [fields] gives, when the trace is at DEBUG, as a test run's is (`xtc.trace.level`), and nothing otherwise, so
     * that the trace that each user has keeps to timing; and when [fields] fails, only that it did: the trace never
     * fails the work it describes.
     */
    fun details(fields: () -> Map<String, Any?>): Map<String, Any?> =
        if (!logger.isDebugEnabled) {
            emptyMap()
        } else {
            try {
                fields()
            } catch (failure: Exception) {
                mapOf("detailsFailed" to failure.javaClass.name)
            }
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
