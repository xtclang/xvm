package org.xtclang.idea.playbook.probe

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Tells IntelliJ's virtual file system of a change a case made on disk before returning, as its file watcher would some
 * time later. `refreshAndFindFileByPath` alone refreshes only a path the file system does not have yet: a file it has,
 * changed or deleted, would reach it, and LSP4IJ, only with the watcher's refresh.
 */
object DiskChanges {
    @JvmStatic
    fun refresh(path: String): VirtualFile? {
        val files = LocalFileSystem.getInstance()
        files.findFileByPath(path)?.let { VfsUtil.markDirtyAndRefresh(false, false, true, it) }
        return files.refreshAndFindFileByPath(path)
    }
}
