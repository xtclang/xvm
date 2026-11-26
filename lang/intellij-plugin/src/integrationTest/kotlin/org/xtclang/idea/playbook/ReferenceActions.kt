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
import org.xtclang.idea.playbook.probe.HANG_LIMIT

/** Native Find Usages and Highlight Usages must preserve the identity selected in the editor. */
fun Driver.referencesAndHighlights(
    editor: JEditorUiComponent,
    at: Int,
    excluded: Int,
) = usagesAndHighlights(editor, at, "contain this use and exclude its shadowed namesake") { ranges ->
    ranges.any { at in it } && ranges.none { excluded in it }
}

/**
 * Invokes Find Usages and then Highlight Usages at [at] in [editor], natively, and waits until [usages] takes the usage
 * view's ranges, and [highlights], unless it is null, the highlights', as [what] says of each.
 */
fun Driver.usagesAndHighlights(
    editor: JEditorUiComponent,
    at: Int,
    what: String,
    highlights: ((List<IntRange>) -> Boolean)? = null,
    usages: (List<IntRange>) -> Boolean,
) {
    val support = fileSupport(editor, at) { it }
    awaitFindUsages(editor, "native references $what", usages)
    val highlighted = highlights ?: usages
    withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().moveToOffset(at) }
    invokeAction("HighlightUsagesInFile", component = editor.component)
    awaitUi("native highlights $what", HANG_LIMIT) {
        val future = support.getHighlightSupport().getValidLSPFuture()
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return@awaitUi false
        }
        highlighted(future.get().map { editor.offsets(it.getRange()) })
    }
    invokeAction("EditorEscape", component = editor.component)
}

/**
 * Invokes Find Usages in [editor] and waits, as [message] says, until a new usage view has ended its search with ranges in
 * the editor's file that [accept] takes.
 */
fun Driver.awaitFindUsages(
    editor: JEditorUiComponent,
    message: String,
    accept: (List<IntRange>) -> Boolean,
) {
    val path = editor.editor.getVirtualFile().getPath()
    val views = service<NativeUsageViews>(singleProject())
    val previous = withContext(OnDispatcher.EDT) { views.getSelectedUsageView() }
    invokeAction("FindUsages", component = editor.component)
    awaitUi(message, HANG_LIMIT) {
        withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
            val view = views.getSelectedUsageView()
            if (view == null || view == previous || view.isSearchInProgress()) {
                return@withContext false
            }
            accept(view.rangesIn(path))
        }
    }
}

/** LSP4IJ's support of [editor]'s file, as [part] takes it from the file's, read with the caret moved to [at]. */
internal fun <T> Driver.fileSupport(
    editor: JEditorUiComponent,
    at: Int,
    part: (LspFileSupport) -> T,
): T =
    withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
        editor.editor.getCaretModel().moveToOffset(at)
        val file =
            requireNotNull(
                service<PsiManager>(singleProject()).findFile(editor.editor.getVirtualFile()),
            )
        part(utility(LspFileSupport::class).getSupport(file))
    }

/** The offsets in [this] editor's document of the protocol's [range]. */
internal fun JEditorUiComponent.offsets(range: SourceRange): IntRange {
    fun offset(position: SourcePosition) = document.getLineStartOffset(position.getLine()) + position.getCharacter()
    return offset(range.getStart()) until offset(range.getEnd())
}

/** Read inside an IDE read action, after the native usage search has completed. */
fun NativeUsageView.rangesIn(path: String): List<IntRange> =
    getUsages()
        .flatMap { it.getMergedInfos().toList() }
        .filter { it.getVirtualFile()?.getPath() == path }
        .mapNotNull { it.getNavigationRange() }
        .map { it.getStartOffset() until it.getEndOffset() }

/** Locates the native Find Usages result view. */
@Remote("com.intellij.usages.UsageViewManager")
interface NativeUsageViews {
    fun getSelectedUsageView(): NativeUsageView?
}

/** Exposes the usages displayed by IntelliJ for reference-result assertions. */
@Remote("com.intellij.usages.UsageView")
interface NativeUsageView {
    fun isSearchInProgress(): Boolean

    fun getUsages(): Collection<NativeUsage>
}

/** Reads one native usage entry's underlying source information. */
@Remote("com.intellij.usages.UsageInfo2UsageAdapter")
interface NativeUsage {
    fun getMergedInfos(): Array<NativeUsageInfo>
}

/** Exposes a usage's file and source range through the driver. */
@Remote("com.intellij.usageView.UsageInfo")
interface NativeUsageInfo {
    fun getVirtualFile(): VirtualFile?

    fun getNavigationRange(): NativeSourceRange?
}

/** Reads offsets from a native usage result without converting to protocol coordinates. */
@Remote("com.intellij.openapi.util.Segment")
interface NativeSourceRange {
    fun getStartOffset(): Int

    fun getEndOffset(): Int
}

/** Requests document highlights through the installed native client. */
@Remote(
    "com.redhat.devtools.lsp4ij.features.highlight.LSPHighlightSupport",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface HighlightSupport {
    fun getValidLSPFuture(): HighlightFuture?
}

/** Polls the client's document-highlight result. */
@Remote("java.util.concurrent.CompletableFuture")
interface HighlightFuture {
    fun isDone(): Boolean

    fun isCompletedExceptionally(): Boolean

    fun get(): List<ClientHighlight>
}

/** Reads a native document highlight's range and access kind. */
@Remote("org.eclipse.lsp4j.DocumentHighlight", plugin = "com.redhat.devtools.lsp4ij")
interface ClientHighlight {
    fun getRange(): SourceRange

    fun getKind(): ClientHighlightKind?
}

/** Exposes the protocol highlight kind by enum name. */
@Remote("org.eclipse.lsp4j.DocumentHighlightKind", plugin = "com.redhat.devtools.lsp4ij")
interface ClientHighlightKind {
    fun name(): String
}
