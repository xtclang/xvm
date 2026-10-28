package org.xvm.lsp.server

import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkBuildResult
import org.xvm.lsp.adapter.xdk.XdkBuiltModule
import org.xvm.lsp.model.toLsp
import org.xvm.lsp.run.RunWorkerProcess
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** `xtc/build` parameters: a module name or root URI, or every source module when [module] is absent. */
data class BuildParams(
    val module: String? = null,
    val rebuild: Boolean? = null,
)

/** `xtc/build` result. Diagnostics are grouped by document, in the shape of `publishDiagnostics`. */
data class BuildResponse(
    val succeeded: Boolean,
    val cancelled: Boolean,
    val modules: List<XdkBuiltModule>,
    val diagnostics: List<PublishDiagnosticsParams>,
    val outputDirectory: String?,
)

/**
 * `xtc/run` parameters. The client chooses [runId] so it can attach its console before any output arrives;
 * [args] are passed to the entry method as given, never shell-parsed.
 */
data class RunParams(
    val module: String? = null,
    val method: String? = null,
    val args: List<String>? = null,
    val workingDirectory: String? = null,
    val runId: String? = null,
)

/** `xtc/run` result: the build outcome, and the run's ID when the build succeeded and the run started. */
data class RunResponse(
    val runId: String?,
    val build: BuildResponse,
)

data class StopRunParams(
    val runId: String? = null,
)

/**
 * `xtc/runInput` parameters: [text] for the program's console, sent as typed, with line ends; [end] ends
 * the input, after which a program waiting for a line gets an empty one.
 */
data class RunInputParams(
    val runId: String? = null,
    val text: String? = null,
    val end: Boolean? = null,
)

data class RunOutputParams(
    val runId: String,
    val text: String,
)

/** A run's single terminal outcome: the entry method's exit code, or the reason it failed. */
data class RunExitedParams(
    val runId: String,
    val exitCode: Long?,
    val error: String?,
)

/** Client side of the embedded execution notifications. */
interface XtcLanguageClient : LanguageClient {
    @JsonNotification("xtc/runOutput")
    fun runOutput(params: RunOutputParams)

    @JsonNotification("xtc/runExited")
    fun runExited(params: RunExitedParams)
}

/**
 * Embedded build and run for IDE clients: builds through the compiler adapter's serialized queue, then runs
 * the built module in the execution worker JVM. Clients that do not implement [XtcLanguageClient] can still
 * build; runs then have nowhere to report.
 */
internal class EmbeddedRuns(
    private val adapter: Adapter,
    private val client: () -> LanguageClient?,
    private val worker: RunWorkerProcess = RunWorkerProcess(),
) : Closeable {
    private val workerIds = ConcurrentHashMap<String, Long>()

    /** Input typed while a run is still building, forwarded in order when the program starts. */
    private val waiting = HashMap<String, WaitingInput>()
    private val inputLock = Any()

    private class WaitingInput {
        val text = StringBuilder()
        var ended = false
    }

    fun build(params: BuildParams): CompletableFuture<BuildResponse> =
        compiler()?.buildAsync(params.module, params.rebuild == true)?.thenApply(::response) ?: unsupported()

    fun run(params: RunParams): CompletableFuture<RunResponse> {
        val module = params.module ?: return CompletableFuture.failedFuture(IllegalArgumentException("A run requires a module"))
        val compiler = compiler() ?: return unsupported()
        val runId = params.runId ?: UUID.randomUUID().toString()
        synchronized(inputLock) { waiting[runId] = WaitingInput() }
        return compiler
            .buildAsync(module, false)
            .thenApply { result ->
                val build = response(result)
                val target = result.modules.lastOrNull()
                if (!result.succeeded || target == null) {
                    synchronized(inputLock) { waiting.remove(runId) }
                    return@thenApply RunResponse(null, build)
                }
                // a run can end before start returns, e.g. while another program still runs
                val ended = AtomicBoolean()
                val id =
                    worker.start(
                        RunWorkerProcess.Request(
                            result.modulePath,
                            target.name,
                            params.method ?: "run",
                            params.args.orEmpty(),
                            params.workingDirectory ?: result.workingDirectory,
                        ),
                        object : RunWorkerProcess.Listener {
                            override fun output(text: String) {
                                (client() as? XtcLanguageClient)?.runOutput(RunOutputParams(runId, text))
                            }

                            override fun exited(
                                code: Long?,
                                error: String?,
                            ) {
                                synchronized(inputLock) {
                                    ended.set(true)
                                    waiting.remove(runId)
                                    workerIds.remove(runId)
                                }
                                (client() as? XtcLanguageClient)?.runExited(RunExitedParams(runId, code, error))
                            }
                        },
                    )
                synchronized(inputLock) {
                    if (!ended.get()) workerIds[runId] = id
                    waiting.remove(runId)?.takeUnless { ended.get() }?.let { typed ->
                        if (typed.text.isNotEmpty()) worker.input(id, typed.text.toString())
                        if (typed.ended) worker.endInput(id)
                    }
                }
                RunResponse(runId, build)
            }.whenComplete { _, failure -> if (failure != null) synchronized(inputLock) { waiting.remove(runId) } }
    }

    /** Forwards console input to the run, or holds it while the run is still building. */
    fun input(params: RunInputParams): Boolean {
        val runId = params.runId ?: return false
        synchronized(inputLock) {
            val id = workerIds[runId]
            if (id != null) {
                params.text?.takeIf { it.isNotEmpty() }?.let { worker.input(id, it) }
                if (params.end == true) worker.endInput(id)
                return true
            }
            val typed = waiting[runId] ?: return false
            params.text?.let(typed.text::append)
            if (params.end == true) typed.ended = true
            return true
        }
    }

    /** Stops the run; the client still receives its `xtc/runExited`. False when no such run is active. */
    fun stop(params: StopRunParams): Boolean {
        val id = params.runId?.let(workerIds::get) ?: return false
        worker.stop(id)
        return true
    }

    /** Runs still tracked, here and by the execution workers, which is none once every run has ended; for tests. */
    internal fun tracked(): Int = synchronized(inputLock) { workerIds.size + waiting.size } + worker.tracked()

    /** Process IDs of the live execution workers, for tests. */
    internal fun workerPids(): List<Long> = worker.workerPids()

    override fun close() = worker.close()

    private fun compiler(): XdkAdapter? = adapter as? XdkAdapter

    private fun <T> unsupported(): CompletableFuture<T> =
        CompletableFuture.failedFuture(
            UnsupportedOperationException("Embedded build and run require the compiler language service"),
        )

    private fun response(result: XdkBuildResult) =
        BuildResponse(
            succeeded = result.succeeded,
            cancelled = result.cancelled,
            modules = result.modules,
            diagnostics =
                result.diagnostics
                    .groupBy { it.location.uri }
                    .map { (uri, diagnostics) -> PublishDiagnosticsParams(uri, diagnostics.map { it.toLsp(uri) }) },
            outputDirectory = result.modulePath.firstOrNull(),
        )
}
