package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.CallHierarchyIncomingCall
import org.xvm.lsp.adapter.CallHierarchyItem
import org.xvm.lsp.adapter.CallHierarchyOutgoingCall
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.model.SymbolInfo.SymbolKind

/** Static selected calls in one successful module snapshot. No runtime dispatch or name matching. */
internal class XdkCalls(
    views: Map<String, SemanticModel>,
) {
    private val views = views.filterValues { it.status == SemanticModel.Status.COMPLETE }
    private val model = this.views.values.firstOrNull()
    private val callables = model?.callables.orEmpty()
    private val calls =
        this.views.values
            .flatMap { it.calls }
            .filter { it.caller in callables && it.method in callables }

    fun prepare(
        uri: String,
        line: Int,
        column: Int,
    ): List<CallHierarchyItem> {
        val view = views[uri] ?: return emptyList()
        view.symbolAt(line, column)?.let { return listOfNotNull(item(it.id)) }
        val position = SemanticModel.Position(line, column)
        val enclosing =
            callables.values
                .filter { it.location.sourceName == view.sourceName && position in it.location.range }
                .maxByOrNull { it.location.range.start }
        return listOfNotNull(enclosing?.let { item(it.symbol) })
    }

    fun incoming(item: CallHierarchyItem): List<CallHierarchyIncomingCall> {
        val id = resolve(item) ?: return emptyList()
        return calls.filter { it.method == id }.groupBy { it.caller }.mapNotNull { (caller, calls) ->
            caller?.let(::item)?.let { CallHierarchyIncomingCall(it, calls.map { it.callee.toRange() }.distinct()) }
        }
    }

    fun outgoing(item: CallHierarchyItem): List<CallHierarchyOutgoingCall> {
        val id = resolve(item) ?: return emptyList()
        return calls.filter { it.caller == id }.groupBy { it.method }.mapNotNull { (target, calls) ->
            item(target)?.let { CallHierarchyOutgoingCall(it, calls.map { it.callee.toRange() }.distinct()) }
        }
    }

    private fun resolve(item: CallHierarchyItem): SemanticModel.SymbolId? =
        callables.keys.firstOrNull { item.data == token(it) && item.uri == item(it)?.uri }

    private fun item(id: SemanticModel.SymbolId): CallHierarchyItem? {
        val callable = callables[id] ?: return null
        val symbol = model?.symbol(id) ?: return null
        val uri = views.entries.firstOrNull { it.value.sourceName == callable.location.sourceName }?.key ?: return null
        return CallHierarchyItem(
            symbol.name,
            SymbolKind.METHOD,
            uri,
            callable.location.range.toRange(),
            callable.selection.toRange(),
            data = token(id),
        )
    }

    private fun token(id: SemanticModel.SymbolId): String = "${id.snapshot}:${id.index}"

    private fun SemanticModel.Range.toRange(): Range = Range(Position(start.line, start.column), Position(end.line, end.column))
}
