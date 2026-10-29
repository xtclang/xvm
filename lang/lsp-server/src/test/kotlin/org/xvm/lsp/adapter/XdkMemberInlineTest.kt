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

/**
 * Checks bounded private-member inlining while retaining the declaration. Preserves evaluation and bindings,
 * and refuses capture, recursive bodies and unsupported runtime initializer behavior.
 */
class XdkMemberInlineTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["1 + 2", "probe() + probe()", "stored + probe()", "stored & probe()"])
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

    @Test
    fun `every call of a private method is inlined and the method removed`() {
        everyUse(
            """
            module Inline {
                Int stored = 3;
                private Int helper() { return stored + 1; }
                Int read() { return 2 * §helper(); }
                Int twice() = helper() + helper();
            }
            """.trimIndent(),
            "Inline and remove private method 'helper'",
            """
            module Inline {
                Int stored = 3;
                Int read() { return 2 * (stored + 1); }
                Int twice() = (stored + 1) + (stored + 1);
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `every read of a constant is inlined and the constant removed, also from its declaration`() {
        everyUse(
            """
            module Inline {
                private static Int §amount = 1 + 2;
                Int read() { return 2 * amount; }
                Int more() = amount + amount;
            }
            """.trimIndent(),
            "Inline and remove constant property 'amount'",
            """
            module Inline {
                Int read() { return 2 * (1 + 2); }
                Int more() = (1 + 2) + (1 + 2);
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `a member with one use is inlined and removed`() {
        everyUse(
            """
            module Inline {
                private Int helper() { return 7; }
                Int read() = §helper();
            }
            """.trimIndent(),
            "Inline and remove private method 'helper'",
            """
            module Inline {
                Int read() = (7);
            }
            """.trimIndent(),
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            // a use in a lambda can have another receiver or captures
            "Int read() { function Int() f = () -> helper(); return §helper() + f(); }",
            // a method value is not a call that a copy can replace
            "Int read() { function Int() f = helper; return §helper() + f(); }",
            // the expression names a parameter of the caller's
            "Int read(Int stored) = §helper();",
        ],
    )
    fun `members with a use that a copy cannot replace stay unchanged`(uses: String) {
        everyUse(
            """
            module Inline {
                Int stored = 3;
                private Int helper() { return stored; }
                $uses
            }
            """.trimIndent(),
            "Inline and remove private method 'helper'",
            null,
        )
    }

    /** Applies the selected action's every edit and compares the whole result. */
    private fun everyUse(
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

                fun offset(at: Position) = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
                val changed =
                    edit.changes
                        .getValue(uri)
                        .sortedByDescending { offset(it.range.start) }
                        .fold(
                            text,
                        ) { value, change -> value.replaceRange(offset(change.range.start), offset(change.range.end), change.newText) }
                assertThat(changed).isEqualTo(expected)
                assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            }
            assertThat(file.readText()).isEqualTo(text)
        }
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
