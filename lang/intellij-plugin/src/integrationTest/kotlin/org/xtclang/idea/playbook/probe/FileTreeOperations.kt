package org.xtclang.idea.playbook.probe

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

/** Use real VFS rename events: LSP4IJ must participate through its installed file listener. */
object FileTreeOperations {
    @JvmStatic
    fun rename(project: Project, path: String, newName: String) {
        val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(path))
        WriteCommandAction.runWriteCommandAction(
            project,
            "Rename XTC file",
            null,
            Runnable {
                file.rename(FileTreeOperations, newName)
            },
        )
    }
}
