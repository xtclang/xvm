package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.compiler.ast.NewExpression
import org.xvm.compiler.ast.ReturnStatement
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.util.ExecutionTrace

/** Syntax chooses a writable declaration; a complete compiler repair must prove its use. */
internal object XdkMissingDeclarations {
    data class Candidate(
        val name: String,
        val kind: SemanticModel.SymbolKind,
        val use: SemanticModel.SourceLocation,
        val edit: XdkRename.Edit,
        val expectedType: ProofIdentity? = null,
    ) {
        val title: String get() = "Create ${if (kind == SemanticModel.SymbolKind.TYPE) "class" else "read-only property"} '$name'"

        fun proves(
            after: CompilerRenameFacts,
            plan: XdkRename.Plan,
        ): Boolean {
            val source = use.sourceName ?: return false
            val original = plan.original[source] ?: return false
            val changed = plan.proposed[source] ?: return false
            val useOffset = XdkRename.offset(original, use.range.start)?.let { plan.map(source, it) } ?: return false
            val at = XdkRename.position(changed, useOffset)
            val model = after.models.singleOrNull { it.sourceName == source } ?: return false
            val selected = model.symbolAt(at.line, at.column) ?: return false
            val declaration = selected.declaration ?: return false
            val insertedAt = (plan.map(source, edit.start) ?: return false) - edit.text.length
            val declarationAt = XdkRename.offset(changed, declaration.start) ?: return false
            if (selected.name != name || selected.kind != kind || selected.declarationSource != source ||
                declarationAt !in insertedAt until insertedAt + edit.text.length
            ) {
                return false
            }
            if (expectedType != null) {
                val end = XdkRename.offset(original, use.range.end)?.let { plan.map(source, it) } ?: return false
                val finish = XdkRename.position(changed, end)
                val location =
                    SemanticModel.SourceLocation(
                        source,
                        SemanticModel.Range(SemanticModel.Position(at.line, at.column), SemanticModel.Position(finish.line, finish.column)),
                    )
                val actual = after.extraction.types[location] ?: return false
                if (!XdkRename.sameType(expectedType, actual, plan)) return false
            }
            return true
        }
    }

    fun candidates(
        text: String,
        selection: Range,
        model: SemanticModel,
        facts: CompilerRenameFacts,
    ): List<Candidate> {
        val source = model.sourceName ?: return emptyList()
        val errors = ErrorList()
        val root =
            try {
                ExecutionTrace.api("Parser.parseSource(missing-declarations)") { Parser(Source(text), errors).parseSource() }
            } catch (_: CompilerException) {
                return emptyList()
            }
        if (errors.hasSeriousErrors()) return emptyList()

        fun tree(
            node: AstNode,
            parent: AstNode? = null,
        ): Sequence<Pair<AstNode, AstNode?>> = sequenceOf(node to parent) + node.childNodes().asSequence().flatMap { tree(it, node) }
        val parents = tree(root).toMap()

        fun position(value: Long) = SemanticModel.Position(Source.calculateLine(value), Source.calculateOffset(value))

        fun location(node: AstNode) =
            SemanticModel.SourceLocation(source, SemanticModel.Range(position(node.startPosition), position(node.endPosition)))

        fun selected(node: AstNode): Boolean {
            val range = location(node).range
            return range.start <= SemanticModel.Position(selection.end.line, selection.end.column) &&
                range.end >= SemanticModel.Position(selection.start.line, selection.start.column)
        }

        fun insertion(
            owner: TypeCompositionStatement,
            declaration: String,
        ): XdkRename.Edit? {
            val at = XdkRename.offset(text, position(owner.ensureBody().endPosition))?.minus(1) ?: return null
            if (text.getOrNull(at) != '}') return null
            val newline = Regex("\r\n|\r|\n").find(text)?.value ?: "\n"
            val indent =
                text.lineSequence().elementAtOrNull(position(owner.startPosition).line).orEmpty().takeWhile {
                    it == ' ' ||
                        it == '\t'
                }
            return XdkRename.Edit(at, at, "$newline$indent    $declaration$newline$indent")
        }
        return parents.keys
            .mapNotNull { node ->
                when (node) {
                    is NewExpression -> {
                        if (node.isVirtualNew || node.hasSquareBrackets() || node.arguments.isNotEmpty() ||
                            node.isComponentNode || node.unregisteredBody.isPresent
                        ) {
                            return@mapNotNull null
                        }
                        val type = node.childNodes().singleOrNull() as? NamedTypeExpression ?: return@mapNotNull null
                        val name = type.name.takeIf(XdkRename::identifier) ?: return@mapNotNull null
                        if (!selected(type)) return@mapNotNull null
                        val start = XdkRename.offset(text, position(type.startPosition)) ?: return@mapNotNull null
                        val end = XdkRename.offset(text, position(type.endPosition)) ?: return@mapNotNull null
                        if (text.substring(start, end) != name) return@mapNotNull null
                        val owner =
                            generateSequence(parents[node]) { parents[it] }.filterIsInstance<TypeCompositionStatement>().firstOrNull()
                                ?: return@mapNotNull null
                        if (owner.category.id != Token.Id.MODULE) return@mapNotNull null
                        Candidate(
                            name,
                            SemanticModel.SymbolKind.TYPE,
                            location(type),
                            insertion(owner, "class $name {}") ?: return@mapNotNull null,
                        )
                    }

                    is NameExpression -> {
                        if (!node.isSimpleName || node.hasTrailingTypeParams() || node.isSuppressDeref ||
                            !selected(node)
                        ) {
                            return@mapNotNull null
                        }
                        val returned = parents[node] as? ReturnStatement ?: return@mapNotNull null
                        if (returned.expressions?.singleOrNull() !== node) return@mapNotNull null
                        val method =
                            generateSequence(parents[node]) { parents[it] }.filterIsInstance<MethodDeclarationStatement>().firstOrNull()
                                ?: return@mapNotNull null
                        val owner = parents[parents[method]] as? TypeCompositionStatement ?: return@mapNotNull null
                        val methodToken = method.nameToken ?: return@mapNotNull null
                        val methodAt = position(methodToken.startPosition)
                        val declared = model.symbolAt(methodAt.line, methodAt.column) ?: return@mapNotNull null
                        if (declared.signature?.conditional != false ||
                            declared.signature.parameters.any { it.typeParameter }
                        ) {
                            return@mapNotNull null
                        }
                        val type = XdkLocalExtraction.writtenReturnType(text, method) ?: return@mapNotNull null
                        val expected = facts.methodSignatures[declared.id]?.returns?.singleOrNull() ?: return@mapNotNull null
                        val modifiers = if (SemanticModel.Modifier.STATIC in declared.modifiers) "private static" else "private"
                        val edit = insertion(owner, "$modifiers $type ${node.name}.get() { TODO(); }") ?: return@mapNotNull null
                        Candidate(node.name, SemanticModel.SymbolKind.PROPERTY, location(node), edit, expected)
                    }

                    else -> {
                        null
                    }
                }
            }.distinct()
    }
}
