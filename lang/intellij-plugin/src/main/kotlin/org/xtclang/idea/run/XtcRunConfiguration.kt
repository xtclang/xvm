package org.xtclang.idea.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.executors.DefaultDebugExecutor
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
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationBase
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition
import org.jdom.Element
import org.xtclang.idea.dap.XtcDebugAdapterFactory
import org.xtclang.idea.lsp.RunParams
import org.xtclang.idea.lsp.ServerTest
import org.xtclang.idea.lsp.TestSelector
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
 * `runXtc` task, or `xtc run`, and each can also be debugged: Debug is LSP4IJ's DAP client, which
 * [XtcDebugAdapterFactory] connects to a debug session in the language server.
 */
class XtcRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : DAPRunConfigurationBase<Any>(project, factory, name) {
    private val logger = logger<XtcRunConfiguration>()

    var moduleName = ""
    var moduleUri = "" // A source of the module; identifies it for embedded runs
    var methodName = "" // Empty means use default "run"
    var moduleArguments = "" // Comma-separated arguments passed to the module
    var runWith = XtcRunMode.PROJECT
    var quietMode = true // Use -q flag for less verbose Gradle output
    var stopOnEntry = false // Under Debug, the program stops at its first line
    var evaluationRunsProgram = false // While Evaluate Expression runs code under Debug, the rest of the program runs too

    /** The module's tests that xunit runs instead of its entry method, all of them for an empty list; null for none. */
    var tests: List<TestSelector>? = null

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
    ): RunProfileState? {
        // Debug is LSP4IJ's DAP client, whose session in the language server builds and runs as embedded runs do
        if (executor.id == DefaultDebugExecutor.EXECUTOR_ID) return super.getState(executor, environment)
        return when (val mode = effectiveRunMode()) {
            XtcRunMode.EMBEDDED -> {
                val label = moduleName.ifBlank { moduleUri }
                if (tests == null) {
                    EmbeddedRunState(environment, embeddedRequest(), label)
                } else {
                    EmbeddedTestState(this, environment, embeddedRequest(), label)
                }
            }

            else -> {
                val tests = tests
                if (tests != null) {
                    TestCommandLineState(
                        this,
                        environment,
                        if (mode ==
                            XtcRunMode.GRADLE
                        ) {
                            createGradleTestCommandLine(tests)
                        } else {
                            createXtcTestCommandLine(tests)
                        },
                    )
                } else {
                    object : CommandLineState(environment) {
                        override fun startProcess() =
                            OSProcessHandler(
                                if (mode == XtcRunMode.GRADLE) createGradleCommandLine() else createXtcCommandLine(),
                            )
                    }
                }
            }
        }
    }

    /**
     * Debug is offered with the embedded compiler, whose runs a debug session in the language server launches, and
     * with Gradle and `xtc run`, whose task or command runs the program under the session's debugger (see
     * [org.xtclang.idea.dap.DebugTasks]).
     */
    override fun canRun(executorId: String): Boolean = executorId == DefaultDebugExecutor.EXECUTOR_ID && effectiveRunMode() in DEBUGGABLE

    override fun getDebugAdapterServer(): DebugAdapterServerDefinition? =
        DebugAdapterManager.getInstance().getDebugAdapterServerById(XtcDebugAdapterFactory.SERVER_ID)

    internal fun embeddedRequest() = embeddedRequest(moduleName, moduleUri, methodName, moduleArguments, tests)

    private fun createGradleCommandLine() =
        GeneralCommandLine().apply {
            exePath = gradleWrapper()
            if (quietMode) addParameter("-q")
            addParameter("runXtc")
            // Pass configuration as Gradle task options (--module, --method, --args)
            moduleName.takeIf { it.isNotBlank() }?.let { addParameter("--module=$it") }
            methodName.takeIf { it.isNotBlank() }?.let { addParameter("--method=$it") }
            moduleArguments.takeIf { it.isNotBlank() }?.let { addParameter("--args=$it") }
            workDirectory = project.basePath?.let { Path(it).toFile() }
            logger.info("Running Gradle: $commandLineString")
        }

    /** `testXtc` for [tests] of the module, with xunit's TeamCity report, which the test console reads. */
    private fun createGradleTestCommandLine(tests: List<TestSelector>) =
        GeneralCommandLine().apply {
            exePath = gradleWrapper()
            if (quietMode) addParameter("-q")
            addParameter("testXtc")
            moduleName.takeIf { it.isNotBlank() }?.let { addParameter("--module=$it") }
            addParameters(gradleTestOptions(tests))
            workDirectory = project.basePath?.let { Path(it).toFile() }
            logger.info("Running Gradle tests: $commandLineString")
        }

    private fun createXtcTestCommandLine(tests: List<TestSelector>) =
        xtcTestCommandLine(moduleName, tests).apply {
            workDirectory = project.basePath?.let { Path(it).toFile() }
            logger.info("Running Ecstasy tests: $commandLineString")
        }

    private fun createXtcCommandLine() =
        xtcCommandLine(moduleName, methodName.ifBlank { null }, moduleArguments.split(",").filter { it.isNotBlank() }).apply {
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
        stopOnEntry = element.getAttributeValue("stopOnEntry")?.toBoolean() ?: false
        evaluationRunsProgram = element.getAttributeValue("evaluationRunsProgram")?.toBoolean() ?: false
        tests =
            element.getChild(TESTS)?.getChildren(TEST)?.map { test ->
                TestSelector(test.getAttributeValue(SELECTOR).orEmpty(), test.getAttributeValue(KIND) ?: ServerTest.TEST)
            }
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("moduleName", moduleName)
        element.setAttribute("moduleUri", moduleUri)
        element.setAttribute("methodName", methodName)
        element.setAttribute("moduleArguments", moduleArguments)
        element.setAttribute("runWith", runWith.name)
        element.setAttribute("quietMode", quietMode.toString())
        element.setAttribute("stopOnEntry", stopOnEntry.toString())
        element.setAttribute("evaluationRunsProgram", evaluationRunsProgram.toString())
        element.removeChild(TESTS)
        tests?.let { chosen ->
            element.addContent(
                Element(TESTS).apply {
                    chosen.forEach { addContent(Element(TEST).setAttribute(SELECTOR, it.selector).setAttribute(KIND, it.kind)) }
                },
            )
        }
    }

    internal companion object {
        /** How a configuration saves its tests: a `tests` element with a `test` for each, its selector and kind. */
        private const val TESTS = "tests"
        private const val TEST = "test"
        private const val SELECTOR = "selector"
        private const val KIND = "kind"

        /**
         * Tests as the settings editor shows them: `kind:selector`, comma-separated, such as `class:Inner,
         * test:Inner.works`, or `module:` for all of the module's tests; empty for none, an application run.
         */
        fun testsText(tests: List<TestSelector>?): String =
            when {
                tests == null -> ""
                tests.isEmpty() -> "${ServerTest.MODULE}:"
                else -> tests.joinToString(", ") { "${it.kind}:${it.selector}" }
            }

        /** The tests that [text], as [testsText] writes it, names; null when it names none. */
        fun testsOf(text: String): List<TestSelector>? =
            text
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { item ->
                    val kind = item.substringBefore(':', ServerTest.TEST).trim()
                    TestSelector(item.substringAfter(':', item).trim(), kind)
                }.takeIf { it.isNotEmpty() }
                ?.let { chosen -> if (chosen.any { it.kind == ServerTest.MODULE }) emptyList() else chosen }

        /** The run modes whose runs Debug can run under the debugger. */
        private val DEBUGGABLE = setOf(XtcRunMode.EMBEDDED, XtcRunMode.GRADLE, XtcRunMode.COMMAND_LINE)

        /** The project's Gradle wrapper, which Gradle runs go through. */
        fun gradleWrapper(): String = if ("win" in System.getProperty("os.name").lowercase()) "gradlew.bat" else "./gradlew"

        /**
         * `xtc run`'s command line, which command-line runs go through, Debug's too: the [module], which `xtc run` finds by
         * its name from the project's directory, there or, with `--deduce`, in `src/main/x`, as `xtc init` lays a project
         * out, with its resources and its compiled form in the project's `build` directory; its [method] when given, the
         * launcher's `--debug` options [debug] when given, and the module's [args].
         */
        fun xtcCommandLine(
            module: String,
            method: String?,
            args: List<String>,
            debug: String? = null,
        ) = GeneralCommandLine("xtc", "run", "--deduce").apply {
            method?.let { addParameters("--method", it) }
            debug?.let { addParameters("--debug", it) }
            module.takeIf { it.isNotBlank() }?.let(::addParameter)
            addParameters(args)
        }

        /** The xunit report that IntelliJ's test console reads, as `xtc test` and `testXtc` take its name. */
        private const val TEAMCITY = "teamcity"

        /** `testXtc`'s options that run [tests], all of the module's for none, with xunit's TeamCity report. */
        fun gradleTestOptions(tests: List<TestSelector>): List<String> =
            tests.mapNotNull { test -> selection(test.kind, "--test-package=", "--test-class=", "--test-method=")?.plus(test.selector) } +
                "--test-report=$TEAMCITY"

        /**
         * `xtc test`'s command line, as a command-line test run's: [module]'s [tests], all of them for none, which
         * `xtc test` finds by its name as `xtc run` does, with xunit's TeamCity report and the `--debug` options [debug].
         */
        fun xtcTestCommandLine(
            module: String,
            tests: List<TestSelector>,
            debug: String? = null,
        ) = GeneralCommandLine("xtc", "test", "--deduce").apply {
            tests.forEach { test -> selection(test.kind, "-p", "-c", "-t")?.let { addParameters(it, test.selector) } }
            addParameters("-I", "xvm.xunit.report=$TEAMCITY")
            debug?.let { addParameters("--debug", it) }
            module.takeIf { it.isNotBlank() }?.let(::addParameter)
        }

        /** The option of a test's [kind]: a package's, a class's or a test method's; none for a module, all of it. */
        private fun selection(
            kind: String,
            packages: String,
            classes: String,
            methods: String,
        ): String? =
            when (kind) {
                ServerTest.PACKAGE -> packages
                ServerTest.CLASS -> classes
                ServerTest.TEST -> methods
                else -> null
            }

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

        /**
         * The module's source URI, or else its name; the server accepts either, and runs in the folder that owns
         * the module, as Gradle runs do. A source identifies the module exactly, whatever its qualified name. The
         * name goes along with a URI: a saved configuration keeps its URI after the file is renamed or moved,
         * and the server then finds the module by its name.
         */
        fun embeddedRequest(
            moduleName: String,
            moduleUri: String,
            methodName: String,
            moduleArguments: String,
            tests: List<TestSelector>? = null,
        ) = RunParams(
            module = moduleUri.ifBlank { moduleName },
            method = methodName.ifBlank { null },
            args = moduleArguments.split(",").map(String::trim).filter(String::isNotEmpty),
            moduleName = moduleName.ifBlank { null }.takeIf { moduleUri.isNotBlank() },
            tests = tests,
        )
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
        val editsSent = EmbeddedExecution.editsSent(project)
        val console =
            TextConsoleBuilderFactory
                .getInstance()
                .createBuilder(project)
                .apply {
                    addFilter(DiagnosticLinks(project))
                    addFilter(BuildSettingsLink)
                }.console
        val handler =
            EmbeddedRunProcessHandler(
                EmbeddedRunSessions.getInstance(project),
                { EmbeddedExecution.server(project).thenCombine(editsSent) { server, _ -> server } },
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
    private var stopOnEntry = false
    private var evaluationRunsProgram = false
    private var tests = ""

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
            row("Tests:") {
                textField()
                    .bindText(::tests)
                    .comment(
                        "xunit tests to run instead of the module, as kind:selector, comma-separated, such as " +
                            "class:Inner, test:Inner.works; module: runs all of them. Empty runs the module",
                    )
            }
            row("Run with:") {
                comboBox(XtcRunMode.entries.toList())
                    .bindItem({ runWith }, { runWith = it ?: XtcRunMode.PROJECT })
                    .comment("The project default is set in Settings | Build, Execution, Deployment | Ecstasy")
            }
            row { checkBox("Quiet mode (-q, Gradle only)").bindSelected(::quietMode) }
            row {
                checkBox("Stop at the program's first line (Debug)")
                    .bindSelected(::stopOnEntry)
                    .comment("The first line of the module's own code, before it runs, as a breakpoint there would")
            }
            row {
                checkBox("Let the program run while Evaluate Expression runs code (Debug)")
                    .bindSelected(::evaluationRunsProgram)
                    .comment(
                        "For code that waits on work the program's other fibers do; they run without stopping at " +
                            "breakpoints, and their frames may then have moved",
                    )
            }
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
        config.stopOnEntry = stopOnEntry
        config.evaluationRunsProgram = evaluationRunsProgram
        config.tests = XtcRunConfiguration.testsOf(tests)
    }

    override fun resetEditorFrom(config: XtcRunConfiguration) {
        moduleName = config.moduleName
        moduleUri = config.moduleUri
        methodName = config.methodName
        moduleArguments = config.moduleArguments
        runWith = config.runWith
        quietMode = config.quietMode
        stopOnEntry = config.stopOnEntry
        evaluationRunsProgram = config.evaluationRunsProgram
        tests = XtcRunConfiguration.testsText(config.tests)
        editorPanel.reset() // Reset UI from backing properties
    }
}
