package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkRename
import java.nio.file.Path

class XdkMemberInlineTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["1 + 2", "probe() + probe()", "stored + probe()"])
    fun `inline method call preserves bindings evaluation order and its declaration`(expression: String) {
        query(
            """
            module Inline {
                Int stored = 3;
                Int probe() = ++stored;
                private Int helper() { return $expression; }
                Int read() { return 2 * §helper(); }
            }
            """.trimIndent(),
            "Inline private method call",
            "return 2 * ($expression);",
        )
    }

    @Test
    fun `static constant property uses compiler validated value at selected read`() {
        query(
            """
            module Inline {
                private static Int amount = 1 + 2;
                Int read() { return 2 * §amount; }
            }
            """.trimIndent(),
            "Inline constant property",
            "return 2 * (1 + 2);",
        )
    }

    @Test
    fun `caller name capture refuses an otherwise valid body copy`() {
        query(
            """
            module Inline {
                Int stored = 3;
                private Int helper() { return stored; }
                Int read(Int stored) { return §helper(); }
            }
            """.trimIndent(),
            "Inline private method call",
            null,
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Int helper() { return 1; }",
            "private Int helper(Int value = 1) { return value; }",
            "private Int helper() { Int value = 1; return value; }",
            "private Int helper() { return helper(); }",
        ],
    )
    fun `public parameterized multi statement and recursive methods stay unchanged`(method: String) {
        query(
            """
            module Inline {
                $method
                Int read() { return §helper(); }
            }
            """.trimIndent(),
            "Inline private method call",
            null,
        )
    }

    @Test
    fun `instance property reads cannot discard their initialization or getter behavior`() {
        query(
            """
            module Inline {
                private Int amount = 3;
                Int read() { return §amount; }
            }
            """.trimIndent(),
            "Inline constant property",
            null,
        )
    }

    @Test
    fun `static runtime initializer is not repeated at its read`() {
        query(
            """
            module Inline {
                private static Int amount = compute();
                static Int compute() = 3;
                Int read() { return §amount; }
            }
            """.trimIndent(),
            "Inline constant property",
            null,
        )
    }

    private fun query(
        marked: String,
        title: String,
        expected: String?,
    ) {
        CompilerTestSupport.configure()
        val text = marked.replace("§", "")
        val file =
            directory
                .resolve("Inline.x")
                .toFile()
                .canonicalFile
                .apply { writeText(text) }
        val uri = file.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val at = XdkRename.position(text, marked.indexOf('§'))
            val actions = adapter.getCodeActions(uri, Range(at, at), emptyList()).filter { it.title == title }
            if (expected == null) {
                assertThat(actions).isEmpty()
            } else {
                assertThat(actions).hasSize(1)
                val edit = requireNotNull(actions.single().edit)
                assertThat(edit.versioned).isTrue()
                val change = edit.changes.getValue(uri).single()

                fun offset(at: Position) = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
                val changed = text.replaceRange(offset(change.range.start), offset(change.range.end), change.newText)
                assertThat(changed).contains(expected)
                assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            }
            assertThat(file.readText()).isEqualTo(text)
        }
    }
}
