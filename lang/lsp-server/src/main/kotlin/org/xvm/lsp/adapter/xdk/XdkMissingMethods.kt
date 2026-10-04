package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.Range

/** Detached signatures from fresh declarations; only a complete repair proof can publish them. */
internal object XdkMissingMethods {
    data class ArgumentBinding(
        val use: SemanticModel.SourceLocation,
        val declaration: SemanticModel.SourceLocation,
    )

    data class Candidate(
        val callee: SemanticModel.SourceLocation,
        val name: String,
        val insertion: SemanticModel.Position,
        val owner: SemanticModel.Position,
        val declaration: String,
        val arguments: List<ArgumentBinding> = emptyList(),
    ) {
        val title: String get() = "Create private method '$name'"

        fun selected(
            source: String,
            range: Range,
        ): Boolean =
            callee.sourceName == source &&
                callee.range.start <= SemanticModel.Position(range.end.line, range.end.column) &&
                callee.range.end >= SemanticModel.Position(range.start.line, range.start.column)

        fun edit(text: String): XdkRename.Edit? {
            val at = XdkRename.offset(text, insertion) ?: return null
            if (text.getOrNull(at) != '}') return null
            val newline = Regex("\r\n|\r|\n").find(text)?.value ?: "\n"
            val lineStart = maxOf(text.lastIndexOf('\n', at - 1), text.lastIndexOf('\r', at - 1)) + 1
            val ownLine = text.substring(lineStart, at).all { it == ' ' || it == '\t' }
            val indent =
                text
                    .lineSequence()
                    .elementAtOrNull(owner.line)
                    .orEmpty()
                    .takeWhile { it == ' ' || it == '\t' }
            val body = "$indent    $declaration {$newline$indent        TODO();$newline$indent    }$newline"
            return if (ownLine) XdkRename.Edit(lineStart, lineStart, body) else XdkRename.Edit(at, at, "$newline$body$indent")
        }

        /** Successful compilation must resolve this call to the inserted declaration itself. */
        fun bindsNewMethod(
            after: CompilerRenameFacts,
            plan: XdkRename.Plan,
            edit: XdkRename.Edit,
        ): Boolean {
            val source = callee.sourceName ?: return false
            val original = plan.original[source] ?: return false
            val changed = plan.proposed[source] ?: return false
            val offset = XdkRename.offset(original, callee.range.start)?.let { plan.map(source, it) } ?: return false
            val at = XdkRename.position(changed, offset)
            val model = after.models.singleOrNull { it.sourceName == source } ?: return false
            val symbol = model.symbolAt(at.line, at.column) ?: return false
            val declaration = symbol.declaration ?: return false
            val declarationAt = XdkRename.offset(changed, declaration.start) ?: return false
            if (symbol.kind != SemanticModel.SymbolKind.METHOD || symbol.name != name || symbol.declarationSource != source ||
                declarationAt !in edit.start until edit.start + edit.text.length
            ) {
                return false
            }

            fun mapped(location: SemanticModel.SourceLocation): SemanticModel.Position? {
                if (location.sourceName != source) return null
                val originalAt = XdkRename.offset(original, location.range.start) ?: return null
                val changedAt = plan.map(source, originalAt) ?: return null
                return XdkRename.position(changed, changedAt).let { SemanticModel.Position(it.line, it.column) }
            }
            return arguments.all { argument ->
                val use = mapped(argument.use) ?: return@all false
                val target = mapped(argument.declaration) ?: return@all false
                val bound = model.symbolAt(use.line, use.column) ?: return@all false
                bound.kind == SemanticModel.SymbolKind.VARIABLE && bound.declarationSource == source && bound.declaration?.start == target
            }
        }
    }
}
