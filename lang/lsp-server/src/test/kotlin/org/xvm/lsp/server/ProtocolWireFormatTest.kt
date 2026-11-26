package org.xvm.lsp.server

import com.google.gson.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkBuiltModule
import org.xvm.lsp.adapter.xdk.XdkModuleTests
import org.xvm.lsp.adapter.xdk.XdkTest
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.toLsp
import org.xvm.lsp.protocol.BuildParams
import org.xvm.lsp.protocol.BuildResponse
import org.xvm.lsp.protocol.ConsumerDiscovery
import org.xvm.lsp.protocol.DebugMessageParams
import org.xvm.lsp.protocol.DebugSessionParams
import org.xvm.lsp.protocol.DebugSessionResponse
import org.xvm.lsp.protocol.DebugTaskParams
import org.xvm.lsp.protocol.DebugTaskResult
import org.xvm.lsp.protocol.DiscoverConsumersParams
import org.xvm.lsp.protocol.DocumentDiagnostics
import org.xvm.lsp.protocol.GradleProjectsParams
import org.xvm.lsp.protocol.RenameProposal
import org.xvm.lsp.protocol.RenameScope
import org.xvm.lsp.protocol.ReplacedLibrariesParams
import org.xvm.lsp.protocol.RunExitedParams
import org.xvm.lsp.protocol.RunInputParams
import org.xvm.lsp.protocol.RunLens
import org.xvm.lsp.protocol.RunOutputParams
import org.xvm.lsp.protocol.RunParams
import org.xvm.lsp.protocol.RunResponse
import org.xvm.lsp.protocol.SourceGraphReplacement
import org.xvm.lsp.protocol.SourceModuleConfiguration
import org.xvm.lsp.protocol.StopRunParams
import org.xvm.lsp.protocol.TestEvent
import org.xvm.lsp.protocol.TestEventParams
import org.xvm.lsp.protocol.TestSelector

/**
 * The types that moved into lang/protocol write the JSON that the server's own types wrote before they moved, and read
 * it back. The expected text, `protocol/server-types-bcfffac523.txt`, was captured at errs bcfffac523 from the server's
 * classes (EmbeddedRuns.kt, DebugSessions.kt, RenameProposal.kt, XdkBuild.kt, XdkTests.kt, and dap-server's DebugRuns.kt
 * and TestEvent.kt), serialized with lsp4j's Gson as the server's connection writes them. Each value here is the same
 * value made from the protocol's types the way the server makes it now: a build's modules and diagnostics and a
 * module's tests through ProtocolConversions.
 */
class ProtocolWireFormatTest {
    private val gson = MessageJsonHandler(emptyMap()).gson

    private val expected: Map<String, String> =
        requireNotNull(javaClass.getResource("/protocol/server-types-bcfffac523.txt"))
            .readText()
            .lines()
            .filter { it.isNotBlank() }
            .associate { it.substringBefore('\t') to it.substringAfter('\t') }

    private val app = "file:///w/app/src/main/x/app.x"
    private val lib = "file:///w/lib/src/main/x/lib.x"

    private val own = Diagnostic(Location(app, 3, 4, 3, 9), Diagnostic.Severity.ERROR, "Unknown name", "COMPILER-43", "xtc")
    private val warning =
        Diagnostic(Location(app, 7, 0, 7, 2), Diagnostic.Severity.WARNING, "Unused", null, "xtc", setOf(Diagnostic.Tag.UNNECESSARY))
    private val hint = Diagnostic(Location(app, 9, 1, 9, 1), Diagnostic.Severity.HINT, "Hint", null, null, setOf(Diagnostic.Tag.DEPRECATED))
    private val foreign = Diagnostic(Location(lib, 1, 2, 1, 5), Diagnostic.Severity.INFORMATION, "Elsewhere", "DEPENDENCY-FAILED", "xtc")

    private val diagnostics =
        listOf(
            DocumentDiagnostics(app, listOf(own, warning, hint).map { it.toProtocol(app) }),
            DocumentDiagnostics(app, listOf(foreign.toProtocol(app))),
            DocumentDiagnostics(lib, emptyList()),
        )

    private val modules =
        listOf(
            XdkBuiltModule(
                "app.examples.org",
                app,
                true,
                false,
                "/w/build/xtc/embedded/app.xtc",
                reused = true,
                revision = "0123456789abcdef",
            ),
            XdkBuiltModule("lib.examples.org", lib, false, false, null),
            XdkBuiltModule("dep.examples.org", "file:///w/dep/dep.x", false, true, null),
        ).map { it.toProtocol() }

    private val build = BuildResponse(false, false, modules, diagnostics, "/w/build/xtc/embedded")

    private val source =
        SourceModuleConfiguration("app.examples.org", app, listOf("lib.examples.org"), listOf("file:///w/app/src/main/resources"))
    private val sourceNoResources = SourceModuleConfiguration("lib.examples.org", lib, emptyList())

    private val edit = WorkspaceEdit(mapOf(app to listOf(TextEdit(Range(Position(1, 2), Position(1, 5)), "renamed"))))

    private val message =
        JsonObject().apply {
            addProperty("seq", 1)
            addProperty("type", "request")
            addProperty("command", "initialize")
        }

    private val event =
        TestEvent(
            TestEvent.FINISHED,
            id = "app.examples.org:tests.MathTest.adds",
            status = "failed",
            message = "m",
            details = "d",
            durationMillis = 5,
        )

    private val tests =
        XdkModuleTests(
            "app.examples.org",
            app,
            XdkTest(
                "",
                "app",
                XdkTest.MODULE,
                Location(app, 0, 7, 0, 10),
                listOf(
                    XdkTest(
                        "tests.MathTest",
                        "MathTest",
                        XdkTest.CLASS,
                        Location(app, 4, 10, 4, 18),
                        listOf(XdkTest("tests.MathTest.adds", "adds", XdkTest.TEST, Location(app, 6, 13, 6, 17))),
                    ),
                    XdkTest("tests", "tests", XdkTest.PACKAGE, null),
                ),
            ),
        ).toProtocol()

    /** Each captured case, by its name in the expected text, as the protocol's types make it. */
    private val values: Map<String, Any> =
        mapOf(
            "BuildParams" to BuildParams("app.examples.org", true, "file:///w/"),
            "BuildParams.empty" to BuildParams(),
            "BuildResponse" to build,
            "BuildResponse.cancelled" to BuildResponse(false, true, emptyList(), emptyList(), null),
            "RunParams" to
                RunParams(app, "run", listOf("a", "b"), "/w", "r1", "app.examples.org", listOf(TestSelector("tests.MathTest", "class"))),
            "RunParams.empty" to RunParams(),
            "RunResponse" to RunResponse("r1", build),
            "RunResponse.failed" to RunResponse(null, BuildResponse(false, false, emptyList(), emptyList(), null)),
            "RunLens" to RunLens("app.examples.org", 3),
            "StopRunParams" to StopRunParams("r1"),
            "StopRunParams.empty" to StopRunParams(),
            "RunInputParams.text" to RunInputParams("r1", "line\n"),
            "RunInputParams.end" to RunInputParams("r1", end = true),
            "ReplacedLibrariesParams" to ReplacedLibrariesParams(listOf("json.xtclang.org"), listOf("file:///w/")),
            "ReplacedLibrariesParams.empty" to ReplacedLibrariesParams(),
            "GradleProjectsParams" to GradleProjectsParams(mapOf("app.examples.org" to ":app", "lib.examples.org" to ":lib")),
            "RunOutputParams" to RunOutputParams("r1", "out\n"),
            "TestEventParams" to TestEventParams("r1", event),
            "RunExitedParams.code" to RunExitedParams("r1", 0, null),
            "RunExitedParams.error" to RunExitedParams("r1", null, "boom"),
            "DebugTaskParams" to
                DebugTaskParams(
                    "s1",
                    "app.examples.org",
                    "run",
                    listOf("a"),
                    "connect:127.0.0.1:5005,suspend,token=t",
                    "gradle",
                    listOf(TestSelector("tests", "package")),
                ),
            "DebugTaskParams.noTests" to
                DebugTaskParams("s1", "app.examples.org", null, emptyList(), "connect:127.0.0.1:5005", "commandLine"),
            "DebugTaskResult" to DebugTaskResult(3),
            "DebugTaskResult.empty" to DebugTaskResult(),
            "DebugSessionResponse" to DebugSessionResponse("s1"),
            "DebugSessionParams" to DebugSessionParams("s1"),
            "DebugMessageParams" to DebugMessageParams("s1", message),
            "RenameProposal" to
                RenameProposal(
                    edit,
                    SourceGraphReplacement(
                        listOf(source, sourceNoResources),
                        listOf(source.copy(name = "renamed.examples.org"), sourceNoResources),
                    ),
                    RenameScope("source-graph", listOf(source), listOf(app, lib), "rev-1"),
                ),
            "RenameProposal.editOnly" to RenameProposal(edit),
            "SourceModuleConfiguration" to source,
            "SourceModuleConfiguration.noResources" to sourceNoResources,
            "DiscoverConsumersParams" to DiscoverConsumersParams(listOf("file:///w/", "file:///v/")),
            "ConsumerDiscovery" to ConsumerDiscovery(listOf(source), listOf(sourceNoResources)),
            "ConsumerDiscovery.empty" to ConsumerDiscovery(emptyList(), emptyList()),
            "ModuleTests" to tests,
            "TestSelector" to TestSelector("tests.MathTest.adds", "test"),
            "TestEvent.finished" to event,
            "TestEvent.run" to TestEvent(TestEvent.RUN, version = 1),
            "TestEvent.started" to
                TestEvent(
                    TestEvent.STARTED,
                    id = "app.examples.org:tests",
                    parent = "app.examples.org:",
                    name = "tests",
                    selector = "tests",
                    container = true,
                ),
            "TestEvent.skipped" to TestEvent(TestEvent.SKIPPED, id = "app.examples.org:tests.T.t", message = "disabled"),
        )

    @Test
    fun `every captured case has its value, and every value its captured case`() {
        assertThat(values.keys).containsExactlyInAnyOrderElementsOf(expected.keys)
    }

    @Test
    fun `each type writes the JSON that the server's own type wrote`() {
        values.forEach { (case, value) ->
            assertThat(gson.toJson(value)).describedAs(case).isEqualTo(expected[case])
        }
    }

    @Test
    fun `each type reads the JSON that the server's own type wrote as the same value`() {
        values.forEach { (case, value) ->
            assertThat(gson.fromJson(expected[case], value.javaClass)).describedAs(case).isEqualTo(value)
        }
    }

    @Test
    fun `a build's diagnostics are grouped by document as publishDiagnostics would publish them`() {
        val diagnostics = listOf(own, foreign, warning, hint)
        val published =
            diagnostics
                .groupBy { it.location.uri }
                .map { (uri, found) -> PublishDiagnosticsParams(uri, found.map { it.toLsp(uri) }) }

        assertThat(gson.toJson(diagnostics.toProtocol())).isEqualTo(gson.toJson(published))
    }
}
