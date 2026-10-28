package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Test

class ServerWorkspaceEditTest {
    @Test
    fun `platform and LSP4IJ edit representations preserve the same edits`() {
        val edit = TextEdit(Range(Position(0, 0), Position(0, 3)), "new")
        assertThat(ServerWorkspaceEdit.textEdits(listOf(edit))).containsExactly(edit)
        assertThat(ServerWorkspaceEdit.textEdits(listOf(Either.forLeft<TextEdit, Any>(edit))))
            .containsExactly(edit)
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
}
