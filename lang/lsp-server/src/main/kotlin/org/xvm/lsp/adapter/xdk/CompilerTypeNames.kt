package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.TypedefConstant
import org.xvm.compiler.Lexer
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.ImportStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.NamedTypeExpression

/** Worker-local syntax and resolved identities; only detached [TypeName] values survive capture. */
internal data class CompilerTypeName(
    val location: SemanticModel.SourceLocation,
    val terminal: SemanticModel.Range,
    val target: IdentityConstant,
    val imported: Boolean,
)

internal data class TypeName(
    val location: SemanticModel.SourceLocation,
    val terminal: SemanticModel.Range,
    val target: ProofIdentity,
    val module: String,
    val path: List<String>,
    val imported: Boolean,
)

internal data class TypePath(
    val target: ProofIdentity,
    val module: String,
    val path: List<String>,
)

/** Capture written type names, including import clauses that are not expression children. */
internal fun compilerTypeNames(nodes: List<AstNode>): List<CompilerTypeName> {
    val importTokens =
        nodes
            .filterIsInstance<ImportStatement>()
            .mapNotNull { it.source }
            .distinctBy { it.fileName }
            .associate { source ->
                val lexer = Lexer(Source(source.toRawString()), ErrorList())
                source.fileName to generateSequence { if (lexer.hasNext()) lexer.next() else null }.toList()
            }
    return nodes
        .mapNotNull { node ->
            val source = node.source ?: return@mapNotNull null
            val target =
                when (node) {
                    is NamedTypeExpression -> node.nameBindings.lastOrNull()?.target()
                    is NameExpression -> node.resolvedTarget
                    is ImportStatement -> node.importedIdentity.takeUnless { node.isWildcard }
                    else -> null
                } as? IdentityConstant ?: return@mapNotNull null
            if (target !is ClassConstant && target !is TypedefConstant) return@mapNotNull null
            val tokens =
                when (node) {
                    is NamedTypeExpression -> {
                        node.nameBindings.map { it.name() }
                    }

                    is NameExpression -> {
                        val chain = generateSequence(node) { it.leftExpression as? NameExpression }.toList().asReversed()
                        if (chain.first().leftExpression != null) return@mapNotNull null
                        chain.map { it.nameToken }
                    }

                    is ImportStatement -> {
                        // The AST exposes import identity and spelling, but not its token list. Lex
                        // the source to recover positions; the compiler remains the identity authority.
                        val written =
                            importTokens
                                .getValue(source.fileName)
                                .asSequence()
                                .dropWhile { it.startPosition <= node.startPosition }
                                .takeWhile { it.startPosition < node.endPosition }
                                .filter { it.id == Token.Id.IDENTIFIER }
                                .take(node.qualifiedNameLength)
                                .toList()
                        if (written.map { it.valueText } != node.qualifiedName.toList()) return@mapNotNull null
                        written
                    }

                    else -> {
                        return@mapNotNull null
                    }
                }
            if (tokens.isEmpty()) return@mapNotNull null

            fun range(
                start: Long,
                end: Long,
            ) = SemanticModel.Range(
                SemanticModel.Position(Source.calculateLine(start), Source.calculateOffset(start)),
                SemanticModel.Position(Source.calculateLine(end), Source.calculateOffset(end)),
            )
            CompilerTypeName(
                SemanticModel.SourceLocation(source.fileName, range(tokens.first().startPosition, tokens.last().endPosition)),
                range(tokens.last().startPosition, tokens.last().endPosition),
                target,
                node is ImportStatement,
            )
        }.distinct()
}
