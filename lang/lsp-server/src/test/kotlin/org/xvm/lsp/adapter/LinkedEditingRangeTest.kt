package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Linked editing range tests for [TreeSitterAdapter].
 *
 * Exercises same-file identifier linking: when the cursor is on an identifier,
 * all same-name occurrences are returned so editors can rename them simultaneously.
 *
 * All tests are skipped (not failed) when the tree-sitter native library is unavailable.
 */
@DisplayName("TreeSitterAdapter - Linked Editing Ranges")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LinkedEditingRangeTest : TreeSitterTestBase() {
    @Nested
    @DisplayName("getLinkedEditingRanges()")
    inner class LinkedEditingRangeTests {
        @Test
        @DisplayName("should return all occurrences of a variable used multiple times")
        fun shouldReturnAllOccurrences() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Foo {
                        void bar() {
                            String name = "hello";
                            console.print(name);
                            return name;
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on 'name' at declaration (line 3, col 19)
            val result = ts.getLinkedEditingRanges(uri, 3, 19)

            assertThat(result).isNotNull
            assertThat(result!!.ranges).hasSizeGreaterThanOrEqualTo(3)
            logged("shouldReturnAllOccurrences", result.ranges)
        }

        @Test
        @DisplayName("should return null for single-use identifier")
        fun shouldReturnNullForSingleUse() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Foo {
                        void bar() {
                            String unique = "only here";
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val result = ts.getLinkedEditingRanges(uri, 3, 19)

            // Single occurrence — no linked editing (need 2+)
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should return null for non-identifier position")
        fun shouldReturnNullForNonIdentifier() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Foo {
                        void bar() {
                            return;
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on 'return' keyword
            val result = ts.getLinkedEditingRanges(uri, 3, 12)

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should link method parameter with its uses in body")
        fun shouldLinkParameterWithUses() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Math {
                        Int double(Int value) {
                            return value + value;
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on 'value' parameter (line 2, col 27)
            val result = ts.getLinkedEditingRanges(uri, 2, 27)

            assertThat(result).isNotNull
            // 'value' appears 3 times: parameter + 2 uses in return
            assertThat(result!!.ranges).hasSizeGreaterThanOrEqualTo(3)
            logged("shouldLinkParameterWithUses", result.ranges)
        }

        @Test
        @DisplayName("should return ranges with correct positions")
        fun shouldReturnCorrectPositions() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Foo {
                        void test() {
                            Int x = 1;
                            Int y = x + x;
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on 'x' at declaration (line 3)
            val result = ts.getLinkedEditingRanges(uri, 3, 16)

            assertThat(result).isNotNull
            val ranges = result!!.ranges
            assertThat(ranges).hasSizeGreaterThanOrEqualTo(3)

            // All ranges should span exactly the length of 'x' (1 character)
            ranges.forEach { range ->
                val length = range.end.column - range.start.column
                assertThat(length).isEqualTo(1)
            }
        }
    }
}
