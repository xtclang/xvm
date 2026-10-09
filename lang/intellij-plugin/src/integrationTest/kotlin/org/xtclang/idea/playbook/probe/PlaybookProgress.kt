package org.xtclang.idea.playbook.probe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.WindowManager
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel

/** Persistent progress belongs only to the disposable test IDE, never the shipping plugin. */
object PlaybookProgress {
    private const val ID = "XtcPlaybookProgress"

    @JvmStatic
    fun install(project: Project) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val windows = WindowManager.getInstance()
        windows
            .getStatusBar(project)
            ?.addWidget(Widget(windows.getFrame(project)), "before Position", project)
    }

    @JvmStatic
    fun update(
        project: Project,
        text: String,
        detail: String,
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val status = requireNotNull(WindowManager.getInstance().getStatusBar(project))
        (status.getWidget(ID) as Widget).show(text, detail)
        status.updateWidget(ID)
    }

    @JvmStatic
    fun focus(
        project: Project,
        restoring: Boolean,
    ) {
        val widget = WindowManager.getInstance().getStatusBar(project)?.getWidget(ID) as Widget
        val text = widget.label.text.substringBefore(" [focus:")
        update(project, text + if (restoring) " [focus: restoring]" else "", widget.label.toolTipText)
    }

    @JvmStatic
    fun text(project: Project): String = (WindowManager.getInstance().getStatusBar(project)?.getWidget(ID) as Widget).label.text

    private class Widget(
        private val frame: JFrame?,
    ) : CustomStatusBarWidget {
        val label = JLabel("Ecstasy playbook: starting").apply { toolTipText = text }
        private val originalTitle = frame?.title
        private val titleListener = PropertyChangeListener { updateTitle() }

        init {
            frame?.addPropertyChangeListener("title", titleListener)
            updateTitle()
        }

        fun show(
            text: String,
            detail: String,
        ) {
            label.text = text
            label.toolTipText = detail
            updateTitle()
        }

        private fun updateTitle() {
            if (frame != null && frame.title != label.text) frame.title = label.text
        }

        override fun ID(): String = ID

        override fun getComponent(): JComponent = label

        override fun dispose() {
            frame?.removePropertyChangeListener("title", titleListener)
            if (frame != null) frame.title = originalTitle
        }
    }
}
