package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorList
import org.xvm.compiler.Lexer
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AnnotationExpression
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.ReturnStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.compiler.ast.TypeExpression
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.util.ExecutionTrace

/**
 * Copy one private member expression at its use, keeping the declaration, or at every use, removing the
 * declaration. Each is proven separately.
 */
internal object XdkMemberInline {
    /** Proposed member inlining with the use, replacement and intentionally removed binding sites. */
    data class Candidate(
        val title: String,
        val source: String,
        val expression: SemanticModel.Range,
        val use: SemanticModel.Range,
        val edit: XdkRename.Edit,
        val ignored: Set<SemanticModel.SourceLocation>,
    )

    /** One use of the member, which a copy of its expression replaces. */
    data class Use(
        val range: SemanticModel.Range,
        val edit: XdkRename.Edit,
    )

    /**
     * Proposed inlining of every use of a member, with the member removed: its uses, the removal, and the binding sites
     * that go, which are the uses' and those of the declaration outside its expression.
     */
    data class Removal(
        val title: String,
        val source: String,
        val identity: ProofIdentity,
        val expression: SemanticModel.Range,
        val uses: List<Use>,
        val removal: XdkRename.Edit,
        val ignored: Set<SemanticModel.SourceLocation>,
    ) {
        val edits: List<XdkRename.Edit> get() = uses.map { it.edit } + removal
    }

    fun candidate(
        text: String,
        selection: Range,
        model: SemanticModel,
        facts: CompilerRenameFacts,
    ): Candidate? {
        val source = model.sourceName ?: return null
        val symbol = model.symbolAt(selection.start.line, selection.start.column) ?: return null
        if (symbol.declarationSource != source) return null
        val root = XdkRefactoringSyntax.parse(text, ErrorList(), "inline-member") ?: return null
        val syntax = Syntax(XdkRefactoringSyntax.tree(root).toMap())
        val selected = SemanticModel.Position(selection.start.line, selection.start.column)
        val read = syntax.read { selected in syntax.range(it) } ?: return null
        val (member, expression) = syntax.declared(symbol, source, facts) ?: return null
        val use = syntax.use(read, symbol.kind) ?: return null
        if (!syntax.usable(member, use)) return null
        val value = syntax.copyable(text, model, symbol, member, expression) ?: return null
        val useRange = syntax.range(use)
        val start = XdkRename.offset(text, useRange.start) ?: return null
        val end = XdkRename.offset(text, useRange.end) ?: return null
        return Candidate(
            if (symbol.kind == SemanticModel.SymbolKind.METHOD) "Inline private method call" else "Inline constant property",
            source,
            syntax.range(expression),
            useRange,
            XdkRename.Edit(start, end, "($value)"),
            within(model, source, listOf(useRange)),
        )
    }

    /**
     * Replace every use of the selected member, at a use or at its declaration, by a copy of its expression, and remove
     * the member. Every use must be one that [candidate] would copy to.
     */
    fun everyUse(
        text: String,
        selection: Range,
        model: SemanticModel,
        facts: CompilerRenameFacts,
    ): Removal? {
        val source = model.sourceName ?: return null
        val symbol = model.symbolAt(selection.start.line, selection.start.column) ?: return null
        if (symbol.declarationSource != source) return null
        val identity = facts.constants[symbol.id] as? ProofIdentity.Source ?: return null
        val root = XdkRefactoringSyntax.parse(text, ErrorList(), "inline-member") ?: return null
        val syntax = Syntax(XdkRefactoringSyntax.tree(root).toMap())
        val (member, expression) = syntax.declared(symbol, source, facts) ?: return null
        val value = syntax.copyable(text, model, symbol, member, expression) ?: return null
        val declared = syntax.range(member)

        fun inMember(range: SemanticModel.Range) = range.start >= declared.start && range.end <= declared.end
        // A private member's uses are in its own source; one anywhere else is not copied to.
        val references =
            facts.models.flatMap { view ->
                view.occurrences
                    .filter { it.symbol?.let(facts.constants::get) == identity }
                    .map { view.sourceName to it.range } +
                    view.calls.filter { facts.constants[it.method] == identity }.map { view.sourceName to it.callee }
            }
        if (references.any { (path, _) -> path != source }) return null
        val uses =
            references
                .map { (_, range) -> range }
                .filterNot(::inMember)
                .map { range ->
                    val read = syntax.read { range.start in syntax.range(it) } ?: return null
                    syntax.use(read, symbol.kind)?.takeIf { syntax.usable(member, it) } ?: return null
                }.distinct()
                .sortedBy { it.startPosition }
        if (uses.isEmpty()) return null
        val copies =
            uses.map { use ->
                val range = syntax.range(use)
                val start = XdkRename.offset(text, range.start) ?: return null
                val end = XdkRename.offset(text, range.end) ?: return null
                Use(range, XdkRename.Edit(start, end, "($value)"))
            }
        val removal = XdkSafeDelete.removal(text, member) ?: return null
        val edits = (copies.map { it.edit } + removal).sortedBy { it.start }
        if (edits.zipWithNext().any { (first, next) -> first.end > next.start }) return null
        val copied = syntax.range(expression)
        val removed =
            model.occurrences
                .map { it.range }
                .filter { inMember(it) && !(it.start >= copied.start && it.end <= copied.end) }
                .mapTo(linkedSetOf()) { SemanticModel.SourceLocation(source, it) }
        return Removal(
            if (symbol.kind == SemanticModel.SymbolKind.METHOD) {
                "Inline and remove private method '${symbol.name}'"
            } else {
                "Inline and remove constant property '${symbol.name}'"
            },
            source,
            identity,
            copied,
            copies,
            removal,
            within(model, source, copies.map { it.range }) + removed,
        )
    }

    /** The source's binding sites within the given ranges. */
    private fun within(
        model: SemanticModel,
        source: String,
        ranges: List<SemanticModel.Range>,
    ): Set<SemanticModel.SourceLocation> =
        model.occurrences
            .filter { occurrence -> ranges.any { occurrence.range.start >= it.start && occurrence.range.end <= it.end } }
            .mapTo(linkedSetOf()) { SemanticModel.SourceLocation(source, it.range) }

    /** A parsed source, with each node's parent. */
    private class Syntax(
        val parents: Map<AstNode, AstNode?>,
    ) {
        fun at(value: Long) = SemanticModel.Position(Source.calculateLine(value), Source.calculateOffset(value))

        fun range(node: AstNode) = SemanticModel.Range(at(node.startPosition), at(node.endPosition))

        fun owner(node: AstNode) =
            generateSequence(parents[node]) { parents[it] }.filterIsInstance<TypeCompositionStatement>().firstOrNull()

        /** The simple name read that [where] picks. */
        fun read(where: (NameExpression) -> Boolean): NameExpression? =
            parents.keys.filterIsInstance<NameExpression>().singleOrNull {
                it.isSimpleName && !it.hasTrailingTypeParams() && !it.isSuppressDeref && where(it)
            }

        /** The member's declaration and the expression it gives, when it is one that can be copied. */
        fun declared(
            symbol: SemanticModel.Symbol,
            source: String,
            facts: CompilerRenameFacts,
        ): Pair<AstNode, Expression>? {
            val declaration = symbol.declaration ?: return null
            return when (symbol.kind) {
                SemanticModel.SymbolKind.METHOD -> {
                    val signature = symbol.signature ?: return null
                    if (signature.parameters.isNotEmpty() || signature.conditional || signature.returns.size != 1) return null
                    val method =
                        parents.keys.filterIsInstance<MethodDeclarationStatement>().singleOrNull {
                            it.nameToken?.startPosition?.let(::at) == declaration.start
                        } ?: return null
                    if (method.defaultAccess != Access.PRIVATE || method.isConstructor || method.isConstructorFinally) return null
                    val block = method.childNodes().filterIsInstance<StatementBlock>().singleOrNull() ?: return null
                    val returned = block.childNodes().singleOrNull() as? ReturnStatement ?: return null
                    method to (returned.expressions?.singleOrNull() ?: return null)
                }

                SemanticModel.SymbolKind.PROPERTY -> {
                    if (SemanticModel.SourceLocation(source, declaration) !in facts.constantProperties) return null
                    val property =
                        parents.keys.filterIsInstance<PropertyDeclarationStatement>().singleOrNull {
                            at(it.nameToken.startPosition) == declaration.start
                        } ?: return null
                    if (property.defaultAccess != Access.PRIVATE || !property.isStatic) return null
                    if (property.childNodes().any { it is StatementBlock }) return null
                    property to
                        (
                            property
                                .childNodes()
                                .filterIsInstance<Expression>()
                                .filterNot { it is TypeExpression }
                                .singleOrNull() ?: return null
                        )
                }

                else -> null
            }
        }

        /** What a copy replaces at [read]: a method's call, or a property's read. */
        fun use(
            read: NameExpression,
            kind: SemanticModel.SymbolKind,
        ): AstNode? =
            if (kind == SemanticModel.SymbolKind.METHOD) {
                (parents[read] as? InvocationExpression)?.takeIf { !it.isAsync && it.childNodes().singleOrNull() === read }
            } else {
                read
            }

        /** A use in the member's own class, outside any local callable or closure, which can have another receiver or captures. */
        fun usable(
            member: AstNode,
            use: AstNode,
        ): Boolean =
            owner(member) === owner(use) &&
                generateSequence(parents[use]) { parents[it] }
                    .takeWhile { it !== owner(use) }
                    .none { it is LambdaExpression }

        /** The text of [expression], when a copy of it means what it does in the member; null otherwise. */
        fun copyable(
            text: String,
            model: SemanticModel,
            symbol: SemanticModel.Symbol,
            member: AstNode,
            expression: Expression,
        ): String? {
            if (XdkRefactoringSyntax.tree(member).any { (node, _) -> node is AnnotationExpression }) return null
            if (XdkRefactoringSyntax.tree(expression).any { (node, _) ->
                    node is LambdaExpression || node is TypeCompositionStatement
                }
            ) {
                return null
            }
            val expressionRange = range(expression)

            fun inside(range: SemanticModel.Range) = range.start >= expressionRange.start && range.end <= expressionRange.end
            // A copy declares nothing of its own, so each of its bindings names what the original's does.
            if (model.symbols.any { it.declarationSource == model.sourceName && it.declaration?.let(::inside) == true }) return null
            val from = XdkRename.offset(text, expressionRange.start) ?: return null
            val to = XdkRename.offset(text, expressionRange.end) ?: return null
            val value = text.substring(from, to)
            val errors = ErrorList()
            val tokens = ExecutionTrace.api("Lexer.lex(inline-member)") { Lexer(Source(value), errors).asSequence().toList() }
            // Ref-taking (&name) and super dispatch change meaning when copied; a bitwise & is an ordinary operator.
            if (errors.hasSeriousErrors() ||
                XdkRefactoringSyntax.tree(expression).any { (node, _) -> node is NameExpression && node.isSuppressDeref } ||
                tokens.any { it.valueText == "super" }
            ) {
                return null
            }
            // Copying a recursive invocation or escaped self-reference is not method expansion.
            if (model.occurrences.any { it.symbol == symbol.id && inside(it.range) }) return null
            return value
        }
    }
}
