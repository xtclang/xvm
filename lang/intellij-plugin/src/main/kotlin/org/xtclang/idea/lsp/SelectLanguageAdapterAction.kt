package org.xtclang.idea.lsp

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.xtclang.idea.XtcProjectAction

/** The existing settings listener owns restarting and reopening the project's documents. */
class SelectLanguageAdapterAction : XtcProjectAction() {
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
