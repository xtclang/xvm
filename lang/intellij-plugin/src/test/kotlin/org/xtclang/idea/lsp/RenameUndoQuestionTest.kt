package org.xtclang.idea.lsp

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndWait
import com.redhat.devtools.lsp4ij.LSPIJUtils
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
    val editor = LightEditor(StubLanguageServer(xtc = true, client = ::XtcLanguageClient))

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
            ) { "declare" in editor.document(root) && "declare" in editor.document(caller) }
        assertThat(questions).hasSize(1)
        assertThat(questions.single()).contains("Undo Rename")
        assertThat(editor.document(root)).isEqualTo(ROOT)
        assertThat(editor.document(caller)).isEqualTo(CALLER)
    }

    @Test
    fun `Undo of a rename that changes one file takes it back at once, without asking`() {
        val root = file("Root.x", ROOT)
        val questions =
            renameThenUndo(root, mapOf(root to listOf(TextEdit(Range(Position(2, 8), Position(2, 16)), "declare")))) {
                "declare" in editor.document(root)
            }
        assertThat(questions).isEmpty()
        assertThat(editor.document(root)).isEqualTo(ROOT)
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
        editor.openConnected(root)
        val project = editor.fixture.project
        val questions = CopyOnWriteArrayList<String>()
        TestDialogManager.setTestDialog { message ->
            questions += message
            Messages.OK
        }
        try {
            XtcFileOperations.submit(
                project,
                "Rename",
                XtcRenameEdit.request(editor.connection(), root, ROOT.indexOf("announce"), "declare"),
            )
            editor.await("the rename is applied", renamed)
            runInEdtAndWait { pressUndo(project) }
        } finally {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        }
        return questions
    }

    private fun file(
        path: String,
        text: String,
    ): VirtualFile = editor.file(directory, path, text)

    private companion object {
        const val ROOT = "module Root {\n    void run() {\n        announce();\n    }\n}\n"
        const val CALLER = "class Caller {\n    void call() {\n        Root.announce();\n    }\n}\n"
    }
}
