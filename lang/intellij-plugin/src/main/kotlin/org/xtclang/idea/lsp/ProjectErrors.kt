package org.xtclang.idea.lsp

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.HighlightingDuplicateProblem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.xtclang.idea.run.EmbeddedExecution

/**
 * The Problems window's Project Errors for Ecstasy files: each file's errors as the newer of its two sources has them,
 * the language server's report of the file or the embedded build. A report of a file, an empty one included, replaces
 * the build's errors in it, and a build's errors in a file replace the report's, so a closed file's errors are listed
 * without a Build, and once after one.
 *
 * IntelliJ fills Project Errors from its ProblemsCollector, which neither of them reaches on its own: LSP4IJ reports a
 * file with errors to the problem files that the Project view marks red, and nothing to the collector, and IntelliJ
 * takes a build's errors to it only from a build view's file messages, through its internal BuildViewProblemsService:
 * 2026.2 from Gradle's and Maven's views, 2026.3 from every view, BuildViewManager's included. The embedded build
 * reports its errors to the Build window as messages that go to their file and line, not as file messages
 * ([XtcProjectTaskRunner][org.xtclang.idea.run.XtcProjectTaskRunner]), so that on each version this provider is the
 * only source of Project Errors for Ecstasy files.
 *
 * Errors are values, so the collector, which keeps a set of each file's problems, lists equal ones once, whichever
 * source reported them. The build's are [HighlightingDuplicateProblem]s, which IntelliJ leaves out for a file whose
 * highlighting has problems in the collector, as it does a Gradle or Maven build's.
 */
@Service(Service.Level.PROJECT)
internal class ProjectErrors(
    override val project: Project,
) : ProblemsProvider {
    private val lock = Any()

    /** Each file's errors in Project Errors, all from one source, replaced as a whole; guarded by [lock]. */
    private var listed = emptyMap<VirtualFile, Set<ProjectError>>()

    /**
     * The server's report of [file], its diagnostics as they are now: its errors replace those listed for it. Each
     * diagnostic's text is what [message] reads, as LSP4IJ's diagnostic feature does: the plugin compiles against the
     * IDE's LSP4J 0.24, whose Diagnostic.getMessage is a String, and LSP4IJ's diagnostics are its own LSP4J 1.0.0's,
     * whose message is an Either, so the plugin cannot read it itself.
     */
    fun reported(
        file: VirtualFile,
        diagnostics: List<Diagnostic>,
        message: (Diagnostic) -> String,
    ) = list(file, diagnostics.filter { it.severity in ERRORS }.map { ReportedError(this, file, it, message(it)) })

    /** The server's connection ended: the errors of its reports leave Project Errors, as LSP4IJ's leave the problem files. */
    fun disconnected() = clear<ReportedError>()

    /** A build starts: the previous build's errors leave Project Errors, as its messages leave the Build window. */
    fun buildStarted() = clear<BuildError>()

    /** [build]'s errors that have a file: each file's replace those listed for it. */
    fun built(build: BuildResponse) =
        build.diagnostics
            .mapNotNull { document ->
                EmbeddedExecution.file(document.uri)?.let(LocalFileSystem.getInstance()::findFileByNioFile)?.to(document)
            }.groupBy({ it.first }, { it.second })
            .forEach { (file, documents) ->
                val errors = documents.flatMap { it.diagnostics }.filter { it.severity !in WARNINGS_AND_BELOW }
                list(file, errors.map { BuildError(this, file, it) })
            }

    /** Takes [T]'s source's errors out of Project Errors, from each file whose errors are its. */
    private inline fun <reified T : ProjectError> clear() =
        synchronized(lock) { listed.filterValues { errors -> errors.any { it is T } }.keys.forEach { list(it, emptyList()) } }

    /** Lists [errors] as [file]'s, in place of those listed for it. */
    private fun list(
        file: VirtualFile,
        errors: List<ProjectError>,
    ) {
        synchronized(lock) {
            if (project.isDisposed) return
            val collector = ProblemsCollector.getInstance(project)
            val before = listed[file].orEmpty()
            val after = errors.toSet()
            listed = if (after.isEmpty()) listed - file else listed + (file to after)
            (before - after).forEach(collector::problemDisappeared)
            // an equal error of the other source takes the place of the one listed, which the collector updates
            after.filter { error -> before.none { it == error && it.javaClass == error.javaClass } }.forEach(collector::problemAppeared)
        }
    }

    companion object {
        /** The severities of an error in a server's report: one omitted is the client's to choose, and LSP4IJ's is an error. */
        private val ERRORS = setOf(DiagnosticSeverity.Error, null)

        /** The LSP severities of a build's diagnostic that is no error: 2 warning, 3 information, 4 hint. */
        private val WARNINGS_AND_BELOW = 2..4

        fun getInstance(project: Project): ProjectErrors = project.service()
    }
}

/**
 * An error in Project Errors, at its [file]'s line and column, its message's first line as its text. It is a value:
 * two errors with the same file, place, severity, message and code are one problem, whichever source reported them.
 */
internal sealed class ProjectError(
    final override val provider: ProblemsProvider,
    final override val file: VirtualFile,
    final override val line: Int,
    final override val column: Int,
    /** The LSP severity, 1 for an error. */
    val severity: Int,
    val message: String,
    val code: String?,
) : FileProblem {
    // Problem's contextGroup is @ApiStatus.Experimental; it is left to its default
    final override val text: String
        get() = message.lineSequence().first()

    final override val description: String
        get() = message + code?.let { " [$it]" }.orEmpty()

    private val value
        get() = listOf(file, line, column, severity, message, code)

    final override fun equals(other: Any?): Boolean = other is ProjectError && value == other.value

    final override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "${file.name}:${line + 1}:${column + 1}: $text"
}

/** An error of the server's report of its file. */
private class ReportedError(
    provider: ProblemsProvider,
    file: VirtualFile,
    diagnostic: Diagnostic,
    message: String,
) : ProjectError(
        provider,
        file,
        diagnostic.range.start.line,
        diagnostic.range.start.character,
        diagnostic.severity?.value ?: DiagnosticSeverity.Error.value,
        message,
        diagnostic.code?.let { it.left ?: it.right?.toString() },
    )

/**
 * An error of the embedded build, which IntelliJ leaves out for a file whose highlighting has problems in the
 * collector. HighlightingDuplicateProblem is @ApiStatus.Experimental; the verifier fails on no experimental API.
 */
private class BuildError(
    provider: ProblemsProvider,
    file: VirtualFile,
    diagnostic: BuildDiagnostic,
) : ProjectError(
        provider,
        file,
        diagnostic.range.start.line,
        diagnostic.range.start.character,
        diagnostic.severity ?: DiagnosticSeverity.Error.value,
        diagnostic.message,
        diagnostic.code,
    ),
    HighlightingDuplicateProblem
