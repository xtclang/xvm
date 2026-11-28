package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.Project
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import java.nio.file.Files

/**
 * X339: the module's tests have icons in the gutter, the module's and its class's too; Run of the module's icon runs
 * them in IntelliJ's test console, which marks each passed or failed and goes to each one's declaration; Debug of a
 * test's icon stops at a breakpoint in it, in a Tests tab of the debug session, and Resume runs it to its end, which
 * passes it alone.
 */
internal fun ParityScenarios.testRunCases() {
    case("X339") { data ->
        val document = openModule(data)
        val console = TestConsole(this)
        val editor = document.editor.editor
        val ran = runTests(console, editor, data, data.string("runAll"), "Run marks every test")
        // the console goes to the test's declaration, the line of its name
        val works = ran.result(console.location(data.string("debugged")))
        check(works.int("line") == data.int("nameLine") - 1 && works.string("file").endsWith(data.string("file"))) { "works: $works" }

        debugTest(console, editor, data, data.string("file"), uri(data.string("file")), "the debugged test stops at the breakpoint")
    }
}

/**
 * X340: in Gradle mode, Run of a test class's gutter icon runs the project's testXtc for its tests, in IntelliJ's test
 * console, and Debug of a test's icon has testXtc run it under the debugger, with the console in the session's Tests
 * tab; Resume runs it to its end, which passes it alone. The project is made as X337's.
 */
internal fun ParityScenarios.gradleTestCases() {
    case("X340") { data ->
        gradleProject("X340", data) { _, _ ->
            val file = projectRoot.resolve(data.string("testFile"))
            Files.writeString(file, data.string("testSource"))
            // the playbook's graph is explicit, so the project's modules join it as the test source set has them
            val main = projectRoot.resolve(data.string("mainFile")).toUri().toString()
            configure(
                listOf(
                    SharedScenarios.SourceModule(data.string("mainModule"), main, emptyList()),
                    SharedScenarios.SourceModule(data.string("module"), file.toUri().toString(), listOf(data.string("mainModule"))),
                ),
            )
            val document = open(file, data.string("testFile"))
            val console = TestConsole(this)
            val editor = document.editor.editor
            runTests(console, editor, data, data.string("runIcon"), "testXtc runs the class's tests")

            try {
                val stops = "testXtc runs the test under the debugger, which stops at the breakpoint"
                debugTest(console, editor, data, data.string("testFile"), file.toUri().toString(), stops)
            } finally {
                closeEditor(document)
            }
        }
    }
}

/**
 * Runs the tests of [editor]'s gutter icon [icon] in IntelliJ's test console, once the case's test icons show, as [what]
 * says: the case's passed tests pass, and its failed one fails with its message. Answers the console's results.
 */
private fun ParityWorkspace.runTests(
    console: TestConsole,
    editor: Editor,
    data: JsonObject,
    icon: String,
    what: String,
): JsonObject {
    console.tests.reset(console.project)
    awaitUi("Test icons in the gutter", HANG_LIMIT) {
        with(driver) { withContext(OnDispatcher.EDT) { console.tests.icons(editor) } }.containsAll(data.strings("icons"))
    }

    with(driver) { withContext(OnDispatcher.EDT) { console.tests.start(editor, icon, false) } }
    val ran =
        awaitUi(
            what,
            HANG_LIMIT,
            errorMessage = { it.toString() },
            getter = console::results,
            checker = { all -> (data.strings("passed") + data.string("failed")).all { all.finished(console.location(it)) } },
        )
    data.strings("passed").forEach { id ->
        check(ran.result(console.location(id)).string("state") == "passed") { "$id did not pass: $ran" }
    }
    val failure = ran.result(console.location(data.string("failed")))
    check(failure.string("state") == "failed" && data.string("failure") in failure.string("message")) { "Failure: $failure" }
    return ran
}

/**
 * Debugs the case's debugged test from [editor]'s gutter icon, with a breakpoint on its line of [file], at [uri], where
 * it stops, as [what] says; Resume runs the test to its end, which passes it alone.
 */
private fun ParityWorkspace.debugTest(
    console: TestConsole,
    editor: Editor,
    data: JsonObject,
    file: String,
    uri: String,
    what: String,
) {
    console.tests.reset(console.project)
    val name = data.string("debugRun")
    val debugged = console.location(data.string("debugged"))
    try {
        console.tests.breakpoint(console.project, uri, data.int("breakpointLine"))
        with(driver) { withContext(OnDispatcher.EDT) { console.tests.start(editor, data.string("debugIcon"), true) } }
        val stopped =
            awaitUi(
                what,
                HANG_LIMIT,
                errorMessage = { it.toString() },
                getter = { JsonParser.parseString(console.tests.debugState(name)).asJsonObject },
                checker = { it["suspended"]?.asBoolean == true },
            )
        checkStoppedAt(stopped, data.int("breakpointLine"), file)
        console.tests.resume(name)
        val ended =
            awaitUi(
                "Resume runs the test to its end, which passes it",
                HANG_LIMIT,
                errorMessage = { it.toString() },
                getter = console::results,
                checker = { it.finished(debugged) },
            )
        check(ended.result(debugged).string("state") == "passed") { "Debugged: $ended" }
        check(ended.keySet() == setOf(debugged)) { "Debug of the test runs it alone: $ended" }
    } finally {
        with(driver) { utility(DebugSessionProbe::class) }.clearBreakpoints(console.project)
    }
}

private fun JsonObject.result(location: String): JsonObject = checkNotNull(getAsJsonObject(location)) { "No result for $location: $this" }

/** The test icons in an editor's gutter and IntelliJ's test console's results, recorded by the IDE-side probe. */
@Remote("org.xtclang.idea.playbook.probe.TestRunUi", plugin = "org.xtclang.playbook.probe")
internal interface TestRunProbe {
    fun reset(project: Project)

    fun results(): String

    fun icons(editor: Editor): List<String>

    fun start(
        editor: Editor,
        tooltip: String,
        debug: Boolean,
    )

    fun breakpoint(
        project: Project,
        uri: String,
        line: Int,
    )

    fun debugState(name: String): String

    fun resume(name: String)

    fun stop(
        project: Project,
        name: String,
    )

    fun rerunFailed(
        project: Project,
        name: String,
    )

    fun runCommandLine(
        project: Project,
        module: String,
        uri: String,
    )

    fun runAllTests(project: Project)

    fun testRunsEnded(project: Project): Boolean

    fun rerunFailedOffered(
        project: Project,
        name: String,
    ): Boolean

    fun gradleProjects(project: Project): String
}

/** Whether the results hold the test at [location] as finished: the probe also lists a test while it runs. */
private fun JsonObject.finished(location: String): Boolean =
    getAsJsonObject(location)?.get("state")?.asString.let {
        it != null &&
            it != "running"
    }
