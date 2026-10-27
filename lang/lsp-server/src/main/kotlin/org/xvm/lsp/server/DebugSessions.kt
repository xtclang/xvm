package org.xvm.lsp.server

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory
import org.xvm.debug.BreakpointState
import org.xvm.debug.ChildFilter
import org.xvm.debug.DebugMessageEndpoint
import org.xvm.debug.DebugRuns
import org.xvm.debug.DebugSetup
import org.xvm.debug.ExceptionStops
import org.xvm.debug.LaunchResult
import org.xvm.debug.LaunchTarget
import org.xvm.debug.ProgramEvents
import org.xvm.debug.SourceBreakpoints
import org.xvm.debug.StepKind
import org.xvm.debug.StopState
import org.xvm.debug.XtcDebugServer
import org.xvm.lsp.adapter.xdk.XdkLibrarySources
import org.xvm.lsp.run.RunWorkerProcess
import java.io.Closeable
import java.net.URI
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration

/** `xtc/debugStart` result: the ID of the new debug session. */
data class DebugSessionResponse(
    val sessionId: String,
)

/** `xtc/debugEnd` parameters. */
data class DebugSessionParams(
    val sessionId: String? = null,
)

/** `xtc/debugMessage` parameters: one Debug Adapter Protocol message of a session, in either direction. */
data class DebugMessageParams(
    val sessionId: String,
    val message: JsonObject,
)

/**
 * Debug Adapter Protocol (DAP) sessions carried by the language server's own connection. A client starts one with
 * `xtc/debugStart`, exchanges DAP messages with it in `xtc/debugMessage` notifications, in both directions, and ends
 * it with `xtc/debugEnd`. Each session is an [XtcDebugServer] that launches through [runs], so a debug launch builds
 * and runs with the same embedded build and execution worker as Run, and no socket or second process is needed.
 * A session's messages are handled in order on a thread of its own, never on the connection's reader.
 *
 * Ending a session, or closing the server, stops the program the session launched.
 */
internal class DebugSessions(
    private val runs: EmbeddedRuns,
    private val client: () -> LanguageClient?,
) : Closeable {
    private val sessions = ConcurrentHashMap<String, Session>()

    private inner class Session(
        val id: String,
    ) {
        val messages: ExecutorService =
            Executors.newSingleThreadExecutor { task -> Thread(task, "xtc-debug-$id").apply { isDaemon = true } }
        val server = XtcDebugServer(Launches(id), messages)
        val endpoint =
            DebugMessageEndpoint(server) { message ->
                (client() as? XtcLanguageClient)?.debugMessage(DebugMessageParams(id, JsonParser.parseString(message).asJsonObject))
            }

        fun close() {
            server.close()
            messages.shutdown()
        }
    }

    fun start(): DebugSessionResponse {
        val id = UUID.randomUUID().toString()
        sessions[id] = Session(id)
        logger.info("debug session {} started", id)
        return DebugSessionResponse(id)
    }

    fun receive(params: DebugMessageParams) {
        val session = sessions[params.sessionId]
        if (session == null) {
            logger.warn("debug message for unknown session {}", params.sessionId)
            return
        }
        val message = params.message.toString()
        session.messages.execute { session.endpoint.receive(message) }
    }

    fun end(params: DebugSessionParams) {
        params.sessionId?.let(sessions::remove)?.let { session ->
            logger.info("debug session {} ended", session.id)
            session.close()
        }
    }

    override fun close() {
        sessions.values.toList().forEach(Session::close)
        sessions.clear()
    }

    /** Sessions started and not yet ended, for tests. */
    internal fun tracked(): Int = sessions.size

    /** A session's launches, as embedded runs. */
    private inner class Launches(
        private val session: String,
    ) : DebugRuns {
        override fun launch(
            runId: String,
            target: LaunchTarget,
            events: ProgramEvents,
            ready: CompletionStage<out DebugSetup?>,
        ): CompletableFuture<LaunchResult> {
            logger.info("debug session launches a run: session={} run={}", session, runId)
            return runs
                .launch(
                    RunParams(target.module, target.method, target.args, target.workingDirectory, runId, target.moduleName),
                    object : RunWorkerProcess.Listener {
                        override fun output(text: String) = events.output(text)

                        override fun exited(
                            code: Long?,
                            error: String?,
                        ) = events.exited(code, error)

                        override fun breakpoints(
                            path: String,
                            states: List<BreakpointState>,
                        ) = events.breakpoints(path, states)

                        override fun stopped(stop: StopState) = events.stopped(stop)

                        override fun logged(text: String) = events.logged(text)
                    },
                    ready,
                ).thenApply { LaunchResult(it.runId, describe(it.build)) }
        }

        override fun input(
            runId: String,
            text: String,
        ) = runs.input(RunInputParams(runId, text))

        override fun stop(runId: String) = runs.stop(StopRunParams(runId))

        override fun setBreakpoints(
            runId: String,
            breakpoints: SourceBreakpoints,
        ) = runs.setBreakpoints(runId, breakpoints)

        override fun setExceptionStops(
            runId: String,
            stops: ExceptionStops,
        ) = runs.setExceptionStops(runId, stops)

        override fun threads(runId: String) = runs.threads(runId)

        override fun stackTrace(
            runId: String,
            threadId: Int,
        ) = runs.stackTrace(runId, threadId)

        override fun scopes(
            runId: String,
            frameId: Int,
        ) = runs.scopes(runId, frameId)

        override fun variables(
            runId: String,
            reference: Int,
            hex: Boolean,
            filter: ChildFilter,
            start: Int,
            count: Int,
        ) = runs.variables(runId, reference, hex, filter, start, count)

        override fun evaluate(
            runId: String,
            frameId: Int,
            expression: String,
            hex: Boolean,
        ) = runs.evaluate(runId, frameId, expression, hex)

        override fun setVariable(
            runId: String,
            reference: Int,
            name: String,
            expression: String,
            hex: Boolean,
        ) = runs.setVariable(runId, reference, name, expression, hex)

        override fun completions(
            runId: String,
            frameId: Int,
            text: String,
            offset: Int,
        ) = runs.completions(runId, frameId, text, offset)

        override fun run(
            runId: String,
            frameId: Int,
            expression: String,
            limit: Duration,
            program: Boolean,
            hex: Boolean,
        ) = runs.runCode(runId, frameId, expression, limit, program, hex)

        override fun exceptionInfo(
            runId: String,
            threadId: Int,
        ) = runs.exceptionInfo(runId, threadId)

        override fun step(
            runId: String,
            threadId: Int,
            kind: StepKind,
        ) = runs.step(runId, threadId, kind)

        override fun pause(runId: String) = runs.pause(runId)

        override fun resume(runId: String) = runs.resume(runId)

        override fun librarySource(
            module: String,
            path: String,
        ) = XdkLibrarySources.file(module, path)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(DebugSessions::class.java)

        /** A build's module outcomes and diagnostics as `file:line:column` lines, as the embedded build task shows them. */
        fun describe(build: BuildResponse): String =
            buildList {
                build.modules.forEach { module ->
                    add(
                        when {
                            module.succeeded -> "  ✓ ${module.name}${module.artifact?.let { " -> $it" }.orEmpty()}"
                            module.skipped -> "  - ${module.name} (not built: a dependency failed)"
                            else -> "  ✗ ${module.name}"
                        },
                    )
                }
                build.diagnostics.forEach { file ->
                    val path = runCatching { Path.of(URI(file.uri)).toString() }.getOrDefault(file.uri)
                    file.diagnostics.forEach { diagnostic ->
                        val start = diagnostic.range.start
                        val severity =
                            when (diagnostic.severity) {
                                DiagnosticSeverity.Warning -> "warning"
                                DiagnosticSeverity.Information -> "info"
                                DiagnosticSeverity.Hint -> "hint"
                                else -> "error"
                            }
                        val code =
                            diagnostic.code
                                ?.get()
                                ?.let { " [$it]" }
                                .orEmpty()
                        add("$path:${start.line + 1}:${start.character + 1}: $severity: ${diagnostic.message}$code")
                    }
                }
                if (build.cancelled) add("Build cancelled.")
            }.joinToString("\n")
    }
}
