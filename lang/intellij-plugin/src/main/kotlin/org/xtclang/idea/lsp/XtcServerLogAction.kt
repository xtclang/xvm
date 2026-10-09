package org.xtclang.idea.lsp

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import com.redhat.devtools.lsp4ij.LanguageServersRegistry
import com.redhat.devtools.lsp4ij.console.LSPConsoleToolWindowPanel
import com.redhat.devtools.lsp4ij.ui.LSPToolWindowId

/** Reuse the connection's log console, including output retained after a server failure. */
class XtcServerLogAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val window =
            ToolWindowManager.getInstance(project).getToolWindow(LSPToolWindowId.LANGUAGE_SERVERS)
                ?: return
        if (window.isVisible) {
            window.hide()
        } else {
            val definition =
                LanguageServersRegistry
                    .getInstance()
                    .getServerDefinition(CompilerSettings.SERVER_ID) ?: return
            window.activate { LSPConsoleToolWindowPanel.selectLogTab(definition, project) }
        }
    }
}
