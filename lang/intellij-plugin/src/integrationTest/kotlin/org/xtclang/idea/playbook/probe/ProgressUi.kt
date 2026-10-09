package org.xtclang.idea.playbook.probe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.panel.ProgressPanel
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.util.ui.UIUtil
import java.awt.Window
import javax.swing.JProgressBar

/** Observe real progress and activate its visible Cancel control without moving the pointer. */
object ProgressUi {
    private fun status(project: Project): StatusBarEx {
        ApplicationManager.getApplication().assertIsDispatchThread()
        return WindowManager.getInstance().getStatusBar(project) as StatusBarEx
    }

    @JvmStatic
    fun visible(
        project: Project,
        title: String,
    ): Boolean =
        status(project).backgroundProcessModels.any { (_, model) ->
            model.title.contains(title) && model.isRunning() && model.isCancellable()
        }

    @JvmStatic
    fun show(project: Project) {
        status(project).isProcessWindowOpen = true
    }

    @JvmStatic
    fun hide(project: Project) {
        status(project).isProcessWindowOpen = false
    }

    @JvmStatic
    fun cancel(
        project: Project,
        title: String,
    ): Boolean {
        val model =
            status(project)
                .backgroundProcessModels
                .map { it.second }
                .singleOrNull { it.title.contains(title) && it.isRunning() && it.isCancellable() } ?: return false
        val control =
            Window
                .getWindows()
                .asSequence()
                .filter { it.isShowing }
                .flatMap { UIUtil.uiTraverser(it).filter(JProgressBar::class.java).asSequence() }
                // The current IDE uses ProgressPanel, not ProgressComponent's legacy child panel.
                // Match the unique displayed title to the project's running model; heavyweight
                // popups can use Swing's shared owner frame rather than the project frame.
                .mapNotNull(ProgressPanel::getProgressPanel)
                .filter { it.labelText == model.title }
                .mapNotNull { it.cancelButton }
                .filter { it.isShowing && it.isEnabled }
                .distinct()
                .singleOrNull() ?: return false
        return control.accessibleContext.accessibleAction.doAccessibleAction(0)
    }

    @JvmStatic
    fun alive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
}
