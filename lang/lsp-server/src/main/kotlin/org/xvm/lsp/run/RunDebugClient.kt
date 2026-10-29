package org.xvm.lsp.run

import com.google.gson.JsonObject
import org.xvm.debug.BreakpointState
import org.xvm.debug.ChildFilter
import org.xvm.debug.CompletionsState
import org.xvm.debug.DataBreakpointRequest
import org.xvm.debug.DataInfoState
import org.xvm.debug.ExceptionInfoState
import org.xvm.debug.ExceptionStops
import org.xvm.debug.FrameState
import org.xvm.debug.FunctionBreakpointRequest
import org.xvm.debug.ModuleState
import org.xvm.debug.ScopeState
import org.xvm.debug.SourceBreakpoints
import org.xvm.debug.StepKind
import org.xvm.debug.StepTargetState
import org.xvm.debug.StopState
import org.xvm.debug.ThreadState
import org.xvm.debug.VariableState
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

/** Receives one run's events, in order, on the thread that reads them from where its program runs. */
internal interface RunListener {
    fun output(text: String)

    fun exited(
        code: Long?,
        error: String?,
    )

    /** How one file's breakpoints were installed, before a run under the debugger started. */
    fun breakpoints(
        path: String,
        states: List<BreakpointState>,
    ) {}

    /** How the function breakpoints were installed, before a run under the debugger started. */
    fun functionBreakpoints(states: List<BreakpointState>) {}

    /** A run under the debugger stopped; it waits until it is resumed. */
    fun stopped(stop: StopState) {}

    /** A logpoint of a debugged run logged a message. */
    fun logged(text: String) {}

    /**
     * The debugger has left an attached program, which runs on: as asked, or for the [reason] given, as when the
     * connection to the program was lost. A run's last event, as an exit is.
     */
    fun detached(reason: String?) = exited(null, reason ?: "Detached from the program")
}

/**
 * The debugger's side of the worker protocol: the requests and commands to a run's runtime debugger, and the debug
 * events and replies that come back, wherever the program runs: in an execution worker ([RunWorkerProcess]), or in a
 * program that the launcher runs with `--debug`, which a debugger attached to ([AttachedProgram]). The commands and
 * events are those [RunWorker] lists.
 */
internal abstract class RunDebugClient {
    /** Requests to a run's debugger that wait for the reply, by request number. */
    private val pending = ConcurrentHashMap<Long, Pending>()
    private val nextRequest = AtomicLong(1)

    private class Pending(
        val run: Long,
        val reply: CompletableFuture<JsonObject>,
    )

    /** Sends a message for run [id] to where its program runs; false when it cannot be sent there. */
    protected abstract fun deliver(
        id: Long,
        message: JsonObject,
    ): Boolean

    /** Replaces one file's breakpoints in a run under the debugger; answers how they were installed. */
    fun setBreakpoints(
        id: Long,
        breakpoints: SourceBreakpoints,
    ): CompletableFuture<List<BreakpointState>> =
        request("setBreakpoints", id) {
            val source = RunWorkerDebug.source(breakpoints)
            source.keySet().forEach { add(it, source[it]) }
        }.thenApply { RunWorkerDebug.statesOf(it.getAsJsonArray("breakpoints")) }

    /** Names a variable of a stopped run as a data breakpoint's target, a field of an object, or says why it cannot be. */
    fun dataBreakpointInfo(
        id: Long,
        reference: Int,
        name: String,
    ): CompletableFuture<DataInfoState> =
        request("dataBreakpointInfo", id) {
            addProperty("reference", reference)
            addProperty("name", name)
        }.thenApply { DataInfoState(it["dataId"]?.asString, it["description"].asString) }

    /** Replaces the data breakpoints of a run under the debugger; answers how they were installed. */
    fun setDataBreakpoints(
        id: Long,
        breakpoints: List<DataBreakpointRequest>,
    ): CompletableFuture<List<BreakpointState>> =
        request("setDataBreakpoints", id) { add("breakpoints", RunWorkerDebug.data(breakpoints)) }
            .thenApply { RunWorkerDebug.statesOf(it.getAsJsonArray("breakpoints")) }

    /** Replaces the function breakpoints of a run under the debugger; answers how they were installed. */
    fun setFunctionBreakpoints(
        id: Long,
        breakpoints: List<FunctionBreakpointRequest>,
    ): CompletableFuture<List<BreakpointState>> =
        request("setFunctionBreakpoints", id) { add("breakpoints", RunWorkerDebug.functions(breakpoints)) }
            .thenApply { RunWorkerDebug.statesOf(it.getAsJsonArray("breakpoints")) }

    /** The fibers of a stopped run; none while it runs. */
    fun threads(id: Long): CompletableFuture<List<ThreadState>> =
        request("threads", id).thenApply { RunWorkerDebug.threadsOf(it.getAsJsonArray("threads")) }

    /** The calls that the line of a stopped frame makes, in the order of its code. */
    fun stepInTargets(
        id: Long,
        frame: Int,
    ): CompletableFuture<List<StepTargetState>> =
        request(
            "stepInTargets",
            id,
        ) { addProperty("frame", frame) }.thenApply { RunWorkerDebug.stepTargetsOf(it.getAsJsonArray("targets")) }

    /** Steps a stopped fiber into one of the calls of its line. */
    fun stepInto(
        id: Long,
        thread: Int,
        target: Int,
    ): CompletableFuture<Boolean> =
        request("stepInto", id) {
            addProperty("thread", thread)
            addProperty("target", target)
        }.thenApply { it["stepped"].asBoolean }

    /** Restarts a stopped frame, which its caller calls again. */
    fun restartFrame(
        id: Long,
        frame: Int,
    ): CompletableFuture<Boolean> = request("restartFrame", id) { addProperty("frame", frame) }.thenApply { it["restarted"].asBoolean }

    /** The modules of a debugged run, its own and the libraries it uses; none before it starts. */
    fun modules(id: Long): CompletableFuture<List<ModuleState>> =
        request("modules", id).thenApply { RunWorkerDebug.modulesOf(it.getAsJsonArray("modules")) }

    /** A stopped fiber's stack, innermost frame first. */
    fun stackTrace(
        id: Long,
        thread: Int,
    ): CompletableFuture<List<FrameState>> =
        request("stackTrace", id) { addProperty("thread", thread) }.thenApply { RunWorkerDebug.framesOf(it.getAsJsonArray("frames")) }

    /** A stopped frame's scopes. */
    fun scopes(
        id: Long,
        frame: Int,
    ): CompletableFuture<List<ScopeState>> =
        request("scopes", id) { addProperty("frame", frame) }.thenApply { RunWorkerDebug.scopesOf(it.getAsJsonArray("scopes")) }

    /** The variables of a scope, or the fields or elements of a variable, as the waiting fiber read them. */
    fun variables(
        id: Long,
        reference: Int,
        hex: Boolean,
        filter: ChildFilter,
        start: Int,
        count: Int,
    ): CompletableFuture<List<VariableState>> =
        request("variables", id) {
            addProperty("reference", reference)
            addProperty("hex", hex)
            addProperty("filter", filter.name)
            addProperty("start", start)
            addProperty("count", count)
        }.thenApply { RunWorkerDebug.variablesOf(it.getAsJsonArray("variables")) }

    /** Replaces a debugged run's exception stops. */
    fun setExceptionStops(
        id: Long,
        stops: ExceptionStops,
    ) = command("exceptionStops", id) {
        val json = RunWorkerDebug.exceptions(stops)
        json.keySet().forEach { add(it, json[it]) }
    }

    /** Changes a variable of the stopped program to an expression's value; fails with the reason when it cannot. */
    fun setVariable(
        id: Long,
        reference: Int,
        name: String,
        expression: String,
        hex: Boolean,
    ): CompletableFuture<VariableState> =
        request("setVariable", id) {
            addProperty("reference", reference)
            addProperty("name", name)
            addProperty("expression", expression)
            addProperty("hex", hex)
        }.thenApply { RunWorkerDebug.variableOf(it.getAsJsonObject("variable")) }

    /** The names that complete the name typed at an offset of a text in a stopped frame; fails with the reason when it cannot. */
    fun completions(
        id: Long,
        frame: Int,
        text: String,
        offset: Int,
    ): CompletableFuture<CompletionsState> =
        request("completions", id) {
            addProperty("frame", frame)
            addProperty("text", text)
            addProperty("offset", offset)
        }.thenApply { RunWorkerDebug.completionsOf(it.getAsJsonObject("completions")) }

    /** Evaluates an expression in a stopped frame; fails with the reason when it cannot. */
    fun evaluate(
        id: Long,
        frame: Int,
        expression: String,
        hex: Boolean,
    ): CompletableFuture<VariableState> =
        request("evaluate", id) {
            addProperty("frame", frame)
            addProperty("expression", expression)
            addProperty("hex", hex)
        }.thenApply { RunWorkerDebug.variableOf(it.getAsJsonObject("variable")) }

    /**
     * Runs code in a stopped frame, on the fiber that waits there, for at most [limit], and with [program], the whole
     * program meanwhile; fails with the reason when it cannot. Cancelling the answer ends the run: the program is told,
     * and its late reply is dropped.
     */
    fun runCode(
        id: Long,
        frame: Int,
        expression: String,
        limit: Duration,
        program: Boolean,
        hex: Boolean,
    ): CompletableFuture<VariableState> {
        val (number, reply) =
            send("runCode", id) {
                addProperty("frame", frame)
                addProperty("expression", expression)
                addProperty("limit", limit.inWholeMilliseconds)
                addProperty("program", program)
                addProperty("hex", hex)
            }
        val answer = reply.thenApply { RunWorkerDebug.variableOf(it.getAsJsonObject("variable")) }
        answer.whenComplete { _, failure ->
            if (failure is CancellationException && pending.remove(number) != null) {
                command("cancel", id) { addProperty("request", number) }
            }
        }
        return answer
    }

    /** The exception a stopped run's fiber stopped it at; null when it stopped it for another reason. */
    fun exceptionInfo(
        id: Long,
        thread: Int,
    ): CompletableFuture<ExceptionInfoState?> =
        request("exceptionInfo", id) { addProperty("thread", thread) }
            .thenApply { reply -> reply.getAsJsonObject("exception")?.let(RunWorkerDebug::exceptionInfoOf) }

    /** Steps a stopped run's fiber; false when that fiber does not wait. */
    fun step(
        id: Long,
        thread: Int,
        kind: StepKind,
    ): CompletableFuture<Boolean> =
        request("step", id) {
            addProperty("thread", thread)
            addProperty("step", kind.name.lowercase())
        }.thenApply { it["stepped"].asBoolean }

    /** Pauses a debugged run at the next line one of its fibers reaches. */
    fun pause(id: Long) = command("pause", id)

    /** Resumes a stopped run. */
    fun resume(id: Long) = command("continue", id)

    /**
     * Sends a request to a run's debugger and answers with the reply; fails when the run's program cannot be
     * reached, ends before the reply, or answers with an error.
     */
    private fun request(
        name: String,
        id: Long,
        fill: JsonObject.() -> Unit = {},
    ): CompletableFuture<JsonObject> = send(name, id, fill).second

    /** Sends a request as [request] does, and answers with its number too, by which a `cancel` names it. */
    private fun send(
        name: String,
        id: Long,
        fill: JsonObject.() -> Unit,
    ): Pair<Long, CompletableFuture<JsonObject>> {
        val number = nextRequest.getAndIncrement()
        val reply = CompletableFuture<JsonObject>()
        pending[number] = Pending(id, reply)
        val sent =
            deliver(
                id,
                JsonObject().apply {
                    addProperty("command", name)
                    addProperty("id", id)
                    addProperty("request", number)
                    fill()
                },
            )
        if (!sent) pending.remove(number)?.reply?.completeExceptionally(IllegalStateException("The program is not running"))
        return number to reply
    }

    /** Fails the requests still waiting for a reply from a run that has ended. */
    protected fun abandon(id: Long) {
        pending.entries.removeIf { (_, request) ->
            (request.run == id).also { if (it) request.reply.completeExceptionally(IllegalStateException("The program has ended")) }
        }
    }

    /** Sends a command for one run, when its program can be reached. */
    protected fun command(
        name: String,
        id: Long,
        fill: JsonObject.() -> Unit = {},
    ) {
        deliver(
            id,
            JsonObject().apply {
                addProperty("command", name)
                addProperty("id", id)
                fill()
            },
        )
    }

    /**
     * Delivers one of a run's debug events to [listener], or a reply to the request waiting for it; false when the
     * event is not one of them.
     */
    protected fun debugEvent(
        event: JsonObject,
        listener: RunListener?,
    ): Boolean {
        when (event["event"]?.asString) {
            "breakpoints" -> listener?.breakpoints(event["path"].asString, RunWorkerDebug.statesOf(event.getAsJsonArray("breakpoints")))

            "functionBreakpoints" -> listener?.functionBreakpoints(RunWorkerDebug.statesOf(event.getAsJsonArray("breakpoints")))

            "stopped" -> listener?.stopped(RunWorkerDebug.stopOf(event))

            "logged" -> listener?.logged(event["text"].asString)

            "reply" ->
                event["request"]?.asLong?.let(pending::remove)?.reply?.let { reply ->
                    val error = event["error"]?.asString
                    if (error == null) reply.complete(event) else reply.completeExceptionally(IllegalStateException(error))
                }

            else -> return false
        }
        return true
    }
}
