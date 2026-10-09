package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Answers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.xvm.lsp.util.ExecutionTrace
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

internal class ClientNotificationsTest {
    @ParameterizedTest
    @EnumSource(ClientRefresh.Feature::class)
    fun `each negotiated refresh recovers after refusal and retires pending replies on close`(feature: ClientRefresh.Feature) {
        val methods =
            mapOf(
                ClientRefresh.Feature.DIAGNOSTICS to "refreshDiagnostics",
                ClientRefresh.Feature.TOKENS to "refreshSemanticTokens",
                ClientRefresh.Feature.INLAYS to "refreshInlayHints",
                ClientRefresh.Feature.LENSES to "refreshCodeLenses",
                ClientRefresh.Feature.FOLDING to "refreshFoldingRanges",
            )
        val calls = LinkedBlockingQueue<Pair<String, CompletableFuture<Void>>>()
        val client =
            mock(LanguageClient::class.java) { call ->
                if (call.method.name in methods.values) {
                    CompletableFuture<Void>().also { calls.add(call.method.name to it) }
                } else {
                    Answers.RETURNS_DEFAULTS.answer(call)
                }
            }
        ClientRefresh { client }.use { refresh ->
            refresh.configure(setOf(feature))
            refresh.request(*ClientRefresh.Feature.entries.toTypedArray())
            assertThat(calls).isEmpty()
            refresh.initialized()
            refresh.request(*ClientRefresh.Feature.entries.toTypedArray())
            val first = requireNotNull(calls.poll(5, SECONDS))
            assertThat(first.first).isEqualTo(methods.getValue(feature))
            repeat(20) { refresh.request(*ClientRefresh.Feature.entries.toTypedArray()) }
            assertThat(calls).isEmpty()
            first.second.completeExceptionally(IllegalStateException("Client refused refresh"))
            val retry = requireNotNull(calls.poll(5, SECONDS))
            assertThat(retry.first).isEqualTo(first.first)
            refresh.request(feature)
            refresh.close()
            retry.second.complete(null)
            refresh.request(*ClientRefresh.Feature.entries.toTypedArray())
            assertThat(calls).isEmpty()
        }
    }

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
        }.`when`(client)
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
                trace.completed(span, ResponseErrorCode.ContentModified.value)
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
