package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Lexer
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AnnotationExpression
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.util.ExecutionTrace

/** Private source members have bounded consumers; public APIs and initialization effects do not. */
internal object XdkSafeDelete {
    data class Candidate(
        val name: String,
        val identity: ProofIdentity,
        val edit: XdkRename.Edit,
        val removed: Set<SemanticModel.SourceLocation>,
    )

    fun candidate(
        text: String,
        selection: Range,
        model: SemanticModel,
        facts: CompilerRenameFacts,
    ): Candidate? {
        val source = model.sourceName ?: return null
        val symbol = model.symbolAt(selection.start.line, selection.start.column) ?: return null
        if (symbol.declarationSource != source) return null
        val declaration = symbol.declaration ?: return null
        val identity = facts.constants[symbol.id] as? ProofIdentity.Source ?: return null
        val errors = ErrorList()
        val root =
            try {
                ExecutionTrace.api("Parser.parseSource(safe-delete)") { Parser(Source(text), errors).parseSource() }
            } catch (_: CompilerException) {
                return null
            }
        if (errors.hasSeriousErrors()) return null

        fun tree(
            node: AstNode,
            parent: AstNode? = null,
        ): Sequence<Pair<AstNode, AstNode?>> = sequenceOf(node to parent) + node.childNodes().asSequence().flatMap { tree(it, node) }
        val parents = tree(root).toMap()

        fun at(value: Long) = SemanticModel.Position(Source.calculateLine(value), Source.calculateOffset(value))
        val member =
            when (symbol.kind) {
                SemanticModel.SymbolKind.METHOD -> {
                    parents.keys.filterIsInstance<MethodDeclarationStatement>().singleOrNull {
                        it.nameToken?.startPosition?.let(::at) == declaration.start && it.defaultAccess == Access.PRIVATE &&
                            !it.isConstructor && !it.isConstructorFinally && it.childNodes().any { child -> child is StatementBlock }
                    }
                }

                SemanticModel.SymbolKind.PROPERTY -> {
                    if (SemanticModel.SourceLocation(source, declaration) !in facts.constantProperties) return null
                    parents.keys.filterIsInstance<PropertyDeclarationStatement>().singleOrNull {
                        at(it.nameToken.startPosition) == declaration.start && it.defaultAccess == Access.PRIVATE && it.isStatic &&
                            it.childNodes().none { child -> child is StatementBlock }
                    }
                }

                else -> {
                    null
                }
            } ?: return null
        if (parents[parents[member]] !is TypeCompositionStatement) return null
        if (tree(member).any { (node, _) -> node is AnnotationExpression }) return null
        val start = XdkRename.offset(text, at(member.startPosition)) ?: return null
        val end = XdkRename.offset(text, at(member.endPosition)) ?: return null
        val terminator = Regex("[ \t]*;").find(text, end)?.takeIf { it.range.first == end }
        val syntaxEnd = terminator?.range?.last?.plus(1) ?: end
        val tokens =
            ExecutionTrace.api("Lexer.lex(safe-delete)") {
                Lexer(Source(text.substring(start, syntaxEnd)), errors).asSequence().toList()
            }
        if (errors.hasSeriousErrors() || tokens.any { it.id in setOf(Token.Id.EOL_COMMENT, Token.Id.ENC_COMMENT) }) return null
        val members = SemanticModel.Range(at(member.startPosition), at(member.endPosition))

        fun inside(
            path: String?,
            range: SemanticModel.Range,
        ) = path == source && range.start >= members.start && range.end <= members.end
        // Include every configured source view, not just the active file's reference list.
        if (facts.models.any { view ->
                view.occurrences.any { occurrence ->
                    occurrence.symbol?.let(facts.constants::get) == identity && !inside(view.sourceName, occurrence.range)
                } || view.calls.any { call -> facts.constants[call.method] == identity && !inside(view.sourceName, call.callee) }
            }
        ) {
            return null
        }
        val removed =
            facts.models
                .filter { it.sourceName == source }
                .flatMap { view ->
                    view.occurrences.map { it.range } + view.calls.map { it.callee }
                }.filter { inside(source, it) }
                .mapTo(linkedSetOf()) { SemanticModel.SourceLocation(source, it) }
        val lineStart = maxOf(text.lastIndexOf('\n', start - 1), text.lastIndexOf('\r', start - 1)) + 1
        val lineEnd = Regex("[ \t]*(?:\r\n|\r|\n)").find(text, syntaxEnd)?.takeIf { it.range.first == syntaxEnd }
        val edit =
            if (text.substring(lineStart, start).isBlank() && lineEnd != null) {
                XdkRename.Edit(lineStart, lineEnd.range.last + 1, "")
            } else {
                XdkRename.Edit(start, syntaxEnd, "")
            }
        return Candidate(symbol.name, identity, edit, removed)
    }
}
