package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.Parameter
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.util.ExecutionTrace

/**
 * Declaration located in attached library source, with the source range used for navigation and its doc comment, which
 * a compiled module does not keep, and of a method the range of each of its parameters' names, by name, which Go to
 * Definition of a named argument goes to; and what it declares, as Go to Symbol lists it.
 */
internal data class LibraryDeclaration(
    val path: List<String>,
    val range: SemanticModel.Range,
    val firstLine: Int,
    val lastLine: Int,
    val documentation: String?,
    val parameters: Map<String, SemanticModel.Range>,
    val kind: SymbolInfo.SymbolKind,
) {
    /** The declaration's own name; a module's declaration is under none, as its path is its sources' folders. */
    val name: String
        get() = path.lastOrNull().orEmpty()
}

/** The declarations of [declarations] that Go to Symbol finds by name: not the modules', which have none of their own. */
internal fun symbolic(declarations: List<LibraryDeclaration>): List<LibraryDeclaration> =
    declarations.filter { it.kind != SymbolInfo.SymbolKind.MODULE && it.path.isNotEmpty() }

/** Parse declaration spans and doc comments shared by bundled and explicitly attached library sources. */
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
            // the name, and the doc comment as the compiler formats a project's own
            val (token, documentation) =
                when (node) {
                    is TypeCompositionStatement -> node.nameToken to node.documentation
                    is MethodDeclarationStatement -> node.nameToken to node.documentation
                    is PropertyDeclarationStatement -> node.nameToken to node.documentation
                    else -> null to null
                }
            val module = node is TypeCompositionStatement && node.category.id == Token.Id.MODULE
            val names = if (token == null || module) parents else parents + token.valueText
            if (token != null) {
                fun at(position: Long) =
                    SemanticModel.Position(
                        Source.calculateLine(position),
                        Source.calculateOffset(position),
                    )
                // a method's parameters, its type parameters among them, as its compiled structure has them, but not the
                // names of its return values, which no argument names
                val parameters =
                    if (node is MethodDeclarationStatement) {
                        val returns = node.returns.orEmpty()
                        node
                            .childNodes()
                            .filterIsInstance<Parameter>()
                            .filter { parameter -> returns.none { it === parameter } }
                            .mapNotNull { parameter ->
                                parameter.nameToken?.let { parameter.name to SemanticModel.Range(at(it.startPosition), at(it.endPosition)) }
                            }.toMap()
                    } else {
                        emptyMap()
                    }
                add(
                    LibraryDeclaration(
                        names,
                        SemanticModel.Range(at(token.startPosition), at(token.endPosition)),
                        Source.calculateLine(node.startPosition),
                        Source.calculateLine(node.endPosition),
                        documentation?.trim()?.takeIf { it.isNotEmpty() },
                        parameters,
                        kindOf(node),
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

/** What [node], a declaration that [libraryDeclarations] takes, declares, as Go to Symbol lists it. */
private fun kindOf(node: AstNode): SymbolInfo.SymbolKind =
    when (node) {
        is TypeCompositionStatement -> XdkSymbols.kindOf(node)
        is MethodDeclarationStatement -> SymbolInfo.SymbolKind.METHOD
        else -> SymbolInfo.SymbolKind.PROPERTY
    }
