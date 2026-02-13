package org.xvm.lsp.index

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
import org.xvm.lsp.treesitter.XtcQueryEngine
import java.nio.file.Files
import java.nio.file.Path

/**
 * Integration tests for [WorkspaceIndexer].
 *
 * Requires the tree-sitter native library. Tests are skipped (not failed)
 * when the native library is unavailable.
 */
@DisplayName("WorkspaceIndexer")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspaceIndexerTest {
    private var parser: XtcParser? = null
    private var queryEngine: XtcQueryEngine? = null

    @BeforeAll
    fun setUpParser() {
        parser = runCatching { XtcParser() }.getOrNull()
        parser?.let { queryEngine = XtcQueryEngine(it.getLanguage()) }
    }

    @BeforeEach
    fun assumeAvailable() {
        Assumptions.assumeTrue(parser != null, "Tree-sitter native library not available")
    }

    @AfterAll
    fun tearDown() {
        queryEngine?.close()
        parser?.close()
    }

    // ========================================================================
    // Scan workspace
    // ========================================================================

    @Nested
    @DisplayName("scanWorkspace()")
    inner class ScanTests {
        @Test
        @DisplayName("should index all .x files in workspace")
        fun shouldIndexXtcFiles(
            @TempDir tempDir: Path,
        ) {
            // Create test .x files
            Files.writeString(
                tempDir.resolve("Foo.x"),
                """
                module myapp {
                    class Foo {
                    }
                }
                """.trimIndent(),
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
                """.trimIndent(),
            )

            // Create non-.x file that should be ignored
            Files.writeString(tempDir.resolve("readme.txt"), "not XTC code")

            val index = WorkspaceIndex()
            val indexer = WorkspaceIndexer(index, parser!!, queryEngine!!)

            indexer.scanWorkspace(listOf(tempDir.toString())).join()

            assertThat(index.fileCount).isEqualTo(2)
            assertThat(index.symbolCount).isGreaterThanOrEqualTo(2) // at least Foo and Bar
            assertThat(index.findByName("Foo")).isNotEmpty
            assertThat(index.findByName("Bar")).isNotEmpty

            indexer.close()
        }

        @Test
        @DisplayName("should index nested directories")
        fun shouldIndexNestedDirs(
            @TempDir tempDir: Path,
        ) {
            val subDir = tempDir.resolve("src/main")
            Files.createDirectories(subDir)
            Files.writeString(
                subDir.resolve("Nested.x"),
                """
                module myapp {
                    class Nested {
                    }
                }
                """.trimIndent(),
            )

            val index = WorkspaceIndex()
            val indexer = WorkspaceIndexer(index, parser!!, queryEngine!!)

            indexer.scanWorkspace(listOf(tempDir.toString())).join()

            assertThat(index.findByName("Nested")).isNotEmpty

            indexer.close()
        }

        @Test
        @DisplayName("should handle empty workspace")
        fun shouldHandleEmptyWorkspace(
            @TempDir tempDir: Path,
        ) {
            val index = WorkspaceIndex()
            val indexer = WorkspaceIndexer(index, parser!!, queryEngine!!)

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
            val indexer = WorkspaceIndexer(index, parser!!, queryEngine!!)
            val uri = "file:///test.x"

            // Index initial content
            indexer.reindexFile(
                uri,
                """
                module myapp {
                    class OldName {
                    }
                }
                """.trimIndent(),
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
                """.trimIndent(),
            )
            assertThat(index.findByName("OldName")).isEmpty()
            assertThat(index.findByName("NewName")).isNotEmpty

            indexer.close()
        }

        @Test
        @DisplayName("should remove file from index")
        fun shouldRemoveFile() {
            val index = WorkspaceIndex()
            val indexer = WorkspaceIndexer(index, parser!!, queryEngine!!)
            val uri = "file:///test.x"

            indexer.reindexFile(
                uri,
                """
                module myapp {
                    class ToBeDeleted {
                    }
                }
                """.trimIndent(),
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
            val indexer = WorkspaceIndexer(index, parser!!, queryEngine!!)

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
                """.trimIndent(),
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
