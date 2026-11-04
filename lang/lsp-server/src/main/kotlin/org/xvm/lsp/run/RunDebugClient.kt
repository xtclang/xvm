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
import org.xvm.debug.TestEvent
import org.xvm.debug.ThreadState
import org.xvm.debug.VariableState
import org.xvm.runtime.SessionDebugger
import org.xvm.runtime.debug.DebugProtocol
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

/** Receives one run's events, in order, on the thread that reads them from where its program runs. */
internal interface RunListener {
    fun output(text: String)

    /** An event of a run of tests, which its output reported, and which is not in [output]. */
    fun tested(event: TestEvent) {}

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
 * program that the launcher runs with `--debug`, which a debugger attached to ([AttachedProgram]). The messages are
 * javatools' [DebugProtocol] records, which the program's side answers with too.
 */
internal abstract class RunDebugClient {
    /** Requests to a run's debugger that wait for the reply, by request number. */
    private val pending = ConcurrentHashMap<Long, Pending>()
    private val nextRequest = AtomicLong(1)

    private class Pending(
        val run: Long,
        val reply: CompletableFuture<DebugProtocol.Reply>,
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
        request(id) { DebugProtocol.SetBreakpoints(id, it, breakpoints.record()) }.thenApply { it.map(SessionDebugger.Breakpoint::state) }

    /** Names a variable of a stopped run as a data breakpoint's target, a field of an object, or says why it cannot be. */
    fun dataBreakpointInfo(
        id: Long,
        reference: Int,
        name: String,
    ): CompletableFuture<DataInfoState> = request(id) { DebugProtocol.DataBreakpointInfo(id, it, reference, name) }.thenApply { it.state() }

    /** Replaces the data breakpoints of a run under the debugger; answers how they were installed. */
    fun setDataBreakpoints(
        id: Long,
        breakpoints: List<DataBreakpointRequest>,
    ): CompletableFuture<List<BreakpointState>> =
        request(id) { DebugProtocol.SetDataBreakpoints(id, it, breakpoints.map(DataBreakpointRequest::record)) }
            .thenApply { it.map(SessionDebugger.Breakpoint::state) }

    /** Replaces the function breakpoints of a run under the debugger; answers how they were installed. */
    fun setFunctionBreakpoints(
        id: Long,
        breakpoints: List<FunctionBreakpointRequest>,
    ): CompletableFuture<List<BreakpointState>> =
        request(id) { DebugProtocol.SetFunctionBreakpoints(id, it, breakpoints.map(FunctionBreakpointRequest::record)) }
            .thenApply { it.map(SessionDebugger.Breakpoint::functionState) }

    /** The fibers of a stopped run; none while it runs. */
    fun threads(id: Long): CompletableFuture<List<ThreadState>> =
        request(id) { DebugProtocol.Threads(id, it) }.thenApply { it.map(SessionDebugger.ThreadInfo::state) }

    /** The calls that the line of a stopped frame makes, in the order of its code. */
    fun stepInTargets(
        id: Long,
        frame: Int,
    ): CompletableFuture<List<StepTargetState>> =
        request(id) { DebugProtocol.StepInTargets(id, it, frame) }.thenApply { it.map(SessionDebugger.StepTarget::state) }

    /** Steps a stopped fiber into one of the calls of its line, [alone] while the program's other fibers stay stopped. */
    fun stepInto(
        id: Long,
        thread: Int,
        target: Int,
        alone: Boolean = false,
    ): CompletableFuture<Boolean> = request(id) { DebugProtocol.StepInto(id, it, thread, target, alone) }

    /** Restarts a stopped frame, which its caller calls again. */
    fun restartFrame(
        id: Long,
        frame: Int,
    ): CompletableFuture<Boolean> = request(id) { DebugProtocol.RestartFrame(id, it, frame) }

    /** The modules of a debugged run, its own and the libraries it uses; none before it starts. */
    fun modules(id: Long): CompletableFuture<List<ModuleState>> =
        request(id) { DebugProtocol.Modules(id, it) }.thenApply { it.map(SessionDebugger.ModuleInfo::state) }

    /** A stopped fiber's stack, innermost frame first. */
    fun stackTrace(
        id: Long,
        thread: Int,
    ): CompletableFuture<List<FrameState>> =
        request(id) { DebugProtocol.StackTrace(id, it, thread) }.thenApply { it.map(SessionDebugger.StackFrameInfo::state) }

    /** A stopped frame's scopes. */
    fun scopes(
        id: Long,
        frame: Int,
    ): CompletableFuture<List<ScopeState>> =
        request(id) {
            DebugProtocol.Scopes(id, it, frame)
        }.thenApply { it.map(SessionDebugger.Scope::state) }

    /** The variables of a scope, or the fields or elements of a variable, as the waiting fiber read them. */
    fun variables(
        id: Long,
        reference: Int,
        hex: Boolean,
        filter: ChildFilter,
        start: Int,
        count: Int,
    ): CompletableFuture<List<VariableState>> =
        request(id) { DebugProtocol.Variables(id, it, reference, hex, SessionDebugger.Filter.valueOf(filter.name), start, count) }
            .thenApply { it.map(SessionDebugger.Variable::state) }

    /** Replaces a debugged run's exception stops. */
    fun setExceptionStops(
        id: Long,
        stops: ExceptionStops,
    ) = command(DebugProtocol.SetExceptionStops(id, stops.record()))

    /** Changes a variable of the stopped program to an expression's value; fails with the reason when it cannot. */
    fun setVariable(
        id: Long,
        reference: Int,
        name: String,
        expression: String,
        hex: Boolean,
    ): CompletableFuture<VariableState> =
        request(id) { DebugProtocol.SetVariable(id, it, reference, name, expression, hex) }.thenApply { it.state() }

    /** The names that complete the name typed at an offset of a text in a stopped frame; fails with the reason when it cannot. */
    fun completions(
        id: Long,
        frame: Int,
        text: String,
        offset: Int,
    ): CompletableFuture<CompletionsState> = request(id) { DebugProtocol.Completions(id, it, frame, text, offset) }.thenApply { it.state() }

    /** Evaluates an expression in a stopped frame; fails with the reason when it cannot. */
    fun evaluate(
        id: Long,
        frame: Int,
        expression: String,
        hex: Boolean,
    ): CompletableFuture<VariableState> = request(id) { DebugProtocol.Evaluate(id, it, frame, expression, hex) }.thenApply { it.state() }

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
        val (number, reply) = send(id) { DebugProtocol.RunCode(id, it, frame, expression, limit.inWholeMilliseconds, program, hex) }
        val answer = reply.thenApply { it.state() }
        answer.whenComplete { _, failure ->
            if (failure is CancellationException && pending.remove(number) != null) command(DebugProtocol.Cancel(id, number))
        }
        return answer
    }

    /** The exception a stopped run's fiber stopped it at; null when it stopped it for another reason. */
    fun exceptionInfo(
        id: Long,
        thread: Int,
    ): CompletableFuture<ExceptionInfoState?> = request(id) { DebugProtocol.ExceptionInfo(id, it, thread) }.thenApply { it?.state() }

    /** Steps a stopped run's fiber, [alone] while the others stay stopped; false when that fiber does not wait. */
    fun step(
        id: Long,
        thread: Int,
        kind: StepKind,
        alone: Boolean = false,
    ): CompletableFuture<Boolean> = request(id) { DebugProtocol.Step(id, it, thread, SessionDebugger.Step.valueOf(kind.name), alone) }

    /** Pauses a debugged run at the next line one of its fibers reaches. */
    fun pause(id: Long) = command(DebugProtocol.Pause(id))

    /** Resumes a stopped run. */
    fun resume(id: Long) = command(DebugProtocol.Continue(id))

    /** Continues one fiber of a stopped run alone, while the others stay stopped; false when that fiber does not wait. */
    fun resumeAlone(
        id: Long,
        thread: Int,
    ): CompletableFuture<Boolean> = request(id) { DebugProtocol.ContinueAlone(id, it, thread) }

    /**
     * Sends the request that [make] makes, with its number, to a run's debugger and answers with the reply's answer;
     * fails when the run's program cannot be reached, ends before the reply, or answers with an error.
     */
    private fun <A> request(
        id: Long,
        make: (Long) -> DebugProtocol.Request<A>,
    ): CompletableFuture<A> = send(id, make).second

    /** Sends a request as [request] does, and answers with its number too, by which a `cancel` names it. */
    private fun <A> send(
        id: Long,
        make: (Long) -> DebugProtocol.Request<A>,
    ): Pair<Long, CompletableFuture<A>> {
        val number = nextRequest.getAndIncrement()
        val request = make(number)
        val reply = CompletableFuture<DebugProtocol.Reply>()
        pending[number] = Pending(id, reply)
        if (!deliver(id, DebugProtocol.json(request))) {
            pending.remove(number)?.reply?.completeExceptionally(IllegalStateException("The program is not running"))
        }
        return number to reply.thenApply { DebugProtocol.answer(request, it) }
    }

    /** Fails the requests still waiting for a reply from a run that has ended. */
    protected fun abandon(id: Long) {
        pending.entries.removeIf { (_, request) ->
            (request.run == id).also { if (it) request.reply.completeExceptionally(IllegalStateException("The program has ended")) }
        }
    }

    /** Sends a command to a run's debugger, when its program can be reached. */
    protected fun command(command: DebugProtocol.Command) {
        deliver(command.id(), DebugProtocol.json(command))
    }

    /** Sends one of the worker's own commands for one run, when its program can be reached. */
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
        event: DebugProtocol.Event,
        listener: RunListener?,
    ): Boolean {
        when (event) {
            is DebugProtocol.Breakpoints -> listener?.breakpoints(event.path(), event.breakpoints().map(SessionDebugger.Breakpoint::state))

            is DebugProtocol.FunctionBreakpoints ->
                listener?.functionBreakpoints(
                    event.breakpoints().map(SessionDebugger.Breakpoint::functionState),
                )

            is DebugProtocol.Stopped -> listener?.stopped(event.stop().state())

            is DebugProtocol.Logged -> listener?.logged(event.text())

            is DebugProtocol.Reply ->
                pending.remove(event.request())?.reply?.let { reply ->
                    val error = event.error()
                    if (error == null) reply.complete(event) else reply.completeExceptionally(IllegalStateException(error))
                }

            else -> return false
        }
        return true
    }
}
