package org.xtclang.idea.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.redhat.devtools.lsp4ij.LSPFileSupport
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.internal.VirtualFileCancelChecker
import com.redhat.devtools.lsp4ij.internal.editor.CodeVisionEditorFeature
import com.redhat.devtools.lsp4ij.internal.editor.DeclarativeInlayHintsEditorFeature
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeature
import com.redhat.devtools.lsp4ij.internal.editor.SemanticTokensEditorFeature
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

/** One coalesced read/UI pass per feature and connection, independent of document count. */
internal class EditorRefresh(
    private val project: Project,
    private val owner: Disposable,
    private val isDisposed: () -> Boolean,
) {
    enum class Feature {
        LENSES,
        HINTS,
        TOKENS,
    }

    private val renderers =
        mapOf(
            Feature.LENSES to CodeVisionEditorFeature(),
            Feature.HINTS to DeclarativeInlayHintsEditorFeature(),
            Feature.TOKENS to SemanticTokensEditorFeature(),
        )

    private data class Presentation(
        val file: PsiFile,
        val editor: Editor,
        val updates: List<Runnable>,
    )

    // TODO LSP4IJ: UP27 — upstream schedules one NBRA per connected document on the global
    // executor. Coalesce the whole connection/feature instead. Reuse its cache/rendering bridges
    // until EditorFeatureManager offers a bounded batch API with lifecycle cancellation.
    fun request(
        wrapper: LanguageServerWrapper,
        feature: Feature,
    ): CompletableFuture<Void> {
        if (isDisposed() || project.isDisposed || wrapper.isDisposed) return CompletableFuture.completedFuture(null)
        val rendering: EditorFeature = renderers.getValue(feature)
        ReadAction
            .nonBlocking(
                Callable {
                    wrapper.openedDocuments.flatMap { opened ->
                        ProgressManager.checkCanceled()
                        val file = opened.file
                        if (!file.isValid) return@flatMap emptyList()
                        val editors = LSPIJUtils.editorsForFile(file, project).filterNot { it.isDisposed }
                        if (editors.isEmpty()) return@flatMap emptyList()
                        val psi = LSPIJUtils.getPsiFile(file, project) ?: return@flatMap emptyList()
                        if (!rendering.shouldProcess(psi)) return@flatMap emptyList()
                        rendering.clearLSPCache(psi)
                        editors.map { editor ->
                            Presentation(psi, editor, buildList { rendering.collectUiRunnable(editor, psi, this) })
                        }
                    }
                },
            ).expireWith(owner)
            .expireWith(project)
            .expireWhen { isDisposed() || wrapper.isDisposed }
            .coalesceBy(this, feature)
            .finishOnUiThread(ModalityState.any()) { presentations ->
                presentations.filter { it.file.isValid && !it.editor.isDisposed }.forEach { presentation ->
                    rendering.clearEditorCache(presentation.editor, project)
                    LSPFileSupport.getSupport(presentation.file).restartDaemonCodeAnalyzerWithDebounce(
                        VirtualFileCancelChecker(presentation.file.virtualFile),
                    )
                    presentation.updates.forEach(Runnable::run)
                }
            }.submit(AppExecutorUtil.getAppExecutorService())
        // A refresh acknowledges scheduling, as upstream does. Coalescing a superseded pass must
        // not turn a successful server refresh request into an RPC cancellation/error.
        return CompletableFuture.completedFuture(null)
    }
}
