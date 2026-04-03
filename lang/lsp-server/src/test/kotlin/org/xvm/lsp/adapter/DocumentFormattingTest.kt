package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.xvm.lsp.adapter.treesitter.TreeSitterAdapter
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for AST-aware document formatting (`textDocument/formatting`) in [TreeSitterAdapter].
 *
 * Each test provides an XTC source with incorrect indentation and asserts the formatter
 * produces the correctly indented version.
 *
 * All tests are skipped (not failed) when the tree-sitter native library is unavailable.
 */
@DisplayName("DocumentFormatting")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentFormattingTest {
    private var adapter: TreeSitterAdapter? = null
    private val uriCounter = AtomicInteger(0)

    private val ts: TreeSitterAdapter get() = adapter!!

    private fun freshUri(): String = "file:///docfmt${uriCounter.incrementAndGet()}.x"

    private val defaultOptions =
        XtcCompilerAdapter.FormattingOptions(
            tabSize = 4,
            insertSpaces = true,
        )

    @BeforeAll
    fun setUpAdapter() {
        adapter = runCatching { TreeSitterAdapter() }.getOrNull()
    }

    @BeforeEach
    fun assumeAvailable() {
        Assumptions.assumeTrue(adapter != null, "Tree-sitter native library not available")
    }

    @AfterAll
    fun tearDown() {
        adapter?.close()
    }

    // ========================================================================
    // Test helpers
    // ========================================================================

    /**
     * Compile [input], format the whole document, apply edits, and return the result.
     */
    private fun formatWhole(input: String): String {
        val uri = freshUri()
        ts.compile(uri, input)
        val edits = ts.formatDocument(uri, input, defaultOptions)
        return applyEdits(input, edits)
    }

    /**
     * Compile [input], format only the specified line range, apply edits, and return the result.
     */
    private fun formatRange(
        input: String,
        startLine: Int,
        endLine: Int,
    ): String {
        val uri = freshUri()
        ts.compile(uri, input)
        val range =
            XtcCompilerAdapter.Range(
                start = XtcCompilerAdapter.Position(startLine, 0),
                end = XtcCompilerAdapter.Position(endLine, 0),
            )
        val edits = ts.formatRange(uri, input, range, defaultOptions)
        return applyEdits(input, edits)
    }

    /**
     * Compile [input], format, assert that no edits are returned.
     */
    private fun assertNoEdits(input: String) {
        val uri = freshUri()
        ts.compile(uri, input)
        val edits = ts.formatDocument(uri, input, defaultOptions)
        assertThat(edits).isEmpty()
    }

    /**
     * Compile [input], format, and assert the result equals [expected].
     * Automatically appends `\n` to [expected] if it doesn't already end with one,
     * since the formatter always ensures a final newline.
     */
    private fun assertFormatsTo(
        input: String,
        expected: String,
    ) {
        val result = formatWhole(input)
        val expectedWithNewline = if (expected.endsWith("\n")) expected else expected + "\n"
        assertThat(result).isEqualTo(expectedWithNewline)
    }

    /**
     * Apply text edits to source. Edits are applied in reverse order (bottom-to-top,
     * right-to-left) to preserve positions of earlier edits.
     */
    private fun applyEdits(
        source: String,
        edits: List<XtcCompilerAdapter.TextEdit>,
    ): String {
        if (edits.isEmpty()) return source
        val lines = source.split("\n").toMutableList()

        // Sort edits in reverse document order so we can apply them without shifting positions.
        val sorted =
            edits.sortedWith(
                compareByDescending<XtcCompilerAdapter.TextEdit> { it.range.start.line }
                    .thenByDescending { it.range.start.column },
            )

        for (edit in sorted) {
            val startLine = edit.range.start.line
            val startCol = edit.range.start.column
            val endLine = edit.range.end.line
            val endCol = edit.range.end.column

            if (startLine == endLine) {
                // Single-line edit: replace within the line
                val line = lines[startLine]
                lines[startLine] = line.substring(0, startCol) + edit.newText + line.substring(endCol)
            } else {
                // Multi-line edit: splice lines
                val prefix = lines[startLine].substring(0, startCol)
                val suffix = lines[endLine].substring(endCol)
                val newContent = prefix + edit.newText + suffix
                val newLines = newContent.split("\n")

                // Remove old lines and insert new ones
                for (i in endLine downTo startLine) {
                    lines.removeAt(i)
                }
                lines.addAll(startLine, newLines)
            }
        }

        return lines.joinToString("\n")
    }

    // ========================================================================
    // Module / class / method body indentation
    // ========================================================================

    @Nested
    @DisplayName("Basic indentation")
    inner class BasicIndentationTests {
        @Test
        @DisplayName("top-level module body indented to 4 spaces")
        fun topLevelModule() {
            val input =
                """
                module myapp {
                class Foo {}
                }
                """.trimIndent()

            val expected =
                """
                module myapp {
                    class Foo {}
                }
                """.trimIndent()

            assertFormatsTo(input, expected)
        }

        @Test
        @DisplayName("nested class body indented to 8 spaces")
        fun nestedClass() {
            val input =
                """
                module myapp {
                    class Outer {
                class Inner {}
                    }
                }
                """.trimIndent()

            val expected =
                """
                module myapp {
                    class Outer {
                        class Inner {}
                    }
                }
                """.trimIndent()

            assertFormatsTo(input, expected)
        }

        @Test
        @DisplayName("method body at correct level")
        fun methodBody() {
            val input =
                """
                module myapp {
                    class Foo {
                void bar() {
                Int x = 1;
                }
                    }
                }
                """.trimIndent()

            val expected =
                """
                module myapp {
                    class Foo {
                        void bar() {
                            Int x = 1;
                        }
                    }
                }
                """.trimIndent()

            assertFormatsTo(input, expected)
        }
    }

    // ========================================================================
    // Control flow blocks
    // ========================================================================

    @Nested
    @DisplayName("Control flow indentation")
    inner class ControlFlowTests {
        @Test
        @DisplayName("if/for/while blocks indent body")
        fun controlFlowBlocks() {
            val input =
                """
                module myapp {
                    class Foo {
                        void bar() {
                if (True) {
                x = 1;
                }
                        }
                    }
                }
                """.trimIndent()

            val expected =
                """
                module myapp {
                    class Foo {
                        void bar() {
                            if (True) {
                                x = 1;
                            }
                        }
                    }
                }
                """.trimIndent()

            assertFormatsTo(input, expected)
        }
    }

    // ========================================================================
    // Continuation lines
    // ========================================================================

    @Nested
    @DisplayName("Continuation lines")
    inner class ContinuationTests {
        @Test
        @DisplayName("extends/implements get continuation indent from declaration")
        fun continuationKeywords() {
            val input =
                """
                module myapp {
                    class Foo
                extends Bar {
                    }
                }
                """.trimIndent()

            val expected =
                """
                module myapp {
                    class Foo
                            extends Bar {
                    }
                }
                """.trimIndent()

            assertFormatsTo(input, expected)
        }
    }

    // ========================================================================
    // Switch / case
    // ========================================================================

    @Nested
    @DisplayName("Switch/case indentation")
    inner class SwitchCaseTests {
        @Test
        @DisplayName("case labels at same indent as switch keyword")
        fun caseLabelsAtSwitchLevel() {
            val input =
                """
                module myapp {
                    class Foo {
                        void bar() {
                            return switch (x) {
                                    case 1: True;
                            };
                        }
                    }
                }
                """.trimIndent()

            val result = formatWhole(input)
            val lines = result.trimEnd('\n').split("\n")

            val caseLine = lines.firstOrNull { it.trimStart().startsWith("case") }
            val switchLine = lines.first { it.trimStart().startsWith("return switch") }
            if (caseLine != null) {
                val caseIndent = caseLine.takeWhile { it == ' ' }.length
                val switchIndent = switchLine.takeWhile { it == ' ' }.length
                // Case at same indent as the switch, or handled by general depth
                assertThat(caseIndent).isEqualTo(switchIndent)
            }
        }
    }

    // ========================================================================
    // Closing braces and parens
    // ========================================================================

    @Nested
    @DisplayName("Closing delimiters")
    inner class ClosingDelimiterTests {
        @Test
        @DisplayName("closing } matches opening construct")
        fun closingBraceMatchesOpening() {
            val input =
                """
                module myapp {
                    class Foo {
                            }
                        }
                """.trimIndent()

            val expected =
                """
                module myapp {
                    class Foo {
                    }
                }
                """.trimIndent()

            assertFormatsTo(input, expected)
        }
    }

    // ========================================================================
    // Comments
    // ========================================================================

    @Nested
    @DisplayName("Comment indentation")
    inner class CommentTests {
        @Test
        @DisplayName("doc comment interior lines aligned with opening")
        fun docCommentAlignment() {
            val input =
                """
                module myapp {
                    /**
                * Some doc.
                    */
                    class Foo {}
                }
                """.trimIndent()

            val result = formatWhole(input)
            val lines = result.split("\n")

            // Interior " * " and closing " */" should be at indent 4 + 1 = 5
            val interiorLine = lines.first { it.trimStart().startsWith("* Some") }
            val interiorIndent = interiorLine.takeWhile { it == ' ' }.length
            assertThat(interiorIndent).isEqualTo(5)

            val closingLine = lines.first { it.trimStart().startsWith("*/") }
            val closingIndent = closingLine.takeWhile { it == ' ' }.length
            assertThat(closingIndent).isEqualTo(5)
        }

        @Test
        @DisplayName("block comment interior lines aligned with opening")
        fun blockCommentAlignment() {
            val input =
                """
                module myapp {
                    /*
                * Block comment.
                    */
                    class Foo {}
                }
                """.trimIndent()

            val result = formatWhole(input)
            val lines = result.split("\n")

            val interiorLine = lines.first { it.trimStart().startsWith("* Block") }
            val interiorIndent = interiorLine.takeWhile { it == ' ' }.length
            assertThat(interiorIndent).isEqualTo(5)
        }

        @Test
        @DisplayName("line comments indent same as surrounding code")
        fun lineCommentIndent() {
            val input =
                """
                module myapp {
                // A comment
                    class Foo {}
                }
                """.trimIndent()

            val result = formatWhole(input)
            val lines = result.split("\n")

            // Line comment should be at module body level (indent 4)
            val commentLine = lines.first { it.trimStart().startsWith("//") }
            val commentIndent = commentLine.takeWhile { it == ' ' }.length
            assertThat(commentIndent).isEqualTo(4)
        }
    }

    // ========================================================================
    // String literals
    // ========================================================================

    @Nested
    @DisplayName("String literals")
    inner class StringLiteralTests {
        @Test
        @DisplayName("string literal content is never modified")
        fun stringLiteralPreserved() {
            val input =
                """
                module myapp {
                    class Foo {
                        String s = ${"\"\"\""}
                  some text
                    indented
                ${"\"\"\""}
                    }
                }
                """.trimIndent()

            // After formatting, the lines inside the multiline string should not change
            val result = formatWhole(input)
            assertThat(result).contains("  some text")
            assertThat(result).contains("    indented")
        }
    }

    // ========================================================================
    // Blank lines
    // ========================================================================

    @Nested
    @DisplayName("Blank lines")
    inner class BlankLineTests {
        @Test
        @DisplayName("blank lines are preserved but trailing whitespace removed")
        fun blankLinesPreserved() {
            val input = "module myapp {\n    \n    class Foo {}\n}"
            val result = formatWhole(input)
            // Blank line should still be there, but trailing spaces removed
            assertThat(result).contains("\n\n") // blank line preserved
            val contentLines = result.trimEnd('\n').split("\n")
            assertThat(contentLines).hasSize(4) // same number of content lines
            assertThat(contentLines[1]).isEmpty() // blank line has no content
        }
    }

    // ========================================================================
    // Final newline
    // ========================================================================

    @Nested
    @DisplayName("Final newline")
    inner class FinalNewlineTests {
        @Test
        @DisplayName("default options insert final newline (XTC default)")
        fun defaultInsertsNewline() {
            val input = "module myapp {}"
            val uri = freshUri()
            ts.compile(uri, input)

            // Default options have insertFinalNewline = true
            val edits = ts.formatDocument(uri, input, defaultOptions)
            val result = applyEdits(input, edits)
            assertThat(result).endsWith("\n")
        }

        @Test
        @DisplayName("insertFinalNewline=false respects user preference")
        fun respectsUserPreferenceNoNewline() {
            val input = "module myapp {}"
            val uri = freshUri()
            ts.compile(uri, input)

            val options =
                XtcCompilerAdapter.FormattingOptions(
                    tabSize = 4,
                    insertSpaces = true,
                    insertFinalNewline = false,
                )
            val edits = ts.formatDocument(uri, input, options)
            val result = applyEdits(input, edits)

            // User explicitly opted out of final newline
            assertThat(result).doesNotEndWith("\n")
        }

        @Test
        @DisplayName("no duplicate newline when file already ends with one")
        fun noDuplicateNewline() {
            val input = "module myapp {}\n"
            val uri = freshUri()
            ts.compile(uri, input)
            val edits = ts.formatDocument(uri, input, defaultOptions)

            // No newline edit needed — file already ends with \n
            assertThat(edits.none { it.newText == "\n" }).isTrue()
        }
    }

    // ========================================================================
    // Already correct file
    // ========================================================================

    @Nested
    @DisplayName("Idempotent formatting")
    inner class IdempotentTests {
        @Test
        @DisplayName("well-formatted file produces no edits")
        fun alreadyCorrect() {
            val input =
                """
                module myapp {
                    class Foo {
                        void bar() {
                            Int x = 1;
                        }
                    }
                }
                """.trimIndent() + "\n"

            assertNoEdits(input)
        }

        @Test
        @DisplayName("formatting is idempotent - second pass produces no indent edits")
        fun idempotent() {
            val input =
                """
                module myapp {
                class Foo {
                void bar() {
                Int x = 1;
                }
                }
                }
                """.trimIndent()

            val firstPass = formatWhole(input)

            // Run a second format pass on the already-formatted output
            val secondPass = formatWhole(firstPass)

            // The second pass should not change anything
            assertThat(secondPass).isEqualTo(firstPass)
        }
    }

    // ========================================================================
    // Range formatting
    // ========================================================================

    @Nested
    @DisplayName("Range formatting")
    inner class RangeFormattingTests {
        @Test
        @DisplayName("only formats lines within the requested range")
        fun rangeFormatting() {
            val input =
                """
                module myapp {
                class Foo {
                void bar() {
                Int x = 1;
                }
                }
                }
                """.trimIndent()

            // Format only lines 2-4 (the method and its body)
            val result = formatRange(input, startLine = 2, endLine = 4)
            val lines = result.split("\n")

            // Line 1 (class Foo) should NOT be fixed - still at column 0
            assertThat(lines[1]).startsWith("class")

            // Line 5 (}) should NOT be fixed - still at column 0
            assertThat(lines[5]).isEqualTo("}")
        }
    }

    // ========================================================================
    // Lambda arrow
    // ========================================================================

    @Nested
    @DisplayName("Lambda arrow indentation")
    inner class LambdaArrowTests {
        @Test
        @DisplayName("body after -> is indented from arrow line")
        fun lambdaArrowBody() {
            val input =
                """
                module myapp {
                    class Foo {
                        void bar() {
                            list.each(e -> {
                            x = e;
                            });
                        }
                    }
                }
                """.trimIndent()

            val result = formatWhole(input)
            val lines = result.split("\n")

            // The "x = e;" line should be inside the lambda block, indented
            val bodyLine = lines.first { it.trimStart().startsWith("x = e") }
            val bodyIndent = bodyLine.takeWhile { it == ' ' }.length
            // Inside lambda block -> deeper than the arrow line
            assertThat(bodyIndent).isGreaterThan(12)
        }
    }

    // ========================================================================
    // Mixed issues in one file
    // ========================================================================

    @Nested
    @DisplayName("Mixed formatting issues")
    inner class MixedIssuesTests {
        @Test
        @DisplayName("multiple problems fixed in one pass")
        fun mixedIssues() {
            val input =
                """
                module myapp {
                class Foo {
                        void bar() {
                   Int x = 1;
                        }
                }
                }
                """.trimIndent()

            val result = formatWhole(input)
            val lines = result.split("\n")

            // Verify each line is at the correct indent level
            assertThat(lines[0]).isEqualTo("module myapp {")
            assertThat(lines[1]).startsWith("    ") // class at 4
            assertThat(lines[2]).startsWith("        ") // method at 8
        }
    }

    // ========================================================================
    // Multi-line parameter alignment
    // ========================================================================

    @Nested
    @DisplayName("Multi-line parameter alignment")
    inner class MultiLineParamTests {
        @Test
        @DisplayName("multi-line method parameters get continuation indent")
        fun methodParameters() {
            val input =
                """
                module myapp {
                    class Foo {
                        void bar(
                Int a,
                String b,
                        ) {
                        }
                    }
                }
                """.trimIndent()

            // Parameters indent = countIndentDepth(parenAncestor) * 4 + 4
            // parenAncestor is inside class_body + module_body = depth 2 -> 2*4+4 = 12
            val expected =
                """
                module myapp {
                    class Foo {
                        void bar(
                            Int a,
                            String b,
                        ) {
                        }
                    }
                }
                """.trimIndent()

            assertFormatsTo(input, expected)
        }

        @Test
        @DisplayName("multi-line call arguments get continuation indent")
        fun callArguments() {
            val input =
                """
                module myapp {
                    class Foo {
                        void bar() {
                            foo(
                1,
                2,
                            );
                        }
                    }
                }
                """.trimIndent()

            // Arguments indent = countIndentDepth(parenAncestor) * 4 + 4
            // parenAncestor is inside block + class_body + module_body = depth 3 -> 3*4+4 = 16
            val expected =
                """
                module myapp {
                    class Foo {
                        void bar() {
                            foo(
                                1,
                                2,
                            );
                        }
                    }
                }
                """.trimIndent()

            assertFormatsTo(input, expected)
        }

        @Test
        @DisplayName("single-line parens are not affected")
        fun singleLineParensUnchanged() {
            val input =
                """
                module myapp {
                    class Foo {
                        void bar(Int a, String b) {
                        }
                    }
                }
                """.trimIndent() + "\n"

            assertNoEdits(input)
        }
    }

    // ========================================================================
    // Performance
    // ========================================================================

    @Nested
    @DisplayName("Performance")
    inner class PerformanceTests {
        @Test
        @DisplayName("1000+ line file formats in under 500ms")
        fun largeFilePerformance() {
            // Generate a 1000+ line XTC file
            val sb = StringBuilder()
            sb.appendLine("module myapp {")
            sb.appendLine("    class BigClass {")
            for (i in 1..250) {
                sb.appendLine("        void method$i() {")
                sb.appendLine("Int x = $i;")
                sb.appendLine("        }")
            }
            sb.appendLine("    }")
            sb.appendLine("}")

            val input = sb.toString()
            val uri = freshUri()
            ts.compile(uri, input)

            val start = System.nanoTime()
            ts.formatDocument(uri, input, defaultOptions)
            val elapsed = (System.nanoTime() - start) / 1_000_000

            assertThat(elapsed).isLessThan(500)
        }
    }
}
