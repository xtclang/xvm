package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.invokeAction
import org.eclipse.lsp4j.InlineCompletionTriggerKind
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.inlineCompletionCases() {
    listOf("X255", "X256", "X257", "X258").forEach { id ->
        case(id) { data ->
            val marked = data.string("source")
            val offset = marked.indexOf('§')
            val source = marked.replace("§", "")
            val document = open(data.string("file"), source)
            val mode = data.string("mode")
            fun queryInline(kind: InlineCompletionTriggerKind, selected: Map<String, Any>? = null) =
                query("textDocument/inlineCompletion", document, offset, mapOf("context" to buildMap {
                    put("triggerKind", kind.value)
                    selected?.let { put("selectedCompletionInfo", it) }
                })).asJsonObject["items"].rows()
            check(protocol.capabilities().asJsonObject["inlineCompletionProvider"].asBoolean)
            if (mode == "selection") {
                check(queryInline(InlineCompletionTriggerKind.Automatic).isEmpty())
                check(queryInline(InlineCompletionTriggerKind.Invoked).map { it.string("insertText") }.sorted() == listOf("another", "answer"))
                val range = mapOf("start" to position(source, offset - 2), "end" to position(source, offset))
                check(queryInline(InlineCompletionTriggerKind.Invoked, mapOf("range" to range, "text" to "ans")).map { it.string("insertText") } == listOf("answer"))
                check(queryInline(InlineCompletionTriggerKind.Invoked, mapOf("range" to range, "text" to "answer")).isEmpty())
                // TODO LSP4IJ: UP26 always sends Automatic, including DirectCall, without selectedCompletionInfo.
                // Keep this protocol coverage explicit until native invocation/selection is forwarded.
                return@case
            }
            check(queryInline(InlineCompletionTriggerKind.Automatic).map { it.string("insertText") } == listOf(data.string("expected")))
            with(driver) {
                val editor = document.editor
                focusEditor(editor)
                withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(offset) }
                fun visible() = withContext(OnDispatcher.EDT) { utility(InlineCompletionProbe::class).visibleText(editor.editor) }
                invokeAction("CallInlineCompletionAction", component = editor.component)
                awaitUi("compiler ghost text is visible", 30.seconds) { visible() == "wer" }
                if (mode == "typing") {
                    invokeAction("EditorEscape", component = editor.component)
                    awaitUi("ghost text is dismissed", 10.seconds) { visible().isEmpty() }
                    check(document.text == source)
                    // Use the native typing route so the provider sees the real document-change event.
                    withContext(OnDispatcher.EDT) { utility(InlineCompletionProbe::class).type(editor.editor, "w") }
                    invokeAction("CallInlineCompletionAction", component = editor.component)
                    awaitUi("continued typing has current ghost text", 30.seconds) { visible() == "er" }
                }
                invokeAction("InsertInlineCompletionAction", component = editor.component)
                val expected = source.substring(0, offset - 3) + data.string("expected") + source.substring(offset)
                awaitUi("native inline acceptance preserves source suffix", 15.seconds) { document.text == expected }
                if (mode == "accept") {
                    clean(document)
                    invokeAction("\$Undo", component = editor.component)
                    awaitUi("one Undo restores the prefix", 15.seconds) { document.text == source }
                } else if (mode == "incomplete") {
                    replace(document, expected.replace("consume(answer\n", "consume(answer);\n"))
                    clean(document)
                }
            }
        }
    }
}

@Remote("org.xtclang.idea.playbook.probe.InlineCompletionUi", plugin = "org.xtclang.playbook.probe")
internal interface InlineCompletionProbe {
    fun type(editor: Editor, text: String)

    fun visibleText(editor: Editor): String
}
