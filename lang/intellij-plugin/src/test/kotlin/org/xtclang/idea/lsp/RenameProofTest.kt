package org.xtclang.idea.lsp

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndGet
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

/**
 * A rename's proof stays current while the sources it read are as they were: a document opened since with the text its
 * file had as the rename was asked, or one that LSP4IJ keeps connected after its file was deleted before the rename,
 * changes none of them, as the IntelliJ playbook's full catalog had it from X337 on, whose Gradle debug left such a
 * document. A document opened since with other text, or with its file's text that changed since, by a save or on the
 * disk, a file deleted since, and a file that takes a deleted one's place refuse the rename.
 */
class RenameProofTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor(StubLanguageServer(client = ::XtcLanguageClient))

    @TempDir
    lateinit var directory: Path

    @Test
    fun `a rename applies though a file opened since has its file's text`() {
        val other = file("Other.x", OTHER)
        assertThat(rename { editor.openConnected(other) }).isTrue()
    }

    // TODO LSP4IJ: UP93 — LSP4IJ keeps a document connected that no editor shows once its file is deleted, with no
    //  didClose, which XtcRenameEdit tolerates while no file takes its place; drop this test with that tolerance once a
    //  release we ship closes such a document
    @Test
    fun `a rename applies though a document stays connected whose file was deleted before the rename`() {
        val other = file("Other.x", OTHER)
        editor.awaitConnected(other)
        delete(other)
        assertThat(editor.connection().openedDocuments.map { it.file }).contains(other)
        assertThat(rename {}).isTrue()
    }

    @Test
    fun `a rename is refused once a file opened since has text that its file has not`() {
        val other = file("Other.x", OTHER)
        assertThat(
            rename {
                editor.openConnected(other)
                edit(other)
            },
        ).isFalse()
    }

    @Test
    fun `a rename is refused once a file opened since is edited and saved`() {
        val other = file("Other.x", OTHER)
        assertThat(
            rename {
                editor.openConnected(other)
                edit(other)
                runInEdtAndWait { FileDocumentManager.getInstance().saveAllDocuments() }
            },
        ).isFalse()
    }

    @Test
    fun `a rename is refused once a file rewritten on the disk since is opened`() {
        val other = file("Other.x", OTHER)
        assertThat(
            rename {
                file("Other.x", "class Other {\n    Int count;\n}\n")
                editor.openConnected(other)
            },
        ).isFalse()
    }

    @Test
    fun `a rename is refused once a connected file is deleted`() {
        val other = file("Other.x", OTHER)
        editor.awaitConnected(other)
        assertThat(rename { delete(other) }).isFalse()
    }

    @Test
    fun `a rename is refused once a file takes the place of a connected one deleted before the rename`() {
        val other = file("Other.x", OTHER)
        editor.awaitConnected(other)
        delete(other)
        assertThat(rename { file("Other.x", OTHER) }).isFalse()
    }

    /**
     * Asks the server for the rename of `announce` in a connected Root.x, runs [meanwhile] once it has answered, and
     * answers whether the rename applies, which it does in Root.x then.
     */
    private fun rename(meanwhile: () -> Unit): Boolean {
        val root = file("Root.x", ROOT)
        editor.server.renameEdit = {
            WorkspaceEdit(mapOf(LSPIJUtils.toUriAsString(root) to listOf(TextEdit(Range(Position(2, 8), Position(2, 16)), "declare"))))
        }
        editor.openConnected(root)
        val request = XtcRenameEdit.request(editor.connection(), root, ROOT.indexOf("announce"), "declare")
        editor.await("the server proves the rename") { request.isDone }
        meanwhile()
        val applied = runInEdtAndGet { checkNotNull(request.get()).apply() }
        assertThat(editor.document(root)).isEqualTo(if (applied) ROOT.replace("announce", "declare") else ROOT)
        return applied
    }

    /** Types into [file]'s document, as an edit in its editor does. */
    private fun edit(file: VirtualFile) =
        runInEdtAndWait {
            WriteCommandAction.runWriteCommandAction(editor.fixture.project) {
                checkNotNull(FileDocumentManager.getInstance().getDocument(file)).insertString(0, "// edited\n")
            }
        }

    private fun delete(file: VirtualFile) = runInEdtAndWait { WriteAction.run<Exception> { file.delete(this) } }

    /** The file at [path] in the test's directory, written with [text], which IntelliJ then reads, as after a change on the disk. */
    private fun file(
        path: String,
        text: String,
    ): VirtualFile = editor.file(directory, path, text).also { runInEdtAndWait { it.refresh(false, false) } }

    private companion object {
        const val ROOT = "module Root {\n    void run() {\n        announce();\n    }\n}\n"
        const val OTHER = "class Other {\n}\n"
    }
}
