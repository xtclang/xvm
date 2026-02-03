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
 * XTC-specific wizard step for the New Project wizard.
 * Uses XtcProjectCreator (synced from javatools, compiled for Java 21).
 */
class XtcNewProjectWizardStep(
    parent: NewProjectWizardStep,
) : AbstractNewProjectWizardStep(parent) {
    private val logger = logger<XtcNewProjectWizardStep>()

    private val projectTypeProperty = propertyGraph.property(XtcProjectCreator.ProjectType.APPLICATION)
    private val multiModuleProperty = propertyGraph.property(false)

    var projectType: XtcProjectCreator.ProjectType by projectTypeProperty
    var multiModule: Boolean by multiModuleProperty

    override fun setupUI(builder: Panel) {
        builder.apply {
            row("Project type:") {
                comboBox(XtcProjectCreator.ProjectType.entries.toList()).bindItem(projectTypeProperty)
            }
            row {
                checkBox("Multi-module project").bindSelected(multiModuleProperty)
            }
        }
    }

    override fun setupProject(project: Project) {
        val base = baseData ?: return logger.error("No base data available")
        val projectPath = Path(base.path).resolve(base.name)
        val xtcVersion =
            PluginManagerCore.getPlugin(PluginId.getId("org.xtclang.idea"))?.version
                ?: XtcProjectCreator.DEFAULT_XTC_VERSION

        logger.info("Creating XTC project: path=$projectPath, type=$projectType, multiModule=$multiModule, xtcVersion=$xtcVersion")

        val creator = XtcProjectCreator(projectPath, projectType, multiModule, xtcVersion, null)
        val result = creator.create()

        when {
            result.success -> {
                logger.info(result.message)
                refreshVfs(projectPath)
                createDefaultRunConfiguration(project, base.name)
            }
            else -> {
                logger.error("Failed to create XTC project: ${result.message}")
                Messages.showErrorDialog("Failed to create XTC project: ${result.message}", "XTC Project Creation Failed")
            }
        }
    }

    private fun refreshVfs(projectPath: java.nio.file.Path) {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectPath)?.let { projectDir ->
            VfsUtil.markDirtyAndRefresh(false, true, true, projectDir)
            logger.info("Refreshed VFS for project directory: $projectPath")
        } ?: logger.warn("Could not find project directory in VFS: $projectPath")
    }

    private fun createDefaultRunConfiguration(
        project: Project,
        projectName: String,
    ) {
        runCatching {
            val moduleName = projectName.trim().trimEnd('.')
            val runManager = RunManager.getInstance(project)
            val factory = XtcRunConfigurationType().configurationFactories.first()
            val settings = runManager.createConfiguration("Run $moduleName", factory)

            (settings.configuration as XtcRunConfiguration).apply {
                this.moduleName = moduleName
                useGradle = true
            }

            runManager.addConfiguration(settings)
            runManager.selectedConfiguration = settings
            logger.info("Created default run configuration for module: $moduleName")
        }.onFailure { e ->
            logger.warn("Failed to create default run configuration: ${e.message}")
        }
    }
}
