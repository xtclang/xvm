package org.xtclang.idea.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiManager
import com.intellij.util.messages.MessageBusConnection

class XtcEditorStartupActivity : ProjectActivity {
    private val logger = logger<XtcEditorStartupActivity>()

    override suspend fun execute(project: Project) {
        val delegates =
            EnterHandlerDelegate.EP_NAME.extensionList
                .map { it.javaClass.name }
                .sorted()
        logger.warn(
            "EnterHandlerDelegate registry for project '${project.name}': " +
                "${delegates.joinToString(", ")}",
        )

        val scheme = EditorColorsManager.getInstance().globalScheme.name
        val fileTypeForExtension = FileTypeManager.getInstance().getFileTypeByExtension("x")
        logger.warn(
            "XTC editor diagnostics for project '${project.name}': " +
                "globalColorScheme='$scheme', fileTypeByExtension('x')='${fileTypeForExtension.name}' (${fileTypeForExtension.javaClass.name})",
        )

        logOpenXtcFiles(project, "startup")

        val busConnection: MessageBusConnection = project.messageBus.connect(project)
        busConnection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    event.newFile?.takeIf { it.extension == "x" }?.let {
                        logXtcFile(project, it.path, "selectionChanged")
                    }
                }
            },
        )
    }

    private fun logOpenXtcFiles(
        project: Project,
        phase: String,
    ) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles
            .filter { it.extension == "x" }
            .forEach { logXtcFile(project, it.path, phase) }
    }

    private fun logXtcFile(
        project: Project,
        filePath: String,
        phase: String,
    ) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val virtualFile = fileEditorManager.openFiles.firstOrNull { it.path == filePath } ?: return
        val psiFile =
            ApplicationManager.getApplication().runReadAction<com.intellij.psi.PsiFile?> {
                PsiManager.getInstance(project).findFile(virtualFile)
            }
        val fileType = virtualFile.fileType
        val language = psiFile?.language
        logger.warn(
            "XTC file diagnostics ($phase): path='${virtualFile.path}', " +
                "fileType='${fileType.name}' (${fileType.javaClass.name}), " +
                "psiLanguage='${language?.id ?: "<none>"}' (${language?.javaClass?.name ?: "<none>"})",
        )
    }
}
