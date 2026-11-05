package org.xtclang.idea.run

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.xtclang.idea.lsp.BuildDiagnostic
import org.xtclang.idea.lsp.BuildResponse
import java.util.concurrent.atomic.AtomicReference

/**
 * The embedded build's errors in the Problems window's Project Errors: each error of the last build that has a file,
 * until the next build starts. The Build tool window lists them as well, but IntelliJ fills Project Errors from a build
 * view only where the view is connected to its internal BuildViewProblemsService, as Gradle's and Maven's run views
 * are, and BuildViewManager's, which the embedded build reports to, is not.
 */
@Service(Service.Level.PROJECT)
internal class EmbeddedBuildProblems(
    override val project: Project,
) : ProblemsProvider {
    private val reported = AtomicReference<List<FileProblem>>(emptyList())

    /** Takes the previous build's errors out of Project Errors, as a build starts. */
    fun started() {
        val collector = ProblemsCollector.getInstance(project)
        reported.getAndSet(emptyList()).forEach(collector::problemDisappeared)
    }

    /** Puts [build]'s errors that have a file into Project Errors, each at its file's line and column. */
    fun report(build: BuildResponse) {
        val problems =
            build.diagnostics.flatMap { document ->
                val file =
                    EmbeddedExecution.file(document.uri)?.let(LocalFileSystem.getInstance()::findFileByNioFile)
                        ?: return@flatMap emptyList()
                document.diagnostics.filter { it.severity !in WARNINGS_AND_BELOW }.map { BuildProblem(this, file, it) }
            }
        reported.updateAndGet { it + problems }
        val collector = ProblemsCollector.getInstance(project)
        problems.forEach(collector::problemAppeared)
    }

    companion object {
        /** The LSP severities that are no error: 2 warning, 3 information, 4 hint. */
        private val WARNINGS_AND_BELOW = 2..4

        fun getInstance(project: Project): EmbeddedBuildProblems = project.service()
    }
}

/** An error of the embedded build, at its [file]'s line and column, its message's first line as its text. */
private class BuildProblem(
    override val provider: ProblemsProvider,
    override val file: VirtualFile,
    diagnostic: BuildDiagnostic,
) : FileProblem {
    // Problem is @ApiStatus.Experimental: no BuildViewProblemsService takes BuildViewManager's errors to Project Errors
    override val text: String = diagnostic.message.lineSequence().first()
    override val description: String = diagnostic.message + diagnostic.code?.let { " [$it]" }.orEmpty()
    override val line: Int = diagnostic.range.start.line
    override val column: Int = diagnostic.range.start.character
}
