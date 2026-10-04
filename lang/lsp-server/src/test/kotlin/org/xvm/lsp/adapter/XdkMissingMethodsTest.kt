package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkRename
import java.nio.file.Path

class XdkMissingMethodsTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @CsvSource(value = ["Int;Int64", "String;String", "List<Int>;List<Int64>", "(Int | String);(Int64 | String)"], delimiter = ';')
    fun `resolved parameter and return types produce a private method`(
        type: String,
        rendered: String,
    ) {
        query(
            """
            module Missing {
                $type read($type value) {
                    return §missing(value);
                }
            }
            """.trimIndent(),
        ) { changed ->
            assertThat(changed).contains("private $rendered missing($rendered arg1)", "TODO();", "return missing(value);")
        }
    }

    @Test
    fun `statement call creates void method with no arguments`() {
        query(
            """
            module Missing {
                void run() {
                    §missing();
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private void missing()") }
    }

    @Test
    fun `static typed return preserves CRLF and unicode`() {
        query(
            """
            module Missing {
                // 😀 keep the call's UTF-16 position
                static Int read(Int value) {
                    return §missing(value);
                }
            }
            """.trimIndent().replace("\n", "\r\n"),
        ) { assertThat(it).contains("private static Int64 missing(Int64 arg1) {\r\n        TODO();\r\n") }
    }

    @Test
    fun `class owner and multiple returns preserve their written contract`() {
        query(
            """
            module Missing {
                class Box {
                    (Int, String) read(Int value, String label) {
                        return §missing(value, label, value);
                    }
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("        private (Int64, String) missing(Int64 arg1, String arg2, Int64 arg3)") }
    }

    @Test
    fun `literal types come from the compiler without exposing numeric literal internals`() {
        query(
            """
            module Missing {
                void run() {
                    §missing("hello", 'x', Int8:7);
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private void missing(String arg1, Char arg2, Int8 arg3)") }
    }

    @Test
    fun `one declaration repairs repeated calls with matching signatures`() {
        query(
            """
            module Missing {
                Int read(Int value) {
                    return §missing(value);
                }
                Int other(Int value) {
                    return missing(value);
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private Int64 missing(Int64 arg1)") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["1", "1.5", "True", "Null", "value + 1", "value = value", "() -> value", "[value]"])
    fun `unproven argument types do not invent a signature`(argument: String) {
        refused(
            """
            module Missing {
                Int read(Int value) {
                    return §missing($argument);
                }
            }
            """.trimIndent(),
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Int other = value; return §missing(other);",
            "return 1 + §missing(value);",
            "Int result = §missing(value); return result;",
            "return this.§missing(value);",
            "return §missing<Int>(value);",
        ],
    )
    fun `unsupported receiver argument and expected type contexts remain refusals`(body: String) {
        refused(
            """
            module Missing {
                Int read(Int value) {
                    $body
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `existing overload is not mistaken for an absent method`() {
        refused(
            """
            module Missing {
                Int missing(Int value) = value;
                Int read(String text) {
                    return §missing(text);
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `method formals cannot escape into a sibling signature`() {
        refused(
            """
            module Missing {
                <Value> Value read(Value value) {
                    return §missing(value);
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `conditional return is not flattened into ordinary returns`() {
        refused(
            """
            module Missing {
                conditional Int read(Int value) {
                    return §missing(value);
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `lambda cannot acquire a method with its enclosing method return type`() {
        refused(
            """
            module Missing {
                Int read(Int value) {
                    function Int () compute = () -> { return §missing(value); };
                    return compute();
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `a partial repair cannot hide an unrelated broken source graph`() {
        directory.resolve("Broken.x").toFile().writeText("module Broken { Unknown value; }")
        refused(
            """
            module Missing {
                void run() {
                    §missing();
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `existing calls and overload selection survive the addition`() {
        query(
            """
            module Missing {
                Int keep(Int value) = value;
                Int keep(String value) = value.size;
                Int read(Int value) {
                    Int previous = keep(value);
                    return §missing(value);
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("Int previous = keep(value);", "private Int64 missing(Int64 arg1)") }
    }

    private fun refused(marked: String) = query(marked, expected = false) { error("Unexpected method creation") }

    private fun query(
        marked: String,
        expected: Boolean = true,
        check: (String) -> Unit,
    ) {
        CompilerTestSupport.configure()
        val offset = marked.indexOf('§')
        val text = marked.replace("§", "")
        val file =
            directory
                .resolve("Missing.x")
                .toFile()
                .apply { writeText(text) }
                .canonicalFile
        val uri = file.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val diagnostics = adapter.compile(uri, text).diagnostics
            assertThat(diagnostics).isNotEmpty()
            assertThat(diagnostics).noneMatch { it.code == "EMB-5" }
            val at = XdkRename.position(text, offset)
            val action =
                adapter.getCodeActions(uri, Range(at, at), diagnostics).singleOrNull {
                    it.title ==
                        "Create private method 'missing'"
                }
            if (!expected) {
                assertThat(action).isNull()
                return
            }
            assertThat(action).describedAs("Missing method action for %s", marked).isNotNull()
            assertThat(action!!.kind).isEqualTo(CodeAction.CodeActionKind.QUICKFIX)
            val edit = requireNotNull(action.edit)
            assertThat(edit.versioned).isTrue()
            assertThat(edit.changes.keys).containsExactly(uri)
            val changed =
                edit.changes
                    .getValue(uri)
                    .sortedByDescending { it.range.start.line * text.length + it.range.start.column }
                    .fold(text) { value, change ->
                        fun at(position: Position) =
                            requireNotNull(XdkRename.offset(text, SemanticModel.Position(position.line, position.column)))
                        value.replaceRange(at(change.range.start), at(change.range.end), change.newText)
                    }
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            assertThat(adapter.getCodeActions(uri, Range(at, at), emptyList()).filter { it.title == action.title }).isEmpty()
            assertThat(file.readText()).isEqualTo(text)
            check(changed)
        }
    }
}
