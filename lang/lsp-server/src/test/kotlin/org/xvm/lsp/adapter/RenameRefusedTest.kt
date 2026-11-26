package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException

/** What a client sees of a failed rename: a refusal's reason as a request failure, any other failure as it is. */
class RenameRefusedTest {
    @Test
    fun `a refusal, also wrapped in a completion, reaches the client as a request failure with its reason`() {
        val refused = RenameRefused("needs a larger heap")
        listOf<Throwable>(refused, CompletionException(refused), CompletionException(CompletionException(refused))).forEach { failure ->
            assertThat(RenameRefused.asResponseError(failure)).isInstanceOfSatisfying(ResponseErrorException::class.java) {
                assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.RequestFailed.value)
                assertThat(it.responseError.message).isEqualTo("needs a larger heap")
            }
        }
    }

    @Test
    fun `a cancellation or any other failure stays as it is`() {
        listOf(CancellationException(), CompletionException(IllegalStateException("broken")), IllegalStateException()).forEach {
            assertThat(RenameRefused.asResponseError(it)).isSameAs(it)
        }
    }
}
