package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkRename
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import java.nio.file.Path

class XdkMethodExtractionTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(
        strings = ["Int|input + step()", "Int8|input + 1", "Boolean|input && flag()", "String|input + \"!\"", "Int[]|[input, step()]"],
    )
    fun `whole return expression keeps its type calls and stable inputs`(example: String) {
        val (type, expression) = example.split('|')
        val inputType = if (type == "Int[]") "Int" else type
        query(
            """
            module Extract {
                Int step() = 2;
                Boolean flag() = True;
                $type read($inputType input) {
                    return §$expression§;
                }
            }
            """.trimIndent(),
        ) { text, changed ->
            assertThat(
                changed,
            ).contains("return extractedMethod(input);", "private $type extractedMethod($inputType input)", "return $expression;")
            assertThat(changed).isNotEqualTo(text)
        }
    }

    @Test
    fun `typed initializer captures a stable local and preserves overload selection`() {
        query(
            """
            module Extract {
                Int step(Int input) = input;
                Int step(String input) = input.size;
                Int read(Int input) {
                    Int fixed = input + 1;
                    Int result = §step(fixed) + input§;
                    return result;
                }
            }
            """.trimIndent(),
        ) { _, changed ->
            assertThat(
                changed,
            ).contains(
                "Int result = extractedMethod(fixed, input);",
                "private Int extractedMethod(Int fixed, Int input)",
                "return step(fixed) + input;",
            )
        }
    }

    @Test
    fun `generic owner and implicit instance receiver stay in the same type`() {
        query(
            """
            module Extract {
                class Box<Element> {
                    Element identity(Element value) = value;
                    Element read(Element input) {
                        return §identity(input)§;
                    }
                }
            }
            """.trimIndent(),
        ) { _, changed ->
            assertThat(changed).contains("private Element extractedMethod(Element input)", "return identity(input);")
        }
    }

    @Test
    fun `static helper preserves comments unicode CRLF and chooses a fresh name`() {
        query(
            """
            module Extract {
                Int extractedMethod() = 0;
                static Int read(Int input) {
                    return §input /* 😀 */ + 1§;
                }
            }
            """.trimIndent().replace("\n", "\r\n"),
        ) { _, changed ->
            assertThat(
                changed,
            ).contains(
                "return extractedMethod1(input);",
                "private static Int extractedMethod1(Int input) {\r\n        return input /* 😀 */ + 1;",
            )
        }
    }

    @Test
    fun `constant expression needs no invented parameter`() {
        query(
            """
            module Extract {
                Int read() {
                    return §1 + 2§;
                }
            }
            """.trimIndent(),
        ) { _, changed ->
            assertThat(changed).contains("return extractedMethod();", "private Int extractedMethod()")
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "input++;\nreturn §input + 1§;",
            "@Volatile Int stored = input;\nreturn §stored + 1§;",
            "Int changed = input;\nchanged++;\nreturn §changed + 1§;",
        ],
    )
    fun `refuse mutation and reference storage`(body: String) {
        refused(
            """
            module Extract {
                Int read(Int input) {
                    ${body.replace("\n", "\n                    ")}
                }
            }
            """.trimIndent(),
        )
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
    fun `stateful calls retain their order around a stable input`() {
        query(
            """
            module Extract {
                class Counter {
                    Int count = 0;
                    Int next() = ++count;
                    Int read(Int input) {
                        return §next() + input + next()§;
                    }
                }
            }
            """.trimIndent(),
        ) { _, changed ->
            assertThat(changed).contains("return extractedMethod(input);", "return next() + input + next();")
            assertThat(Regex("return next\\(\\) ").findAll(changed).count()).isEqualTo(1)
        }
    }

    @Test
    fun `anonymous class creation is not moved across a closure boundary`() {
        refused(
            """
            module Extract {
                interface Reader { Int read(); }
                Reader make(Int input) {
                    return §new Reader() { @Override Int read() = input; }§;
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `broken graph neighbor refuses publication`() {
        directory.resolve("Broken.x").toFile().writeText("module Broken { Missing value; }")
        refused(
            """
            module Extract {
                Int read(Int input) {
                    return §input + 1§;
                }
            }
            """.trimIndent(),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["return §input§ + 1;", "val value = §input + 1§;\nreturn value;", "return accept(§input + step()§);"])
    fun `nested expressions and inferred initializers retain the compiler selected result type`(body: String) {
        query(
            """
            module Extract {
                Int step() = 1;
                Int accept(Int value) = value;
                Int read(Int input) {
                    ${body.replace("\n", "\n                    ")}
                }
            }
            """.trimIndent(),
        ) { _, changed -> assertThat(changed).contains("private Int64 extractedMethod(Int input)") }
    }

    @Test
    fun `condition extraction keeps its original branch evaluation site`() {
        query(
            """
            module Extract {
                Int read(Int input) {
                    if (§input > 0§) {
                        return 1;
                    }
                    return 0;
                }
            }
            """.trimIndent(),
        ) { _, changed -> assertThat(changed).contains("if (extractedMethod(input))", "private Boolean extractedMethod(Int input)") }
    }

    @Test
    fun `contiguous call statements move once in their original order`() {
        query(
            """
            module Extract {
                void emit(Int value) {}
                void run(Int input) {
                    §emit(input);
                    emit(input + 1);§
                }
            }
            """.trimIndent(),
            title = "Extract statements to private method",
        ) { _, changed ->
            assertThat(changed)
                .contains("private void extractedMethod(Int input)", "extractedMethod(input);")
                .containsOnlyOnce("emit(input);")
                .containsOnlyOnce("emit(input + 1);")
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `inferred result from another module uses its source alias for source and binary dependencies`(binary: Boolean) {
        CompilerTestSupport.configure()
        val library =
            directory.resolve("Library.x").toFile().canonicalFile.apply {
                writeText("module Library { class Value {} }")
            }
        query(
            """
            module Extract {
                package lib import Library;
                Object read(lib.Value input) {
                    val value = §input§;
                    return value;
                }
            }
            """.trimIndent(),
            configure = { adapter, uri ->
                if (binary) {
                    val errors = ErrorList()
                    val compiled = EmbeddingSupport.instance().compileModule(Source(library.readText(), library.path), null, errors)
                    assertThat(compiled.succeeded()).describedAs("%s", errors).isTrue()
                    adapter.replaceDependencies(listOf(compiled.toDependency()))
                }
                adapter.replaceSourceModules(
                    buildList {
                        if (!binary) add(XdkSourceModule("Library", library.toURI().toString()))
                        add(XdkSourceModule("Extract", uri, setOf("Library")))
                    },
                )
            },
        ) { _, changed -> assertThat(changed).contains("private lib.Value extractedMethod(lib.Value input)") }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "if (input > 0) { emit(input); }",
            "Int local = input;",
            "input++;",
        ],
    )
    fun `statement extraction refuses intervening control flow declarations and mutable captures`(middle: String) {
        query(
            """
            module Extract {
                void emit(Int value) {}
                void run(Int input) {
                    §emit(input);
                    $middle
                    emit(input);§
                }
            }
            """.trimIndent(),
            expected = false,
            title = "Extract statements to private method",
        ) { _, _ -> error("Unexpected statement extraction") }
    }

    private fun refused(marked: String) = query(marked, expected = false) { _, _ -> error("Unexpected extraction") }

    private fun query(
        marked: String,
        expected: Boolean = true,
        title: String = "Extract expression to private method",
        configure: (XdkAdapter, String) -> Unit = { _, _ -> },
        check: (String, String) -> Unit,
    ) {
        CompilerTestSupport.configure()
        val start = marked.indexOf('§')
        val end = marked.lastIndexOf('§') - 1
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
            configure(adapter, uri)
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val range = Range(XdkRename.position(text, start), XdkRename.position(text, end))
            val action = adapter.getCodeActions(uri, range, emptyList()).singleOrNull { it.title == title }
            if (!expected) {
                assertThat(action).isNull()
                return
            }
            val edit = requireNotNull(action) { "No method extraction for $marked" }.edit!!
            assertThat(edit.versioned).isTrue()
            val changed =
                edit.changes
                    .getValue(uri)
                    .sortedWith(
                        compareByDescending<TextEdit> {
                            it.range.start.line
                        }.thenByDescending { it.range.start.column },
                    ).fold(text) { value, change ->
                        fun offset(at: Position) = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
                        value.replaceRange(offset(change.range.start), offset(change.range.end), change.newText)
                    }
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            assertThat(file.readText()).isEqualTo(text)
            check(text, changed)
        }
    }
}
