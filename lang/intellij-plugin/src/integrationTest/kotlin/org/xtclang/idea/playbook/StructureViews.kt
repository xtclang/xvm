package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.toolwindows.structureToolWindow
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.ui
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// TODO LSP4IJ: #1738 — a release before 0.30.0 can send a document's textDocument/documentSymbol ahead of the didChange
//  of the edit it was asked for. The server answers ContentModified, and LSP4IJ keeps that empty answer as the edited
//  text's symbols, so the Structure view stays empty until the next edit. Drop this once the playbook's LSP4IJ is
//  0.30.0 or later; a local build or a channel's has the fix.
private val keepsStaleSymbols: Boolean =
    System.getProperty("xtc.playbook.lsp4ijPlugin") == null &&
        System.getProperty("xtc.playbook.lsp4ijChannel").isNullOrEmpty() &&
        System.getProperty("xtc.playbook.lsp4ijVersion", "").split('.').let { parts ->
            val (major, minor) = (0..1).map { parts.getOrNull(it)?.toIntOrNull() ?: 0 }
            major == 0 && minor < 30
        }

/** How long the Structure view has to show an edit's symbols before the text is edited again, for [keepsStaleSymbols]. */
private val staleAfter = 10.seconds

/**
 * How long one try at expanding the Structure tree runs in the IDE, the driver's default: a try that takes longer is
 * tried again, so this paces the wait and decides nothing.
 */
private val expansion = 5.seconds

/**
 * Inspect the actual Structure tree populated by LSP4IJ, including deliberately absent
 * declarations.
 */
fun Driver.structure(
    editor: JEditorUiComponent,
    include: List<String>,
    exclude: List<String> = emptyList(),
) {
    val visible = withContext(OnDispatcher.EDT) { getToolWindow("Structure").isVisible() }
    if (!visible) invokeAction("ActivateStructureToolWindow", component = editor.component)
    // the window and its tree, looked for as the wait polls, not found with the driver's waitAndGetStructureTree: that
    //  gives the tree ten seconds to show and its first expansion five, without a retry, and X1's first expansion after
    //  the server's start outlasted those five on a loaded machine (a TimeoutException in JTreeTextFixture.expandAll,
    //  three times on 2026-11-26); waited for as the tour's waits do, which say so every minute and end once nothing that
    //  could make the Structure view show them is in progress
    var structure: JTreeUiComponent? = null
    var rows = emptyList<String>()
    var asked = TimeSource.Monotonic.markNow()
    Quiescence.stuck = null
    val contains =
        withinOrFail(HANG_LIMIT, "Structure contains $include and excludes $exclude") {
            // looked for until they show, and not again: each look writes a line of the driver's log
            if (structure == null) {
                val window = ui.structureToolWindow(structureWindow())
                structure = window.tree().takeIf { shows(window) && shows(it) }
            }
            val tree = structure ?: return@withinOrFail false
            // the driver gives a tree's expansion a few seconds of its own, which a loaded machine can exceed; expanding
            // again is harmless, so its timeout means only that the rows are not all there yet, and the hang bound ends a
            // tree that never expands; nor does a tree whose nodes still load expand yet
            val fixture = tree.fixture
            val expanded =
                fixture.areTreeNodesLoaded() &&
                    runCatching { fixture.expandAll(expansion.inWholeMilliseconds.toInt()) }
                        .onFailure { failure ->
                            if (generateSequence(failure, Throwable::cause).none { it is TimeoutException }) throw failure
                        }.isSuccess
            if (!expanded) return@withinOrFail false
            rows = tree.collectExpandedPaths().map { it.path.last() }

            fun contains(name: String) =
                rows.any {
                    Regex("(?<![\\w$])${Regex.escape(name)}(?![\\w$])").containsMatchIn(it)
                }
            val shown = include.all(::contains) && exclude.none(::contains)
            if (!shown && keepsStaleSymbols && asked.elapsedNow() > staleAfter) {
                println(
                    "[playbook] ${LocalTime.now().truncatedTo(ChronoUnit.SECONDS)} warning: the Structure view shows $rows " +
                        "after $staleAfter; editing the text and back, as LSP4IJ before 0.30.0 can keep an empty answer (#1738)",
                )
                val text = editor.text
                editor.text = "$text "
                editor.text = text
                asked = TimeSource.Monotonic.markNow()
            }
            shown
        }
    check(contains) { "The Structure view did not show $include without $exclude: it shows $rows; ${Quiescence.stuck}" }
}

/**
 * Where the driver finds the Structure tool window: its decorator, by the name it has for accessibility, its title, or else
 * its stripe title, or else its ID, and " Tool Window" (InternalDecoratorImpl.AccessibleInternalDecorator). The driver's
 * own structureToolWindow() takes "Logical" and "Physical" too, which can be the Project window's: X1 once read the
 * Project view's tree, the JDK's classes, as the Structure view's (run-12107724608578254908).
 */
private fun Driver.structureWindow(): String {
    val window = cast(getToolWindow("Structure"), TitledToolWindow::class)
    val name = withContext(OnDispatcher.EDT) { window.getTitle()?.ifEmpty { null } ?: window.getStripeTitle().ifEmpty { window.getId() } }
    return "//div[@class='InternalDecoratorImpl' and @accessiblename='$name Tool Window']"
}

/** A tool window, by the names its decorator takes. */
@Remote("com.intellij.openapi.wm.ToolWindow")
interface TitledToolWindow {
    fun getTitle(): String?

    fun getStripeTitle(): String

    fun getId(): String
}

/** Whether [component] shows: one that the driver finds more than once, which present() fails on, does not yet. */
private fun shows(component: UiComponent): Boolean =
    runCatching { component.present() }
        .getOrElse { failure -> if ("more than one" in failure.message.orEmpty()) false else throw failure }

/**
 * Lines of fold regions installed in the native editor, rather than a second direct LSP request.
 */
fun Driver.folds(editor: JEditorUiComponent): List<IntRange> =
    withContext(OnDispatcher.EDT) {
        cast(editor.editor, FoldingEditor::class)
            .getFoldingModel()
            .getAllFoldRegions()
            .filter { it.isValid() }
            .map {
                val firstLine = editor.document.getLineNumber(it.getStartOffset())
                val lastLine = editor.document.getLineNumber(it.getEndOffset())
                firstLine..lastLine
            }
    }

/**
 * Extend Selection must grow around the same caret position without moving or editing the document.
 */
fun Driver.selectionParents(
    editor: JEditorUiComponent,
    at: Int,
) {
    val original = editor.text
    val support =
        fileSupport(editor, at) {
            editor.editor.getSelectionModel().removeSelection()
            it.getSelectionRangeSupport()
        }
    val previous = support.getValidLSPFuture()

    fun selection() =
        withContext(OnDispatcher.EDT) {
            cast(editor.editor.getSelectionModel(), SelectionOffsets::class).let {
                it.getSelectionStart()..it.getSelectionEnd()
            }
        }
    invokeAction("EditorSelectWord", component = editor.component)
    awaitUi("native selection action receives compiler selection parents", HANG_LIMIT) {
        val future = support.getValidLSPFuture()
        if (
            future == null ||
            future == previous ||
            !future.isDone() ||
            future.isCompletedExceptionally()
        ) {
            return@awaitUi false
        }

        fun offsets(range: SourceRange): IntRange = withContext(OnDispatcher.EDT) { editor.offsets(range) }
        future.get().any { selected ->
            val range = offsets(selected.getRange())
            selected.getParent()?.let { parent ->
                val enclosing = offsets(parent.getRange())
                at in range && enclosing.first <= range.first && enclosing.last >= range.last
            } == true
        }
    }
    awaitUi("first structural selection contains the cursor", HANG_LIMIT) {
        selection().let { at in it && it.first < it.last }
    }
    val first = selection()
    invokeAction("EditorSelectWord", component = editor.component)
    awaitUi("parent structural selection contains the first selection", HANG_LIMIT) {
        selection().let { it.first <= first.first && it.last >= first.last && it != first }
    }
    check(editor.text == original)
    withContext(OnDispatcher.EDT) { editor.editor.getSelectionModel().removeSelection() }
}

/** Exposes an editor's native folding model through the driver. */
@Remote("com.intellij.openapi.editor.Editor")
interface FoldingEditor {
    fun getFoldingModel(): EditorFolding
}

/** Enumerates the fold regions installed in the native editor. */
@Remote("com.intellij.openapi.editor.FoldingModel")
interface EditorFolding {
    fun getAllFoldRegions(): Array<EditorFoldRegion>
}

/** Reads native fold offsets and validity for folding assertions. */
@Remote("com.intellij.openapi.editor.FoldRegion")
interface EditorFoldRegion {
    fun isValid(): Boolean

    fun getStartOffset(): Int

    fun getEndOffset(): Int
}

/** Reads the actual editor selection for expand-selection assertions. */
@Remote("com.intellij.openapi.editor.SelectionModel")
interface SelectionOffsets {
    fun getSelectionStart(): Int

    fun getSelectionEnd(): Int
}

/** Requests selection ranges through the installed client's feature support. */
@Remote(
    "com.redhat.devtools.lsp4ij.features.selectionRange.LSPSelectionRangeSupport",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface SelectionRangeSupport {
    fun getValidLSPFuture(): SelectionRangeFuture?
}

/** Polls native selection-range results. */
@Remote("java.util.concurrent.CompletableFuture")
interface SelectionRangeFuture {
    fun isDone(): Boolean

    fun isCompletedExceptionally(): Boolean

    fun get(): List<ClientSelectionRange>
}

/** Exposes a protocol selection range and its enclosing parent. */
@Remote("org.eclipse.lsp4j.SelectionRange", plugin = "com.redhat.devtools.lsp4ij")
interface ClientSelectionRange {
    fun getRange(): SourceRange

    fun getParent(): ClientSelectionRange?
}
