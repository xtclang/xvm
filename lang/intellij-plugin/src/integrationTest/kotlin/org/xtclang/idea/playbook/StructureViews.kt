package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.PsiManager
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.toolwindows.structureToolWindow
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

/** Inspect the actual Structure tree populated by LSP4IJ, including deliberately absent declarations. */
fun Driver.structure(
    editor: JEditorUiComponent,
    include: List<String>,
    exclude: List<String> = emptyList(),
) {
    val visible = withContext(OnDispatcher.EDT) { getToolWindow("Structure").isVisible() }
    if (!visible) invokeAction("ActivateStructureToolWindow", component = editor.component)
    val tree = ui.structureToolWindow().waitAndGetStructureTree()
    waitFor("Structure contains $include and excludes $exclude", 45.seconds) {
        tree.expandAll()
        val rows = tree.collectExpandedPaths().map { it.path.last() }

        fun contains(name: String) = rows.any { Regex("(?<![\\w$])${Regex.escape(name)}(?![\\w$])").containsMatchIn(it) }
        include.all(::contains) && exclude.none(::contains)
    }
}

/** Lines of fold regions installed in the native editor, rather than a second direct LSP request. */
fun Driver.folds(editor: JEditorUiComponent): List<IntRange> =
    withContext(OnDispatcher.EDT) {
        cast(editor.editor, FoldingEditor::class)
            .getFoldingModel()
            .getAllFoldRegions()
            .filter { it.isValid() }
            .map { editor.document.getLineNumber(it.getStartOffset())..editor.document.getLineNumber(it.getEndOffset()) }
    }

/** Extend Selection must grow around the same caret position without moving or editing the document. */
fun Driver.selectionParents(
    editor: JEditorUiComponent,
    at: Int,
) {
    val original = editor.text
    val support =
        withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
            editor.editor.getSelectionModel().removeSelection()
            editor.editor.getCaretModel().moveToOffset(at)
            val file = requireNotNull(service<PsiManager>(singleProject()).findFile(editor.editor.getVirtualFile()))
            utility(LspFileSupport::class).getSupport(file).getSelectionRangeSupport()
        }
    val previous = support.getValidLSPFuture()

    fun selection() =
        withContext(OnDispatcher.EDT) {
            cast(editor.editor.getSelectionModel(), SelectionOffsets::class).let { it.getSelectionStart()..it.getSelectionEnd() }
        }
    invokeAction("EditorSelectWord", component = editor.component)
    waitFor("native selection action receives compiler selection parents", 45.seconds) {
        val future = support.getValidLSPFuture()
        if (future == null || future == previous || !future.isDone() || future.isCompletedExceptionally()) return@waitFor false

        fun offsets(range: SourceRange): IntRange =
            withContext(OnDispatcher.EDT) {
                val start = range.getStart().let { editor.document.getLineStartOffset(it.getLine()) + it.getCharacter() }
                val end = range.getEnd().let { editor.document.getLineStartOffset(it.getLine()) + it.getCharacter() }
                start until end
            }
        future.get().any { selected ->
            val range = offsets(selected.getRange())
            selected.getParent()?.let { parent ->
                val enclosing = offsets(parent.getRange())
                at in range && enclosing.first <= range.first && enclosing.last >= range.last
            } == true
        }
    }
    waitFor("first structural selection contains the cursor", 15.seconds) { selection().let { at in it && it.first < it.last } }
    val first = selection()
    invokeAction("EditorSelectWord", component = editor.component)
    waitFor("parent structural selection contains the first selection", 15.seconds) {
        selection().let { it.first <= first.first && it.last >= first.last && it != first }
    }
    check(editor.text == original)
    withContext(OnDispatcher.EDT) { editor.editor.getSelectionModel().removeSelection() }
}

@Remote("com.intellij.openapi.editor.Editor")
interface FoldingEditor {
    fun getFoldingModel(): EditorFolding
}

@Remote("com.intellij.openapi.editor.FoldingModel")
interface EditorFolding {
    fun getAllFoldRegions(): Array<EditorFoldRegion>
}

@Remote("com.intellij.openapi.editor.FoldRegion")
interface EditorFoldRegion {
    fun isValid(): Boolean

    fun getStartOffset(): Int

    fun getEndOffset(): Int
}

@Remote("com.intellij.openapi.editor.SelectionModel")
interface SelectionOffsets {
    fun getSelectionStart(): Int

    fun getSelectionEnd(): Int
}

@Remote("com.redhat.devtools.lsp4ij.features.selectionRange.LSPSelectionRangeSupport", plugin = "com.redhat.devtools.lsp4ij")
interface SelectionRangeSupport {
    fun getValidLSPFuture(): SelectionRangeFuture?
}

@Remote("java.util.concurrent.CompletableFuture")
interface SelectionRangeFuture {
    fun isDone(): Boolean

    fun isCompletedExceptionally(): Boolean

    fun get(): List<ClientSelectionRange>
}

@Remote("org.eclipse.lsp4j.SelectionRange", plugin = "com.redhat.devtools.lsp4ij")
interface ClientSelectionRange {
    fun getRange(): SourceRange

    fun getParent(): ClientSelectionRange?
}
