package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.util.ExecutionTrace
import org.xvm.lsp.util.ProgressLabels
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

/** Observes the existing worker; no compiler work or future completion runs under this lock. */
internal class CompilerQueueTrace {
    private enum class Phase {
        DEBOUNCING,
        QUEUED,
        RUNNING,
    }

    private data class Job(
        val span: ExecutionTrace.Span,
        val submission: Long,
        val phase: Phase,
    )

    private val queue = queues.incrementAndGet()
    private val lock = Any()
    private val submitted = AtomicLong()
    private val enqueued = AtomicLong()
    private val started = AtomicLong()
    private val jobs = linkedMapOf<Long, Job>()
    private val readyOrder = linkedSetOf<Long>()

    fun task(
        kind: String,
        uri: String,
        result: CompletableFuture<*>,
        action: () -> Unit,
    ): Work = Work(kind, uri, result, action)

    inner class Work
        internal constructor(
            kind: String,
            uri: String,
            private val result: CompletableFuture<*>,
            private val action: () -> Unit,
        ) : Runnable {
            private val span = ExecutionTrace.span("compiler-queue", kind, uri)

            init {
                synchronized(lock) {
                    jobs[span.id] = Job(span, submitted.incrementAndGet(), Phase.DEBOUNCING)
                    log(span, "submitted")
                }
                result.whenComplete { _, failure ->
                    synchronized(lock) {
                        val job = jobs[span.id] ?: return@synchronized
                        if (job.phase != Phase.RUNNING) {
                            jobs.remove(span.id)
                            readyOrder.remove(span.id)
                            log(
                                span,
                                "removed",
                                mapOf(
                                    "outcome" to
                                        if (result.isCancelled) {
                                            "cancelled-before-start"
                                        } else {
                                            "completed-before-start"
                                        },
                                ),
                            )
                        } else if (failure != null) {
                            // Cancellation signals do not prove that cooperative compiler work has
                            // returned.
                            log(span, "cancel-or-failure-signalled")
                        }
                    }
                }
            }

            /** Called at the existing executor submission, after any debounce interval. */
            fun ready(): Work =
                apply {
                    synchronized(lock) {
                        jobs[span.id]?.let { jobs[span.id] = it.copy(phase = Phase.QUEUED) }
                        if (span.id in jobs) readyOrder += span.id
                        log(span, "queued", mapOf("enqueueOrder" to enqueued.incrementAndGet()))
                    }
                }

            override fun run() {
                val tracked =
                    synchronized(lock) {
                        val job = jobs[span.id]
                        if (job == null) {
                            false
                        } else {
                            jobs[span.id] = job.copy(phase = Phase.RUNNING)
                            readyOrder.remove(span.id)
                            log(
                                span,
                                "start",
                                mapOf(
                                    "executionOrder" to started.incrementAndGet(),
                                    "waitMs" to ExecutionTrace.elapsed(span.created),
                                ),
                            )
                            true
                        }
                    }
                // Preserve the original runnable even if cancellation raced with dequeueing it.
                if (!tracked) {
                    action()
                    return
                }
                val begin = System.nanoTime()
                try {
                    ExecutionTrace.within(span, action)
                } finally {
                    synchronized(lock) {
                        jobs.remove(span.id)
                        val outcome =
                            when {
                                result.isCancelled -> "cancelled"
                                result.isCompletedExceptionally -> "failed"
                                result.isDone -> "completed"
                                else -> "incomplete"
                            }
                        log(
                            span,
                            "end",
                            mapOf("outcome" to outcome, "runMs" to ExecutionTrace.elapsed(begin)),
                        )
                    }
                }
            }
        }

    /** Copied metadata only; never enters compiler code or waits for the worker. */
    fun snapshot(): Map<String, Any> =
        synchronized(lock) {
            fun Job.label() = "#${this.span.id} ${this.span.operation} ${this.span.uri}"
            val queued = readyOrder.mapNotNull(jobs::get).map { it.label() }
            val waiting = jobs.values.filter { it.phase == Phase.DEBOUNCING }.map { it.label() }
            val running = jobs.values.filter { it.phase == Phase.RUNNING }.map { it.label() }
            mapOf(
                "queue" to queue,
                "submittedTotal" to submitted.get(),
                "startedTotal" to started.get(),
                "queueSize" to queued.size,
                "queuedJobs" to queued,
                "debouncingSize" to waiting.size,
                "debouncingJobs" to waiting,
                "runningSize" to running.size,
                "runningJobs" to running,
            )
        }

    /** UI text from copied queue metadata only; never inspects an AST or enters compiler locks. */
    fun progressDescription(): String? {
        val current = synchronized(lock) { jobs.values.toList() }

        fun Job.label(): String {
            val operation =
                when (span.operation) {
                    "compile" -> {
                        "compiling"
                    }

                    "project-REFERENCES" -> {
                        "finding references in"
                    }

                    "project-DIAGNOSTICS" -> {
                        "checking diagnostics in"
                    }

                    "rename-proof", "project-RENAME", "project-RENAME_PROPOSAL", "project-FILE_RENAME" -> {
                        "checking rename in"
                    }

                    else -> {
                        val kind =
                            span.operation
                                .removePrefix("project-")
                                .removePrefix("cursor-")
                                .replace('_', ' ')
                                .lowercase(Locale.ROOT)
                        "checking $kind in"
                    }
                }
            return "$operation ${ProgressLabels.source(span.uri.orEmpty())}"
        }
        return buildList {
            current.filter { it.phase == Phase.RUNNING }.forEach { add(it.label()) }
            val queued = current.count { it.phase == Phase.QUEUED }
            if (queued > 0) add("$queued ${if (queued == 1) "job" else "jobs"} queued")
            current.firstOrNull { it.phase == Phase.DEBOUNCING }?.let {
                add("waiting for edits to settle: ${it.label()}")
            }
        }.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private fun log(
        span: ExecutionTrace.Span,
        event: String,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        ExecutionTrace.event(span, event, snapshot() + fields)
    }

    private companion object {
        val queues = AtomicLong()
    }
}
