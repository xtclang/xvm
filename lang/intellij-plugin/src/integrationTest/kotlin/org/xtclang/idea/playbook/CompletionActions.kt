package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.LookupElementPresentation
import kotlin.time.Duration.Companion.seconds

fun Driver.lookup(
    editor: JEditorUiComponent,
    at: Int,
    matches: (List<CompletionItem>) -> Boolean,
) {
    dismissPopups()
    focusEditor(editor)
    withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(at) }
    editor.scrollToCaretNow()
    val inspection =
        PopupInspection(this, editor) {
            invokeAction("CodeCompletion", component = editor.component)
        }
    invokeAction("CodeCompletion", component = editor.component)
    val manager = utility(EditorLookupManager::class).getInstance(singleProject())
    awaitUi(
        message = "shared completion scope",
        timeout = 45.seconds,
        errorMessage = {
            "Current native items: " +
                manager.getActiveLookup()?.getItems()?.map { item ->
                    val presentation = new(LookupElementPresentation::class)
                    item.renderElement(presentation)
                    "${item.getLookupString()}: ${presentation.getTypeText()} ${presentation.getTailText()}"
                }
        },
    ) {
        inspection.recover()
        manager.getActiveLookup()?.let { !it.isCalculating() && matches(it.getItems()) } == true
    }
}

fun Driver.hasType(
    items: List<CompletionItem>,
    label: String,
    pattern: String,
): Boolean =
    items
        .firstOrNull { it.getLookupString() == label }
        ?.let { item ->
            val presentation = new(LookupElementPresentation::class)
            item.renderElement(presentation)
            Regex(pattern)
                .containsMatchIn(
                    presentation.getTypeText().orEmpty() +
                        " " +
                        presentation.getTailText().orEmpty()
                )
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
            (kind == null || hasCompletionKinds(items, expected, kind))
    }
    invokeAction("EditorEscape", component = editor.component)
    accept(editor, at, selected)
}

/** Inspect the actual native proposals, including metadata omitted from their rendered text. */
fun Driver.hasCompletionKinds(
    items: List<CompletionItem>,
    labels: List<String>,
    kind: Int,
): Boolean {
    val protocol = ClientProtocol(this)
    return labels.all { label ->
        items
            .singleOrNull { it.getLookupString() == label }
            ?.let {
                val proposal = cast(it, NativeCompletionElement::class).getObject()
                protocol.copy(proposal.getItem()).asJsonObject["kind"]?.asInt == kind
            } == true
    }
}

@Remote("com.intellij.codeInsight.lookup.LookupElement")
interface NativeCompletionElement {
    fun getObject(): NativeCompletionProposal
}

@Remote(
    "com.redhat.devtools.lsp4ij.client.features.LSPCompletionProposal",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface NativeCompletionProposal {
    fun getItem(): ClientValue
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
    editor.scrollToCaretNow()
    val inspection =
        PopupInspection(this, editor) {
            invokeAction("CodeCompletion", component = editor.component)
        }
    invokeAction("CodeCompletion", component = editor.component)
    val manager = utility(EditorLookupManager::class).getInstance(singleProject())
    awaitUi("$label completion or single-item insertion", 45.seconds) {
        // An insertion may already have completed before focus was lost. Never reopen it.
        if (editor.text == expected) return@awaitUi true
        inspection.recover()
        editor.text == expected ||
            manager.getActiveLookup()?.let {
                !it.isCalculating() && it.getItems().any { item -> item.getLookupString() == label }
            } == true
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
    awaitUi("exact replacement of the typed prefix", 15.seconds) { editor.text == expected }
}
