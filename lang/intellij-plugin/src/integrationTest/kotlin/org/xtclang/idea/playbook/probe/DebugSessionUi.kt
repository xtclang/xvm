package org.xtclang.idea.playbook.probe

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.SimpleColoredText
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueContainer
import com.intellij.xdebugger.frame.XValueModifier
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog
import com.intellij.xdebugger.impl.evaluate.XEvaluationOrigin
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackWithOrigin
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant
import org.xtclang.idea.run.XtcRunConfiguration
import org.xtclang.idea.run.XtcRunConfigurationType
import org.xtclang.idea.run.XtcRunMode
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import javax.swing.SwingUtilities

/**
 * Drives Debug of an Ecstasy Application configuration, through LSP4IJ's DAP client and the debug session in the
 * language server, and reports the session as the IDE shows it: whether it is paused, where, its frames and their
 * variables, its console and its exit code.
 */
object DebugSessionUi {
    /** Sessions by name as they start: the debugger manager lists a session only while it runs. */
    private val sessions = ConcurrentHashMap<String, XDebugSession>()
    private val watched = ConcurrentHashMap.newKeySet<Project>()

    private fun watch(project: Project) {
        if (watched.add(project)) {
            project.messageBus.connect().subscribe(
                XDebuggerManager.TOPIC,
                object : XDebuggerManagerListener {
                    override fun processStarted(debugProcess: XDebugProcess) {
                        sessions[debugProcess.session.sessionName] = debugProcess.session
                    }
                },
            )
        }
    }

    /** Sets a line breakpoint on the 1-based [breakpointLine] of the module's source, unless it is 0, and starts Debug. */
    @JvmStatic
    fun debug(
        project: Project,
        module: String,
        uri: String,
        breakpointLine: Int,
    ) = debug(project, module, uri, breakpointLine, evaluationRunsProgram = false, stopOnEntry = false)

    /** Starts Debug as [debug] does, with a configuration that lets the program run while Evaluate Expression runs code. */
    @JvmStatic
    fun debugRunningProgram(
        project: Project,
        module: String,
        uri: String,
        breakpointLine: Int,
    ) = debug(project, module, uri, breakpointLine, evaluationRunsProgram = true, stopOnEntry = false)

    /** Starts Debug, with no breakpoint, of a configuration that stops the program at its first line. */
    @JvmStatic
    fun debugFromEntry(
        project: Project,
        module: String,
        uri: String,
    ) = debug(project, module, uri, 0, evaluationRunsProgram = false, stopOnEntry = true)

    private fun debug(
        project: Project,
        module: String,
        uri: String,
        breakpointLine: Int,
        evaluationRunsProgram: Boolean,
        stopOnEntry: Boolean,
    ) {
        watch(project)
        sessions.remove(name(module))
        ApplicationManager.getApplication().invokeAndWait {
            if (breakpointLine > 0) {
                val file = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByUrl(uri)) { "No file $uri" }
                XDebuggerUtil.getInstance().toggleLineBreakpoint(project, file, breakpointLine - 1)
            }
            val runManager = RunManager.getInstance(project)
            val factory = ConfigurationTypeUtil.findConfigurationType(XtcRunConfigurationType::class.java).configurationFactories.first()
            val settings = runManager.createConfiguration(name(module), factory)
            (settings.configuration as XtcRunConfiguration).apply {
                moduleName = module
                moduleUri = uri
                runWith = XtcRunMode.PROJECT
                this.evaluationRunsProgram = evaluationRunsProgram
                this.stopOnEntry = stopOnEntry
            }
            runManager.setTemporaryConfiguration(settings)
            ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
        }
    }

    /**
     * The module's debug session: `started`, `suspended`, the paused `line` (1-based) and `file`, the `frames` of the
     * paused fiber with their names and lines, the `console` text, and the `exitCode` once the process has ended.
     */
    @JvmStatic
    fun state(
        project: Project,
        module: String,
    ): String {
        val session = session(project, module) ?: return JsonObject().apply { addProperty("started", false) }.toString()
        val state =
            JsonObject().apply {
                addProperty("started", true)
                addProperty("suspended", session.isSuspended)
            }
        session.currentPosition?.let { position ->
            state.addProperty("file", position.file.path)
            state.addProperty("line", position.line + 1)
        }
        session.suspendContext?.activeExecutionStack?.let { stack -> state.add("frames", frames(stack)) }
        // the exit code is read before the console, so a process that ended has printed everything the console shows;
        // a session that is still starting has no process, nor its handler, yet: XDebugSessionImpl.getDebugProcess()
        // fails with a NullPointerException until it has one
        val exitCode =
            runCatching { session.debugProcess }
                .getOrNull()
                ?.processHandler
                ?.takeIf { it.isProcessTerminated }
                ?.exitCode
        ApplicationManager.getApplication().invokeAndWait {
            (session.consoleView as? ConsoleViewImpl)?.let { console ->
                // the console's editor is made with its component, which the Debug tool window may not have shown yet
                console.component
                console.flushDeferredText()
                state.addProperty("console", console.text)
            }
        }
        exitCode?.let { state.addProperty("exitCode", it) }
        return state.toString()
    }

    /**
     * The variables of the paused fiber's top frame as the Variables view gets them from LSP4IJ, in the frame's Locals
     * group, or, along [path], names separated by `/` such as `point`, the fields or elements of one of them: each with
     * its `name`, `value`, `type` and whether it `expands`.
     */
    @JvmStatic
    fun variables(
        project: Project,
        module: String,
        path: String,
    ): String {
        var shown = locals(project, module)
        path.split('/').filter { it.isNotEmpty() }.forEach { name -> shown = children(named(shown, name, path)) }
        return JsonArray().apply { (0 until shown.size()).forEach { add(presentation(shown.getName(it), shown.getValue(it))) } }.toString()
    }

    /**
     * Clicks the link that the Variables view shows beside the value at the `/`-separated [path], such as LSP4IJ's
     * Show value of a lazy variable, and returns the value it shows, or fails with the error it reports.
     */
    @JvmStatic
    fun showValue(
        project: Project,
        module: String,
        path: String,
    ): String {
        val names = path.split('/').filter { it.isNotEmpty() }
        var shown = locals(project, module)
        names.dropLast(1).forEach { name -> shown = children(named(shown, name, path)) }
        val link = CompletableFuture<XFullValueEvaluator>()
        named(shown, names.last(), path).computePresentation(
            object : XValueNode {
                override fun setPresentation(
                    icon: Icon?,
                    type: String?,
                    value: String,
                    hasChildren: Boolean,
                ) {}

                override fun setPresentation(
                    icon: Icon?,
                    presentation: XValuePresentation,
                    hasChildren: Boolean,
                ) {}

                override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) {
                    link.complete(fullValueEvaluator)
                }
            },
            XValuePlace.TREE,
        )
        val value = CompletableFuture<String>()
        link.get(30, TimeUnit.SECONDS).startEvaluation(
            object : XFullValueEvaluator.XFullValueEvaluationCallback {
                override fun evaluated(fullValue: String) {
                    value.complete(fullValue)
                }

                override fun errorOccurred(errorMessage: String) {
                    value.completeExceptionally(IllegalStateException(errorMessage))
                }

                override fun isObsolete() = false
            },
        )
        return value.get(30, TimeUnit.SECONDS)
    }

    /**
     * Turns LSP4IJ's Show Values as Hexadecimal on or off for the module's session, as its item in the Variables
     * view's menu does (LSP4IJ #1769), which shows the values again: false if this LSP4IJ has no such item.
     */
    @JvmStatic
    fun showHex(
        project: Project,
        module: String,
        on: Boolean,
    ): Boolean {
        val action = ActionManager.getInstance().getAction("DAP.HexadecimalValues") as? ToggleAction ?: return false
        val session = checkNotNull(session(project, module)) { "No debug session of $module" }
        ApplicationManager.getApplication().invokeAndWait {
            val context =
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .add(XDebugSession.DATA_KEY, session)
                    .build()
            action.setSelected(AnActionEvent.createEvent(action, context, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null), on)
        }
        return true
    }

    /**
     * The children of the value at the `/`-separated [path], as the Variables view shows them when it opens the value
     * and then, up to [pages] times, the `...` node that a debugger adds for children it has not read yet: `children`,
     * their presentations, `remaining`, how many more the debugger says there are, and `more`, true while it still
     * shows a `...` node.
     */
    @JvmStatic
    fun pagedVariables(
        project: Project,
        module: String,
        path: String,
        pages: Int,
    ): String {
        val names = path.split('/').filter { it.isNotEmpty() }
        var shown = locals(project, module)
        names.dropLast(1).forEach { name -> shown = children(named(shown, name, path)) }
        val collected = XValueChildrenList()
        var left = 0
        var next: Runnable? = null
        var batch = CompletableFuture<Unit>()
        val node =
            object : XCompositeNode {
                override fun addChildren(
                    children: XValueChildrenList,
                    last: Boolean,
                ) {
                    (0 until children.size()).forEach { collected.add(children.getName(it), children.getValue(it)) }
                    if (last) {
                        left = 0
                        batch.complete(Unit)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun tooManyChildren(remaining: Int) = tooManyChildren(remaining, Runnable {})

                override fun tooManyChildren(
                    remaining: Int,
                    addNextChildren: Runnable,
                ) {
                    left = remaining
                    next = addNextChildren
                    batch.complete(Unit)
                }

                override fun setAlreadySorted(alreadySorted: Boolean) {}

                override fun setErrorMessage(errorMessage: String) {
                    batch.completeExceptionally(IllegalStateException(errorMessage))
                }

                override fun setErrorMessage(
                    errorMessage: String,
                    link: XDebuggerTreeNodeHyperlink?,
                ) = setErrorMessage(errorMessage)

                override fun setMessage(
                    message: String,
                    icon: Icon?,
                    attributes: SimpleTextAttributes,
                    link: XDebuggerTreeNodeHyperlink?,
                ) {}
            }
        named(shown, names.last(), path).computeChildren(node)
        batch.get(30, TimeUnit.SECONDS)
        repeat(pages) {
            val open = next ?: return@repeat
            next = null
            batch = CompletableFuture()
            // the node's action runs on the event thread, as when the user opens it
            ApplicationManager.getApplication().invokeAndWait { open.run() }
            batch.get(30, TimeUnit.SECONDS)
        }
        return JsonObject()
            .apply {
                add(
                    "children",
                    JsonArray().apply {
                        (0 until collected.size()).forEach { add(presentation(collected.getName(it), collected.getValue(it))) }
                    },
                )
                addProperty("remaining", left)
                addProperty("more", next != null)
            }.toString()
    }

    /**
     * Sets the value at the `/`-separated [path] to [expression], as the Variables view's Set Value does through the
     * value's modifier: "" once it is set, or the error the modifier reports.
     */
    @JvmStatic
    fun setValue(
        project: Project,
        module: String,
        path: String,
        expression: String,
    ): String {
        val names = path.split('/').filter { it.isNotEmpty() }
        var shown = locals(project, module)
        names.dropLast(1).forEach { name -> shown = children(named(shown, name, path)) }
        val modifier = checkNotNull(named(shown, names.last(), path).modifier) { "${names.last()} has no Set Value" }
        val result = CompletableFuture<String>()
        modifier.setValue(
            XExpressionImpl.fromText(expression),
            object : XValueModifier.XModificationCallback {
                override fun valueModified() {
                    result.complete("")
                }

                override fun errorOccurred(errorMessage: String) {
                    result.complete(errorMessage)
                }
            },
        )
        return result.get(30, TimeUnit.SECONDS)
    }

    /**
     * Completes [text] in Evaluate Expression, which opens on the paused fiber's top frame as its action opens it:
     * Basic Completion at the end of the text, then [pick] chosen from the lookup, which always shows, even for one
     * completion. Answers the lookup's `items`, whether the pick was `inserted`, and the expression's `text` after it.
     * With an expression fragment that is not physical, the lookup closes without a pick, which would edit it.
     */
    @JvmStatic
    fun complete(
        project: Project,
        module: String,
        text: String,
        pick: String,
    ): String {
        val session = checkNotNull(session(project, module)) { "No debug session of $module" }
        val position = checkNotNull(session.currentStackFrame?.sourcePosition) { "The debug session of $module has no frame position" }
        val settings = CodeInsightSettings.getInstance()
        val insertsAtOnce = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
        lateinit var dialog: XDebuggerEvaluationDialog
        // the dialog is not modal, so it shows and this goes on
        ApplicationManager.getApplication().invokeAndWait {
            dialog =
                XDebuggerEvaluationDialog(session, session.debugProcess.editorsProvider, XExpressionImpl.fromText(text), position, false)
            dialog.show()
        }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            val editor = expressionEditor(project, dialog, deadline)
            // TODO LSP4IJ: UP37 — LSP4IJ 0.21.0's expression fragment is not physical, although its view provider has
            //  the event system on, so an edit of it fails IntelliJ's Document/PSI check; the pick is inserted only
            //  into a physical one, as #1763 makes it
            var physical = false
            ApplicationManager.getApplication().invokeAndWait {
                physical = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.isPhysical == true
                settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
                editor.selectionModel.removeSelection()
                editor.caretModel.moveToOffset(editor.document.textLength)
            }
            // Basic Completion once the expression has the focus, as when it is typed there; a lookup that shows
            // before the dialog takes the focus closes, so completion starts again if none has shown for a while
            var focused = false
            while (!focused && System.nanoTime() < deadline) {
                ApplicationManager.getApplication().invokeAndWait { focused = editor.contentComponent.isFocusOwner }
                if (!focused) Thread.sleep(100)
            }

            fun invoke() =
                ApplicationManager.getApplication().invokeAndWait {
                    CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor)
                }
            invoke()
            var invoked = System.nanoTime()

            val items = JsonArray()
            var inserted = false
            var done = false
            while (!done) {
                check(System.nanoTime() < deadline) { "No completion $pick of $text: \"${editor.document.text}\"" }
                Thread.sleep(100)
                var shown = false
                ApplicationManager.getApplication().invokeAndWait {
                    val lookup = LookupManager.getActiveLookup(editor) as LookupImpl?
                    shown = lookup != null
                    val item = lookup?.items?.firstOrNull { it.lookupString == pick }
                    if (lookup != null && item != null) {
                        lookup.items.forEach { items.add(it.lookupString) }
                        if (physical) {
                            lookup.currentItem = item
                            lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR)
                            inserted = true
                        } else {
                            lookup.hideLookup(true)
                        }
                        done = true
                    }
                }
                if (!done && !shown && System.nanoTime() - invoked > TimeUnit.SECONDS.toNanos(5)) {
                    invoke()
                    invoked = System.nanoTime()
                }
            }
            var completed = ""
            ApplicationManager.getApplication().invokeAndWait { completed = editor.document.text }
            return JsonObject()
                .apply {
                    add("items", items)
                    addProperty("inserted", inserted)
                    addProperty("text", completed)
                }.toString()
        } finally {
            ApplicationManager.getApplication().invokeAndWait {
                settings.AUTOCOMPLETE_ON_CODE_COMPLETION = insertsAtOnce
                dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
            }
        }
    }

    /** The dialog's expression editor, whose document is the debugger's code fragment, once it shows. */
    private fun expressionEditor(
        project: Project,
        dialog: XDebuggerEvaluationDialog,
        deadline: Long,
    ): Editor {
        while (true) {
            var editor: Editor? = null
            var shown = emptyList<String>()
            ApplicationManager.getApplication().invokeAndWait {
                val editors = EditorFactory.getInstance().allEditors.filter { SwingUtilities.isDescendingFrom(it.component, dialog.window) }
                val files = PsiDocumentManager.getInstance(project)
                editor = editors.firstOrNull { files.getPsiFile(it.document) != null }
                shown = editors.map { "\"${it.document.text}\" ${files.getPsiFile(it.document)}" }
            }
            editor?.let { return it }
            check(System.nanoTime() < deadline) { "Evaluate Expression has no expression editor: $shown" }
            Thread.sleep(100)
        }
    }

    /** The paused session's top frame's Locals, as the Variables view shows them. */
    private fun locals(
        project: Project,
        module: String,
    ): XValueChildrenList {
        val session = checkNotNull(session(project, module)) { "No debug session of $module" }
        val frame = checkNotNull(session.currentStackFrame) { "The debug session of $module is not paused" }
        val groups = children(frame).bottomGroups
        return children(checkNotNull(groups.firstOrNull { it.name == "Locals" }) { "No Locals in ${groups.map { it.name }}" })
    }

    private fun named(
        shown: XValueChildrenList,
        name: String,
        path: String,
    ): XValue {
        val index = (0 until shown.size()).firstOrNull { shown.getName(it) == name } ?: error("No variable $name along $path")
        return shown.getValue(index)
    }

    /** Steps the module's paused session as Step Over (`over`), Step Into (`into`) or Step Out (`out`) does. */
    @JvmStatic
    fun step(
        project: Project,
        module: String,
        kind: String,
    ) {
        val session = checkNotNull(session(project, module)) { "No debug session of $module" }
        ApplicationManager.getApplication().invokeAndWait {
            when (kind) {
                "over" -> session.stepOver(false)
                "into" -> session.stepInto()
                "out" -> session.stepOut()
                else -> error("No step $kind")
            }
        }
    }

    /**
     * Evaluates an expression in the paused fiber's top frame, as the Watches view does: its `value`, `type` and
     * whether it `expands`, or the `error` the evaluator reports.
     */
    @JvmStatic
    fun evaluate(
        project: Project,
        module: String,
        expression: String,
    ): String = evaluate(project, module, expression, null)

    /**
     * Evaluates an expression as [evaluate] does, as the evaluation's [origin] asks for it, such as `DIALOG` for the
     * Evaluate Expression dialog: an evaluator learns the origin from the callback, as LSP4IJ's does, which sends it
     * as DAP's context.
     */
    @JvmStatic
    fun evaluateFrom(
        project: Project,
        module: String,
        expression: String,
        origin: String,
    ): String = evaluate(project, module, expression, XEvaluationOrigin.valueOf(origin))

    private fun evaluate(
        project: Project,
        module: String,
        expression: String,
        origin: XEvaluationOrigin?,
    ): String {
        val session = checkNotNull(session(project, module)) { "No debug session of $module" }
        val evaluator = checkNotNull(session.currentStackFrame?.evaluator) { "The debug session of $module has no frame evaluator" }
        val result = CompletableFuture<JsonObject>()
        val callback =
            object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(value: XValue) {
                    result.complete(presentation(expression, value))
                }

                override fun errorOccurred(errorMessage: String) {
                    result.complete(JsonObject().apply { addProperty("error", errorMessage) })
                }
            }
        evaluator.evaluate(
            expression,
            if (origin == null) {
                callback
            } else {
                object : XDebuggerEvaluator.XEvaluationCallback by callback, XEvaluationCallbackWithOrigin {
                    override fun getOrigin() = origin
                }
            },
            null,
        )
        return result.get(30, TimeUnit.SECONDS).toString()
    }

    /** True while an editor hint shows, such as the one LSP4IJ shows where an exception stopped the program. */
    @JvmStatic
    fun hintShown(): Boolean {
        var shown = false
        ApplicationManager.getApplication().invokeAndWait { shown = HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(false) }
        return shown
    }

    /** Resumes the module's paused session, as the Resume Program action does. */
    @JvmStatic
    fun resume(
        project: Project,
        module: String,
    ) {
        checkNotNull(session(project, module)) { "No debug session of $module" }.resume()
    }

    /**
     * The paused session's threads, as the Frames view lists them: each one's `name`, and its `frames` with their names and
     * lines, which LSP4IJ asks the debug adapter for once a thread's frames are wanted.
     */
    @JvmStatic
    fun threads(
        project: Project,
        module: String,
    ): String {
        val session = checkNotNull(session(project, module)) { "No debug session of $module" }
        val context = checkNotNull(session.suspendContext) { "The debug session of $module is not paused" }
        return JsonArray()
            .apply {
                context.executionStacks.forEach { stack ->
                    add(
                        JsonObject().apply {
                            addProperty("name", stack.displayName)
                            add("frames", frames(stack))
                        },
                    )
                }
            }.toString()
    }

    /** The texts of the Smart Step Into variants where the module's paused session stopped, as Shift+F7 offers them. */
    @JvmStatic
    fun smartStepTargets(
        project: Project,
        module: String,
    ): String = JsonArray().apply { smartStepVariants(project, module).forEach { add(it.text) } }.toString()

    /** Steps the module's paused session into the Smart Step Into variant with the given [text], as choosing it does. */
    @JvmStatic
    fun smartStepInto(
        project: Project,
        module: String,
        text: String,
    ) {
        val session = checkNotNull(session(project, module)) { "No debug session of $module" }
        val handler = smartStepHandler(session)
        val variant = smartStepVariants(project, module).single { it.text == text }
        ApplicationManager.getApplication().invokeAndWait { session.smartStepInto(handler, variant) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun smartStepHandler(session: XDebugSession) =
        checkNotNull(
            session.debugProcess.smartStepIntoHandler,
        ) { "No Smart Step Into handler" } as XSmartStepIntoHandler<XSmartStepIntoVariant>

    private fun smartStepVariants(
        project: Project,
        module: String,
    ): List<XSmartStepIntoVariant> {
        val session = checkNotNull(session(project, module)) { "No debug session of $module" }
        val position = checkNotNull(session.currentPosition) { "The debug session of $module is not paused" }
        return checkNotNull(smartStepHandler(session).computeSmartStepVariantsAsync(position).blockingGet(30_000)) {
            "No Smart Step Into variants"
        }
    }

    /**
     * Whether Reset Frame is offered on each frame of the module's paused session, in the stack's order, as the Frames
     * view's context menu shows it once that frame is selected and the debugger has said whether it can be dropped.
     */
    @JvmStatic
    fun resettable(
        project: Project,
        module: String,
    ): String {
        checkNotNull(session(project, module)) { "No debug session of $module" }
        val proxy = sessionProxy(project)
        val stack = call(proxy, "getCurrentExecutionStack") as XExecutionStack
        val frames = stackFrames(stack)
        val offered = JsonArray()
        frames.forEachIndexed { index, frame ->
            select(proxy, stack, frame, index == 0)
            offered.add(resetFrameOffered(project))
        }
        select(proxy, stack, frames.first(), true)
        return offered.toString()
    }

    /**
     * Selects the frame of the module's paused session whose name contains [name], as the Frames view does, and resets it
     * with Reset Frame, which LSP4IJ's drop-frame handler takes.
     */
    @JvmStatic
    fun resetFrame(
        project: Project,
        module: String,
        name: String,
    ) {
        checkNotNull(session(project, module)) { "No debug session of $module" }
        val proxy = sessionProxy(project)
        val stack = call(proxy, "getCurrentExecutionStack") as XExecutionStack
        val frames = stackFrames(stack)
        val frame = frames.first { SimpleColoredText().also(it::customizePresentation).toString().contains(name) }
        select(proxy, stack, frame, frame === frames.first())
        check(resetFrameOffered(project)) { "Reset Frame is not offered on $name" }
        ApplicationManager.getApplication().invokeAndWait {
            ActionManager.getInstance().getAction(RESET_FRAME).actionPerformed(resetFrameEvent(project))
        }
    }

    /** IntelliJ's Reset Frame, in the Frames view's context menu and in Run | Debugging Actions. */
    private const val RESET_FRAME = "Debugger.PopFrame"

    /**
     * The project's current debug session as the debugger's actions and the Frames view see it, a frontend proxy since
     * 2025.3, whose frames are its own; its methods are newer than the platform this probe compiles against.
     */
    private fun sessionProxy(project: Project): Any =
        checkNotNull(XDebugManagerProxy.getInstance().getCurrentSessionProxy(project)) { "No current debug session" }

    private fun call(
        target: Any,
        name: String,
        vararg args: Any?,
    ): Any? =
        target.javaClass.methods
            .first { it.name == name && it.parameterCount == args.size }
            .invoke(target, *args)

    /**
     * Selects the frame of the stack, as clicking it in the Frames view does, and waits until the drop-frame handler has
     * said whether it can be dropped, which it asks the debugger for once.
     */
    private fun select(
        proxy: Any,
        stack: XExecutionStack,
        frame: XStackFrame,
        top: Boolean,
    ) {
        ApplicationManager.getApplication().invokeAndWait { call(proxy, "setCurrentStackFrame", stack, frame, top) }
        val handler = checkNotNull(call(proxy, "getDropFrameHandler")) { "No drop-frame handler" }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (call(proxy, "getCurrentStackFrame") !== frame || call(handler, "canDropFrame", frame).toString() == "UNSURE") {
            check(System.nanoTime() < deadline) {
                "The debugger did not say whether ${SimpleColoredText().also(frame::customizePresentation)} can be reset"
            }
            Thread.sleep(100)
        }
    }

    /**
     * Whether Reset Frame is offered for the current session's current frame: outside the main menu and the debugger's
     * toolbar, as in the Frames view's context menu, a frame that cannot be dropped hides the item rather than disabling it.
     */
    private fun resetFrameOffered(project: Project): Boolean {
        var offered = false
        ApplicationManager.getApplication().invokeAndWait { offered = resetFrameEvent(project).presentation.isEnabledAndVisible }
        return offered
    }

    /** Reset Frame's event in the project, updated as its menu item is. */
    private fun resetFrameEvent(project: Project): AnActionEvent {
        val action = ActionManager.getInstance().getAction(RESET_FRAME)
        val context = SimpleDataContext.getProjectContext(project)
        return AnActionEvent.createEvent(action, context, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null).also(action::update)
    }

    /** Runs the module's paused session to the 1-based [line] of the file [uri], as Run to Cursor does. */
    @JvmStatic
    fun runToCursor(
        project: Project,
        module: String,
        uri: String,
        line: Int,
    ) {
        val session = checkNotNull(session(project, module)) { "No debug session of $module" }
        ApplicationManager.getApplication().invokeAndWait {
            val file = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByUrl(uri)) { "No file $uri" }
            val position = checkNotNull(XDebuggerUtil.getInstance().createPosition(file, line - 1)) { "No line $line in $uri" }
            session.runToPosition(position, false)
        }
    }

    /** Sets a line breakpoint with a [condition] on the 1-based [breakpointLine] of the module's source, and starts Debug. */
    @JvmStatic
    fun debugWhen(
        project: Project,
        module: String,
        uri: String,
        breakpointLine: Int,
        condition: String,
    ) {
        ApplicationManager.getApplication().invokeAndWait {
            val file = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByUrl(uri)) { "No file $uri" }
            val line = breakpointLine - 1

            @Suppress("UNCHECKED_CAST")
            val type =
                XDebuggerUtil.getInstance().lineBreakpointTypes.first { it.canPutAt(file, line, project) }
                    as XLineBreakpointType<XBreakpointProperties<*>>
            ApplicationManager.getApplication().runWriteAction {
                XDebuggerManager
                    .getInstance(project)
                    .breakpointManager
                    .addLineBreakpoint(type, file.url, line, type.createBreakpointProperties(file, line))
                    .setCondition(condition)
            }
        }
        debug(project, module, uri, 0)
    }

    /** Adds a line breakpoint on the 1-based [line] of the file [uri], as a click in its gutter does. */
    @JvmStatic
    fun addBreakpoint(
        project: Project,
        uri: String,
        line: Int,
    ) {
        ApplicationManager.getApplication().invokeAndWait {
            val file = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByUrl(uri)) { "No file $uri" }
            XDebuggerUtil.getInstance().toggleLineBreakpoint(project, file, line - 1)
        }
    }

    /**
     * The line breakpoints of the file [uri], in line order, as the module's debug session shows them in the gutter:
     * each one's 1-based `line` and its `state`, `verified` once the debug adapter installed it, `invalid` with the
     * `message` it gave if it did not, or `pending` before it says either.
     */
    @JvmStatic
    fun breakpoints(
        project: Project,
        module: String,
        uri: String,
    ): String {
        val session = checkNotNull(session(project, module) as? XDebugSessionImpl) { "No debug session of $module" }
        val states = JsonArray()
        ApplicationManager.getApplication().invokeAndWait {
            val file = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByUrl(uri)) { "No file $uri" }
            XDebuggerManager
                .getInstance(project)
                .breakpointManager.allBreakpoints
                .filterIsInstance<XLineBreakpoint<*>>()
                .filter { it.fileUrl == file.url }
                .sortedBy { it.line }
                .forEach { breakpoint ->
                    val shown = session.getBreakpointPresentation(breakpoint)
                    states.add(
                        JsonObject().apply {
                            addProperty("line", breakpoint.line + 1)
                            addProperty(
                                "state",
                                when {
                                    shown == null -> "pending"
                                    shown.errorMessage != null -> "invalid"
                                    else -> "verified"
                                },
                            )
                            shown?.errorMessage?.let { addProperty("message", it) }
                        },
                    )
                }
        }
        return states.toString()
    }

    /**
     * Adds a DAP function breakpoint on the function [name], with the [condition], as the Breakpoints dialog's "+" and
     * its DAP Function Breakpoint item do once the name is typed, through the type that LSP4IJ registers (#1780); the
     * probe finds it by its ID, since LSP4IJ 0.21.0, which it compiles against, has none. False without that type.
     */
    @JvmStatic
    fun addFunctionBreakpoint(
        project: Project,
        name: String,
        condition: String,
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        val type =
            XBreakpointType.EXTENSION_POINT_NAME.extensionList.firstOrNull { it.id == FUNCTION_BREAKPOINT }
                as? XBreakpointType<XBreakpoint<XBreakpointProperties<*>>, XBreakpointProperties<*>> ?: return false
        ApplicationManager.getApplication().invokeAndWait {
            val properties = checkNotNull(type.createProperties()) { "No properties for a function breakpoint" }
            properties.javaClass.getMethod("setName", String::class.java).invoke(properties, name)
            ApplicationManager.getApplication().runWriteAction {
                val breakpoint = XDebuggerManager.getInstance(project).breakpointManager.addBreakpoint(type, properties)
                breakpoint.conditionExpression = XExpressionImpl.fromText(condition)
            }
        }
        return true
    }

    /**
     * The function breakpoints, as the module's debug session shows them in the Breakpoints dialog: each one's `state`,
     * `verified` once the debug adapter installed it, `invalid` with the `message` it gave if it did not, or `pending`.
     */
    @JvmStatic
    fun functionBreakpoints(
        project: Project,
        module: String,
    ): String {
        val session = checkNotNull(session(project, module) as? XDebugSessionImpl) { "No debug session of $module" }
        val states = JsonArray()
        ApplicationManager.getApplication().invokeAndWait {
            XDebuggerManager
                .getInstance(project)
                .breakpointManager.allBreakpoints
                .filter { it.type.id == FUNCTION_BREAKPOINT }
                .forEach { breakpoint ->
                    val shown = session.getBreakpointPresentation(breakpoint)
                    states.add(
                        JsonObject().apply {
                            addProperty(
                                "state",
                                when {
                                    shown == null -> "pending"
                                    shown.errorMessage != null -> "invalid"
                                    else -> "verified"
                                },
                            )
                            shown?.errorMessage?.let { addProperty("message", it) }
                        },
                    )
                }
        }
        return states.toString()
    }

    /**
     * The inline values that the debugger draws at the ends of the lines of the file [uri] while the session is paused:
     * each one's 1-based `line` and its `text`, such as `n = 2`, in line order. IntelliJ's renderer is read by name,
     * since its class is not API.
     */
    @JvmStatic
    fun inlineValues(
        project: Project,
        uri: String,
    ): String {
        val values = JsonArray()
        ApplicationManager.getApplication().invokeAndWait {
            // the Variables view, as the Debug tool window shows it on a stop, computes the values that are drawn inline
            ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DEBUG)?.show()
            val file = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByUrl(uri)) { "No file $uri" }
            val document = checkNotNull(FileDocumentManager.getInstance().getDocument(file)) { "No document for $uri" }
            EditorFactory
                .getInstance()
                .getEditors(document, project)
                .firstOrNull()
                ?.inlayModel
                ?.getAfterLineEndElementsInRange(0, document.textLength)
                .orEmpty()
                .filter { it.renderer.javaClass.simpleName == "InlineDebugRenderer" }
                .forEach { inlay ->
                    val renderer = inlay.renderer
                    val text =
                        renderer.javaClass
                            .getMethod("getPresentation")
                            .invoke(renderer)
                            .toString()
                    values.add(
                        JsonObject().apply {
                            addProperty("line", document.getLineNumber(inlay.offset) + 1)
                            addProperty("text", text)
                        },
                    )
                }
        }
        return JsonArray().apply { values.sortedBy { it.asJsonObject["line"].asInt }.forEach(::add) }.toString()
    }

    /** The ID of LSP4IJ's DAP function breakpoint type (#1780). */
    private const val FUNCTION_BREAKPOINT = "dap-function-breakpoint"

    /** Types [text] at the start of the 1-based [line] of the file [uri], without saving it. */
    @JvmStatic
    fun insert(
        project: Project,
        uri: String,
        line: Int,
        text: String,
    ) {
        ApplicationManager.getApplication().invokeAndWait {
            val file = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByUrl(uri)) { "No file $uri" }
            val document = checkNotNull(FileDocumentManager.getInstance().getDocument(file)) { "No document of $uri" }
            WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.getLineStartOffset(line - 1), text) }
        }
    }

    /** Removes every line breakpoint of the project. */
    @JvmStatic
    fun clearBreakpoints(project: Project) {
        ApplicationManager.getApplication().invokeAndWait {
            val manager = XDebuggerManager.getInstance(project).breakpointManager
            ApplicationManager.getApplication().runWriteAction { manager.allBreakpoints.forEach(manager::removeBreakpoint) }
        }
    }

    private fun session(
        project: Project,
        module: String,
    ): XDebugSession? = sessions[name(module)]

    private fun name(module: String) = "Playbook Debug $module"

    /** The children of a frame, group or value, as the Variables view receives them. */
    private fun children(container: XValueContainer): XValueChildrenList {
        val done = CompletableFuture<XValueChildrenList>()
        val collected = XValueChildrenList()
        container.computeChildren(
            object : XCompositeNode {
                override fun addChildren(
                    children: XValueChildrenList,
                    last: Boolean,
                ) {
                    (0 until children.size()).forEach { collected.add(children.getName(it), children.getValue(it)) }
                    children.topGroups.forEach(collected::addTopGroup)
                    children.bottomGroups.forEach(collected::addBottomGroup)
                    if (last) done.complete(collected)
                }

                override fun tooManyChildren(remaining: Int) {
                    done.complete(collected)
                }

                override fun setAlreadySorted(alreadySorted: Boolean) {}

                override fun setErrorMessage(errorMessage: String) {
                    done.completeExceptionally(IllegalStateException(errorMessage))
                }

                override fun setErrorMessage(
                    errorMessage: String,
                    link: XDebuggerTreeNodeHyperlink?,
                ) = setErrorMessage(errorMessage)

                override fun setMessage(
                    message: String,
                    icon: Icon?,
                    attributes: SimpleTextAttributes,
                    link: XDebuggerTreeNodeHyperlink?,
                ) {}
            },
        )
        return done.get(30, TimeUnit.SECONDS)
    }

    /** A value's text, type, whether it expands, and the text of the link beside it, as the Variables view shows them. */
    private fun presentation(
        name: String,
        value: XValue,
    ): JsonObject {
        val shown = CompletableFuture<JsonObject>()
        val link = AtomicReference<XFullValueEvaluator>()

        fun complete(
            text: String,
            type: String?,
            expands: Boolean,
        ) = shown.complete(
            JsonObject().apply {
                addProperty("name", name)
                addProperty("value", text)
                type?.let { addProperty("type", it) }
                addProperty("expands", expands)
            },
        )
        value.computePresentation(
            object : XValueNode {
                override fun setPresentation(
                    icon: Icon?,
                    type: String?,
                    value: String,
                    hasChildren: Boolean,
                ) {
                    complete(value, type, hasChildren)
                }

                override fun setPresentation(
                    icon: Icon?,
                    presentation: XValuePresentation,
                    hasChildren: Boolean,
                ) {
                    val text = StringBuilder()
                    presentation.renderValue(TextOnly(text))
                    complete(text.toString(), presentation.type, hasChildren)
                }

                override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) {
                    link.set(fullValueEvaluator)
                }
            },
            XValuePlace.TREE,
        )
        // a link is set right after the presentation, as computePresentation returns
        return shown.get(30, TimeUnit.SECONDS).apply { link.get()?.let { addProperty("link", it.linkText) } }
    }

    /** Collects the text a value presentation renders, without its colors and comments; strings in quotes, as the view draws them. */
    private class TextOnly(
        private val text: StringBuilder,
    ) : XValuePresentation.XValueTextRenderer {
        override fun renderValue(value: String) {
            text.append(value)
        }

        override fun renderStringValue(value: String) {
            text.append('"').append(value).append('"')
        }

        override fun renderNumericValue(value: String) {
            text.append(value)
        }

        override fun renderKeywordValue(value: String) {
            text.append(value)
        }

        override fun renderValue(
            value: String,
            key: TextAttributesKey,
        ) {
            text.append(value)
        }

        override fun renderStringValue(
            value: String,
            additionalSpecialCharsToHighlight: String?,
            maxLength: Int,
        ) {
            text.append('"').append(value).append('"')
        }

        override fun renderComment(comment: String) {}

        override fun renderSpecialSymbol(symbol: String) {
            text.append(symbol)
        }

        override fun renderError(error: String) {
            text.append(error)
        }
    }

    private fun frames(stack: XExecutionStack): JsonArray =
        JsonArray().apply {
            stackFrames(stack).forEach { frame ->
                add(
                    JsonObject().apply {
                        addProperty("name", SimpleColoredText().also(frame::customizePresentation).toString())
                        frame.sourcePosition?.let { position ->
                            addProperty("file", position.file.path)
                            addProperty("line", position.line + 1)
                        }
                    },
                )
            }
        }

    /** The stack's frames, as the Frames view receives them. */
    private fun stackFrames(stack: XExecutionStack): List<XStackFrame> {
        val frames = CompletableFuture<List<XStackFrame>>()
        val collected = mutableListOf<XStackFrame>()
        stack.computeStackFrames(
            0,
            object : XExecutionStack.XStackFrameContainer {
                override fun addStackFrames(
                    stackFrames: List<XStackFrame>,
                    last: Boolean,
                ) {
                    collected += stackFrames
                    if (last) frames.complete(collected.toList())
                }

                override fun errorOccurred(errorMessage: String) {
                    frames.completeExceptionally(IllegalStateException(errorMessage))
                }
            },
        )
        return frames.get(30, TimeUnit.SECONDS)
    }
}
