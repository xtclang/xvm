package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component
import org.xvm.asm.ErrorList
import org.xvm.asm.ModuleStructure
import org.xvm.asm.PackageStructure
import org.xvm.asm.TypedefStructure
import org.xvm.asm.constants.ClassConstant
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.ImportStatement
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.util.ExecutionTrace

/**
 * A name written in a library source that names a type or a package, an import's or a type's: its names and their
 * ranges, the types and packages it is written in, outermost first, as [LibraryDeclaration.path] names them, and, for
 * an import, the name it imports as, null for a wildcard.
 */
internal data class LibraryReference(
    val names: List<String>,
    val ranges: List<SemanticModel.Range>,
    val scope: List<String>,
    val import: Boolean,
    val alias: String?,
)

/** The imports and type names written in a library source, as its parse gives them; none when it does not parse. */
internal fun libraryReferences(
    text: String,
    path: String,
): List<LibraryReference> {
    val errors = ErrorList()
    val root =
        try {
            ExecutionTrace.api("Parser.parseSource(library-references)") {
                Parser(Source(text), errors).parseSource()
            }
        } catch (_: CompilerException) {
            null
        }
    if (root == null || errors.hasSeriousErrors()) return emptyList()
    return buildList {
        fun add(
            tokens: List<Token>,
            scope: List<String>,
            import: Boolean,
            alias: String?,
        ) {
            fun at(position: Long) = SemanticModel.Position(Source.calculateLine(position), Source.calculateOffset(position))
            if (tokens.isNotEmpty()) {
                add(
                    LibraryReference(
                        tokens.map { it.valueText },
                        tokens.map { SemanticModel.Range(at(it.startPosition), at(it.endPosition)) },
                        scope,
                        import,
                        alias,
                    ),
                )
            }
        }

        fun visit(
            node: AstNode,
            scope: List<String>,
        ) {
            when (node) {
                is ImportStatement -> add(node.qualifiedNameTokens, scope, true, node.aliasName)

                // a module-qualified name is not resolved here
                is NamedTypeExpression -> if (node.module == null) add(node.nameBindings.map { it.name }, scope, false, null)

                else -> Unit
            }
            val inner =
                if (node is TypeCompositionStatement && node.category.id != Token.Id.MODULE) {
                    scope + node.nameToken.valueText
                } else {
                    scope
                }
            node.childNodes().forEach { visit(it, inner) }
        }
        visit(root, path.substringBeforeLast('/', "").split('/').drop(1))
    }
}

/**
 * What the first [count] names of [reference], written in a source of the library [module], name, as the library's
 * binary has them: the first name as a member of the types and packages the reference is written in, innermost first,
 * or as a name that one of [imports] written there imports, which an import's own names do not use; then as the
 * module's own name, or one of the language's implicit imports. Each name after it is a member of what the names
 * before it name. Null when a name does not name a type, a typedef or a package that way: a name that a type inherits,
 * for one, is not found.
 */
internal fun libraryTarget(
    module: ModuleStructure,
    imports: List<LibraryReference>,
    reference: LibraryReference,
    count: Int,
): Component? {
    val names = reference.names.take(count)
    val first = names.firstOrNull() ?: return null
    val scope = reference.scope
    val start =
        (scope.size downTo 0).firstNotNullOfOrNull { depth ->
            val level = scope.take(depth)
            val imported =
                if (reference.import) {
                    null
                } else {
                    imports
                        .filter { it.alias == first && it.scope == level }
                        .firstNotNullOfOrNull { libraryTarget(module, imports, it, it.names.size) }
                }
            imported ?: members(module, level)?.let { member(it, first) }
        } ?: module.takeIf { first == it.simpleName } ?: implicitImports[first]?.component
    return start?.let { members(it, names.drop(1)) }
}

/** The type or package that [names] name from [component], member by member. */
private fun members(
    component: Component,
    names: List<String>,
): Component? = names.fold<String, Component?>(component) { found, name -> found?.let { member(it, name) } }

/** The type, typedef or package named [name] in [component], or in the module that a package of it imports. */
private fun member(
    component: Component,
    name: String,
): Component? =
    ((component as? PackageStructure)?.importedModule?.name?.let(XdkLibraries::module) ?: component)
        .getChild(name)
        ?.takeIf { it is ClassStructure || it is TypedefStructure }

/** The classes the language imports implicitly, by the name they are imported as. */
private val implicitImports: Map<String, ClassConstant> by lazy {
    XdkLibraries
        .module("ecstasy.xtclang.org")
        .constantPool.constants
        .filterIsInstance<ClassConstant>()
        .mapNotNull { constant -> constant.implicitImportName?.let { it to constant } }
        .toMap()
}
