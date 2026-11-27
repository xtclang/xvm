package org.xvm.lsp.adapter

import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException

/**
 * A rename that an adapter refuses for a reason the client should show, instead of answering it with no edit after
 * `prepareRename` accepted the name: one it can't make, or one it can't prove while the workspace is as it is, as while a
 * module does not compile, which [unproven] says. The server answers `textDocument/rename` and `xtc/rename` with the
 * reason as their error message, and with [UNPROVEN] as its data for an unproven one, which a client may ask again once
 * the workspace has changed ([asResponseError]).
 */
class RenameRefused(
    reason: String,
    val unproven: Boolean = false,
) : RuntimeException(reason) {
    companion object {
        /** The data of the error that answers a rename that can't be proven while the workspace is as it is. */
        const val UNPROVEN = "unproven"

        /**
         * [failure] as the client should see it: a [RenameRefused] as a request failure with its reason, which both
         * editors show; a [RenameOvertaken], or a cancellation that the client did not ask for, which a change of the
         * project asks for, or a later rename of the document that replaces this one, as ContentModified, for the client
         * to ask again of the project as it then is, which names the [operation], a rename or a move; and any other
         * failure as it is. The client's own cancellation completes the future that [mapFailureCancellable] returns
         * before the adapter's ends, so it stays a cancellation and still stops the proof's compiles.
         */
        fun asResponseError(
            failure: Throwable,
            operation: String = "rename",
        ): Throwable =
            when (val cause = generateSequence(failure) { (it as? CompletionException)?.cause }.last()) {
                is RenameRefused ->
                    ResponseErrorException(
                        ResponseError(ResponseErrorCode.RequestFailed, cause.message, UNPROVEN.takeIf { cause.unproven }),
                    )

                is RenameOvertaken, is CancellationException ->
                    ResponseErrorException(ResponseError(ResponseErrorCode.ContentModified, RenameOvertaken.reason(operation), null))

                else -> failure
            }
    }
}

/**
 * A rename whose proof a change of the project overtook: the sources it proved the edit for changed while it did, so the
 * edit could be of sources that are no longer there. The server answers it with ContentModified
 * ([RenameRefused.asResponseError]), and the same rename asked again proves the project as it then is.
 */
class RenameOvertaken : RuntimeException(REASON) {
    companion object {
        /** What the server says of a rename that a change of the project overtook. */
        const val REASON = "The project changed while the rename was proven"

        /** What the server says of an [operation], a rename or a move, that a change of the project overtook. */
        fun reason(operation: String): String = "The project changed while the $operation was proven"
    }
}
