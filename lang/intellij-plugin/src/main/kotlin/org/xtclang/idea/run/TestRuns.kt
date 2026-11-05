package org.xtclang.idea.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.Location
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.PsiLocation
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleView
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import org.xtclang.idea.lsp.CompilerSettings
import org.xtclang.idea.lsp.ModuleTests
import org.xtclang.idea.lsp.RunParams
import org.xtclang.idea.lsp.ServerTest
import org.xtclang.idea.lsp.TestEvent
import org.xtclang.idea.lsp.TestLocation
import org.xtclang.idea.lsp.TestSelector
import org.xtclang.idea.lsp.XtcLanguageServer
import java.io.OutputStream
import java.util.concurrent.CompletableFuture

/** The test framework's name, which IntelliJ's test console shows and keys its settings by. */
internal const val TEST_FRAMEWORK = "Ecstasy"

/**
 * Runs the tests of [configuration]'s module that its [XtcRunConfiguration.tests] select with xunit, embedded, in
 * IntelliJ's test console, which shows the test events the server sends with the run's output.
 */
internal class EmbeddedTestState(
    private val configuration: XtcRunConfiguration,
    private val environment: ExecutionEnvironment,
    private val request: RunParams,
    private val label: String,
) : RunProfileState {
    override fun execute(
        executor: Executor,
        runner: ProgramRunner<*>,
    ): ExecutionResult {
        val handler = EmbeddedExecution.runHandler(environment.project, request, label)
        ProcessTerminatedListener.attach(handler)
        return testResult(handler, XtcTestConsoleProperties(configuration, executor))
    }
}

/**
 * Runs the tests of [configuration]'s module with [command], Gradle's `testXtc` or `xtc test`, with xunit's `json`
 * report, in IntelliJ's test console, which reads the events from the command's output.
 */
internal class TestCommandLineState(
    private val configuration: XtcRunConfiguration,
    environment: ExecutionEnvironment,
    private val command: GeneralCommandLine,
) : CommandLineState(environment) {
    override fun startProcess(): ProcessHandler = OSProcessHandler(command).also(ProcessTerminatedListener::attach)

    override fun execute(
        executor: Executor,
        runner: ProgramRunner<*>,
    ): ExecutionResult {
        val handler = startProcess()
        return testResult(handler, XtcTestConsoleProperties(configuration, executor))
    }
}

/**
 * A test run's result: the test console, attached to [handler], and its Rerun Failed Tests, which the run's toolbar
 * shows beside Rerun only as a restart action that the result gives it, reading the failed tests from the console's
 * tree; none under Debug ([XtcTestConsoleProperties.createRerunFailedTestsAction]).
 */
private fun testResult(
    handler: ProcessHandler,
    properties: XtcTestConsoleProperties,
): ExecutionResult {
    val console = SMTestRunnerConnectionUtil.createAndAttachConsole(TEST_FRAMEWORK, handler, properties)
    return DefaultExecutionResult(console, handler).apply {
        properties.createRerunFailedTestsAction(console)?.let { rerun ->
            (console as? SMTRunnerConsoleView)?.let { view -> rerun.setModelProvider { view.resultsViewer } }
            setRestartActions(rerun)
        }
    }
}

/**
 * The test console's properties: it reads xunit's events from the output ([XtcTestEventsConverter]), builds its tree
 * from their IDs, and finds a test's `xunit://` location, its declaration in the configuration's module.
 */
internal class XtcTestConsoleProperties(
    configuration: XtcRunConfiguration,
    executor: Executor,
) : SMTRunnerConsoleProperties(configuration, TEST_FRAMEWORK, executor),
    SMCustomMessagesParsing {
    private val locator = XtcTestLocator(configuration.moduleName)

    init {
        isIdBasedTestTree = true
    }

    override fun getTestLocator(): SMTestLocator = locator

    override fun createTestEventsConverter(
        testFrameworkName: String,
        consoleProperties: TestConsoleProperties,
    ): OutputToGeneralTestEventsConverter = XtcTestEventsConverter(testFrameworkName, consoleProperties)

    // under Debug, the rerun would be LSP4IJ's DAP launch, which takes the configuration itself and no rerun's profile
    override fun createRerunFailedTestsAction(consoleView: ConsoleView): AbstractRerunFailedTestsAction? =
        if (executor.id == DefaultDebugExecutor.EXECUTOR_ID) null else XtcRerunFailedTestsAction(consoleView).also { it.init(this) }
}

/**
 * The test console's Rerun Failed Tests: runs its configuration again, as it ran, with only the tests that failed,
 * each by the selector that its `xunit://` location names.
 */
internal class XtcRerunFailedTestsAction(
    console: ComponentContainer,
) : AbstractRerunFailedTestsAction(console) {
    override fun getRunProfile(environment: ExecutionEnvironment): MyRunProfile? {
        val configuration = myConsoleProperties.configuration as? XtcRunConfiguration ?: return null
        val failed = failedTests(getFailedTests(configuration.project).map { it.locationUrl })
        if (failed.isEmpty()) return null
        return object : MyRunProfile(configuration) {
            override fun getState(
                executor: Executor,
                environment: ExecutionEnvironment,
            ): RunProfileState? = (configuration.clone() as XtcRunConfiguration).apply { tests = failed }.getState(executor, environment)
        }
    }

    companion object {
        /** The tests that the failed tests' [locations] name, each once, as test selectors; none for another protocol's. */
        fun failedTests(locations: List<String?>): List<TestSelector> =
            locations
                .mapNotNull { location ->
                    location?.takeIf { it.startsWith(LOCATION) }?.removePrefix(LOCATION)?.takeIf { it.isNotEmpty() }
                }.distinct()
                .map { TestSelector(it, ServerTest.TEST) }

        private const val LOCATION = "${XtcTestLocator.PROTOCOL}://"
    }
}

/** Finds a test of [module] by the selector its `xunit://` location names, among the tests the server last listed. */
internal class XtcTestLocator(
    private val module: String,
) : SMTestLocator {
    override fun getLocation(
        protocol: String,
        path: String,
        project: Project,
        scope: GlobalSearchScope,
    ): List<Location<*>> {
        if (protocol != PROTOCOL) return emptyList()
        val at = XtcTestCatalog.location(project, module, path) ?: return emptyList()
        val file = LSPIJUtils.findResourceFor(at.uri) ?: return emptyList()
        val psi = PsiManager.getInstance(project).findFile(file) ?: return emptyList()
        val offset =
            PsiDocumentManager
                .getInstance(project)
                .getDocument(psi)
                ?.takeIf { at.startLine < it.lineCount }
                ?.let { (it.getLineStartOffset(at.startLine) + at.startColumn).coerceAtMost(it.textLength) }
                ?: 0
        // an Ecstasy file's PSI is not fine-grained, so the location goes to the declaration's line and column itself
        return listOf(
            object : PsiLocation<PsiElement>(project, psi.findElementAt(offset) ?: psi) {
                override fun getOpenFileDescriptor() = OpenFileDescriptor(project, file, at.startLine, at.startColumn)
            },
        )
    }

    companion object {
        /** The protocol of xunit's location hints, `xunit://<selector>`. */
        const val PROTOCOL = "xunit"
    }
}

/** The tests of the project's source modules, as the language server last listed them (`xtc/tests`). */
internal object XtcTestCatalog {
    private val latest = Key.create<List<ModuleTests>>("xtc.tests")

    /** Asks the language server for the tests again; the answer is also kept for [location]. */
    fun refresh(project: Project): CompletableFuture<List<ModuleTests>> {
        val requests =
            LanguageServiceAccessor
                .getInstance(project)
                .startedServers
                .filter { it.serverDefinition.id == CompilerSettings.SERVER_ID }
                .map { wrapper ->
                    wrapper.initializedServer.thenCompose { server ->
                        (server as? XtcLanguageServer)?.tests() ?: CompletableFuture.completedFuture(emptyList())
                    }
                }
        return CompletableFuture
            .allOf(*requests.toTypedArray())
            .thenApply { requests.flatMap { it.join() } }
            .whenComplete { modules, _ -> modules?.let { project.putUserData(latest, it) } }
    }

    /** Where the test of [module] that [selector] selects is declared, among the tests last listed. */
    fun location(
        project: Project,
        module: String,
        selector: String,
    ): TestLocation? =
        project
            .getUserData(latest)
            .orEmpty()
            .filter { module.isBlank() || it.module == module }
            .firstNotNullOfOrNull { find(it.tests, selector)?.location }

    private fun find(
        test: ServerTest,
        selector: String,
    ): ServerTest? = test.takeIf { it.selector == selector } ?: test.children.firstNotNullOfOrNull { find(it, selector) }
}

/** Runs or debugs tests of a module from the editor, as a temporary Ecstasy Application configuration with tests. */
internal object XtcTestRuns {
    /**
     * Runs [test] of [module], whose source is [uri], with [executor]: a module runs all of its tests, a package or a
     * class those under it, and a test itself; with the project's build mode, embedded, Gradle's `testXtc` or `xtc test`.
     */
    fun start(
        project: Project,
        module: String,
        uri: String,
        test: ServerTest,
        executor: Executor,
    ) {
        val runManager = RunManager.getInstance(project)
        val factory = ConfigurationTypeUtil.findConfigurationType(XtcRunConfigurationType::class.java).configurationFactories.first()
        val settings = runManager.createConfiguration(name(module, test), factory)
        (settings.configuration as XtcRunConfiguration).apply {
            moduleName = module
            moduleUri = uri
            tests = if (test.kind == ServerTest.MODULE) emptyList() else listOf(TestSelector(test.selector, test.kind))
        }
        runManager.setTemporaryConfiguration(settings)
        ProgramRunnerUtil.executeConfiguration(settings, executor)
    }

    /** A test run's name: the module's tests, or the test's or container's own name. */
    fun name(
        module: String,
        test: ServerTest,
    ): String = if (test.kind == ServerTest.MODULE) "Tests of $module" else "Test ${test.selector}"
}

/**
 * The test events of a debugged test run, as a process whose output IntelliJ's test console reads: the session's
 * program output, and the events of its tests as their lines among it ([XtcTestEventsConverter]). What comes before the
 * console attaches waits for it; the events end with the debug session's process.
 */
internal class TestEventsProcessHandler : ProcessHandler() {
    private val lock = Any()
    private val pending = StringBuilder()
    private var started = false
    private var exitCode: Int? = null

    /** One piece of the program's output. */
    fun output(text: String) {
        synchronized(lock) {
            if (!started) {
                pending.append(text)
                return
            }
        }
        notifyTextAvailable(text, ProcessOutputTypes.STDOUT)
    }

    /** An event of the session's tests. */
    fun tested(event: TestEvent) = output(event.line())

    /** The debug session's process ended with [code]. */
    fun ended(code: Int) {
        synchronized(lock) {
            if (!started) {
                exitCode = code
                return
            }
        }
        notifyProcessTerminated(code)
    }

    /** The console is attached: what waited goes to it, and an end that came first ends the events. */
    fun attached() {
        startNotify()
        val (text, code) =
            synchronized(lock) {
                started = true
                pending.toString().also { pending.setLength(0) } to exitCode
            }
        if (text.isNotEmpty()) notifyTextAvailable(text, ProcessOutputTypes.STDOUT)
        code?.let(::notifyProcessTerminated)
    }

    override fun destroyProcessImpl() = notifyProcessTerminated(exitCode ?: 1)

    override fun detachProcessImpl() = notifyProcessDetached()

    override fun detachIsDefault() = false

    override fun getProcessInput(): OutputStream? = null
}

/**
 * The Tests tab of a debug session that runs tests: IntelliJ's test console over the session's [events], added to the
 * Debug tool window of the session whose process is [process], once it starts.
 */
internal object DebugTestsTab {
    fun attach(
        project: Project,
        process: ProcessHandler,
        events: TestEventsProcessHandler,
        configuration: XtcRunConfiguration,
        executor: Executor,
    ) {
        val connection = project.messageBus.connect()
        connection.subscribe(
            XDebuggerManager.TOPIC,
            object : XDebuggerManagerListener {
                override fun processStarted(debugProcess: XDebugProcess) {
                    if (debugProcess.processHandler !== process) return
                    connection.disconnect()
                    debugProcess.session.runWhenUiReady { ui ->
                        ApplicationManager.getApplication().invokeLater({
                            val console =
                                SMTestRunnerConnectionUtil.createConsole(
                                    TEST_FRAMEWORK,
                                    XtcTestConsoleProperties(configuration, executor),
                                )
                            console.attachToProcess(events)
                            val content =
                                ui.createContent(
                                    CONTENT_ID,
                                    console.component,
                                    "Tests",
                                    AllIcons.Nodes.Test,
                                    console.preferredFocusableComponent,
                                )
                            Disposer.register(content, console)
                            ui.addContent(content)
                            ui.selectAndFocus(content, false, false)
                            events.attached()
                        }, project.disposed)
                    }
                }
            },
        )
    }

    /** The tab's ID in the Debug tool window. */
    const val CONTENT_ID = "XtcTests"
}
