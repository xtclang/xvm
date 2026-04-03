package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Rename tests for [TreeSitterAdapter].
 *
 * Exercises prepare-rename and rename-all-occurrences via AST-based
 * identifier matching.
 *
 * All tests are skipped (not failed) when the tree-sitter native library is unavailable.
 */
@DisplayName("TreeSitterAdapter - Rename")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RenameTest : TreeSitterTestBase() {
    // ========================================================================
    // rename()
    // ========================================================================

    @Nested
    @DisplayName("rename()")
    inner class RenameTests {
        /**
         * `prepareRename` finds the identifier AST node at the cursor and returns
         * its text as the placeholder. Cursor on "Person" should yield exactly that.
         */
        @Test
        @DisplayName("should prepare rename for identifier")
        fun shouldPrepareRename() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val result = ts.prepareRename(uri, 1, 10)

            assertThat(result).isNotNull
            assertThat(result!!.placeholder).isEqualTo("Person")
        }

        /**
         * Renaming "Person" to "Human" should produce edits for every identifier node
         * with text "Person" in the file -- at least the declaration and usage sites.
         */
        @Test
        @DisplayName("should rename all occurrences")
        fun shouldRenameAllOccurrences() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                        Person create() {
                            return new Person();
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val edit = ts.rename(uri, 1, 10, "Human")

            assertThat(edit).isNotNull
            val edits = edit!!.changes[uri]
            assertThat(edits).isNotNull
            assertThat(edits!!).hasSizeGreaterThanOrEqualTo(2)
            assertThat(edits).allMatch { it.newText == "Human" }
        }

        /** Past-EOF has no identifier to rename, so prepareRename must return null. */
        @Test
        @DisplayName("should return null for unknown position")
        fun shouldReturnNullForUnknownPosition() {
            val uri = freshUri()
            ts.compile(uri, "module myapp {}")

            assertThat(ts.prepareRename(uri, 100, 0)).isNull()
        }
    }
}
