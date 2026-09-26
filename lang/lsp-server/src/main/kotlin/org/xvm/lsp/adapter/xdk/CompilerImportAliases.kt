package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.ImportStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.compiler.ast.StatementBlock

/** Copy explicit aliases using resolved identity and lexical import ownership; retain no syntax. */
internal fun compilerImportAliases(
    nodes: List<AstNode>,
    source: String?,
    occurrences: Map<SemanticModel.SourceLocation, SemanticModel.Occurrence>,
    constants: Map<Constant, SemanticModel.SymbolId>,
): List<SemanticModel.ImportAlias> {
    val imports = nodes.filterIsInstance<ImportStatement>().filter { it.source?.fileName == source && !it.isWildcard }
    return imports.filter { it.aliasName != it.qualifiedName.lastOrNull() && it.childNodes().none() }.mapNotNull { alias ->
        val token = alias.aliasToken ?: return@mapNotNull null
        val identity = alias.importedIdentity ?: return@mapNotNull null
        val target = constants[identity]

        fun range(
            start: Long,
            end: Long,
        ) = SemanticModel.Range(
            SemanticModel.Position(Source.calculateLine(start), Source.calculateOffset(start)),
            SemanticModel.Position(Source.calculateLine(end), Source.calculateOffset(end)),
        )
        val uses =
            nodes
                .filter { it.source?.fileName == source }
                .mapNotNull { node ->
                    val first =
                        when (node) {
                            is NameExpression -> node.nameToken.takeIf { node.leftExpression == null }
                            is NamedTypeExpression -> node.nameBindings.firstOrNull()?.name()
                            else -> null
                        } ?: return@mapNotNull null
                    if (first.valueText != alias.aliasName) return@mapNotNull null
                    val at = range(first.startPosition, first.endPosition)
                    val occurrence = occurrences[SemanticModel.SourceLocation(source, at)]
                    if (target == null || occurrence?.symbol != target) return@mapNotNull null
                    val owner =
                        generateSequence(node.parent) { it.parent }
                            .filterIsInstance<StatementBlock>()
                            .firstNotNullOfOrNull { block ->
                                imports.lastOrNull {
                                    it.parentBlock === block && it.aliasName == alias.aliasName &&
                                        it.startPosition < first.startPosition
                                }
                            }
                    at.takeIf { owner === alias }
                }.distinct()
        SemanticModel.ImportAlias(alias.aliasName, range(token.startPosition, token.endPosition), uses)
    }
}
