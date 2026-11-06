package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.singleProject
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import java.nio.file.Files

/**
 * The test console of a case's module: its test icons and results, as IntelliJ's console has them by each test's
 * `xunit://<selector>` location, from the IDE-side [TestRunProbe].
 */
private class TestConsole(
    private val workspace: ParityWorkspace,
) {
    val project = with(workspace.driver) { singleProject() }
    val tests = with(workspace.driver) { utility(TestRunProbe::class) }

    /** A test item's location, by its ID as VS Code's has it, the module and the selector. */
    fun location(id: String) = "xunit://${id.substringAfter('/')}"

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

/** Writes [file] with [source] as the module [module], configured as the workspace's, with no errors. */
private fun ParityWorkspace.testModule(
    file: String,
    module: String,
    source: String,
): ParityWorkspace.Document {
    val document = open(file, source)
    configure(listOf(SharedScenarios.SourceModule(module, uri(file), emptyList())))
    clean(document)
    return document
}

/** The state and message of a result, for a check's message. */
private fun JsonObject.shown() = "${string("state")}: ${get("message")?.asString}"

/**
 * X342-X349: xunit tests stopped partway, added while the project is open, thrown, skipped, rerun when they failed, at
 * their sources, in several modules, and run by the xtc command line, in IntelliJ's test console.
 */
internal fun ParityScenarios.xunitCases() {
    case("X342") { data ->
        val document = testModule(data.string("file"), data.string("module"), data.string("source"))
        val console = TestConsole(this)
        console.tests.reset(console.project)
        console.run(document, data.string("runAll"))
        // xunit runs the module's tests one at a time, in an order of its own: the stop comes as either waiting test starts
        val blocking = data.strings("blocking")
        awaitUi("a waiting test starts", HANG_LIMIT, errorMessage = { console.results().toString() }) {
            blocking.any { console.result(it)?.string("state") == "running" }
        }
        console.tests.stop(console.project, data.string("runAll"))
        awaitUi("the stopped run ends its tests", HANG_LIMIT, errorMessage = { console.results().toString() }) {
            blocking.none { console.result(it)?.string("state") == "running" }
        }
        // the test that had started is terminated, the other did not run, and neither is passed
        blocking.forEach { id ->
            check(
                console.result(id)?.string("state").let { it == null || it == "terminated" || it == "not run" },
            ) { "$id: ${console.results()}" }
        }
        check(blocking.any { console.result(it)?.string("state") == "terminated" }) { "none terminated: ${console.results()}" }
        // adds passed if it ran before the stop, and otherwise did not run
        check(console.result(data.string("passed"))?.string("state").let { it == null || it == "passed" || it == "not run" }) {
            "adds: ${console.results()}"
        }
        awaitUi("no program of the stopped run is left", HANG_LIMIT) {
            protocol.query("xtc/languageServiceStatus", emptyMap<String, Any>()).asJsonObject["embeddedRuns"]?.asInt == 0
        }
        closeEditor(document)
    }

    case("X343") { data ->
        val document = testModule(data.string("file"), data.string("module"), data.string("source"))
        val console = TestConsole(this)
        console.tests.reset(console.project)
        val after = data.string("after")
        val text = data.string("source").replace(after, after + data.string("added"))
        replace(document, text)
        save(document)
        // the gutter shows the added test's icon, from the server's analysis of the edit, with no restart
        console.run(document, data.string("runIcon"))
        val (added) = console.ended(listOf(data.string("addedItem")), "the added test runs")
        check(added.string("state") == "passed") { "second: ${added.shown()}" }
        check(console.results().keySet() == setOf(console.location(data.string("addedItem")))) { "It runs alone: ${console.results()}" }
        closeEditor(document)
    }

    case("X344") { data ->
        val document = testModule(data.string("file"), data.string("module"), data.string("source"))
        val console = TestConsole(this)
        console.tests.reset(console.project)
        console.run(document, data.string("runAll"))
        val (failed, errored) = console.ended(listOf(data.string("failed"), data.string("errored")), "Run reports both tests")
        check(failed.string("state") == "failed" && data.string("failure") in failed.string("message")) { "fails: ${failed.shown()}" }
        check(errored.string("state") == "error" && data.string("error") in errored.string("message")) {
            "A test that throws is an error, apart from a failure: ${errored.shown()}"
        }
        closeEditor(document)
    }

    case("X345") { data ->
        val document = testModule(data.string("file"), data.string("module"), data.string("source"))
        val console = TestConsole(this)
        console.tests.reset(console.project)
        console.run(document, data.string("runAll"))
        val (passed, disabled, precondition) =
            console.ended(listOf(data.string("passed"), data.string("disabled"), data.string("precondition")), "Run reports every test")
        check(passed.string("state") == "passed") { "runs: ${passed.shown()}" }
        check(
            disabled.string("state") == "ignored" && data.string("disabledReason") in disabled.string("message"),
        ) { "later: ${disabled.shown()}" }
        check(precondition.string("state") == "ignored" && data.string("preconditionReason") in precondition.string("message")) {
            "assumes: ${precondition.shown()}"
        }
        closeEditor(document)
    }

    case("X346") { data ->
        val document = testModule(data.string("file"), data.string("module"), data.string("source"))
        val console = TestConsole(this)
        val failed = data.strings("failed")
        console.tests.reset(console.project)
        console.run(document, data.string("runAll"))
        val first = console.ended(listOf(data.string("passed")) + failed, "Run reports every test")
        check(first.map { it.string("state") } == listOf("passed") + failed.map { "failed" }) { "The run: ${console.results()}" }
        console.tests.reset(console.project)
        console.tests.rerunFailed(console.project, data.string("runAll"))
        val rerun = console.ended(failed, "Rerun Failed Tests reports the failed tests")
        check(rerun.all { it.string("state") == "failed" }) { "The rerun: ${console.results()}" }
        check(
            console.results().keySet() == failed.map(console::location).toSet(),
        ) { "The rerun runs only the failed tests: ${console.results()}" }
        closeEditor(document)
    }

    case("X347") { data ->
        val document = testModule(data.string("file"), data.string("module"), data.string("source"))
        val console = TestConsole(this)
        console.tests.reset(console.project)
        console.run(document, data.string("runAll"))
        val tests = data.getAsJsonObject("tests").entrySet().associate { (id, line) -> id to line.asInt }
        val ran = console.ended(tests.keys.toList(), "Run reports every test")
        tests.entries.zip(ran).forEach { (test, result) ->
            // the console goes to the test's declaration, the line of its name
            check(result.int("line") == test.value - 1 && result.string("file").endsWith(data.string("file"))) { "${test.key}: $result" }
        }
        val deep = checkNotNull(console.result(data.string("failed")))
        check(deep.string("state") == "failed" && data.string("failure") in deep.string("message")) { "deep: ${deep.shown()}" }
        closeEditor(document)
    }

    case("X348") { data ->
        val modules = data.getAsJsonArray("modules").map { it.asJsonObject }
        val documents = modules.map { open(it.string("file"), it.string("source")) }
        configure(modules.map { SharedScenarios.SourceModule(it.string("module"), uri(it.string("file")), emptyList()) })
        documents.forEach(::clean)
        val console = TestConsole(this)
        console.tests.reset(console.project)
        // IntelliJ runs a module's tests from its module's icon, a module at a time
        modules.zip(documents).forEach { (module, document) -> console.run(document, module.string("runAll")) }
        val passed = data.strings("passed")
        val reports = console.ended(passed + data.string("failed"), "every module's tests run")
        check(reports.dropLast(1).all { it.string("state") == "passed" }) { "The runs: ${console.results()}" }
        check(reports.last().string("state") == "failed" && data.string("failure") in reports.last().string("message")) {
            "three: ${reports.last().shown()}"
        }
        documents.forEach(::closeEditor)
    }

    case("X349") { data ->
        val module = data.string("module")
        val file = projectRoot.resolve(data.string("file"))
        val compiled = data.strings("compiled").map(projectRoot::resolve)
        compiled.plusElement(file).forEach { check(!Files.exists(it)) { "X349 needs a project root without $it" } }
        Files.writeString(file, data.string("source"))
        val document = open(file, data.string("file"))
        val console = TestConsole(this)
        try {
            console.tests.reset(console.project)
            console.tests.runCommandLine(console.project, module, file.toUri().toString())
            val (passed, failed) = console.ended(listOf(data.string("passed"), data.string("failed")), "xtc test reports both tests")
            check(passed.string("state") == "passed") { "adds: ${passed.shown()}" }
            check(failed.string("state") == "failed" && data.string("failure") in failed.string("message")) { "fails: ${failed.shown()}" }
        } finally {
            closeEditor(document)
            Files.deleteIfExists(file)
            compiled.forEach(Files::deleteIfExists)
            refresh(projectRoot)
        }
    }
}
