package org.xvm.lsp.index

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.model.SymbolInfo.SymbolKind
import org.xvm.lsp.treesitter.XtcParser

/**
 * Integration tests for [WorkspaceIndexer].
 *
 * Requires the tree-sitter native library. Tests are skipped (not failed) when the native library
 * is unavailable.
 */
@DisplayName("WorkspaceIndexer")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspaceIndexerTest {
    private var parser: XtcParser? = null

    @BeforeAll
    fun setUpParser() {
        parser = runCatching { XtcParser() }.getOrNull()
    }

    @BeforeEach
    fun assumeAvailable() {
        Assumptions.assumeTrue(parser != null, "Tree-sitter native library not available")
    }

    @AfterAll
    fun tearDown() {
        parser?.close()
    }

    @Test
    fun `an open buffer wins over an in flight disk read and survives disk deletion`(
        @TempDir directory: Path
    ) {
        val path = directory.resolve("Owner.x")
        // Java File URIs use file:/ while LSP/Path URIs use file:///; ownership is identical.
        val uri = path.toFile().toURI().toString()
        Files.writeString(path, "module Owner { class Disk {} }")
        val read = CompletableFuture<Void>()
        val release = CompletableFuture<Void>()
        val index = WorkspaceIndex()
        WorkspaceIndexer(index, requireNotNull(parser).getLanguage()) { file ->
                (if (Files.isRegularFile(file)) Files.readString(file) else null).also {
                    read.complete(null)
                    release.get(5, SECONDS)
                }
            }
            .use { indexer ->
                val scan = indexer.scanWorkspace(listOf(directory.toString()))
                try {
                    read.get(5, SECONDS)
                    indexer.reindexFile(uri, "module Owner { class Buffer {} }")
                } finally {
                    release.complete(null)
                }
                scan.get(5, SECONDS)
                assertThat(index.findByName("Disk")).isEmpty()
                assertThat(index.findByName("Buffer")).hasSize(1)
                Files.delete(path)
                indexer.refreshFile(uri)
                assertThat(index.findByName("Buffer")).hasSize(1)
                indexer.closeDocument(uri)
                assertThat(index.findByName("Buffer")).isEmpty()
            }
    }

    @Test
    fun `closing restores disk and reopening invalidates an older close read`(
        @TempDir directory: Path
    ) {
        val path = directory.resolve("Owner.x")
        val uri = path.toUri().toString()
        Files.writeString(path, "module Owner { class Disk {} }")
        val block = AtomicBoolean(false)
        val read = CompletableFuture<Void>()
        val release = CompletableFuture<Void>()
        val index = WorkspaceIndex()
        WorkspaceIndexer(index, requireNotNull(parser).getLanguage()) { file ->
                Files.readString(file).also {
                    if (block.get()) {
                        read.complete(null)
                        release.get(5, SECONDS)
                    }
                }
            }
            .use { indexer ->
                indexer.reindexFile(uri, "module Owner { class FirstBuffer {} }")
                indexer.closeDocument(uri)
                assertThat(index.findByName("Disk")).hasSize(1)
                assertThat(index.findByName("FirstBuffer")).isEmpty()
                indexer.reindexFile(uri, "module Owner { class FirstBuffer {} }")
                block.set(true)
                val closing = CompletableFuture.runAsync { indexer.closeDocument(uri) }
                try {
                    read.get(5, SECONDS)
                    indexer.reindexFile(uri, "module Owner { class Reopened {} }")
                } finally {
                    release.complete(null)
                }
                closing.get(5, SECONDS)
                assertThat(index.findByName("Disk")).isEmpty()
                assertThat(index.findByName("Reopened")).hasSize(1)
            }
    }

    @Test
    fun `buffers opened before the initial scan are indexed and closing observes disk deletion`(
        @TempDir directory: Path
    ) {
        val path = directory.resolve("Owner.x")
        val uri = path.toUri().toString()
        Files.writeString(path, "module Owner { class Disk {} }")
        val index = WorkspaceIndex()
        WorkspaceIndexer(index, requireNotNull(parser).getLanguage()).use { indexer ->
            indexer.reindexFile(uri, "module Owner { class Buffer {} }")
            indexer.scanWorkspace(listOf(directory.toString())).get(5, SECONDS)
            assertThat(index.findByName("Buffer")).hasSize(1)
            assertThat(index.findByName("Disk")).isEmpty()
            indexer.closeDocument(uri)
            Files.delete(path)
            indexer.refreshFile(uri)
            assertThat(index.symbolCount).isZero()
        }
    }

    @Test
    fun `concurrent workspace scans do not starve their own bounded executor`(
        @TempDir directory: Path
    ) {
        Files.writeString(directory.resolve("Concurrent.x"), "module Concurrent { class Value {} }")
        val index = WorkspaceIndex()
        WorkspaceIndexer(index, requireNotNull(parser).getLanguage()).use { indexer ->
            val scans = List(12) { indexer.scanWorkspace(listOf(directory.toString())) }
            CompletableFuture.allOf(*scans.toTypedArray()).get(15, SECONDS)
            assertThat(index.findByName("Value")).hasSize(1)
            indexer.close()
            indexer.reindexFile(
                directory.resolve("Concurrent.x").toUri().toString(),
                "module Replaced {}",
            )
            assertThat(index.findByName("Value")).hasSize(1)
            assertThat(index.findByName("Replaced")).isEmpty()
        }
    }

    @Test
    fun `concurrent native parser requests retain independent source trees`() {
        XtcParser(requireNotNull(parser).getLanguage()).use { shared ->
            val requests =
                List(40) { number ->
                    CompletableFuture.runAsync {
                        val source = "module Parallel$number { Int value = $number; }"
                        shared.parse(source).use { tree ->
                            assertThat(tree.root.text).isEqualTo(source)
                        }
                    }
                }
            CompletableFuture.allOf(*requests.toTypedArray()).get(15, SECONDS)
        }
    }

    // ========================================================================
    // Scan workspace
    // ========================================================================

    @Nested
    @DisplayName("scanWorkspace()")
    inner class ScanTests {
        @Test
        @DisplayName("should index all .x files in workspace")
        fun shouldIndexXtcFiles(@TempDir tempDir: Path) {
            // Create test .x files
            Files.writeString(
                tempDir.resolve("Foo.x"),
                """
                module myapp {
                    class Foo {
                    }
                }
                """
                    .trimIndent(),
            )
            Files.writeString(
                tempDir.resolve("Bar.x"),
                """
                module myapp {
                    class Bar {
                        String getName() {
                            return "bar";
                        }
                    }
                }
                """
                    .trimIndent(),
            )

            // Create non-.x file that should be ignored
            Files.writeString(tempDir.resolve("readme.txt"), "not XTC code")

            val index = WorkspaceIndex()
            val indexer = WorkspaceIndexer(index, parser!!.getLanguage())

            indexer.scanWorkspace(listOf(tempDir.toString())).join()

            assertThat(index.fileCount).isEqualTo(2)
            assertThat(index.symbolCount).isGreaterThanOrEqualTo(2) // at least Foo and Bar
            assertThat(index.findByName("Foo")).isNotEmpty
            assertThat(index.findByName("Bar")).isNotEmpty

            indexer.close()
        }

        /**
         * Issue #459: cmd-click on `JsonArray` (a typedef in json.x, used from JsonArrayBuilder.x)
         * found nothing because typedef declarations never reached the workspace index. Shorthand
         * constructor parameters (`const Point(Int x, Int y)`) declare properties and must be
         * indexed too -- `structure.y` navigated to an unrelated file without this.
         */
        @Test
        @DisplayName("should index typedefs and shorthand constructor properties")
        fun shouldIndexTypedefsAndShorthandProperties(@TempDir tempDir: Path) {
            Files.writeString(
                tempDir.resolve("json.x"),
                """
                module json {
                    typedef Doc as JsonArray;
                    const Point(Int x, Int y);
                }
                """
                    .trimIndent(),
            )

            val index = WorkspaceIndex()
            val indexer = WorkspaceIndexer(index, parser!!.getLanguage())

            indexer.scanWorkspace(listOf(tempDir.toString())).join()

            assertThat(index.findByName("JsonArray")).isNotEmpty.allMatch {
                it.kind == SymbolKind.CLASS
            }
            assertThat(index.findByName("x")).isNotEmpty
            assertThat(index.findByName("y")).isNotEmpty.allMatch { it.kind == SymbolKind.PROPERTY }

            indexer.close()
        }

        @Test
        @DisplayName("should index nested directories")
        fun shouldIndexNestedDirs(@TempDir tempDir: Path) {
            val subDir = tempDir.resolve("src/main")
            Files.createDirectories(subDir)
            Files.writeString(
                subDir.resolve("Nested.x"),
                """
                module myapp {
                    class Nested {
                    }
                }
                """
                    .trimIndent(),
            )

            val index = WorkspaceIndex()
            val indexer = WorkspaceIndexer(index, parser!!.getLanguage())

            indexer.scanWorkspace(listOf(tempDir.toString())).join()

            assertThat(index.findByName("Nested")).isNotEmpty

            indexer.close()
        }

        @Test
        @DisplayName("should handle empty workspace")
        fun shouldHandleEmptyWorkspace(@TempDir tempDir: Path) {
            val index = WorkspaceIndex()
            val indexer = WorkspaceIndexer(index, parser!!.getLanguage())

            indexer.scanWorkspace(listOf(tempDir.toString())).join()

            assertThat(index.symbolCount).isEqualTo(0)
            assertThat(index.fileCount).isEqualTo(0)

            indexer.close()
        }
    }

    // ========================================================================
    // Reindex / Remove
    // ========================================================================

    @Nested
    @DisplayName("reindex/remove")
    inner class ReindexTests {
        @Test
        @DisplayName("should reindex a file with updated content")
        fun shouldReindexFile() {
            val index = WorkspaceIndex()
            val indexer = WorkspaceIndexer(index, parser!!.getLanguage())
            val uri = "file:///test.x"

            // Index initial content
            indexer.reindexFile(
                uri,
                """
                module myapp {
                    class OldName {
                    }
                }
                """
                    .trimIndent(),
            )
            assertThat(index.findByName("OldName")).isNotEmpty

            // Reindex with new content
            indexer.reindexFile(
                uri,
                """
                module myapp {
                    class NewName {
                    }
                }
                """
                    .trimIndent(),
            )
            assertThat(index.findByName("OldName")).isEmpty()
            assertThat(index.findByName("NewName")).isNotEmpty

            indexer.close()
        }

        @Test
        @DisplayName("should remove file from index")
        fun shouldRemoveFile() {
            val index = WorkspaceIndex()
            val indexer = WorkspaceIndexer(index, parser!!.getLanguage())
            val uri = "file:///test.x"

            indexer.reindexFile(
                uri,
                """
                module myapp {
                    class ToBeDeleted {
                    }
                }
                """
                    .trimIndent(),
            )
            assertThat(index.findByName("ToBeDeleted")).isNotEmpty

            indexer.removeFile(uri)
            assertThat(index.findByName("ToBeDeleted")).isEmpty()
            assertThat(index.fileCount).isEqualTo(0)

            indexer.close()
        }
    }

    // ========================================================================
    // Symbol extraction
    // ========================================================================

    @Nested
    @DisplayName("symbol extraction")
    inner class SymbolExtractionTests {
        @Test
        @DisplayName("should extract multiple symbol kinds")
        fun shouldExtractMultipleKinds() {
            val index = WorkspaceIndex()
            val indexer = WorkspaceIndexer(index, parser!!.getLanguage())

            indexer.reindexFile(
                "file:///test.x",
                """
                module myapp {
                    class Person {
                        String name;
                        String getName() {
                            return name;
                        }
                    }
                    interface Runnable {
                    }
                }
                """
                    .trimIndent(),
            )

            assertThat(index.findByName("myapp")).isNotEmpty
            assertThat(index.findByName("myapp")[0].kind).isEqualTo(SymbolKind.MODULE)

            assertThat(index.findByName("Person")).isNotEmpty
            assertThat(index.findByName("Person")[0].kind).isEqualTo(SymbolKind.CLASS)

            assertThat(index.findByName("Runnable")).isNotEmpty
            assertThat(index.findByName("Runnable")[0].kind).isEqualTo(SymbolKind.INTERFACE)

            indexer.close()
        }
    }
}
