package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import kotlin.time.Duration.Companion.seconds

/** Exercise actual focus switches, native popup reopening, and the document-stamp replay guard. */
fun Driver.focusRecovery(
    fixtures: Map<String, String>,
    shared: SharedScenarios,
) {
    ParityWorkspace(this, "FOCUS", fixtures, shared).use { workspace ->
        workspace.write(
            "Focus.x",
            """
            module Focus {
                private Int run(String word) { return word.si; }
                void pair(Int number, String text) {}
                void inspect() { pair(1, "x"); }
            }
            """.trimIndent() + "\n",
        )
        val document = workspace.open("Focus.x")
        val editor = document.editor
        val at = document.at("word.si") + "word.si".length

        fun completion() = lookup(editor, at) { items -> items.any { it.getLookupString() == "size" } }
        val inspection = PopupInspection(this, editor, ::completion)
        completion()
        interruptFocus(editor) { inspection.recover() }
        check(editor.text.contains("word.si;")) { "Inspecting completion applied an edit" }
        accept(editor, at, "size")
        workspace.clean(document)
        val accepted = editor.text
        interruptFocus(editor) {
            val failure = runCatching { inspection.recover() }.exceptionOrNull()
            check(failure is IllegalStateException && failure.message.orEmpty().startsWith("Source changed")) {
                "A completed insertion was allowed into the replay path: $failure"
            }
        }
        check(editor.text == accepted)

        val call = document.at("pair(1") + "pair(".length

        fun parameters() =
            signature(editor, call, keepOpen = true) { values ->
                values.singleOrNull()?.parameters?.size == 2
            }
        val parameterInspection = PopupInspection(this, editor, ::parameters)
        parameters()
        interruptFocus(editor) { parameterInspection.recover() }
        dismissPopups()

        // Capture the same guard before a real rename, then prove it rejects any replay afterward.
        val renameInspection = PopupInspection(this, editor) { error("A completed rename must never be replayed") }
        rename(editor, document.at("word"), "text")
        val renamed = accepted.replace("word", "text")
        awaitUi("rename applied exactly once", 45.seconds) { editor.text == renamed }
        interruptFocus(editor) {
            val failure = runCatching { renameInspection.recover() }.exceptionOrNull()
            check(failure is IllegalStateException && failure.message.orEmpty().startsWith("Source changed")) {
                "A completed rename was allowed into the replay path: $failure"
            }
        }
        check(editor.text == renamed)
        workspace.clean(document)
    }
}

private fun Driver.interruptFocus(
    editor: JEditorUiComponent,
    action: () -> Unit,
) {
    val nativeEditor = editor.editor
    val other = withContext(OnDispatcher.EDT) { utility(NativeEditorUi::class).interruptFocus() }
    try {
        awaitUi("unowned test window interrupts IDE focus", 10.seconds) {
            withContext(OnDispatcher.EDT) { other.isFocused() && !utility(NativeEditorUi::class).hasFocus(nativeEditor) }
        }
        action()
        check(withContext(OnDispatcher.EDT) { utility(NativeEditorUi::class).hasFocus(nativeEditor) })
    } finally {
        withContext(OnDispatcher.EDT) { other.dispose() }
    }
}
