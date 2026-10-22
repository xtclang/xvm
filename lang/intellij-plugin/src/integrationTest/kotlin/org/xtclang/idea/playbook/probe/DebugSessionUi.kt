package org.xtclang.idea.playbook.probe

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.SimpleColoredText
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import org.xtclang.idea.run.XtcRunConfiguration
import org.xtclang.idea.run.XtcRunConfigurationType
import org.xtclang.idea.run.XtcRunMode
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Drives Debug of an Ecstasy Application configuration, through LSP4IJ's DAP client and the debug session in the
 * language server, and reports the session as the IDE shows it: whether it is paused, where, its frames, its console
 * and its exit code.
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
        ApplicationManager.getApplication().invokeAndWait {
            (session.consoleView as? ConsoleViewImpl)?.let { console ->
                // the console's editor is made with its component, which the Debug tool window may not have shown yet
                console.component
                console.flushDeferredText()
                state.addProperty("console", console.text)
            }
        }
        session.debugProcess.processHandler
            .takeIf { it.isProcessTerminated }
            ?.exitCode
            ?.let { state.addProperty("exitCode", it) }
        return state.toString()
    }

    /** Resumes the module's paused session, as the Resume Program action does. */
    @JvmStatic
    fun resume(
        project: Project,
        module: String,
    ) {
        checkNotNull(session(project, module)) { "No debug session of $module" }.resume()
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
