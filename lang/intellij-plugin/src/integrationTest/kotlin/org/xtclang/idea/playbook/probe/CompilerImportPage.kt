package org.xtclang.idea.playbook.probe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.xtclang.idea.lsp.CompilerBuildModel
import org.xtclang.idea.lsp.CompilerProjectConfigurable
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTabbedPane
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
        (component as JTabbedPane).selectedIndex = component.indexOfTab("Build import")
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

    fun sync() {
        val root = requireNotNull(project.basePath)
        val settings = GradleSettings.getInstance(project)
        if (settings.getLinkedProjectSettings(root) == null) {
            settings.linkProject(GradleProjectSettings().apply { externalProjectPath = root })
        }
        ExternalSystemUtil.refreshProject(
            root,
            ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
                .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
                .withImportProjectData(false),
        )
    }

    fun unlink() {
        GradleSettings.getInstance(project).unlinkExternalProject(requireNotNull(project.basePath))
    }

    fun refreshReports() {
        WriteIntentReadAction.run {
            LocalFileSystem.getInstance().refreshAndFindFileByPath("${project.basePath}/.gradle/xtc")?.refresh(false, true)
        }
    }

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
