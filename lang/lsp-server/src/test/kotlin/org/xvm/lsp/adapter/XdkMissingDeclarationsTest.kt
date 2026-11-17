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
 * Checks bounded class and property creation from unresolved source uses. Rejects unknown ownership,
 * unsupported constructor requirements and unrelated errors instead of guessing a declaration that merely
 * compiles.
 */
class XdkMissingDeclarationsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `create missing zero argument class repairs the selected constructor use`() {
        query(
            """
            module App {
                Object make() {
                    return new §Widget();
                }
            }
            """.trimIndent(),
            "Create class 'Widget'",
            "class Widget {}",
        )
    }

    @Test
    fun `a missing class is a quick fix for the diagnostic the compiler reports, as an editor asks for it`() {
        CompilerTestSupport.configure()
        // the compiler reports the declaration's type, the statement's first error, and not the new expression
        val text = "module App {\n    void run() {\n        Joker joker = new Joker();\n    }\n}\n"
        val file =
            directory
                .resolve("App.x")
                .toFile()
                .canonicalFile
                .apply { writeText(text) }
        val uri = file.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val diagnostic = adapter.compile(uri, text).diagnostics.single()
            val at = diagnostic.location
            val span = Range(Position(at.startLine, at.startColumn), Position(at.endLine, at.endColumn))
            assertThat(span).isEqualTo(Range(Position(2, 8), Position(2, 13)))
            // LSP4IJ asks for each diagnostic under the caret, over its range, for quick fixes only
            val action =
                adapter
                    .getCodeActionsAsync(uri, span, listOf(diagnostic), setOf(CodeAction.CodeActionKind.QUICKFIX))
                    .get()
                    .single { it.title == "Create class 'Joker'" }
            assertThat(action.diagnostics).containsExactly(diagnostic)
            // a selection of the whole statement names the class twice, and gets one action for it
            val statement = Range(Position(2, 8), Position(2, 34))
            assertThat(adapter.getCodeActions(uri, statement, listOf(diagnostic)).filter { it.title == "Create class 'Joker'" })
                .hasSize(1)
            val changed =
                requireNotNull(action.edit)
                    .changes
                    .getValue(uri)
                    .sortedByDescending { it.range.start.line * text.length + it.range.start.column }
                    .fold(text) { current, change ->
                        fun offset(at: Position) = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
                        current.replaceRange(offset(change.range.start), offset(change.range.end), change.newText)
                    }
            assertThat(changed).contains("class Joker {}")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "static "])
    fun `read only property repairs instance access but never invents a static constant value`(modifier: String) {
        query(
            """
            module App {
                ${modifier}Int read() {
                    return §missing;
                }
            }
            """.trimIndent(),
            "Create read-only property 'missing'",
            if (modifier.isEmpty()) "private Int missing.get() { TODO(); }" else null,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["new §Widget(Int:1)", "new package.§Widget()", "new §Widget<Int>()"])
    fun `constructor requirements and unknown destination owners are not guessed`(expression: String) {
        query(
            """
            module App {
                Object make() {
                    return $expression;
                }
            }
            """.trimIndent(),
            "Create class 'Widget'",
            null,
        )
    }

    @Test
    fun `neighbor error prevents publication of a new declaration`() {
        query(
            """
            module App {
                Int broken() = "wrong";
                Object make() {
                    return new §Widget();
                }
            }
            """.trimIndent(),
            "Create class 'Widget'",
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
                .resolve("App.x")
                .toFile()
                .canonicalFile
                .apply { writeText(text) }
        val uri = file.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val diagnostics = adapter.compile(uri, text).diagnostics
            assertThat(diagnostics).isNotEmpty().noneMatch { it.code == "EMB-5" }
            val at = XdkRename.position(text, marked.indexOf('§'))
            val actions = adapter.getCodeActions(uri, Range(at, at), diagnostics).filter { it.title == title }
            if (expected == null) {
                assertThat(actions).isEmpty()
            } else {
                assertThat(actions).describedAs("Diagnostics: %s", diagnostics).hasSize(1)
                val edit = requireNotNull(actions.single().edit)
                assertThat(edit.versioned).isTrue()
                val changed =
                    edit.changes
                        .getValue(uri)
                        .sortedByDescending { it.range.start.line * text.length + it.range.start.column }
                        .fold(text) { current, change ->
                            fun offset(at: Position) = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
                            current.replaceRange(offset(change.range.start), offset(change.range.end), change.newText)
                        }
                assertThat(changed).contains(expected)
                assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            }
            assertThat(file.readText()).isEqualTo(text)
        }
    }
}
