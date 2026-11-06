package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

/** The client's requests that the server has received and not yet answered, as `xtc/busy` counts them. */
class ProtocolTraceTest {
    @Test
    fun `a request is open from its receipt until its answer is written, and the asking request is left out`() {
        val open = ConcurrentHashMap<Any, String>()
        ProtocolTrace(open = open).use { trace ->
            val received = trace.wrap(MessageConsumer {}, received = true)
            val written = trace.wrap(MessageConsumer {}, received = false)
            assertThat(answering(open)).isZero()

            received.consume(request("1", "textDocument/hover"))
            received.consume(request("2", BUSY))
            assertThat(answering(open)).isEqualTo(1)
            assertThat(open).hasSize(2)

            written.consume(response("1"))
            assertThat(answering(open)).isZero()
            written.consume(response("2"))
            assertThat(open).isEmpty()
        }
    }

    private fun request(
        id: String,
        method: String,
    ) = RequestMessage().apply {
        jsonrpc = "2.0"
        this.id = id
        this.method = method
    }

    private fun response(id: String) =
        ResponseMessage().apply {
            jsonrpc = "2.0"
            this.id = id
            result = null
        }
}
