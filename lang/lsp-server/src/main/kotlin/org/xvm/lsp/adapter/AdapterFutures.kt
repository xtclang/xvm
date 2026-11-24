package org.xvm.lsp.adapter

import java.util.concurrent.CompletableFuture

/** Keep ownership of query cancellation when converting an asynchronous adapter result. */
internal fun <T, R> CompletableFuture<T>.mapCancellable(transform: (T) -> R): CompletableFuture<R> =
    thenApply(transform).also { mapped ->
        mapped.whenComplete { _, _ -> if (mapped.isCancelled) cancel(false) }
    }

/**
 * This future's answer, with a failure answered by the failure that [transform] makes of it, keeping ownership of query
 * cancellation: each cancellation asked of the returned future, also once it is done, is asked of this one, as it would
 * be of this one returned itself. [CompletableFuture.exceptionallyCompose] returns a future whose cancellation does not
 * reach this one.
 */
internal fun <T> CompletableFuture<T>.mapFailureCancellable(transform: (Throwable) -> Throwable): CompletableFuture<T> {
    val source = this
    val mapped =
        object : CompletableFuture<T>() {
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                source.cancel(mayInterruptIfRunning)
                return super.cancel(mayInterruptIfRunning)
            }
        }
    whenComplete { value, failure ->
        if (failure == null) mapped.complete(value) else mapped.completeExceptionally(runCatching { transform(failure) }.getOrElse { it })
    }
    return mapped
}

/** Cancellation follows both stages, including cancellation while the second stage is starting. */
internal fun <T, R> CompletableFuture<T>.composeCancellable(transform: (T) -> CompletableFuture<R>): CompletableFuture<R> {
    val result = CompletableFuture<R>()
    result.whenComplete { _, _ -> if (result.isCancelled) cancel(false) }
    whenComplete { value, failure ->
        if (failure != null) {
            result.completeExceptionally(failure)
        } else if (!result.isDone) {
            try {
                val next = transform(value)
                result.whenComplete { _, _ -> if (result.isCancelled) next.cancel(false) }
                next.whenComplete { answer, error ->
                    if (error == null) result.complete(answer) else result.completeExceptionally(error)
                }
            } catch (error: Throwable) {
                result.completeExceptionally(error)
            }
        }
    }
    return result
}
