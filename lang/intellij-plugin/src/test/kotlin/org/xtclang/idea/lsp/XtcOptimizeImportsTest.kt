package org.xtclang.idea.lsp

import com.intellij.lang.LanguageImportStatements
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor

/**
 * Optimize Imports finds XtcImportOptimizer for an Ecstasy file, and its edit applies as the one Undo of the command
 * that Optimize Imports runs it in, only to the document the server answered for. No server runs here.
 */
class XtcOptimizeImportsTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    private val source = "module App {\n    import b.B;\n    import c.Unused;\n    import a.A;\n}\n"

    // what the server's Organize imports answers for the source: one replacement of the import group
    private val organize = listOf(TextEdit(Range(Position(1, 4), Position(3, 15)), "import a.A;\n    import b.B;"))

    @Test
    fun `Optimize Imports finds the Ecstasy import optimizer, and no other file's`() {
        editor.open("App.x", source) { file ->
            assertThat(LanguageImportStatements.INSTANCE.forFile(file)).hasExactlyElementsOfTypes(XtcImportOptimizer::class.java)
        }
        editor.open("notes.txt", source) { file ->
            assertThat(LanguageImportStatements.INSTANCE.forFile(file)).noneMatch { it is XtcImportOptimizer }
        }
    }

    @Test
    fun `the edit applies in the command it runs in, which one Undo reverts`() {
        editor.open("App.x", source) { file ->
            val document = editor.document
            val organized = XtcImportOptimizer.Organize(document, document.modificationStamp, organize)
            WriteCommandAction.writeCommandAction(project).withName("Optimize Imports").run<RuntimeException> { organized.run() }
            assertThat(document.text).isEqualTo("module App {\n    import a.A;\n    import b.B;\n}\n")
            assertThat(organized.userNotificationInfo).isEqualTo(XtcImportOptimizer.OPTIMIZED)
            val textEditor = FileEditorManagerEx.getInstanceEx(project).getSelectedEditor(file.virtualFile) as TextEditor
            UndoManager.getInstance(project).undo(textEditor)
            assertThat(document.text).isEqualTo(source)
        }
    }

    @Test
    fun `an edit for a document that has changed since is not applied`() {
        editor.open("App.x", source) {
            val document = editor.document
            val organized = XtcImportOptimizer.Organize(document, document.modificationStamp, organize)
            WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "// edited\n") }
            val edited = document.text
            WriteCommandAction.runWriteCommandAction(project) { organized.run() }
            assertThat(document.text).isEqualTo(edited)
            assertThat(organized.userNotificationInfo).contains("changed")
        }
    }

    @Test
    fun `an edit that does not fit the document is not applied`() {
        editor.open("App.x", source) {
            val document = editor.document
            val beyond = listOf(TextEdit(Range(Position(1, 4), Position(9, 0)), ""))
            val organized = XtcImportOptimizer.Organize(document, document.modificationStamp, beyond)
            WriteCommandAction.runWriteCommandAction(project) { organized.run() }
            assertThat(document.text).isEqualTo(source)
            assertThat(organized.userNotificationInfo).contains("does not fit")
        }
    }
}
