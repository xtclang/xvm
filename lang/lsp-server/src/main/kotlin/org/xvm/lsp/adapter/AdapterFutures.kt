package org.xvm.lsp.adapter

import java.util.concurrent.CompletableFuture

/** Keep ownership of query cancellation when converting an asynchronous adapter result. */
internal fun <T, R> CompletableFuture<T>.mapCancellable(transform: (T) -> R): CompletableFuture<R> =
    thenApply(transform).also { mapped ->
        mapped.whenComplete { _, _ -> if (mapped.isCancelled) cancel(false) }
    }
