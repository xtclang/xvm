package org.xtclang.idea.lsp

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.ServerStatus
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.CompletableFuture

/** Export through the connected server, without guessing another project's process log paths. */
class ExportServerLogsAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val destination =
            FileChooserFactory
                .getInstance()
                .createSaveFileDialog(
                    FileSaverDescriptor(
                        "Export Ecstasy Server Logs",
                        "ZIP of bounded recent server logs, timing traces and status. May contain local paths and logged diagnostics.",
                        "zip",
                    ),
                    project,
                ).save("ecstasy-server-logs.zip") ?: return
        export(project, destination.file.toPath()).whenComplete { _, failure ->
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    Notification(
                        "XTC Language Server",
                        "Ecstasy server logs",
                        if (failure ==
                            null
                        ) {
                            "Saved ${destination.file.name}"
                        } else {
                            "Log export failed: ${failure.message}. See the Language Servers log."
                        },
                        if (failure == null) NotificationType.INFORMATION else NotificationType.ERROR,
                    ).notify(project)
                }
            }
        }
    }

    companion object {
        fun export(
            project: Project,
            destination: Path,
        ): CompletableFuture<Void> {
            val wrapper =
                LanguageServiceAccessor.getInstance(project).startedServers.firstOrNull {
                    it.serverDefinition.id == CompilerSettings.SERVER_ID && it.serverStatus == ServerStatus.started
                }
                    ?: return CompletableFuture.runAsync { project.getService(ServerSupportLogs::class.java).export(destination) }
            return wrapper.initializedServer.thenCompose { (it as XtcLanguageServer).exportLogs() }.thenAcceptAsync { bundle ->
                Files.write(destination, Base64.getDecoder().decode(requireNotNull(bundle["base64"])))
            }
        }
    }
}
