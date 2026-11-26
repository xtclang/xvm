package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.singleProject
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import java.nio.file.Files

/**
 * The test console of a case's module: its test icons and results, as IntelliJ's console has them by each test's
 * `xunit://<selector>` location, or, in a run of every test module's tests ([allModules]),
 * `xunit://<module>/<selector>`, from the IDE-side [TestRunProbe].
 */
internal class TestConsole(
    private val workspace: ParityWorkspace,
    private val allModules: Boolean = false,
) {
    val project = with(workspace.driver) { singleProject() }
    val tests = with(workspace.driver) { utility(TestRunProbe::class) }

    /** A test item's location, by its ID as VS Code's has it, the module and the selector. */
    fun location(id: String) = if (allModules) "xunit://$id" else "xunit://${id.substringAfter('/')}"

    fun results(): JsonObject = JsonParser.parseString(tests.results()).asJsonObject

    /** The result of the test [id], or none yet. */
    fun result(id: String): JsonObject? = results().getAsJsonObject(location(id))

    /** Starts Run of the gutter icon with [tooltip] in [document]'s editor, once it shows. */
    fun run(
        document: ParityWorkspace.Document,
        tooltip: String,
    ) {
        val editor = document.editor.editor
        awaitUi("the test icon $tooltip", HANG_LIMIT) {
            with(workspace.driver) { withContext(OnDispatcher.EDT) { tests.icons(editor) } }.any { it.startsWith("$tooltip@") }
        }
        with(workspace.driver) { withContext(OnDispatcher.EDT) { tests.start(editor, tooltip, false) } }
    }

    /** Waits until every test run has ended, its process and its console's tree of tests. */
    fun runsEnded(what: String) =
        awaitUi(what, HANG_LIMIT, errorMessage = { results().toString() }) {
            with(workspace.driver) { withContext(OnDispatcher.EDT) { tests.testRunsEnded(project) } }
        }

    /** Waits until each of [ids] has a result other than running, and answers them. */
    fun ended(
        ids: List<String>,
        what: String,
    ): List<JsonObject> =
        awaitUi(
            what,
            HANG_LIMIT,
            errorMessage = { results().toString() },
            getter = { ids.map(::result) },
            checker = { all -> all.all { it != null && it.string("state") != "running" } },
        ).map { checkNotNull(it) }
}
