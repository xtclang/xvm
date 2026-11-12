package org.xtclang.idea.playbook.probe

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.UIUtil
import org.xtclang.idea.lsp.CompilerBuildModel
import org.xtclang.idea.lsp.CompilerProjectConfigurable
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

/** Configure the shipping UI; the second IDE launch reads its normal persisted settings. */
object SettingsPersistencePage {
    @JvmStatic
    fun sources(
        project: Project,
        module: String,
        uri: String,
    ) {
        val page = CompilerProjectConfigurable(project)
        try {
            val component = page.createComponent()
            UIUtil
                .uiTraverser(component)
                .filter(JBCheckBox::class.java)
                .single { it.text == "Use Gradle model or automatic source discovery" }
                .isSelected = false
            val model =
                UIUtil
                    .uiTraverser(component)
                    .filter(JTable::class.java)
                    .single { it.name == "Ecstasy source modules" }
                    .model as DefaultTableModel
            model.rowCount = 0
            model.addRow(arrayOf(module, uri, "", "[]"))
            page.apply()
        } finally {
            page.disposeUIResources()
        }
    }

    @JvmStatic
    fun rejectUntrustedImport(project: Project) {
        val trusted = TrustedProjects.isProjectTrusted(project)
        val before = CompilerBuildModel.read(project)
        try {
            TrustedProjects.setProjectTrusted(project, false)
            var refusal: String? = null
            CompilerBuildModel.refresh(project, true) { refusal = it }
            check(refusal?.contains("Trust this project") == true)
            check(CompilerBuildModel.read(project) == before)
        } finally {
            TrustedProjects.setProjectTrusted(project, trusted)
        }
    }
}
