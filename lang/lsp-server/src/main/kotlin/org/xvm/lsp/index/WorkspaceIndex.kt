package org.xvm.lsp.index

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Workspace-wide symbol index for cross-file lookup.
 *
 * Provides O(1) name-based lookup and fuzzy search across all indexed files.
 * Thread-safe: concurrent reads are allowed during writes via [ReentrantReadWriteLock].
 *
 * ## Search algorithm (4-tier priority)
 * 1. Exact match (case-insensitive)
 * 2. Prefix match (case-insensitive)
 * 3. CamelCase match ("HSM" -> "HashMap")
 * 4. Subsequence match ("hmap" -> "HashMap")
 *
 * No trie for MVP -- with ~5-10K symbols, iterating all keys is sub-millisecond.
 */
@Suppress("LoggingSimilarMessage")
class WorkspaceIndex {
    private val logger = LoggerFactory.getLogger(WorkspaceIndex::class.java)

    /** Lowercase name -> symbols. O(1) name lookup. */
    private val byName = ConcurrentHashMap<String, MutableList<IndexedSymbol>>()

    /** File URI -> symbols in that file. O(1) per-file removal during re-indexing. */
    private val byUri = ConcurrentHashMap<String, List<IndexedSymbol>>()

    /** Qualified name -> symbol. For future import resolution. */
    private val byQualifiedName = ConcurrentHashMap<String, IndexedSymbol>()

    private val lock = ReentrantReadWriteLock()

    /** Total number of indexed symbols across all files. */
    val symbolCount: Int get() = lock.read { byUri.values.sumOf { it.size } }

    /** Number of indexed files. */
    val fileCount: Int get() = byUri.size

    /**
     * Add symbols for a file. Replaces any previously indexed symbols for that URI.
     */
    fun addSymbols(
        uri: String,
        symbols: List<IndexedSymbol>,
    ) {
        lock.write {
            // Remove old symbols for this URI first
            val oldCount = byUri[uri]?.size ?: 0
            removeSymbolsForUriInternal(uri)

            // Add new symbols
            byUri[uri] = symbols
            for (symbol in symbols) {
                val key = symbol.name.lowercase()
                byName.getOrPut(key) { mutableListOf() }.add(symbol)
                byQualifiedName[symbol.qualifiedName] = symbol
            }

            val verb = if (oldCount > 0) "replaced $oldCount with" else "added"
            logger.info("{} {} symbols for {}", verb, symbols.size, uri.substringAfterLast('/'))
        }
    }

    /**
     * Remove all symbols for a file URI.
     */
    fun removeSymbolsForUri(uri: String) {
        lock.write {
            val count = byUri[uri]?.size ?: 0
            removeSymbolsForUriInternal(uri)
            logger.info("removed {} symbols for {}", count, uri.substringAfterLast('/'))
        }
    }

    private fun removeSymbolsForUriInternal(uri: String) {
        val oldSymbols = byUri.remove(uri) ?: return
        for (symbol in oldSymbols) {
            val key = symbol.name.lowercase()
            byName[key]?.let { list ->
                list.removeAll { it.uri == uri }
                if (list.isEmpty()) byName.remove(key)
            }
            byQualifiedName.remove(symbol.qualifiedName)
        }
    }

    /**
     * Exact name lookup (case-insensitive). Returns all symbols with the given name.
     */
    fun findByName(name: String): List<IndexedSymbol> =
        lock.read {
            val results = byName[name.lowercase()]?.toList() ?: emptyList()
            logger.info("findByName '{}' -> {} results", name, results.size)
            results
        }

    /**
     * Fuzzy search across all indexed symbols.
     *
     * Results are ranked by match quality:
     * 1. Exact match (case-insensitive)
     * 2. Prefix match
     * 3. CamelCase match
     * 4. Subsequence match
     *
     * @param query the search query
     * @param limit maximum number of results (default 100)
     * @return matching symbols, best matches first
     */
    fun search(
        query: String,
        limit: Int = 100,
    ): List<IndexedSymbol> {
        if (query.isBlank()) {
            logger.info("search: blank query, returning empty")
            return emptyList()
        }

        val lowerQuery = query.lowercase()
        logger.info("search: query='{}', limit={}, index has {} names across {} files", query, limit, byName.size, byUri.size)

        return lock.read {
            val exact = mutableListOf<IndexedSymbol>()
            val prefix = mutableListOf<IndexedSymbol>()
            val camelCase = mutableListOf<IndexedSymbol>()
            val subsequence = mutableListOf<IndexedSymbol>()

            for ((key, symbols) in byName) {
                when {
                    key == lowerQuery -> exact.addAll(symbols)
                    key.startsWith(lowerQuery) -> prefix.addAll(symbols)
                    else -> {
                        // Check representative symbol for camelCase/subsequence (all have same name)
                        val name = symbols.firstOrNull()?.name ?: continue
                        if (matchesCamelCase(query, name)) {
                            camelCase.addAll(symbols)
                        } else if (matchesSubsequence(lowerQuery, key)) {
                            subsequence.addAll(symbols)
                        }
                    }
                }
            }

            val result = mutableListOf<IndexedSymbol>()
            result.addAll(exact)
            result.addAll(prefix)
            result.addAll(camelCase)
            result.addAll(subsequence)
            val limited = result.take(limit)

            logger.info(
                "search '{}' -> {} results (exact={}, prefix={}, camelCase={}, subsequence={}){}",
                query,
                limited.size,
                exact.size,
                prefix.size,
                camelCase.size,
                subsequence.size,
                if (result.size > limit) " [truncated from ${result.size}]" else "",
            )
            limited
        }
    }

    /**
     * Clear all indexed data.
     */
    fun clear() {
        lock.write {
            byName.clear()
            byUri.clear()
            byQualifiedName.clear()
        }
        logger.info("cleared")
    }

    companion object {
        /**
         * Check if query matches name via CamelCase initials.
         * E.g., "HSM" matches "HashMap", "CCE" matches "ClassCastException".
         */
        internal fun matchesCamelCase(
            query: String,
            name: String,
        ): Boolean {
            if (query.isEmpty()) return false
            val upperChars =
                buildList {
                    for (i in name.indices) {
                        if (name[i].isUpperCase() || i == 0) {
                            add(name[i])
                        }
                    }
                }
            if (upperChars.size < query.length) return false

            var qi = 0
            for (uc in upperChars) {
                if (qi < query.length && uc.equals(query[qi], ignoreCase = true)) {
                    qi++
                }
            }
            return qi == query.length
        }

        /**
         * Check if query is a subsequence of name (both lowercase).
         * E.g., "hmap" is a subsequence of "hashmap".
         */
        internal fun matchesSubsequence(
            query: String,
            name: String,
        ): Boolean {
            var qi = 0
            for (ni in name.indices) {
                if (qi < query.length && name[ni] == query[qi]) {
                    qi++
                }
            }
            return qi == query.length
        }
    }
}
