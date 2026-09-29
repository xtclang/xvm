package org.xtclang.idea.playbook.probe

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.refactoring.rename.RenameHandlerRegistry
import org.xtclang.idea.lsp.XtcFileRenameHandler

/** Invoke the registered file-tree Rename handler, including its real dialog and preflight. */
object FileTreeOperations {
    @JvmStatic
    fun loadDirectory(path: String) {
        val directory = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(path))
        // External fixtures are otherwise unknown to VFS: first discovery is not a create event.
        directory.children
    }

    @JvmStatic
    fun rename(project: Project, path: String) {
        val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(path))
        ReadAction.runBlocking<RuntimeException> {
            val psi =
                requireNotNull(
                    if (file.isDirectory) PsiManager.getInstance(project).findDirectory(file)
                    else PsiManager.getInstance(project).findFile(file)
                )
            val context =
                SimpleDataContext.builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .add(CommonDataKeys.VIRTUAL_FILE, file)
                    .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(file))
                    .add(CommonDataKeys.PSI_ELEMENT, psi)
                    .build()
            ApplicationManager.getApplication().invokeLater {
                WriteIntentReadAction.run {
                    val handler = RenameHandlerRegistry.getInstance().getRenameHandler(context)
                    check(handler is XtcFileRenameHandler) {
                        "Unexpected file Rename handler: $handler"
                    }
                    handler.invoke(project, arrayOf(psi), context)
                }
            }
        }
    }
}
