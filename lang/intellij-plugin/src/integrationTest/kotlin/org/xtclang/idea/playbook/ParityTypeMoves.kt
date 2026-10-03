package org.xtclang.idea.playbook

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.intellij.driver.client.Remote
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
    listOf("X161", "X162", "X163", "X169", "X170", "X171", "X172").forEach { id ->
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
            val moves =
                if (data.has("moves")) {
                    data["moves"].rows().map { it.string("from") to it.string("to") }
                } else {
                    listOf(data.string("from") to data.string("to"))
                }
            if (data["refused"]?.asBoolean == true) {
                val protocol = ClientProtocol(driver)
                val beforeGraph = driver.utility(CompilerSettingsPage::class).content(driver.singleProject())
                val proposal =
                    protocol.query(
                        "xtc/renameFiles",
                        mapOf(
                            "files" to
                                moves.map { (from, to) ->
                                    mapOf(
                                        "oldUri" to directory.resolve(from).toUri().toString(),
                                        "newUri" to directory.resolve(to).toUri().toString(),
                                    )
                                },
                        ),
                    )
                check(proposal.isJsonNull) { "A colliding member must reject the entire proposal" }
                files.forEach { check(Files.readString(directory.resolve(it.string("file"))) == it.string("source")) }
                moves.forEach { (_, to) -> check(!Files.exists(directory.resolve(to))) }
                check(driver.utility(CompilerSettingsPage::class).content(driver.singleProject()) == beforeGraph)
                clean(document)
                return@case
            }
            val target = directory.resolve(data.string("destination"))
            refresh(target)
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(FileTreeOperations::class).move(
                        singleProject(),
                        moves.map { directory.resolve(it.first).toString() },
                        target.toString(),
                    )
                }
                val dialog = ui.dialog(title = "Move Ecstasy Sources")
                awaitUi("source Move dialog", 45.seconds) { dialog.present() }
                if (data.has("newName")) {
                    val field = cast(dialog.x { byAccessibleName("New name") }.component, MoveNameField::class)
                    withContext(OnDispatcher.EDT) { field.setText(data.string("newName")) }
                }
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
                    val openPaths =
                        withContext(OnDispatcher.EDT) {
                            service<FileEditorManager>(singleProject()).getAllEditors().map { it.getFile().getPath() }.toSet()
                        }
                    files.forEach { file ->
                        val path = directory.resolve(file.string(if (moved) "destination" else "file"))
                        if (path.toString() !in openPaths) {
                            check(Files.readString(path) == file.string(if (moved) "expected" else "source")) {
                                "Closed source/resource $path differs on disk after Move/Undo/Redo"
                            }
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
                if (data.has("afterModules")) {
                    // Directory Move/Undo/Redo must retire the old synchronizer as well as move
                    // files/settings. A later unsaved edit must reach the current URI exactly once.
                    val moved = open(data.string("afterRoot"))
                    val source = moved.text
                    replace(moved, source.replaceFirst("{", "{\n    Int relocationProbe = missingRelocationValue;"))
                    errors(moved)
                    replace(moved, source)
                    clean(moved)
                }
            }
        }
    }
}

@Remote("javax.swing.JTextField")
private interface MoveNameField {
    fun setText(text: String)
}
