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

    /** Source ownership and spelling context copied from a successfully compiled project module. */
    data class Destination(
        val location: SemanticModel.SourceLocation,
        val insertion: SemanticModel.Position,
        val modules: Map<String, String>,
        val imports: Map<String, XdkMemberActions.Import> = emptyMap(),
        val importSource: String? = null,
    )

    data class TypeSource(
        val source: String,
        val imports: List<XdkMemberActions.Import> = emptyList(),
    )

    data class Signature(
        val parameters: List<ProofIdentity>,
        val returns: List<ProofIdentity>,
        val isPublic: Boolean = true,
        val formals: List<ProofIdentity> = emptyList(),
        val conditional: Boolean = false,
    )

    /** Detached body evidence copied before the fresh declaration attempt checks eligibility. */
    data class Inputs(
        val localTypes: Map<SemanticModel.SourceLocation, LocalType> = emptyMap(),
        val receivers: Map<SemanticModel.SourceLocation, Receiver> = emptyMap(),
    )

    data class LocalType(
        val source: String?,
        val identity: ProofIdentity,
        val destinationSources: Map<SemanticModel.SourceLocation, TypeSource> = emptyMap(),
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
        val importSource: String? = null,
        val imports: List<XdkMemberActions.Import> = emptyList(),
    ) {
        val title: String get() = publicOwner?.let { "Create public method '$name' in '$it'" } ?: "Create private method '$name'"

        fun selected(
            source: String,
            range: Range,
        ): Boolean =
            callee.sourceName == source &&
                callee.range.start <= SemanticModel.Position(range.end.line, range.end.column) &&
                callee.range.end >= SemanticModel.Position(range.start.line, range.start.column)

        fun edit(
            text: String,
            importText: String = text,
        ): XdkMemberActions.Edits? {
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
            val member = if (ownLine) XdkRename.Edit(lineStart, lineStart, body) else XdkRename.Edit(at, at, "$newline$body$indent")
            return XdkMemberActions.Edits(member, XdkMemberActions.importEdits(importText, imports) ?: return null)
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
            if (edit !in plan.edits[targetSource].orEmpty()) return false
            val insertedAt = (plan.map(targetSource, edit.start) ?: return false) - edit.text.length
            if (symbol.kind != SemanticModel.SymbolKind.METHOD || symbol.name != name || symbol.declarationSource != targetSource ||
                (SemanticModel.Modifier.STATIC in symbol.modifiers) != (dispatch == Dispatch.STATIC) ||
                declarationAt !in insertedAt until insertedAt + edit.text.length
            ) {
                return false
            }

            if (signature != null || publicOwner != null) {
                val expected = signature ?: return false
                // A dependency use has a different snapshot-local symbol id. Read the signature
                // from the exact source declaration selected by the completed caller instead.
                val target = after.models.singleOrNull { it.sourceName == targetSource } ?: return false
                val declared = target.symbolAt(declaration.start.line, declaration.start.column) ?: return false
                if (declared.declaration != declaration || declared.name != name) return false
                val actual = after.methodSignatures[declared.id] ?: return false
                if (actual.isPublic != expected.isPublic || actual.conditional != expected.conditional ||
                    actual.formals.size != expected.formals.size
                ) {
                    return false
                }
                // New method formals are alpha-renamed binders. Compare their constraints and
                // all uses by ordinal, never by source spelling or assignability.
                val binders = expected.formals.zip(actual.formals).toMap()

                fun sameType(
                    old: ProofIdentity,
                    new: ProofIdentity,
                ): Boolean =
                    when {
                        old in binders -> {
                            binders[old] == new
                        }

                        old is ProofIdentity.TypeShape && new is ProofIdentity.TypeShape -> {
                            old.format == new.format && old.components.size == new.components.size &&
                                old.components.zip(new.components).all { (first, second) -> sameType(first, second) }
                        }

                        old is ProofIdentity.Alternatives && new is ProofIdentity.Alternatives -> {
                            old.targets.size == new.targets.size && old.targets.all { first -> new.targets.any { sameType(first, it) } }
                        }

                        else -> {
                            XdkRename.sameType(old, new, plan)
                        }
                    }

                fun equivalent(
                    expected: List<ProofIdentity>,
                    actual: List<ProofIdentity>,
                ) = expected.size == actual.size && expected.zip(actual).all { (old, new) -> sameType(old, new) }
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
