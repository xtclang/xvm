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

/** A launched program's output, and its single exit: the entry method's exit code, or the reason it failed. */
interface ProgramEvents {
    fun output(text: String)

    fun exited(
        code: Long?,
        error: String?,
    )
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
     * Builds [target] and, if the build succeeds, starts it once [ready] completes, under [runId]. [events] receives
     * the program's output and its exit, also when it is stopped before it starts.
     */
    fun launch(
        runId: String,
        target: LaunchTarget,
        events: ProgramEvents,
        ready: CompletionStage<*>,
    ): CompletableFuture<LaunchResult>

    /** Console input for the run, with its line end. False when no such run is building or running. */
    fun input(
        runId: String,
        text: String,
    ): Boolean

    /** Stops the run, or keeps it from starting if it is still building. False when no such run is active. */
    fun stop(runId: String): Boolean
}
