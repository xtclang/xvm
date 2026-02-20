package org.xvm.lsp.index

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo.SymbolKind
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Unit tests for [WorkspaceIndex].
 *
 * Tests add/remove/search/findByName operations and the 4-tier fuzzy matching algorithm.
 * No native library needed -- pure Kotlin data structures.
 */
@DisplayName("WorkspaceIndex")
class WorkspaceIndexTest {
    private lateinit var index: WorkspaceIndex

    @BeforeEach
    fun setUp() {
        index = WorkspaceIndex()
    }

    private fun symbol(
        name: String,
        kind: SymbolKind = SymbolKind.CLASS,
        uri: String = "file:///test.x",
    ): IndexedSymbol =
        IndexedSymbol(
            name = name,
            qualifiedName = name,
            kind = kind,
            uri = uri,
            location = Location(uri, 0, 0, 0, name.length),
        )

    // ========================================================================
    // Add / Remove
    // ========================================================================

    @Nested
    @DisplayName("add/remove")
    inner class AddRemoveTests {
        @Test
        @DisplayName("should add symbols and update counts")
        fun shouldAddSymbols() {
            index.addSymbols("file:///a.x", listOf(symbol("Foo", uri = "file:///a.x"), symbol("Bar", uri = "file:///a.x")))

            assertThat(index.symbolCount).isEqualTo(2)
            assertThat(index.fileCount).isEqualTo(1)
        }

        @Test
        @DisplayName("should add symbols from multiple files")
        fun shouldAddFromMultipleFiles() {
            index.addSymbols("file:///a.x", listOf(symbol("Foo", uri = "file:///a.x")))
            index.addSymbols("file:///b.x", listOf(symbol("Bar", uri = "file:///b.x")))

            assertThat(index.symbolCount).isEqualTo(2)
            assertThat(index.fileCount).isEqualTo(2)
        }

        @Test
        @DisplayName("should replace symbols when re-indexing same URI")
        fun shouldReplaceOnReindex() {
            index.addSymbols("file:///a.x", listOf(symbol("Foo", uri = "file:///a.x")))
            index.addSymbols("file:///a.x", listOf(symbol("Bar", uri = "file:///a.x"), symbol("Baz", uri = "file:///a.x")))

            assertThat(index.symbolCount).isEqualTo(2)
            assertThat(index.findByName("Foo")).isEmpty()
            assertThat(index.findByName("Bar")).hasSize(1)
        }

        @Test
        @DisplayName("should remove symbols for URI")
        fun shouldRemoveForUri() {
            index.addSymbols("file:///a.x", listOf(symbol("Foo", uri = "file:///a.x")))
            index.addSymbols("file:///b.x", listOf(symbol("Bar", uri = "file:///b.x")))

            index.removeSymbolsForUri("file:///a.x")

            assertThat(index.symbolCount).isEqualTo(1)
            assertThat(index.fileCount).isEqualTo(1)
            assertThat(index.findByName("Foo")).isEmpty()
            assertThat(index.findByName("Bar")).hasSize(1)
        }

        @Test
        @DisplayName("should clear all data")
        fun shouldClear() {
            index.addSymbols("file:///a.x", listOf(symbol("Foo", uri = "file:///a.x")))
            index.clear()

            assertThat(index.symbolCount).isEqualTo(0)
            assertThat(index.fileCount).isEqualTo(0)
        }
    }

    // ========================================================================
    // findByName
    // ========================================================================

    @Nested
    @DisplayName("findByName()")
    inner class FindByNameTests {
        @Test
        @DisplayName("should find by exact name (case-insensitive)")
        fun shouldFindExact() {
            index.addSymbols("file:///a.x", listOf(symbol("HashMap", uri = "file:///a.x")))

            assertThat(index.findByName("HashMap")).hasSize(1)
            assertThat(index.findByName("hashmap")).hasSize(1)
            assertThat(index.findByName("HASHMAP")).hasSize(1)
        }

        @Test
        @DisplayName("should return empty for non-existent name")
        fun shouldReturnEmptyForMissing() {
            assertThat(index.findByName("DoesNotExist")).isEmpty()
        }

        @Test
        @DisplayName("should find same-named symbols from different files")
        fun shouldFindAcrossFiles() {
            index.addSymbols("file:///a.x", listOf(symbol("Person", uri = "file:///a.x")))
            index.addSymbols("file:///b.x", listOf(symbol("Person", uri = "file:///b.x")))

            assertThat(index.findByName("Person")).hasSize(2)
        }
    }

    // ========================================================================
    // search() -- 4-tier fuzzy matching
    // ========================================================================

    @Nested
    @DisplayName("search()")
    inner class SearchTests {
        @Test
        @DisplayName("should return empty for blank query")
        fun shouldReturnEmptyForBlank() {
            index.addSymbols("file:///a.x", listOf(symbol("Foo", uri = "file:///a.x")))
            assertThat(index.search("")).isEmpty()
            assertThat(index.search("   ")).isEmpty()
        }

        @Test
        @DisplayName("should match exactly (case-insensitive)")
        fun shouldMatchExact() {
            index.addSymbols("file:///a.x", listOf(symbol("HashMap", uri = "file:///a.x")))

            val results = index.search("hashmap")
            assertThat(results).hasSize(1)
            assertThat(results[0].name).isEqualTo("HashMap")
        }

        @Test
        @DisplayName("should match by prefix")
        fun shouldMatchPrefix() {
            index.addSymbols("file:///a.x", listOf(symbol("HashMap", uri = "file:///a.x"), symbol("HashSet", uri = "file:///a.x")))

            val results = index.search("hash")
            assertThat(results).hasSize(2)
        }

        @Test
        @DisplayName("should match by CamelCase initials")
        fun shouldMatchCamelCase() {
            index.addSymbols(
                "file:///a.x",
                listOf(
                    symbol("HashMap", uri = "file:///a.x"),
                    symbol("ClassCastException", uri = "file:///a.x"),
                ),
            )

            assertThat(index.search("HM")).extracting("name").containsExactly("HashMap")
            assertThat(index.search("CCE")).extracting("name").containsExactly("ClassCastException")
        }

        @Test
        @DisplayName("should match by subsequence")
        fun shouldMatchSubsequence() {
            index.addSymbols("file:///a.x", listOf(symbol("HashMap", uri = "file:///a.x")))

            val results = index.search("hmap")
            assertThat(results).hasSize(1)
            assertThat(results[0].name).isEqualTo("HashMap")
        }

        @Test
        @DisplayName("should rank exact > prefix > camelCase > subsequence")
        fun shouldRankByMatchQuality() {
            index.addSymbols(
                "file:///a.x",
                listOf(
                    symbol("map", uri = "file:///a.x"), // exact
                    symbol("mapper", uri = "file:///a.x"), // prefix
                    symbol("MyApp", uri = "file:///a.x"), // camelCase "MA" -> not match for "map"
                    symbol("myMapper", uri = "file:///a.x"), // subsequence
                ),
            )

            val results = index.search("map")
            assertThat(results).isNotEmpty
            assertThat(results[0].name).isEqualTo("map") // exact first
            assertThat(results[1].name).isEqualTo("mapper") // prefix second
        }

        @Test
        @DisplayName("should enforce result limit")
        fun shouldEnforceLimit() {
            val symbols = (1..200).map { symbol("Class$it", uri = "file:///a.x") }
            index.addSymbols("file:///a.x", symbols)

            val results = index.search("Class", limit = 10)
            assertThat(results).hasSize(10)
        }
    }

    // ========================================================================
    // CamelCase matching (static)
    // ========================================================================

    @Nested
    @DisplayName("matchesCamelCase()")
    inner class CamelCaseTests {
        @Test
        @DisplayName("HSM matches HashMap")
        fun hsmMatchesHashMap() {
            assertThat(WorkspaceIndex.matchesCamelCase("HM", "HashMap")).isTrue()
        }

        @Test
        @DisplayName("CCE matches ClassCastException")
        fun cceMatchesClassCastException() {
            assertThat(WorkspaceIndex.matchesCamelCase("CCE", "ClassCastException")).isTrue()
        }

        @Test
        @DisplayName("empty query does not match")
        fun emptyDoesNotMatch() {
            assertThat(WorkspaceIndex.matchesCamelCase("", "HashMap")).isFalse()
        }

        @Test
        @DisplayName("query longer than initials does not match")
        fun tooLongDoesNotMatch() {
            assertThat(WorkspaceIndex.matchesCamelCase("ABCDEF", "Ab")).isFalse()
        }
    }

    // ========================================================================
    // Subsequence matching (static)
    // ========================================================================

    @Nested
    @DisplayName("matchesSubsequence()")
    inner class SubsequenceTests {
        @Test
        @DisplayName("hmap matches hashmap")
        fun hmapMatchesHashmap() {
            assertThat(WorkspaceIndex.matchesSubsequence("hmap", "hashmap")).isTrue()
        }

        @Test
        @DisplayName("xyz does not match hashmap")
        fun xyzDoesNotMatch() {
            assertThat(WorkspaceIndex.matchesSubsequence("xyz", "hashmap")).isFalse()
        }

        @Test
        @DisplayName("empty query matches anything")
        fun emptyMatches() {
            assertThat(WorkspaceIndex.matchesSubsequence("", "anything")).isTrue()
        }
    }

    // ========================================================================
    // Thread safety
    // ========================================================================

    @Nested
    @DisplayName("thread safety")
    inner class ThreadSafetyTests {
        @Test
        @DisplayName("should handle concurrent reads during writes")
        fun shouldHandleConcurrentReadsWrites() {
            val executor = Executors.newFixedThreadPool(8)
            val latch = CountDownLatch(1)
            val iterations = 1000

            // Start reader threads
            val readFutures =
                (1..4).map {
                    executor.submit {
                        latch.await()
                        repeat(iterations) {
                            index.search("Test")
                            index.findByName("Test")
                        }
                    }
                }

            // Start writer threads
            val writeFutures =
                (1..4).map { threadId ->
                    executor.submit {
                        latch.await()
                        repeat(iterations) { i ->
                            val uri = "file:///thread${threadId}_file$i.x"
                            index.addSymbols(uri, listOf(symbol("Test$i", uri = uri)))
                            if (i % 2 == 0) {
                                index.removeSymbolsForUri(uri)
                            }
                        }
                    }
                }

            // Release all threads simultaneously
            latch.countDown()

            // Wait for completion -- no exceptions means thread-safe
            (readFutures + writeFutures).forEach { it.get() }
            executor.shutdown()
        }
    }
}
