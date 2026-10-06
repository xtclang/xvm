package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import java.nio.file.Files
import java.nio.file.Path

/** Two complete IDE processes use the same disposable project and profile. */
internal fun Driver.settingsPersistence(
    shared: SharedScenarios,
    phase: Int,
) {
    val project = singleProject()
    val root = Path.of(project.getBasePath())
    val data = shared.common.runtimeSettings
    val file = root.resolve(data["file"].asString)
    val runtime = utility(ServerRuntimePage::class)
    val service = utility(LanguageServicePage::class)
    val inputs = utility(SettingsPersistencePage::class)
    val lifecycle = utility(ProjectLifecycle::class)
    val expected = root.parent.resolve("persisted-settings.json")
    if (phase == 0) {
        Files.writeString(file, data["source"].asString)
        withContext(OnDispatcher.EDT) {
            runtime.edit(
                JsonObject()
                    .apply {
                        addProperty("vmOptions", data["vmOptions"].asJsonArray.joinToString("\n") { it.asString })
                        data["logs"].asJsonObject.entrySet().forEach { (key, value) -> add("xtc.logs.$key", value) }
                    }.toString(),
                "apply",
            )
            service.transport(project, "incremental")
            service.inlayHints(project, false)
            inputs.sources(project, "RuntimeSettings", file.toUri().toString())
            inputs.rejectUntrustedImport(project)
            lifecycle.show(project, file.toString())
            Files.writeString(
                expected,
                JsonObject()
                    .apply {
                        addProperty("runtime", runtime.content())
                        addProperty("project", service.content(project))
                    }.toString(),
            )
        }
    } else {
        val saved = JsonParser.parseString(Files.readString(expected)).asJsonObject
        withContext(OnDispatcher.EDT) {
            check(runtime.content() == saved["runtime"].asString)
            check(service.content(project) == saved["project"].asString)
            lifecycle.show(project, file.toString())
        }
    }
    val status = ClientProtocol(this).query("xtc/languageServiceStatus", emptyMap<String, String>()).asJsonObject
    check(status["textSynchronization"].asString == "incremental")
    check(status["jvmOptions"].asJsonArray.contains(data["vmOptions"].asJsonArray.single()))
    check(status["logs"].asJsonObject["retention"] == data["logs"])
    val pidFile = root.parent.resolve("persisted-settings-pid.txt")
    if (phase == 0) {
        Files.writeString(pidFile, status["pid"].asString)
    } else {
        check(Files.readString(pidFile) != status["pid"].asString)
    }
}

@Remote("org.xtclang.idea.playbook.probe.SettingsPersistencePage", plugin = "org.xtclang.playbook.probe")
internal interface SettingsPersistencePage {
    fun sources(
        project: Project,
        module: String,
        uri: String,
    )

    fun rejectUntrustedImport(project: Project)
}
