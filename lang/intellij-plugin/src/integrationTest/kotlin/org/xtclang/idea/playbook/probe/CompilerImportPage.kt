package org.xtclang.idea.playbook.probe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.UIUtil
import org.xtclang.idea.lsp.CompilerBuildModel
import org.xtclang.idea.lsp.CompilerProjectConfigurable
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextArea

/** Display the shipping Compiler settings component; activate its buttons without pointer input. */
class CompilerImportPage private constructor(
    private val project: Project,
) : DialogWrapper(project, false) {
    private val page = CompilerProjectConfigurable(project)
    private val component = page.createComponent()

    init {
        title = "Ecstasy Compiler — import playbook"
        isModal = false
        init()
    }

    override fun createCenterPanel(): JComponent = component

    fun click(label: String) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        UIUtil
            .uiTraverser(component)
            .filter(JButton::class.java)
            .single { it.text == label }
            .also { check(it.isShowing && it.isEnabled) }
            .doClick()
    }

    fun text(): String {
        ApplicationManager.getApplication().assertIsDispatchThread()
        return requireNotNull(UIUtil.uiTraverser(component).filter(JTextArea::class.java).single()).text
    }

    fun accepted(): String = CompilerBuildModel.read(project).toString()

    fun description(): String = CompilerBuildModel.describe(project)

    fun resetPage() = page.reset()

    fun closePage() = close(CANCEL_EXIT_CODE)

    override fun dispose() {
        page.disposeUIResources()
        super.dispose()
    }

    companion object {
        @JvmStatic
        fun open(project: Project): CompilerImportPage = CompilerImportPage(project).also { it.show() }
    }
}
