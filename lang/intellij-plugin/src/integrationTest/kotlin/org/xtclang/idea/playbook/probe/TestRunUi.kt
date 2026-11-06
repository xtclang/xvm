package org.xtclang.idea.playbook.probe

import com.google.gson.JsonObject
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
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

    /** The tests that started and have no result, by their locations. */
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val sessions = ConcurrentHashMap<String, XDebugSession>()
    private val watched = ConcurrentHashMap.newKeySet<Project>()

    private fun watch(project: Project) {
        if (!watched.add(project)) return
        val bus = project.messageBus.connect()
        bus.subscribe(
            SMTRunnerEventsListener.TEST_STATUS,
            object : SMTRunnerEventsAdapter() {
                override fun onTestStarted(test: SMTestProxy) {
                    test.locationUrl?.let(running::add)
                }

                override fun onTestFinished(test: SMTestProxy) = record(project, test)

                override fun onTestFailed(test: SMTestProxy) = record(project, test)

                override fun onTestIgnored(test: SMTestProxy) = record(project, test)
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
                addProperty(
                    "state",
                    when {
                        test.isDefect && test.isIgnored -> "ignored"
                        test.isDefect -> "failed"
                        else -> "passed"
                    },
                )
                test.errorMessage?.let { addProperty("message", it) }
                // a test that ended in an exception other than a failed assertion, which the console shows as an error
                if (test.magnitudeInfo == TestStateInfo.Magnitude.ERROR_INDEX) addProperty("error", true)
            }
        // where the console goes for the test: its location, through the plugin's locator
        ApplicationManager.getApplication().runReadAction {
            test.getLocation(project, GlobalSearchScope.allScope(project))?.openFileDescriptor?.let { target ->
                result.addProperty("line", target.line)
                result.addProperty("file", target.file.path)
            }
        }
        results[url] = result
        running.remove(url)
    }

    /** Forgets the results and sessions, and starts recording them for [project]. */
    @JvmStatic
    fun reset(project: Project) {
        watch(project)
        results.clear()
        running.clear()
        sessions.clear()
    }

    /** The locations of the tests that started since [reset] and have no result, one to a line. */
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

    /** Each test's latest result by its location: its `state`, a failure's `message`, and its location's `line`. */
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
