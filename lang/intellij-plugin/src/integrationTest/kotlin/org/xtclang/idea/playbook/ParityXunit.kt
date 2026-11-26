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

/**
 * X348 in Gradle mode, which runs every project's testXtc in one build: here a wrapper that records what it is asked to
 * run and prints xunit's events of the [modules]' tests, as such a build's `json` report does, each event's ID naming its
 * module. Without an imported model, which names each module's project, the build runs the bare testXtc and its console
 * has no Rerun Failed Tests; with one, each project's testXtc, and Rerun Failed Tests reruns the failed test in its own
 * module's project only.
 */
private fun ParityWorkspace.gradleTests(
    data: JsonObject,
    modules: List<JsonObject>,
    console: TestConsole,
) {
    val rootFiles = listOf("gradleFile", "wrapper", "windowsWrapper", "invocations", "events").map { projectRoot.resolve(data.string(it)) }
    rootFiles.forEach { check(!Files.exists(it)) { "X348 needs a project root without $it" } }
    // Gradle's project cache, which holds the imported model, as X129's
    val cache = projectRoot.resolve(".gradle")
    val report = cache.resolve(data.string("model"))
    check(!Files.exists(report)) { "X348 needs a project root without $report" }
    val made = !Files.exists(cache)
    val invocations = projectRoot.resolve(data.string("invocations"))

    fun calls() = if (Files.exists(invocations)) Files.readAllLines(invocations).filter { it.isNotBlank() } else emptyList()

    fun projects() = JsonParser.parseString(console.tests.gradleProjects(console.project))

    val passed = data.strings("passed")
    val failed = data.string("failed")
    val runAll = data.string("runAll")
    val execution = with(driver) { utility(EmbeddedExecutionProbe::class) }
    val previous = mode(execution, console.project, "GRADLE")
    try {
        writeGradleWrapper(data)
        Files.writeString(projectRoot.resolve(data.string("events")), xunitEvents(passed, listOf(failed), data.string("failure")))

        // without an imported model: the bare testXtc, every project's, and no project to send a rerun to
        awaitUi("no Gradle projects without a model", HANG_LIMIT, errorMessage = { projects().toString() }) {
            projects() == JsonObject()
        }
        console.tests.reset(console.project)
        console.tests.runAllTests(console.project)
        val bare = console.ended(passed + failed, "the Gradle build reports every module's tests")
        console.runsEnded("the Gradle build ends")
        check(bare.map { it.string("state") } == passed.map { "passed" } + "failed") { "The Gradle build: ${console.results()}" }
        check(calls() == listOf(data.string("gradleRun"))) { "Run 'All Ecstasy Tests' in Gradle mode: ${calls()}" }
        check(!with(driver) { withContext(OnDispatcher.EDT) { console.tests.rerunFailedOffered(console.project, runAll) } }) {
            "Without a model, the Gradle build has no Rerun Failed Tests"
        }

        // the model that an import writes, whose source sets name each module's project, which the plugin gives the
        // language server once no explicit modules replace it, as it reads the modules from it
        val paths = data.getAsJsonObject("projects")
        Files.createDirectories(report.parent)
        Files.writeString(report, Gson().toJson(model(modules, paths)))
        configure(null)
        awaitUi("the model's Gradle projects", HANG_LIMIT, errorMessage = { projects().toString() }) { projects() == paths }
        console.tests.reset(console.project)
        console.tests.runAllTests(console.project)
        val built = console.ended(passed + failed, "each project's testXtc reports its module's tests")
        console.runsEnded("the projects' build ends")
        check(built.map { it.string("state") } == passed.map { "passed" } + "failed") { "The projects' build: ${console.results()}" }
        check(calls().drop(1) == listOf(data.string("gradleProjectsRun"))) { "Each project's testXtc: ${calls()}" }

        // Rerun Failed Tests: the failed test's module's project only, with its module and the test
        console.tests.reset(console.project)
        console.tests.rerunFailed(console.project, runAll)
        awaitUi("Rerun Failed Tests runs Gradle", HANG_LIMIT, errorMessage = { calls().toString() }) { calls().size == 3 }
        console.runsEnded("the rerun's build ends")
        check(calls().last() == data.string("gradleRerun")) { "Rerun Failed Tests in Gradle mode: ${calls()}" }
    } finally {
        mode(execution, console.project, previous)
        rootFiles.forEach(Files::deleteIfExists)
        Files.deleteIfExists(report)
        if (made) cache.toFile().deleteRecursively()
        configure(emptyList())
    }
}

/**
 * The model that a Gradle import writes of [modules], `lsp-model.json`: each module the test source set of its own
 * project, whose path in the build tree [paths] gives by the module's name.
 */
private fun ParityWorkspace.model(
    modules: List<JsonObject>,
    paths: JsonObject,
): Map<String, Any> =
    mapOf(
        "schemaVersion" to 1,
        "sourceSets" to
            modules.map { module ->
                val path = paths.string(module.string("module"))
                val file = uri(module.string("file"))
                mapOf(
                    "projectId" to "${directory.toUri()}#$path",
                    "projectPath" to path,
                    "projectDirectory" to directory.toUri().toString(),
                    "buildFile" to directory.resolve("build.gradle.kts").toUri().toString(),
                    "sourceSet" to "test",
                    "sourceRoots" to listOf(directory.toUri().toString()),
                    "sourceFiles" to listOf(file),
                    "moduleRoots" to listOf(file),
                    "resourceSourceRoots" to emptyList<String>(),
                    "resourceRoots" to emptyList<String>(),
                    "resourceTask" to "$path:processTestXtcResources",
                    "projectDependencies" to emptyList<String>(),
                    "modulePath" to emptyList<String>(),
                    "buildTreePath" to path,
                )
            },
    )

/**
 * xunit's events of the tests [passed] and [failed], by their IDs as VS Code's, `<module>/<selector>`, each a line as a
 * Gradle build's `json` report prints them, with IDs as xunit's `UniqueId.path`, which start with the module's name;
 * each failed test fails with [failure].
 */
private fun xunitEvents(
    passed: List<String>,
    failed: List<String>,
    failure: String,
): String {
    val tests = passed.map { it to "passed" } + failed.map { it to "failed" }
    val events =
        listOf(mapOf("event" to "run", "version" to 1)) +
            tests.groupBy { it.first.substringBefore('/') }.flatMap { (module, results) ->
                val root = "$module:"
                val started = mapOf("event" to "started", "id" to root, "name" to module, "selector" to "", "container" to true)
                listOf(started) +
                    results.flatMap { (id, status) ->
                        val selector = id.substringAfter('/')
                        val test = "$module:void $selector()"
                        listOf(
                            mapOf("event" to "started", "id" to test, "parent" to root, "name" to selector, "selector" to selector),
                            mapOf("event" to "finished", "id" to test, "status" to status) +
                                if (status == "failed") mapOf("message" to failure) else emptyMap(),
                        )
                    } +
                    mapOf("event" to "finished", "id" to root, "status" to "passed")
            }
    return events.joinToString("") { "##xunit ${Gson().toJson(it)}\n" }
}

/** The state and message of a result, for a check's message. */
private fun JsonObject.shown() = "${string("state")}: ${get("message")?.asString}"

/**
 * X342-X349: xunit tests stopped partway, added while the project is open, thrown, skipped, rerun when they failed, at
 * their sources, in several modules, run with Run 'All Ecstasy Tests' and rerun when they failed, and run by the xtc
 * command line, in IntelliJ's test console.
 */
internal fun ParityScenarios.xunitCases() {
    case("X342") { data ->
        val document = openModule(data)
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
            protocol.status()["embeddedRuns"]?.asInt == 0
        }
        closeEditor(document)
    }

    case("X343") { data ->
        val document = openModule(data)
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
        val document = openModule(data)
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
        val document = openModule(data)
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
        val document = openModule(data)
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
        val document = openModule(data)
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
        // Run 'All Ecstasy Tests' runs each module's tests in turn into one console, whose locations name each test's module
        val console = TestConsole(this, allModules = true)
        val passed = data.strings("passed")
        val failed = data.string("failed")
        console.tests.reset(console.project)
        console.tests.runAllTests(console.project)
        val reports = console.ended(passed + failed, "every module's tests run")
        check(reports.dropLast(1).all { it.string("state") == "passed" }) { "The runs: ${console.results()}" }
        check(reports.last().string("state") == "failed" && data.string("failure") in reports.last().string("message")) {
            "three: ${reports.last().shown()}"
        }
        // each test goes to its own module's source, though both modules have a three
        (passed + failed).zip(reports).forEach { (id, report) ->
            val file = modules.single { it.string("module") == id.substringBefore('/') }.string("file")
            check(
                report
                    .get("file")
                    ?.asString
                    .orEmpty()
                    .endsWith("/$file"),
            ) { "$id goes to $report" }
        }
        console.runsEnded("Run 'All Ecstasy Tests' ends")
        // its Rerun Failed Tests runs only the failed test, in its own module, and not the other module's of its name
        console.tests.reset(console.project)
        console.tests.rerunFailed(console.project, data.string("runAll"))
        val (rerun) = console.ended(listOf(failed), "Rerun Failed Tests reports the failed test")
        console.runsEnded("Rerun Failed Tests ends")
        check(rerun.string("state") == "failed" && data.string("failure") in rerun.string("message")) { "The rerun: ${rerun.shown()}" }
        check(console.results().keySet() == setOf(console.location(failed))) {
            "The rerun runs only the failed test, of its module: ${console.results()}"
        }
        gradleTests(data, modules, console)
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
