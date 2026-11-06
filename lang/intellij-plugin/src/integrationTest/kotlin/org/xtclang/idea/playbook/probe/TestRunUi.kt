package org.xtclang.idea.playbook.probe

import com.google.gson.JsonObject
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.testframework.Printable
import com.intellij.execution.testframework.Printer
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentManager
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.XDebuggerUtil
import org.xtclang.idea.run.XtcRunConfiguration
import org.xtclang.idea.run.XtcRunConfigurationType
import org.xtclang.idea.run.XtcRunMode
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities

/**
 * Drives the test icons in an Ecstasy editor's gutter, and reports the tests as IntelliJ's test console has them: each
 * test's result by its location, `xunit://<selector>`, with a failure's message and the line its location resolves
 * to, and the debug session of a test run, by the run's name.
 */
object TestRunUi {
    /** The latest result of each test, by its location. */
    private val results = ConcurrentHashMap<String, JsonObject>()

    /** The tests that started and have not ended, by their locations. */
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val sessions = ConcurrentHashMap<String, XDebugSession>()
    private val watched = ConcurrentHashMap.newKeySet<Project>()

    private fun watch(project: Project) {
        if (!watched.add(project)) return
        val bus = project.messageBus.connect()
        bus.subscribe(
            SMTRunnerEventsListener.TEST_STATUS,
            object : SMTRunnerEventsAdapter() {
                override fun onTestStarted(test: SMTestProxy) = record(project, test)

                override fun onTestFinished(test: SMTestProxy) = record(project, test)

                override fun onTestFailed(test: SMTestProxy) = record(project, test)

                override fun onTestIgnored(test: SMTestProxy) = record(project, test)

                // a stopped run ends its tests that did not finish, terminated or not run, which no other event
                // says; only those, as a run's end can come after the results of its tests were reset for the next
                override fun onTestingFinished(testsRoot: SMTestProxy.SMRootTestProxy) =
                    testsRoot.allTests
                        .filter { it.isLeaf && state(it) in setOf("terminated", "not run") }
                        .forEach { record(project, it) }
            },
        )
        bus.subscribe(
            XDebuggerManager.TOPIC,
            object : XDebuggerManagerListener {
                override fun processStarted(debugProcess: XDebugProcess) {
                    sessions[debugProcess.session.sessionName] = debugProcess.session
                }
            },
        )
    }

    private fun record(
        project: Project,
        test: SMTestProxy,
    ) {
        val url = test.locationUrl ?: return
        val result =
            JsonObject().apply {
                addProperty("state", state(test))
                // an ignored test's reason is in what the console prints of it, its state keeps it nowhere else
                (test.errorMessage ?: printed(test).trim().takeIf { test.isIgnored && it.isNotEmpty() })?.let { addProperty("message", it) }
            }
        // where the console goes for the test: its location, through the plugin's locator
        ApplicationManager.getApplication().runReadAction {
            test.getLocation(project, GlobalSearchScope.allScope(project))?.openFileDescriptor?.let { target ->
                result.addProperty("line", target.line)
                result.addProperty("file", target.file.path)
            }
        }
        results[url] = result
        if (result["state"].asString == "running") running.add(url) else running.remove(url)
    }

    /**
     * A test's state as the console shows it, by its magnitude: `passed`, `failed` (a failed assertion or comparison),
     * `error` (an exception other than a failed assertion), `ignored` (skipped), `terminated` (one a stop ended),
     * `not run`, or `running`.
     */
    private fun state(test: SMTestProxy): String =
        when (test.magnitudeInfo) {
            TestStateInfo.Magnitude.PASSED_INDEX, TestStateInfo.Magnitude.COMPLETE_INDEX -> "passed"
            TestStateInfo.Magnitude.FAILED_INDEX -> "failed"
            TestStateInfo.Magnitude.ERROR_INDEX -> "error"
            TestStateInfo.Magnitude.IGNORED_INDEX, TestStateInfo.Magnitude.SKIPPED_INDEX -> "ignored"
            TestStateInfo.Magnitude.TERMINATED_INDEX -> "terminated"
            TestStateInfo.Magnitude.NOT_RUN_INDEX -> "not run"
            TestStateInfo.Magnitude.RUNNING_INDEX -> "running"
        }

    /** What the console prints of [test]: its output, and an ignored test's reason. */
    private fun printed(test: SMTestProxy): String {
        val text = StringBuilder()
        test.printOn(
            object : Printer {
                override fun print(
                    printed: String,
                    contentType: ConsoleViewContentType,
                ) {
                    text.append(printed)
                }

                override fun onNewAvailable(printable: Printable) = printable.printOn(this)

                override fun printHyperlink(
                    printed: String,
                    info: HyperlinkInfo?,
                ) {
                    text.append(printed)
                }

                override fun mark() {}
            },
        )
        return text.toString()
    }

    /** The run, or test run, whose tab is named [name]: its content descriptor, as the Run window has it. */
    private fun descriptor(
        project: Project,
        name: String,
    ) = checkNotNull(RunContentManager.getInstance(project).allDescriptors.lastOrNull { it.displayName == name }) {
        "No run $name: ${RunContentManager.getInstance(project).allDescriptors.map { it.displayName }}"
    }

    /** Stops the run named [name], as its Stop button does. */
    @JvmStatic
    fun stop(
        project: Project,
        name: String,
    ) {
        ApplicationManager.getApplication().invokeAndWait { descriptor(project, name).processHandler?.destroyProcess() }
    }

    /**
     * Chooses Rerun Failed Tests on the run named [name], as its button on the run's toolbar does: the action that the
     * run's result gives as a restart action. Fails when the run has none.
     */
    @JvmStatic
    fun rerunFailed(
        project: Project,
        name: String,
    ) {
        ApplicationManager.getApplication().invokeAndWait {
            val run = descriptor(project, name)
            val action =
                checkNotNull(run.restartActions.filterIsInstance<AbstractRerunFailedTestsAction>().singleOrNull()) {
                    "The run $name has no Rerun Failed Tests: ${run.restartActions.map { it.templateText }}"
                }
            val context = DataManager.getInstance().getDataContext(run.component)
            ActionUtil.performAction(
                action,
                AnActionEvent.createEvent(action, context, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null),
            )
        }
    }

    /** Runs an Ecstasy test configuration of [module]'s tests, all of them, with the xtc command line, named [module]. */
    @JvmStatic
    fun runCommandLine(
        project: Project,
        module: String,
        uri: String,
    ) {
        watch(project)
        ApplicationManager.getApplication().invokeAndWait {
            val runManager = RunManager.getInstance(project)
            val factory = ConfigurationTypeUtil.findConfigurationType(XtcRunConfigurationType::class.java).configurationFactories.first()
            val settings = runManager.createConfiguration(module, factory)
            (settings.configuration as XtcRunConfiguration).apply {
                moduleName = module
                moduleUri = uri
                runWith = XtcRunMode.COMMAND_LINE
                tests = emptyList()
            }
            runManager.setTemporaryConfiguration(settings)
            ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
        }
    }

    /** Forgets the results and sessions, and starts recording them for [project]. */
    @JvmStatic
    fun reset(project: Project) {
        watch(project)
        results.clear()
        running.clear()
        sessions.clear()
    }

    /** The locations of the tests that started since [reset] and have not ended, one to a line. */
    @JvmStatic
    fun running(): String = running.sorted().joinToString("\n")

    /**
     * Whether the newest test run's console says the run was stopped before its tests ended, as its tree's root does
     * ("Terminated", and "Stopped." in its status); on the EDT. The newest run's, of all the run content, whichever
     * the Run window selects.
     */
    @JvmStatic
    fun stopped(project: Project): Boolean =
        RunContentManager
            .getInstance(project)
            .allDescriptors
            .mapNotNull { (it.executionConsole as? SMTRunnerConsoleView)?.resultsViewer }
            .lastOrNull()
            ?.testsRootNode
            ?.wasTerminated() == true

    /**
     * Whether the runs' consoles show all that they will: every run's process has ended, no test run's tree is still in
     * progress, and each console of each run's content, a test run's and a Gradle build's included, has shown the output
     * it held back, which this flushes; on the EDT. A console shows its last output only once it flushes it, after the
     * process has ended.
     */
    @JvmStatic
    fun settled(project: Project): Boolean {
        val descriptors = RunContentManager.getInstance(project).allDescriptors
        // the consoles in the runs' content, however a view nests them, as a Gradle build's does in its build view
        val consoles =
            descriptors
                .mapNotNull { it.component }
                .flatMap { UIUtil.uiTraverser(it).filter(ConsoleViewImpl::class.java).toList() }
                .distinct()
        consoles.forEach(ConsoleViewImpl::flushDeferredText)
        return descriptors.all { it.processHandler?.isProcessTerminated != false } &&
            descriptors.none { (it.executionConsole as? SMTRunnerConsoleView)?.resultsViewer?.testsRootNode?.isInProgress == true } &&
            consoles.none(ConsoleViewImpl::hasDeferredOutput)
    }

    /** Each test's latest result by its location: its `state` ([state]), a failure's `message`, and its location's `line`. */
    @JvmStatic
    fun results(): String = JsonObject().apply { results.forEach { (url, result) -> add(url, result) } }.toString()

    private fun testIcons(editor: Editor): List<Pair<RangeHighlighter, GutterIconRenderer>> =
        DocumentMarkupModel
            .forDocument(editor.document, editor.project, false)
            ?.allHighlighters
            .orEmpty()
            .filter { it.isValid }
            .mapNotNull { highlighter -> highlighter.gutterIconRenderer?.let { highlighter to it } }
            .filter { (_, renderer) -> renderer.javaClass.simpleName == "TestIcon" }

    /** The test icons' tooltips, each with the zero-based line it is on, as "Test Inner.works@14"; on the EDT. */
    @JvmStatic
    fun icons(editor: Editor): List<String> =
        testIcons(editor).map { (highlighter, renderer) ->
            "${renderer.tooltipText}@${editor.document.getLineNumber(highlighter.startOffset)}"
        }

    /** Chooses Run, or with [debug] Debug, in the popup of the test icon with [tooltip], as a click there offers. */
    @JvmStatic
    fun start(
        editor: Editor,
        tooltip: String,
        debug: Boolean,
    ) {
        check(SwingUtilities.isEventDispatchThread())
        val renderer = testIcons(editor).single { it.second.tooltipText == tooltip }.second
        val prefix = if (debug) "Debug '" else "Run '"
        val action: AnAction =
            (renderer.popupMenuActions as DefaultActionGroup)
                .getChildren(ActionManager.getInstance())
                .single { it.templateText?.startsWith(prefix) == true }
        val context = DataManager.getInstance().getDataContext(editor.contentComponent)
        ActionUtil.performAction(
            action,
            AnActionEvent.createEvent(action, context, null, ActionPlaces.EDITOR_GUTTER_POPUP, ActionUiKind.NONE, null),
        )
    }

    /** The debug session of the test run [name]: `started`, `suspended`, and where it is paused, `file` and `line` (1-based). */
    @JvmStatic
    fun debugState(name: String): String {
        val session = sessions[name] ?: return JsonObject().apply { addProperty("started", false) }.toString()
        return JsonObject()
            .apply {
                addProperty("started", true)
                addProperty("suspended", session.isSuspended)
                addProperty("stopped", session.isStopped)
                session.currentPosition?.let { position ->
                    addProperty("file", position.file.path)
                    addProperty("line", position.line + 1)
                }
            }.toString()
    }

    /** Sets a line breakpoint on the 1-based [line] of the file at [uri]. */
    @JvmStatic
    fun breakpoint(
        project: Project,
        uri: String,
        line: Int,
    ) {
        boundedEdt(ModalityState.defaultModalityState()) {
            val file = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByUrl(uri)) { "No file $uri" }
            XDebuggerUtil.getInstance().toggleLineBreakpoint(project, file, line - 1)
        }
    }

    /** Resumes the paused debug session of the test run [name]. */
    @JvmStatic
    fun resume(name: String) {
        checkNotNull(sessions[name]) { "No debug session $name" }.resume()
    }
}
