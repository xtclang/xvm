package org.xtclang.idea.lsp

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.xtclang.idea.LightEditor
import org.xtclang.idea.StubLanguageServer
import org.xtclang.idea.awaitUntil
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Undo of a rename that changes more than one file asks first, once, as Undo of IntelliJ's own refactorings does, and
 * takes every file back once the question is answered OK. Undo of a rename that changes one file takes it back at once,
 * without the question, as an in-place rename's does. Each presses the keymap's Undo key in the renamed file's editor.
 */
class RenameUndoQuestionTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor(StubLanguageServer(client = ::XtcLanguageClient))

    @TempDir
    lateinit var directory: Path

    private val server: StubLanguageServer
        get() = editor.server

    @Test
    fun `Undo of a rename that changes two files asks once, and takes both back once answered`() {
        val root = file("Root.x", ROOT)
        val caller = file("Caller.x", CALLER)
        val questions =
            renameThenUndo(
                root,
                mapOf(
                    root to listOf(TextEdit(Range(Position(2, 8), Position(2, 16)), "declare")),
                    caller to listOf(TextEdit(Range(Position(2, 13), Position(2, 21)), "declare")),
                ),
            ) { "declare" in document(root) && "declare" in document(caller) }
        assertThat(questions).hasSize(1)
        assertThat(questions.single()).contains("Undo Rename")
        assertThat(document(root)).isEqualTo(ROOT)
        assertThat(document(caller)).isEqualTo(CALLER)
    }

    @Test
    fun `Undo of a rename that changes one file takes it back at once, without asking`() {
        val root = file("Root.x", ROOT)
        val questions =
            renameThenUndo(root, mapOf(root to listOf(TextEdit(Range(Position(2, 8), Position(2, 16)), "declare")))) {
                "declare" in document(root)
            }
        assertThat(questions).isEmpty()
        assertThat(document(root)).isEqualTo(ROOT)
    }

    /**
     * Renames announce in [root] with the server's [edits] and, once [renamed] holds, presses the Undo key in [root]'s
     * editor, answering each question that Undo asks with OK; answers the questions.
     */
    private fun renameThenUndo(
        root: VirtualFile,
        edits: Map<VirtualFile, List<TextEdit>>,
        renamed: () -> Boolean,
    ): List<String> {
        server.renameEdit = { WorkspaceEdit(edits.mapKeys { (file, _) -> LSPIJUtils.toUriAsString(file) }) }
        open(root)
        val project = editor.fixture.project
        val questions = CopyOnWriteArrayList<String>()
        TestDialogManager.setTestDialog { message ->
            questions += message
            Messages.OK
        }
        try {
            XtcFileOperations.submit(project, "Rename", XtcRenameEdit.request(connection(), root, ROOT.indexOf("announce"), "declare"))
            await("the rename is applied", renamed)
            runInEdtAndWait { pressUndo(project) }
        } finally {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        }
        return questions
    }

    private fun file(
        path: String,
        text: String,
    ): VirtualFile {
        val file = directory.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file))
    }

    private fun document(file: VirtualFile): String =
        runInEdtAndGet { checkNotNull(FileDocumentManager.getInstance().getDocument(file)).text }

    private fun open(file: VirtualFile) {
        runInEdtAndWait { editor.fixture.openFileInEditor(file) }
        ReadAction.runBlocking<RuntimeException> {
            val psi = checkNotNull(PsiManager.getInstance(editor.fixture.project).findFile(file))
            LanguageServiceAccessor.getInstance(editor.fixture.project).getLanguageServers(psi, null, null)
        }
        await("LSP4IJ opens ${file.name}") { server.texts.keys.any { it.endsWith("/${file.name}") } }
    }

    private fun connection(): LanguageServerWrapper = LanguageServiceAccessor.getInstance(editor.fixture.project).startedServers.single()

    private fun await(
        what: String,
        condition: () -> Boolean,
    ) {
        awaitUntil(what) { condition() || false.also { runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() } } }
    }

    private companion object {
        const val ROOT = "module Root {\n    void run() {\n        announce();\n    }\n}\n"
        const val CALLER = "class Caller {\n    void call() {\n        Root.announce();\n    }\n}\n"
    }
}
