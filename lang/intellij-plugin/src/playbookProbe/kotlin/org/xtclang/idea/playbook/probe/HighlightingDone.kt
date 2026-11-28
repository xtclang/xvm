package org.xtclang.idea.playbook.probe

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticsApplier

/**
 * Whether IntelliJ has finished highlighting a file, so that what its editor shows of the file's diagnostics changes no
 * more: the daemon has no pass pending or running for it, and LSP4IJ has no repaint of its diagnostics pending for it.
 * Called on the EDT, in a read action, as the daemon's state is read.
 */
object HighlightingDone {
    @JvmStatic
    fun finished(
        project: Project,
        path: String,
    ): Boolean {
        val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return true
        val daemon = DaemonCodeAnalyzerEx.getInstanceEx(project)
        val psi = PsiManager.getInstance(project).findFile(file)
        // a daemon with nothing pending or running highlights no file, this one too
        val analyzed = psi == null || daemon.isErrorAnalyzingFinished(psi) || !(daemon as DaemonCodeAnalyzerImpl).isRunningOrPending
        // LSP4IJ paints a document's diagnostics apart from the daemon, and tells whether a repaint is still to come
        return analyzed && !LSPDiagnosticsApplier.getInstance(project).isRefreshPending(file)
    }
}
