package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.LookupElementPresentation
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

fun Driver.lookup(
    editor: JEditorUiComponent,
    at: Int,
    matches: (List<CompletionItem>) -> Boolean,
) {
    focusEditor(editor)
    withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(at) }
    invokeAction("CodeCompletion", component = editor.component)
    val manager = utility(EditorLookupManager::class).getInstance(singleProject())
    waitFor("shared completion scope", 45.seconds) {
        requirePopupFocus()
        manager.getActiveLookup()?.let { !it.isCalculating() && matches(it.getItems()) } == true
    }
}

fun Driver.hasType(
    items: List<CompletionItem>,
    label: String,
    pattern: String,
): Boolean =
    items.firstOrNull { it.getLookupString() == label }?.let { item ->
        val presentation = new(LookupElementPresentation::class)
        item.renderElement(presentation)
        Regex(pattern).containsMatchIn(presentation.getTypeText().orEmpty() + " " + presentation.getTailText().orEmpty())
    } == true

fun Driver.acceptCandidates(
    editor: JEditorUiComponent,
    at: Int,
    expected: List<String>,
    selected: String,
    kind: Int? = null,
) {
    lookup(editor, at) { items ->
        items.map { it.getLookupString() }.sorted() == expected.sorted() &&
            (kind == null || hasCompletionKinds(editor, expected, kind))
    }
    invokeAction("EditorEscape", component = editor.component)
    accept(editor, at, selected)
}

/** Read metadata from the native completion request, including fields that lookup text omits. */
fun Driver.hasCompletionKinds(
    editor: JEditorUiComponent,
    labels: List<String>,
    kind: Int,
): Boolean {
    val future = semanticSupport(editor).getCompletionSupport().getValidLSPFuture() ?: return false
    if (!future.isDone() || future.isCompletedExceptionally()) return false
    val protocol = ClientProtocol(this)
    val items =
        future.get().flatMap {
            val result = protocol.copy(it.completion())
            if (result.isJsonObject) result.asJsonObject["items"].rows() else result.rows()
        }
    return labels.all { label -> items.singleOrNull { it.string("label") == label }?.get("kind")?.asInt == kind }
}

fun Driver.accept(
    editor: JEditorUiComponent,
    at: Int,
    label: String,
    expectedText: String? = null,
) {
    val before = editor.text
    check(at in 0..before.length)
    val prefix = before.take(at).takeLastWhile { it.isLetterOrDigit() || it == '_' }
    val expected = expectedText ?: before.replaceRange(at - prefix.length, at, label)
    focusEditor(editor)
    withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(at) }
    invokeAction("CodeCompletion", component = editor.component)
    val manager = utility(EditorLookupManager::class).getInstance(singleProject())
    waitFor("$label completion or single-item insertion", 45.seconds) {
        requirePopupFocus()
        editor.text == expected ||
            manager.getActiveLookup()?.let { !it.isCalculating() && it.getItems().any { item -> item.getLookupString() == label } } ==
            true
    }
    if (editor.text != expected) {
        withContext(OnDispatcher.EDT) {
            val lookup =
                requireNotNull(manager.getActiveLookup()) {
                    "Native completion closed before acceptance; switching applications or editors cancels the popup"
                }
            lookup.setCurrentItem(lookup.getItems().first { it.getLookupString() == label })
        }
        invokeAction("EditorChooseLookupItem", component = editor.component)
    }
    waitFor("exact replacement of the typed prefix", 15.seconds) { editor.text == expected }
}
