package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.CallHierarchyIncomingCall
import org.xvm.lsp.adapter.CallHierarchyItem
import org.xvm.lsp.adapter.CallHierarchyOutgoingCall
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.TypeHierarchyItem
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo

/** Detached graph views. Hierarchy handles are bound to the exact source/configuration digest. */
internal class XdkWorkspaceNavigation(
    private val views: Map<String, SemanticModel>,
    private val revision: String,
    private val complete: Boolean,
) {
    private val hierarchy = XdkHierarchy(views)
    private val calls = XdkCalls(views)
    private val sourceUris = views.keys.mapNotNull { uri -> XdkSources.file(uri)?.let { it to uri } }.toMap()

    private fun sourceUri(uri: String): String = if (uri in views) uri else sourceUris[XdkSources.file(uri)] ?: uri

    fun symbols(query: String): List<SymbolInfo> =
        views
            .flatMap { (uri, model) ->
                model.symbols
                    .filter { symbol ->
                        symbol.declarationSource == model.sourceName && symbol.declaration != null &&
                            symbol.kind in
                            setOf(
                                SemanticModel.SymbolKind.TYPE,
                                SemanticModel.SymbolKind.METHOD,
                                SemanticModel.SymbolKind.PROPERTY,
                                SemanticModel.SymbolKind.MODULE,
                                SemanticModel.SymbolKind.PACKAGE,
                            ) &&
                            (query.isBlank() || symbol.name.contains(query, ignoreCase = true))
                    }.map { symbol ->
                        val range = requireNotNull(symbol.declaration)
                        SymbolInfo.of(
                            symbol.name,
                            when (symbol.kind) {
                                SemanticModel.SymbolKind.METHOD -> SymbolInfo.SymbolKind.METHOD
                                SemanticModel.SymbolKind.PROPERTY -> SymbolInfo.SymbolKind.PROPERTY
                                SemanticModel.SymbolKind.MODULE -> SymbolInfo.SymbolKind.MODULE
                                SemanticModel.SymbolKind.PACKAGE -> SymbolInfo.SymbolKind.PACKAGE
                                else -> SymbolInfo.SymbolKind.CLASS
                            },
                            Location(uri, range.start.line, range.start.column, range.end.line, range.end.column),
                        )
                    }
            }.distinctBy { it.location }
            .sortedBy { it.name }

    /** A reference list promises graph closure; partial navigation must not weaken rename callers. */
    fun references(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> {
        if (!complete) return emptyList()
        val target = views[sourceUri(uri)]?.symbolAt(line, column)?.id ?: return emptyList()
        return views
            .flatMap { (source, model) ->
                model.occurrences
                    .filter { it.symbol == target && (includeDeclaration || it.role != SemanticModel.Role.DECLARATION) }
                    .map { Location(source, it.range.start.line, it.range.start.column, it.range.end.line, it.range.end.column) }
            }.distinct()
            .sortedWith(compareBy(Location::uri, Location::startLine, Location::startColumn))
    }

    fun definition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? = views[sourceUri(uri)]?.definitionLocationAt(line, column)?.let { locations(listOf(it)).singleOrNull() }

    fun typeDefinitions(
        uri: String,
        line: Int,
        column: Int,
    ): List<Location> = locations(views[sourceUri(uri)]?.typeDefinitionLocationsAt(line, column).orEmpty())

    fun implementations(
        uri: String,
        line: Int,
        column: Int,
    ): List<Location> = locations(views[sourceUri(uri)]?.implementationLocationsAt(line, column).orEmpty())

    fun prepareTypes(
        uri: String,
        line: Int,
        column: Int,
    ): List<TypeHierarchyItem> = hierarchy.prepare(sourceUri(uri), line, column).map { it.copy(data = revision) }

    fun parents(item: TypeHierarchyItem): List<TypeHierarchyItem> =
        current(item)?.let(hierarchy::supertypes).orEmpty().map { it.copy(data = revision) }

    fun children(item: TypeHierarchyItem): List<TypeHierarchyItem> =
        current(item)?.let(hierarchy::subtypes).orEmpty().map { it.copy(data = revision) }

    fun prepareCalls(
        uri: String,
        line: Int,
        column: Int,
    ): List<CallHierarchyItem> = calls.prepare(sourceUri(uri), line, column).map { it.copy(data = revision) }

    fun incoming(item: CallHierarchyItem): List<CallHierarchyIncomingCall> =
        current(item)?.let(calls::incoming).orEmpty().map { it.copy(from = it.from.copy(data = revision)) }

    fun outgoing(item: CallHierarchyItem): List<CallHierarchyOutgoingCall> =
        current(item)?.let(calls::outgoing).orEmpty().map { it.copy(to = it.to.copy(data = revision)) }

    private fun current(item: TypeHierarchyItem): TypeHierarchyItem? =
        item.takeIf { it.data == revision }?.let {
            hierarchy
                .prepare(it.uri, it.selectionRange.start.line, it.selectionRange.start.column)
                .singleOrNull { candidate -> candidate.range == item.range && candidate.selectionRange == item.selectionRange }
        }

    private fun current(item: CallHierarchyItem): CallHierarchyItem? =
        item.takeIf { it.data == revision }?.let {
            calls
                .prepare(it.uri, it.selectionRange.start.line, it.selectionRange.start.column)
                .singleOrNull { candidate -> candidate.range == item.range && candidate.selectionRange == item.selectionRange }
        }

    private fun locations(locations: List<SemanticModel.SourceLocation>): List<Location> =
        locations
            .mapNotNull { target ->
                (
                    views.entries.firstOrNull { it.value.sourceName == target.sourceName }?.key
                        ?: XdkLibrarySources.sourceUri(target.sourceName)
                )?.let { uri ->
                    val range =
                        Range(
                            Position(target.range.start.line, target.range.start.column),
                            Position(target.range.end.line, target.range.end.column),
                        )
                    Location(uri, range.start.line, range.start.column, range.end.line, range.end.column)
                }
            }.distinct()
            .sortedWith(compareBy(Location::uri, Location::startLine, Location::startColumn))
}
