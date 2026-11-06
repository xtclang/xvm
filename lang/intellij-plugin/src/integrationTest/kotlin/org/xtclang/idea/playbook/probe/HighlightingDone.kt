package org.xtclang.idea.playbook.probe

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
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
        return analyzed && !repainting(project, file)
    }

    // TODO LSP4IJ: UP75 — LSP4IJ paints a document's diagnostics apart from the daemon, with a non-blocking read action
    // of its own that it coalesces by its applier and the file, and says nothing of one pending; the platform's
    // coalesced actions under way, which it keeps until each has painted, name it by that key. 0.21.0 drops a repaint
    // asked for before LSPDiagnosticsPassFactory registers its group (GROUP_ID -1), which then never comes; LSP4IJ's
    // main (171f1a7c) parks it in the applier's pendingRefreshes instead, which this must read too once LSP4IJ has it
    private fun repainting(
        project: Project,
        file: VirtualFile,
    ): Boolean {
        val applier = LSPDiagnosticsApplier.getInstance(project)
        // the platform locks the map itself as it reads and writes it
        val tasks = NonBlockingReadActionImpl.getTasksByEquality() as Map<*, *>
        return synchronized(tasks) { tasks.keys.toList() }.any { key ->
            val equality =
                key
                    ?.javaClass
                    ?.getDeclaredField("myEquality")
                    ?.also { it.setAccessible(true) }
                    ?.get(key) as? Array<*>
            equality != null && equality.size == 2 && equality[0] === applier && equality[1] == file
        }
    }
}
