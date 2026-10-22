package org.xvm.debug

import com.google.gson.Gson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments
import org.eclipse.lsp4j.debug.DisconnectArguments
import org.eclipse.lsp4j.debug.EvaluateArguments
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext
import org.eclipse.lsp4j.debug.ExitedEventArguments
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory
import org.eclipse.lsp4j.debug.PauseArguments
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.SourceBreakpoint
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
 * A debug session launches through the language server's runs and owns the program it launched. Without a runtime
 * debugger it must say so: nothing it reports may suggest that debugging works.
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
    fun `breakpoints are reported unverified with the reason`() {
        val response =
            server
                .setBreakpoints(
                    SetBreakpointsArguments().apply {
                        source = Source().apply { path = "/work/App.x" }
                        breakpoints = arrayOf(SourceBreakpoint().apply { line = 3 }, SourceBreakpoint().apply { line = 7 })
                    },
                ).get(10, SECONDS)
        assertThat(response.breakpoints.map { it.line }).containsExactly(3, 7)
        assertThat(response.breakpoints).allSatisfy {
            assertThat(it.isVerified).isFalse()
            assertThat(it.message).isEqualTo(XtcDebugServer.NOT_AVAILABLE)
        }
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
            .startsWith("Building and running App with the embedded compiler...\n${XtcDebugServer.NOT_AVAILABLE}\n")
            .endsWith("Process finished with exit code 3\n")
        assertThat(client.events).containsSubsequence("exited 3", "terminated")
    }

    @Test
    fun `a launch without debugging does not mention breakpoints`() {
        server.configurationDone(ConfigurationDoneArguments())
        server.launch(mutableMapOf("module" to "App", "noDebug" to true)).get(10, SECONDS)

        assertThat(client.output(OutputEventArgumentsCategory.CONSOLE)).doesNotContain(XtcDebugServer.NOT_AVAILABLE)
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
    fun `requests whose arguments are optional work without them, as VS Code sends configurationDone`() {
        server.configurationDone(null).get(10, SECONDS)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)
        server.terminate(null).get(10, SECONDS)
        runs.events.exited(null, "Stopped.")
        server.disconnect(null).get(10, SECONDS)
        assertThat(client.events).containsSubsequence("exited 1", "terminated")
    }

    @Test
    fun `attach and pause fail with the reason`() {
        assertFailsWith("Attaching is not available; launch the module instead.") { server.attach(mutableMapOf()) }
        assertFailsWith(XtcDebugServer.NOT_AVAILABLE) { server.pause(PauseArguments()) }
        assertThat(server.threads().get(10, SECONDS).threads).isEmpty()
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
        lateinit var ready: CompletionStage<*>
        var result: LaunchResult? = null
        val inputs = CopyOnWriteArrayList<String>()
        val stopped = CopyOnWriteArrayList<String>()

        override fun launch(
            runId: String,
            target: LaunchTarget,
            events: ProgramEvents,
            ready: CompletionStage<*>,
        ): CompletableFuture<LaunchResult> {
            this.runId = runId
            this.target = target
            this.events = events
            this.ready = ready
            return ready.toCompletableFuture().thenApply { result ?: LaunchResult(runId, "  ✓ App") }
        }

        override fun input(
            runId: String,
            text: String,
        ) = (runId == this.runId).also { if (it) inputs.add(text) }

        override fun stop(runId: String) = (runId == this.runId).also { if (it) stopped.add(runId) }
    }

    /** Records the events the session sends its client. */
    private class Client : IDebugProtocolClient {
        val events = CopyOnWriteArrayList<String>()
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
    }
}
