package org.xvm.lsp.run

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.slf4j.LoggerFactory
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
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
    private var worker = RunWorkerProcess()

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

    @AfterEach
    fun freshWorkerAfterALeftoverRun() {
        // a test that fails before its run or debug session ends leaves its program on the class's worker, where it
        // would fail the tests after it; they get a fresh worker instead
        val left = worker.tracked()
        if (left != 0) {
            println("a test left runs on the class's worker (tracked=$left), so the next test gets a fresh one")
            worker.close()
            worker = RunWorkerProcess()
        }
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
    fun `after a run the worker reports its heap in use and its threads, within the thread limit`() {
        assertThat(run("Exit").code).isEqualTo(42)
        soon(30).until { worker.usage().isNotEmpty() }
        val limits = RunWorkerProcess.Limits()
        assertThat(worker.usage().values).allSatisfy { usage ->
            println("execution worker after a run: $usage")
            // not against the heap limit: the heap in use is what the JVM left after its own collections, if any yet
            assertThat(usage.heapUsed).isNotNegative()
            assertThat(usage.heapMax).isGreaterThanOrEqualTo(usage.heapUsed)
            assertThat(usage.threads).isPositive().isLessThan(limits.threads)
        }
    }

    @Test
    fun `repeated runs reuse one worker and leave no run, console or pool thread behind`() {
        // the heap a worker reports is what the JVM left after collections it runs when it chooses to, so no heap use
        // retires this worker; what the test checks is counted
        RunWorkerProcess(limits = RunWorkerProcess.Limits(heap = Long.MAX_VALUE)).use { workers ->
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
            try {
                // killed, as when the IDE or the operating system ends the server: nothing tells the worker
                host.destroyForcibly()
                val exited = runCatching { worker.onExit().get(30, SECONDS) }.isSuccess
                assertThat(exited)
                    .describedAs { "The worker still runs 30 s after its server died:\n${linuxState(pid)}\n${threadDump(pid)}" }
                    .isTrue()
            } finally {
                worker.destroyForcibly()
            }
        } finally {
            host.destroyForcibly()
        }
    }

    /** What Linux says of a process that should have ended: its state and parent, and each thread's. */
    private fun linuxState(pid: Long): String {
        val proc = Path.of("/proc/$pid")

        fun read(what: () -> String) = runCatching(what).getOrElse { "unreadable: $it" }
        val status =
            read {
                Files
                    .readAllLines(
                        proc.resolve("status"),
                    ).filter { it.startsWith("State:") || it.startsWith("PPid:") }
                    .joinToString("; ")
            }
        val threads =
            read {
                Files.list(proc.resolve("task")).use { tasks ->
                    tasks.toList().joinToString("\n") { task ->
                        val state =
                            read {
                                Files
                                    .readString(task.resolve("stat"))
                                    .substringAfterLast(')')
                                    .trim()
                                    .substringBefore(' ')
                            }
                        "  ${task.fileName} ${read {
                            Files
                                .readString(
                                    task.resolve("comm"),
                                ).trim()
                        }} $state ${read { Files.readString(task.resolve("wchan")) }}"
                    }
                }
            }
        return "$status\n$threads"
    }

    /** The threads of a JVM that should have ended, from the JDK's jcmd, or why there are none. */
    private fun threadDump(pid: Long): String =
        runCatching {
            val jcmd =
                Path
                    .of(
                        ProcessHandle
                            .current()
                            .info()
                            .command()
                            .orElseThrow(),
                    ).resolveSibling("jcmd")
            val dump = ProcessBuilder(jcmd.toString(), pid.toString(), "Thread.print").redirectErrorStream(true).start()
            dump.inputReader().use { it.readText() }.also { dump.waitFor(30, SECONDS) }
        }.getOrElse { "no thread dump: $it" }

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
    fun `a breakpoint stops a debugged program, which shows its threads, stack and variables, and continue runs it to its exit`() {
        CompilerTestSupport.configure()
        val stops = source("Stops.x", STOPS.trimIndent())
        val path = directory.resolve("Stops.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Stops", stops)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Stops", "moduleUri": "$stops"}""")
            sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 11}]}""")
            sessions.request(id, 4, "configurationDone")
            // output events can come between any of the replies
            val seen = mutableListOf<JsonObject>()
            val untilStop = client.until(id) { it.event == "stopped" }.also(seen::addAll)

            val pending =
                untilStop
                    .single { it.response == "setBreakpoints" }["body"]
                    .asJsonObject["breakpoints"]
                    .asJsonArray
                    .single()
                    .asJsonObject
            assertThat(pending["verified"].asBoolean).isFalse()
            val installed = untilStop.single { it.event == "breakpoint" }["body"].asJsonObject["breakpoint"].asJsonObject
            assertThat(installed["id"].asInt).isEqualTo(pending["id"].asInt)
            assertThat(installed["verified"].asBoolean).isTrue()
            assertThat(installed["line"].asInt).isEqualTo(11)
            val stopped = untilStop.last()["body"].asJsonObject
            assertThat(stopped["reason"].asString).isEqualTo("breakpoint")
            // what the program printed before it stopped arrives before the stop
            assertThat(untilStop.filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString })
                .contains("before")
            assertThat(stopped["hitBreakpointIds"].asJsonArray.map { it.asInt }).containsExactly(pending["id"].asInt)
            val thread = stopped["threadId"].asInt

            sessions.request(id, 5, "threads")
            val threads =
                client
                    .until(id) { it.response == "threads" }
                    .also(seen::addAll)
                    .last()["body"]
                    .asJsonObject["threads"]
                    .asJsonArray
            assertThat(threads.map { it.asJsonObject["id"].asInt }).contains(thread)
            sessions.request(id, 6, "stackTrace", """{"threadId": $thread}""")
            val frames =
                client
                    .until(
                        id,
                    ) { it.response == "stackTrace" }
                    .also(seen::addAll)
                    .last()["body"]
                    .asJsonObject["stackFrames"]
                    .asJsonArray
                    .map { it.asJsonObject }
            // the program's frames, without those of the runner that hosts it
            assertThat(
                frames.map { it["name"].asString to it["line"].asInt },
            ).containsExactly("Stops.twice" to 11, "Stops.run" to 5)
            assertThat(frames.first()["source"].asJsonObject["path"].asString).isEqualTo(path)

            sessions.request(id, 7, "scopes", """{"frameId": ${frames.first()["id"].asInt}}""")
            val scope =
                client
                    .until(id) { it.response == "scopes" }
                    .also(seen::addAll)
                    .last()["body"]
                    .asJsonObject["scopes"]
                    .asJsonArray
                    .single()
                    .asJsonObject
            assertThat(scope["name"].asString).isEqualTo("Locals")
            sessions.request(id, 8, "variables", """{"variablesReference": ${scope["variablesReference"].asInt}}""")
            val n =
                client
                    .until(id) { it.response == "variables" }
                    .also(seen::addAll)
                    .last()["body"]
                    .asJsonObject["variables"]
                    .asJsonArray
                    .map { it.asJsonObject }
                    .single { it["name"].asString == "n" }
            assertThat(n["value"].asString).isEqualTo("21")
            assertThat(n["type"].asString).isEqualTo("Int")

            // a watch on the stopped frame evaluates an expression there, with the runtime's own operations, and one
            // that cannot be evaluated says why
            val frameId = frames.first()["id"].asInt
            sessions.request(id, 20, "evaluate", """{"expression": "n", "frameId": $frameId, "context": "watch"}""")
            val watched = client.until(id) { it.response == "evaluate" }.last()
            assertThat(watched["body"].asJsonObject["result"].asString).isEqualTo("21")
            sessions.request(id, 22, "evaluate", """{"expression": "n * 2 + 1", "frameId": $frameId, "context": "watch"}""")
            val computed = client.until(id) { it.response == "evaluate" }.last()
            assertThat(computed["body"].asJsonObject["result"].asString).isEqualTo("43")
            sessions.request(id, 21, "evaluate", """{"expression": "nope", "frameId": $frameId, "context": "watch"}""")
            val refused = client.until(id) { it.response == "evaluate" }.last()
            assertThat(refused["success"].asBoolean).isFalse()
            assertThat(refused["message"].asString).isEqualTo("There is no variable \"nope\" in this frame.")

            sessions.request(id, 9, "continue", """{"threadId": $thread}""")
            val rest = client.until(id) { it.event == "terminated" }.also(seen::addAll)
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(42)
            val output = seen.filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString }
            assertThat(output).contains("before").contains("after 42")
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `once a debugged file is edited, a breakpoint is placed on the line the program was built from, or refused on a changed line`() {
        CompilerTestSupport.configure()
        val stops = source("Stops.x", STOPS.trimIndent())
        val path = directory.resolve("Stops.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Stops", stops)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Stops", "moduleUri": "$stops"}""")
            // once launch is handled, its build has taken the text it compiles
            sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": []}""")
            client.until(id) { it.response == "setBreakpoints" }

            // two lines typed above n = twice(21), on line 5 as built and on line 7 now
            val lines = STOPS.trimIndent().lines().toMutableList()
            lines.addAll(3, listOf("    // one", "    // two"))
            adapter.updateDocument(stops, lines.joinToString("\n"))
            sessions.request(id, 4, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 7}, {"line": 4}]}""")
            val pending =
                client
                    .until(id) { it.response == "setBreakpoints" }
                    .last()["body"]
                    .asJsonObject["breakpoints"]
                    .asJsonArray
                    .map { it.asJsonObject["id"].asInt }
            sessions.request(id, 5, "configurationDone")
            val untilStop = client.until(id) { it.event == "stopped" }

            // the moved line's breakpoint is installed where it is now, the typed line's is refused with the reason
            val changes =
                untilStop
                    .filter { it.event == "breakpoint" }
                    .map { it["body"].asJsonObject["breakpoint"].asJsonObject }
                    .associateBy { it["id"].asInt }
            assertThat(changes.getValue(pending[0])["verified"].asBoolean).isTrue()
            assertThat(changes.getValue(pending[0])["line"].asInt).isEqualTo(7)
            assertThat(changes.getValue(pending[1])["verified"].asBoolean).isFalse()
            assertThat(changes.getValue(pending[1])["message"].asString).isEqualTo(EmbeddedRuns.CHANGED_SINCE_BUILD)
            val thread = untilStop.last()["body"].asJsonObject["threadId"].asInt

            fun stack(seq: Int): List<Pair<String, Int>> {
                sessions.request(id, seq, "stackTrace", """{"threadId": $thread}""")
                return client
                    .until(id) { it.response == "stackTrace" }
                    .last()["body"]
                    .asJsonObject["stackFrames"]
                    .asJsonArray
                    .map { it.asJsonObject }
                    .map { it["name"].asString to it["line"].asInt }
            }
            // the stop is shown on its line now
            assertThat(stack(6)).containsExactly("Stops.run" to 7)

            // while it runs, too: Int result = n * 2, line 11 as built, is line 13 now, and line 5 is one of those typed
            sessions.request(id, 7, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 13}, {"line": 5}]}""")
            val placed =
                client
                    .until(id) { it.response == "setBreakpoints" }
                    .last()["body"]
                    .asJsonObject["breakpoints"]
                    .asJsonArray
                    .map { it.asJsonObject }
            assertThat(placed.map { it["verified"].asBoolean to it["line"].asInt }).containsExactly(true to 13, false to 5)
            assertThat(placed[1]["message"].asString).isEqualTo(EmbeddedRuns.CHANGED_SINCE_BUILD)
            sessions.request(id, 8, "continue", """{"threadId": $thread}""")
            client.until(id) { it.event == "stopped" }
            assertThat(stack(9)).containsExactly("Stops.twice" to 13, "Stops.run" to 7)

            sessions.request(id, 10, "continue", """{"threadId": $thread}""")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(42)
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `a step into one of the calls of the line goes into that call, and the line's other calls run on`() {
        CompilerTestSupport.configure()
        val module = source("Targets.x", TARGETS.trimIndent())
        val path = directory.resolve("Targets.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Targets", module)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Targets", "moduleUri": "$module"}""")
            sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 4}]}""")
            sessions.request(id, 4, "configurationDone")
            val thread =
                client
                    .until(id) { it.event == "stopped" }
                    .last()["body"]
                    .asJsonObject["threadId"]
                    .asInt

            fun top(seq: Int): JsonObject {
                sessions.request(id, seq, "stackTrace", """{"threadId": $thread}""")
                return client
                    .until(id) { it.response == "stackTrace" }
                    .last()["body"]
                    .asJsonObject["stackFrames"]
                    .asJsonArray
                    .first()
                    .asJsonObject
            }
            sessions.request(id, 6, "stepInTargets", """{"frameId": ${top(5)["id"].asInt}}""")
            val targets =
                client
                    .until(id) { it.response == "stepInTargets" }
                    .last()["body"]
                    .asJsonObject["targets"]
                    .asJsonArray
                    .map { it.asJsonObject }
            assertThat(targets.map { it["label"].asString }).containsExactly("add(Int, Int)", "twice(Int)")

            // into the second call: add() runs without stopping, and the step ends in twice()
            sessions.request(id, 7, "stepIn", """{"threadId": $thread, "targetId": ${targets[1]["id"].asInt}}""")
            val stepped = client.until(id) { it.event == "stopped" }
            assertThat(stepped.single { it.response == "stepIn" }["success"].asBoolean).isTrue()
            assertThat(stepped.last()["body"].asJsonObject["reason"].asString).isEqualTo("step")
            assertThat(top(8).let { it["name"].asString to it["line"].asInt }).isEqualTo("Targets.twice" to 15)

            sessions.request(id, 9, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": []}""")
            client.until(id) { it.response == "setBreakpoints" }
            sessions.request(id, 10, "continue", """{"threadId": $thread}""")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(6)
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `a function breakpoint set before the start stops where the method of its name is entered`() {
        CompilerTestSupport.configure()
        val module = source("Entered.x", TARGETS.trimIndent().replace("Targets", "Entered"))
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Entered", module)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Entered", "moduleUri": "$module"}""")
            sessions.request(id, 3, "setFunctionBreakpoints", """{"breakpoints": [{"name": "twice"}, {"name": "nothing"}]}""")
            val pending =
                client
                    .until(id) { it.response == "setFunctionBreakpoints" }
                    .last()["body"]
                    .asJsonObject["breakpoints"]
                    .asJsonArray
                    .map { it.asJsonObject }
            assertThat(pending.map { it["verified"].asBoolean }).containsOnly(false)
            sessions.request(id, 4, "configurationDone")
            val stopped = client.until(id) { it.event == "stopped" }

            // installed with the program: twice() at its first line, and no method has the other name
            val installed = stopped.filter { it.event == "breakpoint" }.map { it["body"].asJsonObject["breakpoint"].asJsonObject }
            val twice = installed.single { it["id"].asInt == pending[0]["id"].asInt }
            assertThat(twice["verified"].asBoolean).isTrue()
            assertThat(twice["line"].asInt).isEqualTo(15)
            assertThat(Path.of(twice["source"].asJsonObject["path"].asString).toRealPath())
                .isEqualTo(directory.resolve("Entered.x").toRealPath())
            assertThat(installed.single { it["id"].asInt == pending[1]["id"].asInt }["verified"].asBoolean).isFalse()

            val stop = stopped.last()["body"].asJsonObject
            assertThat(stop["reason"].asString).isEqualTo("function breakpoint")
            assertThat(stop["hitBreakpointIds"].asJsonArray.map { it.asInt }).containsExactly(pending[0]["id"].asInt)
            sessions.request(id, 5, "stackTrace", """{"threadId": ${stop["threadId"].asInt}}""")
            val top =
                client
                    .until(id) { it.response == "stackTrace" }
                    .last()["body"]
                    .asJsonObject["stackFrames"]
                    .asJsonArray
                    .first()
                    .asJsonObject
            assertThat(top["name"].asString to top["line"].asInt).isEqualTo("Entered.twice" to 15)

            // replaced while it runs, at once
            sessions.request(id, 6, "setFunctionBreakpoints", """{"breakpoints": []}""")
            client.until(id) { it.response == "setFunctionBreakpoints" }
            sessions.request(id, 7, "continue", """{"threadId": ${stop["threadId"].asInt}}""")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.none { it.event == "stopped" }).isTrue()
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(6)
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `a data breakpoint on a field of an object stops at the next line once the field has changed`() {
        CompilerTestSupport.configure()
        val module = source("Watched.x", WATCHED.trimIndent())
        val path = directory.resolve("Watched.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Watched", module)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Watched", "moduleUri": "$module"}""")
            sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 5}]}""")
            sessions.request(id, 4, "configurationDone")
            val thread =
                client
                    .until(id) { it.event == "stopped" }
                    .last()["body"]
                    .asJsonObject["threadId"]
                    .asInt

            fun body(
                seq: Int,
                command: String,
                arguments: String,
            ): JsonObject {
                sessions.request(id, seq, command, arguments)
                return client.until(id) { it.response == command }.last()["body"].asJsonObject
            }

            fun top(seq: Int) = body(seq, "stackTrace", """{"threadId": $thread}""")["stackFrames"].asJsonArray.first().asJsonObject

            // the counter's count, from the variable of run()'s frame
            val locals = body(6, "scopes", """{"frameId": ${top(5)["id"].asInt}}""")["scopes"].asJsonArray.first().asJsonObject
            val counter =
                body(7, "variables", """{"variablesReference": ${locals["variablesReference"].asInt}}""")["variables"]
                    .asJsonArray
                    .map { it.asJsonObject }
                    .single { it["name"].asString == "counter" }
            val info = body(8, "dataBreakpointInfo", """{"variablesReference": ${counter["variablesReference"].asInt}, "name": "count"}""")
            assertThat(info["description"].asString).isEqualTo("Counter.count")
            val installed = body(9, "setDataBreakpoints", """{"breakpoints": [{"dataId": "${info["dataId"].asString}"}]}""")
            assertThat(
                installed["breakpoints"]
                    .asJsonArray
                    .single()
                    .asJsonObject["verified"]
                    .asBoolean,
            ).isTrue()

            // the first add() stops at the line after the one that changed the count
            body(10, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": []}""")
            sessions.request(id, 11, "continue", """{"threadId": $thread}""")
            val stop = client.until(id) { it.event == "stopped" }.last()["body"].asJsonObject
            assertThat(stop["reason"].asString).isEqualTo("data breakpoint")
            assertThat(stop["description"].asString).isEqualTo("Counter.count changed from 0 to 2")
            assertThat(top(12).let { it["name"].asString to it["line"].asInt }).isEqualTo("Counter.add" to 17)

            body(13, "setDataBreakpoints", """{"breakpoints": []}""")
            sessions.request(id, 14, "continue", """{"threadId": $thread}""")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.none { it.event == "stopped" }).isTrue()
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(5)
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `a restarted frame is called again by its caller, which stops at its first line`() {
        CompilerTestSupport.configure()
        val module = source("Restarts.x", RESTARTS.trimIndent())
        val path = directory.resolve("Restarts.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Restarts", module)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Restarts", "moduleUri": "$module"}""")
            sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 16}]}""")
            sessions.request(id, 4, "configurationDone")
            val first = client.until(id) { it.event == "stopped" }
            val thread = first.last()["body"].asJsonObject["threadId"].asInt

            fun frames(seq: Int): List<JsonObject> {
                sessions.request(id, seq, "stackTrace", """{"threadId": $thread}""")
                return client
                    .until(id) { it.response == "stackTrace" }
                    .last()["body"]
                    .asJsonObject["stackFrames"]
                    .asJsonArray
                    .map { it.asJsonObject }
            }
            val stopped = frames(5)
            assertThat(stopped.map { it["name"].asString }).containsExactly("Restarts.inner", "Restarts.outer", "Restarts.run")
            // run() is called by the runtime, which cannot make the call again; false is said, since a frame that does not
            // say it cannot restart can
            assertThat(stopped.map { it["canRestart"]?.asBoolean }).containsExactly(true, true, false)

            // outer() again, from run(): it stops at its first line and prints again
            sessions.request(id, 6, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": []}""")
            client.until(id) { it.response == "setBreakpoints" }
            sessions.request(id, 7, "restartFrame", """{"frameId": ${stopped[1]["id"].asInt}}""")
            val restarted = client.until(id) { it.event == "stopped" }
            assertThat(restarted.single { it.response == "restartFrame" }["success"].asBoolean).isTrue()
            assertThat(restarted.last()["body"].asJsonObject["reason"].asString).isEqualTo("restart")
            assertThat(frames(8).map { it["name"].asString to it["line"].asInt })
                .containsExactly("Restarts.outer" to 10, "Restarts.run" to 4)

            sessions.request(id, 9, "continue", """{"threadId": $thread}""")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(42)
            val output =
                (first + restarted + rest).filter { it.event == "output" }.joinToString(
                    "",
                ) { it["body"].asJsonObject["output"].asString }
            assertThat(output.split("outer 20").size - 1).withFailMessage(output).isEqualTo(2)
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `a stop lists the fibers that do not wait at a line, with what they do, and the stack of one that waits`() {
        CompilerTestSupport.configure()
        val module = source("Waits.x", PROGRAM.trimIndent().replace("Program", "Waits"))
        val path = directory.resolve("Waits.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Waits", module)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Waits", "moduleUri": "$module"}""")
            // by then, run() has asked the busy worker for its count, and waits for the answer
            sessions.request(
                id,
                3,
                "setBreakpoints",
                """{"source": {"path": "$path"}, "breakpoints": [{"line": 7, "condition": "done == 100000"}]}""",
            )
            sessions.request(id, 4, "configurationDone")
            val thread =
                client
                    .until(id) { it.event == "stopped" }
                    .last()["body"]
                    .asJsonObject["threadId"]
                    .asInt
            sessions.request(id, 5, "threads")
            val threads =
                client
                    .until(id) { it.response == "threads" }
                    .last()["body"]
                    .asJsonObject["threads"]
                    .asJsonArray
                    .map { it.asJsonObject }
            // the stopped fiber, run()'s that waits, and the worker's fiber for that call, once it has taken it
            val names = threads.map { it["name"].asString }
            assertThat(names.take(2)).containsExactly("Worker", "Waits (waiting)")
            assertThat(names.drop(2)).allMatch { it == "Worker (not started)" }
            assertThat(threads[0]["id"].asInt).isEqualTo(thread)

            // the waiting fiber's own stack, where it asks the worker; its frame has no variables
            sessions.request(id, 6, "stackTrace", """{"threadId": ${threads[1]["id"].asInt}}""")
            val frames =
                client
                    .until(id) { it.response == "stackTrace" }
                    .last()["body"]
                    .asJsonObject["stackFrames"]
                    .asJsonArray
                    .map { it.asJsonObject }
            assertThat(frames.map { it["name"].asString to it["line"].asInt }).containsExactly("Waits.run" to 19)
            sessions.request(id, 7, "scopes", """{"frameId": ${frames[0]["id"].asInt}}""")
            assertThat(
                client
                    .until(id) { it.response == "scopes" }
                    .last()["body"]
                    .asJsonObject["scopes"]
                    .asJsonArray,
            ).isEmpty()

            sessions.request(id, 8, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": []}""")
            client.until(id) { it.response == "setBreakpoints" }
            sessions.request(id, 9, "continue", """{"threadId": $thread}""")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(200_000 % 256)
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `a debugged program's modules and loaded sources are its own and its libraries', a library's in its bundled source`() {
        CompilerTestSupport.configure()
        val library = source("Library.x", LIBRARY.trimIndent())
        val path = directory.resolve("Library.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Library", library)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Library", "moduleUri": "$library"}""")
            sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 6}]}""")
            sessions.request(id, 4, "configurationDone")
            val thread =
                client
                    .until(id) { it.event == "stopped" }
                    .last()["body"]
                    .asJsonObject["threadId"]
                    .asInt

            sessions.request(id, 5, "modules", "{}")
            val modules =
                client
                    .until(id) { it.response == "modules" }
                    .last()["body"]
                    .asJsonObject["modules"]
                    .asJsonArray
                    .map { it.asJsonObject }
                    .associate { it["name"].asString to it["isUserCode"].asBoolean }
            assertThat(modules).containsEntry("Library", true).containsEntry("ecstasy.xtclang.org", false)

            sessions.request(id, 6, "loadedSources")
            val sources =
                client
                    .until(id) { it.response == "loadedSources" }
                    .last()["body"]
                    .asJsonObject["sources"]
                    .asJsonArray
                    .map { Path.of(it.asJsonObject["path"].asString) }
            assertThat(sources.map { it.toRealPath() }).contains(Path.of(path).toRealPath())
            // a library's source is the read-only copy bundled with it, which its frames show too
            val collection = sources.single { it.endsWith(Path.of("ecstasy/collections/Collection.x")) }
            assertThat(collection).exists()
            assertThat(collection.startsWith(directory)).isFalse()
            assertThat(Files.isWritable(collection)).isFalse()

            sessions.request(id, 7, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": []}""")
            client.until(id) { it.response == "setBreakpoints" }
            sessions.request(id, 8, "continue", """{"threadId": $thread}""")
            client.until(id) { it.event == "terminated" }
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `a frame in a bundled library's code shows the library's bundled source, read-only, at its line`() {
        CompilerTestSupport.configure()
        val library = source("Library.x", LIBRARY.trimIndent())
        val path = directory.resolve("Library.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Library", library)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Library", "moduleUri": "$library"}""")
            sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 6}]}""")
            sessions.request(id, 4, "configurationDone")
            val thread =
                client
                    .until(id) { it.event == "stopped" }
                    .last()["body"]
                    .asJsonObject["threadId"]
                    .asInt
            sessions.request(id, 5, "stackTrace", """{"threadId": $thread}""")
            val frames =
                client
                    .until(id) { it.response == "stackTrace" }
                    .last()["body"]
                    .asJsonObject["stackFrames"]
                    .asJsonArray
                    .map { it.asJsonObject }
            // the lambda, the library's forEach that calls it, and run, which calls forEach
            assertThat(frames.map { it["name"].asString to it["source"].asJsonObject["path"].asString })
                .containsExactly(
                    "Library.->" to path,
                    "Collection.forEach" to frames[1]["source"].asJsonObject["path"].asString,
                    "Library.run" to path,
                )

            // not a file in the program's folder, nor where the library was built, but the source it was compiled from
            val file = Path.of(frames[1]["source"].asJsonObject["path"].asString)
            assertThat(file).exists().endsWith(Path.of("ecstasy/collections/Collection.x"))
            assertThat(file.startsWith(directory)).isFalse()
            assertThat(Files.isWritable(file)).isFalse()
            assertThat(Files.readAllLines(file)[frames[1]["line"].asInt - 1]).contains("process(")

            sessions.request(id, 6, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": []}""")
            client.until(id) { it.response == "setBreakpoints" }
            sessions.request(id, 7, "continue", """{"threadId": $thread}""")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString })
                .contains("n=1", "n=2", "n=3")
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `run to cursor, a breakpoint added while stopped and removed once reached, stops the program there once`() {
        CompilerTestSupport.configure()
        val loops = source("Loops.x", LOOPS.trimIndent())
        val path = directory.resolve("Loops.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Loops", loops)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Loops", "moduleUri": "$loops"}""")
            sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 4}]}""")
            sessions.request(id, 4, "configurationDone")
            val first = client.until(id) { it.event == "stopped" }
            val thread = first.last()["body"].asJsonObject["threadId"].asInt

            fun top(seq: Int): Pair<String, Int> {
                sessions.request(id, seq, "stackTrace", """{"threadId": $thread}""")
                return client
                    .until(id) { it.response == "stackTrace" }
                    .last()["body"]
                    .asJsonObject["stackFrames"]
                    .asJsonArray
                    .first()
                    .asJsonObject
                    .let { it["name"].asString to it["line"].asInt }
            }
            assertThat(top(5)).isEqualTo("Loops.run" to 4)

            // both editors run to the cursor as one more breakpoint, on line 7 in the loop, and Continue
            sessions.request(id, 6, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 4}, {"line": 7}]}""")
            client.until(id) { it.response == "setBreakpoints" }
            sessions.request(id, 7, "continue", """{"threadId": $thread}""")
            val atCursor = client.until(id) { it.event == "stopped" }
            assertThat(top(8)).isEqualTo("Loops.run" to 7)
            val output = { messages: List<JsonObject> ->
                messages.filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString }
            }
            // the loop's first round, before it prints
            assertThat(output(first + atCursor)).doesNotContain("i=")

            // stopped there, they remove that breakpoint again, so the loop's next rounds do not stop
            sessions.request(id, 9, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 4}]}""")
            client.until(id) { it.response == "setBreakpoints" }
            sessions.request(id, 10, "continue", """{"threadId": $thread}""")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.filter { it.event == "stopped" }).isEmpty()
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(3)
            assertThat(output(first + atCursor + rest)).contains("i=0", "i=1", "i=2", "total=3")
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `a console line in a stopped frame runs code there, whose effect stays, and cancel or the time limit ends a run that loops`() {
        CompilerTestSupport.configure()
        val module = source("Runs.x", RUNS.trimIndent())
        val path = directory.resolve("Runs.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Runs", module)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc", "supportsInvalidatedEvent": true}""")
            sessions.request(id, 2, "launch", """{"module": "Runs", "moduleUri": "$module"}""")
            sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 21}]}""")
            sessions.request(id, 4, "configurationDone")
            val thread =
                client
                    .until(id) { it.event == "stopped" }
                    .last()["body"]
                    .asJsonObject["threadId"]
                    .asInt
            sessions.request(id, 5, "stackTrace", """{"threadId": $thread}""")
            val frame =
                client
                    .until(id) { it.response == "stackTrace" }
                    .last()["body"]
                    .asJsonObject["stackFrames"]
                    .asJsonArray
                    .first()
                    .asJsonObject
            assertThat(frame["name"].asString).isEqualTo("Runs.show")
            val frameId = frame["id"].asInt

            // a call runs on the stopped fiber, and the editors are told to read the variables again
            sessions.request(id, 6, "evaluate", """{"expression": "box.bump(n)", "frameId": $frameId, "context": "repl"}""")
            val bumped = client.until(id) { it.response == "evaluate" }
            assertThat(bumped.last()["body"].asJsonObject["result"].asString).isEqualTo("5")
            val invalidated = bumped.firstOrNull { it.event == "invalidated" } ?: client.until(id) { it.event == "invalidated" }.last()
            assertThat(invalidated["body"].asJsonObject["areas"].asJsonArray.map { it.asString }).containsExactly("variables")
            // what it changed stays changed, also for a watch, which runs nothing
            sessions.request(id, 7, "evaluate", """{"expression": "box.count", "frameId": $frameId, "context": "watch"}""")
            assertThat(
                client
                    .until(id) { it.response == "evaluate" }
                    .last()["body"]
                    .asJsonObject["result"]
                    .asString,
            ).isEqualTo("5")

            // an object's toString() is a lazy variable, and reading its reference runs it on the stopped fiber
            fun variables(
                seq: Int,
                reference: Int,
            ): List<JsonObject> {
                sessions.request(id, seq, "variables", """{"variablesReference": $reference}""")
                return client
                    .until(id) { it.response == "variables" }
                    .last()["body"]
                    .asJsonObject["variables"]
                    .asJsonArray
                    .map { it.asJsonObject }
            }
            sessions.request(id, 30, "scopes", """{"frameId": $frameId}""")
            val locals =
                client
                    .until(id) { it.response == "scopes" }
                    .last()["body"]
                    .asJsonObject["scopes"]
                    .asJsonArray
                    .single()
                    .asJsonObject["variablesReference"]
                    .asInt
            val box = variables(31, locals).single { it["name"].asString == "box" }
            val fields = variables(32, box["variablesReference"].asInt)
            assertThat(fields.map { it["name"].asString to (it["presentationHint"]?.asJsonObject?.get("lazy")?.asBoolean == true) })
                .containsExactly("count" to false, "toString()" to true)
            assertThat(fields.last()["value"].asString).isEqualTo("(...)")
            assertThat(variables(33, fields.last()["variablesReference"].asInt).map { it["name"].asString to it["value"].asString })
                .containsExactly("toString()" to "\"Box\"")

            // a request's hex format shows the integers in hexadecimal
            sessions.request(id, 34, "variables", """{"variablesReference": $locals, "format": {"hex": true}}""")
            val hex =
                client
                    .until(id) { it.response == "variables" }
                    .last()["body"]
                    .asJsonObject["variables"]
                    .asJsonArray
                    .map { it.asJsonObject }
                    .associate { it["name"].asString to it["value"].asString }
            assertThat(hex["n"]).isEqualTo("0x5")
            assertThat(hex["shown"]).isEqualTo("0x6")

            // a request's filter, start and count reach the program: a frame's locals are named, a part at a time
            val names = variables(37, locals).map { it["name"].asString }
            sessions.request(id, 35, "variables", """{"variablesReference": $locals, "filter": "named", "start": 1, "count": 1}""")
            assertThat(
                client
                    .until(id) { it.response == "variables" }
                    .last()["body"]
                    .asJsonObject["variables"]
                    .asJsonArray
                    .map { it.asJsonObject["name"].asString },
            ).containsExactly(names[1])
            // a local takes an expression's value, which the program goes on with; show() has used n already
            sessions.request(id, 38, "setVariable", """{"variablesReference": $locals, "name": "n", "value": "n * 3"}""")
            val changed = client.until(id) { it.response == "setVariable" }.last()
            assertThat(changed["success"].asBoolean).isTrue()
            assertThat(changed["body"].asJsonObject["value"].asString).isEqualTo("15")
            // the console completes the name typed after a dot with the members of the value before it
            sessions.request(id, 39, "completions", """{"frameId": $frameId, "text": "box.co", "column": 7}""")
            assertThat(
                client
                    .until(id) { it.response == "completions" }
                    .last()["body"]
                    .asJsonObject["targets"]
                    .asJsonArray
                    .map { it.asJsonObject }
                    .map { Triple(it["label"].asString, it["type"].asString, it["length"].asInt) },
            ).containsExactly(Triple("count", "field", 2))
            sessions.request(id, 36, "variables", """{"variablesReference": $locals, "filter": "indexed"}""")
            assertThat(
                client
                    .until(id) { it.response == "variables" }
                    .last()["body"]
                    .asJsonObject["variables"]
                    .asJsonArray,
            ).isEmpty()

            // a run that loops ends when it is cancelled, and at the console's time limit when it is not
            sessions.request(id, 8, "evaluate", """{"expression": "spin()", "frameId": $frameId, "context": "repl"}""")
            sessions.request(id, 9, "cancel", """{"requestId": 8}""")
            val cancelled = client.until(id) { it.response == "evaluate" }.last()
            assertThat(cancelled["success"].asBoolean).isFalse()
            assertThat(cancelled["message"].asString).isEqualTo("cancelled")
            sessions.request(id, 10, "evaluate", """{"expression": "spin()", "frameId": $frameId, "context": "repl"}""")
            val ended = client.until(id) { it.response == "evaluate" && it["request_seq"].asInt == 10 }.last()
            assertThat(ended["success"].asBoolean).isFalse()
            assertThat(ended["message"].asString).isEqualTo("spin() did not finish within 2 seconds, so it was ended.")

            // the program goes on from its stop, with the console's change
            sessions.request(id, 11, "continue", """{"threadId": $thread}""")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(11)
            assertThat(rest.filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString })
                .contains("after 5")
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `a launch with evaluationRunsProgram lets a console line's code wait on work that another fiber does`() {
        CompilerTestSupport.configure()
        val module = source("Program.x", PROGRAM.trimIndent())
        val path = directory.resolve("Program.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Program", module)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc", "supportsInvalidatedEvent": true}""")
            sessions.request(id, 2, "launch", """{"module": "Program", "moduleUri": "$module", "evaluationRunsProgram": true}""")
            sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 18}]}""")
            sessions.request(id, 4, "configurationDone")
            val thread =
                client
                    .until(id) { it.event == "stopped" }
                    .last()["body"]
                    .asJsonObject["threadId"]
                    .asInt
            sessions.request(id, 5, "stackTrace", """{"threadId": $thread}""")
            val frameId =
                client
                    .until(id) { it.response == "stackTrace" }
                    .last()["body"]
                    .asJsonObject["stackFrames"]
                    .asJsonArray
                    .first()
                    .asJsonObject["id"]
                    .asInt

            // the worker's service answers once its own fiber, which the stop held, has done its work
            sessions.request(id, 6, "evaluate", """{"expression": "worker.finished()", "frameId": $frameId, "context": "repl"}""")
            val finished = client.until(id) { it.response == "evaluate" }
            assertThat(finished.last()["body"].asJsonObject["result"].asString).isEqualTo("200000")
            val invalidated = finished.firstOrNull { it.event == "invalidated" } ?: client.until(id) { it.event == "invalidated" }.last()
            assertThat(invalidated["body"].asJsonObject["areas"].asJsonArray.map { it.asString }).containsExactly("all")

            sessions.request(id, 7, "continue", """{"threadId": $thread}""")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(200_000 % 256)
            assertThat(rest.filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString })
                .contains("done 200000")
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `a stopped program steps into a call and out of it, and continue runs it to its exit`() {
        CompilerTestSupport.configure()
        val stops = source("Stops.x", STOPS.trimIndent())
        val path = directory.resolve("Stops.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Stops", stops)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Stops", "moduleUri": "$stops"}""")
            sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 5}]}""")
            sessions.request(id, 4, "configurationDone")
            val thread =
                client
                    .until(id) { it.event == "stopped" }
                    .last()["body"]
                    .asJsonObject["threadId"]
                    .asInt
            var request = 5

            /** Steps the fiber and returns the reason of the stop where the step ends, and its stack. */
            fun step(command: String): Pair<String, List<Pair<String, Int>>> {
                sessions.request(id, request++, command, """{"threadId": $thread}""")
                val stopped = client.until(id) { it.event == "stopped" }
                assertThat(stopped.single { it.response == command }["success"].asBoolean).isTrue()
                sessions.request(id, request++, "stackTrace", """{"threadId": $thread}""")
                val frames =
                    client
                        .until(id) { it.response == "stackTrace" }
                        .last()["body"]
                        .asJsonObject["stackFrames"]
                        .asJsonArray
                        .map { it.asJsonObject["name"].asString to it.asJsonObject["line"].asInt }
                return stopped.last()["body"].asJsonObject["reason"].asString to frames.take(2)
            }

            assertThat(step("stepIn")).isEqualTo("step" to listOf("Stops.twice" to 11, "Stops.run" to 5))
            assertThat(step("stepOut").let { (reason, frames) -> reason to frames.first() }).isEqualTo("step" to ("Stops.run" to 6))

            sessions.request(id, request, "continue", """{"threadId": $thread}""")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(42)
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `a conditional breakpoint, a logpoint, an exception class and exceptionInfo work on a real program`() {
        CompilerTestSupport.configure()
        val logs = source("Logs.x", LOGS.trimIndent())
        val path = directory.resolve("Logs.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Logs", logs)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Logs", "moduleUri": "$logs"}""")
            sessions.request(
                id,
                3,
                "setBreakpoints",
                """{"source": {"path": "$path"}, "breakpoints": [""" +
                    """{"line": 6, "condition": "even", "hitCondition": "2"}, {"line": 8, "logMessage": "sum={sum}"}]}""",
            )
            sessions.request(
                id,
                4,
                "setExceptionBreakpoints",
                """{"filters": [], "filterOptions": [{"filterId": "uncaught", "condition": "IllegalArgument"}]}""",
            )
            sessions.request(id, 5, "configurationDone")

            // the second hit where i is even
            val stopped = client.until(id) { it.event == "stopped" }.last()["body"].asJsonObject
            assertThat(stopped["reason"].asString).isEqualTo("breakpoint")
            val thread = stopped["threadId"].asInt
            sessions.request(id, 6, "stackTrace", """{"threadId": $thread}""")
            val frame =
                client
                    .until(id) { it.response == "stackTrace" }
                    .last()["body"]
                    .asJsonObject["stackFrames"]
                    .asJsonArray
                    .first()
                    .asJsonObject["id"]
                    .asInt
            sessions.request(id, 7, "evaluate", """{"expression": "i", "frameId": $frame, "context": "watch"}""")
            assertThat(
                client
                    .until(id) { it.response == "evaluate" }
                    .last()["body"]
                    .asJsonObject["result"]
                    .asString,
            ).isEqualTo("2")

            // the logpoint logs to the console, and the uncaught IllegalArgument stops the program
            sessions.request(id, 8, "continue", """{"threadId": $thread}""")
            val untilException = client.until(id) { it.event == "stopped" }
            val logged = untilException.filter { it.event == "output" }.map { it["body"].asJsonObject }
            assertThat(
                logged.filter { it["category"].asString == "console" }.joinToString("") { it["output"].asString },
            ).contains("sum=6\n")
            val exception = untilException.last()["body"].asJsonObject
            assertThat(exception["reason"].asString).isEqualTo("exception")
            sessions.request(id, 9, "exceptionInfo", """{"threadId": ${exception["threadId"].asInt}}""")
            val info = client.until(id) { it.response == "exceptionInfo" }.last()["body"].asJsonObject
            assertThat(listOf(info["exceptionId"].asString, info["description"].asString, info["breakMode"].asString))
                .containsExactly("IllegalArgument", "bad 6", "unhandled")
            assertThat(info["details"].asJsonObject["stackTrace"].asString).contains("check").doesNotContain("runner.x")

            sessions.request(id, 10, "continue", """{"threadId": ${exception["threadId"].asInt}}""")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(1)
        }
    }

    @Test
    fun `an uncaught exception stops a debugged program where it is thrown, with the exception among the variables`() {
        CompilerTestSupport.configure()
        val throws = source("Throws.x", THROWS.trimIndent())
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Throws", throws)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Throws", "moduleUri": "$throws"}""")
            sessions.request(id, 3, "setExceptionBreakpoints", """{"filters": ["uncaught"]}""")
            sessions.request(id, 4, "configurationDone")
            val stopped =
                client
                    .until(id) { it.event == "stopped" }
                    .last()["body"]
                    .asJsonObject
            assertThat(stopped["reason"].asString).isEqualTo("exception")
            assertThat(stopped["text"].asString).startsWith("IllegalArgument").endsWith(": uncaught one")
            val thread = stopped["threadId"].asInt

            sessions.request(id, 5, "stackTrace", """{"threadId": $thread}""")
            val top =
                client
                    .until(id) { it.response == "stackTrace" }
                    .last()["body"]
                    .asJsonObject["stackFrames"]
                    .asJsonArray
                    .first()
                    .asJsonObject
            assertThat(top["name"].asString to top["line"].asInt).isEqualTo("Throws.check" to 8)
            sessions.request(id, 6, "scopes", """{"frameId": ${top["id"].asInt}}""")
            val reference =
                client
                    .until(id) { it.response == "scopes" }
                    .last()["body"]
                    .asJsonObject["scopes"]
                    .asJsonArray
                    .single()
                    .asJsonObject["variablesReference"]
                    .asInt
            sessions.request(id, 7, "variables", """{"variablesReference": $reference}""")
            val first =
                client
                    .until(id) { it.response == "variables" }
                    .last()["body"]
                    .asJsonObject["variables"]
                    .asJsonArray
                    .first()
                    .asJsonObject
            assertThat(first["name"].asString).isEqualTo("<exception>")
            assertThat(first["type"].asString).contains("IllegalArgument")

            // the program goes on to fail with the exception, without stopping again
            sessions.request(id, 8, "continue", """{"threadId": $thread}""")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.none { it.event == "stopped" }).isTrue()
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isNotZero()
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `a debugged program stops on entry in its own code, and pause stops it while it runs`() {
        CompilerTestSupport.configure()
        val spins = source("Spins.x", SPINS.trimIndent())
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Spins", spins)))
            val sessions = DebugSessions(EmbeddedRuns(adapter, { client }, worker), { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Spins", "moduleUri": "$spins", "stopOnEntry": true}""")
            sessions.request(id, 3, "configurationDone")
            val entry = client.until(id) { it.event == "stopped" }
            val stopped = entry.last()["body"].asJsonObject
            assertThat(stopped["reason"].asString).isEqualTo("entry")
            assertThat(entry.filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString })
                .doesNotContain("spinning")
            val thread = stopped["threadId"].asInt

            /** The innermost frame of the stopped fiber, as name and line. */
            fun top(request: Int): Pair<String, Int> {
                sessions.request(id, request, "stackTrace", """{"threadId": $thread}""")
                val frame =
                    client
                        .until(id) { it.response == "stackTrace" }
                        .last()["body"]
                        .asJsonObject["stackFrames"]
                        .asJsonArray
                        .first()
                        .asJsonObject
                return frame["name"].asString to frame["line"].asInt
            }
            assertThat(top(4).first).isEqualTo("Spins.run")

            sessions.request(id, 5, "continue", """{"threadId": $thread}""")
            client.until(id) { it.event == "output" && "spinning" in it["body"].asJsonObject["output"].asString }
            sessions.request(id, 6, "pause", """{"threadId": $thread}""")
            val paused = client.until(id) { it.event == "stopped" }
            assertThat(paused.single { it.response == "pause" }["success"].asBoolean).isTrue()
            assertThat(paused.last()["body"].asJsonObject["reason"].asString).isEqualTo("pause")
            assertThat(top(7)).isIn("Spins.run" to 6, "Spins.run" to 7)

            // the program never ends on its own; terminating the session ends it where it is
            sessions.request(id, 8, "terminate")
            client.until(id) { it.event == "terminated" }
            sessions.end(DebugSessionParams(id))
        }
    }

    @Test
    fun `terminating a debug session while its program is stopped ends the program`() {
        CompilerTestSupport.configure()
        val halts = source("Halts.x", STOPS.trimIndent().replace("Stops", "Halts"))
        val path = directory.resolve("Halts.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Halts", halts)))
            val runs = EmbeddedRuns(adapter, { client }, worker)
            val sessions = DebugSessions(runs, { client })
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Halts"}""")
            sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 11}]}""")
            sessions.request(id, 4, "configurationDone")
            val untilStop = client.until(id) { it.event == "stopped" }

            sessions.request(id, 5, "terminate")
            val rest = client.until(id) { it.event == "terminated" }
            assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(1)
            // the program ended where it stopped, without running on to its end
            val output = (untilStop + rest).filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString }
            assertThat(output).contains("before").doesNotContain("after")
            soon(30).until { runs.tracked() == 0 }
            sessions.end(DebugSessionParams(id))
        }
        assertThat(run("Exit").code).isEqualTo(42)
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
    @Tag("heavy")
    fun `repeated builds, runs, stops and debug sessions on one worker leave no run, session, thread or process behind`() {
        CompilerTestSupport.configure()
        val soak = source("Soak.x", soak(0))
        val path = directory.resolve("Soak.x").toString()
        val client = SoakClient()
        val reports = mutableListOf<RunWorkerProcess.Usage>()
        val pids = mutableSetOf<Long>()
        val debugged = mutableListOf<String>()
        // the heap a worker reports is what the JVM left after collections it runs when it chooses to, so no heap use
        // retires this worker; what the soak checks is counted
        val counted = RunWorkerProcess.Limits(heap = Long.MAX_VALUE)
        RunWorkerProcess(limits = counted).use { workers ->
            XdkAdapter().use { adapter ->
                adapter.initializeWorkspace(listOf(directory.toString()))
                adapter.replaceSourceModules(listOf(XdkSourceModule("Soak", soak)))
                val runs = EmbeddedRuns(adapter, { client }, workers)
                val sessions = DebugSessions(runs, { client })

                // what the worker kept after the run that just ended, which it reports after the run's exit
                fun measured() {
                    soon(30).until {
                        workers
                            .usage()
                            .values
                            .singleOrNull()
                            ?.run
                            .let { it != null && it != reports.lastOrNull()?.run }
                    }
                    reports += workers.usage().values.single()
                    pids += workers.workerPids()
                }

                // a debug session of the module, stopped at its breakpoint in twice(): its session ID, thread and frame
                fun stopped(round: Int): Triple<String, Int, Int> {
                    val id = sessions.start().sessionId.also(debugged::add)
                    sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
                    sessions.request(id, 2, "launch", """{"module": "Soak", "moduleUri": "$soak"}""")
                    sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 14}]}""")
                    sessions.request(id, 4, "configurationDone")
                    val stop = client.until(id) { it.event == "stopped" }
                    assertThat(stop.filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString })
                        .contains("round $round\n")
                    val thread = stop.last()["body"].asJsonObject["threadId"].asInt
                    sessions.request(id, 5, "stackTrace", """{"threadId": $thread}""")
                    val frame =
                        client
                            .until(id) { it.response == "stackTrace" }
                            .last()["body"]
                            .asJsonObject["stackFrames"]
                            .asJsonArray
                            .first()
                            .asJsonObject
                    assertThat(frame["name"].asString to frame["line"].asInt).isEqualTo("Soak.twice" to 14)
                    return Triple(id, thread, frame["id"].asInt)
                }

                for (round in 1..SOAK_ROUNDS) {
                    // an edit, which the build compiles as the editor's unsaved text, and a run of it to its exit
                    adapter.compile(soak, soak(round))
                    assertThat(runs.run(RunParams(module = "Soak", runId = "exits-$round")).get(60, SECONDS).runId).isNotNull()
                    assertThat(client.exit("exits-$round").get(60, SECONDS).exitCode).isEqualTo(42)
                    assertThat(client.output("exits-$round")).contains("round $round\n", "after 42")
                    measured()

                    // a run stopped while it runs
                    runs.run(RunParams(module = "Soak", args = listOf("spin"), runId = "stops-$round")).get(60, SECONDS)
                    soon(60).until { "round $round\n" in client.output("stops-$round") }
                    assertThat(runs.stop(StopRunParams("stops-$round"))).isTrue()
                    client.exit("stops-$round").get(60, SECONDS)
                    assertThat(client.output("stops-$round")).doesNotContain("after")
                    measured()

                    // a debug session: a watch and a cancelled console run where it stops, then on to its exit
                    stopped(round).let { (id, thread, frame) ->
                        sessions.request(id, 6, "evaluate", """{"expression": "n * 2 + 1", "frameId": $frame, "context": "watch"}""")
                        assertThat(
                            client
                                .until(id) { it.response == "evaluate" }
                                .last()["body"]
                                .asJsonObject["result"]
                                .asString,
                        ).isEqualTo("43")
                        sessions.request(id, 7, "evaluate", """{"expression": "spin()", "frameId": $frame, "context": "repl"}""")
                        sessions.request(id, 8, "cancel", """{"requestId": 7}""")
                        assertThat(client.until(id) { it.response == "evaluate" }.last()["message"].asString).isEqualTo("cancelled")
                        sessions.request(id, 9, "continue", """{"threadId": $thread}""")
                        val rest = client.until(id) { it.event == "terminated" }
                        assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(42)
                        sessions.end(DebugSessionParams(id))
                    }
                    measured()

                    // a debug session terminated while console code runs where it stopped, whose answer then fails
                    stopped(round).let { (id, _, frame) ->
                        sessions.request(id, 6, "evaluate", """{"expression": "spin()", "frameId": $frame, "context": "repl"}""")
                        sessions.request(id, 7, "terminate")
                        val rest = client.until(id) { it.event == "terminated" }
                        assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isEqualTo(1)
                        val answer =
                            rest.singleOrNull { it.response == "evaluate" } ?: client.until(id) { it.response == "evaluate" }.last()
                        assertThat(answer["success"].asBoolean).isFalse()
                        sessions.end(DebugSessionParams(id))
                    }
                    measured()
                }
                println("soak of $SOAK_ROUNDS rounds, the worker's threads after each run: ${reports.map { it.threads }}")

                // the JDK's own pools, such as the common pool's, start threads as they are needed and are not counted
                assertThat(pids).describedAs("one worker served every run").hasSize(1)
                assertThat(reports).hasSize(4 * SOAK_ROUNDS).allSatisfy {
                    assertThat(it.consoles).isZero()
                    assertThat(it.runs).isZero()
                    assertThat(it.poolThreads).isPositive().isEqualTo(reports.first().poolThreads)
                    assertThat(it.threads).isLessThan(counted.threads)
                }
                assertThat(runs.tracked()).isZero()
                assertThat(sessions.tracked()).isZero()
                // each session's message thread ends with its session
                val threads = debugged.map { "xtc-debug-$it" }
                soon(30).until { Thread.getAllStackTraces().keys.none { it.name in threads } }
            }
        }
        // closing the workers ends the worker process
        soon(30).until { pids.none { pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) } }
    }

    @Test
    fun `a run's log lines name its build, its artifact's revision, its worker and generation, and its run ID`() {
        CompilerTestSupport.configure()
        val logged = source("Logged.x", "module Logged { Int run() = 7; }")
        val client = SoakClient()
        val lines = CopyOnWriteArrayList<String>()
        val appender =
            object : AppenderBase<ILoggingEvent>() {
                override fun append(event: ILoggingEvent) {
                    lines += event.formattedMessage
                }
            }.apply { start() }
        val loggers =
            listOf(XdkAdapter::class.java.name, EmbeddedRuns::class.java.name, RunWorkerProcess::class.java.name, "org.xvm.lsp.trace")
                .map { LoggerFactory.getLogger(it) as Logger }
        loggers.forEach { it.addAppender(appender) }
        val pid =
            try {
                RunWorkerProcess().use { workers ->
                    XdkAdapter().use { adapter ->
                        adapter.initializeWorkspace(listOf(directory.toString()))
                        adapter.replaceSourceModules(listOf(XdkSourceModule("Logged", logged)))
                        val runs = EmbeddedRuns(adapter, { client }, workers)
                        assertThat(
                            runs.run(RunParams(module = "Logged", runId = "correlated")).get(60, SECONDS).runId,
                        ).isEqualTo("correlated")
                        assertThat(client.exit("correlated").get(60, SECONDS).exitCode).isEqualTo(7)
                        soon(30).until { lines.any { it.startsWith("execution worker after a run:") } }
                        workers.workerPids().single()
                    }
                }
            } finally {
                loggers.forEach { it.detachAppender(appender) }
                appender.stop()
            }

        // the build is its compiler queue job, which the trace gives by the same ID, and it names the artifact it wrote
        val (build, artifact) =
            Regex("""^embedded build: build=(\d+) .* outcome=succeeded modules=(Logged@[0-9a-f]{12})$""")
                .find(lines.single { it.startsWith("embedded build:") })!!
                .destructured
        assertThat(lines).anyMatch { "\"id\":$build," in it && "\"operation\":\"embedded-build\"" in it }
        // the run names that build and artifact, and the worker, the first this server started, names the run
        val worker = "worker=$pid generation=1"
        assertThat(
            lines,
        ).contains("execution worker started: $worker", "run starting: run=correlated build=$build artifact=$artifact debug=false")
        val workerRun =
            lines.firstNotNullOf {
                Regex("""^run assigned: (workerRun=\d+) run=correlated $worker module=Logged method=run$""").find(it)?.groupValues?.get(1)
            }
        assertThat(lines)
            .contains("run exited: $workerRun run=correlated $worker code=7")
            .anyMatch { it.startsWith("execution worker after a run: $worker $workerRun run=correlated ") }
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

    /** Records each run's output and exit by run ID, and the debug sessions' messages, as [DebugClient] does. */
    private class SoakClient(
        private val debug: DebugClient = DebugClient(),
    ) : XtcLanguageClient by debug {
        private val outputs = ConcurrentHashMap<String, StringBuffer>()
        private val exits = ConcurrentHashMap<String, CompletableFuture<RunExitedParams>>()

        override fun runOutput(params: RunOutputParams) {
            outputs.computeIfAbsent(params.runId) { StringBuffer() }.append(params.text)
        }

        override fun runExited(params: RunExitedParams) {
            exit(params.runId).complete(params)
        }

        fun output(runId: String) = outputs[runId]?.toString().orEmpty()

        fun exit(runId: String): CompletableFuture<RunExitedParams> = exits.computeIfAbsent(runId) { CompletableFuture() }

        fun until(
            sessionId: String,
            last: (JsonObject) -> Boolean,
        ) = debug.until(sessionId, last)
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
        /** Rounds of the soak, each with four runs: five, or `-Plsp.soakRounds` for a longer soak. */
        val SOAK_ROUNDS =
            System.getProperty("xtc.lsp.soakRounds", "5").toInt().also {
                require(it >= 1) { "The soak requires at least one round" }
            }

        /**
         * The soak's module as a round edits it, printing the round: twice() is on lines 13 to 16, and run() runs until it
         * is stopped when it has an argument.
         */
        fun soak(round: Int) =
            """
            module Soak {
                Int run(String[] args) {
                    @Inject Console console;
                    console.print("round $round");
                    Int n = twice(21);
                    if (args.size > 0) {
                        spin();
                    }
                    console.print($"after {n}");
                    return n;
                }

                Int twice(Int n) {
                    Int result = n * 2;
                    return result;
                }

                void spin() {
                    Int count = 0;
                    while (True) {
                        ++count;
                    }
                }
            }
            """.trimIndent()

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

        /** A module whose run() calls a method on line 5; the method's code is on lines 11 and 12. */
        const val STOPS =
            """
            module Stops {
                Int run() {
                    @Inject Console console;
                    console.print("before");
                    Int n = twice(21);
                    console.print($"after {n}");
                    return n;
                }

                Int twice(Int n) {
                    Int result = n * 2;
                    return result;
                }
            }
            """

        /** A module whose run() calls add() and then twice() on line 4; add() is on line 11, twice() on line 15. */
        const val TARGETS =
            """
            module Targets {
                Int run() {
                    @Inject Console console;
                    Int n = twice(add(1, 2));
                    Int m = n > 100 ? twice(n) : n;
                    console.print($"n={n} m={m}");
                    return m;
                }

                Int add(Int a, Int b) {
                    return a + b;
                }

                Int twice(Int n) {
                    return n * 2;
                }
            }
            """

        /**
         * A module whose run() makes a Counter on line 4 and adds 2 and then 3 to its count, on line 16 of Counter.add(),
         * whose next line is 17; it prints "count=5" and returns 5.
         */
        const val WATCHED =
            """
            module Watched {
                Int run() {
                    @Inject Console console;
                    Counter counter = new Counter();
                    counter.add(2);
                    counter.add(3);
                    console.print($"count={counter.count}");
                    return counter.count;
                }

                class Counter {
                    Int count = 0;
                    Int adds  = 0;

                    void add(Int n) {
                        count += n;
                        ++adds;
                    }
                }
            }
            """

        /**
         * A module whose run() calls outer(20) on line 4, which prints "outer 20" on line 11 and calls inner() on line 12,
         * whose code is on line 16; run() returns 42.
         */
        const val RESTARTS =
            """
            module Restarts {
                Int run() {
                    @Inject Console console;
                    Int n = outer(20);
                    console.print($"n={n}");
                    return n;
                }

                Int outer(Int n) {
                    @Inject Console console;
                    console.print($"outer {n}");
                    return inner(n + 1);
                }

                Int inner(Int n) {
                    return n * 2;
                }
            }
            """

        /** A module whose run() calls Collection.forEach, which calls its lambda: line 6 is the lambda's. */
        const val LIBRARY =
            """
            module Library {
                void run() {
                    @Inject Console console;
                    Int[] numbers = [1, 2, 3];
                    numbers.forEach(n -> {
                        console.print($"n={n}");
                    });
                }
            }
            """

        /** A module whose run() prints in a loop, on line 7, three times, and returns the sum 3. */
        const val LOOPS =
            """
            module Loops {
                Int run() {
                    @Inject Console console;
                    Int total = 0;
                    for (Int i = 0; i < 3; ++i) {
                        total += i;
                        console.print($"i={i}");
                    }
                    console.print($"total={total}");
                    return total;
                }
            }
            """

        /** Stops at line 18, in run(), while its Worker counts to 200,000; worker.finished() waits for the count. */
        const val PROGRAM =
            """
            module Program {
                service Worker {
                    Int done;

                    void work() {
                        for (Int i : 0 ..< 200_000) {
                            ++done;
                        }
                    }

                    Int finished() = done;
                }

                Int run() {
                    Worker worker = new Worker();
                    worker.work^();
                    @Inject Console console;
                    console.print("working");
                    Int done = worker.finished();
                    console.print($"done {done}");
                    return done % 256;
                }
            }
            """

        /** Stops at line 21, in show(), where the console runs code: a call with an effect, and one that loops. */
        const val RUNS =
            """
            module Runs {
                class Box {
                    Int count;

                    Int bump(Int by) {
                        count += by;
                        return count;
                    }
                }

                Int run() {
                    @Inject Console console;
                    Box box = new Box();
                    Int n = show(box, 5);
                    console.print($"after {box.count}");
                    return n;
                }

                Int show(Box box, Int n) {
                    Int shown = n + 1;
                    return shown + box.count;
                }

                void spin() {
                    Int count = 0;
                    while (True) {
                        ++count;
                    }
                }
            }
            """

        const val SPINS =
            """
            module Spins {
                void run() {
                    @Inject Console console;
                    console.print("spinning");
                    Int count = 0;
                    while (True) {
                        ++count;
                    }
                }
            }
            """

        const val LOGS =
            """
            module Logs {
                Int run() {
                    Int sum = 0;
                    for (Int i : 0 ..< 4) {
                        Boolean even = i % 2 == 0;
                        sum += i;
                    }
                    return check(sum);
                }

                Int check(Int n) {
                    if (n == 0) {
                        return 0;
                    }
                    throw new IllegalArgument($"bad {n}");
                }
            }
            """

        const val THROWS =
            """
            module Throws {
                Int run() = check(1);

                Int check(Int n) {
                    if (n == 0) {
                        return 0;
                    }
                    throw new IllegalArgument("uncaught one");
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
