package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.eclipse.lsp4j.CreateFile
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Test

/**
 * Compares supported platform and LSP4IJ edit representations. Malformed or unsupported alternatives must be
 * refused as a whole rather than becoming a partial refactoring.
 */
class ServerWorkspaceEditTest {
    @Test
    fun `platform and LSP4IJ edit representations preserve the same edits`() {
        val edit = TextEdit(Range(Position(0, 0), Position(0, 3)), "new")
        assertThat(ServerWorkspaceEdit.textEdits(listOf(edit))).containsExactly(edit)
        assertThat(ServerWorkspaceEdit.textEdits(listOf(Either.forLeft<TextEdit, Any>(edit))))
            .containsExactly(edit)
    }

    @Test
    fun `one document's text edits undo in its editor, more documents or a file operation in the whole project`() {
        val edit = TextEdit(Range(Position(0, 0), Position(0, 3)), "new")

        fun document(uri: String) =
            Either.forLeft<TextDocumentEdit, ResourceOperation>(
                TextDocumentEdit().apply { textDocument = VersionedTextDocumentIdentifier(uri, 1) },
            )
        assertThat(ServerWorkspaceEdit.globalUndo(WorkspaceEdit(mapOf(FIRST to listOf(edit, edit))))).isFalse()
        assertThat(ServerWorkspaceEdit.globalUndo(WorkspaceEdit(listOf(document(FIRST))))).isFalse()
        assertThat(ServerWorkspaceEdit.globalUndo(WorkspaceEdit(mapOf(FIRST to listOf(edit), SECOND to listOf(edit))))).isTrue()
        assertThat(ServerWorkspaceEdit.globalUndo(WorkspaceEdit(listOf(document(FIRST), document(SECOND))))).isTrue()
        assertThat(
            ServerWorkspaceEdit.globalUndo(WorkspaceEdit(listOf(document(FIRST), Either.forRight(CreateFile(SECOND))))),
        ).isTrue()
    }

    @Test
    fun `unsupported alternatives and malformed entries cannot become partial edits`() {
        val edit = TextEdit(Range(Position(0, 0), Position(0, 3)), "new")
        listOf(null, "malformed", Either.forRight<TextEdit, Any>(Any())).forEach { unsupported ->
            assertThatIllegalArgumentException().isThrownBy {
                ServerWorkspaceEdit.textEdits(listOf(edit, unsupported))
            }
        }
    }

    private companion object {
        const val FIRST = "file:///project/src/main/x/First.x"
        const val SECOND = "file:///project/src/main/x/Second.x"
    }
}
