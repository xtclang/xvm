package org.xtclang.idea.playbook.probe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx

/** Observe and cancel the real status-bar progress model, without synthetic server messages. */
object ProgressUi {
    private fun status(project: Project): StatusBarEx {
        ApplicationManager.getApplication().assertIsDispatchThread()
        return WindowManager.getInstance().getStatusBar(project) as StatusBarEx
    }

    @JvmStatic
    fun visible(project: Project, title: String): Boolean =
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
    fun cancel(project: Project, title: String): Boolean {
        val model =
            status(project)
                .backgroundProcessModels
                .singleOrNull { (_, model) ->
                    model.title.contains(title) && model.isRunning() && model.isCancellable()
                }
                ?.second ?: return false
        model.cancel()
        return true
    }

    @JvmStatic
    fun alive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
}
