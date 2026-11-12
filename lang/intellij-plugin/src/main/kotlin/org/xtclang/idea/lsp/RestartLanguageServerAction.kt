package org.xtclang.idea.lsp

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor

/** Restarts the project's Ecstasy language server with its adapter and the saved runtime settings. */
class RestartLanguageServerAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        try {
            if (!restartEcstasyServers(project)) {
                notify(
                    project,
                    NotificationType.INFORMATION,
                    "No Ecstasy language server runs in this project; opening an Ecstasy file starts one.",
                )
            }
        } catch (invalid: IllegalArgumentException) {
            notify(project, NotificationType.ERROR, "The saved server runtime settings are invalid: ${invalid.message}")
        }
    }

    private fun notify(
        project: Project,
        type: NotificationType,
        content: String,
    ) = Notification(XtcFileOperations.NOTIFICATIONS, "Ecstasy language server not restarted", content, type).notify(project)
}

/**
 * Restarts [project]'s started Ecstasy language servers with the saved runtime settings, which it checks first, so that
 * invalid ones stop no working server; whether there was one to restart.
 */
internal fun restartEcstasyServers(project: Project): Boolean {
    ServerRuntimeSettings.getInstance().launchArguments()
    val servers =
        LanguageServiceAccessor
            .getInstance(project)
            .startedServers
            .filter { it.serverDefinition.id == CompilerSettings.SERVER_ID }
    servers.forEach { it.restart() }
    return servers.isNotEmpty()
}
