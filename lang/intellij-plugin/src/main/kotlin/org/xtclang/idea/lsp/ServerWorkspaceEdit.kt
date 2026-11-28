package org.xtclang.idea.lsp

import com.intellij.openapi.editor.Document
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either

/**
 * Reads a server's workspace edit for the edits the plugin applies itself, Optimize Imports' and Rename's; LSP4IJ applies
 * the server's workspace/applyEdit.
 */
internal object ServerWorkspaceEdit {
    /** Text replacement expressed in offsets of the validated document snapshot. */
    internal data class Replacement(
        val start: Int,
        val end: Int,
        val text: String,
    )

    /**
     * Whether the edit is undone for the whole project, as IntelliJ's refactorings are, which Undo asks about
     * first: when it changes more than one document, or carries a resource operation, which makes, renames or
     * deletes a file. A text edit of one document, a quick fix's usual edit, is undone in its editor as a
     * typed change is, without the question.
     */
    internal fun globalUndo(edit: WorkspaceEdit): Boolean {
        val changes = edit.documentChanges
        return if (changes != null) {
            changes.any { it.isRight } || changes.map { it.left.textDocument.uri }.distinct().size > 1
        } else {
            edit.changes.orEmpty().size > 1
        }
    }

    internal fun replacements(
        document: Document,
        edits: List<TextEdit>,
    ): List<Replacement> {
        fun offset(at: Position): Int {
            require(at.line in 0 until document.lineCount && at.character >= 0) {
                "Invalid edit position"
            }
            val start = document.getLineStartOffset(at.line)
            require(at.character <= document.getLineEndOffset(at.line) - start) {
                "Edit exceeds line bounds"
            }
            val offset = start + at.character
            val text = document.immutableCharSequence
            require(
                offset == 0 ||
                    offset == text.length ||
                    !Character.isSurrogatePair(text[offset - 1], text[offset]),
            ) {
                "Edit splits a UTF-16 surrogate pair"
            }
            return offset
        }
        val replacements =
            edits
                .map {
                    Replacement(offset(it.range.start), offset(it.range.end), it.newText)
                        .also { edit ->
                            require(edit.start <= edit.end) { "Reversed edit range" }
                        }
                }.sortedWith(compareBy({ it.start }, { it.end }))
        require(
            replacements.zipWithNext().all { (a, b) -> a.end <= b.start && a.start != b.start },
        ) {
            "Overlapping edits"
        }
        return replacements
    }

    // IntelliJ's platform API declares List<TextEdit>; LSP4IJ also supplies LSP4J 1.0's
    // List<Either<TextEdit, SnippetTextEdit>>. Its own compatibility adapter accepts both.
    // Normalize at this boundary without unchecked casts or replacing platform libraries.
    internal fun textEdits(edits: List<*>): List<TextEdit> =
        edits.map { edit ->
            requireNotNull(
                when (edit) {
                    is TextEdit -> edit
                    is Either<*, *> -> if (edit.isLeft) edit.left as? TextEdit else null
                    else -> null
                },
            ) {
                "Unsupported or malformed text edit (including snippet edits)"
            }
        }
}
