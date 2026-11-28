package org.xtclang.idea.playbook.probe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CompletableFuture

/**
 * Tells IntelliJ's virtual file system of a change a case made on disk before returning, as its file watcher would some
 * time later. `refreshAndFindFileByPath` alone refreshes only a path the file system does not have yet: a file it has,
 * changed or deleted, would reach it, and LSP4IJ, only with the watcher's refresh.
 */
object DiskChanges {
    /**
     * Refreshes [path] and answers its file. The refresh waits for the EDT outside a modal dialog, so behind one it fails
     * at once with the dialog's title and text, as a probe's EDT call does, rather than holding the case and the driver
     * for good, as LSP4IJ's modal "No Implementation found" held the playbook's cases after X37 (UP80).
     */
    @JvmStatic
    fun refresh(path: String): VirtualFile? {
        if (ApplicationManager.getApplication().isDispatchThread) return refreshNow(path)
        val refreshed = CompletableFuture.supplyAsync({ refreshNow(path) }, AppExecutorUtil.getAppExecutorService())
        return awaitEdt(refreshed, ModalityState.nonModal(), "the refresh of $path")
    }

    private fun refreshNow(path: String): VirtualFile? {
        val files = LocalFileSystem.getInstance()
        files.findFileByPath(path)?.let { VfsUtil.markDirtyAndRefresh(false, false, true, it) }
        return files.refreshAndFindFileByPath(path)
    }
}
