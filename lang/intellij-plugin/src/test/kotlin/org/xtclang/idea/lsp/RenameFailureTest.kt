package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException

/**
 * What the rename's hint says of a failed answer: nothing for the user's cancel; a change of the project that overtook the
 * server's proof, which a retry proves again, as ContentModified says; otherwise the server's reason, not the refusal of a
 * rename with no edit.
 */
class RenameFailureTest {
    @Test
    fun `a rename that a change of the project overtook says to invoke Rename again`() {
        val failure = CompletionException(failure(ResponseErrorCode.ContentModified, "The project changed while the rename was proven"))
        assertThat(renameFailure(failure)).isEqualTo(RENAME_OVERTAKEN)
    }

    @Test
    fun `a rename that the user cancels says nothing`() {
        // LSP4IJ's progress cancels the request's future, whose cancellation has no message
        listOf(CancellationException(), CompletionException(CancellationException())).forEach {
            assertThat(renameFailure(it)).isNull()
        }
    }

    @Test
    fun `a rename that the server can't prove says the server's reason`() {
        val reason = "This rename can't be proven while Library does not compile: fix the errors, then rename again"
        assertThat(renameFailure(CompletionException(failure(ResponseErrorCode.RequestFailed, reason)))).isEqualTo("Rename failed: $reason")
    }

    private fun failure(
        code: ResponseErrorCode,
        message: String,
    ) = ResponseErrorException(ResponseError(code, message, null))
}
