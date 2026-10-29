package org.xvm.lsp.run

import com.google.gson.JsonObject
import org.xvm.debug.BreakpointState
import org.xvm.debug.CompletionState
import org.xvm.debug.CompletionsState
import org.xvm.debug.DataBreakpointRequest
import org.xvm.debug.ExceptionInfoState
import org.xvm.debug.FrameState
import org.xvm.debug.FunctionBreakpointRequest
import org.xvm.debug.ModuleState
import org.xvm.debug.ScopeState
import org.xvm.debug.SourceBreakpoints
import org.xvm.debug.StepTargetState
import org.xvm.debug.StopState
import org.xvm.debug.ThreadState
import org.xvm.debug.VariableState
import org.xvm.runtime.SessionDebugger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/**
 * The debug half of the worker protocol, where the program runs: the runtime debugger of each run under the debugger,
 * by the run's ID, and the commands that ask it, which it answers over [channel]. The execution worker ([RunWorker])
 * serves it over its stdin and stdout, and the debugger agent ([XtcDebugAgent]) over a socket, to a debugger that
 * attached to a program the launcher runs. The commands and events are those [RunWorker] lists for a run under the
 * debugger.
 */
internal class RunDebuggers(
    private val channel: RunWorkerChannel,
) {
    /** The runtime debugger of each run under the debugger. */
    private val debuggers = ConcurrentHashMap<Long, SessionDebugger>()

    /** Code that runs on a stopped fiber, by the request that ran it, which a `cancel` names. */
    private val running = ConcurrentHashMap<Long, CompletableFuture<SessionDebugger.Variable>>()

    /** The debuggers and the code runs held, which a run's [Kept] counts. */
    val held: Int get() = debuggers.size + running.size

    /**
     * Reports a run's stops as `stopped` events and its logpoints' messages as `logged` events, each after [before],
     * which sends what the program printed before them.
     */
    fun listener(
        id: Long,
        before: () -> Unit = {},
    ) = object : SessionDebugger.Listener {
        override fun stopped(stop: SessionDebugger.Stop) {
            before()
            channel.send("stopped") {
                addProperty("id", id)
                RunWorkerDebug.stop(this, StopState(stop.reason(), stop.description(), stop.threadId(), stop.breakpointIds()))
            }
        }

        override fun logged(message: String) {
            before()
            channel.send("logged") {
                addProperty("id", id)
                addProperty("text", message)
            }
        }
    }

    /**
     * Sets up a run's debugger as [command] asks, before the program starts or as a debugger attaches: installs the
     * `breakpoints` and the `functions`, reporting how each file's breakpoints and the function breakpoints were
     * installed, the `exceptions` stops and, with `stopOnEntry`, the stop on entry. The program's modules are known
     * by then, so the breakpoints in their files are verified and the stop on entry is in the program's code, not in
     * the libraries before it. The commands with the run's ID then ask this debugger.
     */
    fun start(
        id: Long,
        debugger: SessionDebugger,
        command: JsonObject,
    ) {
        command.getAsJsonArray("breakpoints")?.let(RunWorkerDebug::sourcesOf)?.forEach { source ->
            channel.send("breakpoints") {
                addProperty("id", id)
                addProperty("path", source.path)
                add("breakpoints", RunWorkerDebug.states(install(debugger, source)))
            }
        }
        val functions = RunWorkerDebug.functionsOf(command.getAsJsonArray("functions"))
        if (functions.isNotEmpty()) {
            channel.send("functionBreakpoints") {
                addProperty("id", id)
                add("breakpoints", RunWorkerDebug.states(install(debugger, functions)))
            }
        }
        val exceptions = RunWorkerDebug.exceptionsOf(command["exceptions"])
        debugger.setExceptionStops(exceptions.all, exceptions.allTypes, exceptions.uncaught, exceptions.uncaughtTypes)
        if (command["stopOnEntry"]?.asBoolean == true) debugger.stopOnEntry()
        debuggers[id] = debugger
    }

    /** The run's debugger, which no command asks from now on. */
    fun remove(id: Long): SessionDebugger? = debuggers.remove(id)

    /** Handles a command to a run's debugger; false when it is not one. */
    fun handle(command: JsonObject): Boolean {
        when (command["command"]?.asString) {
            "setBreakpoints" -> setBreakpoints(command)
            "setFunctionBreakpoints" -> setFunctionBreakpoints(command)
            "dataBreakpointInfo" -> dataBreakpointInfo(command)
            "setDataBreakpoints" -> setDataBreakpoints(command)
            "threads" -> threads(command)
            "modules" -> modules(command)
            "stepInTargets" -> stepInTargets(command)
            "stepInto" -> stepInto(command)
            "restartFrame" -> restartFrame(command)
            "stackTrace" -> stackTrace(command)
            "scopes" -> scopes(command)
            "variables" -> variables(command)
            "evaluate" -> evaluate(command)
            "setVariable" -> setVariable(command)
            "completions" -> completions(command)
            "runCode" -> runCode(command)
            "cancel" -> cancel(command)
            "exceptionInfo" -> exceptionInfo(command)
            "step" -> step(command)
            "exceptionStops" -> exceptionStops(command)
            "pause" -> debuggers[command["id"].asLong]?.pause()
            "continue" -> debuggers[command["id"].asLong]?.resume()
            else -> return false
        }
        return true
    }

    private fun setBreakpoints(command: JsonObject) =
        reply(command) { debugger -> add("breakpoints", RunWorkerDebug.states(install(debugger, RunWorkerDebug.sourceOf(command)))) }

    private fun setFunctionBreakpoints(command: JsonObject) =
        reply(command) { debugger ->
            add(
                "breakpoints",
                RunWorkerDebug.states(install(debugger, RunWorkerDebug.functionsOf(command.getAsJsonArray("breakpoints")))),
            )
        }

    private fun dataBreakpointInfo(command: JsonObject) =
        reply(command) { debugger ->
            val info = debugger.watchable(command["reference"].asInt, command["name"].asString)
            info.dataId()?.let { addProperty("dataId", it) }
            addProperty("description", info.description())
        }

    private fun setDataBreakpoints(command: JsonObject) =
        reply(command) { debugger ->
            val data = RunWorkerDebug.dataOf(command.getAsJsonArray("breakpoints"))
            add("breakpoints", RunWorkerDebug.states(watch(debugger, data)))
        }

    private fun threads(command: JsonObject) =
        reply(command) { debugger ->
            add("threads", RunWorkerDebug.threads(debugger.threads().map { ThreadState(it.id(), it.name()) }))
        }

    private fun modules(command: JsonObject) =
        reply(command) { debugger ->
            add(
                "modules",
                RunWorkerDebug.modules(
                    debugger.modules().map { ModuleState(it.name(), it.version(), it.sourceDir(), it.sources(), it.own()) },
                ),
            )
        }

    private fun stackTrace(command: JsonObject) =
        reply(command) { debugger ->
            val frames =
                debugger.stackTrace(command["thread"].asInt).map {
                    FrameState(it.id(), it.name(), it.path(), it.line(), it.module(), it.source(), it.restartable())
                }
            add("frames", RunWorkerDebug.frames(frames))
        }

    private fun scopes(command: JsonObject) =
        reply(command) { debugger ->
            val scopes = debugger.scopes(command["frame"].asInt).map { ScopeState(it.name(), it.variablesReference()) }
            add("scopes", RunWorkerDebug.scopes(scopes))
        }

    /**
     * Answers once the waiting fiber that holds the variables has read them on its own thread, so the commands
     * that follow, such as `continue`, do not wait for the read.
     */
    private fun variables(command: JsonObject) {
        val read =
            debuggers[command["id"].asLong]?.variables(
                command["reference"].asInt,
                command["hex"].asBoolean,
                SessionDebugger.Filter.valueOf(command["filter"].asString),
                command["start"].asInt,
                command["count"].asInt,
            )
                ?: return answer(command) { addProperty("error", NOT_DEBUGGED) }
        read.whenComplete { variables, failure ->
            answer(command) {
                if (failure != null) {
                    addProperty("error", reason(failure))
                } else {
                    add("variables", RunWorkerDebug.variables(variables.map(::state)))
                }
            }
        }
    }

    /** Answers, as `variables` does, with an expression's value, or the reason it cannot be evaluated. */
    private fun evaluate(command: JsonObject) {
        val read =
            debuggers[command["id"].asLong]?.evaluate(
                command["frame"].asInt,
                command["expression"].asString,
                command["hex"].asBoolean,
            )
                ?: return answer(command) { addProperty("error", NOT_DEBUGGED) }
        read.whenComplete { variable, failure ->
            answer(command) {
                if (failure != null) {
                    addProperty("error", reason(failure))
                } else {
                    add("variable", RunWorkerDebug.variable(state(variable)))
                }
            }
        }
    }

    /** Answers, as `evaluate` does, with the variable a change gave a new value, or the reason it is not changed. */
    private fun setVariable(command: JsonObject) {
        val change =
            debuggers[command["id"].asLong]?.setVariable(
                command["reference"].asInt,
                command["name"].asString,
                command["expression"].asString,
                command["hex"].asBoolean,
            ) ?: return answer(command) { addProperty("error", NOT_DEBUGGED) }
        change.whenComplete { variable, failure ->
            answer(command) {
                if (failure != null) {
                    addProperty("error", reason(failure))
                } else {
                    add("variable", RunWorkerDebug.variable(state(variable)))
                }
            }
        }
    }

    /** Answers, as `evaluate` does, with the names that complete the name typed at the offset of the text in the frame. */
    private fun completions(command: JsonObject) {
        val completion =
            debuggers[command["id"].asLong]?.completions(
                command["frame"].asInt,
                command["text"].asString,
                command["offset"].asInt,
            ) ?: return answer(command) { addProperty("error", NOT_DEBUGGED) }
        completion.whenComplete { completions, failure ->
            answer(command) {
                if (failure != null) {
                    addProperty("error", reason(failure))
                } else {
                    add(
                        "completions",
                        RunWorkerDebug.completions(
                            CompletionsState(
                                completions.start(),
                                completions.length(),
                                completions.items().map { CompletionState(it.label(), it.kind()) },
                            ),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Answers, as `evaluate` does, with the value of code run on the fiber that waits at the frame, for at most
     * the request's `limit` in milliseconds, which the server chooses, and with `program`, the whole program
     * meanwhile.
     */
    private fun runCode(command: JsonObject) {
        val run =
            debuggers[command["id"].asLong]?.run(
                command["frame"].asInt,
                command["expression"].asString,
                command["limit"].asLong.milliseconds.toJavaDuration(),
                command["program"].asBoolean,
                command["hex"].asBoolean,
            ) ?: return answer(command) { addProperty("error", NOT_DEBUGGED) }
        val request = command["request"].asLong
        running[request] = run
        run.whenComplete { variable, failure ->
            running.remove(request)
            answer(command) {
                if (failure != null) {
                    addProperty("error", reason(failure))
                } else {
                    add("variable", RunWorkerDebug.variable(state(variable)))
                }
            }
        }
    }

    /** Ends the code that the `request` of a `runCode` runs, before its limit; it then answers that it was cancelled. */
    private fun cancel(command: JsonObject) {
        running[command["request"].asLong]?.cancel(false)
    }

    private fun state(variable: SessionDebugger.Variable) =
        VariableState(
            variable.name(),
            variable.value(),
            variable.type(),
            variable.variablesReference(),
            variable.lazy(),
            variable.elements(),
        )

    /** A failed read's reason, as the runtime debugger put it. */
    private fun reason(failure: Throwable): String {
        val cause = (failure as? CompletionException)?.cause ?: failure
        return cause.message ?: cause.toString()
    }

    /** Answers whether the fiber was stepped; one that does not wait is not, and the program stays stopped. */
    private fun step(command: JsonObject) =
        reply(command) { debugger ->
            val step = SessionDebugger.Step.valueOf(command["step"].asString.uppercase())
            addProperty("stepped", debugger.step(command["thread"].asInt, step))
        }

    private fun stepInTargets(command: JsonObject) =
        reply(command) { debugger ->
            val targets = debugger.stepInTargets(command["frame"].asInt).map { StepTargetState(it.id(), it.label()) }
            add("targets", RunWorkerDebug.stepTargets(targets))
        }

    private fun stepInto(command: JsonObject) =
        reply(command) { debugger -> addProperty("stepped", debugger.stepInto(command["thread"].asInt, command["target"].asInt)) }

    private fun restartFrame(command: JsonObject) =
        reply(command) { debugger -> addProperty("restarted", debugger.restartFrame(command["frame"].asInt)) }

    private fun exceptionStops(command: JsonObject) {
        val stops = RunWorkerDebug.exceptionsOf(command)
        debuggers[command["id"].asLong]?.setExceptionStops(stops.all, stops.allTypes, stops.uncaught, stops.uncaughtTypes)
    }

    /** Answers with the exception the fiber stopped the program at; none when it stopped it for another reason. */
    private fun exceptionInfo(command: JsonObject) =
        reply(command) { debugger ->
            debugger.exceptionInfo(command["thread"].asInt)?.let { info ->
                add(
                    "exception",
                    RunWorkerDebug.exceptionInfo(ExceptionInfoState(info.type(), info.text(), info.stackTrace(), info.uncaught())),
                )
            }
        }

    private fun install(
        debugger: SessionDebugger,
        source: SourceBreakpoints,
    ): List<BreakpointState> =
        debugger
            .setBreakpoints(
                source.path,
                source.breakpoints.map { SessionDebugger.Line(it.id, it.line, it.condition, it.hitCondition, it.logMessage) },
            ).map { BreakpointState(it.id(), it.line(), it.verified(), it.message()) }

    private fun watch(
        debugger: SessionDebugger,
        data: List<DataBreakpointRequest>,
    ): List<BreakpointState> =
        debugger
            .setDataBreakpoints(data.map { SessionDebugger.Data(it.id, it.dataId, it.condition, it.hitCondition) })
            .map { BreakpointState(it.id(), it.line(), it.verified(), it.message()) }

    private fun install(
        debugger: SessionDebugger,
        functions: List<FunctionBreakpointRequest>,
    ): List<BreakpointState> =
        debugger
            .setMethodBreakpoints(functions.map { SessionDebugger.Method(it.id, it.name, it.condition, it.hitCondition) })
            .map { BreakpointState(it.id(), it.line(), it.verified(), it.message(), it.path()) }

    /** Answers a request for a run's debugger; a run without one, or one that ended, answers with an error. */
    private fun reply(
        command: JsonObject,
        fill: JsonObject.(SessionDebugger) -> Unit,
    ) {
        val debugger = debuggers[command["id"].asLong]
        answer(command) { if (debugger == null) addProperty("error", NOT_DEBUGGED) else fill(debugger) }
    }

    /** Sends a reply; its content is made first, since asking the debugger must not wait while holding the channel. */
    private fun answer(
        command: JsonObject,
        fill: JsonObject.() -> Unit,
    ) {
        val content = JsonObject().apply(fill)
        channel.send("reply") {
            addProperty("id", command["id"].asLong)
            addProperty("request", command["request"].asLong)
            content.entrySet().forEach { (name, value) -> add(name, value) }
        }
    }

    private companion object {
        /** The error of a request for the debugger of a run without one, or of one that ended. */
        const val NOT_DEBUGGED = "The program is not running under the debugger"
    }
}
