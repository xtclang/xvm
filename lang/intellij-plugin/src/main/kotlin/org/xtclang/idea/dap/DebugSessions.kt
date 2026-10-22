package org.xtclang.idea.dap

import com.google.gson.JsonObject
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.xtclang.idea.lsp.DebugMessageParams
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

    fun receive(params: DebugMessageParams) {
        sessions[params.sessionId]?.message(params.message)
    }

    /** A stopped or restarted language server takes every debug session, and the programs they launched, with it. */
    fun endAll(reason: String) {
        sessions.keys.toList().forEach { sessions.remove(it)?.ended(reason) }
    }

    companion object {
        fun getInstance(project: Project): DebugSessions = project.getService(DebugSessions::class.java)
    }
}
