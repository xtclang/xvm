package org.xtclang.idea.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import org.jdom.Element
import kotlin.io.path.Path

/**
 * Run configuration for XTC applications.
 * Invokes `xtc run` or the Gradle `xtcRun` task.
 */
class XtcRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<Any>(project, factory, name) {

    var moduleName = ""
    var programArguments = ""
    var useGradle = true

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = XtcRunSettingsEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        object : CommandLineState(environment) {
            override fun startProcess() = OSProcessHandler(
                when {
                    useGradle -> createGradleCommandLine()
                    else -> createXtcCommandLine()
                }
            )
        }

    private fun createGradleCommandLine() = GeneralCommandLine().apply {
        exePath = when {
            "win" in System.getProperty("os.name").lowercase() -> "gradlew.bat"
            else -> "./gradlew"
        }
        addParameter("xtcRun")
        programArguments.takeIf { it.isNotBlank() }?.let { addParameter("--args=$it") }
        workDirectory = project.basePath?.let { Path(it).toFile() }
    }

    private fun createXtcCommandLine() = GeneralCommandLine().apply {
        exePath = "xtc"
        addParameter("run")
        moduleName.takeIf { it.isNotBlank() }?.let { addParameter(it) }
        programArguments.split(" ").filter { it.isNotBlank() }.forEach(::addParameter)
        workDirectory = project.basePath?.let { Path(it).toFile() }
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        moduleName = element.getAttributeValue("moduleName").orEmpty()
        programArguments = element.getAttributeValue("programArguments").orEmpty()
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
 * Settings editor for XTC run configuration using Kotlin UI DSL.
 */
class XtcRunSettingsEditor : SettingsEditor<XtcRunConfiguration>() {

    private var moduleName = ""
    private var programArguments = ""
    private var useGradle = true

    override fun createEditor() = panel {
        row("Module name:") { textField().bindText(::moduleName) }
        row("Program arguments:") { textField().bindText(::programArguments) }
        row { checkBox("Use Gradle (recommended)").bindSelected(::useGradle) }
    }

    override fun applyEditorTo(config: XtcRunConfiguration) {
        config.moduleName = moduleName
        config.programArguments = programArguments
        config.useGradle = useGradle
    }

    override fun resetEditorFrom(config: XtcRunConfiguration) {
        moduleName = config.moduleName
        programArguments = config.programArguments
        useGradle = config.useGradle
    }
}
