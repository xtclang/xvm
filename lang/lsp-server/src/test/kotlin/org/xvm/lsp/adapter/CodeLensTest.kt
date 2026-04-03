package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Tests for `textDocument/codeLens` — inline Run actions on module declarations.
 */
@DisplayName("CodeLens")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CodeLensTest : TreeSitterTestBase() {
    @Nested
    @DisplayName("getCodeLenses()")
    inner class CodeLensTests {
        @Test
        @DisplayName("should return Run lens for module declaration")
        fun shouldReturnRunLensForModule() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    void run() {
                        @Inject Console console;
                        console.print("hello");
                    }
                }
                """.trimIndent(),
            )
            val lenses = ts.getCodeLenses(uri)
            assertThat(lenses).isNotEmpty
            assertThat(lenses).anyMatch { it.command?.command == "xtc.runModule" }
            assertThat(lenses).anyMatch { it.command?.title?.contains("myapp") == true }
        }

        @Test
        @DisplayName("should place lens on module declaration line")
        fun shouldPlaceLensOnModuleLine() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Foo {}
                }
                """.trimIndent(),
            )
            val lenses = ts.getCodeLenses(uri)
            assertThat(lenses).isNotEmpty
            // Module declaration is on line 0
            assertThat(
                lenses
                    .first()
                    .range.start.line,
            ).isEqualTo(0)
        }

        @Test
        @DisplayName("should not return lenses for classes or methods")
        fun shouldNotReturnLensesForClassesOrMethods() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Foo {
                        void bar() {}
                    }
                }
                """.trimIndent(),
            )
            val lenses = ts.getCodeLenses(uri)
            // Only 1 lens for the module, none for class or method
            assertThat(lenses).hasSize(1)
            assertThat(lenses.first().command?.title).contains("myapp")
        }

        @Test
        @DisplayName("should return empty for file with no module")
        fun shouldReturnEmptyForNoModule() {
            val uri = freshUri()
            ts.compile(uri, "class OrphanClass {}")
            val lenses = ts.getCodeLenses(uri)
            assertThat(lenses).isEmpty()
        }
    }
}
