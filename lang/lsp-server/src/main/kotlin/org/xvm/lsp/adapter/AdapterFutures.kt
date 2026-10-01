package org.xvm.lsp.adapter

import java.util.concurrent.CompletableFuture

/** Keep ownership of query cancellation when converting an asynchronous adapter result. */
internal fun <T, R> CompletableFuture<T>.mapCancellable(transform: (T) -> R): CompletableFuture<R> =
    thenApply(transform).also { mapped ->
        mapped.whenComplete { _, _ -> if (mapped.isCancelled) cancel(false) }
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
