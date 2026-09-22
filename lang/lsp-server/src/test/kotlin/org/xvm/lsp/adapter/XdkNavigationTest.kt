package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.model.Location

class XdkNavigationTest {
    @Test
    fun `a local definition points to its declaration name and excludes it from ordinary references`() {
        val source =
            """
            module Local {
                Int read() {
                    Int /*declaration*/count;
                    /*write*/count = 1;
                    return /*read*/count;
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val read = position(source, "read")
            val declaration = span(source, "declaration", "count")
            assertThat(adapter.findDefinition(URI, read.line, read.column)).isEqualTo(declaration)
            assertThat(adapter.findReferences(URI, read.line, read.column, false))
                .containsExactly(span(source, "write", "count"), span(source, "read", "count"))
            assertThat(adapter.findReferences(URI, read.line, read.column, true))
                .containsExactly(declaration, span(source, "write", "count"), span(source, "read", "count"))
            val atDeclaration = position(source, "declaration")
            assertThat(adapter.findReferences(URI, atDeclaration.line, atDeclaration.column, false))
                .containsExactly(span(source, "write", "count"), span(source, "read", "count"))
        }
    }

    @Test
    fun `sibling scopes can use the same spelling without sharing references or highlights`() {
        val source =
            """
            module Siblings {
                Int choose(Boolean flag) {
                    if (flag) {
                        Int /*firstDeclaration*/value = 1;
                        return /*firstRead*/value;
                    } else {
                        Int /*secondDeclaration*/value = 2;
                        return /*secondRead*/value;
                    }
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            for (prefix in listOf("first", "second")) {
                val read = position(source, "${prefix}Read")
                val declaration = span(source, "${prefix}Declaration", "value")
                val use = span(source, "${prefix}Read", "value")
                assertThat(adapter.findDefinition(URI, read.line, read.column)).isEqualTo(declaration)
                assertThat(adapter.findReferences(URI, read.line, read.column, true)).containsExactly(declaration, use)
                assertThat(adapter.getDocumentHighlights(URI, read.line, read.column).map { it.range })
                    .containsExactly(range(declaration), range(use))
            }
        }
    }

    @Test
    fun `identical register slots in different methods do not share a declaration`() {
        val source =
            """
            module Methods {
                Int first() { Int /*firstDeclaration*/value = 1; return /*firstRead*/value; }
                Int second() { Int /*secondDeclaration*/value = 2; return /*secondRead*/value; }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val read = position(source, "secondRead")
            assertThat(adapter.findDefinition(URI, read.line, read.column)).isEqualTo(span(source, "secondDeclaration", "value"))
            assertThat(adapter.findReferences(URI, read.line, read.column, true))
                .containsExactly(span(source, "secondDeclaration", "value"), span(source, "secondRead", "value"))
            assertThat(adapter.getDocumentHighlights(URI, read.line, read.column).map { it.range })
                .containsExactly(range(span(source, "secondDeclaration", "value")), range(span(source, "secondRead", "value")))
        }
    }

    @Test
    fun `narrowed and original uses lead to the same local declaration`() {
        val source =
            """
            module Narrowing {
                String text(Object input) {
                    Object /*declaration*/value = input;
                    if (/*test*/value.is(String)) {
                        return /*narrowed*/value;
                    }
                    return /*original*/value.toString();
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val narrowed = position(source, "narrowed")
            assertThat(adapter.findDefinition(URI, narrowed.line, narrowed.column)).isEqualTo(span(source, "declaration", "value"))
            assertThat(adapter.findReferences(URI, narrowed.line, narrowed.column, false))
                .containsExactly(span(source, "test", "value"), span(source, "narrowed", "value"), span(source, "original", "value"))
        }
    }

    @Test
    fun `a local passed to a call resolves to the local rather than the called method`() {
        val source =
            """
            module Arguments {
                Int twice(Int n) { return n + n; }
                Int read() {
                    Int /*declaration*/value = 1;
                    return /*call*/twice(/*argument*/value) + twice(/*literal*/1);
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val argument = position(source, "argument")
            assertThat(adapter.findDefinition(URI, argument.line, argument.column)).isEqualTo(span(source, "declaration", "value"))
            assertThat(
                adapter.findReferences(URI, argument.line, argument.column, false),
            ).containsExactly(span(source, "argument", "value"))
            val call = position(source, "call")
            assertThat(adapter.findDefinition(URI, call.line, call.column)?.startLine).isEqualTo(1)
            val literal = position(source, "literal")
            assertThat(adapter.findDefinition(URI, literal.line, literal.column)).isNull()
            assertThat(adapter.findDefinition(URI, argument.line, argument.column + "value".length)).isNull()
        }
    }

    @Test
    fun `a local shadowing a property highlights only the local`() {
        val source =
            """
            module Shadow {
                class Holder {
                    Int value = 1;
                    Int read() {
                        Int /*declaration*/value = 2;
                        return /*local*/value + this.value;
                    }
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val local = position(source, "local")
            assertThat(adapter.findReferences(URI, local.line, local.column, false)).containsExactly(span(source, "local", "value"))
            assertThat(adapter.getDocumentHighlights(URI, local.line, local.column).map { it.range })
                .containsExactly(range(span(source, "declaration", "value")), range(span(source, "local", "value")))
        }
    }

    @Test
    fun `an unresolved name does not borrow a declaration or highlights by spelling`() {
        val source =
            """
            module Unresolved {
                Int good() { Int value = 1; return value; }
                Int broken() { return /*unresolved*/value; }
            }
            """.trimIndent()
        CompilerTestSupport.configure()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).success).isFalse()
            val unresolved = position(source, "unresolved")
            assertThat(adapter.findDefinition(URI, unresolved.line, unresolved.column)).isNull()
            assertThat(adapter.findReferences(URI, unresolved.line, unresolved.column, true)).isEmpty()
            assertThat(adapter.getDocumentHighlights(URI, unresolved.line, unresolved.column)).isEmpty()
        }
    }

    @Test
    fun `member declarations and qualified calls use their identifier spans`() {
        val source =
            """
            module Members {
                class /*typeDeclaration*/Box {
                    Int /*propertyDeclaration*/value = 1;
                    Int /*methodDeclaration*/read() { return /*propertyUse*/value; }
                }
                Int use() {
                    /*typeUse*/Box box = new /*construction*/Box();
                    return /*call*/box.read();
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val method = position(source, "methodDeclaration")
            val callStart = position(source, "call").let { it.copy(column = it.column + "box.".length) }
            val call = Location(URI, callStart.line, callStart.column, callStart.line, callStart.column + "read".length)
            val methodDeclaration = span(source, "methodDeclaration", "read")
            assertThat(adapter.findDefinition(URI, callStart.line, callStart.column)).isEqualTo(methodDeclaration)
            assertThat(adapter.findReferences(URI, method.line, method.column, false)).containsExactly(call)
            assertThat(adapter.getDocumentHighlights(URI, method.line, method.column).map { it.range })
                .containsExactly(range(methodDeclaration), range(call))

            val type = position(source, "typeDeclaration")
            assertThat(adapter.findReferences(URI, type.line, type.column, false))
                .containsExactly(span(source, "typeUse", "Box"), span(source, "construction", "Box"))
            val property = position(source, "propertyDeclaration")
            assertThat(adapter.findReferences(URI, property.line, property.column, false))
                .containsExactly(span(source, "propertyUse", "value"))
        }
    }

    @Test
    fun `parameter definitions and declaration queries use the retained register identity`() {
        val source =
            """
            module Parameters {
                Int first(Int value) { return value; }
                Int second(Int /*declaration*/value) { return /*firstUse*/value + /*secondUse*/value; }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val use = position(source, "firstUse")
            assertThat(adapter.findDefinition(URI, use.line, use.column)).isEqualTo(span(source, "declaration", "value"))
            assertThat(adapter.findReferences(URI, use.line, use.column, false))
                .containsExactly(span(source, "firstUse", "value"), span(source, "secondUse", "value"))
            val declaration = position(source, "declaration")
            assertThat(adapter.findReferences(URI, declaration.line, declaration.column, true))
                .containsExactly(
                    span(source, "declaration", "value"),
                    span(source, "firstUse", "value"),
                    span(source, "secondUse", "value"),
                )
        }
    }

    @Test
    fun `constructor generated properties point to their source parameter`() {
        val source =
            """
            module Constructed {
                const Box(Int /*declaration*/value) {
                    Int read() { return /*use*/value; }
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val use = position(source, "use")
            assertThat(adapter.findDefinition(URI, use.line, use.column)).isEqualTo(span(source, "declaration", "value"))
            val declaration = position(source, "declaration")
            assertThat(adapter.findReferences(URI, declaration.line, declaration.column, true))
                .containsExactly(span(source, "declaration", "value"), span(source, "use", "value"))
        }
    }

    @Test
    fun `a narrowed parameter still points to its declaration`() {
        val source =
            """
            module ParameterNarrowing {
                String text(Object /*declaration*/value) {
                    if (value.is(String)) { return /*use*/value; }
                    return value.toString();
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val use = position(source, "use")
            assertThat(adapter.findDefinition(URI, use.line, use.column)).isEqualTo(span(source, "declaration", "value"))
        }
    }

    @Test
    fun `a captured local without a source association does not borrow an unrelated declaration`() {
        val source =
            """
            module Captures {
                Int run() {
                    Int /*declaration*/value = 1;
                    function Int() fn = () -> /*capture*/value;
                    return fn();
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val capture = position(source, "capture")
            assertThat(adapter.findDefinition(URI, capture.line, capture.column)).isNull()
        }
    }

    @Test
    fun `generic and qualified type uses select only the resolved type name`() {
        val source =
            """
            module Types {
                class Outer { class /*declaration*/Nested {} }
                void use(List</*qualified*/Outer.Nested> values) {}
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val qualifier = position(source, "qualified")
            val inner = qualifier.copy(column = qualifier.column + "Outer.".length)
            val declaration = span(source, "declaration", "Nested")
            assertThat(adapter.findDefinition(URI, inner.line, inner.column)).isEqualTo(declaration)
            assertThat(adapter.findDefinition(URI, qualifier.line, qualifier.column)).isNotEqualTo(declaration)
            val typeEnd = inner.column + "Nested".length
            assertThat(adapter.findDefinition(URI, inner.line, typeEnd)).isNull()
            assertThat(adapter.findReferences(URI, inner.line, inner.column, false))
                .containsExactly(Location(URI, inner.line, inner.column, inner.line, typeEnd))
        }
    }

    private fun withSource(
        source: String,
        check: (XdkAdapter) -> Unit,
    ) {
        CompilerTestSupport.configure()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            check(adapter)
        }
    }

    private fun position(
        source: String,
        marker: String,
    ): Position {
        val comment = "/*$marker*/"
        val start = source.indexOf(comment)
        require(start >= 0) { "Missing marker: $marker" }
        val offset = start + comment.length
        return Position(source.take(offset).count { it == '\n' }, offset - source.lastIndexOf('\n', offset) - 1)
    }

    private fun span(
        source: String,
        marker: String,
        name: String,
    ): Location = position(source, marker).let { Location(URI, it.line, it.column, it.line, it.column + name.length) }

    private fun range(location: Location): Range =
        Range(Position(location.startLine, location.startColumn), Position(location.endLine, location.endColumn))

    private companion object {
        const val URI = "file:///Navigation.x"
    }
}
