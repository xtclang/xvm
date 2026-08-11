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

        /**
         * Issue #459: renaming a local variable renamed ALL same-named variables and
         * properties in the file. Rename must be scope-aware: only occurrences that
         * resolve to the same declaration as the cursor's identifier get edited.
         * Here `count` exists as a module property, as a local in run(), and as a
         * local in other() -- renaming the local in run() must leave the property
         * and other()'s local untouched.
         */
        @Test
        @DisplayName("should rename only the scoped local variable")
        fun shouldRenameOnlyScopedLocalVariable() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    Int count = 0;
                    void run() {
                        Int count = 1;
                        count = count + 1;
                    }
                    void other() {
                        Int count = 2;
                        count = 3;
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on `count` usage inside run() -- line 4, col 8
            val edit = ts.rename(uri, 4, 8, "total")

            assertThat(edit).isNotNull
            val edits = edit!!.changes[uri]
            assertThat(edits).isNotNull
            // declaration on line 3 + two usages on line 4; NOT the module property
            // (line 1) and NOT other()'s local (lines 7-8)
            assertThat(edits!!).hasSize(3)
            assertThat(edits.map { it.range.start.line }).containsExactlyInAnyOrder(3, 4, 4)
        }

        /**
         * Renaming an outer variable must not touch an inner declaration that
         * shadows it (nor the shadowed uses, which resolve to the inner one).
         */
        @Test
        @DisplayName("should not rename a shadowing inner declaration")
        fun shouldNotRenameShadowingInnerDeclaration() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    void run() {
                        Int value = 1;
                        value = 2;
                        if (value > 0) {
                            Int value = 10;
                            value = 20;
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on the outer `value` declaration -- line 2, col 12
            val edit = ts.rename(uri, 2, 12, "amount")

            assertThat(edit).isNotNull
            val edits = edit!!.changes[uri]
            assertThat(edits).isNotNull
            // outer declaration (line 2), outer use (line 3), condition use (line 4);
            // NOT the inner shadowing declaration (line 5) or its use (line 6)
            assertThat(edits!!.map { it.range.start.line }).containsExactlyInAnyOrder(2, 3, 4)
        }

        /**
         * Issue #459: with the whole identifier selected, the caret sits at the
         * exclusive end of the word. prepareRename must still find the identifier.
         */
        @Test
        @DisplayName("should prepare rename when caret is at the end of the word")
        fun shouldPrepareRenameAtWordEnd() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // `Person` spans cols 10..16 on line 1; col 16 is the exclusive end
            val result = ts.prepareRename(uri, 1, 16)

            assertThat(result).isNotNull
            assertThat(result!!.placeholder).isEqualTo("Person")
        }
    }
}
