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
