package org.xtclang.idea.project

import com.intellij.facet.ui.ValidationResult
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.GeneratorPeerImpl
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.xtclang.idea.XtcIcons
import java.io.File
import javax.swing.JComponent

/**
 * Project generator for the New Project wizard.
 * Creates XTC projects by invoking the `xtc init` CLI command.
 */
class XtcProjectGenerator : DirectoryProjectGenerator<XtcProjectSettings> {

    override fun getName() = "XTC"

    override fun getDescription() = "Create a new XTC (Ecstasy) project"

    override fun getLogo() = XtcIcons.FILE

    override fun createPeer(): ProjectGeneratorPeer<XtcProjectSettings> = XtcProjectGeneratorPeer()

    override fun generateProject(
        project: Project,
        baseDir: VirtualFile,
        settings: XtcProjectSettings,
        module: Module
    ) {
        val projectDir = File(baseDir.path)
        val projectName = settings.projectName.ifEmpty { projectDir.name }

        // Build the xtc init command
        val cmd = mutableListOf("xtc", "init", projectName)

        if (settings.projectType != XtcProjectSettings.ProjectType.APPLICATION) {
            cmd.add("--type=${settings.projectType.cliValue}")
        }

        if (settings.multiModule) {
            cmd.add("--multi-module")
        }

        // Execute xtc init in the parent directory
        val parentDir = projectDir.parentFile ?: projectDir
        val process = ProcessBuilder(cmd)
            .directory(parentDir)
            .redirectErrorStream(true)
            .start()

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            val output = process.inputStream.bufferedReader().readText()
            throw RuntimeException("Failed to create XTC project: $output")
        }

        // Refresh the VFS to see the generated files
        baseDir.refresh(false, true)
    }

    override fun validate(baseDirPath: String): ValidationResult {
        val dir = File(baseDirPath)
        if (dir.exists() && dir.listFiles()?.isNotEmpty() == true) {
            return ValidationResult("Directory is not empty")
        }
        return ValidationResult.OK
    }
}

/**
 * Peer for collecting project settings in the wizard using Kotlin UI DSL v2.
 */
class XtcProjectGeneratorPeer : GeneratorPeerImpl<XtcProjectSettings>() {

    private var settings = XtcProjectSettings()

    override fun getSettings(): XtcProjectSettings = settings

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getComponent(): JComponent {
        return panel {
            row("Project type:") {
                comboBox(XtcProjectSettings.ProjectType.entries)
                    .bindItem(
                        getter = { settings.projectType },
                        setter = { settings = settings.copy(projectType = it ?: XtcProjectSettings.ProjectType.APPLICATION) }
                    )
            }
            row {
                checkBox("Multi-module project")
                    .bindSelected(
                        getter = { settings.multiModule },
                        setter = { settings = settings.copy(multiModule = it) }
                    )
            }
        }
    }

    override fun buildUI(settingsStep: SettingsStep) {
        settingsStep.addSettingsComponent(component)
    }

    override fun validate(): ValidationInfo? = null

    override fun isBackgroundJobRunning() = false
}
