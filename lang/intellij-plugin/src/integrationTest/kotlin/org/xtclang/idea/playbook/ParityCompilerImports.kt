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
    listOf("X260", "X261", "X262", "X263", "X264").forEach { id ->
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
            val aggregate = root.resolve(".gradle/xtc/lsp-workspace.json")
            val previousAggregate = aggregate.takeIf(Files::exists)?.let(Files::readAllBytes)
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
                Files.deleteIfExists(aggregate)
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
                data["included"]?.rows()?.forEach { build ->
                    val target = Files.createDirectories(root.resolve(build.string("folder")))
                    val gate = Files.createDirectories(target.resolve(".compiler-import-playbook"))
                    Files.copy(shared.resolve("compiler-import/build.gradle.kts"), target.resolve("build.gradle.kts"))
                    Files.writeString(
                        target.resolve("settings.gradle.kts"),
                        "rootProject.name = \"${build.string("module")}\"\n" +
                            if (build.string("folder") == "included") "includeBuild(\"nested\")\n" else "",
                    )
                    val nested =
                        fixture["model"]
                            .toString()
                            .replace("\${workspace}", target.toUri().toString().trimEnd('/'))
                            .replace(fixture.string("module"), build.string("module"))
                    Files.writeString(
                        target.resolve("${build.string("module")}.x"),
                        fixture.string("source").replace(fixture.string("module"), build.string("module")),
                    )
                    Files.createDirectories(target.resolve("processed"))
                    Files.writeString(target.resolve(fixture.string("resource")), fixture.string("contents"))
                    Files.writeString(gate.resolve("model.json"), nested)
                    Files.writeString(gate.resolve("request.properties"), "operation=included\noutput=valid\noutcome=success\n")
                    Files.writeString(gate.resolve("included.release"), "release\n")
                }
                if (data.has(
                        "included",
                    )
                ) {
                    Files.writeString(
                        root.resolve("settings.gradle.kts"),
                        "rootProject.name = \"composite\"\nincludeBuild(\"included\")\n",
                    )
                }
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
                        if (step.has("automatic")) {
                            with(driver) { withContext(OnDispatcher.EDT) { page.sync() } }
                        } else {
                            click(if (prepare) "Prepare generated resources" else "Refresh Gradle model")
                        }
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
                            data["included"]?.rows()?.let { included ->
                                val accepted = JsonParser.parseString(page.accepted()).asJsonObject
                                check(accepted["sourceSets"].asJsonArray.size() == included.size + 1)
                                check(accepted["buildRoots"].asJsonArray.size() == included.size + 1)
                            }
                            errors(document)
                        } else {
                            check(page.accepted() == baseline) { "Rejected report was accepted by a later consumer" }
                            clean(document)
                        }
                        check(Files.readAllLines(control.resolve("invocations.txt")).count { it.startsWith("$operation:") } == 1)
                        check(document.text == fixture.string("source"))
                    }
                    if (id == "X264") {
                        Files.writeString(aggregate, "{")
                        with(driver) { withContext(OnDispatcher.EDT) { page.refreshReports() } }
                        Files.writeString(aggregate, model.toString())
                        with(driver) { withContext(OnDispatcher.EDT) { page.refreshReports() } }
                        // Consume the real VFS notification; do not call publish from the driver.
                        clean(document)
                    }
                } finally {
                    if (id == "X264") with(driver) { withContext(OnDispatcher.EDT) { page.unlink() } }
                    with(driver) { withContext(OnDispatcher.EDT) { page.closePage() } }
                }
            } finally {
                steps.forEach { Files.writeString(control.resolve("$id-${it.string("operation")}.release"), "cleanup\n") }
                with(driver) { withContext(OnDispatcher.EDT) { utility(ProgressUi::class).hide(singleProject()) } }
                files.forEach { Files.deleteIfExists(root.resolve(it)) }
                if (previousReport == null) Files.deleteIfExists(report) else Files.write(report, previousReport)
                if (previousAggregate == null) Files.deleteIfExists(aggregate) else Files.write(aggregate, previousAggregate)
                if (data.has("included")) root.resolve("included").toFile().deleteRecursively()
                with(driver) { withContext(OnDispatcher.EDT) { utility(CompilerSettingsPage::class).clearProjectGraph(singleProject()) } }
                refresh()
            }
        }
    }
    case("X265") { data ->
        val primary = with(driver) { singleProject() }
        val root = Files.createDirectories(Path.of(primary.getBasePath()).parent.resolve("retired-import"))
        val fixture = common["compilerImport"].asJsonObject
        val shared = Path.of(System.getProperty("xtc.playbook.scenarios")).parent
        val repository = shared.parent.parent.parent
        val control = Files.createDirectories(root.resolve(".compiler-import-playbook"))
        listOf(
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "gradlew",
            "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
        ).forEach { file ->
            Files.createDirectories(root.resolve(file).parent)
            Files.copy(
                if (file.contains("gradlew") || file.startsWith("gradle/")) {
                    repository.resolve(file)
                } else {
                    shared.resolve("compiler-import/$file")
                },
                root.resolve(file),
            )
        }
        if (!System.getProperty("os.name").startsWith("Windows")) check(root.resolve("gradlew").toFile().setExecutable(true))
        val model = fixture["model"].toString().replace("\${workspace}", root.toUri().toString().trimEnd('/'))
        Files.writeString(root.resolve(fixture.string("file")), fixture.string("source"))
        Files.createDirectories(root.resolve("processed"))
        Files.writeString(root.resolve(fixture.string("resource")), fixture.string("contents"))
        val report = root.resolve(".gradle/xtc/lsp-model.json")
        Files.createDirectories(report.parent)
        Files.writeString(report, model)
        val operation = data.string("operation")
        Files.writeString(control.resolve("model.json"), model)
        Files.writeString(control.resolve("request.properties"), "operation=$operation\noutput=valid\noutcome=success\n")
        val first = ClientProtocol(driver) { primary }
        val firstPid = first.server().getCurrentProcessId()
        val probe = with(driver) { utility(ProjectLifecycle::class) }
        val opening = probe.open(root.toString())
        awaitUi("Second native project opens", 60.seconds) { opening.isDone() }
        val secondary = requireNotNull(opening.get())
        try {
            with(driver) { withContext(OnDispatcher.EDT) { utility(CompilerSettingsPage::class).useBuildModel(secondary) } }
            val page = with(driver) { withContext(OnDispatcher.EDT) { utility(CompilerImportPage::class).open(secondary) } }
            check(page.accepted() != "null")
            with(driver) { withContext(OnDispatcher.EDT) { page.click("Refresh Gradle model") } }
            awaitUi("Secondary project import is pending", 45.seconds) { Files.exists(control.resolve("$operation.started")) }
            with(driver) { withContext(OnDispatcher.EDT) { page.closePage() } }
            val closing = probe.close(secondary)
            awaitUi("Project closes while its compiler import is pending", 30.seconds) { closing.isDone() }
            check(closing.get())
            Files.writeString(control.resolve("$operation.release"), "late completion\n")
            val producer = Files.readString(control.resolve("$operation.pid")).toLong()
            awaitUi("Closed project producer retires", 30.seconds) {
                Files.exists(control.resolve("$operation.finished")) || !ProcessHandle.of(producer).map { it.isAlive }.orElse(false)
            }
            check(first.server().getCurrentProcessId() == firstPid)
            first.query("xtc/healthCheck", emptyMap<String, Any>())
            write("Survivor.x", "module Survivor {}\n")
            clean(open("Survivor.x"))
        } finally {
            Files.writeString(control.resolve("$operation.release"), "cleanup\n")
            runCatching { probe.close(secondary) }
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

    fun sync()

    fun unlink()

    fun refreshReports()

    fun closePage()
}
