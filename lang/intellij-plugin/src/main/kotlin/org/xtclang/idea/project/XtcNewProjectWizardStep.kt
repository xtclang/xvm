package org.xtclang.idea.project

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import org.xvm.tool.XtcProjectCreator
import kotlin.io.path.Path

/**
 * XTC-specific wizard step. Uses base data (name/path) from NewProjectWizardBaseStep
 * in the chain, and adds XTC-specific options. Uses XtcProjectCreator directly
 * (synced from javatools and compiled for Java 21).
 */
class XtcNewProjectWizardStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

    private val log = logger<XtcNewProjectWizardStep>()

    private val projectTypeProperty = propertyGraph.property(XtcProjectCreator.ProjectType.APPLICATION)
    private val multiModuleProperty = propertyGraph.property(false)
    var projectType: XtcProjectCreator.ProjectType by projectTypeProperty
    var multiModule: Boolean by multiModuleProperty

    override fun setupUI(builder: Panel) {
        with(builder) {
            row("Project type:") {
                comboBox(XtcProjectCreator.ProjectType.entries.toList()).bindItem(projectTypeProperty)
            }
            row { checkBox("Multi-module project").bindSelected(multiModuleProperty) }
        }
    }

    override fun setupProject(project: Project) {
        val base = baseData ?: run {
            log.error("No base data available")
            return
        }

        val projectPath = Path(base.path).resolve(base.name)

        // Get XTC version from the plugin's own version (matches published artifacts)
        val xtcVersion = PluginManagerCore.getPlugin(PluginId.getId("org.xtclang.idea"))?.version
            ?: XtcProjectCreator.DEFAULT_XTC_VERSION

        log.info("Creating XTC project: path=$projectPath, type=$projectType, multiModule=$multiModule, xtcVersion=$xtcVersion")

        val creator = XtcProjectCreator(projectPath, projectType, multiModule, xtcVersion, null)
        val result = creator.create()

        if (result.success()) {
            log.info(result.message())
        } else {
            log.error("Failed to create XTC project: ${result.message()}")
            Messages.showErrorDialog(
                "Failed to create XTC project: ${result.message()}",
                "XTC Project Creation Failed"
            )
        }
    }
}

