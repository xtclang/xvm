package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.Range

/** Detached signatures from fresh declarations; only a complete repair proof can publish them. */
internal object XdkMissingMethods {
    enum class Dispatch {
        INSTANCE,
        STATIC,
    }

    data class Receiver(
        val destination: SemanticModel.SourceLocation,
        val dispatch: Dispatch,
    )

    data class Signature(
        val parameters: List<ProofIdentity>,
        val returns: List<ProofIdentity>,
        val isPublic: Boolean = true,
    )

    /** Detached body evidence copied before the fresh declaration attempt checks eligibility. */
    data class Inputs(
        val localTypes: Map<SemanticModel.SourceLocation, String> = emptyMap(),
        val receivers: Map<SemanticModel.SourceLocation, Receiver> = emptyMap(),
    )

    data class ArgumentBinding(
        val use: SemanticModel.SourceLocation,
        val declaration: SemanticModel.SourceLocation,
    )

    data class Candidate(
        val callee: SemanticModel.SourceLocation,
        val name: String,
        val insertion: SemanticModel.Position,
        val destination: SemanticModel.SourceLocation,
        val declaration: String,
        val dispatch: Dispatch,
        val arguments: List<ArgumentBinding> = emptyList(),
        val publicOwner: String? = null,
        val signature: Signature? = null,
    ) {
        val title: String get() = publicOwner?.let { "Create public method '$name' in '$it'" } ?: "Create private method '$name'"

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
                    .elementAtOrNull(destination.range.start.line)
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
            val targetSource = destination.sourceName ?: return false
            val targetText = plan.proposed[targetSource] ?: return false
            val declarationAt = XdkRename.offset(targetText, declaration.start) ?: return false
            if (symbol.kind != SemanticModel.SymbolKind.METHOD || symbol.name != name || symbol.declarationSource != targetSource ||
                (SemanticModel.Modifier.STATIC in symbol.modifiers) != (dispatch == Dispatch.STATIC) ||
                declarationAt !in edit.start until edit.start + edit.text.length
            ) {
                return false
            }

            if (publicOwner != null) {
                val expected = signature ?: return false
                val actual = after.methodSignatures[symbol.id] ?: return false
                if (!actual.isPublic) return false

                fun equivalent(
                    expected: List<ProofIdentity>,
                    actual: List<ProofIdentity>,
                ) = expected.size == actual.size && expected.zip(actual).all { (old, new) -> XdkRename.sameType(old, new, plan) }
                if (!equivalent(expected.parameters, actual.parameters) || !equivalent(expected.returns, actual.returns)) return false
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
