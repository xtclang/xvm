package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.Range

/** Immutable source contracts for the first bounded implement/override action. */
internal object XdkMemberActions {
    data class Candidate(
        val owner: SemanticModel.SourceLocation,
        val contract: SemanticModel.SourceLocation,
        val insertion: SemanticModel.Position,
        val declaration: String,
        val implementation: Boolean,
    ) {
        val title: String get() = "${if (implementation) "Implement" else "Override"} $declaration"

        fun selected(
            source: String,
            range: Range,
        ): Boolean =
            owner.sourceName == source &&
                owner.range.start <= SemanticModel.Position(range.end.line, range.end.column) &&
                owner.range.end >= SemanticModel.Position(range.start.line, range.start.column)

        fun edit(text: String): XdkRename.Edit? {
            val at = XdkRename.offset(text, insertion) ?: return null
            if (text.getOrNull(at) != '}') return null
            val newline = if ("\r\n" in text) "\r\n" else "\n"
            val lineStart = text.lastIndexOf('\n', at - 1) + 1
            val whitespace = text.substring(lineStart, at)
            val ownLine = whitespace.all { it == ' ' || it == '\t' }
            val ownerLine = text.lineSequence().elementAtOrNull(owner.range.start.line).orEmpty()
            val indent = ownerLine.takeWhile { it == ' ' || it == '\t' }
            val memberIndent = "$indent    "
            val body =
                "$memberIndent@Override$newline$memberIndent$declaration {$newline" +
                    "$memberIndent    TODO();$newline$memberIndent}$newline"
            return if (ownLine) {
                XdkRename.Edit(lineStart, lineStart, body)
            } else {
                XdkRename.Edit(at, at, "$newline$body$indent")
            }
        }
    }
}
