package org.xvm.lsp.run

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.xvm.debug.TestSelector
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.XdkTest
import org.xvm.lsp.server.DebugSessionParams
import org.xvm.lsp.server.DebugSessions
import org.xvm.lsp.server.EmbeddedRuns
import org.xvm.lsp.server.RunExitedParams
import org.xvm.lsp.server.RunOutputParams
import org.xvm.lsp.server.RunParams
import org.xvm.lsp.server.StopRunParams
import org.xvm.lsp.server.XtcLanguageClient
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

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
    fun `a run of a module's tests reports each as a TeamCity service message, and a failed test fails the run`() {
        val mathy = source("Mathy.x", MATHY)
        val client = RunClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Mathy", mathy)))
            EmbeddedRuns(adapter, { client }).use { runs ->
                val started = runs.run(RunParams(module = "Mathy", moduleName = "Mathy", runId = "all", tests = emptyList()))
                assertThat(started.get(120, SECONDS).runId).isEqualTo("all")
                val exit = client.exits.poll(120, SECONDS)!!
                val output = client.output.toString()

                assertThat(output).contains(
                    "##teamcity[testSuiteStarted name='Mathy' nodeId='Mathy:' parentNodeId='0' locationHint='xunit://']",
                    "##teamcity[testStarted name='Mathy.adds()' nodeId='Mathy:void adds()' parentNodeId='Mathy:' locationHint='xunit://adds']",
                    "##teamcity[testSuiteStarted name='Inner' nodeId='Mathy:Inner' parentNodeId='Mathy:' locationHint='xunit://Inner']",
                    "##teamcity[testStarted name='Inner.works()' nodeId='Mathy:Inner.void works()' parentNodeId='Mathy:Inner' " +
                        "locationHint='xunit://Inner.works']",
                    "inside works",
                )
                assertThat(
                    output,
                ).containsPattern("##teamcity\\[testFailed name='Mathy.fails\\(\\)' [^\\n]*message='one and one are not three'")
                assertThat(exit.error).isNull()
                assertThat(exit.exitCode).isNotZero()
                assertThat(runs.tracked()).isZero()
            }
        }
    }

    @Test
    fun `a run of one selected test runs it alone, and passes`() {
        val mathy = source("Mathy.x", MATHY)
        val client = RunClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Mathy", mathy)))
            EmbeddedRuns(adapter, { client }).use { runs ->
                runs.run(RunParams(module = "Mathy", runId = "one", tests = listOf(TestSelector("Inner.works", "test")))).get(120, SECONDS)
                val exit = client.exits.poll(120, SECONDS)!!
                val output = client.output.toString()

                assertThat(output).contains("locationHint='xunit://Inner.works']", "inside works")
                assertThat(output).doesNotContain("xunit://adds", "xunit://fails")
                assertThat(exit.exitCode).isZero()
                assertThat(runs.tracked()).isZero()
            }
        }
    }

    @Test
    fun `stop ends a test run that never finishes, which reports that it was stopped`() {
        val endless = source("Endless.x", ENDLESS)
        val client = RunClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Endless", endless)))
            EmbeddedRuns(adapter, { client }).use { runs ->
                runs.run(RunParams(module = "Endless", runId = "endless", tests = emptyList())).get(120, SECONDS)
                await().atMost(Duration.ofSeconds(60)).until { "spinning" in client.output }
                assertThat(runs.stop(StopRunParams("endless"))).isTrue()

                val exit = client.exits.poll(60, SECONDS)!!
                assertThat(exit.error).isEqualTo(EmbeddedRuns.STOPPED)
                assertThat(runs.tracked()).isZero()
            }
        }
    }

    @Test
    fun `a debug session of a test stops at a breakpoint in it, and continue runs the test to its end`() {
        val mathy = source("Mathy.x", MATHY)
        val path = directory.resolve("Mathy.x").toString()
        val client = DebugClient()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Mathy", mathy)))
            EmbeddedRuns(adapter, { client }).use { runs ->
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
                assertThat(output).contains("inside works", "##teamcity[testFinished name='Inner.works()'")
                assertThat(rest.single { it.event == "exited" }["body"].asJsonObject["exitCode"].asInt).isZero()
                sessions.end(DebugSessionParams(id))
                assertThat(runs.tracked()).isZero()
            }
        }
    }

    /** Records the runs' output and exits. */
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

    private fun source(
        path: String,
        text: String,
    ): String {
        val file = directory.resolve(path).toFile()
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
