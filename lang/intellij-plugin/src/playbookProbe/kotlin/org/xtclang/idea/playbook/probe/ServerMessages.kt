package org.xtclang.idea.playbook.probe

import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil

/** Loaded only in the disposable test IDE; never included in the distributed XTC plugin. */
object ServerMessages {
    /** The notification group in which LSP4IJ shows a language server's window/showMessage. */
    private const val GROUP = "LSP/window/showMessage"

    /** The text of each notification in [project] that shows a language server's message, as its balloon shows it. */
    @JvmStatic
    fun shown(project: Project): List<String> = notifications(project).map { StringUtil.removeHtmlTags(it.content, true).trim() }

    /** Expires the notifications in [project] that show a language server's message, as closing them does. */
    @JvmStatic
    fun dismiss(project: Project) = notifications(project).forEach(Notification::expire)

    private fun notifications(project: Project): List<Notification> =
        NotificationsManager
            .getNotificationsManager()
            .getNotificationsOfType(Notification::class.java, project)
            .filter { it.groupId == GROUP }
}
