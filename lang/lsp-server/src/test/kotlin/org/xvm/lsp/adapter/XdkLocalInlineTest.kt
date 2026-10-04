package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkLocalInlineTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Int|input + probe()|Int input", "Boolean|input && probe() == 1|Boolean input",
            "Int8|input + 1|Int8 input", "function Int()|() -> input|Int input", "String|\"hello 😀\"|Int input",
        ],
    )
    fun `inline adjacent returned local preserves expected type bindings and single evaluation`(example: String) {
        val (type, expression, parameter) = example.split('|')
        val text =
            "module Inline {\r\n    Int probe() = 1;\r\n    $type read($parameter) {\r\n" +
                "        $type value = $expression;\r\n        return value;\r\n    }\r\n}"
        query(text) { adapter, uri, actions ->
            val action = actions.single { it.kind == CodeAction.CodeActionKind.REFACTOR_INLINE }
            assertThat(action.title).isEqualTo("Inline returned local variable")
            val edit = requireNotNull(action.edit)
            assertThat(edit.versioned).isTrue()
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(changed).isEqualTo(text.replace("$type value = $expression;\r\n        return value;", "return $expression;"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            assertThat(directory.resolve("Inline.x").toFile().readText()).isEqualTo(text)
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Int value = probe();\n        assert value > 0;\n        return value;",
            "Int value = probe();\n        value = 3;\n        return value;",
            "Int value = probe();\n        return value + value;",
            "Int value = probe(); // retained comment\n        return value;",
            "Int value = probe(); return value;",
            "Int8 value = 1;\n        return value;",
            "val value = probe();\n        return value;",
            "Int value = probe();\n        probe();\n        return value;",
        ],
    )
    fun `refuse repeated reads writes changed expected type intervening statements and comments`(body: String) {
        val text = "module Inline {\n    Int probe() = 1;\n    Int read() {\n        $body\n    }\n}"
        query(text) { _, _, actions ->
            assertThat(actions.filter { it.kind == CodeAction.CodeActionKind.REFACTOR_INLINE }).isEmpty()
        }
    }

    @Test
    fun `broken graph prevents inline publication`() {
        directory.resolve("Broken.x").toFile().writeText("module Broken { Missing value; }")
        query("module Inline {\n    Int read() {\n        Int value = 1;\n        return value;\n    }\n}") { _, _, actions ->
            assertThat(actions.filter { it.kind == CodeAction.CodeActionKind.REFACTOR_INLINE }).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["value + probe()", "probe() + value", "accept(value)"])
    fun `constant local can move across statements into a nested expression without changing type`(use: String) {
        val text =
            """
            module Inline {
                Int probe() = 1;
                Int accept(Int input) = input;
                Int read() {
                    Int value = 1 + 2;
                    probe();
                    return $use;
                }
            }
            """.trimIndent()
        query(text, select = "Int value") { adapter, uri, actions ->
            val action = actions.single { it.title == "Inline constant local variable" }
            val changed = apply(text, requireNotNull(action.edit).changes.getValue(uri))
            assertThat(changed).doesNotContain("Int value =").contains("return ${use.replace("value", "(1 + 2)")};")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Int8 value = 1;",
            "Int value = /* keep me */ 1;",
            "Int value = 1; // keep me",
            "Int value = probe();",
        ],
    )
    fun `wider inline refuses contextual widening lost comments and deferred effects`(declaration: String) {
        val text =
            """
            module Inline {
                Int probe() = 1;
                Int read() {
                    $declaration
                    probe();
                    return value + 2;
                }
            }
            """.trimIndent()
        query(text, select = declaration) { _, _, actions ->
            assertThat(actions.filter { it.kind == CodeAction.CodeActionKind.REFACTOR_INLINE }).isEmpty()
        }
    }

    private fun query(
        text: String,
        select: String = "return value",
        check: (XdkAdapter, String, List<CodeAction>) -> Unit,
    ) {
        val uri =
            directory
                .resolve("Inline.x")
                .toFile()
                .also { it.writeText(text) }
                .canonicalFile
                .toURI()
                .toString()
        val at = text.positionOf(select, "value")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.compile(uri, text)
            check(adapter, uri, adapter.getCodeActions(uri, Range(at, at), emptyList()))
        }
    }

    private fun apply(
        text: String,
        edits: List<TextEdit>,
    ): String =
        edits
            .sortedWith(compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column })
            .fold(text) { current, edit ->
                fun offset(position: Position) = current.splitToSequence('\n').take(position.line).sumOf { it.length + 1 } + position.column
                current.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
            }
}
