package org.xvm.lsp.run

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.eclipse.lsp4j.services.LanguageClient
import org.mockito.Mockito.mock
import org.xvm.lsp.server.DebugMessageParams
import org.xvm.lsp.server.DebugSessions
import org.xvm.lsp.server.RunExitedParams
import org.xvm.lsp.server.RunOutputParams
import org.xvm.lsp.server.XtcLanguageClient
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

/** Records the DAP messages that debug sessions send their client. */
internal class DebugClient :
    XtcLanguageClient,
    LanguageClient by mock(LanguageClient::class.java) {
    private val messages = LinkedBlockingQueue<DebugMessageParams>()

    override fun debugMessage(params: DebugMessageParams) {
        messages.add(params)
    }

    // a debug session's program reports through DAP events, not the run notifications
    override fun runOutput(params: RunOutputParams) = error("run output outside the debug session: $params")

    override fun runExited(params: RunExitedParams) = error("run exit outside the debug session: $params")

    /** The session's messages, in order, up to the first that satisfies [last]. */
    fun until(
        sessionId: String,
        last: (JsonObject) -> Boolean,
    ): List<JsonObject> {
        val received = mutableListOf<JsonObject>()
        while (received.none(last)) {
            val next = messages.poll(60, SECONDS) ?: error("no DAP message within 60 s after $received")
            if (next.sessionId == sessionId) received += next.message
        }
        return received
    }
}

/** Sends a DAP request; without [arguments], it has none, as VS Code sends `configurationDone`. */
internal fun DebugSessions.request(
    sessionId: String,
    seq: Int,
    command: String,
    arguments: String? = null,
) = receive(
    DebugMessageParams(
        sessionId,
        JsonParser
            .parseString(
                """{"seq": $seq, "type": "request", "command": "$command"${arguments?.let { ", \"arguments\": $it" }.orEmpty()}}""",
            ).asJsonObject,
    ),
)

internal val JsonObject.event get() = takeIf { it["type"]?.asString == "event" }?.get("event")?.asString

internal val JsonObject.response get() = takeIf { it["type"]?.asString == "response" }?.get("command")?.asString
