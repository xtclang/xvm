package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkLiteralExtractionTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["Int|42", "String|\"hello 😀\"", "Char|'x'"])
    fun `extract literal preserves exact source and existing bindings`(example: String) {
        val (type, literal) = example.split('|')
        val text = "module Extract {\r\n    $type read() {\r\n        Int extractedValue = 1;\r\n        assert extractedValue == 1;\r\n        return $literal;\r\n    }\r\n}"
        query(text, literal) { adapter, uri, actions ->
            val edit = requireNotNull(actions.single { it.kind == CodeAction.CodeActionKind.REFACTOR_EXTRACT }.edit)
            assertThat(edit.versioned).isTrue()
            assertThat(edit.changes.keys).containsExactly(uri)
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(changed).contains("val extractedValue1 = $literal;\r\n        return extractedValue1;", "assert extractedValue == 1;")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            assertThat(directory.resolve("Extract.x").toFile().readText()).isEqualTo(text)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["read()", "1 + 2", "\"abc\".size"])
    fun `calls and compound expressions are outside the literal extraction boundary`(expression: String) {
        val text = "module Extract {\n    Int read() {\n        return $expression;\n    }\n}"
        query(text, expression) { _, _, actions ->
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

    private fun query(text: String, selected: String, check: (XdkAdapter, String, List<CodeAction>) -> Unit) {
        val uri = directory.resolve("Extract.x").toFile().also { it.writeText(text) }.canonicalFile.toURI().toString()
        val at = text.positionOf("return $selected", selected)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.compile(uri, text)
            check(adapter, uri, adapter.getCodeActions(uri, Range(at, Position(at.line, at.column + selected.length)), emptyList()))
        }
    }

    private fun apply(text: String, edits: List<TextEdit>): String =
        edits.sortedWith(compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column })
            .fold(text) { current, edit ->
                fun offset(position: Position) = current.splitToSequence('\n').take(position.line).sumOf { it.length + 1 } + position.column
                current.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
            }
}
