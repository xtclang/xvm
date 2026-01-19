package org.xtclang.idea.project

import com.intellij.execution.RunManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import org.xtclang.idea.run.XtcRunConfiguration
import org.xtclang.idea.run.XtcRunConfigurationType
import org.xvm.tool.XtcProjectCreator
import kotlin.io.path.Path

/**
 * XTC-specific wizard step. Uses base data (name/path) from NewProjectWizardBaseStep
 * in the chain, and adds XTC-specific options. Uses XtcProjectCreator directly
 * (synced from javatools and compiled for Java 21).
 */
class XtcNewProjectWizardStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

    private val logger = logger<XtcNewProjectWizardStep>()

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
            logger.error("No base data available")
            return
        }

        val projectPath = Path(base.path).resolve(base.name)

        // Get XTC version from the plugin's own version (matches published artifacts)
        val xtcVersion = PluginManagerCore.getPlugin(PluginId.getId("org.xtclang.idea"))?.version
            ?: XtcProjectCreator.DEFAULT_XTC_VERSION

        logger.info("Creating XTC project: path=$projectPath, type=$projectType, multiModule=$multiModule, xtcVersion=$xtcVersion")

        val creator = XtcProjectCreator(projectPath, projectType, multiModule, xtcVersion, null)
        val result = creator.create()

        if (result.success()) {
            logger.info(result.message())

            // Refresh VFS to pick up the newly created files
            // This prevents assertion failures in Project View when it tries to display the new files
            val projectDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectPath)
            if (projectDir != null) {
                VfsUtil.markDirtyAndRefresh(false, true, true, projectDir)
                logger.info("Refreshed VFS for project directory: $projectPath")
            } else {
                logger.warn("Could not find project directory in VFS: $projectPath")
            }

            createDefaultRunConfiguration(project, base.name)
        } else {
            logger.error("Failed to create XTC project: ${result.message()}")
            Messages.showErrorDialog(
                "Failed to create XTC project: ${result.message()}",
                "XTC Project Creation Failed"
            )
        }
    }

    /**
     * Create a default run configuration for the main module.
     * The module name is derived from the project name (same as XtcProjectCreator).
     */
    private fun createDefaultRunConfiguration(project: Project, projectName: String) {
        try {
            val runManager = RunManager.getInstance(project)
            val configurationType = XtcRunConfigurationType()
            val factory = configurationType.configurationFactories.first()

            // Module name is the project name (matches what XtcProjectCreator generates)
            val moduleName = projectName.trim().trimEnd('.')
            val settings = runManager.createConfiguration("Run $moduleName", factory)
            val config = settings.configuration as XtcRunConfiguration
            config.moduleName = moduleName
            config.useGradle = true

            runManager.addConfiguration(settings)
            runManager.selectedConfiguration = settings
            logger.info("Created default run configuration for module: $moduleName")
        } catch (e: Exception) {
            logger.warn("Failed to create default run configuration: ${e.message}")
        }
    }
}
