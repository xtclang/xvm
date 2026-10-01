package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.Test

class ProtocolLifecycleTest {
    @Test
    fun `failed initialization can retry and late client replies survive shutdown`() {
        val delivered = mutableListOf<Message>()
        val lifecycle = ProtocolLifecycle()
        val outgoing = lifecycle.wrap(MessageConsumer {}, false)
        val incoming = lifecycle.wrap(MessageConsumer(delivered::add), true)

        fun initialize(id: String) =
            incoming.consume(
                RequestMessage().apply {
                    setId(id)
                    method = "initialize"
                },
            )
        initialize("failed")
        outgoing.consume(
            ResponseMessage().apply {
                setId("failed")
                error = ResponseError(ResponseErrorCode.InvalidParams, "Invalid initialization", null)
            },
        )
        initialize("retry")
        outgoing.consume(
            ResponseMessage().apply {
                setId("retry")
                result = emptyMap<String, Any>()
            },
        )
        incoming.consume(
            RequestMessage().apply {
                setId("shutdown")
                method = "shutdown"
            },
        )
        val reply =
            ResponseMessage().apply {
                setId("pending-configuration")
                result = emptyList<Any>()
            }
        incoming.consume(reply)
        assertThat(delivered.filterIsInstance<RequestMessage>().map { it.id }).containsExactly("failed", "retry", "shutdown")
        assertThat(delivered.last()).isSameAs(reply)
    }

    @Test
    fun `handshake gates requests notifications duplicate initialization and shutdown`() {
        val delivered = mutableListOf<Message>()
        val written = mutableListOf<Message>()
        val lifecycle = ProtocolLifecycle()
        val outgoing = lifecycle.wrap(MessageConsumer { written.add(it) }, false)
        val incoming = lifecycle.wrap(MessageConsumer { delivered.add(it) }, true)

        fun request(
            id: Int,
            method: String,
        ) = incoming.consume(
            RequestMessage().apply {
                setId(id)
                this.method = method
            },
        )

        fun notify(method: String) = incoming.consume(NotificationMessage().apply { this.method = method })

        notify("textDocument/didOpen")
        request(1, "textDocument/hover")
        assertThat(delivered).isEmpty()
        assertThat((written.last() as ResponseMessage).error.code)
            .isEqualTo(ResponseErrorCode.ServerNotInitialized.value)
        request(2, "initialize")
        request(3, "textDocument/hover")
        assertThat(delivered).hasSize(1)
        outgoing.consume(
            ResponseMessage().apply {
                setId(2)
                result = emptyMap<String, Any>()
            },
        )
        notify("initialized")
        notify("initialized")
        request(4, "initialize")
        assertThat((written.last() as ResponseMessage).error.code)
            .isEqualTo(ResponseErrorCode.InvalidRequest.value)
        request(5, "textDocument/hover")
        request(6, "shutdown")
        request(7, "textDocument/hover")
        notify("textDocument/didChange")
        notify("exit")
        assertThat(
            delivered.map {
                when (it) {
                    is RequestMessage -> it.method
                    is NotificationMessage -> it.method
                    else -> "response"
                }
            },
        ).containsExactly("initialize", "initialized", "textDocument/hover", "shutdown", "exit")
    }
}
