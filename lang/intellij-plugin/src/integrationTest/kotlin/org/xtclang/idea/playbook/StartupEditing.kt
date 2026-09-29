package org.xtclang.idea.playbook

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.ui
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** Real restarts and editor changes; the probe asserts that initialization overlaps every edit. */
fun Driver.startupEditing(
    fixtures: Map<String, String>,
    shared: SharedScenarios,
) {
    val short = "module Startup {\n    Int run() {\n        return 42;\n    }\n}\n"
    val invalid = short.replace("42", "unknownDuringStartup")
    val long =
        "module Startup {\n" +
            (1..30).joinToString("\n") { "    Int method$it() {\n        return $it;\n    }" } +
            "\n}\n"
    val receipts = buildList {
        ParityWorkspace(this@startupEditing, "STARTUP", fixtures, shared).use { workspace ->
            workspace.write("Startup.x", long)
            val path = workspace.directory.resolve("Startup.x").toString()
            val source =
                requireNotNull(
                    utility(ParityFiles::class).getInstance().refreshAndFindFileByPath(path)
                )
            val cold =
                withContext(OnDispatcher.EDT) {
                        utility(StartupEdits::class).openAndEdit(singleProject(), source, invalid)
                    }
                    .let { JsonParser.parseString(it).asJsonObject }
            val initial = workspace.open("Startup.x")
            check(initial.text == invalid)
            workspace.published(initial.file) { it.isNotEmpty() }
            workspace.errors(initial)
            awaitUi(
                "untouched startup notification fades without mouse or keyboard input",
                20.seconds,
            ) {
                ui.x { byVisibleText("Ecstasy Language Server Started") }.notPresent()
            }
            add(
                cold.apply {
                    addProperty("pid", workspace.protocol.server().getCurrentProcessId())
                    addProperty("version", workspace.version(initial))
                    addProperty("startupBalloonHidden", true)
                }
            )
            listOf("error", "repair", "shorten", "reopen").forEach { phase ->
                val document = workspace.open("Startup.x")
                workspace.replace(document, long)
                workspace.clean(document)
                val expected = if (phase in setOf("error", "reopen")) invalid else short
                val transaction =
                    withContext(OnDispatcher.EDT) {
                            utility(StartupEdits::class)
                                .restartAndEdit(
                                    singleProject(),
                                    document.editor.editor.getVirtualFile(),
                                    if (phase == "shorten") long else invalid,
                                    expected,
                                    phase == "reopen",
                                )
                        }
                        .let { JsonParser.parseString(it).asJsonObject }
                val current = workspace.open("Startup.x")
                check(current.text == expected)
                val pid = requireNotNull(workspace.protocol.server().getCurrentProcessId())
                check(pid != transaction["previousPid"].asLong) {
                    "Startup test reused the old process"
                }
                val version = workspace.version(current)
                workspace.published(current.file) { rows ->
                    if (expected == invalid) rows.isNotEmpty() else rows.isEmpty()
                }
                awaitUi("startup $phase diagnostics reach the editor", 45.seconds) {
                    current.editor.installedDiagnostics().let { diagnostics ->
                        if (expected == invalid)
                            diagnostics.any { "unknownDuringStartup" in it.description }
                        else diagnostics.isEmpty()
                    }
                }
                awaitUi("startup $phase folds match the shortened buffer", 45.seconds) {
                    folds(current.editor).let { ranges ->
                        (1..3) in ranges && ranges.all { it.last < 5 }
                    }
                }
                check(Files.readString(Path.of(URI(current.uri))) == long) {
                    "Startup edits were unexpectedly saved"
                }
                add(
                    transaction.deepCopy().apply {
                        addProperty("phase", phase)
                        addProperty("pid", pid)
                        addProperty("version", version)
                        addProperty(
                            "diagnostics",
                            if (expected == invalid) "unknownDuringStartup" else "clear",
                        )
                    }
                )
                workspace.replace(current, short)
                workspace.published(current.file) { it.isEmpty() }
                workspace.clean(current)
                awaitUi("startup $phase old process exits", 10.seconds) {
                    ProcessHandle.of(transaction["previousPid"].asLong)
                        .map { !it.isAlive }
                        .orElse(true)
                }
            }
        }
    }
    val report = Path.of(singleProject().getBasePath()).parent.resolve("startup-editing.json")
    Files.writeString(report, GsonBuilder().setPrettyPrinting().create().toJson(receipts) + "\n")
    println("IntelliJ startup editing receipt: $report")
}

@Remote("org.xtclang.idea.playbook.probe.StartupEdits", plugin = "org.xtclang.playbook.probe")
interface StartupEdits {
    fun openAndEdit(
        project: Project,
        file: VirtualFile,
        text: String,
    ): String

    fun restartAndEdit(
        project: Project,
        file: VirtualFile,
        intermediate: String,
        finalText: String,
        reopen: Boolean,
    ): String
}
