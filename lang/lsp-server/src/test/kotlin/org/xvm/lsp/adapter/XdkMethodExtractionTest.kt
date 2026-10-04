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

class XdkMethodExtractionTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["Int|input + step()", "Int8|input + 1", "Boolean|input && flag()", "String|input + \"!\"", "Int[]|[input, step()]"])
    fun `whole return expression keeps its type calls and stable inputs`(example: String) {
        val (type, expression) = example.split('|')
        val inputType = if (type == "Int[]") "Int" else type
        query("""
            module Extract {
                Int step() = 2;
                Boolean flag() = True;
                $type read($inputType input) {
                    return §$expression§;
                }
            }
        """.trimIndent()) { text, changed ->
            assertThat(changed).contains("return extractedMethod(input);", "private $type extractedMethod($inputType input)", "return $expression;")
            assertThat(changed).isNotEqualTo(text)
        }
    }

    @Test
    fun `typed initializer captures a stable local and preserves overload selection`() {
        query("""
            module Extract {
                Int step(Int input) = input;
                Int step(String input) = input.size;
                Int read(Int input) {
                    Int fixed = input + 1;
                    Int result = §step(fixed) + input§;
                    return result;
                }
            }
        """.trimIndent()) { _, changed ->
            assertThat(changed).contains("Int result = extractedMethod(fixed, input);", "private Int extractedMethod(Int fixed, Int input)", "return step(fixed) + input;")
        }
    }

    @Test
    fun `generic owner and implicit instance receiver stay in the same type`() {
        query("""
            module Extract {
                class Box<Element> {
                    Element identity(Element value) = value;
                    Element read(Element input) {
                        return §identity(input)§;
                    }
                }
            }
        """.trimIndent()) { _, changed ->
            assertThat(changed).contains("private Element extractedMethod(Element input)", "return identity(input);")
        }
    }

    @Test
    fun `static helper preserves comments unicode CRLF and chooses a fresh name`() {
        query("""
            module Extract {
                Int extractedMethod() = 0;
                static Int read(Int input) {
                    return §input /* 😀 */ + 1§;
                }
            }
        """.trimIndent().replace("\n", "\r\n")) { _, changed ->
            assertThat(changed).contains("return extractedMethod1(input);", "private static Int extractedMethod1(Int input) {\r\n        return input /* 😀 */ + 1;")
        }
    }

    @Test
    fun `constant expression needs no invented parameter`() {
        query("""
            module Extract {
                Int read() {
                    return §1 + 2§;
                }
            }
        """.trimIndent()) { _, changed ->
            assertThat(changed).contains("return extractedMethod();", "private Int extractedMethod()")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "input++;\nreturn §input + 1§;",
        "@Volatile Int stored = input;\nreturn §stored + 1§;",
        "Int changed = input;\nchanged++;\nreturn §changed + 1§;",
        "return §input§ + 1;",
        "val value = §input + 1§;\nreturn value;",
    ])
    fun `refuse mutation reference storage partial selections and inferred initializer`(body: String) {
        refused("""
            module Extract {
                Int read(Int input) {
                    ${body.replace("\n", "\n                    ")}
                }
            }
        """.trimIndent())
    }

    @Test
    fun `method formals lambda creation and conditional returns remain explicit refusals`() {
        listOf(
            """
                module Extract {
                    <Element> Element read(Element input) {
                        return §input§;
                    }
                }
            """.trimIndent(),
            """
                module Extract {
                    function Int() read(Int input) {
                        return §() -> input§;
                    }
                }
            """.trimIndent(),
            """
                module Extract {
                    conditional Int read(String input) {
                        return §input.indexOf('a')§;
                    }
                }
            """.trimIndent(),
        ).forEach(::refused)
    }

    @Test
    fun `broken graph neighbor refuses publication`() {
        directory.resolve("Broken.x").toFile().writeText("module Broken { Missing value; }")
        refused("""
            module Extract {
                Int read(Int input) {
                    return §input + 1§;
                }
            }
        """.trimIndent())
    }

    private fun refused(marked: String) = query(marked, expected = false) { _, _ -> error("Unexpected extraction") }

    private fun query(marked: String, expected: Boolean = true, check: (String, String) -> Unit) {
        CompilerTestSupport.configure()
        val start = marked.indexOf('§')
        val end = marked.lastIndexOf('§') - 1
        val text = marked.replace("§", "")
        val file = directory.resolve("Extract.x").toFile().apply { writeText(text) }.canonicalFile
        val uri = file.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val range = Range(XdkRename.position(text, start), XdkRename.position(text, end))
            val action = adapter.getCodeActions(uri, range, emptyList()).singleOrNull { it.title == "Extract expression to private method" }
            if (!expected) {
                assertThat(action).isNull()
                return
            }
            val edit = requireNotNull(action) { "No method extraction for $marked" }.edit!!
            assertThat(edit.versioned).isTrue()
            val changed = edit.changes.getValue(uri).sortedWith(compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column }).fold(text) { value, change ->
                fun offset(at: Position) = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
                value.replaceRange(offset(change.range.start), offset(change.range.end), change.newText)
            }
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            assertThat(file.readText()).isEqualTo(text)
            check(text, changed)
        }
    }
}
