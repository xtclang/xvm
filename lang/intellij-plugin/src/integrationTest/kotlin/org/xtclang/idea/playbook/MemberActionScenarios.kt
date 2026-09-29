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
    diagnostics: (JEditorUiComponent, Boolean) -> Unit,
) {
    data.rows("variants").forEach { variant ->
        val original = variant["source"].asString
        val expected = variant["expected"].asString
        val title = variant["title"].asString
        val broken = variant["initiallyValid"]?.asBoolean == false
        editor.text = original
        diagnostics(editor, broken)
        val at = editor.text.indexOf(data.text("anchor"))
        val position = mapOf("line" to 0, "character" to at)

        fun actions() =
            ClientProtocol(this)
                .query(
                    "textDocument/codeAction",
                    mapOf(
                        "textDocument" to mapOf("uri" to Path.of(editor.editor.getVirtualFile().getPath()).toUri().toString()),
                        "range" to mapOf("start" to position, "end" to position),
                        "context" to mapOf("diagnostics" to emptyList<Any>()),
                    ),
                ).asJsonArray
        if (title.isEmpty()) {
            val actions = actions()
            check(
                actions.none {
                    val label = it.asJsonObject["title"].asString
                    label.startsWith("Implement ") || label.startsWith("Override ")
                },
            )
            check(editor.text == original)
        } else {
            // Empty diagnostics may still belong to the previous source version. Wait for this
            // exact member query before opening the asynchronously populated native menu.
            awaitUi("current member action $title", 45.seconds) {
                try {
                    actions().any { it.asJsonObject["title"].asString == title }
                } catch (failure: ClientRequestFailure) {
                    if (failure.code != -32801) throw failure
                    false
                }
            }
            quickFix(editor, at, title)
            awaitUi("generated member matches shared source", 45.seconds) { editor.text == expected }
            diagnostics(editor, false)
            listOf("\$Undo" to original, "\$Redo" to expected, "\$Undo" to original).forEach { (action, text) ->
                focusEditor(editor)
                invokeAction(action, now = false, component = editor.component)
                awaitUi("$action restores the shared member source", 45.seconds) { editor.text == text }
                diagnostics(editor, broken && text == original)
            }
        }
    }
}
