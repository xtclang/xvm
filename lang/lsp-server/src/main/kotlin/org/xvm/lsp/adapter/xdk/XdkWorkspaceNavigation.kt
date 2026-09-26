package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.CallHierarchyIncomingCall
import org.xvm.lsp.adapter.CallHierarchyItem
import org.xvm.lsp.adapter.CallHierarchyOutgoingCall
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.TypeHierarchyItem
import org.xvm.lsp.model.Location

/** Detached graph views. Hierarchy handles are bound to the exact source/configuration digest. */
internal class XdkWorkspaceNavigation(
    private val views: Map<String, SemanticModel>,
    private val revision: String,
) {
    private val hierarchy = XdkHierarchy(views)
    private val calls = XdkCalls(views)
    private val sourceUris = views.keys.mapNotNull { uri -> XdkSources.file(uri)?.let { it to uri } }.toMap()

    private fun sourceUri(uri: String): String = if (uri in views) uri else sourceUris[XdkSources.file(uri)] ?: uri

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
                views.entries.firstOrNull { it.value.sourceName == target.sourceName }?.key?.let { uri ->
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
