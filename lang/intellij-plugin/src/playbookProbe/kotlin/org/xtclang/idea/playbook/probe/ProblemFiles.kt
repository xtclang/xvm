package org.xtclang.idea.playbook.probe

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.toolWindow.HighlightingPanel
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.problems.WolfTheProblemSolver
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import org.eclipse.lsp4j.DiagnosticSeverity
import java.nio.file.Path

/**
 * Loaded only in the disposable test IDE; never included in the distributed XTC plugin. IntelliJ's problem files, which
 * the Project view and the editor tabs mark red, the errors LSP4IJ holds for a closed document, and what the Problems
 * window's File and Project Errors tabs list. The Problems window's classes are the same in IntelliJ 2026.2 and 2026.3,
 * where they moved into a plugin of their own, so these read it on both, which the driver's helpers of Starter 262 and
 * 263 do not.
 */
object ProblemFiles {
    /** Whether the Problems window is visible. Called on the EDT. */
    @JvmStatic
    fun windowVisible(project: Project): Boolean = ProblemsViewToolWindowUtils.getToolWindow(project)?.isVisible == true

    /** Selects the Problems window's File tab, which lists the problems of the file in the editor. Called on the EDT. */
    @JvmStatic
    fun selectFileTab(project: Project) = ProblemsViewToolWindowUtils.selectTab(project, HighlightingPanel.ID)

    /**
     * What the File tab lists for the file at [path], each as its zero-based line and column, `line:column`, in line and
     * column order; null while the tab shows another file or none. Called on the EDT.
     */
    @JvmStatic
    fun fileTab(
        project: Project,
        path: String,
    ): List<String>? {
        val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return null
        val panel = ProblemsViewToolWindowUtils.getTabById(project, HighlightingPanel.ID) as? HighlightingPanel ?: return null
        // the tab's root for its file is a ProblemsCollector of that file's problems, on 2026.2 and 2026.3 alike
        val root = panel.currentRoot?.takeIf { it.file == file } as? ProblemsCollector ?: return null
        return root
            .getFileProblems(file)
            .filterIsInstance<FileProblem>()
            .sortedWith(compareBy({ it.line }, { it.column }))
            .map { "${it.line}:${it.column}" }
    }

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

    /**
     * What Project Errors lists in the file at [path], each as its one-based line and column and its text: in every
     * file that is the same file on disk, so that an error listed under another spelling of its path counts too.
     */
    @JvmStatic
    fun projectErrors(
        project: Project,
        path: String,
    ): List<String> = listed(project, path).map(::entry)

    /** [projectErrors], each with the class of the provider that reported it, for a run's record or a failure's message. */
    @JvmStatic
    fun projectErrorSources(
        project: Project,
        path: String,
    ): List<String> = listed(project, path).map { "${entry(it)} [${it.provider.javaClass.simpleName}]" }

    private fun listed(
        project: Project,
        path: String,
    ): List<Problem> {
        val real = runCatching { Path.of(path).toRealPath() }.getOrNull() ?: return emptyList()
        val collector = ProblemsCollector.getInstance(project)
        return collector
            .getProblemFiles()
            .filter { file -> file.isInLocalFileSystem && runCatching { file.toNioPath().toRealPath() == real }.getOrDefault(false) }
            .flatMap(collector::getFileProblems)
    }

    private fun entry(problem: Problem): String {
        val place = (problem as? FileProblem)?.let { "${it.line + 1}:${it.column + 1}" } ?: "-"
        return "$place ${problem.text}"
    }
}
