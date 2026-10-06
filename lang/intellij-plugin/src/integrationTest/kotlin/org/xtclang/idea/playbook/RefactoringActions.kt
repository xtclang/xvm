package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.WaitForException
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleList
import com.intellij.driver.sdk.ui.components.elements.accessibleTable
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.ui
import kotlin.time.Duration.Companion.seconds

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
    awaitUi("installed Rename handler is available", 45.seconds) { available() }
    invokeAction("RenameElement", now = false, component = editor.component)
    val dialog = ui.dialog(title = "Rename")
    awaitUi(
        "native rename dialog",
        45.seconds,
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
    awaitUi("native rename dialog opens", 45.seconds) { dialog.present() }
    val input =
        dialog.x(JEditorUiComponent::class.java) {
            byType("com.intellij.openapi.editor.impl.EditorComponentImpl")
        }
    input.text = replacement
    val button = cast(dialog.button("Refactor").component, NativeButton::class)
    awaitUi("rename accepts the new name", 10.seconds) { button.isEnabled() }
    withContext(OnDispatcher.EDT) { button.doClick() }
    awaitUi("rename dialog closes", 45.seconds) { dialog.notPresent() }
}

/** Select the reference-preserving XTC scope when another plugin also offers plain file rename. */
fun Driver.chooseXtcFileRename() {
    val chooser = ui.dialog(title = "Select Refactoring")
    val rename = ui.dialog(title = "Rename")
    awaitUi("native file rename scope or name dialog", 45.seconds) {
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

/** Select the intention list, excluding its separate preview popup, without moving the pointer. */
fun Driver.choosePopup(
    editor: JEditorUiComponent,
    expected: List<String>,
    selected: String,
    reopen: () -> Unit,
) {
    val inspection = PopupInspection(this, editor, reopen)
    // Capture the source/caret before dispatch: an intention can move the caret while it
    // discovers actions. Recovery must retain the requested location, not that later position.
    reopen()
    val popup = ui.popup("//div[@class='HeavyWeightWindow'][.//div[@class='MyList']]")
    awaitUi(
        "native popup contains $expected",
        45.seconds,
        errorMessage = {
            "Expected $expected; rendered rows: ${if (popup.present()) popup.accessibleList { byClass("MyList") }.items else "no list"}"
        },
    ) {
        inspection.recover()
        if (!popup.present()) {
            try {
                awaitUi("intention menu opens", 2.seconds) { popup.present() }
            } catch (_: WaitForException) {
                // Reopen only an unapplied inspection, never a completed generation or rename.
                inspection.reopenUnapplied()
            }
            return@awaitUi false
        }
        val rows = popup.accessibleList { byClass("MyList") }.items
        expected.all { name -> rows.any { it.contains(name) } }
    }
    val list = popup.accessibleList { byClass("MyList") }
    val index = list.items.indexOfFirst { it.contains(selected) }
    check(index >= 0)
    withContext(OnDispatcher.EDT) {
        cast(list.component, NativeListSelection::class).setSelectedIndex(index)
    }
    popup.keyboard { enter() }
    awaitUi("chosen popup closes", 15.seconds) { popup.notPresent() }
}

/** LSP4IJ renders multiple navigation targets through IntelliJ's Show Usages table. */
fun Driver.chooseTargets(
    editor: JEditorUiComponent,
    expected: List<String>,
    selected: String,
    reopen: () -> Unit,
) {
    val inspection = PopupInspection(this, editor, reopen)
    val popup = ui.popup()
    val table = popup.accessibleTable()
    awaitUi(
        message = "native navigation chooser contains exactly $expected",
        errorMessage = {
            "Expected $expected; rendered rows: ${if (table.present()) table.content() else "no table"}"
        },
        timeout = 45.seconds,
    ) {
        inspection.recover()
        if (!popup.present()) return@awaitUi false
        val rows = table.content().values.map { it.values.joinToString(" ") }
        rows.size == expected.size && expected.all { name -> rows.any { it.contains(name) } }
    }
    val row =
        table
            .content()
            .entries
            .single { (_, cells) -> cells.values.any { it.contains(selected) } }
            .key
    chooseNavigationRow(table, row)
    awaitUi("selected declaration closes the chooser", 15.seconds) { popup.notPresent() }
}

/** Invoke the chooser's registered Enter action once, independent of desktop keyboard focus. */
internal fun Driver.chooseNavigationRow(
    table: JTableUiComponent,
    row: Int,
) {
    withContext(OnDispatcher.EDT) {
        utility(NativeNavigationChooser::class).choose(table.component, row)
    }
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
    // TODO LSP4IJ: UP07 — overlapping full pulls can cancel an old lazy fix during discovery.
    // Dispatch through the UI queue so the platform handles ProcessCanceledException normally;
    // the bounded popup wait may reopen an unapplied inspection, never replay a chosen edit.
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
    awaitUi("native type hierarchy includes $child", 45.seconds) {
        if (!browser.present()) return@awaitUi false
        // The native browser expands its root. Observe it without expandAll's nested ten-second
        // loading deadline cutting short this asynchronous hierarchy request.
        browser.tree().collectExpandedPaths().any { path -> path.path.last().contains(child) }
    }
    val path = editor.editor.getVirtualFile().getPath()
    val views = service<NativeUsageViews>(singleProject())
    val previous = withContext(OnDispatcher.EDT) { views.getSelectedUsageView() }
    focusEditor(editor)
    withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(at) }
    invokeAction("FindUsages", component = editor.component)
    awaitUi("incomplete graph withholds references while retaining local navigation", 45.seconds) {
        withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
            val view = views.getSelectedUsageView()
            if (view == null || view == previous || view.isSearchInProgress()) {
                return@withContext false
            }
            // LSP4IJ combines definitions, implementations, type definitions and references here.
            // The declaration may remain, but the known extends-use must not masquerade as a
            // complete workspace reference result beside the broken module.
            val ranges = view.rangesIn(path)
            ranges.any { at in it } && ranges.none { referenceAt in it }
        }
    }
}

@Remote("javax.swing.JButton")
interface NativeButton {
    fun isEnabled(): Boolean

    fun doClick()
}

@Remote("javax.swing.JList")
interface NativeListSelection {
    fun setSelectedIndex(index: Int)
}

@Remote("org.xtclang.idea.playbook.probe.NavigationChooser", plugin = "org.xtclang.playbook.probe")
internal interface NativeNavigationChooser {
    fun choose(
        component: Component,
        row: Int,
    )
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
    awaitUi("library rename displays its rejection", 45.seconds) {
        ui.x { byVisibleText("Rename failed: $reason") }.present()
    }
    check(ui.dialog(title = "Rename").notPresent())
    check(editor.text == original)
    withContext(OnDispatcher.EDT) { service<EditorHints>().hideAllHints() }
}

/**
 * Reformat must encounter the native read-only gate; cancel it without unlocking bundled sources.
 */
fun Driver.rejectFormatting(editor: JEditorUiComponent) {
    focusEditor(editor)
    invokeAction("ReformatCode", now = false, component = editor.component)
    val dialog = ui.dialog(title = "Clear Read-Only Status")
    awaitUi("reformat is blocked by read-only source", 45.seconds) { dialog.present() }
    withContext(OnDispatcher.EDT) {
        cast(dialog.button("Cancel").component, NativeButton::class).doClick()
    }
    awaitUi("read-only prompt closes", 15.seconds) { dialog.notPresent() }
}
