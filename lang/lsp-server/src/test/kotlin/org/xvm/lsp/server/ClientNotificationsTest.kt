package org.xvm.lsp.server

import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.xvm.lsp.util.ExecutionTrace

class ClientNotificationsTest {
    @Test
    fun `refresh waits for initialized honors negotiation and coalesces outstanding work`() {
        val client = mock(LanguageClient::class.java)
        val calls = LinkedBlockingQueue<CompletableFuture<Void>>()
        doAnswer { CompletableFuture<Void>().also { calls.add(it) } }
            .`when`(client)
            .refreshCodeLenses()
        ClientRefresh { client }
            .use { refresh ->
                refresh.configure(setOf(ClientRefresh.Feature.LENSES))
                refresh.request(ClientRefresh.Feature.LENSES)
                verifyNoInteractions(client)
                refresh.initialized()
                refresh.request(ClientRefresh.Feature.INLAYS, ClientRefresh.Feature.FOLDING)
                verifyNoInteractions(client)
                refresh.request(ClientRefresh.Feature.LENSES)
                val first = requireNotNull(calls.poll(5, SECONDS))
                repeat(50) { refresh.request(ClientRefresh.Feature.LENSES) }
                assertThat(calls).isEmpty()
                first.complete(null)
                val second = requireNotNull(calls.poll(5, SECONDS))
                refresh.request(ClientRefresh.Feature.LENSES)
                refresh.close()
                second.complete(null)
                refresh.request(ClientRefresh.Feature.LENSES)
                assertThat(calls).isEmpty()
            }
    }

    @Test
    fun `runtime trace switches between off messages and verbose without request payloads`() {
        val client = mock(LanguageClient::class.java)
        val messages = LinkedBlockingQueue<LogTraceParams>()
        doAnswer { call ->
                messages.add(call.getArgument(0))
                null
            }
            .`when`(client)
            .logTrace(any())
        ClientTrace { client }
            .use { trace ->
                val span =
                    ExecutionTrace.span(
                        "lsp-request",
                        "textDocument/hover",
                        "file:///private/source.x",
                    )
                trace.completed(span, null)
                verifyNoInteractions(client)
                trace.configure("messages")
                trace.completed(span, null)
                val message = requireNotNull(messages.poll(5, SECONDS))
                assertThat(message.message)
                    .contains("textDocument/hover", "replied")
                    .doesNotContain("private")
                assertThat(message.verbose).isNull()
                trace.configure("verbose")
                trace.completed(span, -32801)
                val verbose = requireNotNull(messages.poll(5, SECONDS))
                assertThat(verbose.verbose).contains("request=${span.id}").doesNotContain("private")
                trace.configure("off")
                trace.completed(span, null)
                assertThat(messages).isEmpty()
                trace.close()
                trace.configure("verbose")
                trace.completed(span, null)
                assertThat(messages).isEmpty()
            }
    }
}
