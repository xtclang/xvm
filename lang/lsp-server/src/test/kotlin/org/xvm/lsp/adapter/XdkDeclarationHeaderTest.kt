package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.compiler.Parser
import org.xvm.compiler.ast.partial.IncompleteDeclarationStatement
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

/**
 * Exercises malformed declaration headers while retaining their written names, ranges and following
 * declarations. Recovery must not register incomplete method or property components as valid declarations.
 */
class XdkDeclarationHeaderTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "void damaged(Int value, Str) {}",
                "void damaged(Int ) {}",
                "void damaged(Int value {}",
            ],
    )
    fun `unfinished parameter headers preserve their written name and following declarations`(declaration: String) {
        XdkAdapter().use { adapter ->
            val result = adapter.compile(URI, "module Headers { $declaration Int later = 1; }")
            assertThat(result.diagnostics).isNotEmpty()
            assertThat(
                result.symbols
                    .single()
                    .children
                    .map { it.name },
            ).containsExactly("damaged", "later")
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "void damaged(Str§ value) {}",
                "void damaged(Int first, Str§ second) {}",
                "Str§ property;",
                "Str§ damaged() = \"x\";",
                "void damaged(List<Str§> value) {}",
                "void damaged(Map<Int, List<Str§>> value) {}",
                "void damaged(Map<Str§, Int> value) {}",
                "List<Str§> property;",
                "List<Str§> damaged() = [\"x\"] ;",
                "void damaged(Str§ | Int value) {}",
                "void damaged(Int | Str§ value) {}",
                "void damaged(Object + Str§ value) {}",
                "void damaged(Object - Str§ value) {}",
                "void damaged((Int | Str§) value) {}",
                "void damaged(List<(Int | Str§)> value) {}",
                "void damaged(List<Str§?> value) {}",
                "void damaged(List<Str§[]> value) {}",
                "void damaged(immutable List<Str§> value) {}",
                "void damaged(function Str§(Int) value) {}",
                "void damaged(function void(Str§) value) {}",
                "void damaged(function (Int, Str§)(Int) value) {}",
                "void damaged(function void(List<Str§>) value) {}",
                "void damaged(Function<<Str§>, <Int>> value) {}",
                "void damaged(Function<<Int>, <Str§>> value) {}",
            ],
    )
    fun `header type prefixes expose compiler types with exact edits, in parameterized and compound headers too`(declaration: String) {
        val prefix =
            "module Headers { String StringValue = \"x\"; " + declaration.substringBefore('§')
        val suffix = declaration.substringAfter('§') + " Int later = 1; }"
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, prefix + suffix)
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label })
                .describedAs(declaration)
                .contains("String")
                .doesNotContain("StringValue")
            assertThat(items.single { it.label == "String" }.textEdit)
                .isEqualTo(
                    TextEdit(
                        Range(Position(0, prefix.length - 3), Position(0, prefix.length)),
                        "String",
                    ),
                )
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).isNull()
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(adapter.compile(URI, prefix.dropLast(3) + "String" + suffix).diagnostics)
                .describedAs(declaration)
                .isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "Int first, "])
    fun `an empty parameter type slot does not invent a parameter name`(parameters: String) {
        val prefix = "module Headers { void damaged($parameters"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix) {} Int later = 1; }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label })
                .contains("String")
        }
    }

    @ParameterizedTest
    @CsvSource(
        "void damaged(Str|, String",
        "void damaged(Str| {}, String",
        "Str|;, String",
        "void damaged(ecstasy.text.Str|, StringBuffer",
        "void damaged(ecstasy.text.Str| {}, StringBuffer",
        "ecstasy.text.Str|;, StringBuffer",
    )
    fun `type completion works without a written name or closing delimiter, whose diagnostics remain`(
        declaration: String,
        type: String,
    ) {
        val prefix = "module Headers { " + declaration.substringBefore('|')
        val suffix = declaration.substringAfter('|') + " }"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, prefix + suffix)
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label })
                .contains(type)
            assertThat(adapter.compile(URI, prefix.dropLast(3) + type + suffix).diagnostics)
                .isNotEmpty()
        }
    }

    @Test
    fun `header types use enclosing names imports aliases and compiler shadowing`() {
        XdkAdapter().use { adapter ->
            for (
            (setup, name) in
            listOf(
                "import ecstasy.text.StringBuffer as Buffer;" to "Buffer",
                "import ecstasy.text.*;" to "StringBuffer",
                "class ItemType {}" to "ItemType",
                "typedef String as Text;" to "Text",
            )
            ) {
                val prefix =
                    "module Headers { $setup class Nested { void damaged(${name.dropLast(1)}"
                adapter.compile(URI, "$prefix value) {} } }")
                assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label })
                    .describedAs(setup)
                    .contains(name)
            }
            val prefix = "module Headers { Int String = 1; void damaged(Str"
            adapter.compile(URI, "$prefix value) {} }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label })
                .doesNotContain("String")
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "void damaged(Int va§) {}",
                "void damaged(missing.te§xt.StringBuffer value) {}",
                "void damaged(missing.text.§ value) {}",
                "<ecstasy> void damaged(ecstasy.text.Str§ value) {}",
                "void damaged(List<Missing>.§ value) {}",
            ],
    )
    fun `unsupported headers, cursor positions and shapes do not guess types, scopes, parameters or signatures`(declaration: String) {
        val prefix = "module Headers { " + declaration.substringBefore('§')
        XdkAdapter().use { adapter ->
            adapter.compile(URI, prefix + declaration.substringAfter('§') + " }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length))
                .describedAs(declaration)
                .isEmpty()
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).isNull()
        }
    }

    @Test
    fun `header queries follow unsaved module types and invalidate after root changes`() {
        val root = directory.resolve("Headers.x").toFile().canonicalFile
        val child = directory.resolve("Headers/Child.x").toFile().canonicalFile
        child.parentFile.mkdirs()
        root.writeText("module Headers { class ItemDisk {} }")
        val prefix = "class Child { void damaged(Ite"
        child.writeText("$prefix value) {} }")
        XdkAdapter().use { adapter ->
            val rootUri = root.toURI().toString()
            val childUri = child.toURI().toString()
            adapter.compile(rootUri, "module Headers { class ItemOverlay {} }")
            adapter.compile(childUri, child.readText())
            assertThat(adapter.getCompletions(childUri, 0, prefix.length).map { it.label })
                .contains("ItemOverlay")
                .doesNotContain("ItemDisk")
            adapter.compile(rootUri, root.readText())
            assertThat(adapter.getCompletions(childUri, 0, prefix.length).map { it.label })
                .contains("ItemDisk")
                .doesNotContain("ItemOverlay")
            assertThat(root.readText()).contains("ItemDisk")
        }
    }

    @Test
    fun `broken headers keep source folds and outline but discard old semantics`() {
        val valid = "module Headers { void damaged(Int value) {} Int later = 1; }"
        val text =
            "module Headers {\r\n void damaged(Int) {\r\n Int hidden = 1;\r\n }\r\n Int later = 1; }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, valid).success).isTrue()
            assertThat(adapter.compile(URI, text).success).isFalse()
            assertThat(adapter.findWorkspaceSymbols("damaged")).hasSize(1)
            assertThat(adapter.findWorkspaceSymbols("hidden")).isEmpty()
            assertThat(adapter.getFoldingRanges(URI)).anySatisfy {
                assertThat(it.startLine).isEqualTo(1)
                assertThat(it.endLine).isEqualTo(3)
            }
            assertThat(adapter.findDefinition(URI, 1, 7)).isNull()
            assertThat(adapter.findReferences(URI, 1, 7, true)).isEmpty()
            assertThat(adapter.getSignatureHelp(URI, 1, 18)).isNull()
            assertThat(adapter.compile(URI, valid).success).isTrue()
        }
    }

    @Test
    fun `embedding header queries own their cursor but register no incomplete declaration`() {
        CompilerTestSupport.configure()
        val prefix = "module Headers { void damaged(Str"
        val text = "$prefix value) { Int hidden = 1; } Int later = 1; }"
        val incomplete = analyzeIncomplete(text, URI, prefix.length)
        val (analysis, errors) = incomplete
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        val site = analysis.sites().single()
        assertThat(site.isTypeCompletion).isTrue()
        assertThat(site.source.toRawString()).isEqualTo(text)
        val declaration = site.parent as IncompleteDeclarationStatement
        assertThat(declaration.nameToken.orElseThrow().valueText).isEqualTo("damaged")
        assertThat(declaration.component).isSameAs(declaration.parent.component)
        assertThat(declaration.component.getChild("damaged")).isNull()
        assertThat(declaration.component.getChild("hidden")).isNull()
        assertThat(analysis.cursorBindings()[site]!!.types().map { it.name() }).contains("String")
        assertListenersStop(incomplete.source, incomplete.cursor)
    }

    private companion object {
        const val URI = "untitled:Headers.x"
    }
}
