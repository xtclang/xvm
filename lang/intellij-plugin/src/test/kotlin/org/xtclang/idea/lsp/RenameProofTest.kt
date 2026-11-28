package org.xtclang.idea.lsp

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
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
import java.nio.file.Files
import java.nio.file.Path

/**
 * A rename's proof stays current while the sources it read are as they were: a document opened since with the text its
 * file had as the rename was asked, or one that LSP4IJ keeps connected after its file was deleted before the rename,
 * changes none of them, as the IntelliJ playbook's full catalog had it from X337 on, whose Gradle debug left such a
 * document. A document opened since with other text, or with its file's text that changed since, by a save or on the
 * disk, a document with unsaved changes that the server never read, however it has been saved or opened since, a file
 * deleted since, and a file that takes a deleted one's place refuse the rename. So does an edit of a document whose
 * text is not the one the server proved the edit against, by its hash in the proposal, where IntelliJ's modification
 * stamps can't tell: a file recreated outside the IDE with other text, and a file changed on the disk that the VFS has
 * not refreshed, whose document has text that the server did not read, or whose disk has, which the refusal then
 * refreshes. A file with a byte order mark has the text that the server reads, without the mark. A server that proves
 * no text may edit only the open documents whose buffers it read.
 */
class RenameProofTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor(StubLanguageServer(xtc = true, client = ::XtcLanguageClient))

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
                save()
            },
        ).isFalse()
    }

    @Test
    fun `a rename is refused once a file whose unsaved changes the server never read is saved and opened`() {
        val other = file("Other.x", OTHER)
        edit(other)
        assertThat(editor.opened(other)).isFalse()
        assertThat(
            rename {
                save()
                editor.openConnected(other)
            },
        ).isFalse()
    }

    @Test
    fun `a rename is refused that edits a closed file whose unsaved changes the server never read, saved since`() {
        val other = file("Other.x", OTHER)
        edit(other)
        assertThat(rename(mapOf(other to OTHER_EDIT)) { save() }).isFalse()
        assertThat(editor.document(other)).isEqualTo("// edited\n$OTHER")
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
    fun `a rename is refused once a file it edits is recreated outside the IDE with other text and opened`() {
        val other = file("Other.x", OTHER)
        val recreated = "// recreated\n$OTHER"
        lateinit var replacement: VirtualFile
        assertThat(
            rename(mapOf(other to OTHER_EDIT)) {
                replacement = recreate(other, recreated)
                editor.openConnected(replacement)
            },
        ).isFalse()
        assertThat(editor.document(replacement)).isEqualTo(recreated)
    }

    @Test
    fun `a rename is refused once a file it edits, changed on the disk before the VFS refreshed it, is opened`() {
        // in a folder of its own, which no refresh of Root.x's folder looks into
        val other = file("other/Other.x", OTHER)
        val changed = "// changed\n$OTHER"
        // IntelliJ's document keeps the text it loaded, as the VFS's content does until it refreshes the file
        val loaded = loaded(other)
        outside(other, changed)
        // the server reads the changed file, and its edit is of that text
        assertThat(
            rename(mapOf(other to listOf(TextEdit(Range(Position(1, 6), Position(1, 11)), "Another")))) { editor.openConnected(other) },
        ).isFalse()
        assertThat(Files.readString(other.toNioPath())).isEqualTo(changed)
        // the refusal refreshes the file, as a rename invoked again needs, and its document, which the edit left as it was,
        // then has the file's text
        editor.await("the VFS refreshes Other.x, and its document has its text") { loaded.text == changed }
    }

    @Test
    fun `a rename is refused once a closed file it edits changes on the disk, before the VFS refreshes it`() {
        val other = file("other/Other.x", OTHER)
        val changed = "// changed\n$OTHER"
        // IntelliJ's document keeps the text it loaded, the one that the server reads, as the VFS's content does
        val loaded = loaded(other)
        assertThat(rename(mapOf(other to OTHER_EDIT)) { outside(other, changed) }).isFalse()
        assertThat(Files.readString(other.toNioPath())).isEqualTo(changed)
        editor.await("the VFS refreshes Other.x, and its document has its text") { loaded.text == changed }
    }

    @Test
    fun `a rename edits a closed file with a byte order mark as its text without the mark`() {
        val path = directory.resolve("other/Other.x")
        Files.createDirectories(path.parent)
        Files.write(path, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + OTHER.toByteArray())
        val other = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
        // the server reads the file without its mark, as IntelliJ's document has it, and the edit's columns do not count it
        assertThat(rename(mapOf(other to OTHER_EDIT)) {}).isTrue()
        assertThat(editor.document(other)).isEqualTo(OTHER.replace("Other", "Another"))
    }

    @Test
    fun `a rename whose server proves no text is refused once it edits a closed file`() {
        val other = file("Other.x", OTHER)
        editor.server.proves = false
        assertThat(rename(mapOf(other to OTHER_EDIT)) {}).isFalse()
        assertThat(editor.document(other)).isEqualTo(OTHER)
    }

    @Test
    fun `a rename whose server proves no text applies to the open document whose buffer it read`() {
        editor.server.proves = false
        assertThat(rename {}).isTrue()
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
     * Asks the server for the rename of `announce` in a connected Root.x, whose edit also changes [others], runs
     * [meanwhile] once it has answered, and answers whether the rename applies, which it does in Root.x then.
     */
    private fun rename(
        others: Map<VirtualFile, List<TextEdit>> = emptyMap(),
        meanwhile: () -> Unit,
    ): Boolean {
        val root = file("Root.x", ROOT)
        val edits = mapOf(root to listOf(TextEdit(Range(Position(2, 8), Position(2, 16)), "declare"))) + others
        editor.server.renameEdit = { WorkspaceEdit(edits.mapKeys { (file, _) -> LSPIJUtils.toUriAsString(file) }) }
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

    /** Saves every document, as IntelliJ's autosave does while it is idle or as its window loses the focus. */
    private fun save() = runInEdtAndWait { FileDocumentManager.getInstance().saveAllDocuments() }

    private fun delete(file: VirtualFile) = runInEdtAndWait { WriteAction.run<Exception> { file.delete(this) } }

    /** [file]'s document, which IntelliJ keeps, with the text it loaded, while the test holds it. */
    private fun loaded(file: VirtualFile): Document = checkNotNull(runInEdtAndGet { FileDocumentManager.getInstance().getDocument(file) })

    /** Writes [text] to [file] on the disk, outside the IDE, which the VFS does not know of until it refreshes the file. */
    private fun outside(
        file: VirtualFile,
        text: String,
    ) {
        Files.writeString(file.toNioPath(), text)
    }

    /**
     * Deletes [file] outside the IDE and writes [text] at its path, each change refreshed into the VFS as it happens, so
     * that the file at the path is a new one, whose modification stamp is that of no change since the rename was asked.
     */
    private fun recreate(
        file: VirtualFile,
        text: String,
    ): VirtualFile {
        val path = file.toNioPath()
        Files.delete(path)
        runInEdtAndWait { file.parent.refresh(false, false) }
        assertThat(file.isValid).isFalse()
        Files.writeString(path, text)
        return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
    }

    /** The file at [path] in the test's directory, written with [text], which IntelliJ then reads, as after a change on the disk. */
    private fun file(
        path: String,
        text: String,
    ): VirtualFile = editor.file(directory, path, text).also { runInEdtAndWait { it.refresh(false, false) } }

    private companion object {
        const val ROOT = "module Root {\n    void run() {\n        announce();\n    }\n}\n"
        const val OTHER = "class Other {\n}\n"

        /** The server's edit of [OTHER], which renames its class. */
        val OTHER_EDIT = listOf(TextEdit(Range(Position(0, 6), Position(0, 11)), "Another"))
    }
}
