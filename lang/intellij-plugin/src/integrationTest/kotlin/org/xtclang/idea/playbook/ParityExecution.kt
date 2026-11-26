package org.xtclang.idea.playbook

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import org.xtclang.idea.playbook.probe.HANG_SECONDS
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Embedded build and run through Build Project, Ecstasy run configurations, Stop, console input and the Run lens. */
internal fun ParityScenarios.executionCases() {
    case("X280") { data ->
        write(data.string("libraryFile"), data.string("librarySource"))
        val runnable = open(data.string("file"), data.string("source"))
        val library = open(data.string("libraryFile"))
        clean(runnable)
        clean(library)
        val probe = with(driver) { utility(CodeLensProbe::class) }

        fun runTitles(document: ParityWorkspace.Document) =
            with(driver) { withContext(OnDispatcher.EDT) { probe.titles(document.editor.editor) } }.filter { it.startsWith("▶ Run") }

        // IntelliJ also shows a Run icon in the gutter, on the line of the Run lens (the module declaration)
        val gutter = with(driver) { utility(RunGutterProbe::class) }

        fun runIcons(document: ParityWorkspace.Document) =
            with(driver) { withContext(OnDispatcher.EDT) { gutter.icons(document.editor.editor) } }

        awaitUi("Run lens on a module with run()", HANG_LIMIT) { data.string("runTitle") in runTitles(runnable) }
        awaitUi("Run icon in the gutter of a module with run()", HANG_LIMIT) {
            runIcons(runnable) ==
                listOf("Run ${data.string("module")}@0")
        }
        check(runTitles(library).isEmpty()) { "No Run lens on a module without run(): ${runTitles(library)}" }
        check(runIcons(library).isEmpty()) { "No Run icon on a module without run(): ${runIcons(library)}" }
        replace(library, data.string("uncallable"))
        clean(library)
        check(runTitles(library).isEmpty()) { "No Run lens when run() needs two arguments: ${runTitles(library)}" }
        check(runIcons(library).isEmpty()) { "No Run icon when run() needs two arguments: ${runIcons(library)}" }
        replace(library, data.string("runnableLibrary"))
        clean(library)
        awaitUi("Run lens after adding run()", HANG_LIMIT) { data.string("libraryRunTitle") in runTitles(library) }
        awaitUi("Run icon after adding run()", HANG_LIMIT) { runIcons(library) == listOf("Run ${data.string("libraryModule")}@0") }
        // clicking the icon runs the module, as the lens does; a run builds a configured source module
        val module = data.string("module")
        configure(listOf(SharedScenarios.SourceModule(module, uri(data.string("file")), emptyList())))
        clean(runnable)
        awaitUi("Run icon on the configured module", HANG_LIMIT) { runIcons(runnable) == listOf("Run $module@0") }
        embedded { execution, project ->
            execution.expect(project, module)
            with(driver) { withContext(OnDispatcher.EDT) { gutter.click(runnable.editor.editor, "Run $module") } }
            awaitUi("run from the gutter icon finishes", HANG_LIMIT) { execution.exitCode(module) != Int.MIN_VALUE }
            check(execution.exitCode(module) == 0) { "Exit code ${execution.exitCode(module)}: ${execution.output(module)}" }
        }
    }

    case("X281") { data ->
        write(data.string("libraryFile"), data.string("librarySource"))
        write(data.string("appFile"), data.string("appSource"))
        configure(
            listOf(
                SharedScenarios.SourceModule(data.string("libraryModule"), uri(data.string("libraryFile")), emptyList()),
                SharedScenarios.SourceModule(data.string("appModule"), uri(data.string("appFile")), listOf(data.string("libraryModule"))),
            ),
        )
        val library = open(data.string("libraryFile"))
        val app = open(data.string("appFile"))
        clean(app)
        embedded { execution, project ->
            val built = execution.build(project)
            val text = built.string("text")
            checkBuilt(built)
            data.strings("built").forEach { check(it in text) { "$it in $text" } }
            check(data.string("artifact") in text.replace('\\', '/')) { text }

            replace(library, data.string("brokenLibrary"))
            val file = directory.resolve(data.string("libraryFile"))
            check(Files.readString(file) == data.string("librarySource")) { "The broken library is unsaved before the build" }
            val failed = execution.build(project)
            val failure = failed.string("text")
            // the build compiles the editor's text without saving it
            check(Files.readString(file) == data.string("librarySource")) { "Build leaves the broken library unsaved" }
            check(failed["errors"].asBoolean) { "The build must fail: $failure" }
            check(data.string("failed") in failure) { failure }
            check(data.string("skipped") in failure) { failure }
            check(failure.lines().any { data.string("diagnostic") in it && ": error: " in it }) { failure }
        }
    }

    case("X282") { data ->
        openModule(data)
        embedded { execution, project ->
            val (module, file) = data.string("module") to uri(data.string("file"))
            val arguments = data.strings("args").joinToString(",")
            val output = execution.runToExit(project, module, file, arguments, "embedded run finishes", data.int("exitCode"))
            check(data.string("output") in output) { output }
            check("Process finished with exit code ${data.int("exitCode")}" in output) { output }
        }
    }

    case("X283") { data ->
        val forever = open(data.string("file"), data.string("source"))
        val next = open(data.string("nextFile"), data.string("nextSource"))
        configure(
            listOf(
                SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList()),
                SharedScenarios.SourceModule(data.string("nextModule"), uri(data.string("nextFile")), emptyList()),
            ),
        )
        clean(forever)
        clean(next)
        embedded { execution, project ->
            val module = data.string("module")
            execution.run(project, module, uri(data.string("file")), "")
            awaitUi("long-running program starts", HANG_LIMIT) { data.string("started") in execution.output(module) }
            // Rerun from the Run tool window stops the program and starts a new run
            execution.rerun(project, module)
            awaitUi("rerun starts a new run", HANG_LIMIT) { data.string("started") in execution.output(module) }
            execution.stop(module)
            awaitUi("stopped program reports its exit", HANG_LIMIT) { execution.exitCode(module) != Int.MIN_VALUE }

            // the worker is free again: the native Run lens runs the next program
            val nextModule = data.string("nextModule")
            execution.expect(project, nextModule)
            val probe = with(driver) { utility(CodeLensProbe::class) }
            val title = "▶ Run $nextModule"
            open(data.string("nextFile"))
            awaitUi("Run lens on the next program", HANG_LIMIT) {
                title in with(driver) { withContext(OnDispatcher.EDT) { probe.titles(next.editor.editor) } }
            }
            with(driver) {
                focusEditor(next.editor)
                withContext(OnDispatcher.EDT) { probe.click(next.editor.editor, title) }
            }
            awaitUi("next program finishes", HANG_LIMIT) { execution.exitCode(nextModule) != Int.MIN_VALUE }
            check(execution.exitCode(nextModule) == data.int("nextExitCode")) {
                "Exit code ${execution.exitCode(nextModule)}: ${execution.output(nextModule)}"
            }
        }
    }

    case("X284") { data ->
        open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        embedded { execution, project ->
            val output = execution.runToExit(project, data.string("module"), uri(data.string("file")), "", "failed build ends the run", 1)
            check(data.string("diagnostic") in output) { output }
            check(data.string("message") in output) { output }
            check(!output.lines().any { it.startsWith("Process finished") && "exit code 0" in it }) { "Nothing ran: $output" }
        }
    }

    case("X285") { data ->
        openModule(data)
        embedded { execution, project ->
            val module = data.string("module")
            val runners =
                JsonParser
                    .parseString(
                        with(driver) { withContext(OnDispatcher.EDT) { execution.runners(project, module) } },
                    ).asJsonObject
            check(runners["run"].asBoolean) { "Run must be available: $runners" }
            check(runners["debug"].asBoolean) { "Debug must be offered with the embedded compiler: $runners" }
            execution.runToExit(project, module, uri(data.string("file")), "", "embedded run finishes", data.int("exitCode"))

            // Debug runs the module in a debug session in the language server, to the same exit code
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            debugger.debug(project, module, uri(data.string("file")), 0)
            val ended = debugger.awaitState(project, module, "debug session ends with the program", HANG_LIMIT) { it.has("exitCode") }
            check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
        }
    }
}

/** Console input for embedded runs; registered after the adapter chooser to keep catalog order. */
internal fun ParityScenarios.executionInputCases() {
    case("X287") { data ->
        openModule(data)
        embedded { execution, project ->
            val module = data.string("module")
            execution.run(project, module, uri(data.string("file")), "")
            awaitUi("program prompts for input", HANG_LIMIT) { data.string("prompt") in execution.output(module) }
            execution.input(module, data.string("input") + "\n")
            awaitUi("program reads the typed line", HANG_LIMIT) { data.string("greeting") in execution.output(module) }
            execution.endInput(module)
            awaitUi("program ends after the end of input", HANG_LIMIT) { execution.exitCode(module) != Int.MIN_VALUE }
            val output = execution.output(module)
            check(execution.exitCode(module) == data.int("exitCode")) { "Exit code ${execution.exitCode(module)}: $output" }
            check(data.string("ended") in output) { output }
        }
    }
}

/** Build modes beside a Gradle build and repeated runs; registered after console input to keep catalog order. */
internal fun ParityScenarios.buildModeCases() {
    case("X288") { data ->
        withGradleWrapper(data, "EMBEDDED") { execution, project, calls ->
            openModule(data)
            val module = data.string("module")

            fun runToEnd(message: String): String = execution.runToExit(project, module, uri(data.string("file")), "", message, 0)

            // the default is embedded, also beside a Gradle build, and says which of the build's settings apply
            val built = execution.build(project)
            val text = built.string("text")
            checkBuilt(built)
            check(EMBEDDED_BUILD in text) { text }
            check(data.string("notice") in text) { text }
            // the notice links to the settings page that chooses the mode
            check(built["links"].asJsonArray.any { it.asString == SETTINGS_PAGE }) { "No link to $SETTINGS_PAGE: ${built["links"]}" }
            // which stands with every other page of the plugin under one node, and is linked beside the build tools
            val paths =
                with(driver) {
                    withContext(OnDispatcher.EDT) {
                        SETTINGS_PAGES.keys.associateWith { execution.settingsPath(project, it) }
                    }
                }
            check(paths == SETTINGS_PAGES) { "Settings pages: $paths" }
            runToEnd("embedded run finishes").let { check(data.string("output") in it) { it } }
            check(calls().isEmpty()) { "Embedded mode must not call Gradle: ${calls()}" }

            // Gradle: Build Project is left to the IDE's builder, and Run calls the Gradle wrapper
            mode(execution, project, "GRADLE")
            gradleBuildAndRun(execution, project, data, calls)

            // and back: Run is embedded again
            mode(execution, project, "EMBEDDED")
            runToEnd("embedded run after switching back finishes").let { check(data.string("output") in it) { it } }
            check(calls().size == 1) { "No Gradle call after switching back: ${calls()}" }
        }
    }

    case("X289") { data ->
        val document = openModule(data)
        embedded { execution, project ->
            fun runOnce(message: String): String =
                execution.runToExit(project, data.string("module"), uri(data.string("file")), "", message, data.int("exitCode"))
            runOnce("first run finishes").let { check(data.string("first") in it) { it } }
            runOnce("second run of the unchanged module finishes").let { check(data.string("first") in it) { it } }
            // edited and not saved: the run builds and runs the edit, not the earlier build; IntelliJ itself saves
            // the file before a run
            replace(document, data.string("edited"))
            val file = directory.resolve(data.string("file"))
            check(Files.readString(file) == data.string("source")) { "The edit is unsaved before the run" }
            val edited = runOnce("run of the edited module finishes")
            check(data.string("second") in edited && data.string("first") !in edited) { edited }
            check(Files.readString(file) == data.string("edited")) { "IntelliJ saved the edit before the run" }
        }
    }
}

/**
 * Writes [data]'s Gradle build at the project root, with a wrapper that records what it is asked to do, and runs [body]
 * in the build mode [initial] with the wrapper's calls so far; then removes the build, with the project's build mode as it
 * was.
 */
private fun ParityWorkspace.withGradleWrapper(
    data: JsonObject,
    initial: String,
    body: (EmbeddedExecutionProbe, Project, () -> List<String>) -> Unit,
) {
    val rootFiles = listOf("gradleFile", "wrapper", "windowsWrapper", "invocations").map { projectRoot.resolve(data.string(it)) }
    val invocations = projectRoot.resolve(data.string("invocations"))

    fun calls() = if (Files.exists(invocations)) Files.readAllLines(invocations).filter { it.isNotBlank() } else emptyList()
    val project = with(driver) { singleProject() }
    val execution = with(driver) { utility(EmbeddedExecutionProbe::class) }
    val previous = mode(execution, project, initial)
    try {
        writeGradleWrapper(data)
        body(execution, project, ::calls)
    } finally {
        mode(execution, project, previous)
        rootFiles.forEach(Files::deleteIfExists)
    }
}

/** Build Project and Run of [data]'s module in Gradle mode: the IDE's builder takes the build, and Run calls the wrapper once. */
private fun ParityWorkspace.gradleBuildAndRun(
    execution: EmbeddedExecutionProbe,
    project: Project,
    data: JsonObject,
    calls: () -> List<String>,
) {
    val gradleBuild = execution.build(project).string("text")
    check(EMBEDDED_BUILD !in gradleBuild) { "The embedded compiler must not take a Gradle-mode build: $gradleBuild" }
    val gradleRun = execution.runToExit(project, data.string("module"), uri(data.string("file")), "", "Gradle run finishes", 0)
    check(data.string("gradleRun") in gradleRun) { gradleRun }
    check(calls().size == 1 && data.string("gradleRun") in calls().single()) { "Run calls the Gradle wrapper once: ${calls()}" }
}

/** X354, registered after X353 in catalog order: Build and Run in the automatic build mode, beside a Gradle build and without one. */
internal fun ParityScenarios.automaticBuildModeCases() {
    case("X354") { data ->
        withGradleWrapper(data, "AUTOMATIC") { execution, project, calls ->
            openModule(data)
            // automatic: Gradle while the project has a Gradle build
            gradleBuildAndRun(execution, project, data, calls)

            // and the embedded compiler once the project has none, with no choice made again
            Files.delete(projectRoot.resolve(data.string("gradleFile")))
            val output = execution.runToExit(project, data.string("module"), uri(data.string("file")), "", "embedded run finishes", 0)
            check(data.string("output") in output) { output }
            check(calls().size == 1) { "No Gradle call without a Gradle build: ${calls()}" }
        }
    }
}

/** Fresh program state, refused entry methods and dropped arguments; registered after the on-type cases, as the catalog orders X292–X294. */
internal fun ParityScenarios.runEntryCases() {
    case("X292") { data ->
        openModule(data)
        embedded { execution, project ->
            val (module, file) = data.string("module") to uri(data.string("file"))
            repeat(2) { round ->
                val output = execution.runToExit(project, module, file, "", "run ${round + 1} finishes", data.int("exitCode"))
                // the counter keeps its count within a run, and a run that went on from the run before it would
                // print the counts after that run's
                check(data.string("output") in output && data.string("carriedOver") !in output) { output }
            }
        }
    }

    case("X293") { data ->
        val library = open(data.string("file"), data.string("source"))
        val twoArguments = open(data.string("twoArgumentsFile"), data.string("twoArgumentsSource"))
        configure(
            listOf(
                SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList()),
                SharedScenarios.SourceModule(data.string("twoArgumentsModule"), uri(data.string("twoArgumentsFile")), emptyList()),
            ),
        )
        clean(library)
        clean(twoArguments)
        embedded { execution, project ->
            // neither module has a Run lens, so each runs from an Ecstasy run configuration
            fun refused(
                module: String,
                file: String,
                expected: String,
            ): String {
                val output = execution.runToExit(project, module, uri(file), "", "run of $module is refused", data.int("exitCode"))
                check(expected in output) { output }
                // the reason alone, as xtc run reports it, not a failure to create the program's container
                check(data.string("containerFailure") !in output) { output }
                return output
            }
            refused(data.string("module"), data.string("file"), data.string("message"))
            val output = refused(data.string("twoArgumentsModule"), data.string("twoArgumentsFile"), data.string("twoArgumentsMessage"))
            check(data.string("twoArgumentsOutput") !in output) { "Nothing ran: $output" }
        }
    }

    case("X294") { data ->
        openModule(data)
        embedded { execution, project ->
            val (module, file) = data.string("module") to uri(data.string("file"))
            val arguments = data.strings("args").joinToString(",")
            val output = execution.runToExit(project, module, file, arguments, "run with dropped arguments finishes", data.int("exitCode"))
            val warning = output.indexOf(data.string("warning"))
            check(
                warning >= 0 && warning < output.indexOf(data.string("output")),
            ) { "The warning comes before the program's output: $output" }
        }
    }

    case("X303") { data ->
        gradleProject("X303", data) { project, execution ->
            val module = data.string("module")
            val file = projectRoot.resolve(data.string("moduleFile"))

            fun gradleRun(
                message: String,
                expected: Int? = null,
            ): String = execution.runToExit(project, module, file.toUri().toString(), data.string("args"), message, expected)

            // the Ecstasy Application configuration calls ./gradlew runXtc, which compiles the module (not its tests) first
            val output = gradleRun("Gradle run finishes", 0)
            check(data.string("output") in output) { output }
            val artifact = data.strings("artifacts").first()
            check(Files.exists(projectRoot.resolve(artifact))) { "The Gradle run builds $artifact: $output" }

            // a type error in the saved module fails the compile, and the run reports it instead of running
            Files.writeString(file, data.string("broken"))
            val failed = gradleRun("Gradle run of the broken module finishes")
            check(execution.exitCode(module) != 0) { "The broken module must not run: $failed" }
            check(data.string("brokenCode") in failed && data.string("brokenLocation") in failed) { failed }
        }
    }

    case("X304") { data ->
        openModule(data)
        debugging(data) {
            val line = data.int("breakpointLine")
            debugger.debug(project, module, uri(data.string("file")), line)
            val stopped = awaitStop()
            checkStoppedAt(stopped, line, data.string("file"))
            val frames = stopped.frames()
            // the program's frames are the whole stack, without the runner that hosts it
            check(frames.size == data.getAsJsonArray("frames").size()) { "The stack is not the program's frames: $frames" }
            checkFrames(frames, data.getAsJsonArray("frames"))
            val console = stopped.string("console")
            check(data.string("before") in console && data.string("after") !in console) { "Console while paused: $console" }

            resumeToExit(data.int("exitCode"), data.string("after"))
        }
    }

    case("X305") { data ->
        openModule(data)
        debugging(data) {
            val line = data.int("breakpointLine")
            debugger.debug(project, module, uri(data.string("file")), line)
            val stopped = awaitStop()
            check(stopped.int("line") == line) { "Stopped at: $stopped" }

            val locals = variables("")
            data.getAsJsonArray("locals").map { it.asJsonArray }.forEach { expected ->
                val variable = locals.firstOrNull { it.string("name") == expected[0].asString }
                // LSP4IJ shows strings and numbers without their type
                val type = variable?.get("type")?.asString.orEmpty()
                check(
                    variable != null && variable.string("value") == expected[1].asString && type in listOf("", expected[2].asString),
                ) {
                    "${expected[0].asString} is not ${expected[1].asString} of ${expected[2].asString}: $locals"
                }
            }
            check(locals.single { it.string("name") == "sum" }["expands"].asBoolean.not()) { "sum does not expand: $locals" }
            listOf("point" to "fields", "ints" to "elements").forEach { (name, key) ->
                val children = variables(name).map { listOf(it.string("name"), it.string("value")) }
                val expected = data.getAsJsonArray(key).map { pair -> pair.asJsonArray.map { it.asString } }
                check(children == expected) { "$name shows $children, not $expected" }
            }

            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X306") { data ->
        openModule(data)
        debugging(data) {
            debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
            awaitStop { it["line"]?.asInt == data.int("breakpointLine") }
            data.getAsJsonArray("steps").map { it.asJsonArray }.forEach { step ->
                val (kind, name, line) = Triple(step[0].asString, step[1].asString, step[2].asInt)
                debugger.step(project, module, kind)
                val stopped = awaitStop("step $kind stops at $name:$line") { it.isAt(name, line) }
                if (kind == "into") {
                    val caller = stopped.frames()[1]
                    val (expected, callerLine) = data.frame("caller")
                    check(expected in caller.string("name") && caller.int("line") == callerLine) { "Below $name: $caller" }
                }
            }

            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X307") { data ->
        openModule(data)
        debugging(data) {
            // no breakpoint: LSP4IJ turns the adapter's Uncaught Exceptions filter on, as it is by default
            debugger.debug(project, module, uri(data.string("file")), 0)
            val stopped = awaitStop("the program stops at the exception")
            val frames = stopped.frames()
            // the program's frames are the whole stack, without the runner that hosts it
            check(frames.size == data.getAsJsonArray("frames").size()) { "The stack is not the program's frames: $frames" }
            checkFrames(frames, data.getAsJsonArray("frames"))
            val locals = variables("")
            val exception = locals.first()
            check(exception.string("name") == data.string("variable") && data.string("exception") in exception.string("type")) {
                "The exception is not the frame's first variable: $locals"
            }
            val argument = data.strings("argument")
            check(locals.any { it.string("name") == argument[0] && it.string("value") == argument[1] }) { "No ${argument[0]}: $locals" }

            // the hint is the exceptionInfo answer: the exception, its message and its stack, from check()
            awaitExceptionHint(listOf(data.string("exception"), data.string("message"), "check"))

            resumeToExit(data.int("exitCode"), data.string("message"), "Resume lets the program fail")
        }
    }

    case("X308") { data ->
        openModule(data)
        debugging(data) {
            // no breakpoint: the program loops forever once it has printed
            debugger.debug(project, module, uri(data.string("file")), 0)
            debugger.awaitState(project, module, "the program runs", HANG_LIMIT) {
                data.string("started") in it["console"]?.asString.orEmpty()
            }

            debugger.pause(project, module)
            val paused = awaitStop("Pause stops the program")
            val top = paused.frames().first()
            check(data.string("frame") in top.string("name") && top.int("line") in data.getAsJsonArray("lines").map { it.asInt }) {
                "Paused at: $paused"
            }

            // the program never ends on its own: stopping the session ends it
            debugger.stopSession(project, module)
            debugger.awaitState(project, module, "stopping the session ends the program", HANG_LIMIT) { it.has("exitCode") }
        }
    }

    case("X309") { data ->
        openModule(data)
        debugging(data) {
            // no breakpoint, and the program throws nothing, so a stop is the configuration's stop on entry
            debugger.debugFromEntry(project, module, uri(data.string("file")))
            val stopped = awaitStop("the program stops at its first line")
            val top = stopped.frames().first()
            check(data.string("frame") in top.string("name") && top.int("line") <= data.int("lastLine")) { "Stopped at: $stopped" }
            check(data.string("output") !in stopped.string("console")) { "Nothing has run yet: ${stopped.string("console")}" }

            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X310") { data ->
        openModule(data)
        debugging(data) {
            debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
            awaitStop()

            checkWatches(data)
            check(evaluate(data.string("expands"))["expands"]?.asBoolean == true) { "${data.string("expands")} does not expand" }

            resumeToExit(data.int("exitCode"))
        }
    }

    case("X311") { data ->
        openModule(data)
        debugging(data) {
            val file = uri(data.string("file"))
            // the logpoint doesn't suspend, and logs its expression's value
            debugger.addLogpoint(project, file, data.int("logLine"), data.string("logExpression"))
            // the breakpoint stops at the hit its hit condition names, among those where its condition holds
            debugger.debugWhen(project, module, file, data.int("breakpointLine"), data.string("condition"), data.string("hitCondition"))
            val stopsAt = data.string("stopsAt")
            awaitUi("the program stops where i is $stopsAt", HANG_LIMIT) {
                runCatching { evaluate("i")["value"]?.asString }.getOrNull() == stopsAt
            }
            val ended = resumeToExit(data.int("exitCode"), message = "the program runs to its exit")
            checkLogged(ended, data.string("logValue"))
        }
    }

    case("X312") { data ->
        openModule(data)
        debugging(data) {
            // as LSP4IJ's exception breakpoints panel checks the filter with its condition, and unchecks the adapter's
            // Uncaught Exceptions, which is checked by default
            val filters =
                JsonObject().apply {
                    addProperty(data.string("filter"), data.string("condition"))
                    add("uncaught", JsonNull.INSTANCE)
                }
            val previous = debugger.exceptionFilters(filters.toString())
            try {
                debugger.debug(project, module, uri(data.string("file")), 0)
                val stopped = awaitStop("the program stops at the exception class the condition names")
                check(data.string("caught") in stopped.string("console")) { "The caught exception stopped the program: $stopped" }
                val (name, line) = data.frame("frame")
                val top = stopped.frames().first()
                check(name in top.string("name") && top.int("line") == line) { "Stopped at: $stopped" }

                // the hint is the exceptionInfo answer: the exception, its description and its stack, from check()
                awaitExceptionHint(listOf(data.string("exception"), data.string("description"), data.string("stackTrace")))

                resumeToExit(data.int("exitCode"), message = "Resume lets the program fail")
            } finally {
                debugger.exceptionFilters(previous)
            }
        }
    }

    case("X313") { data ->
        openModule(data)
        debugging(data) {
            val file = uri(data.string("file"))
            // the logpoint doesn't suspend, and logs its expression's value
            debugger.addLogpoint(project, file, data.int("logLine"), data.string("logExpression"))
            debugger.debugWhen(project, module, file, data.int("breakpointLine"), data.string("condition"), "")

            data.strings("conditionStops").forEachIndexed { stop, expected ->
                // a stop is the next one once i has the value it stops at
                awaitUi("the program stops where i is $expected", HANG_LIMIT) {
                    runCatching { evaluate("i")["value"]?.asString }.getOrNull() == expected
                }
                if (stop == 0) checkWatches(data)
                debugger.resume(project, module)
            }
            val ended = debugger.awaitState(project, module, "the program runs to its exit", HANG_LIMIT) { it.has("exitCode") }
            check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
            checkLogged(ended, data.string("logValue"))
        }
    }

    case("X314") { data ->
        openModule(data)
        debugging(data) {
            debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
            awaitStop()

            // the Evaluate Expression dialog's evaluations run code: LSP4IJ sends them as the console's (repl)
            fun run(code: String) = JsonParser.parseString(debugger.evaluateFrom(project, module, code, "DIALOG")).asJsonObject
            val (call, value) = data.strings("call")
            val rebuilt = debugger.viewRebuilds(project, module)
            val ran = run(call)
            check(ran["value"]?.asString == value) { "$call is not $value: $ran" }
            // the run invalidates the variables, so LSP4IJ rebuilds the session's views
            awaitUi("the run invalidates the variables", HANG_LIMIT) { debugger.viewRebuilds(project, module) > rebuilt }
            val (watch, watched) = data.strings("watch")
            val shown = evaluate(watch)
            check(shown["value"]?.asString == watched) { "$watch is not $watched after $call: $shown" }
            // TODO LSP4IJ: UP41 — LSP4IJ never sends DAP `cancel`, so a cancelled run is checked in VS Code only
            val ended = run(data.string("loop"))["error"]?.asString.orEmpty()
            check(data.string("limited") in ended) { "${data.string("loop")} does not end at its time limit: $ended" }

            resumeToExit(data.int("exitCode"))
        }
    }

    case("X315") { data ->
        openModule(data)
        debugging(data) {
            debugger.debugRunningProgram(project, module, uri(data.string("file")), data.int("breakpointLine"))
            awaitStop()

            // the worker's service answers once its fiber, which the stop holds otherwise, has done its work
            val (call, value) = data.strings("call")
            val rebuilt = debugger.viewRebuilds(project, module)
            val ran = JsonParser.parseString(debugger.evaluateFrom(project, module, call, "DIALOG")).asJsonObject
            check(ran["value"]?.asString == value) { "$call is not $value: $ran" }
            // the program ran, so the run invalidates the threads, stacks and variables, and LSP4IJ rebuilds the views
            awaitUi("the run invalidates the threads, stacks and variables", HANG_LIMIT) {
                debugger.viewRebuilds(project, module) > rebuilt
            }

            resumeToExit(data.int("exitCode"))
        }
    }

    case("X316") { data ->
        openModule(data)
        debugging(data) {
            debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
            awaitStop()

            // the object's fields, then its lazy variables, whose values run code only when asked for
            val expands = data.string("expands")
            val fields = variables(expands)
            val expected = data.getAsJsonArray("fields").map { pair -> pair.asJsonArray.map { it.asString } }
            check(fields.map { listOf(it.string("name"), it.string("value")) } == expected) { "$expands shows $fields" }
            data.getAsJsonArray("lazy").map { it.asJsonArray }.forEach { pair ->
                val (name, value) = pair[0].asString to pair[1].asString
                val lazy = fields.single { it.string("name") == name }
                // a lazy variable has a Show value link, which asks for its value
                check(lazy.has("link")) { "$name has no Show value link: $lazy" }
                val shown = debugger.showValue(project, module, "$expands/$name")
                check(shown == value) { "$name shows $shown, not $value" }
            }

            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X317") { data ->
        openModule(data)
        debugging(data) {
            debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
            awaitStop()

            fun locals() = variables("").associate { it.string("name") to it.string("value") }

            fun pairs(key: String) = data.getAsJsonArray(key).map { pair -> pair.asJsonArray.let { it[0].asString to it[1].asString } }
            val decimal = locals()
            pairs("decimal").forEach { (name, value) -> check(decimal[name] == value) { "$name is not $value: $decimal" } }
            // two objects with the same fields have different identity codes
            val identities = data.strings("identities").map { decimal.getValue(it) }
            identities.forEach { check(Regex(".+ #\\d+").matches(it)) { "No identity code: $identities" } }
            check(identities.distinct().size == identities.size) { "The identity codes are not different: $identities" }

            check(debugger.showHex(project, module, true)) { "No Show Values as Hexadecimal" }
            val hex = pairs("hex").toMap()
            awaitUi("the values show in hexadecimal", HANG_LIMIT) { locals().filterKeys { it in hex } == hex }
            debugger.showHex(project, module, false)
            val shown = pairs("decimal").toMap()
            awaitUi("the values show in decimal again", HANG_LIMIT) { locals().filterKeys { it in shown } == shown }

            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X318") { data ->
        openModule(data)
        debugging(data) {
            debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
            awaitStop()

            val expands = data.string("expands")
            val first = JsonParser.parseString(debugger.pagedVariables(project, module, expands, 0)).asJsonObject
            val elements = first.getAsJsonArray("children").map { it.asJsonObject }
            check(elements.first().string("name") == "[0]" && elements.first().string("value") == "0") { "First: ${elements.first()}" }
            // a page of elements, then IntelliJ's own "..." node, which reads the next page
            check(first["more"].asBoolean) { "The elements are not paged: ${elements.size} shown" }
            val total = data.int("elements")
            check(elements.size == data.int("shown") && first.int("remaining") == total - data.int("shown")) {
                "${elements.size} elements and ${first.int("remaining")} more"
            }
            val all = JsonParser.parseString(debugger.pagedVariables(project, module, expands, 10)).asJsonObject
            val every = all.getAsJsonArray("children").map { it.asJsonObject }
            val (name, value) = data.strings("last")
            check(every.size == total && !all["more"].asBoolean) { "${every.size} elements, more: ${all["more"]}" }
            check(every.last().string("name") == name && every.last().string("value") == value) { "Last: ${every.last()}" }

            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X319") { data ->
        openModule(data)
        debugging(data) {
            debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
            awaitStop()

            // the Variables view's Set Value: a value of another type is refused with the reason, and the variable
            // keeps its value; an expression of its type is its new value
            val (name, expression, value) = data.strings("set")
            val (other, reason) = data.strings("refused")

            fun shown() = variables("").single { it.string("name") == name }.string("value")
            val before = shown()
            val refused = debugger.setValue(project, module, name, other)
            check(reason in refused) { "Set Value of $other says $refused" }
            check(shown() == before) { "$name changed to ${shown()}" }
            check(debugger.setValue(project, module, name, expression).isEmpty()) { "Set Value of $expression failed" }
            check(shown() == value) { "$name is ${shown()}, not $value" }

            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X320") { data ->
        openModule(data)
        debugging(data) {
            debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
            awaitStop()

            // Basic Completion in Evaluate Expression, which LSP4IJ answers with the adapter's completions: after a
            // dot, the members of the value before it, those that start with what is typed of the name
            val members = data.strings("members")
            val (typed, name, completed) = data.strings("typed")
            val offered = JsonParser.parseString(debugger.complete(project, module, members.first(), members[1])).asJsonObject
            val names = offered.getAsJsonArray("items").map { it.asString }
            check(names.containsAll(members.drop(1))) { "${members.first()} offers $names" }
            val chosen = JsonParser.parseString(debugger.complete(project, module, typed, name)).asJsonObject
            val filtered = chosen.getAsJsonArray("items").map { it.asString }
            check(name in filtered && members.drop(1).filter { it != name }.none { it in filtered }) { "$typed offers $filtered" }
            // the chosen name replaces what is typed of it
            check(offered["inserted"].asBoolean && offered.string("text") == members.first() + members[1]) {
                "Choosing ${members[1]}: $offered"
            }
            check(chosen["inserted"].asBoolean && chosen.string("text") == completed) { "Completing $typed with $name: $chosen" }

            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X321") { data ->
        val document = openModule(data)
        debugging(data) {
            val file = uri(data.string("file"))
            debugger.debug(project, module, file, data.int("breakpointLine"))
            awaitStop()

            // lines typed above the stop, which the program was not built with, reach the server unsaved
            debugger.insert(project, file, data.int("insertLine"), data.strings("inserted").joinToString("") { "$it\n" })
            settle(document)

            // a breakpoint on a line that moved is placed on the line the program has for it, and the first one moved
            // with its line; one on a typed line is refused with the reason
            val (moved, typed, now) = listOf(data.int("moved"), data.int("typed"), data.int("stoppedNow"))
            debugger.addBreakpoint(project, file, moved)
            debugger.addBreakpoint(project, file, typed)
            awaitUi(
                "the breakpoints are placed on the lines the program has, or refused",
                HANG_LIMIT,
                getter = {
                    JsonParser
                        .parseString(debugger.breakpoints(project, module, file))
                        .asJsonArray
                        .map { it.asJsonObject }
                        .associateBy { it.int("line") }
                },
                checker = { states ->
                    states[moved]?.string("state") == "verified" &&
                        states[now]?.string("state") == "verified" &&
                        states[typed]?.string("state") == "invalid" &&
                        states[typed]?.string("message") == data.string("reason")
                },
            )

            // the next stop, on the moved line, shows its frames on their lines now
            debugger.resume(project, module)
            val stopped = awaitStop("the program stops at the moved line") { it["line"]?.asInt == moved }
            checkFrames(stopped.frames(), data.getAsJsonArray("frames"))

            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X322") { data ->
        openModule(data)
        embedded { execution, project ->
            val module = data.string("module")
            val file = uri(data.string("file"))
            val line = data.int("breakpointLine")
            for (round in 1..data.int("rounds")) {
                // a run to its exit
                val output = execution.runToExit(project, module, file, "", "run $round finishes")
                check(execution.exitCode(module) == 0 && data.string("done") in output) {
                    "Run $round: exit code ${execution.exitCode(module)}: $output"
                }

                // a run that loops until Stop ends it
                execution.run(project, module, file, data.string("spin"))
                awaitUi("looping run $round starts", HANG_LIMIT) { data.string("started") in execution.output(module) }
                check(
                    JsonParser.parseString(execution.active(project)).asJsonObject.int("runs") >= 1,
                ) { "Looping run $round is not active" }
                execution.stop(module)
                awaitUi("stopped run $round reports its exit", HANG_LIMIT) { execution.exitCode(module) != Int.MIN_VALUE }
                check(data.string("done") !in execution.output(module)) { "Stopped run $round: ${execution.output(module)}" }

                // a debug session that stops at a breakpoint and resumes to its exit
                debugRun(project, module) {
                    debugger.debug(project, module, file, line)
                    val stopped = awaitStop("debug session $round stops at the breakpoint")
                    check(stopped.int("line") == line) { "Debug session $round stopped at: $stopped" }
                    check(JsonParser.parseString(execution.active(project)).asJsonObject.int("debugSessions") >= 1) {
                        "Debug session $round is not open while it is stopped"
                    }
                    resumeToExit(0, data.string("done"), "debug session $round runs to its exit")
                }

                // the round leaves no run and no debug session open
                awaitUi("round $round leaves no run or debug session", HANG_LIMIT) {
                    JsonParser
                        .parseString(
                            execution.active(project),
                        ).asJsonObject
                        .let { it.int("runs") == 0 && it.int("debugSessions") == 0 }
                }
            }
        }
    }

    case("X323") { data ->
        openModule(data)
        debugging(data) {
            val file = uri(data.string("file"))
            val cursor = data.int("cursorLine")
            debugger.debug(project, module, file, data.int("breakpointLine"))
            awaitStop()

            // Run to Cursor, on a line in the loop after a long count
            debugger.runToCursor(project, module, file, cursor)
            // the count takes longer than the continue request's round trip: LSP4IJ keeps the temporary breakpoint until
            //  the program stops (#1773)
            val stopped =
                debugger.awaitState(project, module, "Run to Cursor stops at the cursor's line", HANG_LIMIT) {
                    it.has("exitCode") || it["suspended"]?.asBoolean == true
                }
            val atCursor = !stopped.has("exitCode") && stopped["line"]?.asInt == cursor
            check(atCursor) { "Run to Cursor did not stop at line $cursor: $stopped" }
            // the loop's first round, before it prints
            check(data.string("printed") !in stopped.string("console")) { "Console at the cursor: ${stopped.string("console")}" }

            // no breakpoint stays on the cursor's line: Resume runs the loop's other rounds to the exit, without a stop
            debugger.resume(project, module)
            val ended =
                debugger.awaitState(project, module, "Resume runs the program to its exit", HANG_LIMIT) {
                    it.has("exitCode") || it["suspended"]?.asBoolean == true
                }
            check(ended.has("exitCode")) { "Resume stopped again: $ended" }
            check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
            check(data.string("output") in ended.string("console")) { "Console after Resume: ${ended.string("console")}" }
        }
    }

    case("X324") { data ->
        openModule(data)
        debugging(data) {
            debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
            val stopped = awaitStop("the program stops in the lambda") { it.has("frames") }
            val frames = stopped.frames()
            data.strings("frames").forEachIndexed { index, name ->
                check(frames.getOrNull(index)?.string("name")?.contains(name) == true) { "Frame $index is not $name: $frames" }
            }

            // the library's frame is in the source bundled with it, at its line
            val library = frames[1]
            val file = library.string("file")
            check(file.endsWith(data.string("librarySource"))) { "The library's frame is in $file" }
            val text = Files.readAllLines(Path.of(file))[library.int("line") - 1]
            check(data.string("libraryText") in text) { "Line ${library.int("line")} of $file: $text" }

            // the lambda runs once, so Resume runs the program to its exit
            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X325") { data ->
        openModule(data)
        debugging(data) {
            // by then, run() has asked the busy worker for its count, and waits for the answer
            debugger.debugWhen(project, module, uri(data.string("file")), data.int("breakpointLine"), data.string("condition"), "")
            awaitStop("the worker stops at its count")

            // the stopped fiber first, then run()'s, which waits for the worker, with its own stack
            val threads =
                JsonParser
                    .parseString(debugger.threads(project, module))
                    .asJsonArray
                    .map { it.asJsonObject }
            // the worker takes in the call that run() waits for, as a fiber not started yet, when it next
            // schedules, before the stop or after it
            val names = threads.map { it.string("name") }
            check(names == data.strings("threads") || names == data.strings("threads") + data.string("queuedThread")) {
                "Threads: $threads"
            }
            val frames = threads[1].frames()
            val expected = data.getAsJsonArray("waitingFrames")
            check(frames.size == expected.size()) { "The waiting fiber's frames: $frames" }
            checkFrames(frames, expected)

            debugger.clearBreakpoints(project)
            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X326") { data ->
        openModule(data)
        debugging(data) {
            debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
            awaitStop("the program stops in the lambda")

            // the views list the program's file and its libraries' bundled sources, and say which modules are its own
            val own = data.getAsJsonObject("modules").entrySet().associate { it.key to it.value.asBoolean }
            var shown = JsonObject()

            fun paths() =
                shown
                    .getAsJsonArray("sources")
                    ?.map {
                        it.asJsonObject["path"]
                            ?.asString
                            .orEmpty()
                            .replace('\\', '/')
                    }.orEmpty()
            awaitUi("the Loaded Sources and Modules views show the program's", HANG_LIMIT, { "Views: $shown" }) {
                shown = JsonParser.parseString(debugger.loadedSources(project, module)).asJsonObject
                val modules =
                    shown.getAsJsonArray("modules").associate {
                        it.asJsonObject.string("name") to
                            it.asJsonObject["isUserCode"]?.asBoolean
                    }
                shown["sourcesShown"].asBoolean &&
                    shown["modulesShown"].asBoolean &&
                    paths().any { it.endsWith("/${data.string("file")}") } &&
                    paths().any { it.endsWith("/${data.string("librarySource")}") } &&
                    own.all { (name, isOwn) -> modules[name] == isOwn }
            }
            val library = paths().first { it.endsWith("/${data.string("librarySource")}") }
            check(
                !library.startsWith(project.getBasePath().orEmpty().replace('\\', '/')),
            ) { "A library source in the project: $library" }

            debugger.clearBreakpoints(project)
            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X327") { data ->
        openModule(data)
        debugging(data) {
            debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
            awaitStop("the program stops at the line with two calls")

            // the calls of the line, in the order it makes them, as Smart Step Into offers them
            val targets = JsonParser.parseString(debugger.smartStepTargets(project, module)).asJsonArray.map { it.asString }
            check(targets == data.strings("targets")) { "Smart Step Into variants: $targets" }

            // into the second call: the first runs without stopping, and the step ends in the chosen one
            debugger.smartStepInto(project, module, data.string("into"))
            val (name, line) = data.frame("frame")
            awaitStop("the step stops in the chosen call") { it.isAt(name, line) }

            debugger.clearBreakpoints(project)
            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X328") { data ->
        openModule(data)
        debugging(data) {
            // as the Breakpoints dialog adds it, with a condition on the method's parameter
            debugger.addFunctionBreakpoint(project, data.string("function"), data.string("condition"))
            debugger.debug(project, module, uri(data.string("file")), 0)

            // the second call, the first whose n is more than 5, below run()'s line that makes it
            val (name, line) = data.frame("frame")
            val (caller, callerLine) = data.frame("caller")
            val stopped = awaitStop("the program stops where it enters the method") { it["line"]?.asInt == line }
            val frames = stopped.frames()
            check(name in frames[0].string("name") && caller in frames[1].string("name") && frames[1].int("line") == callerLine) {
                "Frames: $frames"
            }
            val shown = JsonParser.parseString(debugger.functionBreakpoints(project, module)).asJsonArray.map { it.asJsonObject }
            check(shown.map { it.string("state") } == listOf("verified")) { "Function breakpoints: $shown" }

            resumeToExit(data.int("exitCode"), data.string("output"))
        }
    }

    case("X329") { data ->
        openModule(data)
        debugging(data) {
            debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
            awaitStop("the program stops in inner()")

            // the frames that the Frames view offers Reset Frame on: not run(), which the runtime called
            val offered = JsonParser.parseString(debugger.resettable(project, module)).asJsonArray.map { it.asBoolean }
            check(offered == data.getAsJsonArray("canRestart").map { it.asBoolean }) { "Reset Frame offered: $offered" }

            // as Reset Frame does: its caller calls it again, and it stops at its first line
            debugger.clearBreakpoints(project)
            debugger.resetFrame(project, module, data.string("restart"))
            val (name, line) = data.frame("frame")
            awaitStop("the restarted frame stops at its first line") { it.isAt(name, line) }

            val console = resumeToExit(data.int("exitCode"), data.string("output")).string("console")
            check(console.split(data.string("printed")).size - 1 == 2) { "Console after Resume: $console" }
        }
    }

    case("X330") { data ->
        openModule(data)
        debugging(data) {
            debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
            awaitStop("the program stops before the first add()")

            // as Break on Value Change on the counter's count, in the Variables view's menu, adds it
            check(debugger.breakOnValueChange(project, "${data.string("variable")}/${data.string("field")}")) {
                "Break on Value Change is not offered"
            }
            var watched = listOf<JsonObject>()
            awaitUi("the data breakpoint is installed", HANG_LIMIT, { "Data breakpoints: $watched" }) {
                watched = JsonParser.parseString(debugger.dataBreakpoints(project, module)).asJsonArray.map { it.asJsonObject }
                watched.singleOrNull()?.let { it.string("text") == data.string("description") && it.string("state") == "verified" } ==
                    true
            }

            // each add() stops at the line after the one that changed the count
            debugger.clearLineBreakpoints(project)
            val (name, line) = data.frame("frame")
            repeat(data.getAsJsonArray("changes").size()) { index ->
                debugger.resume(project, module)
                awaitStop("change ${index + 1} stops the program") { it.isAt(name, line) }
            }

            resumeToExit(data.int("exitCode"), data.string("output"))
            // the adapter's data ID lasts only the session, so the breakpoint goes with it
            awaitUi("the data breakpoint goes with the session", HANG_LIMIT) {
                JsonParser.parseString(debugger.dataBreakpoints(project, module)).asJsonArray.isEmpty
            }
        }
    }

    case("X331") { data ->
        openModule(data)
        debugging(data) {
            val file = uri(data.string("file"))
            debugger.debug(project, module, file, data.int("breakpointLine"))
            awaitStop("the program stops in add()")

            // the values IntelliJ draws at the ends of the lines, each variable of the stopped frame once
            val expected = data.getAsJsonObject("frameValues").entrySet().associate { it.key to it.value.asString }
            var shown = listOf<Pair<Int, String>>()
            awaitUi("the debugger draws the inline values", HANG_LIMIT, { "Inline values: $shown" }) {
                shown =
                    JsonParser.parseString(debugger.inlineValues(project, file)).asJsonArray.map {
                        it.asJsonObject.int("line") to it.asJsonObject.string("text")
                    }
                // the renderer's text is the name and the value, which it draws with " = " between them
                expected.all { (name, value) -> shown.any { (_, text) -> text.filterNot { it in " =" } == "$name$value" } }
            }
            check(shown.none { (_, text) -> text.startsWith("count") || text.startsWith("after") }) { "Inline values: $shown" }

            debugger.clearBreakpoints(project)
            resumeToExit(data.int("exitCode"))
        }
    }
}

/**
 * X336: a debug session attaches to a program that the XDK's xec runs with --debug outside the IDE, through the agent
 * that the XDK's javatools has.
 */
internal fun ParityScenarios.attachCases() {
    case("X336") { data ->
        openModule(data)
        // the scenarios are in lang/test-fixtures/compiler-playbook of the repository whose XDK the program runs with
        val repository =
            Path
                .of(System.getProperty("xtc.playbook.scenarios"))
                .toAbsolutePath()
                .parent.parent.parent.parent
        val windows = "win" in System.getProperty("os.name").lowercase()
        val xec = repository.resolve("xdk/build/install/xdk/bin").resolve(if (windows) "xec.bat" else "xec")
        check(Files.isRegularFile(xec)) { "X336 runs its program with $xec; run ./gradlew xdk:installDist first" }
        val project = with(driver) { singleProject() }
        val module = data.string("module")
        val line = data.int("breakpointLine")
        val output = Files.createTempDirectory("xtc-attach")
        val program =
            ProcessBuilder(
                xec.toString(),
                "-o",
                output.toString(),
                "--debug",
                "listen:127.0.0.1:0,suspend",
                Path.of(URI(uri(data.string("file")))).toString(),
            ).redirectErrorStream(true).start()
        val printed = StringBuffer()
        Thread.ofVirtual().start { program.inputReader().forEachLine { printed.append(it).append('\n') } }
        try {
            debugRun(project, module) {
                val listening = Regex(data.string("listening"))
                awaitUi("the program listens for the debugger", HANG_LIMIT, { "Printed: $printed" }) { listening.containsMatchIn(printed) }
                val port = listening.find(printed)!!.groupValues[1].toInt()
                check(data.string("before") !in printed) { "The program waits for the debugger: $printed" }

                debugger.attach(project, module, uri(data.string("file")), line, port)
                val stopped = awaitStop()
                checkStoppedAt(stopped, line, data.string("file"))
                checkFrames(stopped.frames(), data.getAsJsonArray("frames"))
                check(data.string("before") in printed && data.string("after") !in printed) { "Printed while paused: $printed" }

                resumeToExit(data.int("exitCode"))
                check(program.waitFor(HANG_SECONDS, TimeUnit.SECONDS)) { "The program did not end: $printed" }
                check(program.exitValue() == data.int("exitCode")) { "Exit code ${program.exitValue()}: $printed" }
                check(data.string("after") in printed) { "Printed: $printed" }
            }
        } finally {
            program.destroyForcibly()
            output.toFile().deleteRecursively()
        }
    }
}

/**
 * X337: Debug of an Ecstasy Application configuration in Gradle mode, in a project made as X303 makes it, runs the
 * module's Gradle task under the debugger, whose program connects to the debug session.
 */
internal fun ParityScenarios.gradleDebugCases() {
    case("X337") { data ->
        gradleProject("X337", data) { project, _ ->
            val module = data.string("module")
            val file = projectRoot.resolve(data.string("moduleFile"))
            Files.writeString(file, data.string("source"))
            // the breakpoint is set in the module's editor, as a user sets it
            val document = open(file, data.string("moduleFile"))
            debugRun(project, module) {
                val line = data.int("breakpointLine")
                debugger.debug(project, module, file.toUri().toString(), line)
                val stopped = awaitStop("Gradle runs the program, which stops at the breakpoint")
                checkStoppedAt(stopped, line, data.string("moduleFile"))
                checkFrames(stopped.frames(), data.getAsJsonArray("frames"))
                // the task's output, the program's included, reaches the Debug console through Gradle, so it may come later
                val paused = awaitConsole("the program's output reaches the Debug console", data.string("before"))
                check(data.string("after") !in paused.string("console")) { "Console while paused: ${paused.string("console")}" }

                // the session runs in the language server, which LSP4IJ stops some seconds after its last open file
                // closes, unless a run or session keeps it: closing the module's editor must leave the session stopped.
                // LSP4IJ decides as the document disconnects: a server it is to stop it marks as stopping at once, before
                // its timer runs. The watch starts once the client has closed the document, so it cannot end before that
                // decision on any machine; on a slow one it can only take longer to show a stop.
                val server = protocol.server()
                discard(document)
                val watched = TimeSource.Monotonic.markNow()
                while (watched.elapsedNow() < LSP4IJ_STOP_DELAY) {
                    val status = with(driver) { server.getServerStatus().name() }
                    check(status == "started") { "LSP4IJ stops the language server the debug session runs in: $status" }
                    Thread.sleep(100)
                }
                val kept = JsonParser.parseString(debugger.state(project, module)).asJsonObject
                check(kept["suspended"]?.asBoolean == true) { "The session did not outlive the module's editor: $kept" }

                resumeToExit(data.int("exitCode"))
                awaitConsole("the rest of the program's output reaches the Debug console", data.string("after"))
            }
        }
    }
}

/**
 * X338: Debug of an Ecstasy Application configuration in command-line mode runs the module with `xtc run` under the
 * debugger, whose program connects to the debug session. `xtc run --deduce` finds the module by its name in the project
 * directory, whose root has the other cases' sources, so the module is written there, and its compiled form goes next
 * to it, or in `build` when the root has one; the playbook puts the XDK built from this tree on the IDE's PATH.
 */
internal fun ParityScenarios.commandLineDebugCases() {
    case("X338") { data ->
        val module = data.string("module")
        val file = projectRoot.resolve(data.string("file"))
        val compiled = data.strings("compiled").map(projectRoot::resolve)
        compiled.plusElement(file).forEach { check(!Files.exists(it)) { "X338 needs a project root without $it" } }
        Files.writeString(file, data.string("source"))
        val project = with(driver) { singleProject() }
        val line = data.int("breakpointLine")
        // the breakpoint is set in the module's editor, as a user sets it
        val document = open(file, data.string("file"))
        try {
            debugRun(project, module) {
                debugger.debugCommandLine(project, module, file.toUri().toString(), line)
                val stopped =
                    debugger.awaitState(project, module, "xtc runs the program, which stops at the breakpoint", HANG_LIMIT) {
                        it["suspended"]?.asBoolean == true || it.has("exitCode")
                    }
                check(stopped["suspended"]?.asBoolean == true) { "The session ended without stopping: $stopped" }
                checkStoppedAt(stopped, line, data.string("file"))
                checkFrames(stopped.frames(), data.getAsJsonArray("frames"))
                // the command's output, the program's included, reaches the Debug console as the process prints it
                val paused = awaitConsole("the program's output reaches the Debug console", data.string("before"))
                check(data.string("after") !in paused.string("console")) { "Console while paused: ${paused.string("console")}" }

                resumeToExit(data.int("exitCode"))
                awaitConsole("the rest of the program's output reaches the Debug console", data.string("after"))
            }
        } finally {
            // the module was not edited; reloading it after xtc run wrote beside it reads it on the EDT unlocked
            closeEditor(document)
            Files.deleteIfExists(file)
            compiled.forEach(Files::deleteIfExists)
            refresh(projectRoot)
        }
    }
}

/**
 * X351, registered after X350 in catalog order: an embedded run in a project whose Gradle model is imported takes the
 * method, arguments and JVM options of the module's xtcRun from the model, says so in its output and leaves out a Java
 * debugger's agent; the run configuration's own arguments win.
 */
internal fun ParityScenarios.gradleRunCases() {
    case("X351") { data ->
        // Gradle's project cache, which holds the imported model, as X129's and X348's
        val cache = projectRoot.resolve(".gradle")
        val report = cache.resolve("xtc/lsp-model.json")
        check(!Files.exists(report)) { "X351 needs a project root without $report" }
        val made = !Files.exists(cache)
        val module = data.string("module")
        val file = data.string("file")
        val document = open(file, data.string("source"))
        val project = with(driver) { singleProject() }
        val tests = with(driver) { utility(TestRunProbe::class) }

        fun projects() = JsonParser.parseString(tests.gradleProjects(project)).asJsonObject
        try {
            // the model that an import writes, which the plugin gives the language server once no explicit modules replace it
            Files.createDirectories(report.parent)
            Files.writeString(report, workspaceModel(data["model"], directory))
            configure(null)
            awaitUi("the model's Gradle projects", HANG_LIMIT, errorMessage = { projects().toString() }) {
                projects()[module]?.asString == data.string("project")
            }
            clean(document)
            embedded { execution, _ ->
                fun run(
                    arguments: String,
                    message: String,
                ): String = execution.runToExit(project, module, uri(file), arguments, message, 0)

                // no method or arguments of its own: Gradle's, its JVM options but the debugger's agent, as it says
                val taken = run("", "the embedded run with Gradle's settings finishes")
                listOf("fromGradle", "leftOut", "output").forEach { check(data.string(it) in taken) { taken } }
                // its own arguments win, with Gradle's method
                val own = run(data.strings("args").joinToString(","), "the embedded run with its own arguments finishes")
                listOf("ownFromGradle", "ownOutput").forEach { check(data.string(it) in own) { own } }
                check(data.string("output") !in own) { own }
            }
        } finally {
            Files.deleteIfExists(report)
            if (made) cache.toFile().deleteRecursively()
            configure(emptyList())
        }
    }
}

/**
 * Makes a project with the plugin's project creator, as New Project does, at the project root, where Gradle mode runs
 * ./gradlew, with the project in Gradle mode, runs [body] with it, and removes it again, with the mode as it was. Its
 * build takes the XTC Gradle plugin and XDK last published to mavenLocal.
 */
internal fun ParityWorkspace.gradleProject(
    id: String,
    data: JsonObject,
    body: (Project, EmbeddedExecutionProbe) -> Unit,
) {
    // Gradle mode runs ./gradlew in the project root, so the project goes there; nothing of it may be there yet,
    // apart from the build directory, which embedded builds share
    val rootEntries = data.strings("rootEntries").map(projectRoot::resolve)
    rootEntries.forEach { check(!Files.exists(it)) { "$id needs a project root without ${projectRoot.relativize(it)}" } }
    val project = with(driver) { singleProject() }
    val execution = with(driver) { utility(EmbeddedExecutionProbe::class) }
    val generated = Files.createTempDirectory("xtc-new-project")
    val previous = mode(execution, project, "GRADLE")
    try {
        val created = JsonParser.parseString(execution.newProject(generated.toString(), data.string("project"))).asJsonObject
        check(created["success"].asBoolean) { created.string("message") }
        generated.resolve(data.string("project")).toFile().copyRecursively(projectRoot.toFile())
        projectRoot.resolve("gradlew").toFile().setExecutable(true)
        val catalog = Files.readString(projectRoot.resolve(data.string("catalog")))
        val version = Regex(data.string("versionPattern"), RegexOption.MULTILINE).find(catalog)?.groupValues?.get(1)
        checkNotNull(version) { "An XTC version in ${data.string("catalog")}" }
        data.strings("mavenLocal").forEach { artifact ->
            check(Files.isDirectory(Path.of(System.getProperty("user.home"), ".m2", "repository", artifact, version))) {
                "$id builds with $artifact $version from mavenLocal; run ./gradlew publishLocal first"
            }
        }
        body(project, execution)
    } finally {
        mode(execution, project, previous)
        rootEntries.forEach { it.toFile().deleteRecursively() }
        // only when nothing else, such as embedded build output, is left in them
        data.strings("sharedDirectories").forEach { projectRoot.resolve(it).toFile().delete() }
        generated.toFile().deleteRecursively()
    }
}

/** Writes the case's Gradle build at the project root, with wrappers, for Unix and Windows, that record what they are asked to do. */
internal fun ParityWorkspace.writeGradleWrapper(data: JsonObject) {
    Files.writeString(projectRoot.resolve(data.string("gradleFile")), data.string("gradleSource"))
    val wrapper = projectRoot.resolve(data.string("wrapper"))
    Files.writeString(wrapper, data.string("wrapperSource"))
    wrapper.toFile().setExecutable(true)
    Files.writeString(projectRoot.resolve(data.string("windowsWrapper")), data.string("windowsWrapperSource"))
}

/** Waits until the module's debug session satisfies [accept], and returns that state. */
private fun DebugSessionProbe.awaitState(
    project: Project,
    module: String,
    message: String,
    timeout: Duration,
    accept: (JsonObject) -> Boolean,
): JsonObject {
    var state = JsonObject()
    awaitUi(message, timeout, { "Debug session: $state" }) {
        state = JsonParser.parseString(state(project, module)).asJsonObject
        accept(state)
    }
    return state
}

/**
 * How long the Gradle debug case watches the server after the module's editor closes: longer than LSP4IJ's own wait to
 * stop a server once its last open file has closed, five seconds by default, though LSP4IJ shows a stop it has decided
 * on as soon as the file disconnects.
 */
private val LSP4IJ_STOP_DELAY = 8.seconds

/** What the Build tool window shows when the embedded compiler takes Build Project. */
private const val EMBEDDED_BUILD = "with the embedded compiler"

/** The settings page that chooses the build mode, which the embedded build's notice links to. */
private const val SETTINGS_PAGE = "Settings | Languages & Frameworks | Ecstasy | Build and Run"

/** The plugin's settings pages by configurable id, with their paths in the Settings tree. */
private val SETTINGS_PAGES =
    listOf("Build and Run" to "xtc.build", "Compiler" to "xtc.compiler", "Language Service" to "xtc.service")
        .associate { (name, id) -> id to "Languages & Frameworks/Ecstasy/$name" } +
        mapOf(
            "xtc.service.defaults" to "Languages & Frameworks/Ecstasy/Language Service Defaults",
            "xtc.runtime" to "Languages & Frameworks/Ecstasy/Server Runtime and Logs",
            "xtc.build.pointer" to "Build, Execution, Deployment/Ecstasy",
        )

/** Runs [body] with the project in embedded build mode, restoring the previous mode afterwards. */
internal fun ParityWorkspace.embedded(body: (EmbeddedExecutionProbe, Project) -> Unit) {
    val project = with(driver) { singleProject() }
    val execution = with(driver) { utility(EmbeddedExecutionProbe::class) }
    val previous = mode(execution, project, "EMBEDDED")
    try {
        body(execution, project)
    } finally {
        mode(execution, project, previous)
    }
}

/** Sets the project's build mode, answering the one it had. */
internal fun ParityWorkspace.mode(
    execution: EmbeddedExecutionProbe,
    project: Project,
    value: String,
): String = with(driver) { withContext(OnDispatcher.EDT) { execution.mode(project, value) } }

/** Build Project, with its answer: the Build tool window's text, and whether it had errors or was aborted. */
private fun EmbeddedExecutionProbe.build(project: Project): JsonObject =
    JsonParser.parseString(build(project, HANG_SECONDS.toInt())).asJsonObject

/** Checks that the embedded build [built] has neither errors nor was aborted. */
private fun checkBuilt(built: JsonObject) =
    check(!built["errors"].asBoolean && !built["aborted"].asBoolean) { "Embedded build failed: ${built.string("text")}" }

/**
 * Runs [module] from [uri] with [arguments] and waits for its exit, as [message] says; checks its exit code when
 * [expected] is given, and returns its output.
 */
private fun EmbeddedExecutionProbe.runToExit(
    project: Project,
    module: String,
    uri: String,
    arguments: String,
    message: String,
    expected: Int? = null,
): String {
    run(project, module, uri, arguments)
    awaitUi(message, HANG_LIMIT) { exitCode(module) != Int.MIN_VALUE }
    val output = output(module)
    expected?.let { check(exitCode(module) == it) { "Exit code ${exitCode(module)}: $output" } }
    return output
}

/** A debug session of [module] in [project], through the IDE-side probe. */
private class DebugRun(
    val debugger: DebugSessionProbe,
    val project: Project,
    val module: String,
) {
    /** Waits until the program is suspended in a state that [accept] takes, as [message] says, and returns that state. */
    fun awaitStop(
        message: String = "the program stops at the breakpoint",
        accept: (JsonObject) -> Boolean = { true },
    ): JsonObject = debugger.awaitState(project, module, message, HANG_LIMIT) { it["suspended"]?.asBoolean == true && accept(it) }

    /** Waits until the Debug console shows [text], as [message] says, and returns that state. */
    fun awaitConsole(
        message: String,
        text: String,
    ): JsonObject = debugger.awaitState(project, module, message, HANG_LIMIT) { text in it.string("console") }

    /**
     * Resumes the program and waits for its exit, as [message] says, which must be with [exitCode] and, when [output] is
     * given, with it in the console; returns the ended state.
     */
    fun resumeToExit(
        exitCode: Int,
        output: String? = null,
        message: String = "Resume runs the program to its exit",
    ): JsonObject {
        debugger.resume(project, module)
        val ended = debugger.awaitState(project, module, message, HANG_LIMIT) { it.has("exitCode") }
        check(ended.int("exitCode") == exitCode) { "Debug session: $ended" }
        output?.let { check(it in ended.string("console")) { "Console after Resume: ${ended.string("console")}" } }
        return ended
    }

    /** The variables at [path] in the Variables view, the stopped frame's own for an empty path. */
    fun variables(path: String): List<JsonObject> =
        JsonParser.parseString(debugger.variables(project, module, path)).asJsonArray.map { it.asJsonObject }

    fun evaluate(expression: String): JsonObject = JsonParser.parseString(debugger.evaluate(project, module, expression)).asJsonObject

    /** Checks the values of the case's watches, and that its refused expression is refused with the reason. */
    fun checkWatches(data: JsonObject) {
        data.getAsJsonArray("watches").map { it.asJsonArray }.forEach { watch ->
            val shown = evaluate(watch[0].asString)
            // LSP4IJ shows strings and numbers without their type
            val type = shown["type"]?.asString.orEmpty()
            check(shown["value"]?.asString == watch[1].asString && type in listOf("", watch[2].asString)) {
                "${watch[0].asString} is not ${watch[1].asString}: $shown"
            }
        }
        val refused = data.strings("refused")
        val error = evaluate(refused[0])["error"]?.asString.orEmpty()
        check(refused[1] in error) { "${refused[0]} is not refused with the reason: $error" }
    }

    /** Waits until LSP4IJ shows the exception's information, the exceptionInfo answer with each of [parts], where it was thrown. */
    fun awaitExceptionHint(parts: List<String>) {
        var hints = listOf<String>()
        awaitUi("LSP4IJ shows the exception's information where it was thrown", HANG_LIMIT, { "Hints: $hints" }) {
            hints = JsonParser.parseString(debugger.hintTexts()).asJsonArray.map { it.asString }
            hints.any { hint -> parts.all { it in hint } }
        }
    }
}

/** Runs [body] with a debug session of [module] in [project], and clears its breakpoints afterwards. */
private fun ParityWorkspace.debugRun(
    project: Project,
    module: String,
    body: DebugRun.() -> Unit,
) {
    val run = DebugRun(with(driver) { utility(DebugSessionProbe::class) }, project, module)
    try {
        run.body()
    } finally {
        run.debugger.clearBreakpoints(project)
    }
}

/** Runs [body] with a debug session of the case's module, in embedded build mode. */
private fun ParityWorkspace.debugging(
    data: JsonObject,
    body: DebugRun.() -> Unit,
) = embedded { _, project -> debugRun(project, data.string("module"), body) }

/** A debug state's frames. */
private fun JsonObject.frames(): List<JsonObject> = getAsJsonArray("frames").map { it.asJsonObject }

/** Whether the state's top frame is in [name], at [line]. */
private fun JsonObject.isAt(
    name: String,
    line: Int,
): Boolean =
    this["line"]?.asInt == line &&
        getAsJsonArray("frames")
            ?.firstOrNull()
            ?.asJsonObject
            ?.string("name")
            ?.contains(name) == true

/** The case's [key], a frame's name and line. */
private fun JsonObject.frame(key: String): Pair<String, Int> = getAsJsonArray(key).let { it[0].asString to it[1].asInt }

/** Checks that [frames] begin with the [expected] ones, each a name the frame's contains and its line. */
private fun checkFrames(
    frames: List<JsonObject>,
    expected: JsonArray,
) = expected.map { it.asJsonArray }.forEachIndexed { index, frame ->
    val shown = frames.getOrNull(index)
    check(shown != null && frame[0].asString in shown.string("name") && shown.int("line") == frame[1].asInt) {
        "Frame $index is not ${frame[0].asString} at ${frame[1].asInt}: $frames"
    }
}

/** Checks that the program stopped at [line] of [file]. */
internal fun checkStoppedAt(
    stopped: JsonObject,
    line: Int,
    file: String,
) = check(stopped.int("line") == line && stopped.string("file").endsWith(file)) { "Stopped at: $stopped" }

/** Checks that the ended program's console has a line of the logpoint's [value]. */
private fun checkLogged(
    ended: JsonObject,
    value: String,
) = ended.string("console").let { console -> check(console.lines().any { it.trim() == value }) { "The logpoint logged nothing: $console" } }

/** Debug of an Ecstasy Application configuration, through LSP4IJ's DAP client, recorded by the IDE-side probe. */
@Remote("org.xtclang.idea.playbook.probe.DebugSessionUi", plugin = "org.xtclang.playbook.probe")
internal interface DebugSessionProbe {
    fun debug(
        project: Project,
        module: String,
        uri: String,
        breakpointLine: Int,
    )

    fun debugRunningProgram(
        project: Project,
        module: String,
        uri: String,
        breakpointLine: Int,
    )

    fun debugFromEntry(
        project: Project,
        module: String,
        uri: String,
    )

    fun debugCommandLine(
        project: Project,
        module: String,
        uri: String,
        breakpointLine: Int,
    )

    fun attach(
        project: Project,
        module: String,
        uri: String,
        breakpointLine: Int,
        port: Int,
    )

    fun state(
        project: Project,
        module: String,
    ): String

    fun variables(
        project: Project,
        module: String,
        path: String,
    ): String

    fun showValue(
        project: Project,
        module: String,
        path: String,
    ): String

    fun showHex(
        project: Project,
        module: String,
        on: Boolean,
    ): Boolean

    fun setValue(
        project: Project,
        module: String,
        path: String,
        expression: String,
    ): String

    fun complete(
        project: Project,
        module: String,
        text: String,
        pick: String,
    ): String

    fun pagedVariables(
        project: Project,
        module: String,
        path: String,
        pages: Int,
    ): String

    fun step(
        project: Project,
        module: String,
        kind: String,
    )

    fun resume(
        project: Project,
        module: String,
    )

    fun viewRebuilds(
        project: Project,
        module: String,
    ): Int

    fun runToCursor(
        project: Project,
        module: String,
        uri: String,
        line: Int,
    )

    fun threads(
        project: Project,
        module: String,
    ): String

    fun smartStepTargets(
        project: Project,
        module: String,
    ): String

    fun resettable(
        project: Project,
        module: String,
    ): String

    fun addFunctionBreakpoint(
        project: Project,
        name: String,
        condition: String,
    )

    fun functionBreakpoints(
        project: Project,
        module: String,
    ): String

    fun inlineValues(
        project: Project,
        uri: String,
    ): String

    fun breakOnValueChange(
        project: Project,
        path: String,
    ): Boolean

    fun dataBreakpoints(
        project: Project,
        module: String,
    ): String

    fun clearLineBreakpoints(project: Project)

    fun resetFrame(
        project: Project,
        module: String,
        name: String,
    )

    fun smartStepInto(
        project: Project,
        module: String,
        text: String,
    )

    fun evaluate(
        project: Project,
        module: String,
        expression: String,
    ): String

    fun evaluateFrom(
        project: Project,
        module: String,
        expression: String,
        origin: String,
    ): String

    fun hintTexts(): String

    fun pause(
        project: Project,
        module: String,
    )

    fun stopSession(
        project: Project,
        module: String,
    )

    fun exceptionFilters(filters: String): String

    fun loadedSources(
        project: Project,
        module: String,
    ): String

    fun debugWhen(
        project: Project,
        module: String,
        uri: String,
        breakpointLine: Int,
        condition: String,
        hitCondition: String,
    )

    fun addLogpoint(
        project: Project,
        uri: String,
        line: Int,
        expression: String,
    )

    fun clearBreakpoints(project: Project)

    fun addBreakpoint(
        project: Project,
        uri: String,
        line: Int,
    )

    fun breakpoints(
        project: Project,
        module: String,
        uri: String,
    ): String

    fun insert(
        project: Project,
        uri: String,
        line: Int,
        text: String,
    )
}

/** The Run icons in an editor's gutter, as "Run Module@line", and a click on one. */
@Remote("org.xtclang.idea.playbook.probe.RunGutterUi", plugin = "org.xtclang.playbook.probe")
internal interface RunGutterProbe {
    fun icons(editor: Editor): List<String>

    fun click(
        editor: Editor,
        tooltip: String,
    )
}

/** Embedded Build Project and runs, recorded by the IDE-side probe. */
@Remote("org.xtclang.idea.playbook.probe.EmbeddedExecutionUi", plugin = "org.xtclang.playbook.probe")
interface EmbeddedExecutionProbe {
    fun settingsPath(
        project: Project,
        id: String,
    ): String

    fun mode(
        project: Project,
        value: String,
    ): String

    fun newProject(
        parent: String,
        name: String,
    ): String

    fun build(
        project: Project,
        timeoutSeconds: Int,
    ): String

    fun run(
        project: Project,
        module: String,
        uri: String,
        arguments: String,
    )

    fun expect(
        project: Project,
        module: String,
    )

    fun runners(
        project: Project,
        module: String,
    ): String

    fun rerun(
        project: Project,
        module: String,
    )

    fun output(module: String): String

    fun exitCode(module: String): Int

    fun input(
        module: String,
        text: String,
    )

    fun endInput(module: String)

    fun stop(module: String)

    fun active(project: Project): String
}
