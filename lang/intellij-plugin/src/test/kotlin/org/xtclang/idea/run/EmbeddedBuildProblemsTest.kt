package org.xtclang.idea.run

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.xtclang.idea.LightEditor
import org.xtclang.idea.lsp.BuildDiagnostic
import org.xtclang.idea.lsp.BuildPosition
import org.xtclang.idea.lsp.BuildRange
import org.xtclang.idea.lsp.BuildResponse
import org.xtclang.idea.lsp.DocumentDiagnostics
import java.nio.file.Files
import java.nio.file.Path

/**
 * The embedded build's errors in the Problems window's Project Errors, which IntelliJ fills from no BuildViewManager
 * build: each error that has a file, in that file, until the next build starts.
 */
class EmbeddedBuildProblemsTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    @TempDir
    lateinit var sources: Path

    @Test
    fun `a failed build's errors are in Project Errors, each in its own file, until the next build clears them`() {
        val eights = source("Eights.x")
        val hand = source("Hand.x")
        val project = editor.fixture.project
        val problems = EmbeddedBuildProblems.getInstance(project)
        val collector = ProblemsCollector.getInstance(project)

        problems.started()
        problems.report(
            BuildResponse(
                diagnostics =
                    listOf(
                        DocumentDiagnostics(eights.uri, listOf(error("Could not find \"popCrd\"\nfor type Hand", 64, "COMPILER-56"))),
                        DocumentDiagnostics(hand.uri, listOf(error("Expected \";\"", 18), warning("Unused local"))),
                    ),
            ),
        )

        val inEights = collector.getFileProblems(eights.file)
        assertThat(inEights.map { it.text }).containsExactly("Could not find \"popCrd\"")
        assertThat(inEights.map { (it as FileProblem).line }).containsExactly(64)
        assertThat(inEights.map { it.description }).containsExactly("Could not find \"popCrd\"\nfor type Hand [COMPILER-56]")
        // the second file's error is its own, and a warning is no Project Error
        assertThat(collector.getFileProblems(hand.file).map { it.text }).containsExactly("Expected \";\"")

        // the next build, which succeeds, starts without them
        problems.started()
        problems.report(BuildResponse(succeeded = true))
        assertThat(collector.getFileProblems(eights.file)).isEmpty()
        assertThat(collector.getFileProblems(hand.file)).isEmpty()
    }

    /** A source file on the local file system, as the embedded build names it, by its `file:` URI. */
    private fun source(name: String): Source {
        val path = Files.writeString(sources.resolve(name), "module ${name.substringBefore('.')} {}\n")
        return Source(path.toUri().toString(), checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)))
    }

    private data class Source(
        val uri: String,
        val file: VirtualFile,
    )

    private fun error(
        message: String,
        line: Int,
        code: String? = null,
    ) = BuildDiagnostic(BuildRange(BuildPosition(line, 4), BuildPosition(line, 10)), 1, message, code)

    private fun warning(message: String) = BuildDiagnostic(BuildRange(), 2, message)
}
