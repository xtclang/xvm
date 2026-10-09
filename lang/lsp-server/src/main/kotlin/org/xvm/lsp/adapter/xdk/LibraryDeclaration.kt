package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.util.ExecutionTrace

internal data class LibraryDeclaration(
    val path: List<String>,
    val range: SemanticModel.Range,
    val firstLine: Int,
    val lastLine: Int,
)

/** Parse declaration spans shared by bundled and explicitly attached library sources. */
internal fun libraryDeclarations(
    text: String,
    path: String,
): List<LibraryDeclaration> {
    val errors = ErrorList()
    val root =
        try {
            ExecutionTrace.api("Parser.parseSource(library-source)") {
                Parser(Source(text), errors).parseSource()
            }
        } catch (_: CompilerException) {
            null
        }
    return buildList {
        fun visit(
            node: AstNode,
            parents: List<String>,
        ) {
            val token =
                when (node) {
                    is TypeCompositionStatement -> node.nameToken
                    is MethodDeclarationStatement -> node.nameToken
                    is PropertyDeclarationStatement -> node.nameToken
                    else -> null
                }
            val module = node is TypeCompositionStatement && node.category.id == Token.Id.MODULE
            val names = if (token == null || module) parents else parents + token.valueText
            if (token != null) {
                fun at(position: Long) =
                    SemanticModel.Position(
                        Source.calculateLine(position),
                        Source.calculateOffset(position),
                    )
                add(
                    LibraryDeclaration(
                        names,
                        SemanticModel.Range(at(token.startPosition), at(token.endPosition)),
                        Source.calculateLine(node.startPosition),
                        Source.calculateLine(node.endPosition),
                    ),
                )
            }
            node.childNodes().forEach { visit(it, names) }
        }
        if (root != null && !errors.hasSeriousErrors()) {
            visit(root, path.substringBeforeLast('/', "").split('/').drop(1))
        }
    }
}
