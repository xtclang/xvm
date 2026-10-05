package org.xtclang.idea.playbook

import com.google.gson.JsonParser
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** Shared real Gradle producer; assertions go through installed settings, progress and diagnostics. */
internal fun ParityScenarios.compilerImportCases() {
    listOf("X260", "X261", "X262").forEach { id ->
        case(id) { data ->
            val root = with(driver) { Path.of(singleProject().getBasePath()) }
            check(
                root.fileName.toString() == "workspace" &&
                    root.parent.fileName
                        .toString()
                        .startsWith("run-"),
            )
            val shared = Path.of(System.getProperty("xtc.playbook.scenarios")).parent
            val repository = shared.parent.parent.parent
            val fixture = common["compilerImport"].asJsonObject
            val files =
                listOf(
                    "build.gradle.kts",
                    "settings.gradle.kts",
                    "gradle.properties",
                    "gradlew",
                    "gradlew.bat",
                    "gradle/wrapper/gradle-wrapper.jar",
                    "gradle/wrapper/gradle-wrapper.properties",
                )
            // Fail before touching a pre-existing build; every test owns an isolated workspace.
            files.forEach { check(!Files.exists(root.resolve(it))) { "Fixture would replace $it" } }
            val control = Files.createDirectories(root.resolve(".compiler-import-playbook"))
            val report = root.resolve(".gradle/xtc/lsp-model.json")
            val previousReport = report.takeIf(Files::exists)?.let(Files::readAllBytes)
            val model =
                JsonParser
                    .parseString(
                        fixture["model"].toString().replace("\${workspace}", directory.toUri().toString().trimEnd('/')),
                    ).asJsonObject
            val replacement =
                model.deepCopy().apply {
                    getAsJsonArray(
                        "sourceSets",
                    )[0].asJsonObject.getAsJsonArray("resourceRoots").asList().clear()
                }
            val steps = data["steps"].rows()

            fun refresh() =
                with(driver) {
                    withContext(OnDispatcher.EDT) { utility(CompilerSettingsPage::class).refreshBuildModel(singleProject()) }
                }

            try {
                Files.writeString(control.resolve("invocations.txt"), "")
                files.forEach { file ->
                    val source =
                        if (file.contains("gradlew") ||
                            file.startsWith("gradle/")
                        ) {
                            repository.resolve(file)
                        } else {
                            shared.resolve("compiler-import/$file")
                        }
                    Files.createDirectories(root.resolve(file).parent)
                    Files.copy(source, root.resolve(file))
                }
                if (!System.getProperty("os.name").startsWith("Windows")) check(root.resolve("gradlew").toFile().setExecutable(true))
                write(fixture.string("file"), fixture.string("source"))
                write(fixture.string("resource"), fixture.string("contents"))
                Files.createDirectories(report.parent)
                Files.writeString(report, model.toString())
                with(driver) { withContext(OnDispatcher.EDT) { utility(CompilerSettingsPage::class).useBuildModel(singleProject()) } }
                val document = open(fixture.string("file"))
                clean(document)
                val page = with(driver) { withContext(OnDispatcher.EDT) { utility(CompilerImportPage::class).open(singleProject()) } }

                fun text() = with(driver) { withContext(OnDispatcher.EDT) { page.text() } }

                fun click(label: String) = with(driver) { withContext(OnDispatcher.EDT) { page.click(label) } }
                val baseline = page.accepted()
                check(baseline != "null")
                try {
                    steps.forEach { step ->
                        val operation = "$id-${step.string("operation")}"
                        val prepare = step["prepare"].asBoolean
                        val title = if (prepare) "Prepare Ecstasy compiler inputs" else "Refresh Ecstasy compiler paths"
                        Files.writeString(control.resolve("model.json"), replacement.toString())
                        Files.writeString(
                            control.resolve("request.properties"),
                            "operation=$operation\noutput=${step.string("output")}\noutcome=${step.string("outcome")}\n",
                        )
                        click(if (prepare) "Prepare generated resources" else "Refresh Gradle model")
                        awaitUi("$id real Gradle producer reached its publication gate", 45.seconds) {
                            control.resolve("$operation.started").takeIf(Files::exists)?.let(Files::readString) ==
                                if (prepare) "prepareXtcLspModel" else "exportXtcLspModel"
                        }
                        refresh()
                        check(page.accepted() == baseline) { "Pending output replaced the accepted report" }
                        clean(document)
                        if (step["duplicate"].asBoolean) {
                            click("Refresh Gradle model")
                            awaitUi("Duplicate import is refused in the settings page", 10.seconds) { text().contains("already running") }
                        }
                        if (step["cancel"].asBoolean) {
                            with(driver) {
                                withContext(OnDispatcher.EDT) { utility(ProgressUi::class).show(singleProject()) }
                                awaitUi("$id visible import Cancel button", 15.seconds) {
                                    withContext(OnDispatcher.EDT) { utility(ProgressUi::class).cancel(singleProject(), title) }
                                }
                            }
                        } else {
                            Files.writeString(control.resolve("$operation.release"), "release\n")
                        }
                        awaitUi("$id import completion status", 30.seconds) {
                            val description = page.description()
                            description.contains("Last import:") && description.contains(step.string("expected"), ignoreCase = true) &&
                                with(
                                    driver,
                                ) { withContext(OnDispatcher.EDT) { !utility(ProgressUi::class).visible(singleProject(), title) } }
                        }
                        Files.writeString(control.resolve("$operation.release"), "cleanup\n")
                        val producer = Files.readString(control.resolve("$operation.pid")).toLong()
                        check(producer > 0)
                        awaitUi("$id Gradle fixture action retired or its cancelled JVM exited", 15.seconds) {
                            Files.exists(control.resolve("$operation.finished")) ||
                                (
                                    step["cancel"].asBoolean &&
                                        ProcessHandle
                                            .of(producer)
                                            .map { it.isAlive }
                                            .orElse(false)
                                            .not()
                                )
                        }
                        refresh()
                        with(driver) { withContext(OnDispatcher.EDT) { page.resetPage() } }
                        awaitUi("$id settings page displays the last outcome", 10.seconds) {
                            text().contains("Last import:") && text().contains(step.string("expected"), ignoreCase = true)
                        }
                        if (step["accepted"].asBoolean) {
                            check(page.accepted() != baseline)
                            errors(document)
                        } else {
                            check(page.accepted() == baseline) { "Rejected report was accepted by a later consumer" }
                            clean(document)
                        }
                        check(Files.readAllLines(control.resolve("invocations.txt")).count { it.startsWith("$operation:") } == 1)
                        check(document.text == fixture.string("source"))
                    }
                } finally {
                    with(driver) { withContext(OnDispatcher.EDT) { page.closePage() } }
                }
            } finally {
                steps.forEach { Files.writeString(control.resolve("$id-${it.string("operation")}.release"), "cleanup\n") }
                with(driver) { withContext(OnDispatcher.EDT) { utility(ProgressUi::class).hide(singleProject()) } }
                files.forEach { Files.deleteIfExists(root.resolve(it)) }
                if (previousReport == null) Files.deleteIfExists(report) else Files.write(report, previousReport)
                with(driver) { withContext(OnDispatcher.EDT) { utility(CompilerSettingsPage::class).clearProjectGraph(singleProject()) } }
                refresh()
            }
        }
    }
}

@Remote("org.xtclang.idea.playbook.probe.CompilerImportPage", plugin = "org.xtclang.playbook.probe")
internal interface CompilerImportPage {
    fun open(project: Project): CompilerImportPage

    fun click(label: String)

    fun text(): String

    fun accepted(): String

    fun description(): String

    fun resetPage()

    fun closePage()
}
