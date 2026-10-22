package org.xvm.lsp.run

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.DirRepository
import org.xvm.asm.ErrorList
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkBuild
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.server.DebugMessageParams
import org.xvm.lsp.server.DebugSessionParams
import org.xvm.lsp.server.DebugSessions
import org.xvm.lsp.server.EmbeddedRuns
import org.xvm.lsp.server.RunExitedParams
import org.xvm.lsp.server.RunInputParams
import org.xvm.lsp.server.RunOutputParams
import org.xvm.lsp.server.RunParams
import org.xvm.lsp.server.StopRunParams
import org.xvm.lsp.server.XtcLanguageClient
import org.xvm.lsp.server.XtcLanguageServer
import org.xvm.lsp.server.launchStdio
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

/**
 * Embedded build to disk through the compiler queue, and runs in the execution worker JVM. The worker
 * tests start a real worker process from the test classpath, as the language server does from its jar.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmbeddedExecutionTest {
    @TempDir lateinit var directory: Path

    private lateinit var lib: File
    private val worker = RunWorkerProcess()

    @BeforeAll
    fun compileProgramsOnce() {
        CompilerTestSupport.configure()
        lib = Files.createTempDirectory("xtc-run-test").toFile()
        listOf(
            """
            module Echo {
                void run(String[] args) {
                    @Inject Console console;
                    console.print($"args={args.size}:{args[0]}|{args[1]}");
                }
            }
            """,
            "module Exit { Int run() = 42; }",
            "module Greeter { void greet() { @Inject Console console; console.print(\"greetings\"); } }",
            "module Thrower { void run() { throw new IllegalState(\"boom\"); } }",
            """
            module Counter {
                static service Calls {
                    Int count = 0;
                    Int next() = ++count;
                }

                void run() {
                    @Inject Console console;
                    console.print($"calls={Calls.next()},{Calls.next()}");
                }
            }
            """,
            """
            module Chatty {
                void run() {
                    @Inject Console console;
                    for (Int line = 0; line < 5000; ++line) {
                        console.print($"line {line}");
                    }
                }
            }
            """,
            FOREVER,
            ASK,
            """
            module Breaker {
                void run() {
                    @Inject Console console;
                    console.print("before");
                    assert:debug;
                    console.print("after");
                }
            }
            """,
            """
            module Waiter {
                void run() {
                    @Inject Console console;
                    console.print("waiting");
                    console.readLine();
                }
            }
            """,
        ).forEach { source ->
            val errors = ErrorList()
            val module = EmbeddingSupport.instance().compile(source.trimIndent(), null, errors)
            assertThat(module).describedAs(errors.errors.toString()).isNotNull()
            DirRepository(lib, false).storeModule(module)
        }
    }

    @AfterAll
    fun closeWorker() {
        worker.close()
        lib.deleteRecursively()
    }

    @BeforeEach
    fun canonicalDirectory() {
        directory = directory.toRealPath()
    }

    @Test
    fun `arguments reach the entry method unchanged, including spaces and non-ASCII text`() {
        val run = run("Echo", args = listOf("hello world", "ünïcødé ✓"))
        assertThat(run.code).isEqualTo(0)
        assertThat(run.error).isNull()
        assertThat(run.output).contains("args=2:hello world|ünïcødé ✓")
    }

    @Test
    fun `the entry method's Int result is the exit code`() {
        assertThat(run("Exit").code).isEqualTo(42)
    }

    @Test
    fun `a named entry method other than run can be invoked`() {
        val run = run("Greeter", method = "greet")
        assertThat(run.error).isNull()
        assertThat(run.output).contains("greetings")
    }

    @Test
    fun `an unhandled exception ends the run with an error`() {
        val run = run("Thrower")
        assertThat(run.code).isNull()
        assertThat(run.error).contains("boom")
    }

    @Test
    fun `a program printing in a loop sends all its output, in order, in far fewer events than prints`() {
        val events = AtomicInteger()
        val run = run("Chatty") { events.incrementAndGet() }
        assertThat(run.error).isNull()
        assertThat(run.output.lines().filter { it.isNotEmpty() }).isEqualTo((0 until 5000).map { "line $it" })
        println("5000 prints arrived in ${events.get()} output events")
        assertThat(events.get()).isLessThan(1000)
    }

    @Test
    fun `after a run the worker reports the heap it keeps and its threads, within the limits`() {
        assertThat(run("Exit").code).isEqualTo(42)
        soon(30).until { worker.usage().isNotEmpty() }
        val limits = RunWorkerProcess.Limits()
        assertThat(worker.usage().values).allSatisfy { usage ->
            println("execution worker after a run: $usage")
            assertThat(usage.heapUsed).isPositive().isLessThan(limits.heap)
            assertThat(usage.heapMax).isGreaterThanOrEqualTo(usage.heapUsed)
            assertThat(usage.threads).isPositive().isLessThan(limits.threads)
        }
    }

    @Test
    fun `repeated runs reuse one worker and leave no run, console or pool thread behind`() {
        RunWorkerProcess().use { workers ->
            val reports = mutableListOf<RunWorkerProcess.Usage>()
            val pids = mutableSetOf<Long>()

            fun once(
                module: String,
                args: List<String> = emptyList(),
                act: (Long, StringBuffer) -> Unit = { _, _ -> },
            ): Outcome {
                val output = StringBuffer()
                val exited = CompletableFuture<Pair<Long?, String?>>()
                val id =
                    workers.start(
                        RunWorkerProcess.Request(listOf(lib.path), module, "run", args, directory.toString()),
                        object : RunWorkerProcess.Listener {
                            override fun output(text: String) {
                                output.append(text)
                            }

                            override fun exited(
                                code: Long?,
                                error: String?,
                            ) {
                                exited.complete(code to error)
                            }
                        },
                    )
                act(id, output)
                val (code, error) = exited.get(60, SECONDS)
                soon(30).until { workers.usage().values.any { it.run == id } }
                reports += workers.usage().values.single { it.run == id }
                pids += workers.workerPids()
                return Outcome(output.toString(), code, error)
            }

            // a normal exit, arguments, an exception, console input and a stopped program, five times over
            repeat(5) {
                assertThat(once("Echo", listOf("a b", "ü")).output).contains("args=2:a b|ü")
                assertThat(once("Exit").code).isEqualTo(42)
                assertThat(once("Thrower").error).contains("boom")
                val asked =
                    once("Ask") { id, output ->
                        soon(60).until { "Name? " in output }
                        workers.input(id, "Ada\n")
                        workers.endInput(id)
                    }
                assertThat(asked.output).contains("Hello, Ada!")
                once("Waiter") { id, output ->
                    soon(60).until { "waiting" in output }
                    workers.stop(id)
                }
            }
            println("repeated runs: first ${reports.first()}, last ${reports.last()}")

            assertThat(pids).describedAs("one worker served every run").hasSize(1)
            assertThat(reports).hasSize(25).allSatisfy {
                assertThat(it.consoles).isZero()
                assertThat(it.runs).isZero()
                assertThat(it.poolThreads).isPositive().isEqualTo(reports.first().poolThreads)
            }
            // after the first round has loaded what runs need, more runs retain nothing that adds up
            assertThat(reports.last().heapUsed - reports[4].heapUsed).isLessThan(64L shl 20)
            assertThat(workers.tracked()).isZero()
        }
    }

    @Test
    fun `each run starts with fresh program state, though one worker serves both`() {
        RunWorkerProcess().use { workers ->
            val pids = mutableSetOf<Long>()
            repeat(2) { round ->
                // the counter keeps its count within a run; a run in the container of the run before it would go on
                // counting from that run's calls
                assertThat(run("Counter", workers = workers).output).describedAs("run ${round + 1}").contains("calls=1,2")
                pids += workers.workerPids()
            }
            assertThat(pids).describedAs("one worker served both runs").hasSize(1)
        }
    }

    @Test
    fun `a worker exits when the language server process dies`() {
        val host =
            ProcessBuilder(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElse("java"),
                "-cp",
                System.getProperty("java.class.path"),
                RunWorkerHost::class.java.name,
                lib.path,
                "Forever",
                directory.toString(),
            ).redirectErrorStream(true)
                .start()
        try {
            val pid =
                CompletableFuture
                    .supplyAsync {
                        host
                            .inputReader()
                            .lineSequence()
                            .first { it.startsWith("worker-pid=") }
                            .removePrefix("worker-pid=")
                            .toLong()
                    }.get(60, SECONDS)
            val worker = ProcessHandle.of(pid).orElseThrow()
            // killed, as when the IDE or the operating system ends the server: nothing tells the worker
            host.destroyForcibly()
            assertThat(worker.onExit().get(30, SECONDS).isAlive).isFalse()
        } finally {
            host.destroyForcibly()
        }
    }

    @Test
    fun `closing the editor ends the server's runs and their workers`() {
        CompilerTestSupport.configure()
        val forever = source("Forever.x", FOREVER.trimIndent())
        val adapter = XdkAdapter()
        adapter.initializeWorkspace(listOf(directory.toString()))
        adapter.replaceSourceModules(listOf(XdkSourceModule("Forever", forever)))
        val server = XtcLanguageServer(adapter)
        assertThat(server.run(RunParams(module = "Forever", runId = "editor-closes")).get(60, SECONDS).runId)
            .isEqualTo("editor-closes")
        val workers = server.runWorkerPids().map { ProcessHandle.of(it).orElseThrow() }
        assertThat(workers).isNotEmpty()
        // the editor closes its end: the server reads the end of its input, shuts down and ends its workers
        launchStdio(server, ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
        workers.forEach { assertThat(it.onExit().get(30, SECONDS).isAlive).isFalse() }
    }

    @Test
    fun `a missing entry method is reported instead of starting, with the command-line runner's message`() {
        val run = run("Exit", method = "nope")
        assertThat(run.code).isNull()
        assertThat(run.error).isEqualTo("Missing method \"nope\" in module Exit")
    }

    @Test
    fun `arguments for a run method without parameters are dropped, with the runner's warning in the output`() {
        val run = run("Exit", args = listOf("ignored"))
        assertThat(run.code).isEqualTo(42)
        assertThat(run.output).contains("Warning: Method \"run\" does not take any parameters; ignoring the specified arguments.")
    }

    @Test
    fun `assert debug does not stop a run, which has no debugger, and the worker serves the next run`() {
        val run = run("Breaker")
        assertThat(run.error).isNull()
        assertThat(run.code).isEqualTo(0)
        assertThat(run.output.lines().filter { it.isNotEmpty() }).containsExactly("before", "after")

        assertThat(run("Exit").code).isEqualTo(42)
    }

    @Test
    fun `stop ends a program that never finishes, and the worker serves the next run`() {
        val output = StringBuffer()
        val exited = CompletableFuture<Long?>()
        val started = CompletableFuture<Unit>()
        val id =
            worker.start(
                RunWorkerProcess.Request(listOf(lib.path), "Forever", "run", emptyList(), directory.toString()),
                object : RunWorkerProcess.Listener {
                    override fun output(text: String) {
                        output.append(text)
                        if ("started" in output) started.complete(Unit)
                    }

                    override fun exited(
                        code: Long?,
                        error: String?,
                    ) {
                        exited.complete(code)
                    }
                },
            )
        started.get(60, SECONDS)
        worker.stop(id)
        exited.get(30, SECONDS)

        assertThat(run("Exit").code).isEqualTo(42)
    }

    @Test
    fun `two programs run at the same time, each in its own worker`() {
        val outputs = List(2) { StringBuffer() }
        val exits = List(2) { CompletableFuture<Long?>() }
        val ids =
            (0..1).map { index ->
                worker.start(
                    RunWorkerProcess.Request(listOf(lib.path), "Forever", "run", emptyList(), directory.toString()),
                    object : RunWorkerProcess.Listener {
                        override fun output(text: String) {
                            outputs[index].append(text)
                        }

                        override fun exited(
                            code: Long?,
                            error: String?,
                        ) {
                            exits[index].complete(code)
                        }
                    },
                )
            }
        soon(60).until { outputs.all { "started" in it } }
        assertThat(worker.workerPids()).hasSizeGreaterThanOrEqualTo(2)
        ids.forEach(worker::stop)
        exits.forEach { it.get(30, SECONDS) }

        assertThat(run("Exit").code).isEqualTo(42)
    }

    @Test
    fun `a program reads console input line by line, and the end of input reads as an empty line`() {
        val output = StringBuffer()
        val prompted = CompletableFuture<Unit>()
        val exited = CompletableFuture<Pair<Long?, String?>>()
        val id =
            worker.start(
                RunWorkerProcess.Request(listOf(lib.path), "Ask", "run", emptyList(), directory.toString()),
                object : RunWorkerProcess.Listener {
                    override fun output(text: String) {
                        output.append(text)
                        if ("Name? " in output) prompted.complete(Unit)
                    }

                    override fun exited(
                        code: Long?,
                        error: String?,
                    ) {
                        exited.complete(code to error)
                    }
                },
            )
        prompted.get(60, SECONDS)
        worker.input(id, "Ada Lovelace ✓\n")
        worker.endInput(id)
        val (code, error) = exited.get(60, SECONDS)

        assertThat(error).isNull()
        assertThat(code).isEqualTo(0)
        assertThat(output.toString()).contains("Hello, Ada Lovelace ✓!").contains("next=[]")
    }

    @Test
    fun `stop ends a program waiting for input, and the worker serves the next run`() {
        val output = StringBuffer()
        val waiting = CompletableFuture<Unit>()
        val exited = CompletableFuture<Long?>()
        val id =
            worker.start(
                RunWorkerProcess.Request(listOf(lib.path), "Waiter", "run", emptyList(), directory.toString()),
                object : RunWorkerProcess.Listener {
                    override fun output(text: String) {
                        output.append(text)
                        if ("waiting" in output) waiting.complete(Unit)
                    }

                    override fun exited(
                        code: Long?,
                        error: String?,
                    ) {
                        exited.complete(code)
                    }
                },
            )
        waiting.get(60, SECONDS)
        worker.stop(id)
        exited.get(30, SECONDS)

        assertThat(run("Exit").code).isEqualTo(42)
    }

    @Test
    fun `input typed while the module is still building reaches the program`() {
        CompilerTestSupport.configure()
        val ask = source("Ask.x", ASK.trimIndent())
        val output = StringBuffer()
        val exited = CompletableFuture<RunExitedParams>()
        val client =
            object : XtcLanguageClient, LanguageClient by mock(LanguageClient::class.java) {
                override fun runOutput(params: RunOutputParams) {
                    output.append(params.text)
                }

                override fun runExited(params: RunExitedParams) {
                    exited.complete(params)
                }
            }
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Ask", ask)))
            // on the class's worker, which is already started; closing these runs would close it
            EmbeddedRuns(adapter, { client }, worker).let { runs ->
                val started = runs.run(RunParams(module = "Ask", runId = "typed-early"))
                // typed right away: until the build finishes and the program starts, the server holds it
                assertThat(runs.input(RunInputParams("typed-early", "Grace\n", end = true))).isTrue()
                assertThat(started.get(60, SECONDS).runId).isEqualTo("typed-early")
                val exit = exited.get(60, SECONDS)

                assertThat(exit.error).isNull()
                assertThat(output.toString()).contains("Hello, Grace!").contains("next=[]")
                assertThat(runs.tracked()).isZero()
                assertThat(runs.input(RunInputParams("typed-early", "late\n"))).isFalse()
                assertThat(runs.input(RunInputParams("no-such-run", "text\n"))).isFalse()
            }
        }
    }

    @Test
    fun `a run stopped while its module builds never starts, and reports that it was stopped`() {
        CompilerTestSupport.configure()
        val greeter = source("Late.x", "module Late { void run() { @Inject Console console; console.print(\"started\"); } }")
        val client = RunClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Late", greeter)))
            // on the class's worker, which is already started; closing these runs would close it
            EmbeddedRuns(adapter, { client }, worker).let { runs ->
                val started = runs.run(RunParams(module = "Late", runId = "stopped-early"))
                assertThat(runs.stop(StopRunParams("stopped-early"))).isTrue()

                assertThat(started.get(60, SECONDS).runId).isEqualTo("stopped-early")
                val exit = client.exits.poll(60, SECONDS)!!
                assertThat(exit.error).isEqualTo(EmbeddedRuns.STOPPED_BEFORE_START)
                assertThat(client.output.toString()).doesNotContain("started")
                assertThat(runs.tracked()).isZero()
            }
        }
    }

    @Test
    fun `a debug session over the server's connection builds and runs the module, and its exit ends the session`() {
        CompilerTestSupport.configure()
        val hello =
            source("Hello.x", "module Hello { void run(String[] args) { @Inject Console console; console.print($\"hello {args[0]}\"); } }")
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Hello", hello)))
            // on the class's worker, which is already started; closing these runs would close it
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Hello", "moduleUri": "$hello", "args": ["world"]}""")
            sessions.request(id, 3, "configurationDone")
            val messages = client.until(id) { it.event == "terminated" }

            val initialize = messages.indexOfFirst { it.response == "initialize" }
            assertThat(messages[initialize]["success"].asBoolean).isTrue()
            assertThat(messages.indexOfFirst { it.event == "initialized" }).isGreaterThan(initialize)
            assertThat(messages.single { it.response == "launch" }["success"].asBoolean).isTrue()
            assertThat(messages.filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString })
                .contains("hello world")
            assertThat(messages.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isZero()
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `a line a debug session's client sends from its console while the program runs is the program's input`() {
        CompilerTestSupport.configure()
        val asks = source("Asks.x", ONE_LINE.trimIndent())
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Asks", asks)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Asks", "noDebug": true}""")
            sessions.request(id, 3, "configurationDone")
            client.until(id) { it.event == "output" && "Name? " in it["body"].asJsonObject["output"].asString }
            sessions.request(id, 4, "evaluate", """{"expression": "Ada", "context": "repl"}""")
            val messages = client.until(id) { it.event == "terminated" }

            assertThat(messages.single { it.response == "evaluate" }["success"].asBoolean).isTrue()
            assertThat(messages.filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString })
                .contains("Hello, Ada!")
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `terminating a debug session while its module builds keeps the program from starting`() {
        CompilerTestSupport.configure()
        val late = source("Waits.x", "module Waits { void run() { @Inject Console console; console.print(\"started\"); } }")
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Waits", late)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            // without configurationDone, the program would start only after a short wait
            sessions.request(id, 2, "launch", """{"module": "Waits"}""")
            sessions.request(id, 3, "terminate")
            val messages = client.until(id) { it.event == "terminated" }

            val output = messages.filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString }
            assertThat(output).contains(EmbeddedRuns.STOPPED_BEFORE_START).doesNotContain("started\n")
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `runs do not start the runner's HTTP endpoint`() {
        assertThat(run("Exit").output).doesNotContain("Runner is listening")
    }

    @Test
    fun `an embedded build writes modules in dependency order into the workspace's embedded output`() {
        CompilerTestSupport.configure()
        val library = source("Library.x", "module Library { static Int value() = 7; }")
        val app = source("App.x", "module App { package lib import Library; Int run() = lib.value(); }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Library", library), XdkSourceModule("App", app, setOf("Library"))),
            )
            val build = adapter.buildAsync("App", rebuild = false).get(60, SECONDS)

            assertThat(build.succeeded).describedAs(build.diagnostics.toString()).isTrue()
            assertThat(build.modules.map { it.name }).containsExactly("Library", "App")
            val output = directory.resolve("build/xtc/embedded/lib").toFile()
            assertThat(File(output, "Library.xtc")).isFile()
            assertThat(File(output, "App.xtc")).isFile()
            assertThat(build.workingDirectory).isEqualTo(directory.toString())
        }
    }

    @Test
    fun `a failed build reports located diagnostics and skips dependent modules`() {
        CompilerTestSupport.configure()
        val broken =
            source(
                "Broken.x",
                """
                module Broken {
                    Int value() = "not a number";
                }
                """.trimIndent(),
            )
        val consumer = source("Consumer.x", "module Consumer { package b import Broken; Int run() = b.value(); }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Broken", broken), XdkSourceModule("Consumer", consumer, setOf("Broken"))),
            )
            val build = adapter.buildAsync("Consumer", rebuild = false).get(60, SECONDS)

            assertThat(build.succeeded).isFalse()
            assertThat(build.modules.first { it.name == "Broken" }.succeeded).isFalse()
            assertThat(build.modules.first { it.name == "Consumer" }.skipped).isTrue()
            assertThat(build.diagnostics).anySatisfy {
                assertThat(it.location.uri).isEqualTo(broken)
                assertThat(it.location.startLine).isEqualTo(1)
            }
        }
    }

    @Test
    fun `a build compiles the editor's unsaved text, not the file on disk`() {
        CompilerTestSupport.configure()
        val app = source("App.x", "module App { Int run() = 1; }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", app)))
            adapter.compile(app, "module App { Int run() = 7; }")
            val build = adapter.buildAsync("App", rebuild = false).get(60, SECONDS)

            assertThat(build.succeeded).describedAs(build.diagnostics.toString()).isTrue()
            assertThat(run("App", modulePath = build.modulePath).code).isEqualTo(7)
        }
    }

    @Test
    fun `unchanged modules are reused, an editor analysis counts, and rebuild compiles everything`() {
        CompilerTestSupport.configure()
        val library = source("Library.x", "module Library { static Int value() = 7; }")
        val app = source("App.x", "module App { package lib import Library; Int run() = lib.value(); }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Library", library), XdkSourceModule("App", app, setOf("Library"))),
            )

            fun reused(rebuild: Boolean = false) =
                adapter
                    .buildAsync("App", rebuild)
                    .get(60, SECONDS)
                    .also {
                        assertThat(it.succeeded).describedAs(it.diagnostics.toString()).isTrue()
                    }.modules
                    .associate { it.name to it.reused }

            assertThat(reused()).containsExactly(entry("Library", false), entry("App", false))
            assertThat(reused()).containsExactly(entry("Library", true), entry("App", true))
            // the editor's analysis of the changed library already compiled it; its consumer compiles again
            adapter.compile(library, "module Library { static Int value() = 8; }")
            assertThat(reused()).containsExactly(entry("Library", true), entry("App", false))
            assertThat(reused(rebuild = true)).containsExactly(entry("Library", false), entry("App", false))
        }
    }

    @Test
    fun `the manifest records each module's compiled sources and dependency revisions`() {
        CompilerTestSupport.configure()
        val libraryText = "module Library { static Int value() = 7; }"
        val library = source("Library.x", libraryText)
        val app = source("App.x", "module App { package lib import Library; Int run() = lib.value(); }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Library", library), XdkSourceModule("App", app, setOf("Library"))),
            )
            val build = adapter.buildAsync("App", rebuild = false).get(60, SECONDS)
            val revisions = build.modules.associate { it.name to it.revision }
            val manifest =
                JsonParser
                    .parseString(directory.resolve("build/xtc/embedded/manifest.json").toFile().readText())
                    .asJsonObject["modules"]
                    .asJsonArray
                    .associateBy { it.asJsonObject["name"].asString }
                    .mapValues { it.value.asJsonObject }

            assertThat(manifest.keys).containsExactly("Library", "App")
            assertThat(manifest.getValue("Library")["sources"].asJsonObject[library].asString)
                .isEqualTo(XdkBuild.sha256(libraryText))
            assertThat(manifest.getValue("Library")["revision"].asString).isEqualTo(revisions["Library"])
            assertThat(manifest.getValue("App")["dependencies"].asJsonObject["Library"].asString).isEqualTo(revisions["Library"])
        }
    }

    @Test
    fun `a module that no longer compiles leaves no earlier artifact to run`() {
        CompilerTestSupport.configure()
        val app = source("App.x", "module App { Int run() = 1; }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", app)))
            val artifact = directory.resolve("build/xtc/embedded/lib/App.xtc").toFile()
            assertThat(adapter.buildAsync("App", rebuild = false).get(60, SECONDS).succeeded).isTrue()
            assertThat(artifact).isFile()

            adapter.compile(app, "module App { Int run() = \"text\"; }")
            assertThat(adapter.buildAsync("App", rebuild = false).get(60, SECONDS).succeeded).isFalse()
            assertThat(artifact).doesNotExist()
        }
    }

    @Test
    fun `a run of a method its module no longer declares is refused, and the earlier artifact's method does not run`() {
        CompilerTestSupport.configure()
        val app = source("Greeting.x", """module Greeting { void greet() { @Inject Console console; console.print("old greeting"); } }""")
        val client = RunClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Greeting", app)))
            // on the class's worker, which is already started; closing these runs would close it
            val runs = EmbeddedRuns(adapter, { client }, worker)
            assertThat(runs.run(RunParams(module = "Greeting", method = "greet", runId = "listed")).get(60, SECONDS).runId)
                .isEqualTo("listed")
            assertThat(client.exits.poll(60, SECONDS)?.error).isNull()
            assertThat(client.output.toString()).contains("old greeting")

            // a target listed for greet, which the module no longer declares
            adapter.compile(app, "module Greeting { void run() {} }")
            client.output.setLength(0)
            val stale = runs.run(RunParams(module = "Greeting", method = "greet", runId = "stale")).get(60, SECONDS)
            assertThat(stale.build.succeeded).isTrue()
            assertThat(client.exits.poll(60, SECONDS)?.error).contains("greet")
            assertThat(client.output.toString()).doesNotContain("old greeting")
        }
    }

    @Test
    fun `a run of a module renamed in its source is refused, and its earlier artifact does not run`() {
        CompilerTestSupport.configure()
        val file = source("Renamed.x", """module Before { void run() { @Inject Console console; console.print("before the rename"); } }""")
        val client = RunClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Before", file)))
            // on the class's worker, which is already started; closing these runs would close it
            val runs = EmbeddedRuns(adapter, { client }, worker)
            assertThat(runs.run(RunParams(module = "Before", runId = "before")).get(60, SECONDS).runId).isEqualTo("before")
            assertThat(client.exits.poll(60, SECONDS)?.error).isNull()

            // a target listed for Before, which the source now calls After
            adapter.compile(file, """module After { void run() { @Inject Console console; console.print("after the rename"); } }""")
            adapter.replaceSourceModules(listOf(XdkSourceModule("After", file)))
            client.output.setLength(0)
            // refused before a build; the earlier artifact is still in the build output, but nothing runs it
            assertThatThrownBy { runs.run(RunParams(module = "Before", runId = "stale")).get(60, SECONDS) }
                .hasRootCauseInstanceOf(IllegalArgumentException::class.java)
                .hasRootCauseMessage("Unknown source module: Before")
            assertThat(client.exits.poll(1, SECONDS)).isNull()
            assertThat(client.output.toString()).doesNotContain("before the rename")
        }
    }

    @Test
    fun `a discovered module whose root file was renamed runs by its name when a saved target has the old file`() {
        CompilerTestSupport.configure()
        // a workspace of its own, so discovery finds no other test's sources
        val old = source("moved-root/App.x", """module App { void run() { @Inject Console console; console.print("app runs"); } }""")
        val workspace = directory.resolve("moved-root")
        val client = RunClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(workspace.toString()))
            // on the class's worker, which is already started; closing these runs would close it
            val runs = EmbeddedRuns(adapter, { client }, worker)
            assertThat(runs.run(RunParams(module = old, moduleName = "App", runId = "before")).get(60, SECONDS).runId)
                .isEqualTo("before")
            assertThat(client.exits.poll(60, SECONDS)?.error).isNull()

            // renamed on disk, as by a terminal; the watcher reports both paths
            val moved = Files.move(workspace.resolve("App.x"), workspace.resolve("Main.x"))
            assertThat(adapter.applyFileEvents(listOf(old, moved.toUri().toString()))).isNotNull()
            assertThatThrownBy { runs.run(RunParams(module = old, runId = "file only")).get(60, SECONDS) }
                .hasRootCauseMessage("Unknown source module: $old")

            client.output.setLength(0)
            assertThat(runs.run(RunParams(module = old, moduleName = "App", runId = "moved")).get(60, SECONDS).runId)
                .isEqualTo("moved")
            assertThat(client.exits.poll(60, SECONDS)?.error).isNull()
            assertThat(client.output.toString()).contains("app runs")
            // and by the new file, which the Run lens now names
            client.output.setLength(0)
            assertThat(runs.run(RunParams(module = moved.toUri().toString(), runId = "lens")).get(60, SECONDS).runId)
                .isEqualTo("lens")
            assertThat(client.exits.poll(60, SECONDS)?.error).isNull()
            assertThat(client.output.toString()).contains("app runs")
        }
    }

    @Test
    fun `only modules with a callable run method are runnable targets with a Run lens`() {
        CompilerTestSupport.configure()
        val libraryText = "module Library { static Int value() = 7; }"
        val appText = "module App { void run() {} }"
        // the runtime calls only methods, so a run function is no entry point
        val utilityText = "module Utility { static void run() {} }"
        val library = source("Library.x", libraryText)
        val app = source("App.x", appText)
        val utility = source("Utility.x", utilityText)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Library", library), XdkSourceModule("App", app), XdkSourceModule("Utility", utility)),
            )
            assertThat(adapter.compile(library, libraryText).success).isTrue()
            assertThat(adapter.compile(app, appText).success).isTrue()
            assertThat(adapter.compile(utility, utilityText).success).isTrue()

            assertThat(adapter.runnableTargets().map { it.module }).containsExactly("App")
            assertThat(adapter.getCodeLensesAsync(app, false).get(30, SECONDS).mapNotNull { it.command?.command })
                .contains("xtc.runModule")
            assertThat(adapter.getCodeLensesAsync(library, false).get(30, SECONDS).mapNotNull { it.command?.command })
                .doesNotContain("xtc.runModule")
        }
    }

    @Test
    fun `a document's Run lenses name their module and line`() {
        CompilerTestSupport.configure()
        val text = "// the module is not on the first line\n\nmodule App {\n    void run() {}\n}"
        val app = source("App.x", text)
        val libraryText = "module Library { static Int value() = 7; }"
        val library = source("Library.x", libraryText)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(app, text).success).isTrue()
            assertThat(adapter.compile(library, libraryText).success).isTrue()
            val lens = adapter.getCodeLensesAsync(app, false).get(30, SECONDS).single { it.command?.command == "xtc.runModule" }
            assertThat(adapter.runLensLines(app)).containsExactly("App" to lens.range.start.line)
            assertThat(lens.range.start.line).isEqualTo(2)
            assertThat(adapter.runLensLines(library)).isEmpty()
        }
    }

    @Test
    fun `a run method an annotation contributes gives its module a Run lens, and the module runs`() {
        CompilerTestSupport.configure()
        val contributionsText =
            """
            module Contributions {
                annotation Runnable into module {
                    void run() {
                        @Inject Console console;
                        console.print("run from an annotation");
                    }
                }
            }
            """.trimIndent()
        val annotatedText =
            """
            @Runnable
            module Annotated {
                package contributions import Contributions;
                import contributions.Runnable;
            }
            """.trimIndent()
        val contributions = source("Contributions.x", contributionsText)
        val annotated = source("Annotated.x", annotatedText)
        val output = StringBuffer()
        val exited = CompletableFuture<RunExitedParams>()
        val client =
            object : XtcLanguageClient, LanguageClient by mock(LanguageClient::class.java) {
                override fun runOutput(params: RunOutputParams) {
                    output.append(params.text)
                }

                override fun runExited(params: RunExitedParams) {
                    exited.complete(params)
                }
            }
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Contributions", contributions), XdkSourceModule("Annotated", annotated, setOf("Contributions"))),
            )
            assertThat(adapter.compile(contributions, contributionsText).success).isTrue()
            assertThat(adapter.compile(annotated, annotatedText).success).isTrue()

            // the module declares no run method; its annotation does, and the module declaring the annotation is no program
            assertThat(adapter.runnableTargets().map { it.module }).containsExactly("Annotated")
            assertThat(adapter.getCodeLensesAsync(annotated, false).get(30, SECONDS).mapNotNull { it.command?.command })
                .contains("xtc.runModule")
            // on the class's worker, which is already started; closing these runs would close it
            EmbeddedRuns(adapter, { client }, worker).let { runs ->
                assertThat(runs.run(RunParams(module = "Annotated", runId = "contributed")).get(60, SECONDS).runId).isEqualTo("contributed")
                assertThat(exited.get(60, SECONDS).error).isNull()
                assertThat(output.toString()).contains("run from an annotation")
            }
        }
    }

    /** Waits for a condition, checking it at once and then often: each wait here is short, and there are many. */
    private fun soon(seconds: Long) =
        await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(seconds))

    /** Records the run output and exits the server sends its client. */
    private class RunClient :
        XtcLanguageClient,
        LanguageClient by mock(LanguageClient::class.java) {
        val output = StringBuffer()
        val exits = LinkedBlockingQueue<RunExitedParams>()

        override fun runOutput(params: RunOutputParams) {
            output.append(params.text)
        }

        override fun runExited(params: RunExitedParams) {
            exits.add(params)
        }
    }

    /** Records the DAP messages that debug sessions send their client. */
    private class DebugClient :
        XtcLanguageClient,
        LanguageClient by mock(LanguageClient::class.java) {
        private val messages = LinkedBlockingQueue<DebugMessageParams>()

        override fun debugMessage(params: DebugMessageParams) {
            messages.add(params)
        }

        // a debug session's program reports through DAP events, not the run notifications
        override fun runOutput(params: RunOutputParams) = error("run output outside the debug session: $params")

        override fun runExited(params: RunExitedParams) = error("run exit outside the debug session: $params")

        /** The session's messages, in order, up to the first that satisfies [last]. */
        fun until(
            sessionId: String,
            last: (JsonObject) -> Boolean,
        ): List<JsonObject> {
            val received = mutableListOf<JsonObject>()
            while (received.none(last)) {
                val next = messages.poll(60, SECONDS) ?: error("no DAP message within 60 s after $received")
                if (next.sessionId == sessionId) received += next.message
            }
            return received
        }
    }

    /** Sends a DAP request; without [arguments], it has none, as VS Code sends `configurationDone`. */
    private fun DebugSessions.request(
        sessionId: String,
        seq: Int,
        command: String,
        arguments: String? = null,
    ) = receive(
        DebugMessageParams(
            sessionId,
            JsonParser
                .parseString(
                    """{"seq": $seq, "type": "request", "command": "$command"${arguments?.let { ", \"arguments\": $it" }.orEmpty()}}""",
                ).asJsonObject,
        ),
    )

    private val JsonObject.event get() = takeIf { it["type"]?.asString == "event" }?.get("event")?.asString

    private val JsonObject.response get() = takeIf { it["type"]?.asString == "response" }?.get("command")?.asString

    private data class Outcome(
        val output: String,
        val code: Long?,
        val error: String?,
    )

    private fun run(
        module: String,
        method: String = "run",
        args: List<String> = emptyList(),
        modulePath: List<String> = listOf(lib.path),
        workers: RunWorkerProcess = worker,
        event: () -> Unit = {},
    ): Outcome {
        val output = StringBuffer()
        val exited = CompletableFuture<Pair<Long?, String?>>()
        workers.start(
            RunWorkerProcess.Request(modulePath, module, method, args, directory.toString()),
            object : RunWorkerProcess.Listener {
                override fun output(text: String) {
                    event()
                    output.append(text)
                }

                override fun exited(
                    code: Long?,
                    error: String?,
                ) {
                    exited.complete(code to error)
                }
            },
        )
        val (code, error) = exited.get(60, SECONDS)
        return Outcome(output.toString(), code, error)
    }

    private companion object {
        const val FOREVER =
            """
            module Forever {
                void run() {
                    @Inject Console console;
                    console.print("started");
                    // runs until stopped: an empty while (True) is rejected as non-terminating
                    for (Int count = 0; count >= 0; ++count) {}
                }
            }
            """

        const val ONE_LINE =
            """
            module Asks {
                void run() {
                    @Inject Console console;
                    String name = console.readLine("Name? ");
                    console.print($"Hello, {name}!");
                }
            }
            """

        const val ASK =
            """
            module Ask {
                void run() {
                    @Inject Console console;
                    String name = console.readLine("Name? ");
                    console.print($"Hello, {name}!");
                    String next = console.readLine();
                    console.print($"next=[{next}]");
                }
            }
            """
    }

    private fun source(
        path: String,
        text: String,
    ): String {
        val file = directory.resolve(path).toFile()
        file.parentFile.mkdirs()
        file.writeText(text)
        return file.toURI().toString()
    }
}
