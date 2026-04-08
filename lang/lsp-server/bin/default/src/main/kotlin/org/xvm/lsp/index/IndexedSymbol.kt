package org.xvm.lsp.index

import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.SymbolInfo.SymbolKind

/**
 * Flat symbol entry optimized for cross-file lookup.
 *
 * Unlike [SymbolInfo] which is a tree (with children for document outline),
 * this is a flat entry suitable for indexing in maps and searching across files.
 *
 * @param name          simple name (e.g., "HashMap")
 * @param qualifiedName currently same as name; enhanced later for import resolution
 * @param kind          symbol kind (class, method, etc.)
 * @param uri           file URI where this symbol is declared
 * @param location      declaration range in the source file
 * @param containerName enclosing type/module name, if any
 */
data class IndexedSymbol(
    val name: String,
    val qualifiedName: String,
    val kind: SymbolKind,
    val uri: String,
    val location: Location,
    val containerName: String? = null,
) {
    /** Convert to a [SymbolInfo] for adapter return types. */
    fun toSymbolInfo(): SymbolInfo =
        SymbolInfo(
            name = name,
            qualifiedName = qualifiedName,
            kind = kind,
            location = location,
        )

    companion object {
        /** Create an [IndexedSymbol] from a [SymbolInfo] and its file URI. */
        fun fromSymbolInfo(
            symbol: SymbolInfo,
            uri: String,
            containerName: String? = null,
        ): IndexedSymbol =
            IndexedSymbol(
                name = symbol.name,
                qualifiedName = symbol.qualifiedName,
                kind = symbol.kind,
                uri = uri,
                location = symbol.location,
                containerName = containerName,
            )
    }
}
