package org.xvm.lsp.server

import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory
import org.xvm.debug.AttachTarget
import org.xvm.debug.BreakpointState
import org.xvm.debug.ChildFilter
import org.xvm.debug.CompletionsState
import org.xvm.debug.DataBreakpointRequest
import org.xvm.debug.DataInfoState
import org.xvm.debug.DebugSetup
import org.xvm.debug.DebugTask
import org.xvm.debug.ExceptionInfoState
import org.xvm.debug.ExceptionStops
import org.xvm.debug.FrameState
import org.xvm.debug.FunctionBreakpointRequest
import org.xvm.debug.LaunchTarget
import org.xvm.debug.ModuleState
import org.xvm.debug.ProgramEvents
import org.xvm.debug.ScopeState
import org.xvm.debug.SourceBreakpoints
import org.xvm.debug.StepKind
import org.xvm.debug.StepTargetState
import org.xvm.debug.StopState
import org.xvm.debug.TestEvent
import org.xvm.debug.TestSelector
import org.xvm.debug.ThreadState
import org.xvm.debug.VariableState
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkBuildResult
import org.xvm.lsp.adapter.xdk.XdkBuiltModule
import org.xvm.lsp.adapter.xdk.XdkSources
import org.xvm.lsp.model.toLsp
import org.xvm.lsp.run.AttachedProgram
import org.xvm.lsp.run.RunDebugClient
import org.xvm.lsp.run.RunWorkerProcess
import org.xvm.lsp.run.TestProcess
import org.xvm.lsp.util.snapshot
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
 * `xtc/run` parameters. [module] is a module name or root URI; a client that sends a root URI also sends the
 * module's name as [moduleName], which identifies the module when the URI no longer does, as after its root file
 * was renamed or moved. The client chooses [runId] so it can attach its console before any output arrives;
 * [args] are passed to the entry method as given, never shell-parsed. With [tests], xunit runs the module's tests that
 * they select instead, all of them for an empty list, as `xtc test` does, and the server reports their execution as
 * `xtc/testEvent` notifications, which are not in the output; the entry method and its arguments are not used.
 */
data class RunParams(
    val module: String? = null,
    val method: String? = null,
    val args: List<String>? = null,
    val workingDirectory: String? = null,
    val runId: String? = null,
    val moduleName: String? = null,
    val tests: List<TestSelector>? = null,
)

/** `xtc/run` result: the build outcome, and the run's ID when the build succeeded and the run started. */
data class RunResponse(
    val runId: String?,
    val build: BuildResponse,
)

/** A Run lens of a document: the module it runs and the zero-based line it is on. */
data class RunLens(
    val module: String,
    val line: Int,
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

/** `xtc/testEvent` parameters: an [event] of the tests that the run [runId] runs, as xunit reported it ([TestEvent]). */
data class TestEventParams(
    val runId: String,
    val event: TestEvent,
)

/** A run's single terminal outcome: the entry method's exit code, or the reason it failed. */
data class RunExitedParams(
    val runId: String,
    val exitCode: Long?,
    val error: String?,
)

/**
 * `xtc/debugTask` parameters: the task that runs a debug session's ([sessionId]) module under the debugger, its
 * Gradle task or the xtc command, as the launch's [buildMode] says ([DebugTask.buildMode]): the module's [module],
 * [method] and [args], as a Gradle run or `xtc run` takes them, with the launcher's `--debug` options [debug], which a
 * Gradle task takes as the Gradle property `xtcDebug`. The program's XDK has the debugger's agent. With [tests], the
 * task runs those tests of the module instead, as `testXtc` or `xtc test` take them, all of them for an empty list,
 * with xunit's `json` report, whose events ([TestEvent]) the client reads from the task's output.
 */
data class DebugTaskParams(
    val sessionId: String,
    val module: String,
    val method: String?,
    val args: List<String>,
    val debug: String,
    val buildMode: String,
    val tests: List<TestSelector>? = null,
)

/** The end of an `xtc/debugTask`: the task's exit code, when it has one. */
data class DebugTaskResult(
    val exitCode: Int? = null,
)

/** Client side of the embedded execution notifications. */
interface XtcLanguageClient : LanguageClient {
    @JsonNotification("xtc/runOutput")
    fun runOutput(params: RunOutputParams)

    @JsonNotification("xtc/runExited")
    fun runExited(params: RunExitedParams)

    /** An event of the tests that a run with `tests` runs, ahead of the run's exit; see [RunParams]. */
    @JsonNotification("xtc/testEvent")
    fun testEvent(params: TestEventParams) {}

    /** One DAP message from a debug session to the client; see [DebugSessions]. */
    @JsonNotification("xtc/debugMessage")
    fun debugMessage(params: DebugMessageParams) {}

    /**
     * Runs a debug session's module with its Gradle task or the xtc command, as [DebugTaskParams] says, and answers
     * once the task has ended; canceling the request ends the task.
     */
    @JsonRequest("xtc/debugTask")
    fun debugTask(params: DebugTaskParams): CompletableFuture<DebugTaskResult> =
        CompletableFuture.failedFuture(
            UnsupportedOperationException(EmbeddedRuns.noTasks(DebugTask.of(params.buildMode) ?: DebugTask.GRADLE)),
        )
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
    /** How long attaching to a program waits for it to answer, [AttachedProgram.CONNECT_LIMIT] unless a test lifts it. */
    private val attachLimit: Duration = AttachedProgram.CONNECT_LIMIT,
) : Closeable {
    private val workerIds = ConcurrentHashMap<String, Long>()

    /** The programs a debugger attached to, by run ID, while it is attached. */
    private val attached = ConcurrentHashMap<String, AttachedProgram>()

    /** The tasks that run programs under the debugger for the client, by run ID, until their program ends. */
    private val taskRuns = ConcurrentHashMap<String, TaskRun>()

    /** The processes that run a module's tests, by run ID, until they end. */
    private val testRuns = ConcurrentHashMap<String, TestProcess>()

    /** The sources each run under the debugger was compiled from, by run ID, while it runs. */
    private val compiled = ConcurrentHashMap<String, CompiledSources>()

    /** Input typed while a run is still building, forwarded in order when the program starts. */
    private val waiting = HashMap<String, WaitingInput>()
    private val inputLock = Any()

    /**
     * A task of the client's, [kind], that runs a program under the debugger, and where the debugger waits for it to
     * connect.
     */
    private class TaskRun(
        val kind: DebugTask,
        val task: CompletableFuture<DebugTaskResult>,
        val server: ServerSocket,
    ) {
        @Volatile
        var stopped = false

        /** Ends the task, and the program with it, as a canceled Gradle run or xtc command ends, and stops waiting for it. */
        fun stop() {
            stopped = true
            task.cancel(true)
            runCatching { server.close() }
        }
    }

    private class WaitingInput {
        val text = StringBuilder()
        var ended = false

        /** Stopped before the program started, which it then never does. */
        var stopped = false
    }

    fun build(params: BuildParams): CompletableFuture<BuildResponse> =
        compiler()?.buildAsync(params.module, params.rebuild == true)?.thenApply(::response) ?: unsupported()

    fun run(params: RunParams): CompletableFuture<RunResponse> {
        val runId = params.runId ?: UUID.randomUUID().toString()
        return launch(
            params.copy(runId = runId),
            object : ProgramEvents {
                override fun output(text: String) {
                    (client() as? XtcLanguageClient)?.runOutput(RunOutputParams(runId, text))
                }

                override fun tested(event: TestEvent) {
                    (client() as? XtcLanguageClient)?.testEvent(TestEventParams(runId, event))
                }

                override fun exited(
                    code: Long?,
                    error: String?,
                ) {
                    (client() as? XtcLanguageClient)?.runExited(RunExitedParams(runId, code, error))
                }
            },
        )
    }

    /**
     * Builds the module and, if the build succeeds, runs it once [ready] completes, as `xtc/run` does at once and a
     * debug session does after its client's configuration. [ready]'s value is the debugger's setup before the
     * program's first line, or null to run it without the debugger. [events] receives the program's output and its
     * single exit, also when the run is stopped before the program starts, and a debugged run's breakpoints and stops.
     */
    internal fun launch(
        params: RunParams,
        events: ProgramEvents,
        ready: CompletionStage<out DebugSetup?> = CompletableFuture.completedFuture(null),
    ): CompletableFuture<RunResponse> {
        val module = params.module ?: return CompletableFuture.failedFuture(IllegalArgumentException("A run requires a module"))
        val compiler = compiler() ?: return unsupported()
        val runId = params.runId ?: UUID.randomUUID().toString()
        synchronized(inputLock) { waiting[runId] = WaitingInput() }
        return compiler
            .buildAsync(module, false, params.moduleName)
            .thenCompose { result ->
                val build = response(result)
                val target = result.modules.lastOrNull()
                if (!result.succeeded || target == null) {
                    logger.info(
                        "run not started: run={} build={} outcome={}",
                        runId,
                        result.id,
                        if (result.cancelled) "cancelled" else "failed",
                    )
                    synchronized(inputLock) { waiting.remove(runId) }
                    return@thenCompose CompletableFuture.completedFuture(RunResponse(null, build))
                }
                ready.thenCompose { setup ->
                    val tests = params.tests
                    if (tests == null) {
                        start(runId, params, result, target.name, setup, events)
                        CompletableFuture.completedFuture(RunResponse(runId, build))
                    } else {
                        startTests(runId, params, tests, result, target, setup, events).thenApply { RunResponse(runId, build) }
                    }
                }
            }.whenComplete { _, failure -> if (failure != null) synchronized(inputLock) { waiting.remove(runId) } }
    }

    /**
     * Attaches the debugger to a program that the launcher runs with `--debug`, whose debugger agent listens at
     * [target]'s host and port, once [ready] completes with the debugger's setup; the workspace's modules are the
     * program's own. Answers with the program's module once it has taken the setup; [events] receives the debugged
     * program's breakpoints and stops, and its exit, or the detach, as the run's last event. The program's console is
     * its own: no output comes, and none goes to it.
     */
    internal fun attach(
        runId: String,
        target: AttachTarget,
        events: ProgramEvents,
        ready: CompletionStage<out DebugSetup>,
    ): CompletableFuture<String> {
        val modules = compiler()?.sourceModuleInputs()?.map { it.name }.orEmpty()
        val listener =
            object : ProgramEvents by events {
                override fun exited(
                    code: Long?,
                    error: String?,
                ) {
                    attached.remove(runId)
                    events.exited(code, error)
                }

                override fun detached(reason: String?) {
                    attached.remove(runId)
                    events.detached(reason)
                }
            }
        return ready.toCompletableFuture().thenComposeAsync({ setup ->
            val program = AttachedProgram.attach(target.host, target.port, target.token, listener, attachLimit)
            attached[runId] = program
            logger.info("run attached: run={} module={} at={}:{}", runId, program.module, target.host, target.port)
            program.debug(modules, setup).thenApply { program.module }.whenComplete { _, failure ->
                if (failure != null && attached.remove(runId) != null) program.close()
            }
        }, attaching)
    }

    /**
     * Has the client run [target]'s module with [kind], its Gradle task or the xtc command, for the debug session
     * [sessionId], and attaches the debugger to the program, as [attach] does, once it connects and [ready] completes:
     * the task runs the program with `--debug connect:`, this server's port and the launch's own token. Answers with
     * the program's module once it has taken the setup; fails with the reason when the task ends first. [events]
     * receives what an attached program's do, but the program's end is an exit: stopping the run ends the task, and
     * the program with it.
     */
    internal fun launchWithTask(
        runId: String,
        sessionId: String,
        target: LaunchTarget,
        kind: DebugTask,
        events: ProgramEvents,
        ready: CompletionStage<out DebugSetup>,
    ): CompletableFuture<String> {
        val client =
            client() as? XtcLanguageClient
                ?: return CompletableFuture.failedFuture(UnsupportedOperationException(noTasks(kind)))
        val modules = compiler()?.sourceModuleInputs()?.map { it.name }.orEmpty()
        val token = UUID.randomUUID().toString()
        // IPv4's loopback address, which the agent's --debug options can name
        val server = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))
        val debug = "connect:$LOOPBACK:${server.localPort},suspend,token=$token"
        val task =
            client.debugTask(
                DebugTaskParams(
                    sessionId,
                    taskModule(target),
                    target.method,
                    target.args,
                    debug,
                    kind.buildMode,
                    target.tests,
                ),
            )
        val run = TaskRun(kind, task, server)
        taskRuns[runId] = run
        // a task that ends before its program connects ends the wait
        task.whenComplete { _, _ -> runCatching { server.close() } }
        val listener =
            object : ProgramEvents by events {
                /**
                 * The program's exit comes over its own connection, ahead of what it printed last, which comes
                 * through its task: the client delivers a task's output before it answers that the task has ended,
                 * so the exit is reported once it has, or [TASK_END_WAIT] after the exit if the task does not end.
                 */
                override fun exited(
                    code: Long?,
                    error: String?,
                ) {
                    attached.remove(runId)
                    taskRuns.remove(runId)
                    task
                        .handle { _, _ -> }
                        .completeOnTimeout(Unit, TASK_END_WAIT.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                        .thenRun { events.exited(code, error) }
                }

                override fun detached(reason: String?) {
                    attached.remove(runId)
                    taskRuns.remove(runId)
                    events.exited(null, reason.takeUnless { run.stopped } ?: stoppedWith(kind))
                }
            }
        return CompletableFuture
            .supplyAsync({
                try {
                    AttachedProgram.accept(server, token, listener, attachLimit)
                } catch (failure: IOException) {
                    throw IOException(neverConnected(run, failure), failure)
                } finally {
                    runCatching { server.close() }
                }
            }, attaching)
            .thenCompose { program ->
                attached[runId] = program
                logger.info("run attached after {} started it: run={} module={}", kind.tool, runId, program.module)
                ready
                    .toCompletableFuture()
                    .thenCompose { setup -> program.debug(modules, setup) }
                    .thenApply { program.module }
                    .whenComplete { _, failure -> if (failure != null && attached.remove(runId) != null) program.close() }
            }.whenComplete { _, failure -> if (failure != null) taskRuns.remove(runId)?.stop() }
    }

    /**
     * The name of [target]'s module, which a Gradle task and `xtc run` take: a source URI names the workspace module it
     * roots.
     */
    private fun taskModule(target: LaunchTarget): String {
        target.moduleName?.let { return it }
        val file = XdkSources.file(target.module) ?: return target.module
        return compiler()?.sourceModuleInputs()?.firstOrNull { it.root == file }?.name ?: target.module
    }

    /** Why the program of a task's [run] never connected: it was stopped, or its task ended first. */
    private fun neverConnected(
        run: TaskRun,
        failure: IOException,
    ): String {
        val task = run.task
        return when {
            run.stopped -> STOPPED_BEFORE_START

            task.isCompletedExceptionally -> "The ${run.kind.task} could not run: ${task.exceptionNow().message}"

            task.isDone -> {
                val code =
                    task
                        .join()
                        .exitCode
                        ?.let { " with exit code $it" }
                        .orEmpty()
                "${run.kind.tool} ended$code before the program started; its output says why."
            }

            else -> failure.message ?: "The program did not connect"
        }
    }

    /**
     * Runs the tests of [target] that [tests] select in a [TestProcess], as `xtc test` does. With [setup], the process
     * runs under the debugger and connects to it, as a task's program does ([launchWithTask]), and the answer comes
     * once it has taken the setup; it fails with the reason when the process ends first. [events] receives the output,
     * the events that xunit reports in it ([TestEvent]) apart from the rest, and the single exit, as a run's, and a
     * debugged run's breakpoints and stops; ending the debugging ends the tests.
     */
    private fun startTests(
        runId: String,
        params: RunParams,
        tests: List<TestSelector>,
        result: XdkBuildResult,
        target: XdkBuiltModule,
        setup: DebugSetup?,
        events: ProgramEvents,
    ): CompletableFuture<Unit> {
        val artifact = target.artifact ?: return CompletableFuture.failedFuture(IllegalStateException("${target.name} was not written"))
        val token = UUID.randomUUID().toString()
        val server = setup?.let { ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)) }
        val command =
            TestProcess.command(
                artifact,
                result.modulePath,
                tests,
                File(File(result.workingDirectory), XUNIT_OUTPUT).path,
                server?.let { "connect:$LOOPBACK:${it.localPort},suspend,token=$token" },
            )
        // a run stopped while its module built never starts; one stopped from here on stops its process
        val process =
            synchronized(inputLock) {
                if (waiting.remove(runId)?.stopped == true) {
                    null
                } else {
                    TestProcess
                        .start(command, File(params.workingDirectory ?: result.workingDirectory)) { line ->
                            TestEvent.parse(line)?.let(events::tested) ?: events.output(line)
                        }.also { testRuns[runId] = it }
                }
            }
        if (process == null) {
            runCatching { server?.close() }
            logger.info("tests stopped before they started: run={} build={}", runId, result.id)
            events.exited(null, STOPPED_BEFORE_START)
            return CompletableFuture.completedFuture(Unit)
        }
        logger.info("tests starting: run={} build={} artifact={} tests={} debug={}", runId, result.id, artifact, tests.size, server != null)
        if (server == null) {
            process.ended.thenAccept { code ->
                testRuns.remove(runId)
                if (process.stopped) events.exited(null, STOPPED) else events.exited(code.toLong(), null)
            }
            return CompletableFuture.completedFuture(Unit)
        }
        // a process that ends before it connects ends the wait
        process.ended.whenComplete { _, _ -> runCatching { server.close() } }
        val over = AtomicBoolean()

        // the program's end, over its connection, comes ahead of what it printed last, so it is reported once the
        // process has ended, or TASK_END_WAIT after it if the process does not end; ending the debugging ends it
        fun end(
            code: Long?,
            error: String?,
        ) {
            attached.remove(runId)
            if (!over.compareAndSet(false, true)) return
            process.ended
                .handle { _, _ -> }
                .completeOnTimeout(Unit, TASK_END_WAIT.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .thenRun {
                    testRuns.remove(runId)
                    process.stop()
                    events.exited(code, error)
                }
        }
        val listener =
            object : ProgramEvents by events {
                override fun exited(
                    code: Long?,
                    error: String?,
                ) = end(code, error)

                override fun detached(reason: String?) = end(null, if (process.stopped) STOPPED else reason)
            }
        val modules = compiler()?.sourceModuleInputs()?.map { it.name }.orEmpty()
        return CompletableFuture
            .supplyAsync({
                try {
                    AttachedProgram.accept(server, token, listener, attachLimit)
                } catch (failure: IOException) {
                    throw IOException(testsNeverConnected(process, failure), failure)
                } finally {
                    runCatching { server.close() }
                }
            }, attaching)
            .thenCompose { program ->
                attached[runId] = program
                logger.info("tests attached: run={} module={}", runId, program.module)
                program
                    .debug(modules, setup)
                    .whenComplete { _, failure -> if (failure != null && attached.remove(runId) != null) program.close() }
            }.whenComplete { _, failure -> if (failure != null) testRuns.remove(runId)?.stop() }
    }

    /** Why the test process [process] never connected to the debugger: it was stopped, or it ended first. */
    private fun testsNeverConnected(
        process: TestProcess,
        failure: IOException,
    ): String =
        when {
            process.stopped -> STOPPED_BEFORE_START
            process.ended.isDone -> "xtc test ended with exit code ${process.ended.join()} before the tests started; its output says why."
            else -> failure.message ?: "The tests did not connect"
        }

    private fun start(
        runId: String,
        params: RunParams,
        result: XdkBuildResult,
        module: String,
        setup: DebugSetup?,
        events: ProgramEvents,
    ) {
        val stoppedEarly = synchronized(inputLock) { waiting[runId]?.stopped == true && waiting.remove(runId) != null }
        if (stoppedEarly) {
            logger.info("run stopped before it started: run={} build={}", runId, result.id)
            events.exited(null, STOPPED_BEFORE_START)
            return
        }
        // the debugger places breakpoints on the lines the program was compiled from, which the editor may have changed
        // since; one on a line changed since has no code, and says so before the program starts
        val sources =
            setup
                ?.let {
                    CompiledSources(
                        result.compiled,
                    ) { file -> compiler()?.sourceText(file) }
                }?.also { compiled[runId] = it }
        val placed =
            setup?.copy(
                breakpoints =
                    setup.breakpoints.map { file ->
                        val lines = sources?.lines(file.path) ?: LineMap.SAME
                        refused(file, lines).takeIf { it.isNotEmpty() }?.let { events.breakpoints(file.path, it) }
                        placed(file, lines)
                    },
            )
        logger.info(
            "run starting: run={} build={} artifact={} debug={}",
            runId,
            result.id,
            result.modules.lastOrNull { it.name == module }?.logged ?: module,
            setup != null,
        )
        // a run can end before start returns, e.g. while another program still runs
        val ended = AtomicBoolean()
        val id =
            worker.start(
                RunWorkerProcess.Request(
                    result.modulePath,
                    module,
                    params.method ?: "run",
                    params.args.orEmpty(),
                    params.workingDirectory ?: result.workingDirectory,
                    placed,
                    runId,
                ),
                object : ProgramEvents {
                    override fun output(text: String) = events.output(text)

                    override fun breakpoints(
                        path: String,
                        states: List<BreakpointState>,
                    ) {
                        val lines = sources?.lines(path) ?: LineMap.SAME
                        events.breakpoints(path, states.map { it.copy(line = lines.now(it.line)) })
                    }

                    override fun functionBreakpoints(states: List<BreakpointState>) =
                        events.functionBreakpoints(states.map { state -> now(state) { path -> sources?.lines(path) ?: LineMap.SAME } })

                    override fun stopped(stop: StopState) = events.stopped(stop)

                    override fun logged(text: String) = events.logged(text)

                    override fun exited(
                        code: Long?,
                        error: String?,
                    ) {
                        synchronized(inputLock) {
                            ended.set(true)
                            waiting.remove(runId)
                            workerIds.remove(runId)
                        }
                        compiled.remove(runId)
                        events.exited(code, error)
                    }
                },
            )
        // input typed while the module built goes to the program, unless the run was stopped meanwhile
        val stop =
            synchronized(inputLock) {
                if (!ended.get()) workerIds[runId] = id
                val typed = waiting.remove(runId)?.takeUnless { ended.get() } ?: return@synchronized false
                if (!typed.stopped) {
                    if (typed.text.isNotEmpty()) worker.input(id, typed.text.toString())
                    if (typed.ended) worker.endInput(id)
                }
                typed.stopped
            }
        if (stop) worker.stop(id)
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

    /**
     * Stops the run, or keeps it from starting while it still builds; the client still receives its `xtc/runExited`,
     * unless the build fails. A program a debugger attached to is not stopped: the debugger detaches from it, and it
     * runs on. False when no such run is active.
     */
    fun stop(params: StopRunParams): Boolean {
        val runId = params.runId ?: return false
        taskRuns[runId]?.let { run ->
            run.stop()
            return true
        }
        attached[runId]?.let { program ->
            program.detach()
            return true
        }
        val id =
            synchronized(inputLock) {
                testRuns[runId]?.let { process ->
                    process.stop()
                    return true
                }
                workerIds[runId] ?: waiting[runId]?.let { typed ->
                    typed.stopped = true
                    return true
                }
            } ?: return false
        worker.stop(id)
        return true
    }

    /** Replaces the exception stops of a running program under the debugger. False when no such run is active. */
    internal fun setExceptionStops(
        runId: String,
        stops: ExceptionStops,
    ): Boolean = commanded(runId) { setExceptionStops(it, stops) }

    /**
     * Replaces one file's breakpoints in a running program under the debugger, on the lines the program was compiled
     * from; one on a line changed since is not installed, and its state says why.
     */
    internal fun setBreakpoints(
        runId: String,
        breakpoints: SourceBreakpoints,
    ): CompletableFuture<List<BreakpointState>> {
        val lines = lines(runId, breakpoints.path)
        val refused = refused(breakpoints, lines).associateBy { it.id }
        return debugged(runId) { setBreakpoints(it, placed(breakpoints, lines)) }.thenApply { states ->
            val installed = states.associateBy { it.id }
            // in the order they were asked for, on their lines now
            breakpoints.breakpoints.mapNotNull { request ->
                refused[request.id] ?: installed[request.id]?.let { it.copy(line = lines.now(it.line)) }
            }
        }
    }

    /** Names a variable of a stopped program under the debugger as a data breakpoint's target. */
    internal fun dataBreakpointInfo(
        runId: String,
        reference: Int,
        name: String,
    ): CompletableFuture<DataInfoState> = debugged(runId) { dataBreakpointInfo(it, reference, name) }

    /** Replaces the data breakpoints of a running program under the debugger. */
    internal fun setDataBreakpoints(
        runId: String,
        breakpoints: List<DataBreakpointRequest>,
    ): CompletableFuture<List<BreakpointState>> = debugged(runId) { setDataBreakpoints(it, breakpoints) }

    /** Replaces the function breakpoints of a running program under the debugger, each on its line now. */
    internal fun setFunctionBreakpoints(
        runId: String,
        breakpoints: List<FunctionBreakpointRequest>,
    ): CompletableFuture<List<BreakpointState>> =
        debugged(runId) { setFunctionBreakpoints(it, breakpoints) }.thenApply { states ->
            states.map { state -> now(state) { path -> lines(runId, path) } }
        }

    /** A function breakpoint on the line of its method's file where that line is now, which may have changed since the build. */
    private fun now(
        state: BreakpointState,
        lines: (String) -> LineMap,
    ) = state.path?.let { path -> state.copy(line = lines(path).now(state.line)) } ?: state

    /** The fibers of a stopped program under the debugger. */
    internal fun threads(runId: String): CompletableFuture<List<ThreadState>> = debugged(runId, RunDebugClient::threads)

    /** The calls that the line of a stopped frame of a program under the debugger makes. */
    internal fun stepInTargets(
        runId: String,
        frame: Int,
    ): CompletableFuture<List<StepTargetState>> = debugged(runId) { stepInTargets(it, frame) }

    /** Steps a stopped fiber of a program under the debugger into one of the calls of its line, [alone] if so asked. */
    internal fun stepInto(
        runId: String,
        thread: Int,
        target: Int,
        alone: Boolean,
    ): CompletableFuture<Boolean> = debugged(runId) { stepInto(it, thread, target, alone) }

    /** Restarts a stopped frame of a program under the debugger, which its caller calls again. */
    internal fun restartFrame(
        runId: String,
        frame: Int,
    ): CompletableFuture<Boolean> = debugged(runId) { restartFrame(it, frame) }

    /** The modules of a program under the debugger, its own and the libraries it uses. */
    internal fun modules(runId: String): CompletableFuture<List<ModuleState>> = debugged(runId, RunDebugClient::modules)

    /** A stopped fiber's stack, with each frame's line where it is now in its file, which may have changed since the build. */
    internal fun stackTrace(
        runId: String,
        thread: Int,
    ): CompletableFuture<List<FrameState>> =
        debugged(runId) { stackTrace(it, thread) }.thenApply { frames ->
            val maps = HashMap<String?, LineMap>()
            frames.map { frame -> frame.copy(line = maps.getOrPut(frame.path) { lines(runId, frame.path) }.now(frame.line)) }
        }

    /** The line map of a file of a run under the debugger, between the text it was compiled from and its text now. */
    private fun lines(
        runId: String,
        path: String?,
    ): LineMap = compiled[runId]?.lines(path) ?: LineMap.SAME

    /** The file's breakpoints on their compiled lines, without those on lines changed since the build. */
    private fun placed(
        breakpoints: SourceBreakpoints,
        lines: LineMap,
    ): SourceBreakpoints =
        breakpoints.copy(
            breakpoints =
                breakpoints.breakpoints.mapNotNull { request ->
                    lines.compiled(request.line)?.let { request.copy(line = it) }
                },
        )

    /** The states of the file's breakpoints on lines changed since the build, which the program has no code for. */
    private fun refused(
        breakpoints: SourceBreakpoints,
        lines: LineMap,
    ): List<BreakpointState> =
        breakpoints.breakpoints
            .filter { lines.compiled(it.line) == null }
            .map { BreakpointState(it.id, it.line, false, CHANGED_SINCE_BUILD) }

    /** A stopped frame's scopes. */
    internal fun scopes(
        runId: String,
        frame: Int,
    ): CompletableFuture<List<ScopeState>> = debugged(runId) { scopes(it, frame) }

    /**
     * The variables of a scope, or the fields or elements of a variable, with integers in hexadecimal if [hex]: those
     * [filter] takes, from [start], [count] of them, or all from there for 0.
     */
    internal fun variables(
        runId: String,
        reference: Int,
        hex: Boolean,
        filter: ChildFilter,
        start: Int,
        count: Int,
    ): CompletableFuture<List<VariableState>> = debugged(runId) { variables(it, reference, hex, filter, start, count) }

    /** Evaluates an expression in a frame of a stopped program under the debugger, an integer in hexadecimal if [hex]. */
    internal fun evaluate(
        runId: String,
        frame: Int,
        expression: String,
        hex: Boolean,
    ): CompletableFuture<VariableState> = debugged(runId) { evaluate(it, frame, expression, hex) }

    /** Changes a variable of a stopped program under the debugger to an expression's value, an integer in hexadecimal if [hex]. */
    internal fun setVariable(
        runId: String,
        reference: Int,
        name: String,
        expression: String,
        hex: Boolean,
    ): CompletableFuture<VariableState> = debugged(runId) { setVariable(it, reference, name, expression, hex) }

    /** The names that complete the name typed at [offset] in [text], in a frame of a stopped program under the debugger. */
    internal fun completions(
        runId: String,
        frame: Int,
        text: String,
        offset: Int,
    ): CompletableFuture<CompletionsState> = debugged(runId) { completions(it, frame, text, offset) }

    /**
     * Runs code in a frame of a stopped program under the debugger, for at most [limit], and with [program], the whole
     * program meanwhile, its integer value in hexadecimal if [hex]; cancelling it ends the run.
     */
    internal fun runCode(
        runId: String,
        frame: Int,
        expression: String,
        limit: Duration,
        program: Boolean,
        hex: Boolean,
    ): CompletableFuture<VariableState> = debugged(runId) { runCode(it, frame, expression, limit, program, hex) }

    /** The exception a fiber of a stopped program under the debugger stopped it at; null for another stop. */
    internal fun exceptionInfo(
        runId: String,
        thread: Int,
    ): CompletableFuture<ExceptionInfoState?> = debugged(runId) { exceptionInfo(it, thread) }

    /** Steps a fiber of a stopped program under the debugger, [alone] if so asked; false when that fiber does not wait. */
    internal fun step(
        runId: String,
        thread: Int,
        kind: StepKind,
        alone: Boolean,
    ): CompletableFuture<Boolean> = debugged(runId) { step(it, thread, kind, alone) }

    /** Pauses a running program under the debugger. False when no such run is active. */
    internal fun pause(runId: String): Boolean = commanded(runId, RunDebugClient::pause)

    /** Resumes a stopped program under the debugger. False when no such run is active. */
    internal fun resume(runId: String): Boolean = commanded(runId, RunDebugClient::resume)

    /** Continues one fiber of a stopped program under the debugger alone; false when that fiber does not wait. */
    internal fun resumeAlone(
        runId: String,
        thread: Int,
    ): CompletableFuture<Boolean> = debugged(runId) { resumeAlone(it, thread) }

    /** Asks the debugger of a run, in its execution worker or in the program a debugger attached to. */
    private fun <T> debugged(
        runId: String,
        request: RunDebugClient.(Long) -> CompletableFuture<T>,
    ): CompletableFuture<T> =
        workerIds[runId]?.let { worker.request(it) }
            ?: attached[runId]?.let { it.request(AttachedProgram.RUN) }
            ?: CompletableFuture.failedFuture(IllegalStateException("The program is not running"))

    /** Sends a command to the debugger of a run, as [debugged] does; false when no such run is active. */
    private fun commanded(
        runId: String,
        command: RunDebugClient.(Long) -> Unit,
    ): Boolean {
        workerIds[runId]?.let { worker.command(it) } ?: attached[runId]?.let { it.command(AttachedProgram.RUN) } ?: return false
        return true
    }

    /** Runs still tracked, here and by the execution workers, which is none once every run has ended; for tests. */
    internal fun tracked(): Int =
        synchronized(inputLock) { workerIds.size + waiting.size } + compiled.size + attached.size + taskRuns.size + testRuns.size +
            worker.tracked()

    /** Process IDs of the live execution workers, for tests. */
    internal fun workerPids(): List<Long> = worker.workerPids()

    /**
     * Ends the runs: the programs in the execution workers and the client's tasks stop, and those a debugger attached
     * to run on.
     */
    override fun close() {
        taskRuns.values.snapshot().forEach(TaskRun::stop)
        testRuns.values.snapshot().forEach(TestProcess::stop)
        attached.values.snapshot().forEach(AttachedProgram::close)
        worker.close()
    }

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

    companion object {
        private val logger = LoggerFactory.getLogger(EmbeddedRuns::class.java)

        /** Where attaching waits for the program to answer: a thread of its own for each. */
        private val attaching = Executor { Thread.ofVirtual().name("xtc-attach").start(it) }

        /** The exit of a run stopped while its module was still building. */
        const val STOPPED_BEFORE_START = "Stopped before the program started."

        /** The end of a run stopped while it ran its tests. */
        const val STOPPED = "Stopped."

        /** Where xunit writes a test run's files, under the workspace folder, beside the embedded build's. */
        private const val XUNIT_OUTPUT = "build/xtc/embedded/xunit"

        /** The end of a program that [task] ran under the debugger, once the task was stopped. */
        fun stoppedWith(task: DebugTask) = "Stopped, with its ${task.task}."

        /** How long the exit of a program that a client's task runs waits for the task to end; see [launchWithTask]. */
        private val TASK_END_WAIT = 10.seconds

        /** Where a debugger waits for a program that the client's task runs to connect. */
        private const val LOOPBACK = "127.0.0.1"

        /** Why a client that cannot run [task] cannot debug a run of it. */
        fun noTasks(task: DebugTask) = "This client does not run ${task.task}s"

        /** Why a breakpoint on a line changed since the program was built is not installed. */
        const val CHANGED_SINCE_BUILD =
            "This line changed after the program was built, so the running program has no code for it. Debug again to stop here."
    }
}
