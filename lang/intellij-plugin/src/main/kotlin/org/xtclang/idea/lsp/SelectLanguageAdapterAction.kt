package org.xtclang.idea.lsp

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory

/** The existing settings listener owns restarting and reopening the project's documents. */
class SelectLanguageAdapterAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val selected = LanguageAdapter.fromSetting(LanguageServiceSettings.validated(project).adapter)
        JBPopupFactory
            .getInstance()
            .createPopupChooserBuilder(LanguageAdapter.entries)
            .setTitle("Ecstasy Language Adapter (restarts server)")
            .setSelectedValue(selected, true)
            .setItemChosenCallback { adapter ->
                if (!project.isDisposed && LanguageServiceSettings.effective(project).adapter != adapter.setting) {
                    val content = LanguageServiceConfiguration.selectAdapter(LanguageServiceSettings.content(project), adapter)
                    LanguageServiceSettings.install(project, content)
                }
            }.createPopup()
            .showInBestPositionFor(event.dataContext)
    }
}
