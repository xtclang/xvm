package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

        awaitUi("Run lens on a module with run()", 45.seconds) { data.string("runTitle") in runTitles(runnable) }
        awaitUi("Run icon in the gutter of a module with run()", 45.seconds) {
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
        awaitUi("Run lens after adding run()", 45.seconds) { data.string("libraryRunTitle") in runTitles(library) }
        awaitUi("Run icon after adding run()", 45.seconds) { runIcons(library) == listOf("Run ${data.string("libraryModule")}@0") }
        // clicking the icon runs the module, as the lens does; a run builds a configured source module
        val module = data.string("module")
        configure(listOf(SharedScenarios.SourceModule(module, uri(data.string("file")), emptyList())))
        clean(runnable)
        awaitUi("Run icon on the configured module", 45.seconds) { runIcons(runnable) == listOf("Run $module@0") }
        embedded { execution, project ->
            execution.expect(project, module)
            with(driver) { withContext(OnDispatcher.EDT) { gutter.click(runnable.editor.editor, "Run $module") } }
            awaitUi("run from the gutter icon finishes", 60.seconds) { execution.exitCode(module) != Int.MIN_VALUE }
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
            val built = JsonParser.parseString(execution.build(project, 120)).asJsonObject
            val text = built.string("text")
            check(!built["errors"].asBoolean && !built["aborted"].asBoolean) { "Embedded build failed: $text" }
            data.strings("built").forEach { check(it in text) { "$it in $text" } }
            check(data.string("artifact") in text.replace('\\', '/')) { text }

            replace(library, data.string("brokenLibrary"))
            val file = directory.resolve(data.string("libraryFile"))
            check(Files.readString(file) == data.string("librarySource")) { "The broken library is unsaved before the build" }
            val failed = JsonParser.parseString(execution.build(project, 120)).asJsonObject
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
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { execution, project ->
            val module = data.string("module")
            execution.run(project, module, uri(data.string("file")), data.strings("args").joinToString(","))
            awaitUi("embedded run finishes", 60.seconds) { execution.exitCode(module) != Int.MIN_VALUE }
            val output = execution.output(module)
            check(execution.exitCode(module) == data.int("exitCode")) { "Exit code ${execution.exitCode(module)}: $output" }
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
            awaitUi("long-running program starts", 60.seconds) { data.string("started") in execution.output(module) }
            // Rerun from the Run tool window stops the program and starts a new run
            execution.rerun(project, module)
            awaitUi("rerun starts a new run", 60.seconds) { data.string("started") in execution.output(module) }
            execution.stop(module)
            awaitUi("stopped program reports its exit", 30.seconds) { execution.exitCode(module) != Int.MIN_VALUE }

            // the worker is free again: the native Run lens runs the next program
            val nextModule = data.string("nextModule")
            execution.expect(project, nextModule)
            val probe = with(driver) { utility(CodeLensProbe::class) }
            val title = "▶ Run $nextModule"
            open(data.string("nextFile"))
            awaitUi("Run lens on the next program", 45.seconds) {
                title in with(driver) { withContext(OnDispatcher.EDT) { probe.titles(next.editor.editor) } }
            }
            with(driver) {
                focusEditor(next.editor)
                withContext(OnDispatcher.EDT) { probe.click(next.editor.editor, title) }
            }
            awaitUi("next program finishes", 60.seconds) { execution.exitCode(nextModule) != Int.MIN_VALUE }
            check(execution.exitCode(nextModule) == data.int("nextExitCode")) {
                "Exit code ${execution.exitCode(nextModule)}: ${execution.output(nextModule)}"
            }
        }
    }

    case("X284") { data ->
        open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        embedded { execution, project ->
            val module = data.string("module")
            execution.run(project, module, uri(data.string("file")), "")
            awaitUi("failed build ends the run", 60.seconds) { execution.exitCode(module) != Int.MIN_VALUE }
            val output = execution.output(module)
            check(execution.exitCode(module) == 1) { "Exit code ${execution.exitCode(module)}: $output" }
            check(data.string("diagnostic") in output) { output }
            check(data.string("message") in output) { output }
            check(!output.lines().any { it.startsWith("Process finished") && "exit code 0" in it }) { "Nothing ran: $output" }
        }
    }

    case("X285") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { execution, project ->
            val module = data.string("module")
            val runners =
                JsonParser
                    .parseString(
                        with(driver) { withContext(OnDispatcher.EDT) { execution.runners(project, module) } },
                    ).asJsonObject
            check(runners["run"].asBoolean) { "Run must be available: $runners" }
            check(runners["debug"].asBoolean) { "Debug must be offered with the embedded compiler: $runners" }
            execution.run(project, module, uri(data.string("file")), "")
            awaitUi("embedded run finishes", 60.seconds) { execution.exitCode(module) != Int.MIN_VALUE }
            check(
                execution.exitCode(module) == data.int("exitCode"),
            ) { "Exit code ${execution.exitCode(module)}: ${execution.output(module)}" }

            // Debug runs the module in a debug session in the language server, to the same exit code
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            debugger.debug(project, module, uri(data.string("file")), 0)
            val ended = debugger.awaitState(project, module, "debug session ends with the program", 60.seconds) { it.has("exitCode") }
            check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
        }
    }
}

/** Console input for embedded runs; registered after the adapter chooser to keep catalog order. */
internal fun ParityScenarios.executionInputCases() {
    case("X287") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { execution, project ->
            val module = data.string("module")
            execution.run(project, module, uri(data.string("file")), "")
            awaitUi("program prompts for input", 60.seconds) { data.string("prompt") in execution.output(module) }
            execution.input(module, data.string("input") + "\n")
            awaitUi("program reads the typed line", 30.seconds) { data.string("greeting") in execution.output(module) }
            execution.endInput(module)
            awaitUi("program ends after the end of input", 30.seconds) { execution.exitCode(module) != Int.MIN_VALUE }
            val output = execution.output(module)
            check(execution.exitCode(module) == data.int("exitCode")) { "Exit code ${execution.exitCode(module)}: $output" }
            check(data.string("ended") in output) { output }
        }
    }
}

/** Build modes beside a Gradle build and repeated runs; registered after console input to keep catalog order. */
internal fun ParityScenarios.buildModeCases() {
    case("X288") { data ->
        // a Gradle build at the project root, with a wrapper that records what it is asked to do
        val rootFiles = listOf("gradleFile", "wrapper", "windowsWrapper", "invocations").map { projectRoot.resolve(data.string(it)) }
        val invocations = projectRoot.resolve(data.string("invocations"))

        fun calls() = if (Files.exists(invocations)) Files.readAllLines(invocations).filter { it.isNotBlank() } else emptyList()
        val project = with(driver) { singleProject() }
        val execution = with(driver) { utility(EmbeddedExecutionProbe::class) }
        val previous = with(driver) { withContext(OnDispatcher.EDT) { execution.mode(project, "EMBEDDED") } }
        try {
            Files.writeString(projectRoot.resolve(data.string("gradleFile")), data.string("gradleSource"))
            val wrapper = projectRoot.resolve(data.string("wrapper"))
            Files.writeString(wrapper, data.string("wrapperSource"))
            wrapper.toFile().setExecutable(true)
            Files.writeString(projectRoot.resolve(data.string("windowsWrapper")), data.string("windowsWrapperSource"))
            val document = open(data.string("file"), data.string("source"))
            configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
            clean(document)
            val module = data.string("module")

            fun runToEnd(message: String): String {
                execution.run(project, module, uri(data.string("file")), "")
                awaitUi(message, 60.seconds) { execution.exitCode(module) != Int.MIN_VALUE }
                val output = execution.output(module)
                check(execution.exitCode(module) == 0) { "Exit code ${execution.exitCode(module)}: $output" }
                return output
            }

            // the default is embedded, also beside a Gradle build, and says the build's settings do not apply
            val built = JsonParser.parseString(execution.build(project, 120)).asJsonObject
            val text = built.string("text")
            check(!built["errors"].asBoolean && !built["aborted"].asBoolean) { "Embedded build failed: $text" }
            check(EMBEDDED_BUILD in text) { text }
            check(data.string("notice") in text) { text }
            // the notice links to the settings page that chooses the mode
            check(built["links"].asJsonArray.any { it.asString == SETTINGS_PAGE }) { "No link to $SETTINGS_PAGE: ${built["links"]}" }
            runToEnd("embedded run finishes").let { check(data.string("output") in it) { it } }
            check(calls().isEmpty()) { "Embedded mode must not call Gradle: ${calls()}" }

            // Gradle: Build Project is left to the IDE's builder, and Run calls the Gradle wrapper
            with(driver) { withContext(OnDispatcher.EDT) { execution.mode(project, "GRADLE") } }
            val gradleBuild = JsonParser.parseString(execution.build(project, 120)).asJsonObject.string("text")
            check(EMBEDDED_BUILD !in gradleBuild) { "The embedded compiler must not take a Gradle-mode build: $gradleBuild" }
            val gradleRun = runToEnd("Gradle run finishes")
            check(data.string("gradleRun") in gradleRun) { gradleRun }
            check(calls().size == 1 && data.string("gradleRun") in calls().single()) { "Run calls the Gradle wrapper once: ${calls()}" }

            // and back: Run is embedded again
            with(driver) { withContext(OnDispatcher.EDT) { execution.mode(project, "EMBEDDED") } }
            runToEnd("embedded run after switching back finishes").let { check(data.string("output") in it) { it } }
            check(calls().size == 1) { "No Gradle call after switching back: ${calls()}" }
        } finally {
            with(driver) { withContext(OnDispatcher.EDT) { execution.mode(project, previous) } }
            rootFiles.forEach(Files::deleteIfExists)
        }
    }

    case("X289") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { execution, project ->
            val module = data.string("module")

            fun runOnce(message: String): String {
                execution.run(project, module, uri(data.string("file")), "")
                awaitUi(message, 60.seconds) { execution.exitCode(module) != Int.MIN_VALUE }
                val output = execution.output(module)
                check(execution.exitCode(module) == data.int("exitCode")) { "Exit code ${execution.exitCode(module)}: $output" }
                return output
            }
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

/** Fresh program state, refused entry methods and dropped arguments; registered after the on-type cases, as the catalog orders X292–X294. */
internal fun ParityScenarios.runEntryCases() {
    case("X292") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { execution, project ->
            val module = data.string("module")
            repeat(2) { round ->
                execution.run(project, module, uri(data.string("file")), "")
                awaitUi("run ${round + 1} finishes", 60.seconds) { execution.exitCode(module) != Int.MIN_VALUE }
                val output = execution.output(module)
                check(execution.exitCode(module) == data.int("exitCode")) { "Exit code ${execution.exitCode(module)}: $output" }
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
                execution.run(project, module, uri(file), "")
                awaitUi("run of $module is refused", 60.seconds) { execution.exitCode(module) != Int.MIN_VALUE }
                val output = execution.output(module)
                check(execution.exitCode(module) == data.int("exitCode")) { "Exit code ${execution.exitCode(module)}: $output" }
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
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { execution, project ->
            val module = data.string("module")
            execution.run(project, module, uri(data.string("file")), data.strings("args").joinToString(","))
            awaitUi("run with dropped arguments finishes", 60.seconds) { execution.exitCode(module) != Int.MIN_VALUE }
            val output = execution.output(module)
            check(execution.exitCode(module) == data.int("exitCode")) { "Exit code ${execution.exitCode(module)}: $output" }
            val warning = output.indexOf(data.string("warning"))
            check(
                warning >= 0 && warning < output.indexOf(data.string("output")),
            ) { "The warning comes before the program's output: $output" }
        }
    }

    case("X303") { data ->
        // Gradle mode runs ./gradlew in the project root, so the project goes there; nothing of it may be there yet,
        // apart from the build directory, which embedded builds share
        val rootEntries = data.strings("rootEntries").map(projectRoot::resolve)
        rootEntries.forEach { check(!Files.exists(it)) { "X303 needs a project root without ${projectRoot.relativize(it)}" } }
        val project = with(driver) { singleProject() }
        val execution = with(driver) { utility(EmbeddedExecutionProbe::class) }
        val generated = Files.createTempDirectory("xtc-new-project")
        val previous = with(driver) { withContext(OnDispatcher.EDT) { execution.mode(project, "GRADLE") } }
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
                    "X303 builds with $artifact $version from mavenLocal; run ./gradlew publishLocal first"
                }
            }
            val module = data.string("module")
            val file = projectRoot.resolve(data.string("moduleFile"))

            fun gradleRun(message: String): String {
                execution.run(project, module, file.toUri().toString(), data.string("args"))
                awaitUi(message, 120.seconds) { execution.exitCode(module) != Int.MIN_VALUE }
                return execution.output(module)
            }

            // the Ecstasy Application configuration calls ./gradlew runXtc, which compiles the module (not its tests) first
            val output = gradleRun("Gradle run finishes")
            check(execution.exitCode(module) == 0) { "Exit code ${execution.exitCode(module)}: $output" }
            check(data.string("output") in output) { output }
            val artifact = data.strings("artifacts").first()
            check(Files.exists(projectRoot.resolve(artifact))) { "The Gradle run builds $artifact: $output" }

            // a type error in the saved module fails the compile, and the run reports it instead of running
            Files.writeString(file, data.string("broken"))
            val failed = gradleRun("Gradle run of the broken module finishes")
            check(execution.exitCode(module) != 0) { "The broken module must not run: $failed" }
            check(data.string("brokenCode") in failed && data.string("brokenLocation") in failed) { failed }
        } finally {
            with(driver) { withContext(OnDispatcher.EDT) { execution.mode(project, previous) } }
            rootEntries.forEach { it.toFile().deleteRecursively() }
            // only when nothing else, such as embedded build output, is left in them
            data.strings("sharedDirectories").forEach { projectRoot.resolve(it).toFile().delete() }
            generated.toFile().deleteRecursively()
        }
    }

    case("X304") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            val line = data.int("breakpointLine")
            try {
                debugger.debug(project, module, uri(data.string("file")), line)
                val stopped =
                    debugger.awaitState(project, module, "the program stops at the breakpoint", 60.seconds) {
                        it["suspended"]?.asBoolean == true
                    }
                check(stopped.int("line") == line && stopped.string("file").endsWith(data.string("file"))) { "Stopped at: $stopped" }
                val frames = stopped.getAsJsonArray("frames").map { it.asJsonObject }
                data.getAsJsonArray("frames").map { it.asJsonArray }.forEachIndexed { index, expected ->
                    val frame = frames.getOrNull(index)
                    check(frame != null && expected[0].asString in frame.string("name") && frame.int("line") == expected[1].asInt) {
                        "Frame $index is not ${expected[0].asString} at ${expected[1].asInt}: $frames"
                    }
                }
                val console = stopped.string("console")
                check(data.string("before") in console && data.string("after") !in console) { "Console while paused: $console" }

                debugger.resume(project, module)
                val ended = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
                check(data.string("after") in ended.string("console")) { "Console after Resume: ${ended.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X305") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            val line = data.int("breakpointLine")
            try {
                debugger.debug(project, module, uri(data.string("file")), line)
                val stopped =
                    debugger.awaitState(project, module, "the program stops at the breakpoint", 60.seconds) {
                        it["suspended"]?.asBoolean == true
                    }
                check(stopped.int("line") == line) { "Stopped at: $stopped" }

                fun shown(path: String) =
                    JsonParser.parseString(debugger.variables(project, module, path)).asJsonArray.map { it.asJsonObject }
                val locals = shown("")
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
                    val children = shown(name).map { listOf(it.string("name"), it.string("value")) }
                    val expected = data.getAsJsonArray(key).map { pair -> pair.asJsonArray.map { it.asString } }
                    check(children == expected) { "$name shows $children, not $expected" }
                }

                debugger.resume(project, module)
                val ended = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
                check(data.string("output") in ended.string("console")) { "Console after Resume: ${ended.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X306") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
                debugger.awaitState(project, module, "the program stops at the breakpoint", 60.seconds) {
                    it["suspended"]?.asBoolean == true && it["line"]?.asInt == data.int("breakpointLine")
                }
                data.getAsJsonArray("steps").map { it.asJsonArray }.forEach { step ->
                    val (kind, name, line) = Triple(step[0].asString, step[1].asString, step[2].asInt)
                    debugger.step(project, module, kind)
                    val stopped =
                        debugger.awaitState(project, module, "step $kind stops at $name:$line", 60.seconds) { state ->
                            val top = state.getAsJsonArray("frames")?.firstOrNull()?.asJsonObject
                            state["suspended"]?.asBoolean == true && state["line"]?.asInt == line && top != null &&
                                name in top.string("name")
                        }
                    if (kind == "into") {
                        val caller = stopped.getAsJsonArray("frames")[1].asJsonObject
                        val expected = data.getAsJsonArray("caller")
                        check(expected[0].asString in caller.string("name") && caller.int("line") == expected[1].asInt) {
                            "Below $name: $caller"
                        }
                    }
                }

                debugger.resume(project, module)
                val ended = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
                check(data.string("output") in ended.string("console")) { "Console after Resume: ${ended.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X307") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                // no breakpoint: LSP4IJ turns the adapter's Uncaught Exceptions filter on, as it is by default
                debugger.debug(project, module, uri(data.string("file")), 0)
                val stopped =
                    debugger.awaitState(project, module, "the program stops at the exception", 60.seconds) {
                        it["suspended"]?.asBoolean == true
                    }
                val frames = stopped.getAsJsonArray("frames").map { it.asJsonObject }
                data.getAsJsonArray("frames").map { it.asJsonArray }.forEachIndexed { index, expected ->
                    val frame = frames.getOrNull(index)
                    check(frame != null && expected[0].asString in frame.string("name") && frame.int("line") == expected[1].asInt) {
                        "Frame $index is not ${expected[0].asString} at ${expected[1].asInt}: $frames"
                    }
                }
                val locals = JsonParser.parseString(debugger.variables(project, module, "")).asJsonArray.map { it.asJsonObject }
                val exception = locals.first()
                check(exception.string("name") == data.string("variable") && data.string("exception") in exception.string("type")) {
                    "The exception is not the frame's first variable: $locals"
                }
                val argument = data.strings("argument")
                check(locals.any { it.string("name") == argument[0] && it.string("value") == argument[1] }) { "No ${argument[0]}: $locals" }

                // LSP4IJ shows the exception in the editor a second after the stop; until then the editor must stay open
                awaitUi("LSP4IJ shows the exception where it was thrown", 30.seconds) { debugger.hintShown() }

                debugger.resume(project, module)
                val ended = debugger.awaitState(project, module, "Resume lets the program fail", 60.seconds) { it.has("exitCode") }
                check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
                check(data.string("message") in ended.string("console")) { "Console after Resume: ${ended.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X310") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
                debugger.awaitState(
                    project,
                    module,
                    "the program stops at the breakpoint",
                    60.seconds,
                ) { it["suspended"]?.asBoolean == true }

                fun evaluate(expression: String) = JsonParser.parseString(debugger.evaluate(project, module, expression)).asJsonObject
                data.getAsJsonArray("watches").map { it.asJsonArray }.forEach { expected ->
                    val shown = evaluate(expected[0].asString)
                    // LSP4IJ shows strings and numbers without their type
                    val type = shown["type"]?.asString.orEmpty()
                    check(shown["value"]?.asString == expected[1].asString && type in listOf("", expected[2].asString)) {
                        "${expected[0].asString} is not ${expected[1].asString}: $shown"
                    }
                }
                check(evaluate(data.string("expands"))["expands"]?.asBoolean == true) { "${data.string("expands")} does not expand" }
                val refused = data.strings("refused")
                val error = evaluate(refused[0])["error"]?.asString.orEmpty()
                check(refused[1] in error) { "${refused[0]} is not refused with the reason: $error" }

                debugger.resume(project, module)
                val ended = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }
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

/** What the Build tool window shows when the embedded compiler takes Build Project. */
private const val EMBEDDED_BUILD = "with the embedded compiler"

/** The settings page that chooses the build mode, which the embedded build's notice links to. */
private const val SETTINGS_PAGE = "Settings | Build, Execution, Deployment | Ecstasy"

/** Runs [body] with the project in embedded build mode, restoring the previous mode afterwards. */
private fun ParityWorkspace.embedded(body: (EmbeddedExecutionProbe, Project) -> Unit) {
    val project = with(driver) { singleProject() }
    val execution = with(driver) { utility(EmbeddedExecutionProbe::class) }
    val previous = with(driver) { withContext(OnDispatcher.EDT) { execution.mode(project, "EMBEDDED") } }
    try {
        body(execution, project)
    } finally {
        with(driver) { withContext(OnDispatcher.EDT) { execution.mode(project, previous) } }
    }
}

/** Debug of an Ecstasy Application configuration, through LSP4IJ's DAP client, recorded by the IDE-side probe. */
@Remote("org.xtclang.idea.playbook.probe.DebugSessionUi", plugin = "org.xtclang.playbook.probe")
internal interface DebugSessionProbe {
    fun debug(
        project: Project,
        module: String,
        uri: String,
        breakpointLine: Int,
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

    fun step(
        project: Project,
        module: String,
        kind: String,
    )

    fun resume(
        project: Project,
        module: String,
    )

    fun evaluate(
        project: Project,
        module: String,
        expression: String,
    ): String

    fun hintShown(): Boolean

    fun clearBreakpoints(project: Project)
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
}
