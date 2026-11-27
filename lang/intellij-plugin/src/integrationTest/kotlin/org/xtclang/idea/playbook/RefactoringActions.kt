package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.ui
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Exercise the installed Rename handler and its dialog, including ordered resource operations. */
fun Driver.rename(
    editor: JEditorUiComponent,
    at: Int,
    replacement: String,
) {
    focusEditor(editor)
    withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(at) }

    fun available() =
        withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
            utility(NativeEditorUi::class).renameAvailable(editor.editor)
        }
    awaitUi("installed Rename handler is available", HANG_LIMIT) { available() }
    invokeAction("RenameElement", now = false, component = editor.component)
    val dialog = ui.dialog(title = "Rename")
    awaitUi(
        "native rename dialog",
        HANG_LIMIT,
        errorMessage = {
            withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
                utility(NativeEditorUi::class).renameState(editor.editor)
            }
        },
    ) {
        if (dialog.present()) return@awaitUi true
        restorePopupFocus(editor.editor)
        false
    }
    // Exercise the focus observation with the actual modal dialog already open as well.
    restorePopupFocus(editor.editor)
    fillRenameDialog(replacement)
}

/** Submit the installed Rename dialog without synthesizing text edits in the driver. */
fun Driver.fillRenameDialog(replacement: String) {
    val dialog = ui.dialog(title = "Rename")
    awaitUi("native rename dialog opens", HANG_LIMIT) { dialog.present() }
    val input =
        dialog.x(JEditorUiComponent::class.java) {
            byType("com.intellij.openapi.editor.impl.EditorComponentImpl")
        }
    input.text = replacement
    val button = cast(dialog.button("Refactor").component, NativeButton::class)
    awaitUi("rename accepts the new name", HANG_LIMIT) { button.isEnabled() }
    withContext(OnDispatcher.EDT) { button.doClick() }
    awaitUi("rename dialog closes", HANG_LIMIT) { dialog.notPresent() }
}

/** Select the reference-preserving XTC scope when another plugin also offers plain file rename. */
fun Driver.chooseXtcFileRename() {
    val chooser = ui.dialog(title = "Select Refactoring")
    val rename = ui.dialog(title = "Rename")
    awaitUi("native file rename scope or name dialog", HANG_LIMIT) {
        chooser.present() || rename.present()
    }
    if (chooser.present()) {
        val option =
            cast(
                chooser
                    .x {
                        byType("javax.swing.JRadioButton") and
                            byAccessibleName("Rename ecstasy file and references")
                    }.component,
                NativeButton::class,
            )
        val accept = cast(chooser.button("OK").component, NativeButton::class)
        withContext(OnDispatcher.EDT) {
            option.doClick()
            accept.doClick()
        }
    }
}

/** Choose from the intention list, excluding its separate preview popup, without moving the pointer. */
fun Driver.choosePopup(
    editor: JEditorUiComponent,
    expected: List<String>,
    selected: String,
    reopen: () -> Unit,
) {
    // how many of the case's code-action requests the server had answered as the menu was last asked for
    var asked = 0
    val ask = {
        asked = Quiescence.trace?.answered(CODE_ACTION) ?: 0
        reopen()
    }
    val inspection = PopupInspection(this, editor, ask)
    val choices = utility(NativePopupChoices::class)

    // Each read, and the choice, is one step in the IDE: a list that closes in between reads as closed and
    // has nothing chosen from it, so the wait goes on and may reopen it while the source is unchanged.
    fun rows() = withContext(OnDispatcher.EDT) { choices.listRows() }

    // Whether the menu is not to show: the server has answered the code actions asked for it, none is left to
    // answer, the IDE has nothing in progress, and a round of its EDT, which shows the menu once it has the
    // answers, has not shown it. A cold request can fill LSP4IJ's cache without showing the menu.
    fun answeredWithoutMenu(): Boolean {
        val trace = Quiescence.trace ?: return false
        if (trace.answered(CODE_ACTION) <= asked || trace.answering(CODE_ACTION)) return false
        withContext(OnDispatcher.EDT) {}
        return rows() == null && Quiescence.activity().text.isEmpty()
    }
    // Capture the source/caret before dispatch: an intention can move the caret while it
    // discovers actions. Recovery must retain the requested location, not that later position.
    ask()
    awaitUi(
        "native popup contains $expected",
        HANG_LIMIT,
        errorMessage = { "Expected $expected; rendered rows: ${rows() ?: "no list"}" },
    ) {
        inspection.recover()
        val rows = rows()
        if (rows == null) {
            // The menu shows once the IDE has the server's code actions, however long the server takes. Reopen only an
            // unapplied inspection, never a completed generation or rename, and only once the menu is not to show, or,
            // when no answer comes, once nothing that could show it is in progress, as the tour's waits judge it: the
            // IDE's work, this case's unanswered requests and the server's compiles.
            Quiescence.stuck = null
            withinOrFail(HANG_LIMIT, "the intention menu opens") { rows() != null || answeredWithoutMenu() }
            if (rows() == null) {
                val why = Quiescence.stuck ?: "the server answered its code actions and the IDE showed no menu"
                println(
                    "[playbook] ${LocalTime.now().truncatedTo(ChronoUnit.SECONDS)} the intention menu did not open: $why; opening it again",
                )
                inspection.reopenUnapplied()
            }
            return@awaitUi false
        }
        expected.all { name -> rows.any { it.contains(name) } } && withContext(OnDispatcher.EDT) { choices.chooseListRow(selected) }
    }
    awaitUi("chosen popup closes", HANG_LIMIT) { rows() == null }
}

/** The LSP request whose answers fill the intention menu with the server's fixes. */
private const val CODE_ACTION = "textDocument/codeAction"

/** LSP4IJ renders multiple navigation targets through IntelliJ's Show Usages table. */
fun Driver.chooseTargets(
    editor: JEditorUiComponent,
    expected: List<String>,
    selected: String,
    reopen: () -> Unit,
) {
    val inspection = PopupInspection(this, editor, reopen)
    val choices = utility(NativePopupChoices::class)

    fun rows() = withContext(OnDispatcher.EDT) { choices.tableRows() }
    awaitUi(
        message = "native navigation chooser contains exactly $expected",
        errorMessage = { "Expected $expected; rendered rows: ${rows() ?: "no table"}" },
        timeout = HANG_LIMIT,
    ) {
        inspection.recover()
        val rows = rows() ?: return@awaitUi false
        rows.size == expected.size &&
            expected.all { name -> rows.any { it.contains(name) } } &&
            withContext(OnDispatcher.EDT) { choices.chooseTableRowContaining(selected, expected.size) }
    }
    awaitUi("selected declaration closes the chooser", HANG_LIMIT) { rows() == null }
}

fun Driver.quickFix(
    editor: JEditorUiComponent,
    at: Int,
    title: String,
    selectionEnd: Int = at,
) {
    focusEditor(editor)
    withContext(OnDispatcher.EDT) {
        editor.editor.getSelectionModel().removeSelection()
        editor.editor.getCaretModel().moveToOffset(at)
        if (selectionEnd != at) editor.editor.getSelectionModel().setSelection(at, selectionEnd)
    }
    // Dispatch through the UI queue so the platform handles ProcessCanceledException normally;
    // the popup wait may reopen an unapplied inspection, never replay a chosen edit.
    choosePopup(editor, listOf(title), title) {
        invokeAction("ShowIntentionActions", now = false, component = editor.component)
    }
}

/** Inspect the rendered native hierarchy, then the result of the native references action. */
fun Driver.partialGraphHierarchy(
    editor: JEditorUiComponent,
    at: Int,
    child: String,
    referenceAt: Int,
) {
    focusEditor(editor)
    withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(at) }
    invokeAction("TypeHierarchy", component = editor.component)
    val browser =
        ui.x {
            byType("com.redhat.devtools.lsp4ij.features.typeHierarchy.LSPTypeHierarchyBrowser")
        }
    awaitUi("native type hierarchy includes $child", HANG_LIMIT) {
        if (!browser.present()) return@awaitUi false
        // The native browser expands its root. Observe it without expandAll's nested ten-second
        // loading deadline cutting short this asynchronous hierarchy request.
        browser.tree().collectExpandedPaths().any { path -> path.path.last().contains(child) }
    }
    focusEditor(editor)
    withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(at) }
    awaitFindUsages(editor, "incomplete graph withholds references while retaining local navigation") { ranges ->
        // LSP4IJ combines definitions, implementations, type definitions and references here.
        // The declaration may remain, but the known extends-use must not masquerade as a
        // complete workspace reference result beside the broken module.
        ranges.any { at in it } && ranges.none { referenceAt in it }
    }
}

/** Invokes a native dialog button through the driver instead of relying on keyboard focus. */
@Remote("javax.swing.JButton")
interface NativeButton {
    fun isEnabled(): Boolean

    fun doClick()
}

/**
 * Answers OK to the question that Undo or Redo, as [title] names it, asks before it takes back or makes again a change
 * of the project, such as a rename or a move that changed several files, if the question shows: IntelliJ's dialog
 * titled Undo or Redo, with OK and Cancel.
 */
internal fun Driver.answerUndoQuestion(title: String) {
    val question = ui.dialog(title = title)
    if (question.present()) {
        withContext(OnDispatcher.EDT) { cast(question.button("OK").component, NativeButton::class).doClick() }
    }
}

/** Reads and chooses the rows of the showing list or table popup in one IDE step each. */
@Remote("org.xtclang.idea.playbook.probe.PopupChoices", plugin = "org.xtclang.playbook.probe")
internal interface NativePopupChoices {
    fun listRows(): List<String>?

    fun chooseListRow(text: String): Boolean

    fun tableRows(): List<String>?

    fun chooseTableRow(
        index: Int,
        rows: Int,
    ): Boolean

    fun chooseTableRowContaining(
        text: String,
        rows: Int,
    ): Boolean
}

/** A library use must be rejected by prepareRename before any refactoring dialog or edit. */
fun Driver.rejectRename(
    editor: JEditorUiComponent,
    at: Int,
    reason: String,
) {
    val original = editor.text
    focusEditor(editor)
    withContext(OnDispatcher.EDT) {
        service<EditorHints>().hideAllHints()
        editor.editor.getCaretModel().moveToOffset(at)
    }
    invokeAction("RenameElement", now = false, component = editor.component)
    // The guarded XTC handler preserves the server reason and adds its native action prefix.
    awaitUi("library rename displays its rejection", HANG_LIMIT) {
        ui.x { byVisibleText("Rename failed: $reason") }.present()
    }
    check(ui.dialog(title = "Rename").notPresent())
    check(editor.text == original)
    withContext(OnDispatcher.EDT) { service<EditorHints>().hideAllHints() }
}

/**
 * Reformat must leave a bundled source unchanged. LSP4IJ opens it from `workspace/textDocumentContent` as a read-only
 * document without a file, whose status IntelliJ can't clear: Reformat Code then refuses without a dialog, so the
 * unchanged text is what shows the refusal.
 */
fun Driver.rejectFormatting(editor: JEditorUiComponent) {
    focusEditor(editor)
    val text = editor.text
    invokeAction("ReformatCode", now = true, component = editor.component)
    check(editor.text == text) { "Reformat Code changed a read-only library source" }
}

/** The text of each notification that shows a message of the language server, such as a refactoring's, as IntelliJ shows it. */
fun Driver.serverMessages(): List<String> = withContext(OnDispatcher.EDT) { utility(ServerMessagesProbe::class).shown(singleProject()) }

/** Closes the notifications that show messages of the language server, so that the next ones are a step's own. */
fun Driver.dismissServerMessages() = withContext(OnDispatcher.EDT) { utility(ServerMessagesProbe::class).dismiss(singleProject()) }

/** Reads and closes the notifications in which LSP4IJ shows the language server's window/showMessage. */
@Remote("org.xtclang.idea.playbook.probe.ServerMessages", plugin = "org.xtclang.playbook.probe")
internal interface ServerMessagesProbe {
    fun shown(project: Project): List<String>

    fun dismiss(project: Project)
}
