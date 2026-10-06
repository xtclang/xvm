package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.runtimeSettingsCases() {
    listOf("X269", "X270").forEach { id ->
        case(id) {
            val data = common["runtimeSettings"].asJsonObject
            val document = open(data.string("file"), data.string("source"))
            clean(document)

            fun status() = protocol.query("xtc/languageServiceStatus", emptyMap<String, String>()).asJsonObject
            with(driver) {
                val page = utility(ServerRuntimePage::class)
                val project = singleProject()
                val original = withContext(OnDispatcher.EDT) { page.content() }

                fun edit(
                    values: JsonObject,
                    action: String,
                ) = withContext(OnDispatcher.EDT) { page.edit(values.toString(), action) }

                fun restart(previous: Int) {
                    edit(JsonObject(), "restart")
                    awaitUi("runtime settings restart completed", 45.seconds) {
                        protocol.server().getCurrentProcessId()?.let { it != previous.toLong() } == true
                    }
                    clean(document)
                }
                try {
                    val before = status()
                    val values =
                        JsonObject().apply {
                            addProperty("vmOptions", data["vmOptions"].asJsonArray.joinToString("\n") { it.asString })
                            if (id == "X270") data["logs"].asJsonObject.entrySet().forEach { (key, value) -> add("xtc.logs.$key", value) }
                        }
                    edit(values, "cancel")
                    edit(values, "reset")
                    edit(values, "apply")
                    check(status().int("pid") == before.int("pid")) { "Apply must preserve the running server" }
                    restart(before.int("pid"))
                    val applied = status()
                    check(applied["jvmOptions"].asJsonArray.contains(data["vmOptions"].asJsonArray.single()))
                    if (id == "X269") {
                        edit(
                            JsonObject().apply {
                                addProperty("vmOptions", data["invalidOptions"].asJsonArray.joinToString("\n") { it.asString })
                            },
                            "invalid",
                        )
                    } else {
                        check(applied["logs"].asJsonObject["retention"] == data["logs"])
                        check(applied["logs"].asJsonObject["directory"] != before["logs"].asJsonObject["directory"])
                        val destination = Files.createTempFile("ecstasy-server-logs-", ".zip")
                        try {
                            page.export(project, destination.toString())
                            check(Files.size(destination) < 6 * 1024 * 1024)
                            ZipFile(destination.toFile()).use { zip ->
                                check(zip.entries().asSequence().any { it.name.endsWith("server.log") })
                                val manifest =
                                    zip.getInputStream(zip.getEntry("manifest.json")).reader().use {
                                        JsonParser.parseReader(it).asJsonObject
                                    }
                                check(manifest["service"].asJsonObject.int("pid") == applied.int("pid"))
                                check(manifest["service"].asJsonObject["logs"].asJsonObject["retention"] == data["logs"])
                            }
                        } finally {
                            Files.deleteIfExists(destination)
                        }
                        edit(JsonObject().apply { addProperty("xtc.logs.totalSizeMb", 0) }, "invalid")
                    }
                    check(status().int("pid") == applied.int("pid"))
                    clean(document)
                    check(document.text == data.string("source"))
                } finally {
                    val previous = status().int("pid")
                    edit(JsonParser.parseString(original).asJsonObject, "apply")
                    restart(previous)
                }
            }
        }
    }
}

@Remote("org.xtclang.idea.playbook.probe.ServerRuntimePage", plugin = "org.xtclang.playbook.probe")
interface ServerRuntimePage {
    fun content(): String

    fun edit(
        json: String,
        action: String,
    )

    fun export(
        project: Project,
        destination: String,
    )
}
