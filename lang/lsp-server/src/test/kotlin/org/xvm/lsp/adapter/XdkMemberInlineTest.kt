package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
        inlined(
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
        inlined(
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
        inlined(
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
        inlined(
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
        inlined(
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
        inlined(
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
        inlined(
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
            everyUse = true,
        )
    }

    @Test
    fun `every read of a constant is inlined and the constant removed, also from its declaration`() {
        inlined(
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
            everyUse = true,
        )
    }

    @Test
    fun `a member with one use is inlined and removed`() {
        inlined(
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
            everyUse = true,
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
        inlined(
            """
            module Inline {
                Int stored = 3;
                private Int helper() { return stored; }
                $uses
            }
            """.trimIndent(),
            "Inline and remove private method 'helper'",
            null,
            everyUse = true,
        )
    }

    /**
     * Offers the action named [title] at the `§` in [marked], or with no [expected] checks that it is not offered. Its single
     * edit, or [everyUse] every edit, must yield a text that contains [expected], or with [everyUse] is [expected], and compiles.
     */
    private fun inlined(
        marked: String,
        title: String,
        expected: String?,
        everyUse: Boolean = false,
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
                val edits = edit.changes.getValue(uri)
                val changed = apply(text, if (everyUse) edits else listOf(edits.single()))
                if (everyUse) assertThat(changed).isEqualTo(expected) else assertThat(changed).contains(expected)
                assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            }
            assertThat(file.readText()).isEqualTo(text)
        }
    }
}
