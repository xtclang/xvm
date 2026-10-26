package org.xvm.debug

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.debug.BreakpointEventArguments
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments
import org.eclipse.lsp4j.debug.DisconnectArguments
import org.eclipse.lsp4j.debug.EvaluateArguments
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext
import org.eclipse.lsp4j.debug.ExceptionBreakMode
import org.eclipse.lsp4j.debug.ExceptionFilterOptions
import org.eclipse.lsp4j.debug.ExceptionInfoArguments
import org.eclipse.lsp4j.debug.ExitedEventArguments
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.InvalidatedEventArguments
import org.eclipse.lsp4j.debug.NextArguments
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory
import org.eclipse.lsp4j.debug.PauseArguments
import org.eclipse.lsp4j.debug.ScopesArguments
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsArguments
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.SourceBreakpoint
import org.eclipse.lsp4j.debug.StackTraceArguments
import org.eclipse.lsp4j.debug.StepInArguments
import org.eclipse.lsp4j.debug.StepOutArguments
import org.eclipse.lsp4j.debug.StoppedEventArguments
import org.eclipse.lsp4j.debug.TerminateArguments
import org.eclipse.lsp4j.debug.TerminatedEventArguments
import org.eclipse.lsp4j.debug.ThreadEventArguments
import org.eclipse.lsp4j.debug.VariablesArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.time.Duration

/**
 * A debug session launches through the language server's runs, owns the program it launched, installs its
 * breakpoints and exception filters before the program's first line, reports stops, threads, stacks and variables
 * from the program, steps and pauses it. What it cannot do yet, it says so and does not advertise.
 */
class XtcDebugServerTest {
    private companion object {
        /** The event that tells a client to read the variables again. */
        const val INVALIDATED = "invalidated variables"
    }

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
            .containsExactlyInAnyOrder(
                "supportsConfigurationDoneRequest",
                "supportsTerminateRequest",
                "supportsEvaluateForHovers",
                "supportsConditionalBreakpoints",
                "supportsHitConditionalBreakpoints",
                "supportsLogPoints",
                "supportsExceptionInfoRequest",
                "supportsExceptionFilterOptions",
                "supportsCancelRequest",
                "exceptionBreakpointFilters",
            )
        // uncaught exceptions stop a program unless the user turns that off; every thrown one only when asked
        assertThat(capabilities.exceptionBreakpointFilters.map { it.filter to it.default_ })
            .containsExactly(XtcDebugServer.UNCAUGHT_EXCEPTIONS to true, XtcDebugServer.ALL_EXCEPTIONS to false)
        // each takes exception classes as its condition
        assertThat(capabilities.exceptionBreakpointFilters.map { it.supportsCondition to it.conditionDescription })
            .containsOnly(true to XtcDebugServer.EXCEPTION_TYPES)
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
        assertThat(
            runs.ready
                .toCompletableFuture()
                .get(10, SECONDS)
                ?.breakpoints,
        ).containsExactly(SourceBreakpoints("/work/App.x", requested))
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

        server.continue_(null).get(10, SECONDS)
        assertThat(runs.resumed).isEqualTo(1)
    }

    @Test
    fun `a stopped frame's locals and the fields of its objects come from the program, a part at a time when asked`() {
        server.configurationDone(null)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)
        runs.events.stopped(StopState("breakpoint", null, 12, listOf(4)))

        val scope =
            server
                .scopes(ScopesArguments().apply { frameId = 1 })
                .get(10, SECONDS)
                .scopes
                .single()
        assertThat(scope.name).isEqualTo("Locals")
        assertThat(scope.presentationHint).isEqualTo("locals")
        assertThat(scope.isExpensive).isFalse()
        assertThat(server.scopes(ScopesArguments().apply { frameId = 2 }).get(10, SECONDS).scopes).isEmpty()

        val locals = server.variables(VariablesArguments().apply { variablesReference = scope.variablesReference }).get(10, SECONDS)
        assertThat(locals.variables.map { listOf(it.name, it.value, it.type, it.variablesReference.toString()) })
            .containsExactly(
                listOf("n", "21", "Int", "0"),
                listOf("point", "Vars:Point", "Vars:Point", "8"),
                listOf("unset", "<unassigned>", null, "0"),
            )
        val fields =
            server
                .variables(
                    VariablesArguments().apply {
                        variablesReference = 8
                        start = 1
                        count = 1
                    },
                ).get(10, SECONDS)
        assertThat(fields.variables.map { it.name to it.value }).containsExactly("y" to "4")
    }

    @Test
    fun `an object's computed properties and its toString() are lazy variables, whose reference runs their code`() {
        server.configurationDone(null)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)
        runs.events.stopped(StopState("breakpoint", null, 12, listOf(4)))

        val fields = server.variables(VariablesArguments().apply { variablesReference = 8 }).get(10, SECONDS).variables
        assertThat(fields.map { it.name to (it.presentationHint?.lazy == true) })
            .containsExactly("x" to false, "y" to false, "sum" to true, "toString()" to true)
        val sum = fields.single { it.name == "sum" }
        assertThat(sum.value).isEqualTo("(...)")
        assertThat(sum.type).isNull()

        val value = server.variables(VariablesArguments().apply { variablesReference = sum.variablesReference }).get(10, SECONDS)
        assertThat(value.variables.map { listOf(it.name, it.value, it.type) }).containsExactly(listOf("sum", "7", "Int"))
        assertThat(value.variables.single().presentationHint).isNull()
    }

    @Test
    fun `next, stepIn and stepOut step the stopped fiber, and a fiber that does not wait cannot be stepped`() {
        server.configurationDone(null)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)
        runs.events.stopped(StopState("breakpoint", null, 12, listOf(4)))

        server.next(NextArguments().apply { threadId = 12 }).get(10, SECONDS)
        server.stepIn(StepInArguments().apply { threadId = 12 }).get(10, SECONDS)
        server.stepOut(StepOutArguments().apply { threadId = 12 }).get(10, SECONDS)
        assertThat(runs.steps).containsExactly(12 to StepKind.OVER, 12 to StepKind.IN, 12 to StepKind.OUT)

        assertFailsWith(XtcDebugServer.NOT_STOPPED) { server.next(NextArguments().apply { threadId = 13 }) }
    }

    @Test
    fun `exception filters set before the start apply from the first line, later ones at once, and an exception stop has its text`() {
        server.setExceptionBreakpoints(SetExceptionBreakpointsArguments().apply { filters = arrayOf(XtcDebugServer.UNCAUGHT_EXCEPTIONS) })
        server.configurationDone(null)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)
        assertThat(
            runs.ready
                .toCompletableFuture()
                .get(10, SECONDS)
                ?.exceptions,
        ).isEqualTo(ExceptionStops(uncaught = true))
        assertThat(runs.exceptionStops).isEmpty()

        server.setExceptionBreakpoints(
            SetExceptionBreakpointsArguments().apply {
                filters =
                    arrayOf(
                        XtcDebugServer.ALL_EXCEPTIONS,
                        XtcDebugServer.UNCAUGHT_EXCEPTIONS,
                    )
            },
        )
        assertThat(runs.exceptionStops).containsExactly(ExceptionStops(all = true, uncaught = true))

        runs.events.stopped(StopState("exception", "IllegalArgument: bad", 12, emptyList()))
        assertThat(client.stops.single().reason).isEqualTo("exception")
        assertThat(client.stops.single().text).isEqualTo("IllegalArgument: bad")
    }

    @Test
    fun `a breakpoint's condition, hit condition and log message reach the program, and a logpoint's message is console output`() {
        server.configurationDone(null)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)

        server
            .setBreakpoints(
                SetBreakpointsArguments().apply {
                    source = Source().apply { path = "/work/A.x" }
                    breakpoints =
                        arrayOf(
                            SourceBreakpoint().apply {
                                line = 7
                                condition = "even"
                                hitCondition = "2"
                            },
                            SourceBreakpoint().apply {
                                line = 9
                                logMessage = "sum is {sum}"
                            },
                        )
                },
            ).get(10, SECONDS)
        assertThat(
            runs.installed
                .single()
                .breakpoints
                .map { listOf(it.line, it.condition, it.hitCondition, it.logMessage) },
        ).containsExactly(listOf(7, "even", "2", null), listOf(9, null, null, "sum is {sum}"))

        runs.events.logged("sum is 10")
        assertThat(client.output(OutputEventArgumentsCategory.CONSOLE)).endsWith("sum is 10\n")
    }

    @Test
    fun `an exception filter's condition names the exception classes it stops at, and one that is not names leaves it off`() {
        server.configurationDone(null)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)

        val answer =
            server
                .setExceptionBreakpoints(
                    SetExceptionBreakpointsArguments().apply {
                        filters = arrayOf(XtcDebugServer.UNCAUGHT_EXCEPTIONS)
                        filterOptions =
                            arrayOf(
                                filterOption(XtcDebugServer.ALL_EXCEPTIONS, " IllegalArgument, OutOfBounds "),
                                filterOption(XtcDebugServer.UNCAUGHT_EXCEPTIONS, "Exception"),
                            )
                    },
                ).get(10, SECONDS)
        // the plain filter wins over the same filter with a condition
        assertThat(runs.exceptionStops.last())
            .isEqualTo(ExceptionStops(all = true, uncaught = true, allTypes = listOf("IllegalArgument", "OutOfBounds")))
        assertThat(answer.breakpoints.map { it.isVerified }).containsExactly(true, true, true)

        val refused =
            server
                .setExceptionBreakpoints(
                    SetExceptionBreakpointsArguments().apply {
                        filterOptions = arrayOf(filterOption(XtcDebugServer.ALL_EXCEPTIONS, "x > 1"))
                    },
                ).get(10, SECONDS)
        assertThat(runs.exceptionStops.last()).isEqualTo(ExceptionStops())
        assertThat(refused.breakpoints.single().isVerified).isFalse()
        assertThat(refused.breakpoints.single().message).isEqualTo("\"x > 1\" is not exception classes. ${XtcDebugServer.EXCEPTION_TYPES}")
    }

    @Test
    fun `exceptionInfo tells of the exception a fiber stopped the program at, and fails for a fiber that stopped at none`() {
        server.configurationDone(null)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)
        runs.events.stopped(StopState("exception", "IllegalArgument: bad 5", 12, emptyList()))

        val info = server.exceptionInfo(ExceptionInfoArguments().apply { threadId = 12 }).get(10, SECONDS)
        assertThat(
            listOf(info.exceptionId, info.description, info.breakMode),
        ).containsExactly("IllegalArgument", "bad 5", ExceptionBreakMode.UNHANDLED)
        assertThat(listOf(info.details.typeName, info.details.message, info.details.stackTrace))
            .containsExactly("IllegalArgument", "bad 5", "at check() (Throws.x:12)")
        assertFailsWith(XtcDebugServer.NO_EXCEPTION) { server.exceptionInfo(ExceptionInfoArguments().apply { threadId = 13 }) }
    }

    @Test
    fun `pause pauses the debugged program, which shows a thread while it runs, and a launch can stop it on entry`() {
        server.configurationDone(null)
        runs.fibers = emptyList()
        server.launch(mutableMapOf("module" to "App", "stopOnEntry" to true)).get(10, SECONDS)
        assertThat(
            runs.ready
                .toCompletableFuture()
                .get(10, SECONDS)
                ?.stopOnEntry,
        ).isTrue()

        // an editor pauses a thread: while no fiber is stopped, the program shows one, announced as it starts
        assertThat(client.events).contains("thread")
        assertThat(
            server
                .threads()
                .get(10, SECONDS)
                .threads
                .map { it.id to it.name },
        ).containsExactly(XtcDebugServer.RUNNING_THREAD to "App")

        server.pause(PauseArguments().apply { threadId = 12 }).get(10, SECONDS)
        assertThat(runs.paused).isEqualTo(1)
    }

    @Test
    fun `a launch without debugging installs no breakpoints and says so`() {
        server.configurationDone(ConfigurationDoneArguments())
        server.launch(mutableMapOf("module" to "App", "noDebug" to true)).get(10, SECONDS)

        assertThat(runs.ready.toCompletableFuture().get(10, SECONDS)).isNull()
        val response = server.setBreakpoints(breakpoints("/work/App.x", 3)).get(10, SECONDS)
        assertThat(response.breakpoints.single().message).isEqualTo(XtcDebugServer.NOT_DEBUGGING)
        assertThat(server.threads().get(10, SECONDS).threads).isEmpty()
        assertThat(server.scopes(ScopesArguments().apply { frameId = 1 }).get(10, SECONDS).scopes).isEmpty()
        assertThat(server.variables(VariablesArguments().apply { variablesReference = 7 }).get(10, SECONDS).variables).isEmpty()
        assertFailsWith(XtcDebugServer.NOT_STOPPED) { server.next(NextArguments().apply { threadId = 12 }) }
        assertThat(runs.steps).isEmpty()
        assertFailsWith(XtcDebugServer.NOT_PAUSABLE) { server.pause(PauseArguments()) }
        assertThat(runs.ready.toCompletableFuture().get(10, SECONDS)).isNull()
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
                .get()!!
                .breakpoints
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
    fun `a line typed in the debug console while the program runs is its input, and nothing is evaluated without a frame`() {
        server.configurationDone(ConfigurationDoneArguments())
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)

        val answer = server.evaluate(evaluate("Grace", EvaluateArgumentsContext.REPL)).get(10, SECONDS)
        assertThat(answer.result).isEmpty()
        assertThat(runs.inputs).containsExactly("Grace\n")
        assertFailsWith(XtcDebugServer.NO_EVALUATION) { server.evaluate(evaluate("count", EvaluateArgumentsContext.WATCH)) }
        assertThat(runs.evaluated).isEmpty()

        runs.events.exited(0, null)
        assertFailsWith("The program is not running, so it cannot read that line.") {
            server.evaluate(evaluate("late", EvaluateArgumentsContext.REPL))
        }
    }

    @Test
    fun `a watch, a hover or a console line in a stopped frame evaluates a path, and what cannot be evaluated says why`() {
        server.configurationDone(null)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)
        runs.events.stopped(StopState("breakpoint", null, 12, listOf(4)))

        for (context in listOf(EvaluateArgumentsContext.WATCH, EvaluateArgumentsContext.HOVER, EvaluateArgumentsContext.REPL)) {
            val answer = server.evaluate(evaluate("point.x", context).apply { frameId = 1 }).get(10, SECONDS)
            assertThat(listOf(answer.result, answer.type, answer.variablesReference.toString())).containsExactly("3", "Int", "0")
        }
        assertThat(runs.evaluated).containsOnly(1 to "point.x")
        assertThat(runs.inputs).isEmpty()

        val point = server.evaluate(evaluate("point", EvaluateArgumentsContext.WATCH).apply { frameId = 1 }).get(10, SECONDS)
        assertThat(point.variablesReference).isEqualTo(8)
        assertFailsWith("There is no variable \"nope\" in this frame.") {
            server.evaluate(evaluate("nope", EvaluateArgumentsContext.WATCH).apply { frameId = 1 })
        }
    }

    @Test
    fun `a console line that a read refuses runs as code, for at most the console's limit, and then the variables are invalidated`() {
        server.initialize(
            InitializeRequestArguments().apply {
                adapterID = "xtc"
                supportsInvalidatedEvent = true
            },
        )
        server.configurationDone(null)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)
        runs.events.stopped(StopState("breakpoint", null, 12, listOf(4)))

        val answer = server.evaluate(evaluate("box.bump(n)", EvaluateArgumentsContext.REPL).apply { frameId = 1 })
        assertThat(runs.ran).containsExactly(Ran(1, "box.bump(n)", XtcDebugServer.CONSOLE_LIMIT, program = false))
        assertThat(answer).isNotDone()
        assertThat(client.events).doesNotContain(INVALIDATED)
        runs.runAnswers.single().complete(VariableState("box.bump(n)", "5", "Int", 0))
        assertThat(answer.get(10, SECONDS).result).isEqualTo("5")
        assertThat(client.events).contains(INVALIDATED)

        // a watch or a hover that a read refuses fails with the reason, and runs nothing
        for (context in listOf(EvaluateArgumentsContext.WATCH, EvaluateArgumentsContext.HOVER)) {
            assertFailsWith("There is no variable \"box.bump(n)\" in this frame.") {
                server.evaluate(evaluate("box.bump(n)", context).apply { frameId = 1 })
            }
        }
        assertThat(runs.ran).hasSize(1)
    }

    @Test
    fun `a launch with evaluationRunsProgram lets the whole program run while console code does, and then everything is invalidated`() {
        server.initialize(
            InitializeRequestArguments().apply {
                adapterID = "xtc"
                supportsInvalidatedEvent = true
            },
        )
        server.configurationDone(null)
        server.launch(mutableMapOf("module" to "App", "evaluationRunsProgram" to true)).get(10, SECONDS)
        runs.events.stopped(StopState("breakpoint", null, 12, listOf(4)))

        val answer = server.evaluate(evaluate("worker.finished()", EvaluateArgumentsContext.REPL).apply { frameId = 1 })
        assertThat(runs.ran).containsExactly(Ran(1, "worker.finished()", XtcDebugServer.CONSOLE_LIMIT, program = true))
        runs.runAnswers.single().complete(VariableState("worker.finished()", "300000", "Int", 0))
        assertThat(answer.get(10, SECONDS).result).isEqualTo("300000")
        // the program's other fibers moved, so the threads and stacks are read again too
        assertThat(client.events).contains("invalidated all").doesNotContain(INVALIDATED)
    }

    @Test
    fun `what code run from the console throws, or why it does not compile, is its answer, and a client without invalidated gets none`() {
        server.initialize(InitializeRequestArguments().apply { adapterID = "xtc" })
        server.configurationDone(null)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)
        runs.events.stopped(StopState("breakpoint", null, 12, listOf(4)))

        val answer = server.evaluate(evaluate("nope()", EvaluateArgumentsContext.REPL).apply { frameId = 1 })
        runs.runAnswers.single().completeExceptionally(IllegalStateException("Could not find a matching method or function \"nope\""))
        assertFailsWith("Could not find a matching method or function \"nope\"") { answer }
        assertThat(client.events).doesNotContain(INVALIDATED)
    }

    @Test
    fun `cancel ends a console line's run, named by its request's seq, which then answers cancelled, also before its code starts`() {
        val messages = CopyOnWriteArrayList<JsonObject>()
        val endpoint = DebugMessageEndpoint(server) { messages.add(JsonParser.parseString(it).asJsonObject) }
        endpoint.receive(request(1, "initialize", """{"adapterID": "xtc"}"""))
        server.configurationDone(null)
        server.launch(mutableMapOf("module" to "App")).get(10, SECONDS)
        runs.events.stopped(StopState("breakpoint", null, 12, listOf(4)))

        endpoint.receive(request(7, "evaluate", """{"expression": "spin()", "frameId": 1, "context": "repl"}"""))
        val run = runs.runAnswers.single()
        assertThat(run).isNotDone()
        endpoint.receive(request(8, "cancel", """{"requestId": 7}"""))
        assertThat(run).isCancelled()
        assertThat(answer(messages, 7)["message"].asString).isEqualTo(XtcDebugServer.CANCELLED)
        assertThat(answer(messages, 8)["success"].asBoolean).isTrue()

        // cancelled while the line is still read as a path, its code never runs
        endpoint.receive(request(10, "evaluate", """{"expression": "slow()", "frameId": 1, "context": "repl"}"""))
        endpoint.receive(request(11, "cancel", """{"requestId": 10}"""))
        runs.slowRead.completeExceptionally(IllegalStateException("slow() is not evaluated"))
        assertThat(answer(messages, 10)["message"].asString).isEqualTo(XtcDebugServer.CANCELLED)
        assertThat(runs.ran.map { it.expression }).containsExactly("spin()")

        // a cancel of a request that is answered, or of none, does nothing
        endpoint.receive(request(12, "cancel", """{"requestId": 1}"""))
        assertThat(answer(messages, 12)["success"].asBoolean).isTrue()
    }

    @Test
    fun `attach and a pause before the launch fail with the reason, and a breakpoint needs a file`() {
        assertFailsWith("Attaching is not available; launch the module instead.") { server.attach(mutableMapOf()) }
        assertFailsWith(XtcDebugServer.NOT_PAUSABLE) { server.pause(PauseArguments()) }
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

    private fun filterOption(
        filter: String,
        types: String,
    ) = ExceptionFilterOptions().apply {
        filterId = filter
        condition = types
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

    /** A DAP request as a client sends it. */
    private fun request(
        seq: Int,
        command: String,
        arguments: String = "{}",
    ) = """{"seq": $seq, "type": "request", "command": "$command", "arguments": $arguments}"""

    /** The answer to the request [seq], once the endpoint has sent it. */
    private fun answer(
        messages: List<JsonObject>,
        seq: Int,
    ): JsonObject {
        val deadline = System.nanoTime() + SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            messages.firstOrNull { it["type"]?.asString == "response" && it["request_seq"]?.asInt == seq }?.let { return it }
            Thread.sleep(10)
        }
        throw AssertionError("no answer to request $seq: $messages")
    }

    /** A run of code that the session asked for: in which frame, the code, its time limit, and whether the program runs too. */
    private data class Ran(
        val frame: Int,
        val expression: String,
        val limit: Duration,
        val program: Boolean,
    )

    /** Stands in for the language server's runs: records what the session asks, and starts once it is ready. */
    private class Runs : DebugRuns {
        lateinit var runId: String
        lateinit var target: LaunchTarget
        lateinit var events: ProgramEvents
        lateinit var ready: CompletionStage<out DebugSetup?>
        var result: LaunchResult? = null
        var onReady: () -> Unit = {}
        val inputs = CopyOnWriteArrayList<String>()
        val stopped = CopyOnWriteArrayList<String>()
        val installed = CopyOnWriteArrayList<SourceBreakpoints>()
        val steps = CopyOnWriteArrayList<Pair<Int, StepKind>>()
        val exceptionStops = CopyOnWriteArrayList<ExceptionStops>()
        val evaluated = CopyOnWriteArrayList<Pair<Int, String>>()

        /** Each run asked for, and its answer, which the test completes. */
        val ran = CopyOnWriteArrayList<Ran>()
        val runAnswers = CopyOnWriteArrayList<CompletableFuture<VariableState>>()

        /** The read of `slow()`, which the test completes. */
        val slowRead = CompletableFuture<VariableState>()
        var resumed = 0
        var paused = 0

        override fun launch(
            runId: String,
            target: LaunchTarget,
            events: ProgramEvents,
            ready: CompletionStage<out DebugSetup?>,
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

        override fun evaluate(
            runId: String,
            frameId: Int,
            expression: String,
        ): CompletableFuture<VariableState> {
            evaluated.add(frameId to expression)
            return when (expression) {
                "point.x" -> CompletableFuture.completedFuture(VariableState("point.x", "3", "Int", 0))
                "point" -> CompletableFuture.completedFuture(VariableState("point", "Vars:Point", "Vars:Point", 8))
                "slow()" -> slowRead
                else -> CompletableFuture.failedFuture(IllegalStateException("There is no variable \"$expression\" in this frame."))
            }
        }

        override fun run(
            runId: String,
            frameId: Int,
            expression: String,
            limit: Duration,
            program: Boolean,
        ): CompletableFuture<VariableState> {
            ran.add(Ran(frameId, expression, limit, program))
            return CompletableFuture<VariableState>().also(runAnswers::add)
        }

        override fun setExceptionStops(
            runId: String,
            stops: ExceptionStops,
        ) = (runId == this.runId).also { if (it) exceptionStops.add(stops) }

        override fun exceptionInfo(
            runId: String,
            threadId: Int,
        ): CompletableFuture<ExceptionInfoState?> =
            CompletableFuture.completedFuture(
                ExceptionInfoState("IllegalArgument", "bad 5", "at check() (Throws.x:12)", uncaught = true).takeIf { threadId == 12 },
            )

        override fun setBreakpoints(
            runId: String,
            breakpoints: SourceBreakpoints,
        ): CompletableFuture<List<BreakpointState>> {
            installed.add(breakpoints)
            return CompletableFuture.completedFuture(breakpoints.breakpoints.map { BreakpointState(it.id, it.line, true) })
        }

        var fibers = listOf(ThreadState(12, "App"))

        override fun threads(runId: String) = CompletableFuture.completedFuture(fibers)

        override fun stackTrace(
            runId: String,
            threadId: Int,
        ) = CompletableFuture.completedFuture(
            listOf(FrameState(1, "App.twice", "/work/App.x", 11), FrameState(2, "App.run", "/work/App.x", 5)),
        )

        override fun scopes(
            runId: String,
            frameId: Int,
        ) = CompletableFuture.completedFuture(if (frameId == 1) listOf(ScopeState("Locals", 7)) else emptyList())

        override fun variables(
            runId: String,
            reference: Int,
        ) = CompletableFuture.completedFuture(
            when (reference) {
                7 ->
                    listOf(
                        VariableState("n", "21", "Int", 0),
                        VariableState("point", "Vars:Point", "Vars:Point", 8),
                        VariableState("unset", "<unassigned>", "", 0),
                    )

                8 ->
                    listOf(
                        VariableState("x", "3", "Int", 0),
                        VariableState("y", "4", "Int", 0),
                        VariableState("sum", "(...)", "", 9, lazy = true),
                        VariableState("toString()", "(...)", "", 10, lazy = true),
                    )

                9 -> listOf(VariableState("sum", "7", "Int", 0))

                else -> emptyList()
            },
        )

        override fun step(
            runId: String,
            threadId: Int,
            kind: StepKind,
        ): CompletableFuture<Boolean> = CompletableFuture.completedFuture((threadId == 12).also { if (it) steps.add(threadId to kind) })

        override fun pause(runId: String) = (runId == this.runId).also { if (it) paused++ }

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

        override fun thread(args: ThreadEventArguments) {
            events.add("thread")
        }

        override fun invalidated(args: InvalidatedEventArguments) {
            events.add("invalidated ${args.areas.joinToString()}")
        }
    }
}
