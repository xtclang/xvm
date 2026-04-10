package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Inlay hint tests for [TreeSitterAdapter].
 *
 * Exercises inlay hint generation (currently disabled, pending compiler
 * type inference support).
 *
 * All tests are skipped (not failed) when the tree-sitter native library is unavailable.
 */
@DisplayName("TreeSitterAdapter - Inlay Hints")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InlayHintTest : TreeSitterTestBase() {
    // ========================================================================
    // getInlayHints() -- future
    // ========================================================================

    @Nested
    @DisplayName("getInlayHints() -- future")
    inner class InlayHintTests {
        /**
         * TODO: Inlay hints show inferred type annotations inline (e.g., `val x` displays
         *   `: Int` after the variable). This requires the compiler's type inference engine;
         *   tree-sitter alone cannot determine types.
         */
        @Test
        @Disabled("Inlay hints not yet implemented -- requires compiler type inference")
        @DisplayName("should return type hints for val declarations")
        fun shouldReturnTypeHints() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                        void test() {
                            val x = 42;
                        }
                    }
                }
                """.trimIndent(),
            )

            val range =
                Range(
                    Position(0, 0),
                    Position(7, 0),
                )
            val hints = ts.getInlayHints(uri, range)

            // TODO: Once implemented, assert a TYPE hint appears after "val x" with label ": Int" or similar.
            assertThat(hints).isNotEmpty
        }
    }
}
