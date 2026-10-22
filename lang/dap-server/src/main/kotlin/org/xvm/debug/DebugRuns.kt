package org.xvm.debug

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * What a debug session launches: the module to build and run, as the language server's `xtc/run` takes it.
 * [module] is a module name or a source URI of the module; [moduleName] still finds the module when that URI no
 * longer names one. [args] are passed to the entry method as given.
 */
data class LaunchTarget(
    val module: String,
    val moduleName: String? = null,
    val method: String? = null,
    val args: List<String> = emptyList(),
    val workingDirectory: String? = null,
)

/** A breakpoint a session asks for: its DAP ID, which the session chooses, and its 1-based line. */
data class BreakpointRequest(
    val id: Int,
    val line: Int,
)

/** The breakpoints of one source file, by its path; none removes the file's breakpoints. */
data class SourceBreakpoints(
    val path: String,
    val breakpoints: List<BreakpointRequest>,
)

/**
 * A breakpoint as the program installed it: the line it is on, which may follow the requested line when that line
 * has no code of its own, whether it is installed, and if not, why.
 */
data class BreakpointState(
    val id: Int,
    val line: Int,
    val verified: Boolean,
    val message: String? = null,
)

/** A fiber of the stopped program, by its ID and its service's name. */
data class ThreadState(
    val id: Int,
    val name: String,
)

/** One frame of a stopped fiber's stack: its ID, the method's name, its source file if known, and its 1-based line. */
data class FrameState(
    val id: Int,
    val name: String,
    val path: String?,
    val line: Int,
)

/** A scope of a stopped frame: its name, and the reference its variables are read by. */
data class ScopeState(
    val name: String,
    val variablesReference: Int,
)

/**
 * A variable of a stopped program, or a field or element of one: its name, its value and type in words (the type
 * is empty when it is not known), and the reference its own fields or elements are read by, or 0 when it has none.
 */
data class VariableState(
    val name: String,
    val value: String,
    val type: String,
    val variablesReference: Int,
)

/** Which thrown exceptions stop a debugged program: every one, or those that no frame will catch. */
data class ExceptionStops(
    val all: Boolean = false,
    val uncaught: Boolean = false,
)

/**
 * What a debugged program starts with: the breakpoints installed before its first line, its exception stops, and
 * whether it stops at its first line.
 */
data class DebugSetup(
    val breakpoints: List<SourceBreakpoints>,
    val exceptions: ExceptionStops = ExceptionStops(),
    val stopOnEntry: Boolean = false,
)

/** How a stopped fiber is stepped: over a call, into one, or out of the current method. */
enum class StepKind {
    OVER,
    IN,
    OUT,
}

/** Why and where the program stopped: the reason, what stopped it in words, the fiber, and the breakpoints' IDs. */
data class StopState(
    val reason: String,
    val description: String?,
    val threadId: Int,
    val breakpointIds: List<Int>,
)

/** A launched program's events. */
interface ProgramEvents {
    fun output(text: String)

    /** The single exit: the entry method's exit code, or the reason it failed. */
    fun exited(
        code: Long?,
        error: String?,
    )

    /** The breakpoints of one file as installed when a debugged program starts. */
    fun breakpoints(
        path: String,
        states: List<BreakpointState>,
    ) {}

    /** A debugged program stopped; it waits until it is resumed. */
    fun stopped(stop: StopState) {}
}

/** A launch's outcome: the run's ID when the program was started, or what a failed build reported. */
data class LaunchResult(
    val runId: String?,
    val buildReport: String,
)

/**
 * The build and execution service a debug session launches through, which is the language server's embedded build
 * and run. A session never starts a runtime of its own.
 */
interface DebugRuns {
    /**
     * Builds [target] and, if the build succeeds, starts it under [runId] once [ready] completes. [ready]'s value is
     * the debugger's setup before the program's first line, or null to run it without a debugger. [events] receives
     * the program's output and its exit, also when it is stopped before it starts.
     */
    fun launch(
        runId: String,
        target: LaunchTarget,
        events: ProgramEvents,
        ready: CompletionStage<out DebugSetup?>,
    ): CompletableFuture<LaunchResult>

    /** Console input for the run, with its line end. False when no such run is building or running. */
    fun input(
        runId: String,
        text: String,
    ): Boolean

    /** Stops the run, or keeps it from starting if it is still building. False when no such run is active. */
    fun stop(runId: String): Boolean

    /** Replaces the exception stops of a debugged program that runs. False when no such run is active. */
    fun setExceptionStops(
        runId: String,
        stops: ExceptionStops,
    ): Boolean

    /** Replaces one file's breakpoints in a debugged program that runs. */
    fun setBreakpoints(
        runId: String,
        breakpoints: SourceBreakpoints,
    ): CompletableFuture<List<BreakpointState>>

    /** The fibers of the stopped program; none while it runs. */
    fun threads(runId: String): CompletableFuture<List<ThreadState>>

    /** A stopped fiber's stack, innermost frame first. */
    fun stackTrace(
        runId: String,
        threadId: Int,
    ): CompletableFuture<List<FrameState>>

    /** The scopes of a stopped frame, which are its local variables; none for a frame whose variables are not read. */
    fun scopes(
        runId: String,
        frameId: Int,
    ): CompletableFuture<List<ScopeState>>

    /**
     * The variables of a scope, or the fields or elements of a variable, by its reference; none for a reference
     * from before the program last resumed.
     */
    fun variables(
        runId: String,
        reference: Int,
    ): CompletableFuture<List<VariableState>>

    /**
     * Evaluates a path to a value, such as `point.x` or `ints[1]`, in a stopped frame, without running the program's
     * code: the value as a variable named by the path. Fails, with the reason as its message, if it cannot.
     */
    fun evaluate(
        runId: String,
        frameId: Int,
        expression: String,
    ): CompletableFuture<VariableState>

    /**
     * Resumes the stopped program, stepping one of its fibers: it stops again, with the reason `step`, where the step
     * ends. False when that fiber does not wait; the program then stays stopped.
     */
    fun step(
        runId: String,
        threadId: Int,
        kind: StepKind,
    ): CompletableFuture<Boolean>

    /**
     * Pauses the running program: it stops, with the reason `pause`, at the next line any of its fibers reaches.
     * False when no such run is active.
     */
    fun pause(runId: String): Boolean

    /** Resumes the stopped program. False when no such run is active. */
    fun resume(runId: String): Boolean
}
