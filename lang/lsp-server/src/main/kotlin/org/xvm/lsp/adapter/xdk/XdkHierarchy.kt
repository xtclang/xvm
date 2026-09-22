package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.TypeHierarchyItem
import org.xvm.lsp.model.SymbolInfo.SymbolKind

/** Queries use copied declaration edges, never compiler objects or a new TypeInfo build. */
internal class XdkHierarchy(
    private val views: Map<String, SemanticModel>,
) {
    private val model = views.values.firstOrNull()
    private val types = model?.typeDeclarations.orEmpty()
    private val children =
        types.values
            .flatMap { child ->
                child.parents.map { it.symbol to child.symbol }
            }.groupBy({ it.first }, { it.second })

    fun prepare(
        uri: String,
        line: Int,
        column: Int,
    ): List<TypeHierarchyItem> =
        views[uri]
            ?.symbolAt(line, column)
            ?.id
            ?.let(::item)
            ?.let(::listOf)
            .orEmpty()

    fun supertypes(item: TypeHierarchyItem): List<TypeHierarchyItem> =
        resolve(item)?.parents.orEmpty().mapNotNull { item(it.symbol, model?.type(it.type)?.displayName) }

    fun subtypes(item: TypeHierarchyItem): List<TypeHierarchyItem> =
        resolve(item)?.let { children[it.symbol] }.orEmpty().mapNotNull { item(it) }

    /** IDs are bound to this compilation; an item returned before an edit cannot identify a new type. */
    private fun resolve(item: TypeHierarchyItem): SemanticModel.TypeDeclaration? =
        types.values.firstOrNull {
            item.data == token(it.symbol) && item.uri == sourceUri(it.location.sourceName)
        }

    private fun item(
        id: SemanticModel.SymbolId,
        detail: String? = null,
    ): TypeHierarchyItem? {
        val declaration = types[id] ?: return null
        val symbol = model?.symbol(id) ?: return null
        val uri = sourceUri(declaration.location.sourceName) ?: return null
        return TypeHierarchyItem(
            symbol.name,
            when (declaration.category) {
                "interface" -> SymbolKind.INTERFACE
                "mixin", "annotation" -> SymbolKind.MIXIN
                "service" -> SymbolKind.SERVICE
                "const" -> SymbolKind.CONST
                "enum" -> SymbolKind.ENUM
                else -> SymbolKind.CLASS
            },
            uri,
            declaration.location.range.toRange(),
            (symbol.declaration ?: return null).toRange(),
            detail ?: symbol.type?.let { model.type(it)?.displayName },
            token(id),
        )
    }

    private fun sourceUri(name: String?): String? = views.entries.firstOrNull { it.value.sourceName == name }?.key

    private fun token(id: SemanticModel.SymbolId): String = "${id.snapshot}:${id.index}"

    private fun SemanticModel.Range.toRange(): Range = Range(Position(start.line, start.column), Position(end.line, end.column))
}
