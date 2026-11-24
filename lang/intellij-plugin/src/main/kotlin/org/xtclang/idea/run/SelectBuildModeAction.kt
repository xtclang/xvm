package org.xtclang.idea.run

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import org.xtclang.idea.lsp.XtcFileOperations

/**
 * Chooses how the project's Ecstasy Build, Rebuild and Run work, as Build, Execution, Deployment › Ecstasy does. The
 * next build or run uses the choice; nothing else needs to restart or sync.
 */
class SelectBuildModeAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val settings = XtcBuildSettings.getInstance(project)
        val replaced = settings.replacedLibraries
        val step =
            object : BaseListPopupStep<XtcBuildMode>("Ecstasy Build Mode", XtcBuildMode.entries) {
                // the embedded compiler, shown unavailable while the project's own modules replace bundled libraries
                override fun isSelectable(value: XtcBuildMode) = XtcBuildSettings.available(value, replaced)

                override fun getTextFor(value: XtcBuildMode) =
                    if (isSelectable(value)) value.label else "${value.label} (not available: this project replaces bundled libraries)"

                override fun getDefaultOptionIndex() = XtcBuildMode.entries.indexOf(settings.mode)

                override fun onChosen(
                    selectedValue: XtcBuildMode,
                    finalChoice: Boolean,
                ): PopupStep<*>? = doFinalStep { choose(project, settings, selectedValue) }
            }
        JBPopupFactory.getInstance().createListPopup(step).showInBestPositionFor(event.dataContext)
    }

    private fun choose(
        project: Project,
        settings: XtcBuildSettings,
        mode: XtcBuildMode,
    ) {
        if (project.isDisposed || settings.mode == mode) return
        settings.mode = mode
        val effective = if (XtcBuildSettings.effective(project) == XtcBuildMode.GRADLE) "Gradle" else "the embedded compiler"
        val replaced = settings.replacedLibraries
        Notification(
            XtcFileOperations.NOTIFICATIONS,
            "Ecstasy build mode: $mode",
            if (replaced.isEmpty() || mode == XtcBuildMode.GRADLE) {
                "Build, Rebuild and Run now use $effective."
            } else {
                "Build, Rebuild and Run still use Gradle while this project's own modules replace the bundled " +
                    "libraries ${replaced.joinToString()}."
            },
            NotificationType.INFORMATION,
        ).notify(project)
    }
}
