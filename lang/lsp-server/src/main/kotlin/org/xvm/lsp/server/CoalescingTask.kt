package org.xvm.lsp.server

import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs [action] on its own daemon thread when requested. Requests made while a run is queued join that run;
 * a request made during a run queues exactly one more, so every request is followed by a run that starts
 * after it. A burst of notifications therefore costs one or two runs, never one per event.
 */
internal class CoalescingTask(
    private val name: String,
    private val action: () -> Unit,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, name).apply { isDaemon = true } }
    private val queued = AtomicBoolean()

    @Volatile
    private var latest: CompletableFuture<Void> = CompletableFuture.completedFuture(null)

    fun request() {
        if (!queued.compareAndSet(false, true)) return
        latest =
            try {
                CompletableFuture.runAsync(
                    {
                        queued.set(false)
                        try {
                            action()
                        } catch (failure: Exception) {
                            logger.error("{} failed", name, failure)
                        }
                    },
                    executor,
                )
            } catch (_: RejectedExecutionException) {
                queued.set(false)
                CompletableFuture.completedFuture(null)
            }
    }

    /** Completes when every run requested so far has finished; the thread runs them in order. */
    fun idle(): CompletableFuture<Void> = latest

    override fun close() {
        executor.shutdownNow()
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(CoalescingTask::class.java)
    }
}
