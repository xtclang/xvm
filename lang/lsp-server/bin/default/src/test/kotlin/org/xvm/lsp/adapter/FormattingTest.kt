package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Formatting tests for [TreeSitterAdapter].
 *
 * Exercises whole-document formatting and range-based formatting, including
 * trailing whitespace removal and final newline insertion.
 *
 * All tests are skipped (not failed) when the tree-sitter native library is unavailable.
 */
@DisplayName("TreeSitterAdapter - Formatting")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FormattingTest : TreeSitterTestBase() {
    // ========================================================================
    // formatDocument()
    // ========================================================================

    @Nested
    @DisplayName("formatDocument()")
    inner class FormatTests {
        /**
         * Lines with trailing spaces ("myapp {   ") should produce edits that replace
         * the trailing whitespace with empty strings. Two lines have trailing spaces,
         * so we expect at least 2 edits.
         */
        @Test
        @DisplayName("should remove trailing whitespace")
        fun shouldRemoveTrailingWhitespace() {
            val source = "module myapp {   \n    class Person {  \n    }\n}"
            val options = formattingOptions(trimTrailingWhitespace = true)

            val edits = ts.formatDocument(freshUri(), source, options)

            assertThat(edits).hasSizeGreaterThanOrEqualTo(2)
            assertThat(edits).allMatch { it.newText.isEmpty() }
        }

        /**
         * When `insertFinalNewline` is true and the source doesn't end with `\n`,
         * the formatter should append one via a zero-width insertion at EOF.
         */
        @Test
        @DisplayName("should insert final newline")
        fun shouldInsertFinalNewline() {
            val options = formattingOptions(insertFinalNewline = true)

            val edits = ts.formatDocument(freshUri(), "module myapp {}", options)

            assertThat(edits).anyMatch { it.newText == "\n" }
        }

        /**
         * A file that already ends with `\n` and has no trailing whitespace is
         * "clean" -- the formatter should produce zero edits.
         */
        @Test
        @DisplayName("should return empty for clean file")
        fun shouldReturnEmptyForCleanFile() {
            val edits = ts.formatDocument(freshUri(), "module myapp {}\n", formattingOptions())

            assertThat(edits).isEmpty()
        }

        private fun formattingOptions(
            trimTrailingWhitespace: Boolean = false,
            insertFinalNewline: Boolean = false,
        ) = FormattingOptions(
            tabSize = 4,
            insertSpaces = true,
            trimTrailingWhitespace = trimTrailingWhitespace,
            insertFinalNewline = insertFinalNewline,
        )
    }

    // ========================================================================
    // formatRange()
    // ========================================================================

    @Nested
    @DisplayName("formatRange()")
    inner class FormatRangeTests {
        /**
         * Only lines 1-2 are inside the range, so edits must be confined to those lines.
         * Lines 0 and 3 also have trailing whitespace but must be left untouched.
         */
        @Test
        @DisplayName("should only format within range")
        fun shouldOnlyFormatWithinRange() {
            val source = "line zero   \nline one   \nline two   \nline three   "
            val options =
                FormattingOptions(
                    tabSize = 4,
                    insertSpaces = true,
                    trimTrailingWhitespace = true,
                )
            val range =
                Range(
                    Position(1, 0),
                    Position(2, 0),
                )

            val edits = ts.formatRange(freshUri(), source, range, options)

            assertThat(edits).isNotEmpty
            assertThat(edits).allMatch { it.range.start.line in 1..2 }
        }

        /**
         * `insertFinalNewline` is a whole-document concern. The adapter's `formatContent`
         * skips it when a range is provided, so no `\n` edit should appear.
         */
        @Test
        @DisplayName("should not insert final newline for range")
        fun shouldNotInsertFinalNewlineForRange() {
            val options =
                FormattingOptions(
                    tabSize = 4,
                    insertSpaces = true,
                    insertFinalNewline = true,
                )
            val range =
                Range(
                    Position(0, 0),
                    Position(0, 15),
                )

            val edits = ts.formatRange(freshUri(), "module myapp {}", range, options)

            assertThat(edits).noneMatch { it.newText == "\n" }
        }
    }
}
