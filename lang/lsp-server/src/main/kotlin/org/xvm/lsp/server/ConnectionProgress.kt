package org.xvm.lsp.server

import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.WorkDoneProgressNotification
import org.eclipse.lsp4j.WorkDoneProgressReport
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serializes progress transport calls outside compiler/document locks. Each token owns only its
 * request future, never the shared analysis it may be waiting for. Fast queries need no popup.
 */
internal class ConnectionProgress(
    private val client: () -> LanguageClient?,
    private val delayMillis: Long = 300,
    private val dispatcher: ExecutorService =
        Executors.newSingleThreadExecutor(Thread.ofVirtual().name("lsp-progress").factory()),
    private val reportScheduler: Executor = CompletableFuture.delayedExecutor(1, SECONDS),
    private val creationDeadline: () -> CompletableFuture<Void> = {
        CompletableFuture<Void>().completeOnTimeout(null, 10, SECONDS)
    },
) : AutoCloseable {
    private data class Entry(
        val title: String,
        val result: CompletableFuture<*>,
        val details: (() -> String)?,
    )

    private val canCreate = AtomicBoolean()
    private val closed = AtomicBoolean()

    // Confined to dispatcher, including completion and cancellation callbacks.
    private val entries = mutableMapOf<Either<String, Int>, Entry>()
    private val begun = mutableSetOf<Either<String, Int>>()

    fun initialized(supported: Boolean) {
        canCreate.set(supported)
    }

    fun <T> track(
        title: String,
        suppliedToken: Either<String, Int>?,
        result: CompletableFuture<T>,
        details: (() -> String)? = null,
    ): CompletableFuture<T> {
        if (closed.get() || (suppliedToken == null && !canCreate.get())) return result
        val token = suppliedToken ?: Either.forLeft("xtc-${UUID.randomUUID()}")
        val entry = Entry(title, result, details)
        val start =
            Runnable {
                dispatch {
                    if (closed.get() || result.isDone || token in entries) return@dispatch
                    entries[token] = entry
                    result.whenComplete { _, _ -> dispatch { finish(token, entry) } }
                    if (suppliedToken != null) {
                        begin(token, entry)
                    } else {
                        create(token, entry)
                    }
                }
            }
        if (suppliedToken != null) {
            start.run()
        } else {
            CompletableFuture.delayedExecutor(delayMillis, MILLISECONDS).execute(start)
        }
        return result
    }

    private fun create(
        token: Either<String, Int>,
        entry: Entry,
    ) {
        val acknowledgement =
            runCatching {
                client()?.createProgress(WorkDoneProgressCreateParams(token))
            }.getOrNull()
        if (acknowledgement == null) {
            entries.remove(token, entry)
            return
        }
        // Do not time out the RPC's own future: the client can still create the token later.
        val bounded = CompletableFuture<Void>()
        val deadline = creationDeadline()
        deadline.thenRun {
            bounded.completeExceptionally(TimeoutException("Progress creation timed out"))
        }
        bounded.whenComplete { _, failure ->
            deadline.cancel(false)
            dispatch {
                if (failure == null) begin(token, entry) else entries.remove(token, entry)
            }
        }
        acknowledgement.whenComplete { _, failure ->
            if (failure != null) {
                bounded.completeExceptionally(failure)
            } else if (!bounded.complete(null)) {
                dispatch {
                    // A successful late acknowledgement owns a client-side progress registration.
                    // Retire it without reattaching cancellation to work that outlived the deadline.
                    if (!closed.get()) {
                        send(
                            token,
                            WorkDoneProgressBegin().apply {
                                title = entry.title
                                cancellable = false
                            },
                        )
                        send(token, WorkDoneProgressEnd().apply { message = "Progress expired" })
                    }
                }
            }
        }
    }

    private fun begin(
        token: Either<String, Int>,
        entry: Entry,
    ) {
        if (closed.get() || entries[token] !== entry) return
        begun.add(token)
        send(
            token,
            WorkDoneProgressBegin().apply {
                title = entry.title
                cancellable = !entry.result.isDone
                message = entry.details?.invoke()
            },
        )
        if (entry.result.isDone) {
            finish(token, entry)
        } else if (entry.details != null) {
            scheduleReport(token, entry)
        }
    }

    private fun scheduleReport(
        token: Either<String, Int>,
        entry: Entry,
    ) {
        reportScheduler.execute {
            dispatch {
                if (closed.get() || entry.result.isDone || entries[token] !== entry || token !in begun) return@dispatch
                send(token, WorkDoneProgressReport().apply { message = entry.details?.invoke() })
                scheduleReport(token, entry)
            }
        }
    }

    private fun finish(
        token: Either<String, Int>,
        entry: Entry,
    ) {
        if (token !in begun && !closed.get()) return
        if (!entries.remove(token, entry)) return
        if (begun.remove(token)) {
            send(
                token,
                WorkDoneProgressEnd().apply {
                    message =
                        when {
                            entry.result.isCancelled -> "Canceled"
                            entry.result.isCompletedExceptionally -> "Failed or superseded"
                            else -> "Completed"
                        }
                },
            )
        }
    }

    fun report(
        result: CompletableFuture<*>,
        message: String,
        percent: Int,
    ) = dispatch {
        if (closed.get() || result.isDone) return@dispatch
        entries.entries
            .firstOrNull { it.value.result === result && it.key in begun }
            ?.let { (token, _) ->
                send(
                    token,
                    WorkDoneProgressReport().apply {
                        this.message = message
                        percentage = percent.coerceIn(0, 100)
                    },
                )
            }
    }

    fun cancel(token: Either<String, Int>) = dispatch { entries[token]?.result?.cancel(false) }

    private fun send(
        token: Either<String, Int>,
        value: WorkDoneProgressNotification,
    ) {
        runCatching { client()?.notifyProgress(ProgressParams(token, Either.forLeft(value))) }
            .onFailure { logger.debug("Unable to publish progress", it) }
    }

    private fun dispatch(block: () -> Unit) {
        try {
            dispatcher.execute(block)
        } catch (_: RejectedExecutionException) {
            // Connection already closed.
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        dispatch {
            entries.toMap().forEach { (token, entry) ->
                entry.result.cancel(false)
                finish(token, entry)
            }
            dispatcher.shutdown()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConnectionProgress::class.java)
    }
}
