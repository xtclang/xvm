package org.xtclang.idea.playbook.probe

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/** Native project lifetime; no fake language clients or process termination in the assertions. */
object ProjectLifecycle {
    @JvmStatic
    fun open(path: String): CompletableFuture<Project?> =
        CompletableFuture.supplyAsync {
            runBlocking {
                // The driver created this fixture. Trust only its exact path in the disposable IDE.
                TrustedProjects.setProjectTrusted(Path.of(path), true)
                ProjectManagerEx.getInstanceEx().openProjectAsync(
                    Path.of(path),
                    OpenProjectTask {
                        forceOpenInNewFrame = true
                        isNewProject = !Files.exists(Path.of(path, ".idea"))
                    },
                )
            }
        }

    @JvmStatic
    fun close(project: Project): CompletableFuture<Boolean> =
        CompletableFuture.supplyAsync {
            runBlocking { ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project, save = true) }
        }

    @JvmStatic
    fun show(
        project: Project,
        path: String,
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        WriteIntentReadAction.run { openFile(project, path) }
    }

    @JvmStatic
    fun edit(
        project: Project,
        path: String,
        text: String,
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        WriteIntentReadAction.run {
            val file = openFile(project, path)
            val document =
                ReadAction.computeBlocking<Document, RuntimeException> {
                    requireNotNull(FileDocumentManager.getInstance().getDocument(file))
                }
            WriteCommandAction.runWriteCommandAction(project) { document.setText(text) }
        }
    }

    private fun openFile(
        project: Project,
        path: String,
    ): VirtualFile =
        requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(path))).also {
            FileEditorManager.getInstance(project).openFile(it, false)
        }

    @JvmStatic
    fun text(path: String): String =
        ReadAction.computeBlocking<String, RuntimeException> {
            val file = requireNotNull(LocalFileSystem.getInstance().findFileByNioFile(Path.of(path)))
            requireNotNull(FileDocumentManager.getInstance().getDocument(file)).text
        }

    @JvmStatic
    fun visible(project: Project): Boolean {
        ApplicationManager.getApplication().assertIsDispatchThread()
        return WindowManager
            .getInstance()
            .getIdeFrame(project)
            ?.component
            ?.isShowing == true
    }
}
