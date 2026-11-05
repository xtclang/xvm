package org.xtclang.idea.playbook.probe

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.problems.WolfTheProblemSolver
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import org.eclipse.lsp4j.DiagnosticSeverity

/**
 * Loaded only in the disposable test IDE; never included in the distributed XTC plugin. IntelliJ's problem files, which
 * the Project view and the editor tabs mark red, and the errors LSP4IJ holds for a closed document.
 */
object ProblemFiles {
    /** Whether the file at [path] is one of the project's problem files. */
    @JvmStatic
    fun problem(
        project: Project,
        path: String,
    ): Boolean {
        val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return false
        return WolfTheProblemSolver.getInstance(project).isProblemFile(file)
    }

    /** The number of errors LSP4IJ holds for the file at [path] while it is closed; -1 when it holds none for it. */
    @JvmStatic
    fun closedErrors(
        project: Project,
        path: String,
    ): Int {
        val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return -1
        val server = LanguageServiceAccessor.getInstance(project).startedServers.singleOrNull() ?: return -1
        val closed = server.getClosedDocument(server.clientFeatures.getFileUri(file), false) ?: return -1
        return closed.diagnostics.count { it.severity == DiagnosticSeverity.Error }
    }
}
