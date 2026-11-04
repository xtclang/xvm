package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.Command
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

/** What Optimize Imports asks the server for, which of its actions it takes, and which edits it accepts. */
class XtcImportOptimizerTest {
    private val uri = "file:///work/App.x"
    private val removal = TextEdit(Range(Position(1, 0), Position(2, 0)), "")

    private fun action(
        title: String,
        kind: String,
    ): Either<Command, CodeAction> = Either.forRight(CodeAction(title).apply { this.kind = kind })

    @Test
    fun `the request asks for organize imports only, from the document's start`() {
        val params = XtcImportOptimizer.params(uri)
        assertThat(params.textDocument.uri).isEqualTo(uri)
        assertThat(params.context.only).containsExactly(CodeActionKind.SourceOrganizeImports)
        assertThat(params.context.diagnostics).isEmpty()
        assertThat(params.range).isEqualTo(Range(Position(0, 0), Position(0, 0)))
    }

    @Test
    fun `the one organize imports action is taken, not other source actions or commands`() {
        val organize = action("Organize imports", CodeActionKind.SourceOrganizeImports)
        val results =
            listOf(
                action("Remove unused import 'HashMap'", CodeActionKind.Source),
                Either.forLeft(Command("Organize", "organize")),
                organize,
            )
        assertThat(XtcImportOptimizer.action(results)).isSameAs(organize.right)
        assertThat(XtcImportOptimizer.action(listOf(action("Sort", "${CodeActionKind.SourceOrganizeImports}.sort"))))
            .isNotNull()
        assertThat(XtcImportOptimizer.action(results.take(2))).isNull()
        assertThat(XtcImportOptimizer.action(listOf(organize, organize))).isNull()
        assertThat(XtcImportOptimizer.action(null)).isNull()
    }

    @Test
    fun `the edits of the document's versioned and plain changes are accepted`() {
        val versioned =
            WorkspaceEdit(
                listOf(
                    Either.forLeft<TextDocumentEdit, ResourceOperation>(
                        TextDocumentEdit(VersionedTextDocumentIdentifier(uri, 3), listOf(removal)),
                    ),
                ),
            )
        assertThat(XtcImportOptimizer.edits(versioned, uri)).containsExactly(removal)
        assertThat(XtcImportOptimizer.edits(WorkspaceEdit(mapOf(uri to listOf(removal))), uri)).containsExactly(removal)
    }

    @Test
    fun `an edit of another file or of the file system is refused whole`() {
        assertThatIllegalArgumentException().isThrownBy {
            XtcImportOptimizer.edits(WorkspaceEdit(mapOf(uri to listOf(removal), "file:///work/Other.x" to listOf(removal))), uri)
        }
        assertThatIllegalArgumentException().isThrownBy {
            XtcImportOptimizer.edits(
                WorkspaceEdit(listOf(Either.forRight<TextDocumentEdit, ResourceOperation>(CreateFile("file:///work/New.x")))),
                uri,
            )
        }
    }
}
