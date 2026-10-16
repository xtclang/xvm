package org.xtclang.idea.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import org.jdom.Element
import org.xtclang.idea.lsp.RunParams
import kotlin.io.path.Path

/** What a run configuration runs with; [PROJECT] follows the project's [XtcBuildSettings]. */
enum class XtcRunMode(
    val label: String,
) {
    PROJECT("Project default"),
    EMBEDDED("Embedded compiler"),
    GRADLE("Gradle"),
    COMMAND_LINE("xtc command line"),
    ;

    override fun toString() = label
}

/**
 * Run configuration for XTC applications. Runs with the embedded compiler and execution worker, the Gradle
 * `runXtc` task, or `xtc run`.
 */
class XtcRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<Any>(project, factory, name) {
    private val logger = logger<XtcRunConfiguration>()

    var moduleName = ""
    var moduleUri = "" // A source of the module; identifies it for embedded runs
    var methodName = "" // Empty means use default "run"
    var moduleArguments = "" // Comma-separated arguments passed to the module
    var runWith = XtcRunMode.PROJECT
    var quietMode = true // Use -q flag for less verbose Gradle output

    /** The mode this configuration runs with now: never [XtcRunMode.PROJECT]. */
    fun effectiveRunMode(): XtcRunMode =
        when (runWith) {
            XtcRunMode.PROJECT -> {
                if (XtcBuildSettings.effective(project) == XtcBuildMode.GRADLE) XtcRunMode.GRADLE else XtcRunMode.EMBEDDED
            }

            else -> {
                runWith
            }
        }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = XtcRunSettingsEditor()

    override fun checkConfiguration() {
        if (effectiveRunMode() == XtcRunMode.EMBEDDED && moduleName.isBlank() && moduleUri.isBlank()) {
            throw RuntimeConfigurationError("An embedded run needs the module name")
        }
    }

    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment,
    ): RunProfileState =
        when (val mode = effectiveRunMode()) {
            XtcRunMode.EMBEDDED -> {
                EmbeddedRunState(environment, embeddedRequest(), moduleName.ifBlank { moduleUri })
            }

            else -> {
                object : CommandLineState(environment) {
                    override fun startProcess() =
                        OSProcessHandler(
                            if (mode == XtcRunMode.GRADLE) createGradleCommandLine() else createXtcCommandLine(),
                        )
                }
            }
        }

    /**
     * The module's source URI, or else its name; the server accepts either, and runs in the folder that owns
     * the module, as Gradle runs do. A source identifies the module exactly, whatever its qualified name.
     */
    internal fun embeddedRequest() =
        RunParams(
            module = moduleUri.ifBlank { moduleName },
            method = methodName.ifBlank { null },
            args = moduleArguments.split(",").map(String::trim).filter(String::isNotEmpty),
        )

    private fun createGradleCommandLine() =
        GeneralCommandLine().apply {
            exePath =
                when {
                    "win" in System.getProperty("os.name").lowercase() -> "gradlew.bat"
                    else -> "./gradlew"
                }
            if (quietMode) addParameter("-q")
            addParameter("runXtc")
            // Pass configuration as Gradle task options (--module, --method, --args)
            moduleName.takeIf { it.isNotBlank() }?.let { addParameter("--module=$it") }
            methodName.takeIf { it.isNotBlank() }?.let { addParameter("--method=$it") }
            moduleArguments.takeIf { it.isNotBlank() }?.let { addParameter("--args=$it") }
            workDirectory = project.basePath?.let { Path(it).toFile() }
            logger.info("Running Gradle: $commandLineString")
        }

    private fun createXtcCommandLine() =
        GeneralCommandLine().apply {
            exePath = "xtc"
            addParameter("run")
            moduleName.takeIf { it.isNotBlank() }?.let { addParameter(it) }
            moduleArguments.split(",").filter { it.isNotBlank() }.forEach(::addParameter)
            workDirectory = project.basePath?.let { Path(it).toFile() }
            logger.info("Running Ecstasy: $commandLineString")
        }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        moduleName = element.getAttributeValue("moduleName").orEmpty()
        moduleUri = element.getAttributeValue("moduleUri").orEmpty()
        methodName = element.getAttributeValue("methodName").orEmpty()
        moduleArguments = element.getAttributeValue("moduleArguments").orEmpty()
        runWith = runMode(element.getAttributeValue("runWith"), element.getAttributeValue("useGradle"))
        quietMode = element.getAttributeValue("quietMode")?.toBoolean() ?: true
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("moduleName", moduleName)
        element.setAttribute("moduleUri", moduleUri)
        element.setAttribute("methodName", methodName)
        element.setAttribute("moduleArguments", moduleArguments)
        element.setAttribute("runWith", runWith.name)
        element.setAttribute("quietMode", quietMode.toString())
    }

    internal companion object {
        /**
         * Configurations saved before [runWith] carry `useGradle`. Gradle was the default for every new
         * configuration, so it becomes the project default, which is Gradle in a Gradle project.
         */
        fun runMode(
            runWith: String?,
            useGradle: String?,
        ): XtcRunMode =
            runWith?.let { name -> XtcRunMode.entries.firstOrNull { it.name == name } }
                ?: if (useGradle?.toBoolean() == false) XtcRunMode.COMMAND_LINE else XtcRunMode.PROJECT
    }
}

/** Runs [request] in the language server's execution worker, in a console with Stop and Rerun. */
internal class EmbeddedRunState(
    private val environment: ExecutionEnvironment,
    private val request: RunParams,
    private val label: String,
) : RunProfileState {
    override fun execute(
        executor: Executor,
        runner: ProgramRunner<*>,
    ): ExecutionResult {
        val project = environment.project
        EmbeddedExecution.saveAll()
        val console =
            TextConsoleBuilderFactory
                .getInstance()
                .createBuilder(project)
                .apply { addFilter(DiagnosticLinks(project)) }
                .console
        val handler =
            EmbeddedRunProcessHandler(
                EmbeddedRunSessions.getInstance(project),
                { EmbeddedExecution.server(project) },
                request,
                label,
                XtcBuildSettings.gradleNotUsed(project.basePath?.let { Path(it) }),
            )
        ProcessTerminatedListener.attach(handler)
        console.attachToProcess(handler)
        return DefaultExecutionResult(console, handler)
    }
}

/** Settings editor for XTC run configuration using Kotlin UI DSL. */
class XtcRunSettingsEditor : SettingsEditor<XtcRunConfiguration>() {
    private var moduleName = ""
    private var moduleUri = ""
    private var methodName = ""
    private var moduleArguments = ""
    private var runWith = XtcRunMode.PROJECT
    private var quietMode = true

    private val editorPanel by lazy {
        panel {
            row("Module name:") {
                textField()
                    .bindText(::moduleName)
                    .comment(
                        "The Ecstasy (.xtc) module to run (overrides build.gradle.kts default)",
                    )
            }
            row("Module source:") {
                textField()
                    .bindText(::moduleUri)
                    .comment("Optional source file URI; identifies the module for embedded runs, ahead of its name")
            }
            row("Method name:") {
                textField()
                    .bindText(::methodName)
                    .comment("Method to invoke (leave empty for default 'run')")
            }
            row("Module arguments:") {
                textField()
                    .bindText(::moduleArguments)
                    .comment("Comma-separated arguments passed to the module")
            }
            row("Run with:") {
                comboBox(XtcRunMode.entries.toList())
                    .bindItem({ runWith }, { runWith = it ?: XtcRunMode.PROJECT })
                    .comment("The project default is set in Settings | Build, Execution, Deployment | Ecstasy")
            }
            row { checkBox("Quiet mode (-q, Gradle only)").bindSelected(::quietMode) }
        }
    }

    override fun createEditor() = editorPanel

    override fun applyEditorTo(config: XtcRunConfiguration) {
        editorPanel.apply() // Apply UI values to backing properties
        config.moduleName = moduleName
        config.moduleUri = moduleUri
        config.methodName = methodName
        config.moduleArguments = moduleArguments
        config.runWith = runWith
        config.quietMode = quietMode
    }

    override fun resetEditorFrom(config: XtcRunConfiguration) {
        moduleName = config.moduleName
        moduleUri = config.moduleUri
        methodName = config.methodName
        moduleArguments = config.moduleArguments
        runWith = config.runWith
        quietMode = config.quietMode
        editorPanel.reset() // Reset UI from backing properties
    }
}
