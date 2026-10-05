package org.xvm.lsp.adapter.xdk

import org.xvm.asm.PackageStructure
import org.xvm.asm.constants.IdentityConstant
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.CompositionNode
import org.xvm.compiler.ast.ImportStatement
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.compiler.ast.TypeCompositionStatement

/** Copy resolved import destinations on the compiler worker; no path is guessed from spelling. */
internal fun compilerSourceLinks(
    nodes: List<AstNode>,
    declaration: (IdentityConstant) -> SemanticModel.SourceLocation?,
): Map<String?, List<SemanticModel.SourceLink>> =
    nodes
        .mapNotNull { node ->
            val imported =
                when (node) {
                    is ImportStatement -> {
                        // A wildcard resolves to its container, not to every exported child.
                        // Only use compiler-resolved identities; unsupported import syntax does
                        // not establish a destination merely by spelling a qualified name.
                        val names = node.qualifiedNameTokens
                        val first = node.aliasToken ?: names.firstOrNull() ?: return@mapNotNull null
                        val last = node.aliasToken ?: names.lastOrNull() ?: return@mapNotNull null
                        Triple(node.importedIdentity, first.startPosition, last.endPosition)
                    }

                    is NamedTypeExpression -> {
                        val composition = node.parent as? CompositionNode.Import ?: return@mapNotNull null
                        val owner = (composition.parent as? TypeCompositionStatement)?.component as? PackageStructure
                        Triple(owner?.importedModule?.identityConstant, node.startPosition, node.endPosition)
                    }

                    else -> {
                        return@mapNotNull null
                    }
                }
            val target = imported.first?.let(declaration)?.takeIf { it.sourceName != null } ?: return@mapNotNull null

            fun position(at: Long) = SemanticModel.Position(Source.calculateLine(at), Source.calculateOffset(at))
            node.source?.fileName to
                SemanticModel.SourceLink(SemanticModel.Range(position(imported.second), position(imported.third)), target)
        }.groupBy({ it.first }, { it.second })
