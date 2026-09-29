package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** Shared generation data, native intention selection and the editor's own Undo/Redo. */
fun Driver.memberActions(
    data: SharedScenarios.Scenario,
    editor: JEditorUiComponent,
    clean: (JEditorUiComponent) -> Unit,
) {
    data.rows("variants").forEach { variant ->
        val original = variant["source"].asString
        val expected = variant["expected"].asString
        val title = variant["title"].asString
        editor.text = original
        clean(editor)
        val at = editor.text.indexOf(data.text("anchor"))
        if (title.isEmpty()) {
            val position = mapOf("line" to 0, "character" to at)
            val actions = ClientProtocol(this).query(
                "textDocument/codeAction",
                mapOf(
                    "textDocument" to mapOf("uri" to Path.of(editor.editor.getVirtualFile().getPath()).toUri().toString()),
                    "range" to mapOf("start" to position, "end" to position),
                    "context" to mapOf("diagnostics" to emptyList<Any>()),
                ),
            )
            check(actions.asJsonArray.none { it.asJsonObject["title"].asString.startsWith("Implement ") })
            check(editor.text == original)
        } else {
            quickFix(editor, at, title)
            awaitUi("generated member matches shared source", 45.seconds) { editor.text == expected }
            clean(editor)
            listOf("\$Undo" to original, "\$Redo" to expected, "\$Undo" to original).forEach { (action, text) ->
                focusEditor(editor)
                invokeAction(action, now = false, component = editor.component)
                awaitUi("$action restores the shared member source", 45.seconds) { editor.text == text }
                clean(editor)
            }
        }
    }
}
