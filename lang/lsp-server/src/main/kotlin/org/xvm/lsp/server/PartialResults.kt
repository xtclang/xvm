package org.xvm.lsp.server

import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/** Publish detached result batches outside compiler/document locks, before the final response. */
internal class PartialResults(
    private val client: () -> LanguageClient?,
    private val dispatcher: ExecutorService =
        Executors.newSingleThreadExecutor(Thread.ofVirtual().name("lsp-partial-results").factory()),
) : AutoCloseable {
    data class Plan<T>(
        val batches: List<Any>,
        val remainder: T,
    )

    private val closed = AtomicBoolean()
    private val pending = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()

    fun <T> publish(
        token: Either<String, Int>?,
        source: CompletableFuture<T>,
        checkCurrent: () -> Unit,
        plan: (T) -> Plan<T>,
    ): CompletableFuture<T> {
        if (token == null) return source
        val result = CompletableFuture<T>()
        pending.add(result)
        result.whenCompleteAsync { _, failure ->
            pending.remove(result)
            if (failure != null) source.cancel(false)
        }
        if (closed.get()) result.cancel(false)
        source.whenComplete { value, failure ->
            try {
                dispatcher.execute {
                    if (result.isDone) return@execute
                    if (failure != null) {
                        result.completeExceptionally(failure)
                        return@execute
                    }
                    try {
                        checkCurrent()
                        val connection = client()
                        if (connection == null) {
                            result.complete(value)
                            return@execute
                        }
                        val publication = plan(value)
                        publication.batches.forEach { batch ->
                            if (result.isDone || closed.get()) return@execute
                            checkCurrent()
                            connection.notifyProgress(ProgressParams(token, Either.forRight(batch)))
                        }
                        checkCurrent()
                        result.complete(publication.remainder)
                    } catch (error: Exception) {
                        result.completeExceptionally(error)
                    }
                }
            } catch (_: RejectedExecutionException) {
                result.cancel(false)
            }
        }
        return result
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        pending.forEach { it.cancel(false) }
        dispatcher.shutdown()
    }

    companion object {
        const val BATCH_SIZE = 64

        fun <T> list(values: List<T>): Plan<List<T>> = Plan(values.chunked(BATCH_SIZE), emptyList())

        fun <L, R> eitherLists(values: Either<List<L>, List<R>>): Plan<Either<List<L>, List<R>>> =
            if (values.isLeft) {
                Plan(values.left.chunked(BATCH_SIZE), Either.forLeft(emptyList()))
            } else {
                Plan(values.right.chunked(BATCH_SIZE), Either.forRight(emptyList()))
            }
    }
}
