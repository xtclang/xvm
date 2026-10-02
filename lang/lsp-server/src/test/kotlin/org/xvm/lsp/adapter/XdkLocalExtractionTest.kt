package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkLocalExtractionTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["Int|42", "String|\"hello 😀\"", "Char|'x'"])
    fun `extract literal preserves exact source and existing bindings`(example: String) {
        val (type, literal) = example.split('|')
        val text =
            "module Extract {\r\n    $type read() {\r\n        Int extractedValue = 1;\r\n" +
                "        assert extractedValue == 1;\r\n        return $literal;\r\n    }\r\n}"
        query(text, literal) { adapter, uri, actions ->
            val edit = requireNotNull(actions.single { it.kind == CodeAction.CodeActionKind.REFACTOR_EXTRACT }.edit)
            assertThat(edit.versioned).isTrue()
            assertThat(edit.changes.keys).containsExactly(uri)
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(
                changed,
            ).contains("val extractedValue1 = $literal;\r\n        return extractedValue1;", "assert extractedValue == 1;")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            assertThat(directory.resolve("Extract.x").toFile().readText()).isEqualTo(text)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["read()", "1 + 2", "\"abc\".size"])
    fun `whole returned calls and compound expressions preserve evaluation and bindings`(expression: String) {
        val text = "module Extract {\n    Int read() {\n        return $expression;\n    }\n}"
        query(text, expression) { adapter, uri, actions ->
            val action = actions.single { it.kind == CodeAction.CodeActionKind.REFACTOR_EXTRACT }
            assertThat(action.title).isEqualTo("Extract expression to local variable")
            val changed = apply(text, requireNotNull(action.edit).changes.getValue(uri))
            assertThat(
                changed,
            ).isEqualTo(text.replace("return $expression;", "Int extractedValue = $expression;\n        return extractedValue;"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int|input + input|Int input", "Int8|input + 1|Int8 input", "Boolean|input && probe()|Boolean input"])
    fun `extraction preserves written expected type and references inside the moved expression`(example: String) {
        val (type, expression, parameter) = example.split('|')
        val text = "module Extract {\n    Boolean probe() = True;\n    $type read($parameter) {\n        return $expression;\n    }\n}"
        query(text, expression) { adapter, uri, actions ->
            val action = actions.single { it.kind == CodeAction.CodeActionKind.REFACTOR_EXTRACT }
            val changed = apply(text, requireNotNull(action.edit).changes.getValue(uri))
            assertThat(changed).contains("$type extractedValue = $expression;\n        return extractedValue;")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `expected function type and captured parameter survive relocation`() {
        val expression = "() -> input"
        val text = "module Extract {\n    function Int() read(Int input) {\n        return $expression;\n    }\n}"
        query(text, expression) { adapter, uri, actions ->
            val action = actions.single { it.kind == CodeAction.CodeActionKind.REFACTOR_EXTRACT }
            val changed = apply(text, requireNotNull(action.edit).changes.getValue(uri))
            assertThat(changed).contains("function Int() extractedValue = () -> input;\n        return extractedValue;")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `conditional return and partial short circuit selections refuse extraction`() {
        val conditional = "module Extract {\n    conditional Int read(String input) {\n        return input.indexOf('a');\n    }\n}"
        query(conditional, "input.indexOf('a')") { _, _, actions ->
            assertThat(actions.filter { it.kind == CodeAction.CodeActionKind.REFACTOR_EXTRACT }).isEmpty()
        }
        val partial = "module Extract {\n    Boolean read(Boolean first, Boolean second) {\n        return first && second;\n    }\n}"
        query(partial, "first") { _, _, actions ->
            assertThat(actions.filter { it.kind == CodeAction.CodeActionKind.REFACTOR_EXTRACT }).isEmpty()
        }
    }

    @Test
    fun `broken known neighbor prevents refactoring publication`() {
        directory.resolve("Broken.x").toFile().writeText("module Broken { Missing value; }")
        val text = "module Extract {\n    Int read() {\n        return 42;\n    }\n}"
        query(text, "42") { _, _, actions ->
            assertThat(actions.filter { it.kind == CodeAction.CodeActionKind.REFACTOR_EXTRACT }).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["4", "", "42;"])
    fun `partial empty and oversized selections have no extraction`(selected: String) {
        val text = "module Extract {\n    Int read() {\n        return 42;\n    }\n}"
        query(text, selected) { _, _, actions ->
            assertThat(actions.filter { it.kind == CodeAction.CodeActionKind.REFACTOR_EXTRACT }).isEmpty()
        }
    }

    @Test
    fun `same line siblings are not reformatted by extraction`() {
        val text = "module Extract { Int read() { return 42; } }"
        query(text, "42") { _, _, actions ->
            assertThat(actions.filter { it.kind == CodeAction.CodeActionKind.REFACTOR_EXTRACT }).isEmpty()
        }
    }

    private fun query(
        text: String,
        selected: String,
        check: (XdkAdapter, String, List<CodeAction>) -> Unit,
    ) {
        val uri =
            directory
                .resolve("Extract.x")
                .toFile()
                .also { it.writeText(text) }
                .canonicalFile
                .toURI()
                .toString()
        val at = text.positionOf("return $selected", selected)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.compile(uri, text)
            check(adapter, uri, adapter.getCodeActions(uri, Range(at, Position(at.line, at.column + selected.length)), emptyList()))
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
