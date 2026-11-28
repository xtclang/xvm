package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.compiler.ast.NewExpression
import org.xvm.compiler.ast.ReturnStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.adapter.Range

/** Syntax chooses a writable declaration; a complete compiler repair must prove its use. */
internal object XdkMissingDeclarations {
    /**
     * Proposed declaration and its unresolved use, retained so recompilation can prove that the use binds to
     * the inserted declaration.
     */
    data class Candidate(
        val name: String,
        val kind: SemanticModel.SymbolKind,
        val use: SemanticModel.SourceLocation,
        val edit: XdkRename.Edit,
        private val expectedType: ProofIdentity? = null,
    ) {
        val title: String get() = "Create ${if (kind == SemanticModel.SymbolKind.TYPE) "class" else "read-only property"} '$name'"

        fun proves(
            after: CompilerRenameFacts,
            plan: XdkRename.Plan,
        ): Boolean {
            val source = use.sourceName ?: return false
            val changed = plan.proposed[source] ?: return false
            val at = plan.mapPosition(source, use.range.start) ?: return false
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
                val finish = plan.mapPosition(source, use.range.end) ?: return false
                val location =
                    SemanticModel.SourceLocation(
                        source,
                        SemanticModel.Range(at, finish),
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
        val root = XdkRefactoringSyntax.parse(text, errors, "missing-declarations") ?: return emptyList()

        val parents = XdkRefactoringSyntax.tree(root).toMap()

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

        // A class to create for a new expression of a type nothing declares, with no arguments: its use is the type.
        fun constructed(node: NewExpression): Candidate? {
            if (node.isVirtualNew || node.hasSquareBrackets() || node.arguments.isNotEmpty() ||
                node.isComponentNode || node.unregisteredBody.isPresent
            ) {
                return null
            }
            val type = node.childNodes().singleOrNull() as? NamedTypeExpression ?: return null
            val name = type.name.takeIf(XdkRename::identifier) ?: return null
            val start = XdkRename.offset(text, position(type.startPosition)) ?: return null
            val end = XdkRename.offset(text, position(type.endPosition)) ?: return null
            if (text.substring(start, end) != name) return null
            val owner =
                generateSequence(parents[node]) { parents[it] }.filterIsInstance<TypeCompositionStatement>().firstOrNull() ?: return null
            if (owner.category.id != Token.Id.MODULE) return null
            return Candidate(name, SemanticModel.SymbolKind.TYPE, location(type), insertion(owner, "class $name {}") ?: return null)
        }

        // The statement of a block that holds a node, the node itself when it is one.
        fun statement(node: AstNode): AstNode? = generateSequence(node) { parents[it] }.firstOrNull { parents[it] is StatementBlock }
        return parents.keys
            .mapNotNull { node ->
                when (node) {
                    is NewExpression -> constructed(node)?.takeIf { selected(node.childNodes().single()) }

                    // The same class for a use of its name that the compiler reports unresolved in the statement that
                    // constructs it, as the type of "Joker joker = new Joker();": the statement's first error, and so the
                    // only diagnostic that an editor asks a quick fix for.
                    is NamedTypeExpression -> {
                        if (parents[node] is NewExpression || !selected(node)) return@mapNotNull null
                        val use = location(node)
                        if (model.occurrences.none { it.symbol == null && it.name == node.name && it.range == use.range }) {
                            return@mapNotNull null
                        }
                        val held = statement(node) ?: return@mapNotNull null
                        parents.keys
                            .filterIsInstance<NewExpression>()
                            .filter { statement(it) === held }
                            .firstNotNullOfOrNull { constructed(it)?.takeIf { candidate -> candidate.name == node.name } }
                            ?.copy(use = use)
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
                        // Static properties are constants: the compiler requires a value and
                        // forbids custom getters. A repair cannot invent that value.
                        if (SemanticModel.Modifier.STATIC in declared.modifiers) return@mapNotNull null
                        val edit = insertion(owner, "private $type ${node.name}.get() { TODO(); }") ?: return@mapNotNull null
                        Candidate(node.name, SemanticModel.SymbolKind.PROPERTY, location(node), edit, expected)
                    }

                    else -> null
                }
            }.distinctBy { Triple(it.name, it.kind, it.edit) }
    }
}
