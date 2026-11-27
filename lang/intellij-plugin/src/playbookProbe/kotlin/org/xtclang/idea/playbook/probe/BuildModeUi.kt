package org.xtclang.idea.playbook.probe

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.UIUtil
import org.xtclang.idea.run.XtcBuildConfigurable
import org.xtclang.idea.run.XtcBuildSettings
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JEditorPane
import javax.swing.JRadioButton

/**
 * What the project's build mode says, while the language server builds and runs some of it with Gradle whatever the mode:
 * the reasons the server gave, the notices, and the Build and Run page as it shows.
 */
object BuildModeUi {
    /** Why the project builds and runs with Gradle, as the server says it, as a JSON array; empty while it does not. */
    @JvmStatic
    fun reasons(project: Project): String =
        JsonArray().apply { XtcBuildSettings.getInstance(project).gradleReasons.forEach(::add) }.toString()

    /**
     * Whether [project] has been told that it builds and runs with Gradle, which XtcBuildSettings says once a session:
     * read from its field, which the plugin keeps to itself.
     */
    @JvmStatic
    fun told(project: Project): Boolean =
        XtcBuildSettings::class.java
            .getDeclaredField("told")
            .apply { isAccessible = true }
            .get(XtcBuildSettings.getInstance(project))
            .let { (it as AtomicBoolean).get() }

    /** Ends the processes that [project]'s runs started and that still run, as their consoles' Stop does, and answers how many. */
    @JvmStatic
    fun endRuns(project: Project): Int {
        val running = ExecutionManager.getInstance(project).getRunningProcesses().filterNot { it.isProcessTerminated }
        ApplicationManager.getApplication().invokeAndWait { running.forEach(ProcessHandler::destroyProcess) }
        return running.size
    }

    /** The mode that builds and runs use, `EMBEDDED` or `GRADLE`. */
    @JvmStatic
    fun effective(project: Project): String = XtcBuildSettings.effective(project).name

    /** The text of each notification titled [title] in [project], as its balloon shows it, as a JSON array. */
    @JvmStatic
    fun notices(
        project: Project,
        title: String,
    ): String =
        JsonArray()
            .apply {
                notifications(project, title).forEach { add(StringUtil.removeHtmlTags(it.content, true).trim()) }
            }.toString()

    /** Expires the notifications titled [title] in [project], as closing them does. */
    @JvmStatic
    fun dismiss(
        project: Project,
        title: String,
    ) = notifications(project, title).forEach(Notification::expire)

    /**
     * The Build and Run page, Settings | Languages & Frameworks | Ecstasy | Build and Run, as it shows: whether each of its
     * choices is enabled, by label, and its comments' text, as a JSON object.
     */
    @JvmStatic
    fun page(project: Project): String {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val panel = XtcBuildConfigurable(project).createPanel()
        return JsonObject()
            .apply {
                add(
                    "choices",
                    JsonObject().apply {
                        UIUtil.findComponentsOfType(panel, JRadioButton::class.java).forEach { addProperty(it.text, it.isEnabled) }
                    },
                )
                add(
                    "comments",
                    JsonArray().apply {
                        UIUtil.findComponentsOfType(panel, JEditorPane::class.java).forEach {
                            add(StringUtil.removeHtmlTags(it.text, true).replace(Regex("\\s+"), " ").trim())
                        }
                    },
                )
            }.toString()
    }

    private fun notifications(
        project: Project,
        title: String,
    ): List<Notification> =
        NotificationsManager
            .getNotificationsManager()
            .getNotificationsOfType(Notification::class.java, project)
            .filter { it.title == title }
}
