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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for on-type formatting (`textDocument/onTypeFormatting`) in [TreeSitterAdapter].
 *
 * Tests parse XTC snippets via the tree-sitter native parser, then call
 * [TreeSitterAdapter.onTypeFormatting] with a trigger character and cursor position,
 * asserting that the returned [XtcCompilerAdapter.TextEdit] list produces the correct
 * indentation.
 *
 * All tests are skipped (not failed) when the tree-sitter native library is unavailable.
 */
@DisplayName("OnTypeFormatting")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OnTypeFormattingTest {
    private var adapter: TreeSitterAdapter? = null
    private val uriCounter = AtomicInteger(0)

    private val ts: TreeSitterAdapter get() = adapter!!

    private fun freshUri(): String = "file:///fmt${uriCounter.incrementAndGet()}.x"

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

    /**
     * Helper: compile source, then call onTypeFormatting.
     * Returns the desired indent (new text length) or -1 if no edit was returned.
     */
    private fun formatAfterTrigger(
        source: String,
        line: Int,
        column: Int,
        ch: String,
    ): Int {
        val uri = freshUri()
        ts.compile(uri, source)
        val edits = ts.onTypeFormatting(uri, line, column, ch, defaultOptions)
        return if (edits.isEmpty()) -1 else edits.first().newText.length
    }

    /**
     * Helper: compile source, call onTypeFormatting, return the raw edit list.
     */
    private fun formatEdits(
        source: String,
        line: Int,
        column: Int,
        ch: String,
    ): List<XtcCompilerAdapter.TextEdit> {
        val uri = freshUri()
        ts.compile(uri, source)
        return ts.onTypeFormatting(uri, line, column, ch, defaultOptions)
    }

    // ========================================================================
    // Enter key (\n) trigger
    // ========================================================================

    @Nested
    @DisplayName("Enter key (\\n)")
    inner class EnterTests {
        @Test
        @DisplayName("after '{' at top-level module -> indent 4")
        fun afterOpenBraceTopLevel() {
            val source = "module myapp {\n}"
            // Cursor is on line 1 (the new line after Enter), column 0
            val indent = formatAfterTrigger(source, line = 1, column = 0, ch = "\n")
            assertThat(indent).isEqualTo(4)
        }

        @Test
        @DisplayName("after '{' in nested class -> indent 8")
        fun afterOpenBraceNestedClass() {
            val source =
                """
                module myapp {
                    class Person {

                    }
                }
                """.trimIndent()
            // Line 2 is the blank line inside Person's body, after pressing Enter on line 1
            val indent = formatAfterTrigger(source, line = 2, column = 0, ch = "\n")
            assertThat(indent).isEqualTo(8)
        }

        @Test
        @DisplayName("after '{' in method -> indent 8")
        fun afterOpenBraceInMethod() {
            val source =
                """
                module myapp {
                    void foo() {

                    }
                }
                """.trimIndent()
            val indent = formatAfterTrigger(source, line = 2, column = 0, ch = "\n")
            assertThat(indent).isEqualTo(8)
        }

        @Test
        @DisplayName("after '{' in if statement -> indent 12")
        fun afterOpenBraceInIf() {
            val source =
                """
                module myapp {
                    void foo() {
                        if (True) {

                        }
                    }
                }
                """.trimIndent()
            val indent = formatAfterTrigger(source, line = 3, column = 0, ch = "\n")
            assertThat(indent).isEqualTo(12)
        }

        @Test
        @DisplayName("after normal statement -> maintain indent")
        fun afterNormalStatement() {
            val source =
                """
                module myapp {
                    class Person {
                        String name;

                    }
                }
                """.trimIndent()
            // After "String name;" on line 2, cursor is on line 3
            val indent = formatAfterTrigger(source, line = 3, column = 0, ch = "\n")
            assertThat(indent).isEqualTo(8)
        }

        @Test
        @DisplayName("after '}' -> maintain brace indent level")
        fun afterClosingBrace() {
            val source =
                """
                module myapp {
                    class Person {
                    }

                }
                """.trimIndent()
            // After "}" on line 2, cursor is on line 3
            val indent = formatAfterTrigger(source, line = 3, column = 0, ch = "\n")
            assertThat(indent).isEqualTo(4)
        }

        @Test
        @DisplayName("after continuation line ending with '{' -> body indent from declaration")
        fun afterContinuationWithBrace() {
            val source =
                """
                module myapp {
                    const Foo
                        implements Closeable {

                    }
                }
                """.trimIndent()
            // "implements Closeable {" is a continuation ending with '{'
            // Body should be at declaration indent (4) + indentSize (4) = 8
            val indent = formatAfterTrigger(source, line = 3, column = 0, ch = "\n")
            assertThat(indent).isEqualTo(8)
        }

        @Test
        @DisplayName("no edit when tree is missing")
        fun noTreeReturnsEmpty() {
            val edits =
                ts.onTypeFormatting(
                    "file:///nonexistent.x",
                    line = 1,
                    column = 0,
                    ch = "\n",
                    options = defaultOptions,
                )
            assertThat(edits).isEmpty()
        }

        @Test
        @DisplayName("no edit when indent already correct")
        fun noEditWhenAlreadyCorrect() {
            // Source where line 1 already has the correct 4-space indent
            val source = "module myapp {\n    \n}"
            val edits = formatEdits(source, line = 1, column = 4, ch = "\n")
            assertThat(edits).isEmpty()
        }
    }

    // ========================================================================
    // Closing brace (}) trigger
    // ========================================================================

    @Nested
    @DisplayName("Closing brace (})")
    inner class CloseBraceTests {
        @Test
        @DisplayName("close top-level module -> indent 0")
        fun closeModuleBody() {
            val source = "module myapp {\n    \n    }"
            // '}' typed at line 2, column 4 (after the 4 spaces); should outdent to 0
            val indent = formatAfterTrigger(source, line = 2, column = 4, ch = "}")
            assertThat(indent).isEqualTo(0)
        }

        @Test
        @DisplayName("close nested class -> indent 4")
        fun closeNestedClass() {
            val source =
                """
                module myapp {
                    class Person {
                        }
                }
                """.trimIndent()
            // '}' on line 2 has 8 spaces but should be at 4
            val indent = formatAfterTrigger(source, line = 2, column = 8, ch = "}")
            assertThat(indent).isEqualTo(4)
        }

        @Test
        @DisplayName("no edit when brace already at correct indent")
        fun noEditWhenCorrect() {
            val source = "module myapp {\n}"
            val edits = formatEdits(source, line = 1, column = 0, ch = "}")
            assertThat(edits).isEmpty()
        }
    }

    // ========================================================================
    // Doc/block comment continuation
    // ========================================================================

    @Nested
    @DisplayName("Comment continuation")
    inner class CommentContinuationTests {
        @Test
        @DisplayName("Enter after '/**' line inserts ' * ' continuation")
        fun enterAfterDocCommentOpening() {
            // Source where the comment has content — NOT the auto-close skeleton case
            val source = "/**\n * first line\n */"
            // Pressing Enter after "/**" line — cursor is on line 1 (the " * first line")
            // We need a blank new line AFTER the opening, so simulate:
            val source2 = "/**\n\n * rest\n */"
            val edits = formatEdits(source2, line = 1, column = 0, ch = "\n")
            assertThat(edits).isNotEmpty
            assertThat(edits.first().newText).isEqualTo(" * ")
        }

        @Test
        @DisplayName("Enter inside doc comment inserts ' * ' continuation")
        fun enterInsideDocComment() {
            val source = "/**\n * existing line\n */"
            val edits = formatEdits(source, line = 2, column = 0, ch = "\n")
            // Should NOT insert continuation on the "*/" closing line
            // Let's try pressing Enter after "existing line" (cursor on line 1)
            val edits2 = formatEdits(source, line = 1, column = 0, ch = "\n")
            assertThat(edits2).isNotEmpty
            assertThat(edits2.first().newText).isEqualTo(" * ")
        }

        @Test
        @DisplayName("Enter after '*/' does NOT insert continuation")
        fun noContAfterClosingComment() {
            val source = "/**\n * text\n */\n"
            // Line 3 is after "*/", Enter here should not get comment continuation
            val edits = formatEdits(source, line = 3, column = 0, ch = "\n")
            // No comment continuation — normal indent behavior
            assertThat(edits.firstOrNull()?.newText ?: "").doesNotContain("*")
        }

        @Test
        @DisplayName("indented doc comment aligns ' * ' correctly")
        fun indentedDocComment() {
            val source = "module myapp {\n    /**\n\n     */\n}"
            // Enter on line 2 (blank line inside indented doc comment)
            val edits = formatEdits(source, line = 2, column = 0, ch = "\n")
            assertThat(edits).isNotEmpty
            // Comment starts at indent 4, so continuation is "    " + " * " = "     * "
            assertThat(edits.first().newText).isEqualTo("     * ")
        }

        @Test
        @DisplayName("block comment gets continuation too")
        fun blockCommentContinuation() {
            val source = "/*\n\n */"
            val edits = formatEdits(source, line = 1, column = 0, ch = "\n")
            assertThat(edits).isNotEmpty
            assertThat(edits.first().newText).isEqualTo(" * ")
        }
    }

    // ========================================================================
    // Doc comment skeleton
    // ========================================================================

    @Nested
    @DisplayName("Doc comment skeleton")
    inner class DocCommentSkeletonTests {
        @Test
        @DisplayName("Enter after '/**' with auto-closed '*/' creates skeleton")
        fun docCommentSkeleton() {
            // Simulates typing /** then Enter with auto-closed */
            val source = "/**\n*/"
            val edits = formatEdits(source, line = 1, column = 0, ch = "\n")
            assertThat(edits).isNotEmpty
            // Should insert " * \n */" — cursor line gets " * " and closing preserved
            assertThat(edits.first().newText).isEqualTo(" * \n */")
        }

        @Test
        @DisplayName("indented doc comment skeleton")
        fun indentedDocCommentSkeleton() {
            val source = "module myapp {\n    /**\n    */\n}"
            val edits = formatEdits(source, line = 2, column = 0, ch = "\n")
            assertThat(edits).isNotEmpty
            assertThat(edits.first().newText).isEqualTo("     * \n     */")
        }
    }

    // ========================================================================
    // Closing paren ()) trigger
    // ========================================================================

    @Nested
    @DisplayName("Closing paren ())")
    inner class CloseParenTests {
        @Test
        @DisplayName("outdent ')' to match opening '(' line")
        fun closeParenOutdent() {
            val source =
                """
                module myapp {
                    void foo(
                        String name,
                            )
                }
                """.trimIndent()
            // ')' on line 3 is indented too much; should match line 1 (foo declaration)
            val indent = formatAfterTrigger(source, line = 3, column = 12, ch = ")")
            assertThat(indent)
                .describedAs("')' should align with the line containing '('")
                .isEqualTo(4)
        }

        @Test
        @DisplayName("no edit when ')' already at correct indent")
        fun noEditWhenCorrect() {
            val source =
                """
                module myapp {
                    void foo(
                        String name,
                    )
                }
                """.trimIndent()
            val edits = formatEdits(source, line = 3, column = 4, ch = ")")
            assertThat(edits).isEmpty()
        }
    }

    // ========================================================================
    // Arrow (->) indent
    // ========================================================================

    @Nested
    @DisplayName("Arrow (->)")
    inner class ArrowTests {
        @Test
        @DisplayName("Enter after '->' indents body")
        fun enterAfterArrow() {
            val source =
                """
                module myapp {
                    void foo() {
                        list.forEach(e ->

                        );
                    }
                }
                """.trimIndent()
            val indent = formatAfterTrigger(source, line = 3, column = 0, ch = "\n")
            assertThat(indent)
                .describedAs("body after '->' should indent +4 from arrow line")
                .isEqualTo(12)
        }
    }

    // ========================================================================
    // Semicolon (;) trigger
    // ========================================================================

    @Nested
    @DisplayName("Semicolon (;)")
    inner class SemicolonTests {
        @Test
        @DisplayName("returns empty (not yet implemented)")
        fun semicolonIsNoop() {
            val source =
                """
                module myapp {
                    Int x = 1;
                }
                """.trimIndent()
            val edits = formatEdits(source, line = 1, column = 14, ch = ";")
            assertThat(edits).isEmpty()
        }
    }

    // ========================================================================
    // Configuration
    // ========================================================================

    @Nested
    @DisplayName("FormattingConfig")
    inner class ConfigTests {
        @Test
        @DisplayName("respects custom tabSize from editor")
        fun respectsCustomTabSize() {
            val source = "module myapp {\n}"
            val uri = freshUri()
            ts.compile(uri, source)

            val customOptions =
                XtcCompilerAdapter.FormattingOptions(
                    tabSize = 2,
                    insertSpaces = true,
                )
            val edits = ts.onTypeFormatting(uri, line = 1, column = 0, ch = "\n", options = customOptions)
            assertThat(edits).isNotEmpty
            assertThat(edits.first().newText.length).isEqualTo(2)
        }

        @Test
        @DisplayName("default config has standard XTC values")
        fun defaultConfig() {
            val config = XtcFormattingConfig.DEFAULT
            assertThat(config.indentSize).isEqualTo(4)
            assertThat(config.continuationIndentSize).isEqualTo(8)
            assertThat(config.insertSpaces).isTrue()
            assertThat(config.maxLineWidth).isEqualTo(120)
        }

        @Test
        @DisplayName("fromLspOptions applies editor tabSize")
        fun fromLspOptions() {
            val options = XtcCompilerAdapter.FormattingOptions(tabSize = 3, insertSpaces = true)
            val config = XtcFormattingConfig.fromLspOptions(options)
            assertThat(config.indentSize).isEqualTo(3)
            assertThat(config.insertSpaces).isTrue()
        }

        @Test
        @DisplayName("fromLspOptions ignores tabSize when not using spaces")
        fun fromLspOptionsNoSpaces() {
            val options = XtcCompilerAdapter.FormattingOptions(tabSize = 8, insertSpaces = false)
            val config = XtcFormattingConfig.fromLspOptions(options)
            // Should use default indent size, not the tab size
            assertThat(config.indentSize).isEqualTo(4)
            assertThat(config.insertSpaces).isFalse()
        }
    }
}
