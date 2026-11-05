package org.xtclang.idea.run

import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.FilePosition
import com.intellij.build.events.MessageEvent
import com.intellij.build.progress.BuildProgress
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.execution.process.ProcessOutputType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.task.ModuleBuildTask
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskRunner
import com.intellij.task.TaskRunnerResults
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.xtclang.idea.XtcFileType
import org.xtclang.idea.lsp.BuildParams
import org.xtclang.idea.lsp.BuildResponse
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 * Build and Rebuild Project in embedded mode: the language server's compiler builds every source module
 * through its compile queue, and the Build tool window shows the outcome with each diagnostic linked to its
 * source. In Gradle mode these tasks stay with Gradle or the IDE's own builder. The plugin registers it before
 * Gradle's runner, which takes every module of a Gradle project that IntelliJ imported: after it, no module of
 * such a project would come here, and Build Project would run Gradle's `classes` and `testClasses`. A module
 * with Java sources stays with the runner after it, which compiles them; the embedded build compiles no Java.
 */
class XtcProjectTaskRunner : ProjectTaskRunner() {
    override fun canRun(
        project: Project,
        task: ProjectTask,
        context: ProjectTaskContext?,
    ): Boolean =
        task is ModuleBuildTask &&
            XtcBuildSettings.effective(project) == XtcBuildMode.EMBEDDED &&
            ReadAction.computeBlocking<Boolean, RuntimeException> { builds(task.module) }

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

    /**
     * Whether the embedded build is the [module]'s builder: when it has Ecstasy sources and no Java ones, or no
     * source roots at all. A module with no source roots of its own, such as the root module of a Gradle build that
     * IntelliJ imported, holds nothing that another builder would build: left to Gradle's runner, building it runs
     * the Gradle build, which embedded mode is not to do, and the embedded build covers every source module. A
     * module with Java files, or with a Java source root that has no Ecstasy files, such as Gradle's `src/main/java`,
     * is for Gradle's runner, or the IDE's builder, to build: with the XTC Gradle plugin, Gradle compiles its
     * Ecstasy sources too. A Java source root that has Ecstasy files is where they are, as a Sources Root marked on
     * `src/main/x`. While indexing, any module without a Java source root may hold Ecstasy sources; claiming it then
     * keeps Build from doing nothing.
     */
    private fun builds(module: Module): Boolean {
        val roots = ModuleRootManager.getInstance(module)
        val javaRoots = roots.getSourceRoots(JavaModuleSourceRootTypes.SOURCES)
        if (DumbService.isDumb(module.project)) return javaRoots.isEmpty()
        val content = module.moduleContentScope
        return !FileTypeIndex.containsFileOfType(JavaFileType.INSTANCE, content) &&
            javaRoots.all { root ->
                FileTypeIndex.containsFileOfType(XtcFileType, GlobalSearchScopesCore.directoryScope(module.project, root, true))
            } &&
            (roots.sourceRoots.isEmpty() || FileTypeIndex.containsFileOfType(XtcFileType, content))
    }
}

/** One embedded build of every source module, reported in the Build tool window. */
internal class EmbeddedBuild(
    private val project: Project,
    private val rebuild: Boolean,
) {
    fun start(): CompletableFuture<TaskRunnerResults> {
        val editsSent = EmbeddedExecution.editsSent(project)
        val title = if (rebuild) "Rebuild Ecstasy modules" else "Build Ecstasy modules"
        val descriptor =
            DefaultBuildDescriptor(Any(), title, project.basePath.orEmpty(), System.currentTimeMillis())
                .withExecutionFilter(BuildSettingsLink)
        val progress =
            BuildViewManager.createBuildProgress(project).start(
                object : BuildProgressDescriptor {
                    override fun getTitle() = title

                    override fun getBuildDescriptor() = descriptor
                },
            )
        // the previous build's errors leave Project Errors as this one starts, as a build view's leave the Build window
        EmbeddedBuildProblems.getInstance(project).started()
        progress.output("${if (rebuild) "Rebuilding" else "Building"} with the embedded compiler...\n", ProcessOutputType.STDOUT)
        XtcBuildSettings.gradleNotUsed(project.basePath?.let(Path::of))?.let { progress.output("$it\n", ProcessOutputType.STDOUT) }
        return EmbeddedExecution
            .server(project)
            .thenCombine(editsSent) { server, _ -> server }
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
        EmbeddedBuildProblems.getInstance(project).report(build)
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
