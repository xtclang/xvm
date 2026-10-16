package org.xtclang.idea.run

import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.FilePosition
import com.intellij.build.events.MessageEvent
import com.intellij.build.progress.BuildProgress
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FileTypeIndex
import com.intellij.task.ModuleBuildTask
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskRunner
import com.intellij.task.TaskRunnerResults
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.xtclang.idea.XtcFileType
import org.xtclang.idea.lsp.BuildParams
import org.xtclang.idea.lsp.BuildResponse
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 * Build and Rebuild Project in embedded mode: the language server's compiler builds every source module
 * through its compile queue, and the Build tool window shows the outcome with each diagnostic linked to its
 * source. In Gradle mode these tasks stay with Gradle or the IDE's own builder.
 */
class XtcProjectTaskRunner : ProjectTaskRunner() {
    override fun canRun(
        project: Project,
        task: ProjectTask,
        context: ProjectTaskContext?,
    ): Boolean = task is ModuleBuildTask && XtcBuildSettings.effective(project) == XtcBuildMode.EMBEDDED && hasEcstasySources(task.module)

    override fun run(
        project: Project,
        context: ProjectTaskContext,
        vararg tasks: ProjectTask,
    ): Promise<Result> {
        val promise = AsyncPromise<Result>()
        // the compiler builds all source modules in dependency order, so one build covers every task
        EmbeddedBuild(project, tasks.any { it is ModuleBuildTask && !it.isIncrementalBuild })
            .start()
            .whenComplete { result, _ -> promise.setResult(result ?: TaskRunnerResults.FAILURE) }
        return promise
    }

    /** While indexing, any module may hold Ecstasy sources; claiming it then keeps Build from doing nothing. */
    private fun hasEcstasySources(module: Module): Boolean =
        ReadAction.computeBlocking<Boolean, RuntimeException> {
            DumbService.isDumb(module.project) || FileTypeIndex.containsFileOfType(XtcFileType, module.moduleContentScope)
        }
}

/** One embedded build of every source module, reported in the Build tool window. */
internal class EmbeddedBuild(
    private val project: Project,
    private val rebuild: Boolean,
) {
    fun start(): CompletableFuture<TaskRunnerResults> {
        EmbeddedExecution.saveAll()
        val title = if (rebuild) "Rebuild Ecstasy modules" else "Build Ecstasy modules"
        val descriptor = DefaultBuildDescriptor(Any(), title, project.basePath.orEmpty(), System.currentTimeMillis())
        val progress =
            BuildViewManager.createBuildProgress(project).start(
                object : BuildProgressDescriptor {
                    override fun getTitle() = title

                    override fun getBuildDescriptor() = descriptor
                },
            )
        progress.output("${if (rebuild) "Rebuilding" else "Building"} with the embedded compiler...\n", ProcessOutputType.STDOUT)
        XtcBuildSettings.gradleNotUsed(project.basePath?.let(Path::of))?.let { progress.output("$it\n", ProcessOutputType.STDOUT) }
        return EmbeddedExecution
            .server(project)
            .thenCompose { it.build(BuildParams(rebuild = rebuild)) }
            .handle { build, failure ->
                if (failure != null) {
                    val cause = if (failure is CompletionException) failure.cause else failure
                    progress.output("Build failed: ${cause?.message}\n", ProcessOutputType.STDERR)
                    progress.fail()
                    TaskRunnerResults.FAILURE
                } else {
                    report(progress, build)
                    when {
                        build.cancelled -> TaskRunnerResults.ABORTED.also { progress.cancel() }
                        build.succeeded -> TaskRunnerResults.SUCCESS.also { progress.finish() }
                        else -> TaskRunnerResults.FAILURE.also { progress.fail() }
                    }
                }
            }
    }

    private fun report(
        progress: BuildProgress<BuildProgressDescriptor>,
        build: BuildResponse,
    ) {
        EmbeddedExecution.describe(build.copy(diagnostics = emptyList())).forEach { progress.output("$it\n", ProcessOutputType.STDOUT) }
        build.diagnostics.forEach { document ->
            val file = EmbeddedExecution.file(document.uri)
            document.diagnostics.forEach { diagnostic ->
                val message = diagnostic.message + diagnostic.code?.let { " [$it]" }.orEmpty()
                val title = diagnostic.message.lineSequence().first()
                val kind = kind(diagnostic.severity)
                if (file == null) {
                    // nothing to navigate to, so the diagnostic stays in the build output
                    progress.output("${document.uri}: $message\n", ProcessOutputType.STDERR)
                } else {
                    val start = diagnostic.range.start
                    progress.fileMessage(title, message, kind, FilePosition(file, start.line, start.character))
                }
            }
        }
    }

    /** LSP severities: 1 error, 2 warning, 3 information, 4 hint. */
    private fun kind(severity: Int?): MessageEvent.Kind =
        when (severity) {
            2 -> MessageEvent.Kind.WARNING
            3, 4 -> MessageEvent.Kind.INFO
            else -> MessageEvent.Kind.ERROR
        }
}
