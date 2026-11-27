package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 * What a client sees of a failed rename: a refusal's reason as a request failure, which says in its data whether the
 * workspace as it is left it unproven; a change of the project that overtook the proof, or a cancellation that the client
 * did not ask for, as ContentModified; the client's own cancellation as a cancellation; any other failure as it is.
 */
class RenameRefusedTest {
    @Test
    fun `a refusal, also wrapped in a completion, reaches the client as a request failure with its reason`() {
        val refused = RenameRefused("needs a larger heap")
        listOf<Throwable>(refused, CompletionException(refused), CompletionException(CompletionException(refused))).forEach { failure ->
            assertThat(RenameRefused.asResponseError(failure)).isInstanceOfSatisfying(ResponseErrorException::class.java) {
                assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.RequestFailed.value)
                assertThat(it.responseError.message).isEqualTo("needs a larger heap")
                assertThat(it.responseError.data).isNull()
            }
        }
    }

    @Test
    fun `an unproven refusal says so in its data, for a client to ask again once the workspace has changed`() {
        val refused = RenameRefused("Library does not compile", unproven = true)
        assertThat(RenameRefused.asResponseError(CompletionException(refused))).isInstanceOfSatisfying(ResponseErrorException::class.java) {
            assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.RequestFailed.value)
            assertThat(it.responseError.message).isEqualTo("Library does not compile")
            assertThat(it.responseError.data).isEqualTo(RenameRefused.UNPROVEN)
        }
    }

    @Test
    fun `a proof that a change of the project overtook, or cancelled without the client, reaches it as ContentModified`() {
        listOf<Throwable>(RenameOvertaken(), CompletionException(RenameOvertaken()), CancellationException()).forEach { failure ->
            assertThat(RenameRefused.asResponseError(failure)).isInstanceOfSatisfying(ResponseErrorException::class.java) {
                assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.ContentModified.value)
                assertThat(it.responseError.message).isEqualTo(RenameOvertaken.REASON)
            }
        }
    }

    @Test
    fun `a cancellation that the client asks for stays one and reaches the proof`() {
        val proof = CompletableFuture<Unit>()
        val answer = proof.mapFailureCancellable(RenameRefused::asResponseError)
        assertThat(answer.cancel(false)).isTrue()
        assertThat(proof.isCancelled).isTrue()
        assertThat(answer.isCancelled).isTrue()
    }

    @Test
    fun `any other failure stays as it is`() {
        listOf(CompletionException(IllegalStateException("broken")), IllegalStateException()).forEach {
            assertThat(RenameRefused.asResponseError(it)).isSameAs(it)
        }
    }
}
