package org.xtclang.idea.lsp

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.HighlightingDuplicateProblem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.codeInsight.daemon.impl.MockWolfTheProblemSolver
import com.intellij.codeInsight.daemon.impl.WolfTheProblemSolverImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticUtils
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.xtclang.idea.LightEditor
import org.xtclang.idea.StubLanguageServer
import org.xtclang.idea.awaitUntil
import org.xtclang.idea.awaited
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

// TODO LSP4IJ: UP69 — LSP4IJ 0.21.0 asks for no workspace report, so only ClosedDocumentDiagnostics gets a closed
// file's errors to IntelliJ. Keep these tests when the bridge goes: they check what IntelliJ shows, whichever of
// LSP4IJ and the plugin asks for the report.
// TODO LSP4IJ: UP70 — LSP4IJ keeps a file red that opens with no errors; "a file that opens with its error fixed"
// checks the plugin's report of it, and stays when that report goes.
// TODO LSP4IJ: UP70 — also LSP4IJ's clear of a closing file's mark can come after the report of its errors; "a closed
// file stays a problem file when LSP4IJ clears its mark last" checks the plugin's report after it, and stays too.

/**
 * The errors of an Ecstasy file that is not open, from the server's workspace report, make it one of IntelliJ's problem
 * files, the files the Project view marks red, and LSP4IJ keeps them as the closed document's; open files keep the
 * diagnostics LSP4IJ pulls for them. The Problems window's Project Errors lists them too, without a build, and once
 * after one. The server is the stub, which reports what each test gives it, and the client is the plugin's own;
 * IntelliJ's problem files are a real WolfTheProblemSolver's, and Project Errors its ProblemsCollector's.
 */
class ClosedFileProblemsTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor(StubLanguageServer(diagnostics = true, client = ::XtcLanguageClient))

    @TempDir
    lateinit var directory: Path

    private val server: StubLanguageServer
        get() = editor.server

    @Test
    fun `a closed file's error makes it a problem file until an edit elsewhere fixes it`() {
        val problems = problemFiles()
        val root = file("Root.x", "module Root {}\n")
        val child = file("Root/Child.x", "class Child extends Base {}\n")
        server.reported[uri(root)] = emptyList()
        server.reported[uri(child)] = listOf(error("unknown-type"))
        open(root)
        refresh()
        // asked for before the refresh was answered: LSP4IJ itself asks for no workspace report
        assertThat(server.requests).contains("workspace/diagnostic")
        await("Child.x is a problem file") { problems.isProblemFile(child) }
        assertThat(closedCodes(child)).containsExactly("unknown-type")

        server.reported[uri(child)] = emptyList()
        refresh()
        await("Child.x is no problem file") { !problems.isProblemFile(child) }
        assertThat(closedCodes(child)).isEmpty()
    }

    @Test
    fun `an open file keeps the diagnostics pulled for it`() {
        val problems = problemFiles()
        val root = file("Root.x", "module Root {}\n")
        val child = file("Root/Child.x", "class Child extends Base {}\n")
        server.pulled[uri(root)] = listOf(error("pulled"))
        server.reported[uri(root)] = listOf(error("reported"))
        server.reported[uri(child)] = listOf(error("unknown-type"))
        open(root)
        refresh()
        // the report has Root.x before Root/Child.x, so a publication of Root.x would have come first
        await("Child.x is a problem file") { problems.isProblemFile(child) }
        await("Root.x has its pulled diagnostics") { "pulled" in openCodes(root) }
        assertThat(openCodes(root)).containsExactly("pulled")
    }

    @Test
    fun `a file that opens with its error fixed is no problem file`() {
        val problems = problemFiles()
        val root = file("Root.x", "module Root {}\n")
        val child = file("Root/Child.x", "class Child extends Base {}\n")
        server.reported[uri(child)] = listOf(error("unknown-type"))
        open(root)
        refresh()
        await("Child.x is a problem file") { problems.isProblemFile(child) }

        // fixed by an edit elsewhere, and opened before a report says so: LSP4IJ drops its published error and pulls none
        server.reported[uri(child)] = emptyList()
        open(child)
        refresh()
        await("Child.x is no problem file") { !problems.isProblemFile(child) }
        assertThat(openCodes(child)).isEmpty()
    }

    @Test
    fun `a closed file stays a problem file when LSP4IJ clears its mark last`() {
        val problems = problemFiles()
        val root = file("Root.x", "module Root {}\n")
        val child = file("Root/Child.x", "class Child extends Base {}\n")
        server.reported[uri(child)] = listOf(error("unknown-type"))
        open(root)
        refresh()
        await("Child.x is a problem file") { problems.isProblemFile(child) }

        // as a file closes, LSP4IJ clears its mark in a task of its own, which can run after the report of the errors
        // then published for it; run that clear now, after the report, as its announcement says it has
        val cleared = CompletableFuture<Unit>()
        editor.fixture.project.messageBus
            .connect(editor.fixture.testRootDisposable)
            .subscribe(
                ProblemListener.TOPIC,
                object : ProblemListener {
                    override fun problemsDisappeared(file: VirtualFile) {
                        if (file == child) cleared.complete(Unit)
                    }
                },
            )
        val features = connection().clientFeatures
        LSPDiagnosticUtils.clearProblem(setOf(checkNotNull(features.getFileUri(child))), features, editor.fixture.project)
        cleared.awaited()
        await("Child.x is a problem file again") { problems.isProblemFile(child) }
        assertThat(closedCodes(child)).containsExactly("unknown-type")
    }

    @Test
    fun `a file the report no longer has is no problem file`() {
        val problems = problemFiles()
        val root = file("Root.x", "module Root {}\n")
        val child = file("Root/Child.x", "class Child extends Base {}\n")
        server.reported[uri(child)] = listOf(error("unknown-type"))
        open(root)
        refresh()
        await("Child.x is a problem file") { problems.isProblemFile(child) }

        // as when its module is no longer analyzed
        server.reported.remove(uri(child))
        refresh()
        await("Child.x is no problem file") { !problems.isProblemFile(child) }
        assertThat(closedCodes(child)).isEmpty()
    }

    @Test
    fun `a closed file's error is in Project Errors without a build`() {
        val root = file("Root.x", "module Root {}\n")
        val child = file("Root/Child.x", "class Child extends Base {}\n")
        server.reported[uri(child)] = listOf(error("unknown-type"))
        open(root)
        refresh()
        await("Child.x's error is in Project Errors") { projectErrors(child).isNotEmpty() }
        assertThat(projectErrors(child).map { it.text }).containsExactly("unknown-type")
        assertThat(projectErrors(child).map { it.line to it.column }).containsExactly(0 to 0)
    }

    @Test
    fun `a closed file's error is in Project Errors once after a build`() {
        val root = file("Root.x", "module Root {}\n")
        val child = file("Root/Child.x", "class Child extends Base {}\n")
        server.reported[uri(child)] = listOf(error("unknown-type"))
        open(root)
        refresh()
        await("Child.x's error is in Project Errors") { projectErrors(child).isNotEmpty() }

        // the build reports the error the report has, which takes its place
        build(child, "unknown-type")
        assertThat(projectErrors(child)).hasSize(1)
        assertThat(projectErrors(child).single()).isInstanceOf(HighlightingDuplicateProblem::class.java)

        // and the next report's takes the build's
        refresh()
        await("Child.x's error is the report's") { projectErrors(child).none { it is HighlightingDuplicateProblem } }
        assertThat(projectErrors(child).map { it.text }).containsExactly("unknown-type")
    }

    @Test
    fun `a report that clears a closed file's error takes it out of Project Errors, the build's copy too`() {
        val problems = problemFiles()
        val root = file("Root.x", "module Root {}\n")
        val child = file("Root/Child.x", "class Child extends Base {}\n")
        server.reported[uri(child)] = listOf(error("unknown-type"))
        open(root)
        refresh()
        await("Child.x's error is in Project Errors") { projectErrors(child).isNotEmpty() }
        build(child, "unknown-type")
        assertThat(projectErrors(child).single()).isInstanceOf(HighlightingDuplicateProblem::class.java)

        server.reported[uri(child)] = emptyList()
        refresh()
        await("Child.x has no error in Project Errors") { projectErrors(child).isEmpty() }
        await("Child.x is no problem file") { !problems.isProblemFile(child) }
    }

    /** A build that reports the error [code] in [file], at the start of its first line, as [error] has it. */
    private fun build(
        file: VirtualFile,
        code: String,
    ) {
        val errors = ProjectErrors.getInstance(editor.fixture.project)
        errors.buildStarted()
        val range = BuildRange(BuildPosition(0, 0), BuildPosition(0, 5))
        errors.built(BuildResponse(diagnostics = listOf(DocumentDiagnostics(uri(file), listOf(BuildDiagnostic(range, 1, code, code))))))
    }

    /** What the Problems window's Project Errors lists in [file]. */
    private fun projectErrors(file: VirtualFile): List<FileProblem> =
        ProblemsCollector.getInstance(editor.fixture.project).getFileProblems(file).filterIsInstance<FileProblem>()

    /** The project's problem files: a test application's ignores every report unless it has a real one behind it. */
    private fun problemFiles(): WolfTheProblemSolver {
        val project = editor.fixture.project
        val problems = WolfTheProblemSolver.getInstance(project)
        if (problems is MockWolfTheProblemSolver) {
            problems.setDelegate(WolfTheProblemSolverImpl.createTestInstance(project))
            Disposer.register(editor.fixture.testRootDisposable) { problems.resetDelegate() }
        }
        return problems
    }

    /** A file on disk, which the light project's in-memory files are not, so that LSP4IJ finds it by its URI. */
    private fun file(
        path: String,
        text: String,
    ): VirtualFile {
        val file = directory.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file))
    }

    private fun uri(file: VirtualFile): String = LSPIJUtils.toUriAsString(file)

    private fun error(code: String) = Diagnostic(Range(Position(0, 0), Position(0, 5)), code, DiagnosticSeverity.Error, "stub", code)

    private fun open(file: VirtualFile) {
        runInEdtAndWait { editor.fixture.openFileInEditor(file) }
        // LSP4IJ connects a file as its editor is selected only if none of its features has made the file's support yet,
        // and one can make it first: under load, a second file once never connected. Connect it as that path does
        // (ConnectDocumentToLanguageServerSetupParticipant), which does nothing for a connected file
        ReadAction.runBlocking<RuntimeException> {
            val psi = checkNotNull(PsiManager.getInstance(editor.fixture.project).findFile(file))
            LanguageServiceAccessor.getInstance(editor.fixture.project).getLanguageServers(psi, null, null)
        }
        await("LSP4IJ opens ${file.name}") { opened(file) }
    }

    private fun opened(file: VirtualFile): Boolean = server.texts.keys.any { it.endsWith("/${file.name}") }

    /** The server's refresh, as it sends one after each analysis, answered. */
    private fun refresh() {
        server.languageClient.refreshDiagnostics().awaited()
    }

    private fun connection(): LanguageServerWrapper = LanguageServiceAccessor.getInstance(editor.fixture.project).startedServers.single()

    private fun closedCodes(file: VirtualFile): List<String?> =
        connection()
            .let { it.getClosedDocument(it.clientFeatures.getFileUri(file), false) }
            ?.diagnostics
            .orEmpty()
            .map { it.code?.left }

    private fun openCodes(file: VirtualFile): List<String?> =
        connection()
            .let { it.getOpenedDocument(it.clientFeatures.getFileUri(file)) }
            ?.diagnostics
            .orEmpty()
            .map { it.code?.left }

    private fun await(
        what: String,
        condition: () -> Boolean,
    ) {
        awaitUntil(what) { condition() || false.also { runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() } } }
    }
}
