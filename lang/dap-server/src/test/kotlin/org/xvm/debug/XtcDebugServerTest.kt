package org.xvm.debug

import com.google.gson.Gson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.debug.BreakpointEventArguments
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments
import org.eclipse.lsp4j.debug.DisconnectArguments
import org.eclipse.lsp4j.debug.EvaluateArguments
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext
import org.eclipse.lsp4j.debug.ExitedEventArguments
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory
import org.eclipse.lsp4j.debug.PauseArguments
import org.eclipse.lsp4j.debug.ScopesArguments
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.SourceBreakpoint
import org.eclipse.lsp4j.debug.StackTraceArguments
import org.eclipse.lsp4j.debug.StoppedEventArguments
import org.eclipse.lsp4j.debug.TerminateArguments
import org.eclipse.lsp4j.debug.TerminatedEventArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit.SECONDS

/**
 * A debug session launches through the language server's runs, owns the program it launched, installs its
 * breakpoints before the program's first line, and reports stops, threads and stacks from the program. What it
 * cannot do yet, it says so and does not advertise.
 */
class XtcDebugServerTest {
    private val runs = Runs()
    private val client = Client()

    /** Tasks to run after the message being handled has been answered; the test runs them when it chooses. */
    private val afterResponse = CopyOnWriteArrayList<Runnable>()
    private val server = XtcDebugServer(runs, Executor(afterResponse::add)).apply { connect(client) }

    @Test
    fun `only the requests that work are advertised, and initialized follows the answer`() {
        val capabilities = server.initialize(InitializeRequestArguments().apply { adapterID = "xtc" }).get(10, SECONDS)
        // the wire form shows exactly what is advertised
        assertThat(Gson().toJsonTree(capabilities).asJsonObject.keySet())
            .containsExactlyInAnyOrder("supportsConfigurationDoneRequest", "supportsTerminateRequest")
        assertThat(client.events).isEmpty()

        afterResponse.forEach(Runnable::run)
        assertThat(client.events).containsExactly("initialized")
    }

    @Test
    fun `a launch starts the program once the configuration is done, and its exit ends the session`() {
        val launched = server.launch(mutableMapOf("module" to "App", "method" to "greet", "args" to listOf("one", "two words")))
        assertThat(runs.target).isEqualTo(LaunchTarget("App", "App", "greet", listOf("one", "two words")))
        assertThat(runs.ready.toCompletableFuture()).isNotDone()
        assertThat(launched).isNotDone()

        server.configurationDone(ConfigurationDoneArguments()).get(10, SECONDS)
        launched.get(10, SECONDS)
        runs.events.output("hello\n")
        runs.events.exited(3, null)

        assertThat(client.output(OutputEventArgumentsCategory.STDOUT)).isEqualTo("hello\n")
        assertThat(client.output(OutputEventArgumentsCategory.CONSOLE))
            .startsWith("Building and running App with the embedded compiler...\n")
            .endsWith("Process finished with exit code 3\n")
        assertThat(client.events).containsSubsequence("exited 3", "terminated")
    }

    @Test
    fun `breakpoints set before the start are pending, installed with the program, and reported as installed`() {
        server.launch(mutableMapOf("module" to "App"))
        val pending = server.setBreakpoints(breakpoints("/work/App.x", 3, 7)).get(10, SECONDS).breakpoints
        assertThat(pending).allSatisfy {
            assertThat(it.isVerified).isFalse()
            assertThat(it.message).isEqualTo(XtcDebugServer.PENDING)
        }
        assertThat(pending.map { it.line }).containsExactly(3, 7)

        server.configurationDone(null)
        val requested = pending.map { BreakpointRequest(it.id, it.line) }
        assertThat(runs.ready.toCompletableFuture().get(10, SECONDS)).containsExactly(SourceBreakpoints("/work/App.x", requested))
        runs.events.breakpoints(
            "/work/App.x",
            listOf(BreakpointState(pending[0].id, 4, true), BreakpointState(pending[1].id, 7, false, "no code")),
        )

        assertThat(client.breakpoints.map { Triple(it.breakpoint.id, it.breakpoint.line, it.breakpoint.isVerified) })
            .containsExactly(Triple(pending[0].id, 4, true), Triple(pending[1].id, 7, false))
        assertThat(client.breakpoints.map { it.breakpoint.source.path }).containsOnly("/work/App.x")
    }

    @Test
    fun `breakpoints set while the program runs are installed at once, also those changed as it started`() {
        server.configurationDone(null)
        server.setBreakpoints(breakpoints("/work/A.x", 1))
        runs.onReady = { server.setBreakpoints(breakpoints("/work/B.x", 2)) }
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)
        // B.x changed after the program's breakpoints were taken and before it started
        assertThat(runs.installed.map { it.path }).containsExactly("/work/B.x")

        val response = server.setBreakpoints(breakpoints("/work/A.x", 5)).get(10, SECONDS)
        assertThat(response.breakpoints.single().isVerified).isTrue()
        assertThat(runs.installed.map { it.path }).containsExactly("/work/B.x", "/work/A.x")
    }

    @Test
    fun `a stop reports every fiber stopped, with threads and stacks from the program, until continue`() {
        server.configurationDone(null)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)
        runs.events.stopped(StopState("breakpoint", null, 12, listOf(4)))

        val stopped = client.stops.single()
        assertThat(stopped.threadId).isEqualTo(12)
        assertThat(stopped.allThreadsStopped).isTrue()
        assertThat(stopped.hitBreakpointIds).containsExactly(4)
        assertThat(
            server
                .threads()
                .get(10, SECONDS)
                .threads
                .map { it.id to it.name },
        ).containsExactly(12 to "App")

        val frames =
            server
                .stackTrace(
                    StackTraceArguments().apply {
                        threadId = 12
                        startFrame = 0
                        levels = 1
                    },
                ).get(10, SECONDS)
        assertThat(frames.totalFrames).isEqualTo(2)
        assertThat(
            frames.stackFrames.map { Triple(it.name, it.source.path, it.line) },
        ).containsExactly(Triple("App.twice", "/work/App.x", 11))
        assertThat(
            frames.stackFrames
                .single()
                .source.name,
        ).isEqualTo("App.x")
        assertThat(server.scopes(ScopesArguments().apply { frameId = 1 }).get(10, SECONDS).scopes).isEmpty()

        server.continue_(null).get(10, SECONDS)
        assertThat(runs.resumed).isEqualTo(1)
    }

    @Test
    fun `a launch without debugging installs no breakpoints and says so`() {
        server.configurationDone(ConfigurationDoneArguments())
        server.launch(mutableMapOf("module" to "App", "noDebug" to true)).get(10, SECONDS)

        assertThat(runs.ready.toCompletableFuture().get(10, SECONDS)).isNull()
        val response = server.setBreakpoints(breakpoints("/work/App.x", 3)).get(10, SECONDS)
        assertThat(response.breakpoints.single().message).isEqualTo(XtcDebugServer.NOT_DEBUGGING)
        assertThat(server.threads().get(10, SECONDS).threads).isEmpty()
    }

    @Test
    fun `a client that counts lines from 0 gets its lines back as it counts them`() {
        server.initialize(InitializeRequestArguments().apply { linesStartAt1 = false })
        server.configurationDone(null)
        server.setBreakpoints(breakpoints("/work/App.x", 10))
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)

        assertThat(
            runs.ready
                .toCompletableFuture()
                .get()
                .orEmpty()
                .single()
                .breakpoints
                .single()
                .line,
        ).isEqualTo(11)
        val frames = server.stackTrace(StackTraceArguments().apply { threadId = 12 }).get(10, SECONDS)
        assertThat(frames.stackFrames.map { it.line }).containsExactly(10, 4)
    }

    @Test
    fun `a failed build reports its diagnostics and fails the launch, which runs nothing`() {
        runs.result = LaunchResult(null, "/work/App.x:2:5: error: no such type [COMPILER-42]")
        server.configurationDone(ConfigurationDoneArguments())

        assertThatThrownBy { server.launch(mutableMapOf("module" to "App")).get(10, SECONDS) }
            .isInstanceOf(ExecutionException::class.java)
            .rootCause()
            .isInstanceOfSatisfying(ResponseErrorException::class.java) {
                assertThat(it.responseError.message).isEqualTo(XtcDebugServer.BUILD_FAILED)
            }
        assertThat(client.output(OutputEventArgumentsCategory.STDERR)).contains("/work/App.x:2:5: error: no such type")
        assertThat(client.events).doesNotContain("terminated")
        // nothing is running, so a disconnect answers at once
        server.disconnect(DisconnectArguments()).get(1, SECONDS)
    }

    @Test
    fun `launch arguments name the module by URI or name, and take a comma-separated string or an array`() {
        server.launch(mutableMapOf("module" to "App", "moduleUri" to "file:///work/App.x", "args" to "a, b ,,c", "cwd" to "/work"))
        assertThat(runs.target).isEqualTo(LaunchTarget("file:///work/App.x", "App", null, listOf("a", "b", "c"), "/work"))

        assertThatThrownBy { XtcDebugServer(runs, Executor(Runnable::run)).launch(mutableMapOf()).get(10, SECONDS) }
            .rootCause()
            .hasMessage("A launch requires a module")
    }

    @Test
    fun `terminate stops the program, whose exit ends the session`() {
        server.configurationDone(ConfigurationDoneArguments())
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)

        server.terminate(TerminateArguments()).get(10, SECONDS)
        assertThat(runs.stopped).containsExactly(runs.runId)
        assertThat(client.events).doesNotContain("terminated")

        runs.events.exited(null, "Stopped.")
        assertThat(client.output(OutputEventArgumentsCategory.STDERR)).isEqualTo("\nStopped.\n")
        assertThat(client.events).containsSubsequence("exited 1", "terminated")
    }

    @Test
    fun `disconnect stops the program and answers once it has exited`() {
        server.configurationDone(ConfigurationDoneArguments())
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)

        val disconnected = server.disconnect(DisconnectArguments())
        assertThat(runs.stopped).containsExactly(runs.runId)
        assertThat(disconnected).isNotDone()
        runs.events.exited(null, "Stopped.")
        disconnected.get(10, SECONDS)
    }

    @Test
    fun `closing the session's transport stops the program`() {
        server.launch(mutableMapOf("module" to "App"))
        server.close()
        assertThat(runs.stopped).containsExactly(runs.runId)
    }

    @Test
    fun `requests whose arguments are optional work without them, as VS Code sends configurationDone`() {
        server.configurationDone(null).get(10, SECONDS)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)
        server.terminate(null).get(10, SECONDS)
        runs.events.exited(null, "Stopped.")
        server.disconnect(null).get(10, SECONDS)
        assertThat(client.events).containsSubsequence("exited 1", "terminated")
    }

    @Test
    fun `a line typed in the debug console while the program runs is its input, and nothing is evaluated`() {
        server.configurationDone(ConfigurationDoneArguments())
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)

        val answer = server.evaluate(evaluate("Grace", EvaluateArgumentsContext.REPL)).get(10, SECONDS)
        assertThat(answer.result).isEmpty()
        assertThat(runs.inputs).containsExactly("Grace\n")
        assertFailsWith(XtcDebugServer.NO_EVALUATION) { server.evaluate(evaluate("count", EvaluateArgumentsContext.WATCH)) }

        runs.events.exited(0, null)
        assertFailsWith("The program is not running, so it cannot read that line.") {
            server.evaluate(evaluate("late", EvaluateArgumentsContext.REPL))
        }
    }

    @Test
    fun `attach and pause fail with the reason, and a breakpoint needs a file`() {
        assertFailsWith("Attaching is not available; launch the module instead.") { server.attach(mutableMapOf()) }
        assertFailsWith(XtcDebugServer.NO_PAUSE) { server.pause(PauseArguments()) }
        val response =
            server
                .setBreakpoints(
                    SetBreakpointsArguments().apply {
                        source = Source().apply { name = "untitled" }
                        breakpoints = arrayOf(SourceBreakpoint().apply { line = 1 })
                    },
                ).get(10, SECONDS)
        assertThat(response.breakpoints.single().message).isEqualTo(XtcDebugServer.NO_SOURCE)
    }

    private fun breakpoints(
        path: String,
        vararg lines: Int,
    ) = SetBreakpointsArguments().apply {
        source = Source().apply { this.path = path }
        breakpoints = lines.map { line -> SourceBreakpoint().apply { this.line = line } }.toTypedArray()
    }

    private fun evaluate(
        expression: String,
        context: String,
    ) = EvaluateArguments().apply {
        this.expression = expression
        this.context = context
    }

    private fun assertFailsWith(
        message: String,
        request: () -> CompletableFuture<*>,
    ) {
        assertThatThrownBy { request().get(10, SECONDS) }
            .isInstanceOf(ExecutionException::class.java)
            .cause()
            .isInstanceOfSatisfying(ResponseErrorException::class.java) { assertThat(it.responseError.message).isEqualTo(message) }
    }

    /** Stands in for the language server's runs: records what the session asks, and starts once it is ready. */
    private class Runs : DebugRuns {
        lateinit var runId: String
        lateinit var target: LaunchTarget
        lateinit var events: ProgramEvents
        lateinit var ready: CompletionStage<out List<SourceBreakpoints>?>
        var result: LaunchResult? = null
        var onReady: () -> Unit = {}
        val inputs = CopyOnWriteArrayList<String>()
        val stopped = CopyOnWriteArrayList<String>()
        val installed = CopyOnWriteArrayList<SourceBreakpoints>()
        var resumed = 0

        override fun launch(
            runId: String,
            target: LaunchTarget,
            events: ProgramEvents,
            ready: CompletionStage<out List<SourceBreakpoints>?>,
        ): CompletableFuture<LaunchResult> {
            this.runId = runId
            this.target = target
            this.events = events
            this.ready = ready
            return ready.toCompletableFuture().thenApply {
                onReady()
                result ?: LaunchResult(runId, "  ✓ App")
            }
        }

        override fun input(
            runId: String,
            text: String,
        ) = (runId == this.runId).also { if (it) inputs.add(text) }

        override fun stop(runId: String) = (runId == this.runId).also { if (it) stopped.add(runId) }

        override fun setBreakpoints(
            runId: String,
            breakpoints: SourceBreakpoints,
        ): CompletableFuture<List<BreakpointState>> {
            installed.add(breakpoints)
            return CompletableFuture.completedFuture(breakpoints.breakpoints.map { BreakpointState(it.id, it.line, true) })
        }

        override fun threads(runId: String) = CompletableFuture.completedFuture(listOf(ThreadState(12, "App")))

        override fun stackTrace(
            runId: String,
            threadId: Int,
        ) = CompletableFuture.completedFuture(
            listOf(FrameState(1, "App.twice", "/work/App.x", 11), FrameState(2, "App.run", "/work/App.x", 5)),
        )

        override fun resume(runId: String) = true.also { resumed++ }
    }

    /** Records the events the session sends its client. */
    private class Client : IDebugProtocolClient {
        val events = CopyOnWriteArrayList<String>()
        val stops = CopyOnWriteArrayList<StoppedEventArguments>()
        val breakpoints = CopyOnWriteArrayList<BreakpointEventArguments>()
        private val outputs = CopyOnWriteArrayList<OutputEventArguments>()

        fun output(category: String) = outputs.filter { it.category == category }.joinToString("") { it.output }

        override fun initialized() {
            events.add("initialized")
        }

        override fun output(args: OutputEventArguments) {
            outputs.add(args)
        }

        override fun exited(args: ExitedEventArguments) {
            events.add("exited ${args.exitCode}")
        }

        override fun terminated(args: TerminatedEventArguments) {
            events.add("terminated")
        }

        override fun stopped(args: StoppedEventArguments) {
            stops.add(args)
        }

        override fun breakpoint(args: BreakpointEventArguments) {
            breakpoints.add(args)
        }
    }
}
