package org.xtclang.idea.playbook.probe

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.refactoring.move.MoveHandler
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.intellij.refactoring.rename.RenameHandlerRegistry
import org.xtclang.idea.lsp.XtcFileMoveHandler
import org.xtclang.idea.lsp.XtcFileRenameHandler

/** Invoke the registered file-tree Rename handler, including its real dialog and preflight. */
object FileTreeOperations {
    @JvmStatic
    fun move(project: Project, paths: List<String>, destination: String) {
        val files = paths.map {
            requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(it))
        }
        val target =
            requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(destination))
        ReadAction.runBlocking<RuntimeException> {
            val manager = PsiManager.getInstance(project)
            val elements =
                files
                    .map {
                        requireNotNull(
                            if (it.isDirectory) manager.findDirectory(it) else manager.findFile(it)
                        )
                    }
                    .toTypedArray()
            val directory = requireNotNull(manager.findDirectory(target))
            val context =
                SimpleDataContext.builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, files.toTypedArray())
                    .build()
            check(
                MoveHandlerDelegate.EP_NAME.extensionList.first {
                    it.canMove(elements, directory, null)
                } is XtcFileMoveHandler
            )
            ApplicationManager.getApplication().invokeLater {
                WriteIntentReadAction.run {
                    MoveHandler.doMove(project, elements, directory, context, null)
                }
            }
        }
    }

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
