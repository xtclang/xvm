package org.xvm.lsp.adapter

import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import java.util.concurrent.CompletionException

/**
 * A rename that an adapter refuses for a reason the client should show, instead of answering it with no edit after
 * `prepareRename` accepted the name. The server answers `textDocument/rename` and `xtc/rename` with the reason as their
 * error message ([asResponseError]).
 */
class RenameRefused(
    reason: String,
) : RuntimeException(reason) {
    companion object {
        /**
         * [failure] as the client should see it: a [RenameRefused] it wraps as a request failure with its reason, which
         * both editors show, and any other failure as it is, so that a client's cancellation still reaches the adapter's
         * future and stops the proof's compiles.
         */
        fun asResponseError(failure: Throwable): Throwable =
            when (val cause = generateSequence(failure) { (it as? CompletionException)?.cause }.last()) {
                is RenameRefused -> ResponseErrorException(ResponseError(ResponseErrorCode.RequestFailed, cause.message, null))
                else -> failure
            }
    }
}
