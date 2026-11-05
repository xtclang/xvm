package org.xvm.lsp.run

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.xvm.debug.TestEvent
import org.xvm.debug.TestSelector
import org.xvm.lsp.Slow
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.XdkTest
import org.xvm.lsp.awaitUntil
import org.xvm.lsp.awaited
import org.xvm.lsp.server.DebugSessionParams
import org.xvm.lsp.server.DebugSessions
import org.xvm.lsp.server.EmbeddedRuns
import org.xvm.lsp.server.RunExitedParams
import org.xvm.lsp.server.RunOutputParams
import org.xvm.lsp.server.RunParams
import org.xvm.lsp.server.StopRunParams
import org.xvm.lsp.server.TestEventParams
import org.xvm.lsp.server.XtcLanguageClient
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue

/** A module's tests, as the editors find them, and their runs with xunit, with and without the debugger. */
class EmbeddedTestRunsTest {
    @TempDir lateinit var directory: Path

    @BeforeEach
    fun canonicalDirectory() {
        directory = directory.toRealPath()
        CompilerTestSupport.configure()
    }

    @Test
    fun `a module's tests are found under it and its classes, each with the selector that runs it alone`() {
        val mathy = source("Mathy.x", MATHY)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Mathy", mathy)))
            assertThat(adapter.compile(mathy, MATHY).success).isTrue()

            val module = adapter.tests().single()
            assertThat(module.module).isEqualTo("Mathy")
            assertThat(module.uri).isEqualTo(mathy)
            val root = module.tests
            assertThat(root.kind).isEqualTo(XdkTest.MODULE)
            assertThat(root.children.map { Triple(it.selector, it.name, it.kind) }).containsExactly(
                Triple("adds", "adds", XdkTest.TEST),
                Triple("fails", "fails", XdkTest.TEST),
                Triple("Inner", "Inner", XdkTest.CLASS),
            )
            assertThat(
                root.children
                    .last()
                    .children
                    .map { it.selector },
            ).containsExactly("Inner.works")
            // each at its declaration's name, on its zero-based line
            assertThat(root.children.map { it.location?.startLine }).containsExactly(2, 7, 12)
            assertThat(
                root.children
                    .last()
                    .children
                    .single()
                    .location
                    ?.startLine,
            ).isEqualTo(14)

            // as the client reads them: the protocol's JSON writes an enum as its ordinal, so the kinds are strings
            val json =
                MessageJsonHandler(emptyMap())
                    .gson
                    .toJsonTree(module)
                    .asJsonObject["tests"]
                    .asJsonObject
            assertThat(json["kind"].asString).isEqualTo("module")
            assertThat(json["children"].asJsonArray.map { it.asJsonObject["kind"].asString }).containsExactly("test", "test", "class")
        }
    }

    @Test
    fun `a Gradle project's test module, which discovery finds beside the module it imports, has its tests listed`() {
        source("src/main/x/GradleTested.x", "module GradleTested { void run() {} }")
        val text =
            """
            module GradleTestedTest {
                package app import GradleTested;

                class MathTest {
                    @Test
                    void adds() {
                        assert 1 + 1 == 2;
                    }
                }
            }
            """.trimIndent()
        val tests = source("src/test/x/GradleTestedTest.x", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.discoverSourceModules()
            val compiled = adapter.compile(tests, text)
            assertThat(compiled.success).describedAs(compiled.diagnostics.toString()).isTrue()

            val module = adapter.tests().single { it.module == "GradleTestedTest" }
            assertThat(
                module.tests.children
                    .single()
                    .children
                    .map { it.selector },
            ).containsExactly("MathTest.adds")
        }
    }

    @Test
    fun `a module without tests has none to run`() {
        val app = source("App.x", "module App { void run() {} }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", app)))
            assertThat(adapter.compile(app, "module App { void run() {} }").success).isTrue()

            assertThat(adapter.tests()).isEmpty()
        }
    }

    @Test
    @Slow
    fun `a run of a module's tests reports each as a test event apart from the output, and a failed test fails the run`() {
        val mathy = source("Mathy.x", MATHY)
        val client = RunClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Mathy", mathy)))
            EmbeddedRuns(adapter, { client }, RunWorkerProcess(limits = LIVELOCK_LIMITS)).use { runs ->
                val started = runs.run(RunParams(module = "Mathy", moduleName = "Mathy", runId = "all", tests = emptyList()))
                assertThat(started.awaited().runId).isEqualTo("all")
                val exit = client.exits.awaited()!!
                val output = client.output.toString()
                val events = client.events("all")

                assertThat(events.first()).isEqualTo(TestEvent(TestEvent.RUN, version = 1))
                assertThat(events.filter { it.event == TestEvent.STARTED }.map { listOf(it.id, it.parent, it.selector, it.container) })
                    .containsExactly(
                        listOf("Mathy:", null, "", true),
                        listOf("Mathy:void adds()", "Mathy:", "adds", false),
                        listOf("Mathy:void fails()", "Mathy:", "fails", false),
                        listOf("Mathy:Inner", "Mathy:", "Inner", true),
                        listOf("Mathy:Inner.void works()", "Mathy:Inner", "Inner.works", false),
                    )
                val finished = events.filter { it.event == TestEvent.FINISHED }.associateBy { it.id }
                assertThat(finished.keys).containsExactlyInAnyOrder(
                    "Mathy:",
                    "Mathy:void adds()",
                    "Mathy:void fails()",
                    "Mathy:Inner",
                    "Mathy:Inner.void works()",
                )
                assertThat(finished.getValue("Mathy:void adds()").status).isEqualTo("passed")
                assertThat(finished.getValue("Mathy:void fails()").status).isEqualTo("failed")
                assertThat(finished.getValue("Mathy:void fails()").message).isEqualTo("one and one are not three")
                assertThat(finished.getValue("Mathy:void fails()").details).contains("one and one are not three")
                assertThat(output).contains("inside works").doesNotContain(TestEvent.MARKER)
                assertThat(exit.error).isNull()
                assertThat(exit.exitCode).isNotZero()
                assertThat(runs.tracked()).isZero()
            }
        }
    }

    @Test
    @Slow
    fun `a test that throws other than by failing an assertion is aborted, a disabled one skipped, and the console counts both`() {
        val broken = source("Broken.x", BROKEN)
        val client = RunClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Broken", broken)))
            EmbeddedRuns(adapter, { client }, RunWorkerProcess(limits = LIVELOCK_LIMITS)).use { runs ->
                runs.run(RunParams(module = "Broken", moduleName = "Broken", runId = "broken", tests = emptyList())).awaited()
                val exit = client.exits.awaited()!!
                val output = client.output.toString()
                val events = client.events("broken")
                val finished = events.filter { it.event == TestEvent.FINISHED }.associateBy { it.id }

                assertThat(finished.getValue("Broken:void passes()").status).isEqualTo("passed")
                // a plain assert throws IllegalState, and assert:once an Assertion, which extends it
                assertThat(finished.getValue("Broken:void fails()").status).isEqualTo("failed")
                assertThat(finished.getValue("Broken:void failsOnce()").status).isEqualTo("failed")
                val breaks = finished.getValue("Broken:void breaks()")
                assertThat(breaks.status).isEqualTo("aborted")
                assertThat(breaks.message).isEqualTo("not yet")
                assertThat(breaks.details).contains("Unsupported").contains("(Broken.x:23)")
                assertThat(events.filter { it.event == TestEvent.SKIPPED }.map { it.id to it.message })
                    .containsExactly("Broken:void waits()" to "later")
                assertThat(finished).doesNotContainKey("Broken:void waits()")
                // the console's summary of the module agrees with the events
                assertThat(output)
                    .contains("(passed=1 failed=2 skipped=1 errors=1)")
                    .doesNotContain("waits ran")
                assertThat(exit.exitCode).isNotZero()
                assertThat(runs.tracked()).isZero()
            }
        }
    }

    @Test
    @Slow
    fun `a run of one selected test runs it alone, and passes`() {
        val mathy = source("Mathy.x", MATHY)
        val client = RunClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Mathy", mathy)))
            EmbeddedRuns(adapter, { client }, RunWorkerProcess(limits = LIVELOCK_LIMITS)).use { runs ->
                runs
                    .run(
                        RunParams(module = "Mathy", runId = "one", tests = listOf(TestSelector("Inner.works", "test"))),
                    ).awaited()
                val exit = client.exits.awaited()!!
                val tests = client.events("one").filter { it.event == TestEvent.STARTED && it.container == false }

                assertThat(tests.map { it.selector }).containsExactly("Inner.works")
                assertThat(client.output.toString()).contains("inside works")
                assertThat(exit.exitCode).isZero()
                assertThat(runs.tracked()).isZero()
            }
        }
    }

    @Test
    @Slow
    fun `stop ends a test run that never finishes, which reports that it was stopped`() {
        val endless = source("Endless.x", ENDLESS)
        val client = RunClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Endless", endless)))
            EmbeddedRuns(adapter, { client }, RunWorkerProcess(limits = LIVELOCK_LIMITS)).use { runs ->
                runs.run(RunParams(module = "Endless", runId = "endless", tests = emptyList())).awaited()
                awaitUntil { "spinning" in client.output }
                assertThat(runs.stop(StopRunParams("endless"))).isTrue()

                val exit = client.exits.awaited()!!
                assertThat(exit.error).isEqualTo(EmbeddedRuns.STOPPED)
                assertThat(runs.tracked()).isZero()
            }
        }
    }

    @Test
    @Slow
    fun `a debug session of a test stops at a breakpoint in it, and continue runs the test to its end`() {
        val mathy = source("Mathy.x", MATHY)
        val path = directory.resolve("Mathy.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Mathy", mathy)))
            EmbeddedRuns(adapter, { client }, RunWorkerProcess(limits = LIVELOCK_LIMITS)).use { runs ->
                val sessions = DebugSessions(runs, { client })
                val id = sessions.start().sessionId
                sessions.request(id, 1, "initialize", """{"adapterID": "xtc"}""")
                sessions.request(
                    id,
                    2,
                    "launch",
                    """{"module": "Mathy", "moduleUri": "$mathy", "tests": [{"selector": "Inner.works", "kind": "test"}]}""",
                )
                sessions.request(id, 3, "setBreakpoints", """{"source": {"path": "$path"}, "breakpoints": [{"line": 17}]}""")
                sessions.request(id, 4, "configurationDone")
                val untilStop = client.until(id) { it.event == "stopped" }

                assertThat(untilStop.single { it.response == "launch" }["success"].asBoolean).isTrue()
                val stopped = untilStop.last()["body"].asJsonObject
                assertThat(stopped["reason"].asString).isEqualTo("breakpoint")
                sessions.request(id, 5, "stackTrace", """{"threadId": ${stopped["threadId"].asInt}}""")
                val top =
                    client
                        .until(id) { it.response == "stackTrace" }
                        .last()["body"]
                        .asJsonObject["stackFrames"]
                        .asJsonArray
                        .first()
                        .asJsonObject
                assertThat(top["name"].asString).contains("works")
                assertThat(top["line"].asInt).isEqualTo(17)

                sessions.request(id, 6, "continue", """{"threadId": ${stopped["threadId"].asInt}}""")
                val rest = client.until(id) { it.event == "terminated" }
                val output =
                    (untilStop + rest).filter { it.event == "output" }.joinToString(
                        "",
                    ) { it["body"].asJsonObject["output"].asString }
                assertThat(output).contains("inside works").doesNotContain(TestEvent.MARKER)
                // the test's events come as the DAP event xtcTest, apart from the output
                val works =
                    (untilStop + rest)
                        .filter { it.event == "xtcTest" }
                        .map { it["body"].asJsonObject }
                        .filter { it["id"]?.asString == "Mathy:Inner.void works()" }
                assertThat(works.map { it["event"].asString }).containsExactly(TestEvent.STARTED, TestEvent.FINISHED)
                assertThat(works.first()["selector"].asString).isEqualTo("Inner.works")
                assertThat(works.last()["status"].asString).isEqualTo("passed")
                assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isZero()
                sessions.end(DebugSessionParams(id))
                assertThat(runs.tracked()).isZero()
            }
        }
    }

    /** Records the runs' output, test events and exits. */
    private class RunClient :
        XtcLanguageClient,
        LanguageClient by mock(LanguageClient::class.java) {
        val output = StringBuffer()
        val exits = LinkedBlockingQueue<RunExitedParams>()
        private val tested = ConcurrentLinkedQueue<TestEventParams>()

        /** The test events of the run [runId], in the order they came. */
        fun events(runId: String): List<TestEvent> = tested.filter { it.runId == runId }.map { it.event }

        override fun runOutput(params: RunOutputParams) {
            output.append(params.text)
        }

        override fun testEvent(params: TestEventParams) {
            tested.add(params)
        }

        override fun runExited(params: RunExitedParams) {
            exits.add(params)
        }
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

    private companion object {
        /** Two tests of the module, one of which fails, and one of a class in it, which prints. */
        val MATHY =
            """
            module Mathy {
                @Test
                void adds() {
                    assert 1 + 1 == 2;
                }

                @Test
                void fails() {
                    Int n = 1;
                    assert n + n == 3 as "one and one are not three";
                }

                class Inner {
                    @Test
                    void works() {
                        @Inject Console console;
                        console.print("inside works");
                    }
                }
            }
            """.trimIndent() + "\n"

        /**
         * Tests that pass, fail an assertion, throw another exception, which aborts the test, and are disabled; line 23
         * throws.
         */
        val BROKEN =
            """
            module Broken {
                package xunit import xunit.xtclang.org;

                @Test
                void passes() {
                    assert 1 + 1 == 2;
                }

                @Test
                void fails() {
                    Int n = 1;
                    assert n + n == 3 as "one and one are not three";
                }

                @Test
                void failsOnce() {
                    Int n = 1;
                    assert:once n + n == 3 as "one and one are still not three";
                }

                @Test
                void breaks() {
                    throw new Unsupported("not yet");
                }

                @Test
                @xunit.annotations.Disabled("later")
                void waits() {
                    @Inject Console console;
                    console.print("waits ran");
                }
            }
            """.trimIndent() + "\n"

        /** A test that runs until it is stopped. */
        val ENDLESS =
            """
            module Endless {
                @Test
                void spins() {
                    @Inject Console console;
                    console.print("spinning");
                    // an empty while (True) is rejected as non-terminating
                    for (Int count = 0; count >= 0; ++count) {}
                }
            }
            """.trimIndent()
    }
}
