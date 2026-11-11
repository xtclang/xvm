package org.xtclang.idea.lsp

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.build.events.MessageEvent
import com.intellij.build.progress.BuildProgress
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.xtclang.idea.LightEditor
import org.xtclang.idea.run.EmbeddedBuild
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

/**
 * The embedded build's errors in the Problems window's Project Errors, which IntelliJ fills from no BuildViewManager
 * build: each error that has a file, in that file, until the next build starts.
 */
class ProjectErrorsTest {
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
        val problems = ProjectErrors.getInstance(project)
        val collector = ProblemsCollector.getInstance(project)

        problems.buildStarted()
        problems.built(
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
        problems.buildStarted()
        problems.built(BuildResponse(succeeded = true))
        assertThat(collector.getFileProblems(eights.file)).isEmpty()
        assertThat(collector.getFileProblems(hand.file)).isEmpty()
    }

    @Test
    fun `a build's error goes to the Build window as a message to its file and line, and to Project Errors once`() {
        val hand = source("Hand.x")
        val project = editor.fixture.project
        val build =
            BuildResponse(diagnostics = listOf(DocumentDiagnostics(hand.uri, listOf(error("Expected \";\"\nbefore }", 18, "COMPILER-12")))))
        val calls = buildList { EmbeddedBuild(project, false).report(recording { name, args -> add(name to args) }, build) }

        // a message, not a file message, which IntelliJ 2026.3 would copy into Project Errors beside the provider's
        val messages = calls.filter { it.first == "message" || it.first == "fileMessage" }
        assertThat(messages.map { it.first }).containsExactly("message")
        val (title, message, kind, navigatable) = messages.single().second
        assertThat(title).isEqualTo("Hand.x:19:5: Expected \";\"")
        assertThat(message as String).endsWith("Hand.x:19:5\nExpected \";\"\nbefore } [COMPILER-12]")
        assertThat(kind).isEqualTo(MessageEvent.Kind.ERROR)
        val target = navigatable as OpenFileDescriptor
        assertThat(listOf(target.file, target.line, target.column)).containsExactly(hand.file, 18, 4)
        assertThat(ProblemsCollector.getInstance(project).getFileProblems(hand.file).map { it.text }).containsExactly("Expected \";\"")

        ProjectErrors.getInstance(project).buildStarted()
    }

    /** A Build window's progress that hands each call, by its method's name, to [record]. */
    private fun recording(record: (String, List<Any?>) -> Unit): BuildProgress<BuildProgressDescriptor> {
        val progress =
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(BuildProgress::class.java)) { proxy, method, args ->
                record(method.name, args?.toList().orEmpty())
                proxy
            }
        @Suppress("UNCHECKED_CAST")
        return progress as BuildProgress<BuildProgressDescriptor>
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
