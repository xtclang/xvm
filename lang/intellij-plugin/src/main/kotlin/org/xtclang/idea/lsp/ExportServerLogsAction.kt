package org.xtclang.idea.lsp

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import java.nio.file.Files
import java.util.Base64

/** Export through the connected server, without guessing another project's process log paths. */
class ExportServerLogsAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val wrapper =
            LanguageServiceAccessor.getInstance(project).startedServers.firstOrNull {
                it.serverDefinition.id ==
                    CompilerSettings.SERVER_ID
            }
        if (wrapper == null) {
            Notification(
                "XTC Language Server",
                "Ecstasy server logs",
                "Open an Ecstasy file to connect to its server, then export logs. Previous logs remain under ~/.xtc/logs/lsp.",
                NotificationType.INFORMATION,
            ).notify(project)
            return
        }
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
        wrapper.initializedServer
            .thenCompose { (it as XtcLanguageServer).exportLogs() }
            .thenAcceptAsync { bundle ->
                val bytes = Base64.getDecoder().decode(requireNotNull(bundle["base64"]))
                Files.write(destination.file.toPath(), bytes)
            }.whenComplete { _, failure ->
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
}
