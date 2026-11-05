package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
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
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        val project = with(driver) { singleProject() }
        val tests = with(driver) { utility(TestRunProbe::class) }
        val debugger = with(driver) { utility(DebugSessionProbe::class) }
        val editor = document.editor.editor
        // a test item's ID, as VS Code's, is its module and selector; IntelliJ's console knows it by its location
        val location = { id: String -> "xunit://${id.substringAfter('/')}" }

        fun results() = JsonParser.parseString(tests.results()).asJsonObject

        fun icons() = with(driver) { withContext(OnDispatcher.EDT) { tests.icons(editor) } }

        tests.reset(project)
        awaitUi("Test icons in the gutter", HANG_LIMIT) { icons().containsAll(data.strings("icons")) }

        with(driver) { withContext(OnDispatcher.EDT) { tests.start(editor, data.string("runAll"), false) } }
        val ran =
            awaitUi(
                "Run marks every test",
                HANG_LIMIT,
                errorMessage = { it.toString() },
                getter = ::results,
                checker = { all -> (data.strings("passed") + data.string("failed")).all { all.has(location(it)) } },
            )
        data.strings("passed").forEach { id ->
            check(ran.result(location(id)).string("state") == "passed") { "$id did not pass: $ran" }
        }
        val failure = ran.result(location(data.string("failed")))
        check(failure.string("state") == "failed" && data.string("failure") in failure.string("message")) { "Failure: $failure" }
        // the console goes to the test's declaration, the line of its name
        val works = ran.result(location(data.string("debugged")))
        check(works.int("line") == data.int("nameLine") - 1 && works.string("file").endsWith(data.string("file"))) { "works: $works" }

        tests.reset(project)
        val name = data.string("debugRun")
        try {
            tests.breakpoint(project, uri(data.string("file")), data.int("breakpointLine"))
            with(driver) { withContext(OnDispatcher.EDT) { tests.start(editor, data.string("debugIcon"), true) } }
            val stopped =
                awaitUi(
                    "the debugged test stops at the breakpoint",
                    HANG_LIMIT,
                    errorMessage = { it.toString() },
                    getter = { JsonParser.parseString(tests.debugState(name)).asJsonObject },
                    checker = { it["suspended"]?.asBoolean == true },
                )
            check(stopped.int("line") == data.int("breakpointLine") && stopped.string("file").endsWith(data.string("file"))) {
                "Stopped at: $stopped"
            }
            tests.resume(name)
            val debugged =
                awaitUi(
                    "Resume runs the test to its end, which passes it",
                    HANG_LIMIT,
                    errorMessage = { it.toString() },
                    getter = ::results,
                    checker = { it.has(location(data.string("debugged"))) },
                )
            check(debugged.result(location(data.string("debugged"))).string("state") == "passed") { "Debugged: $debugged" }
            check(debugged.keySet() == setOf(location(data.string("debugged")))) { "Debug of the test runs it alone: $debugged" }
        } finally {
            debugger.clearBreakpoints(project)
        }
    }
}

/**
 * X340: in Gradle mode, Run of a test class's gutter icon runs the project's testXtc for its tests, in IntelliJ's test
 * console, and Debug of a test's icon has testXtc run it under the debugger, with the console in the session's Tests
 * tab; Resume runs it to its end, which passes it alone. The project is made as X337's.
 */
internal fun ParityScenarios.gradleTestCases() {
    case("X340") { data ->
        gradleProject("X340", data) { project, _ ->
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
            val tests = with(driver) { utility(TestRunProbe::class) }
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val editor = document.editor.editor
            val location = { id: String -> "xunit://${id.substringAfter('/')}" }

            fun results() = JsonParser.parseString(tests.results()).asJsonObject

            fun icons() = with(driver) { withContext(OnDispatcher.EDT) { tests.icons(editor) } }

            tests.reset(project)
            awaitUi("Test icons in the gutter", HANG_LIMIT) { icons().containsAll(data.strings("icons")) }

            with(driver) { withContext(OnDispatcher.EDT) { tests.start(editor, data.string("runIcon"), false) } }
            val ran =
                awaitUi(
                    "testXtc runs the class's tests",
                    HANG_LIMIT,
                    errorMessage = { it.toString() },
                    getter = ::results,
                    checker = { all -> (data.strings("passed") + data.string("failed")).all { all.has(location(it)) } },
                )
            data.strings("passed").forEach { id ->
                check(ran.result(location(id)).string("state") == "passed") { "$id did not pass: $ran" }
            }
            val failure = ran.result(location(data.string("failed")))
            check(failure.string("state") == "failed" && data.string("failure") in failure.string("message")) { "Failure: $failure" }

            tests.reset(project)
            val name = data.string("debugRun")
            try {
                tests.breakpoint(project, file.toUri().toString(), data.int("breakpointLine"))
                with(driver) { withContext(OnDispatcher.EDT) { tests.start(editor, data.string("debugIcon"), true) } }
                val stopped =
                    awaitUi(
                        "testXtc runs the test under the debugger, which stops at the breakpoint",
                        HANG_LIMIT,
                        errorMessage = { it.toString() },
                        getter = { JsonParser.parseString(tests.debugState(name)).asJsonObject },
                        checker = { it["suspended"]?.asBoolean == true },
                    )
                check(stopped.int("line") == data.int("breakpointLine") && stopped.string("file").endsWith(data.string("testFile"))) {
                    "Stopped at: $stopped"
                }
                tests.resume(name)
                val debugged =
                    awaitUi(
                        "Resume runs the test to its end, which passes it",
                        HANG_LIMIT,
                        errorMessage = { it.toString() },
                        getter = ::results,
                        checker = { it.has(location(data.string("debugged"))) },
                    )
                check(debugged.result(location(data.string("debugged"))).string("state") == "passed") { "Debugged: $debugged" }
                check(debugged.keySet() == setOf(location(data.string("debugged")))) { "Debug of the test runs it alone: $debugged" }
            } finally {
                debugger.clearBreakpoints(project)
                closeEditor(document)
            }
        }
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
}
