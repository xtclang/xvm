package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.ui
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** Shared rename fixtures, real native Rename/Undo, and consumers kept closed before the edit. */
fun Driver.renameFamily(
    id: String,
    data: SharedScenarios.Scenario,
    open: (String) -> JEditorUiComponent,
    clean: (JEditorUiComponent) -> Unit,
) {
    val root = Path.of(singleProject().getBasePath()).resolve(id)
    val files = data.rows("files")
    fun graphContains(name: String): Boolean =
        service<LspSettings>().getLanguageServerSettings("xtcLanguageServer")?.getConfigurationContent()?.contains(name) == true

    fun contents(file: String): String {
        val path = root.resolve(file)
        val source = utility(ParityFiles::class).getInstance().refreshAndFindFileByPath(path.toString())
        return source?.let {
            withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
                service<ParityDocuments>().getDocument(it)?.getText()
            }
        } ?: Files.readString(path)
    }

    val editor = open(data.text("file"))
    clean(editor)
    val request = files.single { "$id/${it["file"].asString}" == data.text("file") }
    withContext(OnDispatcher.EDT) {
        val opened = service<FileEditorManager>(singleProject()).getAllEditors().map { it.getFile().getPath() }.toSet()
        files.filter { it !== request }.forEach { check(root.resolve(it["file"].asString).toString() !in opened) }
    }
    rename(editor, editor.text.indexOf(data.text("anchor")), data.text("replacement"))
    awaitUi("$id source and resource edits applied", 45.seconds) {
        files.all { file ->
            val destination = file["destination"].asString
            Files.exists(root.resolve(destination)) && contents(destination) == file["expected"].asString &&
                (destination == file["file"].asString || !Files.exists(root.resolve(file["file"].asString)))
        }
    }
    val renamed = open("$id/${request["destination"].asString}")
    clean(renamed)
    if (data.values.has("sourceModules")) {
        check(graphContains("Renamed.example.org") && !graphContains("Library.example.org"))
    }
    focusEditor(renamed)
    invokeAction("Undo", now = false, component = renamed.component)
    awaitUi("$id one Undo restores every source and path", 45.seconds) {
        val confirmation = ui.dialog(title = "Undo")
        if (confirmation.present()) {
            withContext(OnDispatcher.EDT) { cast(confirmation.button("Undo").component, NativeButton::class).doClick() }
        }
        files.all { file ->
            val original = file["file"].asString
            Files.exists(root.resolve(original)) && contents(original) == file["source"].asString &&
                (original == file["destination"].asString || !Files.exists(root.resolve(file["destination"].asString)))
        }
    }
    clean(open(data.text("file")))
    if (data.values.has("sourceModules")) {
        check(graphContains("Library.example.org") && !graphContains("Renamed.example.org"))
        val restored = open(data.text("file"))
        focusEditor(restored)
        invokeAction("Redo", now = false, component = restored.component)
        awaitUi("$id Redo restores graph, sources and paths", 45.seconds) {
            graphContains("Renamed.example.org") && !graphContains("Library.example.org") && files.all { file ->
                Files.exists(root.resolve(file["destination"].asString)) && contents(file["destination"].asString) == file["expected"].asString
            }
        }
        val redone = open("$id/${request["destination"].asString}")
        clean(redone)
        focusEditor(redone)
        invokeAction("Undo", now = false, component = redone.component)
        awaitUi("$id second Undo restores the original graph and sources", 45.seconds) {
            val confirmation = ui.dialog(title = "Undo")
            if (confirmation.present()) {
                withContext(OnDispatcher.EDT) { cast(confirmation.button("Undo").component, NativeButton::class).doClick() }
            }
            graphContains("Library.example.org") && !graphContains("Renamed.example.org") && files.all { file ->
                Files.exists(root.resolve(file["file"].asString)) && contents(file["file"].asString) == file["source"].asString
            }
        }
        clean(open(data.text("file")))
    }
}
