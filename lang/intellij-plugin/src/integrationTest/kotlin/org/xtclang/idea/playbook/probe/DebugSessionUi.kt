package org.xtclang.idea.playbook.probe

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.codeInsight.hint.HintManager
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.SimpleColoredText
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
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
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.impl.evaluate.XEvaluationOrigin
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackWithOrigin
import org.xtclang.idea.run.XtcRunConfiguration
import org.xtclang.idea.run.XtcRunConfigurationType
import org.xtclang.idea.run.XtcRunMode
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.swing.Icon

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
        // a session that is still starting has no process yet
        val exitCode =
            session.debugProcess.processHandler
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
        val session = checkNotNull(session(project, module)) { "No debug session of $module" }
        val frame = checkNotNull(session.currentStackFrame) { "The debug session of $module is not paused" }
        val groups = children(frame).bottomGroups
        var shown = children(checkNotNull(groups.firstOrNull { it.name == "Locals" }) { "No Locals in ${groups.map { it.name }}" })
        path.split('/').filter { it.isNotEmpty() }.forEach { name ->
            val index = (0 until shown.size()).firstOrNull { shown.getName(it) == name } ?: error("No variable $name along $path")
            shown = children(shown.getValue(index))
        }
        return JsonArray().apply { (0 until shown.size()).forEach { add(presentation(shown.getName(it), shown.getValue(it))) } }.toString()
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

    /** A value's text, type and whether it expands, as the Variables view shows them. */
    private fun presentation(
        name: String,
        value: XValue,
    ): JsonObject {
        val shown = CompletableFuture<JsonObject>()

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

                override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) {}
            },
            XValuePlace.TREE,
        )
        return shown.get(30, TimeUnit.SECONDS)
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

    private fun frames(stack: XExecutionStack): JsonArray {
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
        return JsonArray().apply {
            frames.get(30, TimeUnit.SECONDS).forEach { frame ->
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
    }
}
