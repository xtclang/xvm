package org.xtclang.idea.playbook

import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.invokeGlobalBackendAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.ui
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.typeMoveCases() {
    case("X161") { data ->
        val files = data["files"].rows()
        files.forEach { write(it.string("file"), it.string("source")) }
        configure(
            data["modules"].rows().map {
                SharedScenarios.SourceModule(it.string("name"), uri(it.string("uri")), it.strings("dependencies"))
            },
        )
        val document = open(data.string("root"))
        clean(document)
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
            awaitUi("cross-package Move dialog", 45.seconds) { dialog.present() }
            withContext(OnDispatcher.EDT) { cast(dialog.button("Refactor").component, NativeButton::class).doClick() }

            fun verify(moved: Boolean) {
                awaitUi("cross-package Move ${if (moved) "applied" else "undone"}", 45.seconds) {
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
                clean(document)
            }
            verify(true)
            listOf("\$Undo" to false, "\$Redo" to true).forEach { (action, moved) ->
                awaitUi("project $action is available") {
                    withContext(OnDispatcher.EDT) { utility(FileTreeOperations::class).globalHistoryAvailable(singleProject(), moved) }
                }
                invokeGlobalBackendAction(action, project = singleProject(), now = false)
                awaitUi("$action restores the type path", 45.seconds) {
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
