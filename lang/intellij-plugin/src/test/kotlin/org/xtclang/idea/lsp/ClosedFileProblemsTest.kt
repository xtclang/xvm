package org.xtclang.idea.lsp

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.HighlightingDuplicateProblem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.MockWolfTheProblemSolver
import com.intellij.codeInsight.daemon.impl.WolfTheProblemSolverImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.fileEditor.FileDocumentManager
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
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticsApplier
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
import java.util.concurrent.CountDownLatch

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
        // then published for it; run that clear now, after the report. LSP4IJ clears a file by reporting it from the
        // errors it holds (#1868), which announces a change of a problem file, and its disappearance had it been cleared
        val reported = CompletableFuture<String>()
        editor.fixture.project.messageBus
            .connect(editor.fixture.testRootDisposable)
            .subscribe(
                ProblemListener.TOPIC,
                object : ProblemListener {
                    override fun problemsChanged(file: VirtualFile) {
                        if (file == child) reported.complete("changed")
                    }

                    override fun problemsDisappeared(file: VirtualFile) {
                        if (file == child) reported.complete("disappeared")
                    }
                },
            )
        val features = connection().clientFeatures
        LSPDiagnosticUtils.clearProblem(setOf(checkNotNull(features.getFileUri(child))), features, editor.fixture.project)
        assertThat(reported.awaited()).isEqualTo("changed")
        assertThat(problems.isProblemFile(child)).isTrue()
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

    @Test
    fun `an error that reaches a file's editor before LSP4IJ connects it goes with the connected document's report`() {
        val root = file("Root.x", "module Root {}\n")
        val child = file("Root/Child.x", "class Child extends Base {}\n")
        open(root)
        // fixed by the time LSP4IJ has connected the file, whose own pulls report none
        server.pulled[uri(child)] = emptyList()
        openMarkedBeforeConnection(child, "unknown-type")
        await("Child.x's editor no longer marks the error") { "unknown-type" !in marks(child) }
    }

    @Test
    fun `an error that reaches a file's editor before LSP4IJ connects it goes with an unchanged report of the connected document`() {
        val root = file("Root.x", "module Root {}\n")
        val child = file("Root/Child.x", "class Child extends Base {}\n")
        open(root)
        // the error is fixed by the time LSP4IJ has connected the file; the document's report has a result ID, and a pull
        //  that names it is answered as unchanged
        server.pulled[uri(child)] = emptyList()
        server.pullResults[uri(child)] = "fixed"
        val asked = CompletableFuture<Unit>()
        val answer = CompletableFuture<Unit>()
        server.pulls = {
            if (it == uri(child) && asked.complete(Unit)) answer else CompletableFuture.completedFuture(null)
        }
        openMarkedBeforeConnection(child, "unknown-type")
        await("LSP4IJ pulls Child.x") { asked.isDone }
        // the document changes for the plugin's record of reports while its first report comes, and not for LSP4IJ, which
        //  applies that report: as with an edit that LSP4IJ sends once the document is committed (X71,
        //  run-4165373714699057152), which here changes no text
        runInEdtAndWait {
            WriteAction.run<RuntimeException> {
                val document = checkNotNull(FileDocumentManager.getInstance().getDocument(child)) as DocumentEx
                document.setModificationStamp(document.modificationStamp + 1)
            }
        }
        answer.complete(Unit)
        await("LSP4IJ applies Child.x's first report") { pullResultId(child) == "fixed" }
        // its next pull names that report, and the server answers it as unchanged
        refresh()
        await("Child.x's editor no longer marks the error") { "unknown-type" !in marks(child) }
        assertThat(server.unchangedPulls).contains(uri(child))
    }

    /**
     * The result ID that LSP4IJ's connected document of [file] names in its next pull, from the report it applied last.
     *
     * TODO LSP4IJ: UP86 — read at LSP4IJ's own seam, `DocumentContentSynchronizer.getPullResultId`, for a test only, as
     *  nothing else tells that LSP4IJ has applied a report that changed nothing
     */
    private fun pullResultId(file: VirtualFile): String? {
        val synchronizer = connection().getOpenedDocument(connection().clientFeatures.getFileUri(file))?.synchronizer ?: return null
        return synchronizer.javaClass
            .getDeclaredMethod("getPullResultId")
            .apply { isAccessible = true }
            .invoke(synchronizer) as String?
    }

    @Test
    fun `an error that reaches a file's editor before LSP4IJ connects it stays marked until the connected document's report`() {
        staysMarkedUntilOwnReport { }
    }

    @Test
    fun `an error that reaches a file's editor before LSP4IJ connects it stays marked through an update LSP4IJ rejects`() {
        // as a report for another version of the document, which LSP4IJ rejects, and still tells the plugin of
        staysMarkedUntilOwnReport { child ->
            val document = checkNotNull(connection().getOpenedDocument(connection().clientFeatures.getFileUri(child)))
            connection().updateDiagnostics(child, "stub", emptyList(), checkNotNull(document.synchronizer).version + 1)
        }
    }

    @Test
    fun `an error that reaches a file's editor before LSP4IJ connects it stays marked through another document's report`() {
        // as a report of the document in another document's pull, or in the workspace's, which is not its own pull's
        staysMarkedUntilOwnReport { child -> connection().updateDiagnostics(child, "stub", emptyList(), null) }
    }

    /**
     * Gives a file the error unknown-type before LSP4IJ connects its document, as [openMarkedBeforeConnection] does, with
     * the error still there: the connected document's own pull reports it, and comes once [beforeReport] has updated
     * the file's diagnostics, as another report can. Until that report comes, the file's mark must stay; and with it.
     */
    private fun staysMarkedUntilOwnReport(beforeReport: (VirtualFile) -> Unit) {
        val root = file("Root.x", "module Root {}\n")
        val child = file("Root/Child.x", "class Child extends Base {}\n")
        open(root)
        server.pulled[uri(child)] = listOf(error("unknown-type"))
        val asked = CompletableFuture<Unit>()
        val answer = CompletableFuture<Unit>()
        server.pulls = { if (it == uri(child)) answer.also { asked.complete(Unit) } else CompletableFuture.completedFuture(null) }
        openMarkedBeforeConnection(child, "unknown-type")
        await("LSP4IJ pulls Child.x") { asked.isDone }
        val fileUri = checkNotNull(connection().clientFeatures.getFileUri(child))
        val applier = LSPDiagnosticsApplier.getInstance(editor.fixture.project)
        await("LSP4IJ has connected Child.x, whose report has not come") {
            connection().getOpenedDocument(fileUri)?.synchronizer != null && !applier.isRefreshPending(child)
        }
        beforeReport(child)
        await("LSP4IJ has nothing more to mark in Child.x's editor") { !applier.isRefreshPending(child) }
        runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }
        assertThat(marks(child)).contains("unknown-type")

        answer.complete(Unit)
        await("LSP4IJ has Child.x's report, and nothing more to mark in its editor") {
            "unknown-type" in openCodes(child) && !applier.isRefreshPending(child)
        }
        runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }
        assertThat(marks(child)).contains("unknown-type")
    }

    /**
     * Opens the editor of [file] and gives the file the error [code] before LSP4IJ connects its document; returns once the
     * editor marks it and LSP4IJ has opened the document at the server.
     */
    private fun openMarkedBeforeConnection(
        file: VirtualFile,
        code: String,
    ) {
        // LSP4IJ marks an editor's diagnostics once the daemon has its passes, which a light test's has after a first run
        runInEdtAndWait { editor.fixture.doHighlighting() }
        // TODO LSP4IJ: UP86 — the file's editor opens, and before LSP4IJ connects its document, a workspace report or a
        //  related document's report gives the file an error: LSP4IJ keeps it in a document of the file without a
        //  connection, and the editor marks it. The connection waits here for that, at LSP4IJ's own seam, to make
        //  certain of the order that only sometimes comes otherwise
        val connecting = CountDownLatch(1)
        val marked = CountDownLatch(1)
        setBeforeRegistration {
            connecting.countDown()
            marked.await()
        }
        try {
            runInEdtAndWait { editor.fixture.openFileInEditor(file) }
            // on a thread of its own, which the connection holds
            CompletableFuture.runAsync { connect(file) }
            await("LSP4IJ connects ${file.name}") { connecting.count == 0L }
            connection().updateDiagnostics(file, "stub", listOf(error(code)), null)
            await("${file.name}'s editor marks the error") { code in marks(file) }
        } finally {
            setBeforeRegistration(null)
            marked.countDown()
        }
        await("LSP4IJ opens ${file.name}") { opened(file) }
    }

    /** Runs [hook] as LSP4IJ connects a document, before it registers the document's connection; null runs nothing. */
    private fun setBeforeRegistration(hook: Runnable?) {
        LanguageServerWrapper::class.java
            .getDeclaredMethod("setBeforeRegistration", Runnable::class.java)
            .apply { isAccessible = true }
            .invoke(connection(), hook)
    }

    /** The descriptions of the errors and warnings that the editor of [file] marks. */
    private fun marks(file: VirtualFile): List<String> =
        ReadAction.computeBlocking<List<String>, RuntimeException> {
            val document = checkNotNull(FileDocumentManager.getInstance().getDocument(file))
            DaemonCodeAnalyzerImpl
                .getHighlights(document, HighlightSeverity.WARNING, editor.fixture.project)
                .mapNotNull { it.description }
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
        connect(file)
        await("LSP4IJ opens ${file.name}") { opened(file) }
    }

    /** Asks LSP4IJ to connect [file], whose editor is open, to the server. */
    private fun connect(file: VirtualFile) {
        // LSP4IJ connects a file as its editor is selected only if none of its features has made the file's support yet,
        // and one can make it first: under load, a second file once never connected. Connect it as that path does
        // (ConnectDocumentToLanguageServerSetupParticipant), which does nothing for a connected file
        ReadAction.runBlocking<RuntimeException> {
            val psi = checkNotNull(PsiManager.getInstance(editor.fixture.project).findFile(file))
            LanguageServiceAccessor.getInstance(editor.fixture.project).getLanguageServers(psi, null, null)
        }
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
