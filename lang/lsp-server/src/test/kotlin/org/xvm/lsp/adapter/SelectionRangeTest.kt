package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Selection range tests for [TreeSitterAdapter].
 *
 * Exercises the tree-sitter-specific AST-based selection range expansion,
 * including nested call site scenarios.
 *
 * All tests are skipped (not failed) when the tree-sitter native library is unavailable.
 */
@DisplayName("TreeSitterAdapter - Selection Ranges")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SelectionRangeTest : TreeSitterTestBase() {
    // ========================================================================
    // getSelectionRanges() -- tree-sitter-specific
    // ========================================================================

    @Nested
    @DisplayName("getSelectionRanges() -- tree-sitter-specific")
    inner class SelectionRangeTests {
        /**
         * From a leaf identifier ("name" inside a return statement), the adapter walks
         * the AST parent chain, deduplicating nodes with identical ranges. The result
         * should have depth >= 3 (e.g., identifier -> expression -> block -> declaration).
         */
        @Test
        @DisplayName("should produce nested chain from identifier to root")
        fun shouldProduceNestedChain() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                        String getName() {
                            return name;
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val selections = ts.getSelectionRanges(uri, listOf(Position(3, 15)))
            val depth = selectionDepth(selections.single())

            assertThat(depth).isGreaterThanOrEqualTo(3)
        }

        /**
         * Each parent range must strictly contain (or equal) its child range -- the
         * selection never shrinks as you walk outward. We linearize positions as
         * `line * 10000 + column` for a simple numeric comparison.
         */
        @Test
        @DisplayName("should produce widening chain where each parent contains child")
        fun shouldProduceWideningChain() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                        String getName() {
                            return name;
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val selection =
                ts
                    .getSelectionRanges(
                        uri,
                        listOf(Position(2, 15)),
                    ).single()

            generateSequence(selection) { it.parent }
                .zipWithNext()
                .forEach { (child, parent) ->
                    assertThat(linearize(parent.range.start))
                        .isLessThanOrEqualTo(linearize(child.range.start))
                    assertThat(linearize(parent.range.end))
                        .isGreaterThanOrEqualTo(linearize(child.range.end))
                }
        }

        /**
         * At least one range in the chain should span more than zero characters,
         * proving the selection is meaningful (not just point ranges everywhere).
         */
        @Test
        @DisplayName("should produce at least one range wider than a single point")
        fun shouldProduceMeaningfulRanges() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val selection =
                ts
                    .getSelectionRanges(
                        uri,
                        listOf(Position(1, 10)),
                    ).single()

            val hasNonPointRange =
                generateSequence(selection) { it.parent }.any { sel ->
                    linearize(sel.range.start) != linearize(sel.range.end)
                }
            assertThat(hasNonPointRange).isTrue()
        }

        private fun selectionDepth(sel: SelectionRange): Int = generateSequence(sel) { it.parent }.count()
    }

    // ========================================================================
    // Selection ranges at call sites -- tree-sitter-specific
    // ========================================================================

    @Nested
    @DisplayName("selection ranges at call sites -- tree-sitter-specific")
    inner class SelectionRangesAtCallSiteTests {
        /**
         * Starting from an argument literal (`1` in `add(1, 2)`), the selection
         * range chain should walk outward through argument list, call expression,
         * statement, block, method, class, module -- at least 4 levels deep.
         */
        @Test
        @DisplayName("should walk outward from call argument")
        fun shouldWalkOutwardFromCallArgument() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Calculator {
                        Int add(Int a, Int b) {
                            return a + b;
                        }
                        void test() {
                            add(1, 2);
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on '1' in `add(1, 2)` at line 6, col 16
            val selections = ts.getSelectionRanges(uri, listOf(Position(6, 16)))
            val depth = selectionDepth(selections.single())
            logger.info("[TEST] shouldWalkOutwardFromCallArgument -> depth={}", depth)
            logSelectionChain("shouldWalkOutwardFromCallArgument", selections.single())

            assertThat(depth).isGreaterThanOrEqualTo(4)
            assertWideningChain(selections.single())
        }

        /**
         * Starting from a nested call argument (`1` in `negate(1)` inside
         * `add(negate(1), 2)`), the chain should be even deeper -- at least 5 levels.
         */
        @Test
        @DisplayName("should walk outward from nested call argument")
        fun shouldWalkOutwardFromNestedCallArgument() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Math {
                        Int add(Int a, Int b) {
                            return a + b;
                        }
                        Int negate(Int x) {
                            return -x;
                        }
                        void test() {
                            add(negate(1), 2);
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on '1' in `negate(1)` at line 9, col 23
            val selections = ts.getSelectionRanges(uri, listOf(Position(9, 23)))
            val depth = selectionDepth(selections.single())
            logger.info("[TEST] shouldWalkOutwardFromNestedCallArgument -> depth={}", depth)
            logSelectionChain("shouldWalkOutwardFromNestedCallArgument", selections.single())

            assertThat(depth).isGreaterThanOrEqualTo(5)
            assertWideningChain(selections.single())
        }

        /**
         * Multiple cursor positions in a single request should each produce an
         * independent selection range chain.
         */
        @Test
        @DisplayName("should handle multiple positions independently")
        fun shouldHandleMultiplePositionsIndependently() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Calculator {
                        Int add(Int a, Int b) {
                            return a + b;
                        }
                        void test() {
                            add(1, 2);
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val positions =
                listOf(
                    Position(6, 16), // '1' in add(1, 2)
                    Position(2, 12), // 'add' in declaration
                )
            val selections = ts.getSelectionRanges(uri, positions)
            logger.info("[TEST] shouldHandleMultiplePositionsIndependently -> {} selections", selections.size)
            selections.forEachIndexed { i, sel ->
                logSelectionChain("shouldHandleMultiplePositionsIndependently[$i]", sel)
            }

            assertThat(selections).hasSize(2)
            selections.forEach { assertWideningChain(it) }
        }

        private fun selectionDepth(sel: SelectionRange): Int = generateSequence(sel) { it.parent }.count()

        private fun assertWideningChain(selection: SelectionRange) {
            generateSequence(selection) { it.parent }
                .zipWithNext()
                .forEach { (child, parent) ->
                    assertThat(linearize(parent.range.start))
                        .isLessThanOrEqualTo(linearize(child.range.start))
                    assertThat(linearize(parent.range.end))
                        .isGreaterThanOrEqualTo(linearize(child.range.end))
                }
        }

        private fun logSelectionChain(
            test: String,
            sel: SelectionRange,
        ) {
            val chain = generateSequence(sel) { it.parent }.toList()
            chain.forEachIndexed { i, s ->
                val r = s.range
                logger.info("  [{}] level {} -> L{}:{}-L{}:{}", test, i, r.start.line, r.start.column, r.end.line, r.end.column)
            }
        }
    }

    private fun linearize(pos: Position): Int = pos.line * 10_000 + pos.column
}
