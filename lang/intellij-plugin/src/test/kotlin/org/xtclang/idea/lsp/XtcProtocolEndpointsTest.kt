package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.junit.jupiter.api.Test
import org.xvm.lsp.protocol.XtcClientProtocol
import org.xvm.lsp.protocol.XtcServerProtocol

/**
 * The plugin's ends of the `xtc/` extension, as lsp4j's endpoints find their messages: by the annotations of a class and
 * every type it extends, failing to start on a name that two methods have. The protocol's interfaces hold the
 * annotations, so the plugin's language client must not repeat them on its methods.
 */
class XtcProtocolEndpointsTest {
    @Test
    fun `the language client answers each xtc message of the protocol once`() {
        val client = messages(XtcLanguageClient::class.java)

        assertThat(client).containsExactlyInAnyOrderElementsOf(messages(XtcClientProtocol::class.java))
        assertThat(client).contains("xtc/runOutput", "xtc/runExited", "xtc/testEvent", "xtc/debugMessage", "xtc/debugTask").hasSize(7)
    }

    @Test
    fun `the server's proxy has each xtc message of the protocol once`() {
        assertThat(
            messages(XtcServerProtocol::class.java),
        ).contains("xtc/build", "xtc/run", "xtc/tests", "xtc/endOfLine").hasSize(17)
    }

    /** The `xtc/` messages of [type], as an endpoint finds them, which fails on one that two of its methods have. */
    private fun messages(type: Class<*>): List<String> = ServiceEndpoints.getSupportedMethods(type).keys.filter { it.startsWith("xtc/") }
}
