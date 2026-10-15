package org.xtclang.idea.playbook.probe

import com.google.gson.JsonObject
import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.task.ProjectTaskManager
import org.jetbrains.concurrency.Promise
import org.xtclang.idea.run.XtcBuildMode
import org.xtclang.idea.run.XtcBuildSettings
import org.xtclang.idea.run.XtcRunConfiguration
import org.xtclang.idea.run.XtcRunConfigurationType
import org.xtclang.idea.run.XtcRunMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Drives embedded Build and Run through the IDE's own Build Project, run configurations and Run lens,
 * and records what the Build tool window and Run console receive.
 */
object EmbeddedExecutionUi {
    /** One run's console text and exit code, captured from the moment its process starts. */
    private class Capture : ProcessListener {
        val text = StringBuffer()

        @Volatile var handler: ProcessHandler? = null

        @Volatile var exitCode: Int? = null

        override fun onTextAvailable(
            event: ProcessEvent,
            outputType: Key<*>,
        ) {
            text.append(event.text)
        }

        override fun processTerminated(event: ProcessEvent) {
            exitCode = event.exitCode
        }
    }

    private val runs = ConcurrentHashMap<String, Capture>()
    private val watched = ConcurrentHashMap.newKeySet<Project>()

    /** Sets the project's build mode and returns the previous one. */
    @JvmStatic
    fun mode(
        project: Project,
        value: String,
    ): String {
        val settings = XtcBuildSettings.getInstance(project)
        val previous = settings.mode
        settings.mode = XtcBuildMode.valueOf(value)
        return previous.name
    }

    /** Runs Build Project and returns its result and the Build tool window's output and file messages. */
    @JvmStatic
    fun build(
        project: Project,
        timeoutSeconds: Int,
    ): String {
        val text = StringBuffer()
        val disposable = Disposer.newDisposable("Ecstasy playbook build")
        try {
            project.getService(BuildViewManager::class.java).addListener(
                BuildProgressListener { _, event ->
                    when (event) {
                        is FileMessageEvent -> {
                            val position = event.filePosition
                            val kind = if (event.kind == MessageEvent.Kind.ERROR) "error" else event.kind.name.lowercase()
                            val name = position.path?.fileName
                            text.append("$name:${position.startLine + 1}:${position.startColumn + 1}: $kind: ${event.message}\n")
                        }

                        is OutputBuildEvent -> {
                            text.append(event.message)
                        }
                    }
                },
                disposable,
            )
            var promise: Promise<ProjectTaskManager.Result>? = null
            ApplicationManager.getApplication().invokeAndWait { promise = ProjectTaskManager.getInstance(project).buildAllModules() }
            val result = requireNotNull(promise).blockingGet(timeoutSeconds, TimeUnit.SECONDS)
            return JsonObject()
                .apply {
                    addProperty("aborted", result?.isAborted ?: true)
                    addProperty("errors", result?.hasErrors() ?: true)
                    addProperty("text", text.toString())
                }.toString()
        } finally {
            Disposer.dispose(disposable)
        }
    }

    /** Runs [module] through an Ecstasy Application configuration that follows the project mode. */
    @JvmStatic
    fun run(
        project: Project,
        module: String,
        uri: String,
        arguments: String,
    ) {
        watch(project)
        runs.remove(module)
        ApplicationManager.getApplication().invokeAndWait {
            val runManager = RunManager.getInstance(project)
            val factory = ConfigurationTypeUtil.findConfigurationType(XtcRunConfigurationType::class.java).configurationFactories.first()
            val settings = runManager.createConfiguration("Playbook $module", factory)
            (settings.configuration as XtcRunConfiguration).apply {
                moduleName = module
                moduleUri = uri
                moduleArguments = arguments
                runWith = XtcRunMode.PROJECT
            }
            runManager.setTemporaryConfiguration(settings)
            ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
        }
    }

    /** Whether the Run and Debug executors have a runner for the module's Ecstasy Application configuration. */
    @JvmStatic
    fun runners(
        project: Project,
        module: String,
    ): String {
        val factory = ConfigurationTypeUtil.findConfigurationType(XtcRunConfigurationType::class.java).configurationFactories.first()
        val configuration =
            (factory.createTemplateConfiguration(project) as XtcRunConfiguration).apply {
                moduleName = module
                runWith = XtcRunMode.PROJECT
            }
        return JsonObject()
            .apply {
                addProperty("run", ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, configuration) != null)
                addProperty("debug", ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, configuration) != null)
            }.toString()
    }

    /** Starts capturing runs for a module whose Run lens the driver is about to click. */
    @JvmStatic
    fun expect(
        project: Project,
        module: String,
    ) {
        watch(project)
        runs.remove(module)
    }

    @JvmStatic
    fun output(module: String): String = runs[module]?.text?.toString().orEmpty()

    /** The run's exit code, or `Int.MIN_VALUE` while it is unknown or still running. */
    @JvmStatic
    fun exitCode(module: String): Int = runs[module]?.exitCode ?: Int.MIN_VALUE

    /** Presses Rerun for the module's run, as the Run tool window does; the new run replaces its capture. */
    @JvmStatic
    fun rerun(
        project: Project,
        module: String,
    ) {
        val handler = requireNotNull(runs[module]?.handler) { "No run of $module has started" }
        ApplicationManager.getApplication().invokeAndWait {
            val descriptor = RunContentManager.getInstance(project).allDescriptors.single { it.processHandler === handler }
            runs.remove(module)
            ExecutionUtil.restart(descriptor)
        }
    }

    /** Presses Stop for the module's run. */
    @JvmStatic
    fun stop(module: String) {
        val handler = requireNotNull(runs[module]?.handler) { "No run of $module has started" }
        ApplicationManager.getApplication().invokeAndWait { handler.destroyProcess() }
    }

    private fun watch(project: Project) {
        if (!watched.add(project)) return
        project.messageBus.connect().subscribe(
            ExecutionManager.EXECUTION_TOPIC,
            object : ExecutionListener {
                override fun processStarting(
                    executorId: String,
                    env: ExecutionEnvironment,
                    handler: ProcessHandler,
                ) {
                    val configuration = env.runProfile as? XtcRunConfiguration ?: return
                    val capture = Capture().also { it.handler = handler }
                    handler.addProcessListener(capture)
                    runs[configuration.moduleName] = capture
                }
            },
        )
    }
}
