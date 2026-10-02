package org.xtclang.idea.playbook

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.invokeGlobalBackendAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.ui
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.typeMoveCases() {
    listOf("X161", "X162", "X163").forEach { id ->
        case(id) { data ->
            val files = data["files"].rows()
            files.forEach { write(it.string("file"), it.string("source")) }
            configure(data["modules"])
            val document = open(data.string("root"))
            clean(document)
            if (data.has("consumer")) {
                with(driver) {
                    withContext(OnDispatcher.EDT) {
                        check(
                            service<FileEditorManager>(singleProject()).getAllEditors().none {
                                it.getFile().getPath() == directory.resolve(data.string("consumer")).toString()
                            },
                        )
                    }
                }
            }
            val target = directory.resolve(data.string("destination"))
            refresh(target)
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(FileTreeOperations::class).move(
                        singleProject(),
                        listOf(directory.resolve(data.string("from")).toString()),
                        target.toString(),
                    )
                }
                val dialog = ui.dialog(title = "Move Ecstasy Sources")
                awaitUi("source Move dialog", 45.seconds) { dialog.present() }
                withContext(OnDispatcher.EDT) { cast(dialog.button("Refactor").component, NativeButton::class).doClick() }

                fun verify(moved: Boolean) {
                    awaitUi("source Move ${if (moved) "applied" else "undone"}", 45.seconds) {
                        files.all { file ->
                            val path = directory.resolve(file.string(if (moved) "destination" else "file"))
                            val expected = file.string(if (moved) "expected" else "source")
                            Files.exists(path) &&
                                (
                                    if (path.toString().endsWith(".x")) {
                                        Document(path.fileName.toString(), requireNotNull(refresh(path))).text
                                    } else {
                                        Files.readString(path)
                                    }
                                ) == expected &&
                                (
                                    file.string("file") == file.string("destination") ||
                                        !Files.exists(directory.resolve(file.string(if (moved) "file" else "destination")))
                                )
                        }
                    }
                    if (data.has("afterModules")) {
                        val content = requireNotNull(utility(CompilerSettingsPage::class).content(singleProject()))
                        val modules =
                            JsonParser
                                .parseString(content)
                                .asJsonObject["xtc"]
                                .asJsonObject["compiler"]
                                .asJsonObject["sourceModules"]

                        fun graph(
                            value: JsonElement,
                            relative: Boolean,
                        ) = value.rows().associate { module ->
                            fun path(value: String) =
                                if (relative) directory.resolve(value).normalize() else Path.of(URI(value)).normalize()
                            module.string("name") to
                                Triple(
                                    path(module.string("uri")),
                                    module.strings("dependencies").toSet(),
                                    module["resourceRoots"]?.takeUnless { it.isJsonNull }?.asJsonArray?.map { path(it.asString) },
                                )
                        }
                        val expected = graph(data[if (moved) "afterModules" else "modules"], true)
                        check(graph(modules, false) == expected) {
                            "Persisted graph differs after Move/Undo/Redo"
                        }
                        clean(open(data.string(if (moved) "afterRoot" else "root")))
                        clean(open(data.string("consumer")))
                    } else {
                        clean(document)
                    }
                }
                verify(true)
                listOf("\$Undo" to false, "\$Redo" to true).forEach { (action, moved) ->
                    awaitUi("project $action is available") {
                        withContext(OnDispatcher.EDT) {
                            utility(FileTreeOperations::class).globalHistoryAvailable(singleProject(), moved)
                        }
                    }
                    invokeGlobalBackendAction(action, project = singleProject(), now = false)
                    awaitUi("$action restores the source path", 45.seconds) {
                        val confirm = ui.dialog(title = if (moved) "Redo" else "Undo")
                        if (confirm.present()) {
                            withContext(OnDispatcher.EDT) {
                                cast(confirm.button(if (moved) "Redo" else "Undo").component, NativeButton::class).doClick()
                            }
                        }
                        Files.exists(directory.resolve(data.string("to"))) == moved
                    }
                    verify(moved)
                }
            }
        }
    }
}
