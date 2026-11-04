package org.xtclang.idea.dap

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.xtclang.idea.lsp.DebugMessageParams
import org.xtclang.idea.lsp.TestEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Routes a project's debug session messages from the Ecstasy language server to the processes LSP4IJ's DAP client
 * debugs through. The server names each session's messages by the ID it returned for `xtc/debugStart`.
 */
@Service(Service.Level.PROJECT)
class DebugSessions {
    private val sessions = ConcurrentHashMap<String, Session>()

    interface Session {
        /** One DAP message from the session. */
        fun message(message: JsonObject)

        /** The language server has gone, and the session with it. */
        fun ended(reason: String)
    }

    fun register(
        sessionId: String,
        session: Session,
    ) {
        sessions[sessionId] = session
    }

    fun remove(sessionId: String) {
        sessions.remove(sessionId)
    }

    /** Whether a debug session runs in the language server. */
    val active: Boolean get() = sessions.isNotEmpty()

    fun receive(params: DebugMessageParams) {
        sessions[params.sessionId]?.message(params.message)
    }

    /** Output for a session's console from outside the session, as from its Gradle task, in a DAP `output` event. */
    fun output(
        sessionId: String,
        text: String,
        category: String,
    ) {
        sessions[sessionId]?.message(
            JsonObject().apply {
                addProperty("type", "event")
                addProperty("seq", 0)
                addProperty("event", "output")
                add(
                    "body",
                    JsonObject().apply {
                        addProperty("category", category)
                        addProperty("output", text)
                    },
                )
            },
        )
    }

    /**
     * An event of a session's tests from outside the session, as from its Gradle task's output, in the session's own
     * DAP event `xtcTest`, as the language server sends an embedded launch's.
     */
    fun tested(
        sessionId: String,
        event: TestEvent,
    ) {
        sessions[sessionId]?.message(
            JsonObject().apply {
                addProperty("type", "event")
                addProperty("seq", 0)
                addProperty("event", XTC_TEST)
                add("body", gson.toJsonTree(event))
            },
        )
    }

    /** A stopped or restarted language server takes every debug session, and the programs they launched, with it. */
    fun endAll(reason: String) {
        sessions.keys.toList().forEach { sessions.remove(it)?.ended(reason) }
    }

    companion object {
        /** The DAP event of a session's tests, which the language server sends for a launch with tests. */
        const val XTC_TEST = "xtcTest"

        private val gson = Gson()

        fun getInstance(project: Project): DebugSessions = project.getService(DebugSessions::class.java)
    }
}
