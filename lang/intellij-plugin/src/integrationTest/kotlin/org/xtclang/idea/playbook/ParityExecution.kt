package org.xtclang.idea.playbook

import com.google.gson.JsonParser
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds

/** Embedded build and run through Build Project, Ecstasy run configurations, Stop and the Run lens. */
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

        awaitUi("Run lens on a module with run()", 45.seconds) { data.string("runTitle") in runTitles(runnable) }
        check(runTitles(library).isEmpty()) { "No Run lens on a module without run(): ${runTitles(library)}" }
        replace(library, data.string("uncallable"))
        clean(library)
        check(runTitles(library).isEmpty()) { "No Run lens when run() needs two arguments: ${runTitles(library)}" }
        replace(library, data.string("runnableLibrary"))
        clean(library)
        awaitUi("Run lens after adding run()", 45.seconds) { data.string("libraryRunTitle") in runTitles(library) }
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
            check(Files.readString(file) == data.string("brokenLibrary")) { "Build saves all files first" }
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
}

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

/** Embedded Build Project and runs, recorded by the IDE-side probe. */
@Remote("org.xtclang.idea.playbook.probe.EmbeddedExecutionUi", plugin = "org.xtclang.playbook.probe")
interface EmbeddedExecutionProbe {
    fun mode(
        project: Project,
        value: String,
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

    fun rerun(
        project: Project,
        module: String,
    )

    fun output(module: String): String

    fun exitCode(module: String): Int

    fun stop(module: String)
}
