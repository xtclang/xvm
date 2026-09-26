package org.xtclang.idea.lsp

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.junit.jupiter.api.Test

class ConfigurationJsonTest {
    @Test
    fun `configuration notification preserves explicit discovery reset on the wire`() {
        val settings = JsonParser.parseString("""{"xtc":{"compiler":{"sourceModules":null}}}""")
        val message =
            NotificationMessage().apply {
                method = "workspace/didChangeConfiguration"
                params = DidChangeConfigurationParams(settings)
            }
        val defaults = MessageJsonHandler(emptyMap())
        assertThat(defaults.serialize(message)).doesNotContain("sourceModules")
        val wire = json.serialize(message)
        val received = JsonParser.parseString(wire).asJsonObject["params"].asJsonObject["settings"]
        assertThat(received).isEqualTo(settings)
        assertThat(json.parseMessage(wire)).isEqualTo(message)
    }

    @Test
    fun `unrelated protocol fields keep their normal omission rules`() {
        val message =
            NotificationMessage().apply {
                method = "textDocument/publishDiagnostics"
                params = PublishDiagnosticsParams("file:///source.x", emptyList())
            }
        assertThat(json.serialize(message)).isEqualTo(MessageJsonHandler(emptyMap()).serialize(message))
        assertThat(json.serialize(message)).doesNotContain("version")
    }

    private val json =
        MessageJsonHandler(
            mapOf(
                "workspace/didChangeConfiguration" to
                    JsonRpcMethod.notification("workspace/didChangeConfiguration", DidChangeConfigurationParams::class.java),
            ),
        ) { it.registerTypeAdapterFactory(ConfigurationJson) }
}
