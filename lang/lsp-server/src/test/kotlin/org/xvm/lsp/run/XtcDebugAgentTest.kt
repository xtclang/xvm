package org.xvm.lsp.run

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.xvm.debug.BreakpointRequest
import org.xvm.debug.BreakpointState
import org.xvm.debug.DebugSetup
import org.xvm.debug.SourceBreakpoints
import org.xvm.debug.StepKind
import org.xvm.debug.StopState
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.xdk.XdkLibraries
import org.xvm.lsp.server.DebugSessions
import org.xvm.lsp.server.DebugTaskParams
import org.xvm.lsp.server.DebugTaskResult
import org.xvm.lsp.server.EmbeddedRuns
import org.xvm.lsp.server.XtcLanguageClient
import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Programs that the launcher runs with `--debug`, in a JVM of their own from the test classpath, where the launcher
 * finds [XtcDebugAgent] through its service file, as it does in the language server's jar: a debugger attaches over the
 * socket before the program's first line, stops it at a breakpoint, or at its first line, and sees it exit; a detach
 * lets it run on without the breakpoints; a wrong token is refused; the agent connects to a debugger that waits for the
 * program; and with no debugger attached, `assert:debug` does not hold the program. A language server's debug session
 * attaches so too, and its disconnect detaches. A Gradle launch has its client run the program as the task would, with
 * the task's `--debug`: the program connects, stops at a breakpoint and exits; stopping the launch ends the task, and
 * the program with it; and a task that ends before its program connects fails the launch. The runner compiles each
 * program from its source, so its breakpoints are on its source file's lines.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class XtcDebugAgentTest {
    @TempDir lateinit var directory: Path

    /** The bundled XDK's modules, as files the launcher's module path takes. */
    private lateinit var xdk: File

    @BeforeAll
    fun extractXdk() {
        xdk = Files.createTempDirectory("xtc-agent-xdk").toFile()
        XdkLibraries.packagedResources.forEach { name ->
            checkNotNull(javaClass.getResourceAsStream("/org/xvm/lsp/xdk/$name")) { "Missing bundled module: $name" }.use { input ->
                File(xdk, name).outputStream().use(input::copyTo)
            }
        }
    }

    @AfterAll
    fun removeXdk() {
        xdk.deleteRecursively()
    }

    @Test
    fun `a debugger attaches before the first line, stops the program at a breakpoint and sees it exit`() {
        launch(LOOPED, "listen:127.0.0.1:0,suspend").use { program ->
            val events = Events()
            val attached = AttachedProgram.attach("127.0.0.1", program.port(), null, events)
            assertThat(attached.module).isEqualTo("Looped")
            attached.debug(emptyList(), program.breakpointAt(LOOP_LINE)).get(LIMIT, SECONDS)
            assertThat(events.next()).isEqualTo("breakpoints ${program.source} [true]")
            assertThat(events.next()).isEqualTo("stopped breakpoint [1]")

            val thread = attached.threads(AttachedProgram.RUN).get(LIMIT, SECONDS).first()
            assertThat(
                attached
                    .stackTrace(AttachedProgram.RUN, thread.id)
                    .get(LIMIT, SECONDS)
                    .first()
                    .line,
            ).isEqualTo(LOOP_LINE)
            attached.setBreakpoints(AttachedProgram.RUN, SourceBreakpoints(program.source, emptyList())).get(LIMIT, SECONDS)
            attached.resume(AttachedProgram.RUN)

            assertThat(events.next()).isEqualTo("exited 0")
            assertThat(program.exit()).isEqualTo(0)
            assertThat(program.output).contains("sum 3")
        }
    }

    @Test
    fun `a debugger continues and steps one fiber alone, which stops again while the program's other fibers wait`() {
        launch(COUNTERS, "listen:127.0.0.1:0,suspend").use { program ->
            val events = Events()
            val attached = AttachedProgram.attach("127.0.0.1", program.port(), null, events)
            attached.debug(emptyList(), program.firstCounterAt(1000)).get(LIMIT, SECONDS)
            assertThat(events.next()).isEqualTo("breakpoints ${program.source} [true]")
            assertThat(events.next()).isEqualTo("stopped breakpoint [1]")
            val alone =
                attached
                    .threads(AttachedProgram.RUN)
                    .get(LIMIT, SECONDS)
                    .first()
                    .id

            attached.setBreakpoints(AttachedProgram.RUN, program.firstCounterAt(1500).breakpoints.single()).get(LIMIT, SECONDS)
            assertThat(attached.resumeAlone(AttachedProgram.RUN, alone).get(LIMIT, SECONDS)).isTrue()
            assertThat(events.next()).isEqualTo("stopped breakpoint [1]")
            // the fiber that ran alone is the stopped one again
            assertThat(
                attached
                    .threads(AttachedProgram.RUN)
                    .get(LIMIT, SECONDS)
                    .first()
                    .id,
            ).isEqualTo(alone)
            attached.setBreakpoints(AttachedProgram.RUN, SourceBreakpoints(program.source, emptyList())).get(LIMIT, SECONDS)
            assertThat(attached.step(AttachedProgram.RUN, alone, StepKind.OVER, alone = true).get(LIMIT, SECONDS)).isTrue()
            assertThat(events.next()).isEqualTo("stopped step []")

            attached.resume(AttachedProgram.RUN)
            assertThat(events.next()).isEqualTo("exited 0")
            assertThat(program.exit()).isEqualTo(0)
            assertThat(program.output).contains("sum 5000000")
        }
    }

    @Test
    fun `a debug session attaches to the program, stops it at a breakpoint, and once it disconnects the program runs on`() {
        launch(LOOPED, "listen:127.0.0.1:0,suspend").use { program ->
            val client = DebugClient()
            val runs = EmbeddedRuns(mock(Adapter::class.java), { client })
            DebugSessions(runs, { client }).use { sessions ->
                val id = sessions.start().sessionId
                sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
                sessions.request(id, 2, "attach", """{"port": ${program.port()}}""")
                sessions.request(
                    id,
                    3,
                    "setBreakpoints",
                    """{"source": {"path": "${program.source}"}, "breakpoints": [{"line": $LOOP_LINE}]}""",
                )
                sessions.request(id, 4, "configurationDone")
                val untilStop = client.until(id) { it.event == "stopped" }
                assertThat(untilStop.single { it.response == "attach" }["success"].asBoolean).isTrue()
                val installed = untilStop.single { it.event == "breakpoint" }["body"].asJsonObject["breakpoint"].asJsonObject
                assertThat(installed["verified"].asBoolean).isTrue()
                val stopped = untilStop.last()["body"].asJsonObject
                assertThat(stopped["reason"].asString).isEqualTo("breakpoint")

                sessions.request(id, 5, "stackTrace", """{"threadId": ${stopped["threadId"].asInt}}""")
                val frames =
                    client
                        .until(id) { it.response == "stackTrace" }
                        .last()["body"]
                        .asJsonObject["stackFrames"]
                        .asJsonArray
                assertThat(frames.first().asJsonObject["line"].asInt).isEqualTo(LOOP_LINE)

                sessions.request(id, 6, "disconnect")
                val end = client.until(id) { it.event == "terminated" }
                assertThat(end.filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString })
                    .contains("Detached from Looped, which runs on.")
                assertThat(end.none { it.event == "exited" }).isTrue()
                assertThat(program.exit()).isEqualTo(0)
                assertThat(program.output).contains("sum 3")
                assertThat(runs.tracked()).isZero()
            }
        }
    }

    @Test
    fun `a Gradle launch has the client run the program, which connects, stops at a breakpoint and exits`() {
        GradleClient(LOOPED).use { client ->
            val runs = EmbeddedRuns(mock(Adapter::class.java), { client })
            DebugSessions(runs, { client }).use { sessions ->
                val id = sessions.start().sessionId
                val source = directory.resolve("Looped.x").toFile().canonicalPath
                sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
                sessions.request(id, 2, "launch", """{"module": "Looped", "buildMode": "gradle"}""")
                sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$source"}, "breakpoints": [{"line": $LOOP_LINE}]}""")
                sessions.request(id, 4, "configurationDone")
                val untilStop = client.messages.until(id) { it.event == "stopped" }
                assertThat(untilStop.single { it.response == "launch" }["success"].asBoolean).isTrue()
                val task = client.tasks.single()
                assertThat(task.sessionId).isEqualTo(id)
                assertThat(task.module).isEqualTo("Looped")
                assertThat(task.debug).matches("connect:127\\.0\\.0\\.1:\\d+,suspend,token=[-0-9a-f]+")
                val stopped = untilStop.last()["body"].asJsonObject
                assertThat(stopped["reason"].asString).isEqualTo("breakpoint")

                sessions.request(id, 5, "setBreakpoints", """{"source": {"path": "$source"}, "breakpoints": []}""")
                sessions.request(id, 6, "continue", """{"threadId": ${stopped["threadId"].asInt}}""")
                val end = client.messages.until(id) { it.event == "terminated" }
                assertThat(end.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isZero()
                val program = client.programs.single()
                assertThat(program.exit()).isEqualTo(0)
                assertThat(program.output).contains("sum 3")
                assertThat(runs.tracked()).isZero()
            }
        }
    }

    @Test
    fun `stopping a Gradle launch ends the task, and the program with it`() {
        GradleClient(LOOPED).use { client ->
            val runs = EmbeddedRuns(mock(Adapter::class.java), { client })
            DebugSessions(runs, { client }).use { sessions ->
                val id = sessions.start().sessionId
                val source = directory.resolve("Looped.x").toFile().canonicalPath
                sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
                sessions.request(id, 2, "launch", """{"module": "Looped", "buildMode": "gradle"}""")
                sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$source"}, "breakpoints": [{"line": $LOOP_LINE}]}""")
                sessions.request(id, 4, "configurationDone")
                client.messages.until(id) { it.event == "stopped" }

                sessions.request(id, 5, "disconnect")
                val end = client.messages.until(id) { it.event == "terminated" }
                assertThat(end.filter { it.event == "output" }.joinToString("") { it["body"].asJsonObject["output"].asString })
                    .contains(EmbeddedRuns.STOPPED_WITH_TASK)
                assertThat(client.programs.single().exit()).isNotZero()
                assertThat(runs.tracked()).isZero()
            }
        }
    }

    @Test
    fun `a Gradle launch whose task ends before the program connects fails with the task's exit code`() {
        val messages = DebugClient()
        val client =
            object : XtcLanguageClient by messages {
                override fun debugTask(params: DebugTaskParams) = CompletableFuture.completedFuture(DebugTaskResult(3))
            }
        val runs = EmbeddedRuns(mock(Adapter::class.java), { client })
        DebugSessions(runs, { client }).use { sessions ->
            val id = sessions.start().sessionId
            sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
            sessions.request(id, 2, "launch", """{"module": "Looped", "buildMode": "gradle"}""")
            sessions.request(id, 3, "configurationDone")
            val launch = messages.until(id) { it.response == "launch" }.last()
            assertThat(launch["success"].asBoolean).isFalse()
            assertThat(launch["message"].asString)
                .isEqualTo("Run failed: Gradle ended with exit code 3 before the program started; its output says why.")
            assertThat(runs.tracked()).isZero()
        }
    }

    @Test
    fun `a detach lets the program run on without the debugger's breakpoints`() {
        launch(LOOPED, "listen:127.0.0.1:0,suspend").use { program ->
            val events = Events()
            val attached = AttachedProgram.attach("127.0.0.1", program.port(), null, events)
            attached.debug(emptyList(), program.breakpointAt(LOOP_LINE)).get(LIMIT, SECONDS)
            assertThat(events.next()).startsWith("breakpoints")
            assertThat(events.next()).startsWith("stopped")

            attached.detach()
            assertThat(events.next()).isEqualTo("detached null")
            assertThat(program.exit()).isEqualTo(0)
            assertThat(program.output).contains("sum 3")
        }
    }

    @Test
    fun `a debugger that asks for it stops a waiting program at its first line`() {
        launch(LOOPED, "listen:127.0.0.1:0,suspend").use { program ->
            val events = Events()
            val attached = AttachedProgram.attach("127.0.0.1", program.port(), null, events)
            attached.debug(emptyList(), DebugSetup(emptyList(), stopOnEntry = true)).get(LIMIT, SECONDS)
            assertThat(events.next()).isEqualTo("stopped entry []")
            val thread = attached.threads(AttachedProgram.RUN).get(LIMIT, SECONDS).first()
            assertThat(
                attached
                    .stackTrace(AttachedProgram.RUN, thread.id)
                    .get(LIMIT, SECONDS)
                    .first()
                    .line,
            ).isEqualTo(FIRST_LINE)

            attached.resume(AttachedProgram.RUN)
            assertThat(events.next()).isEqualTo("exited 0")
            assertThat(program.exit()).isEqualTo(0)
        }
    }

    @Test
    fun `a debugger without the program's token is refused, and the program waits for one with it`() {
        launch(LOOPED, "listen:127.0.0.1:0,suspend,token=secret").use { program ->
            val port = program.port()
            assertThatThrownBy { AttachedProgram.attach("127.0.0.1", port, "guess", Events()) }
                .isInstanceOf(IOException::class.java)
                .hasMessageContaining("The token does not match")

            val events = Events()
            AttachedProgram.attach("127.0.0.1", port, "secret", events).debug(emptyList(), DebugSetup(emptyList())).get(LIMIT, SECONDS)
            assertThat(events.next()).isEqualTo("exited 0")
            assertThat(program.exit()).isEqualTo(0)
        }
    }

    @Test
    fun `the agent connects to a debugger that waits for the program, and a command it cannot take fails alone`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            launch(LOOPED, "connect:127.0.0.1:${server.localPort},suspend").use { program ->
                server.soTimeout = (LIMIT * 1000).toInt()
                server.accept().use { socket ->
                    socket.soTimeout = (LIMIT * 1000).toInt()
                    val out = PrintStream(socket.getOutputStream(), true, Charsets.UTF_8)
                    val lines = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                    out.println("""{"command":"attach","id":7}""")
                    val ready = JsonParser.parseString(lines.readLine()).asJsonObject
                    assertThat(ready["event"].asString).isEqualTo("ready")
                    assertThat(ready["protocol"].asInt).isEqualTo(RunWorkerIdentity.PROTOCOL)
                    assertThat(ready["module"].asString).isEqualTo("Looped")

                    // a blank line and a command without its request change nothing: the debugger stays attached
                    out.println()
                    out.println("""{"command":"threads","id":7}""")
                    out.println("""{"command":"debug","id":7}""")
                    val events = generateSequence { lines.readLine() }.map { JsonParser.parseString(it).asJsonObject }.toList()
                    assertThat(events.map { it.name() }).containsExactly("log", "started 7", "exit 7")
                    assertThat(events.first()["text"].asString).startsWith("The debugger command threads failed")
                    assertThat(events.last()["code"].asInt).isEqualTo(0)
                }
                assertThat(program.exit()).isEqualTo(0)
            }
        }
    }

    @Test
    fun `with no debugger attached, assert debug does not hold the program`() {
        launch(BREAKS, "listen:127.0.0.1:0").use { program ->
            program.port()
            assertThat(program.exit()).isEqualTo(0)
            assertThat(program.output).contains("before", "after")
        }
    }

    @Test
    fun `the options name a loopback address to listen at or connect to`() {
        val options = AgentOptions.of("listen:localhost:5005,suspend,token=t")
        assertThat(options.listen).isTrue()
        assertThat(options.address.isLoopbackAddress).isTrue()
        assertThat(options.port).isEqualTo(5005)
        assertThat(options.suspend).isTrue()
        assertThat(options.token).isEqualTo("t")
        assertThat(AgentOptions.of("connect:127.0.0.1:6000").listen).isFalse()

        assertThatThrownBy { AgentOptions.of("listen:192.0.2.1:5005") }.hasMessageContaining("is not a loopback address")
        assertThatThrownBy { AgentOptions.of("connect:127.0.0.1:0") }.hasMessageContaining("not 0")
        assertThatThrownBy { AgentOptions.of("attach:127.0.0.1:5005") }.hasMessage(AgentOptions.USAGE)
        assertThatThrownBy { AgentOptions.of("listen:127.0.0.1") }.hasMessage(AgentOptions.USAGE)
        assertThatThrownBy { AgentOptions.of("listen:127.0.0.1:5005,quiet") }.hasMessageContaining("Unknown --debug option 'quiet'")
    }

    /** Runs a program from its source with `--debug` and the [options] given, in a JVM of its own. */
    private fun launch(
        text: String,
        options: String,
    ): Program {
        val name = Regex("module (\\w+)").find(text)!!.groupValues[1]
        val source = directory.resolve("$name.x").toFile().apply { writeText(text.trimIndent()) }
        val process =
            ProcessBuilder(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElse("java"),
                "-cp",
                System.getProperty("java.class.path"),
                "org.xvm.tool.Launcher",
                "run",
                "-L",
                xdk.path,
                "-o",
                directory.toString(),
                "--debug",
                options,
                source.path,
            ).redirectErrorStream(true).start()
        return Program(process, source.canonicalPath)
    }

    /** A program's JVM, its output lines as they come, and its source file. */
    private class Program(
        private val process: Process,
        val source: String,
    ) : AutoCloseable {
        private val lines = LinkedBlockingQueue<String>()
        private val seen = CopyOnWriteArrayList<String>()

        init {
            Thread.ofVirtual().start {
                process.inputReader(Charsets.UTF_8).lineSequence().forEach { line ->
                    seen += line
                    lines += line
                }
            }
        }

        /** Everything the program printed so far. */
        val output: String get() = seen.joinToString("\n")

        /** The port the agent listens on, as it printed it. */
        fun port(): Int {
            while (true) {
                val line = lines.poll(LIMIT, SECONDS) ?: error("The agent did not listen within $LIMIT seconds:\n$output")
                LISTENING.find(line)?.let { return it.groupValues[1].toInt() }
            }
        }

        fun breakpointAt(line: Int) = DebugSetup(listOf(SourceBreakpoints(source, listOf(BreakpointRequest(1, line)))))

        /** A breakpoint on the line where the first of Counters' counters counts, at the specified count. */
        fun firstCounterAt(count: Int) =
            DebugSetup(listOf(SourceBreakpoints(source, listOf(BreakpointRequest(1, COUNT_LINE, "n == $count && limit == 2000000")))))

        fun exit(): Int {
            check(process.waitFor(LIMIT, SECONDS)) { "The program did not end within $LIMIT seconds:\n$output" }
            return process.exitValue()
        }

        override fun close() {
            process.destroyForcibly()
        }
    }

    /**
     * A client that runs each debug task as a program from [text]'s source, with the task's `--debug`, and answers with
     * the program's exit; canceling a task ends its program, as an editor's Gradle run ends. [messages] has the DAP
     * messages of the sessions.
     */
    private inner class GradleClient(
        private val text: String,
        val messages: DebugClient = DebugClient(),
    ) : XtcLanguageClient by messages,
        AutoCloseable {
        val tasks = CopyOnWriteArrayList<DebugTaskParams>()
        val programs = CopyOnWriteArrayList<Program>()

        override fun debugTask(params: DebugTaskParams): CompletableFuture<DebugTaskResult> {
            tasks += params
            val program = launch(text, params.debug)
            programs += program
            val task = CompletableFuture.supplyAsync { DebugTaskResult(program.exit()) }
            task.whenComplete { _, failure -> if (failure != null) program.close() }
            return task
        }

        override fun close() = programs.forEach(Program::close)
    }

    /** A run's events, each in words, in order. */
    private class Events : RunListener {
        private val events = LinkedBlockingQueue<String>()

        fun next(): String = events.poll(LIMIT, SECONDS) ?: error("No event within $LIMIT seconds")

        override fun output(text: String) {
            events += "output $text"
        }

        override fun exited(
            code: Long?,
            error: String?,
        ) {
            events += "exited ${code ?: error}"
        }

        override fun breakpoints(
            path: String,
            states: List<BreakpointState>,
        ) {
            events += "breakpoints $path ${states.map { it.verified }}"
        }

        override fun stopped(stop: StopState) {
            events += "stopped ${stop.reason} ${stop.breakpointIds}"
        }

        override fun detached(reason: String?) {
            events += "detached $reason"
        }
    }

    private fun JsonObject.name() = listOfNotNull(this["event"].asString, this["id"]?.asLong).joinToString(" ")

    private companion object {
        const val LIMIT = 60L

        val LISTENING = Regex("Listening for the debugger at [^ ]+:(\\d+)$")

        /** A module whose run() adds in a loop on line 6 and prints the sum. */
        const val LOOPED = """
            module Looped {
                void run() {
                    @Inject Console console;
                    Int sum = 0;
                    for (Int i : 0 ..< 3) {
                        sum += i;
                    }
                    console.print($"sum {sum}");
                }
            }
            """

        const val LOOP_LINE = 6

        /** The first line of Looped's code: its injection. */
        const val FIRST_LINE = 3

        /**
         * A module whose run() has two Counter services count at once, one to 2,000,000 and one to 3,000,000, on line
         * 7, and prints the sum of their counts once both are done.
         */
        const val COUNTERS = """
            module Counters {
                service Counter {
                    Int n;

                    void count(Int limit) {
                        for (Int i : 0 ..< limit) {
                            ++n;
                        }
                    }

                    Int counted() = n;
                }

                void run() {
                    Counter first  = new Counter();
                    Counter second = new Counter();
                    first.count^(2_000_000);
                    second.count^(3_000_000);
                    @Inject Console console;
                    console.print($"sum {first.counted() + second.counted()}");
                }
            }
            """

        const val COUNT_LINE = 7

        const val BREAKS = """
            module Breaks {
                void run() {
                    @Inject Console console;
                    console.print("before");
                    assert:debug;
                    console.print("after");
                }
            }
            """
    }
}
