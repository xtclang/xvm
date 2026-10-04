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

    @ParameterizedTest
    @ValueSource(strings = ["", "static "])
    fun `read only property stub preserves the enclosing result type`(modifier: String) {
        query(
            """
            module App {
                ${modifier}Int read() {
                    return §missing;
                }
            }
            """.trimIndent(),
            "Create read-only property 'missing'",
            "private ${modifier}Int missing.get() { TODO(); }",
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
