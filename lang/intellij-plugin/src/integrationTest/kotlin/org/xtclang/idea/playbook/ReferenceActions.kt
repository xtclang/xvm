package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.PsiManager
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

/** Native Find Usages and Highlight Usages must preserve the identity selected in the editor. */
fun Driver.referencesAndHighlights(
    editor: JEditorUiComponent,
    at: Int,
    excluded: Int,
) {
    val support =
        withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
            editor.editor.getCaretModel().moveToOffset(at)
            val file = requireNotNull(service<PsiManager>(singleProject()).findFile(editor.editor.getVirtualFile()))
            utility(LspFileSupport::class).getSupport(file)
        }
    val path = editor.editor.getVirtualFile().getPath()

    fun contains(
        range: SourceRange,
        offset: Int,
    ): Boolean {
        val start = range.getStart().let { editor.document.getLineStartOffset(it.getLine()) + it.getCharacter() }
        val end = range.getEnd().let { editor.document.getLineStartOffset(it.getLine()) + it.getCharacter() }
        return offset in start until end
    }
    val views = service<NativeUsageViews>(singleProject())
    val previous = withContext(OnDispatcher.EDT) { views.getSelectedUsageView() }
    invokeAction("FindUsages", component = editor.component)
    waitFor("native references contain this use and exclude its shadowed namesake", 45.seconds) {
        withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
            val view = views.getSelectedUsageView()
            if (view == null || view == previous || view.isSearchInProgress()) return@withContext false
            val ranges =
                view
                    .getUsages()
                    .flatMap { it.getMergedInfos().toList() }
                    .filter { it.getVirtualFile()?.getPath() == path }
                    .mapNotNull { it.getNavigationRange() }
                    .map { it.getStartOffset() until it.getEndOffset() }
            ranges.any { at in it } && ranges.none { excluded in it }
        }
    }
    withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(at) }
    invokeAction("HighlightUsagesInFile", component = editor.component)
    waitFor("native highlights contain this use and exclude its shadowed namesake", 45.seconds) {
        val future = support.getHighlightSupport().getValidLSPFuture()
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) return@waitFor false
        val highlights = future.get()
        highlights.any { contains(it.getRange(), at) } && highlights.none { contains(it.getRange(), excluded) }
    }
    invokeAction("EditorEscape", component = editor.component)
}

@Remote("com.intellij.usages.UsageViewManager")
interface NativeUsageViews {
    fun getSelectedUsageView(): NativeUsageView?
}

@Remote("com.intellij.usages.UsageView")
interface NativeUsageView {
    fun isSearchInProgress(): Boolean

    fun getUsages(): Collection<NativeUsage>
}

@Remote("com.intellij.usages.UsageInfo2UsageAdapter")
interface NativeUsage {
    fun getMergedInfos(): Array<NativeUsageInfo>
}

@Remote("com.intellij.usageView.UsageInfo")
interface NativeUsageInfo {
    fun getVirtualFile(): VirtualFile?

    fun getNavigationRange(): NativeSourceRange?
}

@Remote("com.intellij.openapi.util.Segment")
interface NativeSourceRange {
    fun getStartOffset(): Int

    fun getEndOffset(): Int
}

@Remote("com.redhat.devtools.lsp4ij.features.highlight.LSPHighlightSupport", plugin = "com.redhat.devtools.lsp4ij")
interface HighlightSupport {
    fun getValidLSPFuture(): HighlightFuture?
}

@Remote("java.util.concurrent.CompletableFuture")
interface HighlightFuture {
    fun isDone(): Boolean

    fun isCompletedExceptionally(): Boolean

    fun get(): List<ClientHighlight>
}

@Remote("org.eclipse.lsp4j.DocumentHighlight", plugin = "com.redhat.devtools.lsp4ij")
interface ClientHighlight {
    fun getRange(): SourceRange
}
