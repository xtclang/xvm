package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.Range

/** Immutable contracts and atomic class-member edits. */
internal object XdkMemberActions {
    data class Import(val position: SemanticModel.Position, val declaration: String)

    data class Edits(val member: XdkRename.Edit, val imports: List<XdkRename.Edit>) {
        val all: List<XdkRename.Edit> get() = imports + member
    }

    data class Candidate(
        val owner: SemanticModel.SourceLocation,
        val contract: ProofIdentity,
        val insertion: SemanticModel.Position,
        val declaration: String,
        val implementation: Boolean,
        val imports: List<Import>,
    ) {
        val title: String get() = "${if (implementation) "Implement" else "Override"} $declaration"

        fun selected(
            source: String,
            range: Range,
        ): Boolean =
            owner.sourceName == source &&
                owner.range.start <= SemanticModel.Position(range.end.line, range.end.column) &&
                owner.range.end >= SemanticModel.Position(range.start.line, range.start.column)

    }

    data class Action(val members: List<Candidate>) {
        init {
            require(members.isNotEmpty())
            require(members.map { it.owner to it.insertion }.distinct().size == 1)
            require(members.size == 1 || members.all { it.implementation })
        }

        val title: String get() = members.singleOrNull()?.title ?: "Implement all required members (${members.size})"

        fun edit(text: String): Edits? {
            val owner = members.first().owner
            val at = XdkRename.offset(text, members.first().insertion) ?: return null
            if (text.getOrNull(at) != '}') return null
            val newline = if ("\r\n" in text) "\r\n" else "\n"
            val lineStart = text.lastIndexOf('\n', at - 1) + 1
            val whitespace = text.substring(lineStart, at)
            val ownLine = whitespace.all { it == ' ' || it == '\t' }
            val ownerLine = text.lineSequence().elementAtOrNull(owner.range.start.line).orEmpty()
            val indent = ownerLine.takeWhile { it == ' ' || it == '\t' }
            val memberIndent = "$indent    "
            val body = members.joinToString(newline) { member ->
                "$memberIndent@Override$newline$memberIndent${member.declaration} {$newline" +
                    "$memberIndent    TODO();$newline$memberIndent}$newline"
            }
            val memberEdit = if (ownLine) {
                XdkRename.Edit(lineStart, lineStart, body)
            } else {
                XdkRename.Edit(at, at, "$newline$body$indent")
            }
            val imports = members.flatMap { it.imports }.distinct().groupBy { it.position }.map { (position, declarations) ->
                val offset = XdkRename.offset(text, position) ?: return null
                if (offset != 0 && text.getOrNull(offset - 1) != '{') return null
                val block = declarations.joinToString(newline, postfix = newline) { it.declaration }
                XdkRename.Edit(offset, offset, if (offset == 0) block else "$newline$block")
            }
            return Edits(memberEdit, imports)
        }
    }

    fun actions(candidates: List<Candidate>, source: String, range: Range): List<Action> {
        val selected = candidates.filter { it.selected(source, range) }.distinct()
        val combined = selected.filter { it.implementation }.groupBy { it.owner to it.insertion }.values
            .filter { it.size > 1 }.map { Action(it.sortedBy(Candidate::declaration)) }
        // A combined repair must contain all available required members, even when individual
        // actions exceed the query limit. Complete compilation rejects any unsupported remainder.
        return (combined + selected.sortedByDescending { it.implementation }.map { Action(listOf(it)) }).take(32)
    }
}
