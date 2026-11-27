package org.xtclang.idea.run

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import org.xtclang.idea.XtcProjectAction
import org.xtclang.idea.lsp.XtcFileOperations

/**
 * Chooses how the project's Ecstasy Build, Rebuild and Run work, as Languages & Frameworks › Ecstasy › Build and Run does. The
 * next build or run uses the choice; nothing else needs to restart or sync.
 */
class SelectBuildModeAction : XtcProjectAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        JBPopupFactory.getInstance().createListPopup(step(project)).showInBestPositionFor(event.dataContext)
    }

    /** The popup's choices for [project], of which the one chosen applies at once. */
    internal fun step(project: Project): BaseListPopupStep<XtcBuildMode> {
        val settings = XtcBuildSettings.getInstance(project)
        val reasons = settings.gradleReasons
        return object : BaseListPopupStep<XtcBuildMode>("Ecstasy Build Mode", XtcBuildMode.entries) {
            // the embedded compiler, shown unavailable, saying why, while the project builds and runs with Gradle
            override fun isSelectable(value: XtcBuildMode) = XtcBuildSettings.available(value, reasons)

            override fun getTextFor(value: XtcBuildMode) =
                if (isSelectable(value)) value.label else "${value.label} (not available: this project ${reasons.joinToString(" and ")})"

            override fun getDefaultOptionIndex() = XtcBuildMode.entries.indexOf(settings.mode)

            override fun onChosen(
                selectedValue: XtcBuildMode,
                finalChoice: Boolean,
            ): PopupStep<*>? = doFinalStep { choose(project, settings, selectedValue) }
        }
    }

    private fun choose(
        project: Project,
        settings: XtcBuildSettings,
        mode: XtcBuildMode,
    ) {
        if (project.isDisposed || settings.mode == mode) return
        settings.mode = mode
        val effective = if (XtcBuildSettings.effective(project) == XtcBuildMode.GRADLE) "Gradle" else "the embedded compiler"
        val reasons = settings.gradleReasons
        Notification(
            XtcFileOperations.NOTIFICATIONS,
            "Ecstasy build mode: $mode",
            if (reasons.isEmpty() || mode == XtcBuildMode.GRADLE) {
                "Build, Rebuild and Run now use $effective."
            } else {
                "Build, Rebuild and Run still use Gradle while this project ${reasons.joinToString(" and ")}."
            },
            NotificationType.INFORMATION,
        ).notify(project)
    }
}
