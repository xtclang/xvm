package org.xtclang.idea.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element
import javax.swing.*

/**
 * Run configuration for XTC applications.
 * Invokes `xtc run` or the Gradle `xtcRun` task.
 */
class XtcRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<Any>(project, factory, name) {

    var moduleName: String = ""
    var programArguments: String = ""
    var useGradle: Boolean = true

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return XtcRunSettingsEditor()
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                val commandLine = if (useGradle) {
                    createGradleCommandLine()
                } else {
                    createXtcCommandLine()
                }
                return OSProcessHandler(commandLine)
            }
        }
    }

    private fun createGradleCommandLine(): GeneralCommandLine {
        val cmd = GeneralCommandLine()
        cmd.exePath = if (System.getProperty("os.name").lowercase().contains("win")) {
            "gradlew.bat"
        } else {
            "./gradlew"
        }
        cmd.addParameter("xtcRun")
        if (programArguments.isNotBlank()) {
            cmd.addParameter("--args=$programArguments")
        }
        cmd.workDirectory = project.basePath?.let { java.io.File(it) }
        return cmd
    }

    private fun createXtcCommandLine(): GeneralCommandLine {
        val cmd = GeneralCommandLine()
        cmd.exePath = "xtc"
        cmd.addParameter("run")
        if (moduleName.isNotBlank()) {
            cmd.addParameter(moduleName)
        }
        if (programArguments.isNotBlank()) {
            programArguments.split(" ").forEach { cmd.addParameter(it) }
        }
        cmd.workDirectory = project.basePath?.let { java.io.File(it) }
        return cmd
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        moduleName = element.getAttributeValue("moduleName") ?: ""
        programArguments = element.getAttributeValue("programArguments") ?: ""
        useGradle = element.getAttributeValue("useGradle")?.toBoolean() ?: true
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("moduleName", moduleName)
        element.setAttribute("programArguments", programArguments)
        element.setAttribute("useGradle", useGradle.toString())
    }
}

/**
 * Settings editor for XTC run configuration.
 */
class XtcRunSettingsEditor : SettingsEditor<XtcRunConfiguration>() {

    private val moduleNameField = JTextField(30)
    private val programArgumentsField = JTextField(30)
    private val useGradleCheckbox = JCheckBox("Use Gradle (recommended)", true)

    override fun createEditor(): JComponent {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)

            add(JPanel().apply {
                add(JLabel("Module name:"))
                add(moduleNameField)
            })

            add(JPanel().apply {
                add(JLabel("Program arguments:"))
                add(programArgumentsField)
            })

            add(useGradleCheckbox)
        }
    }

    override fun applyEditorTo(config: XtcRunConfiguration) {
        config.moduleName = moduleNameField.text
        config.programArguments = programArgumentsField.text
        config.useGradle = useGradleCheckbox.isSelected
    }

    override fun resetEditorFrom(config: XtcRunConfiguration) {
        moduleNameField.text = config.moduleName
        programArgumentsField.text = config.programArguments
        useGradleCheckbox.isSelected = config.useGradle
    }
}
