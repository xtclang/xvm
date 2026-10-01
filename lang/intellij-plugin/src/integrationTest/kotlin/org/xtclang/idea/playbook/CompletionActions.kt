package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.LookupElementPresentation
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE
import kotlin.time.Duration.Companion.seconds

fun Driver.syntaxCompletions(
    data: SharedScenarios.Scenario,
    editor: JEditorUiComponent,
    diagnostics: () -> Unit,
) {
    data.rows("variants").forEach { variant ->
        val source = variant["source"].asString
        val expected = variant["expected"].asString
        val anchor = variant["anchor"].asString
        editor.text = source
        val at = source.indexOf(anchor) + (variant["prefix"]?.asString ?: anchor).length
        accept(editor, at, variant["label"].asString, expected)
        variant["selected"]?.asString?.let { placeholder ->
            withContext(OnDispatcher.EDT) {
                val selection = cast(editor.editor.getSelectionModel(), SelectionOffsets::class)
                check(editor.text.substring(selection.getSelectionStart(), selection.getSelectionEnd()) == placeholder) {
                    "Native snippet must select its first placeholder: $placeholder"
                }
            }
            // Tab is routed to this action while a live template is active. EditorTab itself
            // bypasses that routing and replaces the selected placeholder with whitespace.
            invokeAction(ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE, component = editor.component)
            withContext(OnDispatcher.EDT) {
                val selection = cast(editor.editor.getSelectionModel(), SelectionOffsets::class)
                check(selection.getSelectionStart() == selection.getSelectionEnd())
                check(editor.text == expected) { "Template navigation changed source: ${editor.text}" }
                val body = expected.lines().first { it.isNotEmpty() && it.isBlank() }
                check(editor.editor.getCaretModel().getOffset() == expected.indexOf("\n$body\n") + 1 + body.length)
            }
        }
        diagnostics()
        focusEditor(editor)
        invokeAction("\$Undo", now = false, component = editor.component)
        if (variant["selected"] != null) {
            // IDEA can undo caret navigation separately (editor.undo.transparent.caret.movement).
            // Accept only that exact, text-preserving step before undoing the insertion once.
            awaitUi("undo insertion or restore its selected placeholder", 15.seconds) {
                editor.text == source ||
                    withContext(OnDispatcher.EDT) {
                        val selection = cast(editor.editor.getSelectionModel(), SelectionOffsets::class)
                        editor.text == expected &&
                            editor.text.substring(selection.getSelectionStart(), selection.getSelectionEnd()) ==
                            variant["selected"].asString
                    }
            }
            if (editor.text != source) invokeAction("\$Undo", now = false, component = editor.component)
        }
        awaitUi("one undo restores the completion prefix", 15.seconds) { editor.text == source }
    }
}

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
                        presentation.getTailText().orEmpty(),
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

/**
 * Read metadata from the actual native proposal, without issuing a competing completion request.
 */
fun Driver.hasCompletionMetadata(
    items: List<CompletionItem>,
    label: String,
    expected: JsonObject,
): Boolean {
    val item = items.singleOrNull { it.getLookupString() == label } ?: return false
    val proposal = cast(item, NativeCompletionElement::class).getObject()
    val value = ClientProtocol(this).copy(proposal.getItem()).asJsonObject
    return value["detail"]?.asString?.contains(expected["detailContains"].asString) == true &&
        (expected["kind"] == null || value["kind"]?.asInt == expected["kind"].asInt) &&
        (expected["sorted"]?.asBoolean != true || value["sortText"]?.asString?.isNotBlank() == true)
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
