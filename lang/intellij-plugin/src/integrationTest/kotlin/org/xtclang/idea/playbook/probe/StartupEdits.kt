package org.xtclang.idea.playbook.probe

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.ServerStatus

/** Execute one real editor transaction before the restarted server finishes initialization. */
object StartupEdits {
    @JvmStatic
    fun openAndEdit(
        project: Project,
        file: VirtualFile,
        text: String,
    ): String {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val clients = LanguageServiceAccessor.getInstance(project)
        check(clients.startedServers.isEmpty()) { "Cold-start edit requires a fresh language client" }
        FileEditorManager.getInstance(project).openFile(file, false)
        val document =
            ReadAction.computeBlocking<Document, RuntimeException> {
                requireNotNull(
                    FileDocumentManager.getInstance().getDocument(file),
                )
            }
        WriteCommandAction.runWriteCommandAction(project) { document.setText(text) }
        check(clients.startedServers.none { it.serverStatus == ServerStatus.started }) {
            "Server initialized before the cold-start edit completed"
        }
        return Gson().toJson(mapOf("phase" to "cold-open", "stamp" to document.modificationStamp, "text" to document.text))
    }

    @JvmStatic
    fun restartAndEdit(
        project: Project,
        file: VirtualFile,
        intermediate: String,
        finalText: String,
        reopen: Boolean,
    ): String {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val server = LanguageServiceAccessor.getInstance(project).startedServers.single()
        val previousPid = server.currentProcessId
        val document =
            ReadAction.computeBlocking<Document, RuntimeException> {
                requireNotNull(
                    FileDocumentManager.getInstance().getDocument(file),
                )
            }
        server.restart()
        val initialized = server.initializedServer
        check(!initialized.isDone) { "Server initialized before the startup edit" }
        val before = server.serverStatus.name
        WriteCommandAction.runWriteCommandAction(project) { document.setText(intermediate) }
        if (reopen) {
            val editors = FileEditorManager.getInstance(project)
            editors.closeFile(file)
            editors.openFile(file, false)
        }
        WriteCommandAction.runWriteCommandAction(project) { document.setText(finalText) }
        check(!initialized.isDone) { "Server initialized during the startup transaction" }
        return Gson().toJson(
            mapOf(
                "previousPid" to previousPid,
                "statusBefore" to before,
                "statusAfter" to server.serverStatus.name,
                "stamp" to document.modificationStamp,
                "reopened" to reopen,
                "text" to document.text,
            ),
        )
    }
}
