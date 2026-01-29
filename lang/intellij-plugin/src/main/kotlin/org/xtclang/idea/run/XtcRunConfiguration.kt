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
 * Invokes `xtc run` or the Gradle `runXtc` task.
 */
class XtcRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<Any>(project, factory, name) {

    var moduleName = ""
    var methodName = ""  // Empty means use default "run"
    var moduleArguments = ""  // Comma-separated arguments passed to the module
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
        addParameter("runXtc")
        // Pass configuration as Gradle task options (--module, --method, --args)
        moduleName.takeIf { it.isNotBlank() }?.let { addParameter("--module=$it") }
        methodName.takeIf { it.isNotBlank() }?.let { addParameter("--method=$it") }
        moduleArguments.takeIf { it.isNotBlank() }?.let { addParameter("--args=$it") }
        workDirectory = project.basePath?.let { Path(it).toFile() }
    }

    private fun createXtcCommandLine() = GeneralCommandLine().apply {
        exePath = "xtc"
        addParameter("run")
        moduleName.takeIf { it.isNotBlank() }?.let { addParameter(it) }
        moduleArguments.split(",").filter { it.isNotBlank() }.forEach(::addParameter)
        workDirectory = project.basePath?.let { Path(it).toFile() }
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        moduleName = element.getAttributeValue("moduleName").orEmpty()
        methodName = element.getAttributeValue("methodName").orEmpty()
        moduleArguments = element.getAttributeValue("moduleArguments").orEmpty()
        useGradle = element.getAttributeValue("useGradle")?.toBoolean() ?: true
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("moduleName", moduleName)
        element.setAttribute("methodName", methodName)
        element.setAttribute("moduleArguments", moduleArguments)
        element.setAttribute("useGradle", useGradle.toString())
    }
}

/**
 * Settings editor for XTC run configuration using Kotlin UI DSL.
 */
class XtcRunSettingsEditor : SettingsEditor<XtcRunConfiguration>() {

    private var moduleName = ""
    private var methodName = ""
    private var moduleArguments = ""
    private var useGradle = true

    private val editorPanel by lazy {
        panel {
            row("Module name:") {
                textField().bindText(::moduleName)
                    .comment("The XTC module to run (overrides build.gradle.kts default)")
            }
            row("Method name:") {
                textField().bindText(::methodName)
                    .comment("Method to invoke (leave empty for default 'run')")
            }
            row("Module arguments:") {
                textField().bindText(::moduleArguments)
                    .comment("Comma-separated arguments passed to the module")
            }
            row { checkBox("Use Gradle (recommended)").bindSelected(::useGradle) }
        }
    }

    override fun createEditor() = editorPanel

    override fun applyEditorTo(config: XtcRunConfiguration) {
        editorPanel.apply()  // Apply UI values to backing properties
        config.moduleName = moduleName
        config.methodName = methodName
        config.moduleArguments = moduleArguments
        config.useGradle = useGradle
    }

    override fun resetEditorFrom(config: XtcRunConfiguration) {
        moduleName = config.moduleName
        methodName = config.methodName
        moduleArguments = config.moduleArguments
        useGradle = config.useGradle
        editorPanel.reset()  // Reset UI from backing properties
    }
}
