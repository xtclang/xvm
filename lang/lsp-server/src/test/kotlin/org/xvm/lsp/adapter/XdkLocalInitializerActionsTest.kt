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

class XdkLocalInitializerActionsTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["Int|input + probe()", "Int8|input + 1", "function Int()|() -> input", "Int[]|[input, probe()]"])
    fun `extract an entire initializer retains its contextual type and evaluation order`(example: String) {
        val (type, expression) = example.split('|')
        val parameter = if (type == "Int8") "Int8" else "Int"
        val marked =
            """
            module Extract {
                Int probe() = 1;
                $type read($parameter input) {
                    $type result = §$expression§;
                    return result;
                }
            }
            """.trimIndent()
        query(marked) { adapter, uri, text, actions ->
            val action = actions.single { it.title.endsWith("to local variable") }
            val changed = apply(text, requireNotNull(action.edit).changes.getValue(uri))
            assertThat(changed).isEqualTo(
                text.replace(
                    "$type result = $expression;",
                    "$type extractedValue = $expression;\n        $type result = extractedValue;",
                ),
            )
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `extraction preserves initializer comments CRLF and fresh local naming`() {
        val marked =
            """
            module Extract {
                Int read(Int extractedValue) {
                    Int result = §extractedValue /* 😀 */ + 1§;
                    return result;
                }
            }
            """.trimIndent().replace("\n", "\r\n")
        query(marked) { adapter, uri, text, actions ->
            val action = actions.single { it.title.endsWith("to local variable") }
            val changed = apply(text, requireNotNull(action.edit).changes.getValue(uri))
            assertThat(changed).contains("Int extractedValue1 = extractedValue /* 😀 */ + 1;\r\n        Int result = extractedValue1;")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "val result = §input + 1§;", "Int result = §input§ + 1;",
            "Int result = input + 1; Int other = §input + 2§;", "Int result /* declaration comment */ = §input + 1§;",
        ],
    )
    fun `extraction refuses inference partial selections same line siblings and declaration annotations`(body: String) {
        query(
            """
            module Extract {
                Int read(Int input) {
                    $body
                    return result;
                }
            }
            """.trimIndent(),
        ) { _, _, _, actions ->
            assertThat(actions.filter { it.title.endsWith("to local variable") }).isEmpty()
        }
    }

    @Test
    fun `property initializer and conditional declaration are not local extraction sites`() {
        listOf(
            """
            module Extract {
                Int value = §1 + 2§;
            }
            """.trimIndent(),
            """
            module Extract {
                Int read(String input) {
                    if (Int result := §input.indexOf('a')§) {
                        return result;
                    }
                    return -1;
                }
            }
            """.trimIndent(),
        ).forEach { marked ->
            query(marked) { _, _, _, actions ->
                assertThat(actions.filter { it.title.endsWith("to local variable") }).isEmpty()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int|input + probe()", "Int8|input + 1", "function Int()|() -> input", "Int[]|[input, probe()]"])
    fun `inline into adjacent typed initializer keeps expected type and exactly one evaluation`(example: String) {
        val (type, expression) = example.split('|')
        val parameter = if (type == "Int8") "Int8" else "Int"
        query(
            """
            module Extract {
                Int probe() = 1;
                $type read($parameter input) {
                    $type value = $expression;
                    $type result = §value§;
                    return result;
                }
            }
            """.trimIndent(),
        ) { adapter, uri, text, actions ->
            val action = actions.single { it.kind == CodeAction.CodeActionKind.REFACTOR_INLINE }
            assertThat(action.title).isEqualTo("Inline local variable into initializer")
            val changed = apply(text, requireNotNull(action.edit).changes.getValue(uri))
            assertThat(changed).isEqualTo(
                text.replace(
                    "$type value = $expression;\n        $type result = value;",
                    "$type result = $expression;",
                ),
            )
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "val result = §value§;", "Int result = §value§ + value;",
            "Int result = probe() + §value§;", "probe();\n        Int result = §value§;",
            "// keep explanation\n        Int result = §value§;",
            "Int8 result = §value§.toInt8();", "function Int() result = () -> §value§;",
        ],
    )
    fun `inline refuses changed contexts multiple uses delayed evaluation intervening statements and comments`(use: String) {
        query(
            """
            module Extract {
                Int probe() = 1;
                void read() {
                    Int value = probe();
                    $use
                }
            }
            """.trimIndent(),
        ) { _, _, _, actions ->
            assertThat(actions.filter { it.kind == CodeAction.CodeActionKind.REFACTOR_INLINE }).isEmpty()
        }
    }

    @Test
    fun `different written destination type refuses inline even when conversion would compile`() {
        query(
            """
            module Extract {
                Int read() {
                    Int8 value = 1;
                    Int result = §value§;
                    return result;
                }
            }
            """.trimIndent(),
        ) { _, _, _, actions ->
            assertThat(actions.filter { it.kind == CodeAction.CodeActionKind.REFACTOR_INLINE }).isEmpty()
        }
    }

    private fun query(
        marked: String,
        check: (XdkAdapter, String, String, List<CodeAction>) -> Unit,
    ) {
        CompilerTestSupport.configure()
        val start = marked.indexOf('§')
        val end = marked.lastIndexOf('§').let { if (it == start) it else it - 1 }
        val text = marked.replace("§", "")
        val file =
            directory
                .resolve("Extract.x")
                .toFile()
                .apply { writeText(text) }
                .canonicalFile
        val uri = file.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val range = Range(XdkRename.position(text, start), XdkRename.position(text, end))
            check(adapter, uri, text, adapter.getCodeActions(uri, range, emptyList()))
        }
    }

    private fun apply(
        text: String,
        edits: List<TextEdit>,
    ): String =
        edits
            .sortedWith(
                compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column },
            ).fold(text) { current, edit ->
                fun offset(position: Position) =
                    requireNotNull(
                        XdkRename.offset(text, SemanticModel.Position(position.line, position.column)),
                    )
                current.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
            }
}
