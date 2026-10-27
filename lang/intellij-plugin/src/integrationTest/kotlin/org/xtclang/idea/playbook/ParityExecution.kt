package org.xtclang.idea.playbook

import com.google.gson.JsonNull
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
                // the program's frames are the whole stack, without the runner that hosts it
                check(frames.size == data.getAsJsonArray("frames").size()) { "The stack is not the program's frames: $frames" }
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
                // the program's frames are the whole stack, without the runner that hosts it
                check(frames.size == data.getAsJsonArray("frames").size()) { "The stack is not the program's frames: $frames" }
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

                // TODO LSP4IJ: UP31 — LSP4IJ shows the exception a second after the stop, in the editor it found at the
                // stop, without checking that the editor is still open; until the hint shows, the editor must stay open
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

    // TODO LSP4IJ: UP30 — Pause Program needs LSP4IJ's startPausing (#1757); the catalog keeps X308 not-implemented
    // until a release has it, so this runs only with an LSP4IJ that has it (-Plsp4ijChannel=nightly) and X308 made full
    case("X308") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                // no breakpoint: the program loops forever once it has printed
                debugger.debug(project, module, uri(data.string("file")), 0)
                debugger.awaitState(project, module, "the program runs", 60.seconds) {
                    data.string("started") in it["console"]?.asString.orEmpty()
                }

                debugger.pause(project, module)
                val paused =
                    debugger.awaitState(project, module, "Pause stops the program", 60.seconds) {
                        it["suspended"]?.asBoolean ==
                            true
                    }
                val top = paused.getAsJsonArray("frames").first().asJsonObject
                check(data.string("frame") in top.string("name") && top.int("line") in data.getAsJsonArray("lines").map { it.asInt }) {
                    "Paused at: $paused"
                }

                // the program never ends on its own: stopping the session ends it
                debugger.stopSession(project, module)
                debugger.awaitState(project, module, "stopping the session ends the program", 60.seconds) { it.has("exitCode") }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X309") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            // no breakpoint, and the program throws nothing, so a stop is the configuration's stop on entry
            debugger.debugFromEntry(project, module, uri(data.string("file")))
            val stopped =
                debugger.awaitState(project, module, "the program stops at its first line", 60.seconds) {
                    it["suspended"]?.asBoolean == true
                }
            val top = stopped.getAsJsonArray("frames").first().asJsonObject
            check(data.string("frame") in top.string("name") && top.int("line") <= data.int("lastLine")) { "Stopped at: $stopped" }
            check(data.string("output") !in stopped.string("console")) { "Nothing has run yet: ${stopped.string("console")}" }

            debugger.resume(project, module)
            val ended = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
            check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
            check(data.string("output") in ended.string("console")) { "Console after Resume: ${ended.string("console")}" }
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

    case("X311") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                // LSP4IJ sends the breakpoint's condition, but not a hit condition or a log message (UP32)
                debugger.debugWhen(project, module, uri(data.string("file")), data.int("breakpointLine"), data.string("condition"))
                for (expected in data.strings("conditionStops")) {
                    // a stop is the next one once i has the value it stops at
                    awaitUi("the program stops where i is $expected", 60.seconds) {
                        runCatching { JsonParser.parseString(debugger.evaluate(project, module, "i")).asJsonObject["value"]?.asString }
                            .getOrNull() == expected
                    }
                    debugger.resume(project, module)
                }
                val ended = debugger.awaitState(project, module, "the program runs to its exit", 60.seconds) { it.has("exitCode") }
                check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    // TODO LSP4IJ: UP34, UP33 — an exception filter's condition needs LSP4IJ's filterOptions (#1760), and its hint
    // the exceptionInfo answer (#1761); the catalog keeps X312 not-implemented until a release has them, so this runs
    // only with an LSP4IJ that has them (-Plsp4ijChannel=nightly) and X312 made full
    case("X312") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
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
                val stopped =
                    debugger.awaitState(project, module, "the program stops at the exception class the condition names", 60.seconds) {
                        it["suspended"]?.asBoolean == true
                    }
                check(data.string("caught") in stopped.string("console")) { "The caught exception stopped the program: $stopped" }
                val (name, line) = data.getAsJsonArray("frame").let { it[0].asString to it[1].asInt }
                val top = stopped.getAsJsonArray("frames").first().asJsonObject
                check(name in top.string("name") && top.int("line") == line) { "Stopped at: $stopped" }

                // the hint is the exceptionInfo answer: the exception, its description and its stack, from check()
                var hints = listOf<String>()
                awaitUi("LSP4IJ shows the exception's information where it was thrown", 30.seconds, { "Hints: $hints" }) {
                    hints = JsonParser.parseString(debugger.hintTexts()).asJsonArray.map { it.asString }
                    hints.any { hint ->
                        listOf(data.string("exception"), data.string("description"), data.string("stackTrace")).all { it in hint }
                    }
                }

                debugger.resume(project, module)
                val ended = debugger.awaitState(project, module, "Resume lets the program fail", 60.seconds) { it.has("exitCode") }
                check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
            } finally {
                debugger.exceptionFilters(previous)
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X313") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                // TODO LSP4IJ: UP32 — LSP4IJ sends a breakpoint's condition but not its log message, so the logpoint is
                // checked in VS Code only; set it here too once a release sends log messages (#1759)
                debugger.debugWhen(project, module, uri(data.string("file")), data.int("breakpointLine"), data.string("condition"))

                fun evaluate(expression: String) = JsonParser.parseString(debugger.evaluate(project, module, expression)).asJsonObject
                data.strings("conditionStops").forEachIndexed { stop, expected ->
                    // a stop is the next one once i has the value it stops at
                    awaitUi("the program stops where i is $expected", 60.seconds) {
                        runCatching { evaluate("i")["value"]?.asString }.getOrNull() == expected
                    }
                    if (stop == 0) {
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
                    debugger.resume(project, module)
                }
                val ended = debugger.awaitState(project, module, "the program runs to its exit", 60.seconds) { it.has("exitCode") }
                check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X314") { data ->
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

                // the Evaluate Expression dialog's evaluations run code: LSP4IJ sends them as the console's (repl)
                fun run(code: String) = JsonParser.parseString(debugger.evaluateFrom(project, module, code, "DIALOG")).asJsonObject
                val (call, value) = data.strings("call")
                val ran = run(call)
                check(ran["value"]?.asString == value) { "$call is not $value: $ran" }
                val (watch, watched) = data.strings("watch")
                val shown = JsonParser.parseString(debugger.evaluate(project, module, watch)).asJsonObject
                check(shown["value"]?.asString == watched) { "$watch is not $watched after $call: $shown" }
                // TODO LSP4IJ: UP40, UP41 — LSP4IJ never sends DAP `cancel` (UP41) and does not handle `invalidated` (UP40,
                // #1766), so both are checked in VS Code only; check them here too once a release has them
                val ended = run(data.string("loop"))["error"]?.asString.orEmpty()
                check(data.string("limited") in ended) { "${data.string("loop")} does not end at its time limit: $ended" }

                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X315") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                debugger.debugRunningProgram(project, module, uri(data.string("file")), data.int("breakpointLine"))
                debugger.awaitState(
                    project,
                    module,
                    "the program stops at the breakpoint",
                    60.seconds,
                ) { it["suspended"]?.asBoolean == true }

                // the worker's service answers once its fiber, which the stop holds otherwise, has done its work;
                // TODO LSP4IJ: UP40 — LSP4IJ does not handle `invalidated` (#1766), so its event is checked in VS Code only
                val (call, value) = data.strings("call")
                val ran = JsonParser.parseString(debugger.evaluateFrom(project, module, call, "DIALOG")).asJsonObject
                check(ran["value"]?.asString == value) { "$call is not $value: $ran" }

                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X316") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
                debugger.awaitState(project, module, "the program stops at the breakpoint", 60.seconds) {
                    it["suspended"]?.asBoolean == true
                }

                // the object's fields, then its lazy variables, whose values run code only when asked for
                val expands = data.string("expands")
                val fields = JsonParser.parseString(debugger.variables(project, module, expands)).asJsonArray.map { it.asJsonObject }
                val expected = data.getAsJsonArray("fields").map { pair -> pair.asJsonArray.map { it.asString } }
                check(fields.map { listOf(it.string("name"), it.string("value")) } == expected) { "$expands shows $fields" }
                data.getAsJsonArray("lazy").map { it.asJsonArray }.forEach { pair ->
                    val (name, value) = pair[0].asString to pair[1].asString
                    val lazy = fields.single { it.string("name") == name }
                    val shown =
                        if (lazy.has("link")) {
                            // with LSP4IJ #1768, a lazy variable has a Show value link
                            debugger.showValue(project, module, "$expands/$name")
                        } else {
                            // TODO LSP4IJ: UP35 — LSP4IJ 0.21.0 ignores presentationHint.lazy (#1768), so a lazy variable
                            // expands to one child that has its value
                            JsonParser
                                .parseString(debugger.variables(project, module, "$expands/$name"))
                                .asJsonArray
                                .single()
                                .asJsonObject
                                .string("value")
                        }
                    check(shown == value) { "$name shows $shown, not $value" }
                }

                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
                check(data.string("output") in exited.string("console")) { "Console after Resume: ${exited.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X317") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
                debugger.awaitState(project, module, "the program stops at the breakpoint", 60.seconds) {
                    it["suspended"]?.asBoolean == true
                }

                fun locals() =
                    JsonParser
                        .parseString(debugger.variables(project, module, ""))
                        .asJsonArray
                        .map { it.asJsonObject }
                        .associate { it.string("name") to it.string("value") }

                fun pairs(key: String) = data.getAsJsonArray(key).map { pair -> pair.asJsonArray.let { it[0].asString to it[1].asString } }
                val decimal = locals()
                pairs("decimal").forEach { (name, value) -> check(decimal[name] == value) { "$name is not $value: $decimal" } }
                // two objects with the same fields have different identity codes
                val identities = data.strings("identities").map { decimal.getValue(it) }
                identities.forEach { check(Regex(".+ #\\d+").matches(it)) { "No identity code: $identities" } }
                check(identities.distinct().size == identities.size) { "The identity codes are not different: $identities" }

                // TODO LSP4IJ: UP36 — LSP4IJ 0.21.0 has no Show Values as Hexadecimal (#1769), so the hexadecimal values are
                // checked only with a plugin that has it
                if (debugger.showHex(project, module, true)) {
                    val hex = pairs("hex").toMap()
                    awaitUi("the values show in hexadecimal", 30.seconds) { locals().filterKeys { it in hex } == hex }
                    debugger.showHex(project, module, false)
                    val shown = pairs("decimal").toMap()
                    awaitUi("the values show in decimal again", 30.seconds) { locals().filterKeys { it in shown } == shown }
                }

                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
                check(data.string("output") in exited.string("console")) { "Console after Resume: ${exited.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X318") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
                debugger.awaitState(project, module, "the program stops at the breakpoint", 60.seconds) {
                    it["suspended"]?.asBoolean == true
                }

                val expands = data.string("expands")
                val first = JsonParser.parseString(debugger.pagedVariables(project, module, expands, 0)).asJsonObject
                val elements = first.getAsJsonArray("children").map { it.asJsonObject }
                check(elements.first().string("name") == "[0]" && elements.first().string("value") == "0") { "First: ${elements.first()}" }
                if (first["more"].asBoolean) {
                    // with LSP4IJ #1770, a page of elements, then IntelliJ's own "..." node, which reads the next page
                    val total = data.int("elements")
                    check(elements.size == data.int("shown") && first.int("remaining") == total - data.int("shown")) {
                        "${elements.size} elements and ${first.int("remaining")} more"
                    }
                    val all = JsonParser.parseString(debugger.pagedVariables(project, module, expands, 10)).asJsonObject
                    val every = all.getAsJsonArray("children").map { it.asJsonObject }
                    val (name, value) = data.strings("last")
                    check(every.size == total && !all["more"].asBoolean) { "${every.size} elements, more: ${all["more"]}" }
                    check(every.last().string("name") == name && every.last().string("value") == value) { "Last: ${every.last()}" }
                } else {
                    // TODO LSP4IJ: UP42 — LSP4IJ asks for all of a variable's children at once, never a page by
                    // indexedVariables (#1770), so a large array shows its first elements and how many more there are
                    check(elements.size == data.int("shown") + 1) { "${elements.size} elements shown: ${elements.takeLast(2)}" }
                    check(elements.last().string("name") == "..." && elements.last().string("value") == data.string("more")) {
                        "Last: ${elements.last()}"
                    }
                }

                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
                check(data.string("output") in exited.string("console")) { "Console after Resume: ${exited.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X319") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
                debugger.awaitState(project, module, "the program stops at the breakpoint", 60.seconds) {
                    it["suspended"]?.asBoolean == true
                }

                // the Variables view's Set Value: a value of another type is refused with the reason, and the variable
                // keeps its value; an expression of its type is its new value
                val (name, expression, value) = data.strings("set")
                val (other, reason) = data.strings("refused")

                fun shown() =
                    JsonParser
                        .parseString(debugger.variables(project, module, ""))
                        .asJsonArray
                        .map { it.asJsonObject }
                        .single { it.string("name") == name }
                        .string("value")
                val before = shown()
                val refused = debugger.setValue(project, module, name, other)
                check(reason in refused) { "Set Value of $other says $refused" }
                check(shown() == before) { "$name changed to ${shown()}" }
                check(debugger.setValue(project, module, name, expression).isEmpty()) { "Set Value of $expression failed" }
                check(shown() == value) { "$name is ${shown()}, not $value" }

                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
                check(data.string("output") in exited.string("console")) { "Console after Resume: ${exited.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X320") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
                debugger.awaitState(project, module, "the program stops at the breakpoint", 60.seconds) {
                    it["suspended"]?.asBoolean == true
                }

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
                // TODO LSP4IJ: UP37 — LSP4IJ 0.21.0's expression fragment fails IntelliJ's Document/PSI check once it is
                //  edited, so the chosen name replaces what is typed only with LSP4IJ #1763, whose fragment is physical
                if (chosen["inserted"].asBoolean) {
                    check(offered.string("text") == members.first() + members[1]) { "Choosing ${members[1]}: $offered" }
                    check(chosen.string("text") == completed) { "Completing $typed with $name: $chosen" }
                }

                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
                check(data.string("output") in exited.string("console")) { "Console after Resume: ${exited.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X321") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            val file = uri(data.string("file"))
            try {
                debugger.debug(project, module, file, data.int("breakpointLine"))
                debugger.awaitState(project, module, "the program stops at the breakpoint", 60.seconds) {
                    it["suspended"]?.asBoolean == true
                }

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
                    30.seconds,
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
                val stopped =
                    debugger.awaitState(project, module, "the program stops at the moved line", 60.seconds) {
                        it["suspended"]?.asBoolean == true && it["line"]?.asInt == moved
                    }
                val frames = stopped.getAsJsonArray("frames").map { it.asJsonObject }
                data.getAsJsonArray("frames").map { it.asJsonArray }.forEachIndexed { index, expected ->
                    val frame = frames.getOrNull(index)
                    check(frame != null && expected[0].asString in frame.string("name") && frame.int("line") == expected[1].asInt) {
                        "Frame $index is not ${expected[0].asString} at ${expected[1].asInt}: $frames"
                    }
                }

                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
                check(data.string("output") in exited.string("console")) { "Console after Resume: ${exited.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X322") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { execution, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            val file = uri(data.string("file"))
            val line = data.int("breakpointLine")
            for (round in 1..data.int("rounds")) {
                // a run to its exit
                execution.run(project, module, file, "")
                awaitUi("run $round finishes", 60.seconds) { execution.exitCode(module) != Int.MIN_VALUE }
                check(execution.exitCode(module) == 0 && data.string("done") in execution.output(module)) {
                    "Run $round: exit code ${execution.exitCode(module)}: ${execution.output(module)}"
                }

                // a run that loops until Stop ends it
                execution.run(project, module, file, data.string("spin"))
                awaitUi("looping run $round starts", 60.seconds) { data.string("started") in execution.output(module) }
                check(
                    JsonParser.parseString(execution.active(project)).asJsonObject.int("runs") >= 1,
                ) { "Looping run $round is not active" }
                execution.stop(module)
                awaitUi("stopped run $round reports its exit", 30.seconds) { execution.exitCode(module) != Int.MIN_VALUE }
                check(data.string("done") !in execution.output(module)) { "Stopped run $round: ${execution.output(module)}" }

                // a debug session that stops at a breakpoint and resumes to its exit
                try {
                    debugger.debug(project, module, file, line)
                    val stopped =
                        debugger.awaitState(project, module, "debug session $round stops at the breakpoint", 60.seconds) {
                            it["suspended"]?.asBoolean == true
                        }
                    check(stopped.int("line") == line) { "Debug session $round stopped at: $stopped" }
                    check(JsonParser.parseString(execution.active(project)).asJsonObject.int("debugSessions") >= 1) {
                        "Debug session $round is not open while it is stopped"
                    }
                    debugger.resume(project, module)
                    val ended =
                        debugger.awaitState(
                            project,
                            module,
                            "debug session $round runs to its exit",
                            60.seconds,
                        ) { it.has("exitCode") }
                    check(ended.int("exitCode") == 0 && data.string("done") in ended.string("console")) { "Debug session $round: $ended" }
                } finally {
                    debugger.clearBreakpoints(project)
                }

                // the round leaves no run and no debug session open
                awaitUi("round $round leaves no run or debug session", 30.seconds) {
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
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            val file = uri(data.string("file"))
            val cursor = data.int("cursorLine")
            try {
                debugger.debug(project, module, file, data.int("breakpointLine"))
                debugger.awaitState(project, module, "the program stops at the breakpoint", 60.seconds) {
                    it["suspended"]?.asBoolean == true
                }

                // Run to Cursor, on a line in the loop after a long count
                debugger.runToCursor(project, module, file, cursor)
                // TODO LSP4IJ: UP44 — LSP4IJ removes Run to Cursor's temporary breakpoint once the continue request is answered,
                //  not when the program stops there, so the program runs past the cursor to its exit; with a fix it stops at the
                //  cursor's line, before it prints, and this checks that and Resume to the exit, as VS Code's X323 does
                val ended =
                    debugger.awaitState(project, module, "Run to Cursor resumes the program, which runs past the cursor", 60.seconds) {
                        it.has("exitCode") || (it["suspended"]?.asBoolean == true && it["line"]?.asInt == cursor)
                    }
                check(ended.has("exitCode")) { "Run to Cursor stopped at line $cursor: UP44 is fixed, so X323 is full in IntelliJ: $ended" }
                check(ended.int("exitCode") == data.int("exitCode")) { "Debug session: $ended" }
                check(data.string("output") in ended.string("console")) { "Console after Run to Cursor: ${ended.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X324") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
                val stopped =
                    debugger.awaitState(project, module, "the program stops in the lambda", 60.seconds) {
                        it["suspended"]?.asBoolean == true && it.has("frames")
                    }
                val frames = stopped.getAsJsonArray("frames").map { it.asJsonObject }
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
                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
                check(data.string("output") in exited.string("console")) { "Console after Resume: ${exited.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X325") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                // by then, run() has asked the busy worker for its count, and waits for the answer
                debugger.debugWhen(project, module, uri(data.string("file")), data.int("breakpointLine"), data.string("condition"))
                debugger.awaitState(project, module, "the worker stops at its count", 60.seconds) { it["suspended"]?.asBoolean == true }

                // the stopped fiber first, then run()'s, which waits for the worker, with its own stack
                val threads =
                    JsonParser
                        .parseString(debugger.threads(project, module))
                        .asJsonArray
                        .map { it.asJsonObject }
                check(threads.map { it.string("name") } == data.strings("threads")) { "Threads: $threads" }
                val frames = threads[1].getAsJsonArray("frames").map { it.asJsonObject }
                val expected = data.getAsJsonArray("waitingFrames").map { it.asJsonArray }
                check(frames.size == expected.size) { "The waiting fiber's frames: $frames" }
                expected.forEachIndexed { index, frame ->
                    check(frame[0].asString in frames[index].string("name") && frames[index].int("line") == frame[1].asInt) {
                        "Frame $index is not ${frame[0].asString} at ${frame[1].asInt}: $frames"
                    }
                }

                debugger.clearBreakpoints(project)
                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
                check(data.string("output") in exited.string("console")) { "Console after Resume: ${exited.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    // TODO LSP4IJ: UP45 — loaded sources and modules need LSP4IJ's Loaded Sources and Modules views (#1774); the catalog
    // keeps X326 not-implemented until a release has them, so this runs only with an LSP4IJ that has them
    // (-Plsp4ijChannel=nightly) and X326 made full
    case("X326") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
                debugger.awaitState(project, module, "the program stops in the lambda", 60.seconds) { it["suspended"]?.asBoolean == true }

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
                awaitUi("the Loaded Sources and Modules views show the program's", 30.seconds, { "Views: $shown" }) {
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
                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
                check(data.string("output") in exited.string("console")) { "Console after Resume: ${exited.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    case("X327") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
                debugger.awaitState(project, module, "the program stops at the line with two calls", 60.seconds) {
                    it["suspended"]?.asBoolean == true
                }

                // the calls of the line, in the order it makes them, as Smart Step Into offers them
                val targets = JsonParser.parseString(debugger.smartStepTargets(project, module)).asJsonArray.map { it.asString }
                check(targets == data.strings("targets")) { "Smart Step Into variants: $targets" }

                // into the second call: the first runs without stopping, and the step ends in the chosen one
                debugger.smartStepInto(project, module, data.string("into"))
                val (name, line) = data.getAsJsonArray("frame").let { it[0].asString to it[1].asInt }
                debugger.awaitState(project, module, "the step stops in the chosen call", 60.seconds) {
                    it["suspended"]?.asBoolean == true &&
                        it["line"]?.asInt == line &&
                        it
                            .getAsJsonArray("frames")
                            ?.firstOrNull()
                            ?.asJsonObject
                            ?.string("name")
                            ?.contains(name) == true
                }

                debugger.clearBreakpoints(project)
                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
                check(data.string("output") in exited.string("console")) { "Console after Resume: ${exited.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    // TODO LSP4IJ: UP46 — function breakpoints need LSP4IJ's breakpoint type for them (#1780); the catalog keeps X328
    // not-implemented until a release has it, so this runs only with a plugin built from it (-Plsp4ijPlugin) and X328 made full
    case("X328") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                // as the Breakpoints dialog adds it, with a condition on the method's parameter
                check(debugger.addFunctionBreakpoint(project, data.string("function"), data.string("condition"))) {
                    "This LSP4IJ has no DAP function breakpoints"
                }
                debugger.debug(project, module, uri(data.string("file")), 0)

                // the second call, the first whose n is more than 5, below run()'s line that makes it
                val (name, line) = data.getAsJsonArray("frame").let { it[0].asString to it[1].asInt }
                val (caller, callerLine) = data.getAsJsonArray("caller").let { it[0].asString to it[1].asInt }
                val stopped =
                    debugger.awaitState(project, module, "the program stops where it enters the method", 60.seconds) {
                        it["suspended"]?.asBoolean == true && it["line"]?.asInt == line
                    }
                val frames = stopped.getAsJsonArray("frames").map { it.asJsonObject }
                check(name in frames[0].string("name") && caller in frames[1].string("name") && frames[1].int("line") == callerLine) {
                    "Frames: $frames"
                }
                val shown = JsonParser.parseString(debugger.functionBreakpoints(project, module)).asJsonArray.map { it.asJsonObject }
                check(shown.map { it.string("state") } == listOf("verified")) { "Function breakpoints: $shown" }

                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
                check(data.string("output") in exited.string("console")) { "Console after Resume: ${exited.string("console")}" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    // TODO LSP4IJ: UP47 — Reset Frame needs LSP4IJ's drop-frame handler (#1778); the catalog keeps X329 not-implemented
    // until a release has it, so this runs only with a plugin built from it (-Plsp4ijPlugin) and X329 made full
    case("X329") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
                debugger.awaitState(project, module, "the program stops in inner()", 60.seconds) { it["suspended"]?.asBoolean == true }

                // the frames that the Frames view offers Reset Frame on: not run(), which the runtime called
                val offered = JsonParser.parseString(debugger.resettable(project, module)).asJsonArray.map { it.asBoolean }
                check(offered == data.getAsJsonArray("canRestart").map { it.asBoolean }) { "Reset Frame offered: $offered" }

                // as Reset Frame does: its caller calls it again, and it stops at its first line
                debugger.clearBreakpoints(project)
                debugger.resetFrame(project, module, data.string("restart"))
                val (name, line) = data.getAsJsonArray("frame").let { it[0].asString to it[1].asInt }
                debugger.awaitState(project, module, "the restarted frame stops at its first line", 60.seconds) {
                    it["suspended"]?.asBoolean == true &&
                        it["line"]?.asInt == line &&
                        it
                            .getAsJsonArray("frames")
                            ?.firstOrNull()
                            ?.asJsonObject
                            ?.string("name")
                            ?.contains(name) == true
                }

                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                val console = exited.string("console")
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
                check(data.string("output") in console) { "Console after Resume: $console" }
                check(console.split(data.string("printed")).size - 1 == 2) { "Console after Resume: $console" }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    // TODO LSP4IJ: UP48 — data breakpoints need LSP4IJ's Break on Value Change (#1782); 0.21.0 has none, so the catalog
    // keeps X330 not-implemented and this runs only with a plugin built from it (-Plsp4ijPlugin), X330 made full
    case("X330") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            try {
                debugger.debug(project, module, uri(data.string("file")), data.int("breakpointLine"))
                debugger.awaitState(project, module, "the program stops before the first add()", 60.seconds) {
                    it["suspended"]?.asBoolean == true
                }

                // as Break on Value Change on the counter's count, in the Variables view's menu, adds it
                check(debugger.breakOnValueChange(project, "${data.string("variable")}/${data.string("field")}")) {
                    "Break on Value Change is not offered"
                }
                var watched = listOf<JsonObject>()
                awaitUi("the data breakpoint is installed", 30.seconds, { "Data breakpoints: $watched" }) {
                    watched = JsonParser.parseString(debugger.dataBreakpoints(project, module)).asJsonArray.map { it.asJsonObject }
                    watched.singleOrNull()?.let { it.string("text") == data.string("description") && it.string("state") == "verified" } ==
                        true
                }

                // each add() stops at the line after the one that changed the count
                debugger.clearLineBreakpoints(project)
                val (name, line) = data.getAsJsonArray("frame").let { it[0].asString to it[1].asInt }
                repeat(data.getAsJsonArray("changes").size()) { index ->
                    debugger.resume(project, module)
                    debugger.awaitState(project, module, "change ${index + 1} stops the program", 60.seconds) {
                        it["suspended"]?.asBoolean == true &&
                            it["line"]?.asInt == line &&
                            it
                                .getAsJsonArray("frames")
                                ?.firstOrNull()
                                ?.asJsonObject
                                ?.string("name")
                                ?.contains(name) == true
                    }
                }

                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
                check(data.string("output") in exited.string("console")) { "Console after Resume: ${exited.string("console")}" }
                // the adapter's data ID lasts only the session, so the breakpoint goes with it
                awaitUi("the data breakpoint goes with the session", 30.seconds) {
                    JsonParser.parseString(debugger.dataBreakpoints(project, module)).asJsonArray.isEmpty
                }
            } finally {
                debugger.clearBreakpoints(project)
            }
        }
    }

    // TODO LSP4IJ: UP50 — IntelliJ draws inline values for Ecstasy only with an LSP4IJ that asks the language server
    // for them and expands the frame's locals; 0.21.0 draws none, so the catalog keeps X331 not-implemented and this
    // runs only with a plugin built from our local branch (-Plsp4ijPlugin) and X331 made full
    case("X331") { data ->
        val document = open(data.string("file"), data.string("source"))
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList())))
        clean(document)
        embedded { _, project ->
            val debugger = with(driver) { utility(DebugSessionProbe::class) }
            val module = data.string("module")
            val file = uri(data.string("file"))
            try {
                debugger.debug(project, module, file, data.int("breakpointLine"))
                debugger.awaitState(project, module, "the program stops in add()", 60.seconds) { it["suspended"]?.asBoolean == true }

                // the values IntelliJ draws at the ends of the lines, each variable of the stopped frame once
                val expected = data.getAsJsonObject("frameValues").entrySet().associate { it.key to it.value.asString }
                var shown = listOf<Pair<Int, String>>()
                awaitUi("the debugger draws the inline values", 30.seconds, { "Inline values: $shown" }) {
                    shown =
                        JsonParser.parseString(debugger.inlineValues(project, file)).asJsonArray.map {
                            it.asJsonObject.int("line") to it.asJsonObject.string("text")
                        }
                    // the renderer's text is the name and the value, which it draws with " = " between them
                    expected.all { (name, value) -> shown.any { (_, text) -> text.filterNot { it in " =" } == "$name$value" } }
                }
                check(shown.none { (_, text) -> text.startsWith("count") || text.startsWith("after") }) { "Inline values: $shown" }

                debugger.clearBreakpoints(project)
                debugger.resume(project, module)
                val exited = debugger.awaitState(project, module, "Resume runs the program to its exit", 60.seconds) { it.has("exitCode") }
                check(exited.int("exitCode") == data.int("exitCode")) { "Debug session: $exited" }
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
    ): Boolean

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

    fun hintShown(): Boolean

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
