package org.xtclang.idea.lsp

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
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
import org.eclipse.lsp4j.CodeAction
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
 * A code action that the server resolves, such as Create private method, edits one file: Undo in that file's editor takes
 * it back as a typed change is, at once, without asking first, as IntelliJ asks only for a change of the project.
 */
class CodeActionUndoTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor(StubLanguageServer(client = ::XtcLanguageClient))

    @TempDir
    lateinit var directory: Path

    private val server: StubLanguageServer
        get() = editor.server

    @Test
    fun `Undo in the file's editor takes a resolved code action's edit of that file back, without asking`() {
        val text = "module Root {\n    void run() {\n        announce();\n    }\n}\n"
        val root = file("Root.x", text)
        val method = "\n\n    private void announce() {}"
        server.resolve = { action ->
            action.apply {
                edit =
                    WorkspaceEdit(mapOf(LSPIJUtils.toUriAsString(root) to listOf(TextEdit(Range(Position(3, 5), Position(3, 5)), method))))
            }
        }
        open(root)
        val project = editor.fixture.project
        val dialogs = CopyOnWriteArrayList<String>()
        // Undo's question, if it asks one, answered No, so that the change stays and the test sees it
        TestDialogManager.setTestDialog { message ->
            dialogs += message
            Messages.NO
        }
        try {
            XtcFileOperations.submit(
                project,
                "Create private method 'announce'",
                XtcRenameEdit.requestAction(connection(), CodeAction("Create private method 'announce'")),
            )
            await("the method is created") { method in document(root) }
            runInEdtAndWait {
                val fileEditor = checkNotNull(FileEditorManager.getInstance(project).getSelectedEditor(root)) { "No editor of Root.x" }
                assertThat(UndoManager.getInstance(project).isUndoAvailable(fileEditor)).isTrue()
                UndoManager.getInstance(project).undo(fileEditor)
            }
            assertThat(dialogs).isEmpty()
            assertThat(document(root)).isEqualTo(text)
        } finally {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        }
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
}
