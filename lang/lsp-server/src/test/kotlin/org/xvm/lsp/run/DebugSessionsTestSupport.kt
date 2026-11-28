package org.xvm.lsp.run

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.eclipse.lsp4j.services.LanguageClient
import org.mockito.Mockito.mock
import org.xvm.lsp.protocol.DebugMessageParams
import org.xvm.lsp.protocol.RunExitedParams
import org.xvm.lsp.protocol.RunOutputParams
import org.xvm.lsp.protocol.XtcClientProtocol
import org.xvm.lsp.server.DebugSessions
import org.xvm.testing.awaited
import org.xvm.testing.what
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue

/** Records the DAP messages that debug sessions send their client. */
internal class DebugClient :
    XtcClientProtocol,
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
            val next = messages.awaited(what { "a DAP message after $received" })
            if (next.sessionId == sessionId) received += next.message
        }
        return received
    }

    /** The body of the session's next response to [command]. */
    fun responseBody(
        sessionId: String,
        command: String,
    ): JsonObject = until(sessionId) { it.response == command }.last()["body"].asJsonObject

    /** The body of the session's next [event]. */
    fun eventBody(
        sessionId: String,
        event: String,
    ): JsonObject = until(sessionId) { it.event == event }.last()["body"].asJsonObject
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

/** The top frame of the stopped fiber [thread], as the session [sessionId] answers a stackTrace request [seq] of [client]. */
internal fun DebugSessions.topFrame(
    client: DebugClient,
    sessionId: String,
    seq: Int,
    thread: Int,
): JsonObject {
    request(sessionId, seq, "stackTrace", """{"threadId": $thread}""")
    return client
        .responseBody(sessionId, "stackTrace")["stackFrames"]
        .asJsonArray
        .first()
        .asJsonObject
}

internal val JsonObject.event get() = takeIf { it["type"]?.asString == "event" }?.get("event")?.asString

internal val JsonObject.response get() = takeIf { it["type"]?.asString == "response" }?.get("command")?.asString

/** Writes [text] to the file [path] in [directory], with the directories it is in, and answers the file's URI. */
internal fun writeSource(
    directory: Path,
    path: String,
    text: String,
): String {
    val file = directory.resolve(path).toFile()
    file.parentFile.mkdirs()
    file.writeText(text)
    return file.toURI().toString()
}
