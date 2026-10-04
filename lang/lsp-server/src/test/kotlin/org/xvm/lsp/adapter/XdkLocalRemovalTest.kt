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

class XdkLocalRemovalTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["Int|1 + 2", "Int8|42", "Boolean|True", "String|\"hello 😀 // literal\"", "String?|Null"])
    fun `remove unused compiler constants without changing surrounding behavior`(example: String) {
        val (type, expression) = example.split('|')
        query("""
            module Remove {
                Int read(Int input) {
                    $type §unused = $expression;
                    return input + 1;
                }
            }
        """.trimIndent()) { adapter, uri, text, action ->
            val changed = apply(text, requireNotNull(action).changes.getValue(uri))
            assertThat(changed).isEqualTo(text.replace("        $type unused = $expression;\n", ""))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `removal retains leading and trailing comments CRLF and unrelated same named locals`() {
        query("""
            module Remove {
                Int read() {
                    // retain context
                    Int §unused = 42; // retain explanation
                    return 1;
                }
                Int other() {
                    Int unused = 2;
                    return unused;
                }
            }
        """.trimIndent().replace("\n", "\r\n")) { adapter, uri, text, action ->
            val changed = apply(text, requireNotNull(action).changes.getValue(uri))
            assertThat(changed).isEqualTo(text.replace("Int unused = 42;", ""))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "Int §unused = probe();", "Boolean §unused = probe() == 1 && False;",
        "Int §unused = input;", "@Volatile Int §unused = 42;",
        "Int §unused = 1 /* retain */ + 2;", "Int §unused = 42; unused = 3;",
        "Int §unused = 42; assert unused == 42;", "val §unused = 42;",
    ])
    fun `refuse runtime evaluation storage annotations internal comments writes reads and inferred locals`(declaration: String) {
        query("""
            module Remove {
                Int probe() = 1;
                Int read(Int input) {
                    $declaration
                    return input;
                }
            }
        """.trimIndent()) { _, _, _, action -> assertThat(action).isNull() }
    }

    @Test
    fun `broken known graph prevents removal even for an unused constant`() {
        directory.resolve("Broken.x").toFile().writeText("module Broken { Missing value; }")
        query("""
            module Remove {
                void read() {
                    Int §unused = 42;
                }
            }
        """.trimIndent()) { _, _, _, action -> assertThat(action).isNull() }
    }

    private fun query(marked: String, check: (XdkAdapter, String, String, WorkspaceEdit?) -> Unit) {
        CompilerTestSupport.configure()
        val at = marked.indexOf('§')
        val text = marked.replace("§", "")
        val file = directory.resolve("Remove.x").toFile().apply { writeText(text) }.canonicalFile
        val uri = file.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val position = XdkRename.position(text, at)
            val action = adapter.getCodeActions(uri, Range(position, position), emptyList()).singleOrNull { it.title == "Remove unused local variable" }
            check(adapter, uri, text, action?.edit)
            assertThat(file.readText()).isEqualTo(text)
        }
    }

    private fun apply(text: String, edits: List<TextEdit>): String = edits.sortedWith(
        compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column },
    ).fold(text) { current, edit ->
        fun offset(position: Position) = requireNotNull(XdkRename.offset(text, SemanticModel.Position(position.line, position.column)))
        current.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
    }
}
