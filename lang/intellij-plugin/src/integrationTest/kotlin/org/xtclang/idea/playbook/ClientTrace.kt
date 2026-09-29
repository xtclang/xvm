package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Read the installed client's verbose console. In particular, do not confuse an absent VFS
 * diagnostic with an empty server publication: LSP4IJ drops publications for nonexistent files.
 */
class ClientTrace(private val driver: Driver) {
    /** Preserve native requests and replies before failure cleanup disposes the IDE console. */
    fun capture(path: Path) =
        with(driver) {
            val printed =
                withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
                    utility(TraceEditors::class).getInstance().getAllEditors().joinToString("\n") {
                        it.getDocument().getText()
                    }
                }
            val protocol = ClientProtocol(driver)
            val queued =
                protocol.server().getTraces().joinToString("\n") {
                    protocol.copy(it.message()).toString()
                }
            Files.writeString(path, "$printed\n$queued\n")
        }

    fun notifications(
        method: String,
        received: Boolean = true,
    ): List<JsonObject> =
        with(driver) {
            val direction = if (received) "Received" else "Sending"
            val prefix = "$direction notification '$method'\nParams: "
            val printed =
                withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
                    utility(TraceEditors::class).getInstance().getAllEditors().flatMap { editor ->
                        editor.getDocument().getText().split("[Trace - ").mapNotNull { entry ->
                            if (!entry.contains(prefix)) return@mapNotNull null
                            val json = entry.substringAfter(prefix).substringBefore("\n\n\n").trim()
                            try {
                                JsonParser.parseString(json)
                                    .takeIf { it.isJsonObject }
                                    ?.asJsonObject
                            } catch (_: JsonSyntaxException) {
                                // Console output can be sampled before the final chunk is flushed.
                                null
                            }
                        }
                    }
                }
            val protocol = ClientProtocol(driver)
            val queued =
                protocol
                    .server()
                    .getTraces()
                    .map { protocol.copy(it.message()).asJsonObject }
                    .filter { it["method"]?.asString == method && it["id"] == null }
                    .map { it["params"].asJsonObject }
            printed + queued
        }

    fun diagnostics(
        uri: String,
        matches: (List<JsonObject>) -> Boolean,
    ): List<JsonObject> =
        with(driver) {
            val protocol = ClientProtocol(driver)
            val pull = protocol.capabilities().asJsonObject.has("diagnosticProvider")
            awaitUi(
                message = "diagnostic report for $uri",
                timeout = 45.seconds,
                getter = {
                    if (pull) {
                        // Closed/nonexistent files have no native editor diagnostic cache. Use
                        // the negotiated report on the installed connection, not a push log.
                        protocol
                            .query(
                                "textDocument/diagnostic",
                                mapOf("textDocument" to mapOf("uri" to uri)),
                            )
                            .asJsonObject["items"]
                            ?.rows()
                    } else {
                        notifications("textDocument/publishDiagnostics")
                            .lastOrNull { sameUri(it.string("uri"), uri) }
                            ?.get("diagnostics")
                            ?.rows()
                    }
                },
                checker = { it != null && matches(it) },
            )!!
        }

    fun version(
        uri: String,
        text: String,
    ): Int =
        with(driver) {
            awaitUi(
                message = "client document version for $uri",
                errorMessage = {
                    val protocol = ClientProtocol(driver)
                    val dump =
                        withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
                            utility(TraceEditors::class).getInstance().getAllEditors().joinToString(
                                "\n--- EDITOR ---\n"
                            ) {
                                it.getDocument().getText()
                            }
                        }
                    val path =
                        Path.of(System.getProperty("xtc.playbook.reports"))
                            .resolve("trace-debug.txt")
                    Files.writeString(
                        path,
                        "level=${protocol.server().getServerTrace().name()}\nexpected=$text\n$dump\nqueued=" +
                            protocol.server().getTraces().map { protocol.copy(it.message()) },
                    )
                    "Missing current client version; trace capture: $path"
                },
                timeout = 15.seconds,
                getter = {
                    (notifications("textDocument/didOpen", received = false).filter {
                            it["textDocument"].asJsonObject.string("text") == text
                        } +
                            notifications("textDocument/didChange", received = false).filter {
                                it["contentChanges"].rows().lastOrNull()?.get("text")?.asString ==
                                    text
                            })
                        .map { it["textDocument"].asJsonObject }
                        .filter { sameUri(it.string("uri"), uri) }
                        .maxOfOrNull { it.int("version") }
                },
                checker = { it != null },
            )!!
        }

    private fun sameUri(
        left: String,
        right: String,
    ): Boolean = URI(left) == URI(right)
}

@Remote("com.redhat.devtools.lsp4ij.settings.ServerTrace", plugin = "com.redhat.devtools.lsp4ij")
interface ClientTraceLevel {
    fun valueOf(name: String): ClientTraceLevel

    fun name(): String
}

@Remote("com.intellij.openapi.editor.EditorFactory")
interface TraceEditors {
    fun getInstance(): TraceEditors

    fun getAllEditors(): List<TraceEditor>
}

@Remote("com.intellij.openapi.editor.Editor")
interface TraceEditor {
    fun getDocument(): ParityDocument
}
