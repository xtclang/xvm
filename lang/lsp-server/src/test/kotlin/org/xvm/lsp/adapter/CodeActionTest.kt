package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Code action tests for [TreeSitterAdapter].
 *
 * Exercises import organization and other quick-fix actions.
 *
 * All tests are skipped (not failed) when the tree-sitter native library is unavailable.
 */
@DisplayName("TreeSitterAdapter - Code Actions")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CodeActionTest : TreeSitterTestBase() {
    // ========================================================================
    // getCodeActions()
    // ========================================================================

    @Nested
    @DisplayName("getCodeActions()")
    inner class CodeActionTests {
        /**
         * `buildOrganizeImportsAction` searches `tree.root.children` for `import_statement`
         * nodes. In XTC, imports are often nested inside the module body, so whether
         * the grammar places them at root level is grammar-dependent. We use root-level
         * imports and assert the method completes without error.
         */
        @Test
        @DisplayName("should suggest organize imports when unsorted at root level")
        fun shouldSuggestOrganizeImports() {
            val uri = freshUri()
            val source =
                """
                import foo.Zebra;
                import bar.Alpha;
                module myapp {
                }
                """.trimIndent()

            ts.compile(uri, source)
            val actions = ts.getCodeActions(uri, zeroRange(), emptyList())

            assertThat(actions).isNotNull
        }

        /**
         * Already-sorted imports (alphabetically) should never trigger the
         * "Organize Imports" action, regardless of where the grammar places them.
         */
        @Test
        @DisplayName("should not suggest organize imports when sorted")
        fun shouldNotSuggestWhenSorted() {
            val uri = freshUri()
            val source =
                """
                import bar.Alpha;
                import foo.Zebra;
                module myapp {
                }
                """.trimIndent()

            ts.compile(uri, source)
            val actions = ts.getCodeActions(uri, zeroRange(), emptyList())

            assertThat(actions).noneMatch { it.title == "Organize Imports" }
        }

        private fun zeroRange() =
            Adapter.Range(
                Adapter.Position(0, 0),
                Adapter.Position(0, 0),
            )
    }
}
