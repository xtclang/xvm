package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.IncompleteTypeCompositionStatement
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkTypeHeaderTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(
        strings = [
            "class Damaged extends Ba§",
            "interface Damaged extends Ba§",
            "class Damaged implements Ba§",
            "mixin Damaged into Ba§",
            "class Damaged incorporates Ba§",
            "class Damaged delegates Ba§(value)",
            "class Damaged implements List<Str§>",
            "class Damaged implements List<ecstasy.text.Str§>",
            "class Damaged implements List<Str§",
            "class Damaged extends §",
        ],
    )
    fun `composition type slots query the enclosing scope without registering a partial class`(header: String) {
        CompilerTestSupport.configure()
        val prefix = "module Headers { class Base {} Int BaseValue=1; " + header.substringBefore('§')
        val text = prefix + header.substringAfter('§') + " { Int inside=1; } Int later=2; }"
        val source = Source(text, URI)
        repeat(prefix.length) { source.next() }
        val cursor = source.position
        source.reset()
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, errors)
        assertThat(errors.errors.map { it.code }).describedAs(header).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        val site = analysis.sites().single()
        val declaration = site.parent as IncompleteTypeCompositionStatement
        assertThat(declaration.component).isNull()
        val owner = declaration.parent.component
        assertThat(owner.getChild("Damaged")).isNull()
        assertThat(owner.getChild("inside")).isNull()
        assertThat(owner.getChild("later")).isNotNull()
        assertThat(
            analysis
                .cursorBindings()
                .getValue(site)
                .types()
                .map { it.name() },
        ).contains(if (header.contains("Str")) "String" else "Base")
            .doesNotContain("BaseValue")
        assertThat(source.toRawString()).isEqualTo(text)
        listOf(ErrorList(ErrorList.FIRST_ERROR), ErrorListener.cancellable(ErrorList()) { true }).forEach { listener ->
            source.reset()
            val stopped = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, listener)
            assertThat(stopped.pool()).isEmpty()
            assertThat(stopped.cursorBindings()).isEmpty()
        }
    }

    @Test
    fun `qualified composition lookup excludes private types and value members`() {
        val prefix =
            "module Headers { import Owner as Alias; class Owner { class ItemPublic {} " +
                "private class ItemHidden {} static Int ItemValue=1; } class Damaged extends Alias.Ite"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix {} }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).containsExactly("ItemPublic")
        }
    }

    @Test
    fun `a composition prefix at actual EOF completes without fabricated source`() {
        val text = "module Headers { interface Damaged extends List<Str"
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, text)
            assertThat(adapter.getCompletions(URI, 0, text.length).map { it.label }).contains("String")
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(adapter.compile(URI, text.dropLast(3) + "String> {} }").diagnostics).isEmpty()
        }
    }

    @Test
    fun `accepting a base type edits only the prefix and restores compiler diagnostics`() {
        val prefix = "module Headers { class Base {} class Damaged extends Ba"
        val suffix = " { Int inside=1; } Int later=2; }"
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, prefix + suffix)
            val item = adapter.getCompletions(URI, 0, prefix.length).single { it.label == "Base" }
            assertThat(item.textEdit)
                .isEqualTo(TextEdit(Range(Position(0, prefix.length - 2), Position(0, prefix.length)), "Base"))
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).isNull()
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(cached.diagnostics).isNotEmpty()
            assertThat(adapter.compile(URI, prefix.dropLast(2) + "Base" + suffix).diagnostics).isEmpty()
        }
    }

    @Test
    fun `member file header lookup sees unsaved root types and preserves disk source`() {
        val root = directory.resolve("Headers.x").toFile().canonicalFile
        val child = directory.resolve("Headers/Child.x").toFile().canonicalFile
        child.parentFile.mkdirs()
        root.writeText("module Headers { class Owner { class ItemDisk {} } }")
        val prefix = "class Child extends Owner.Ite"
        child.writeText("$prefix { Int inside=1; }")
        XdkAdapter().use { adapter ->
            val rootUri = root.toURI().toString()
            val childUri = child.toURI().toString()
            adapter.compile(rootUri, "module Headers { class Owner { class ItemOverlay {} } }")
            val cached = adapter.compile(childUri, child.readText())
            assertThat(adapter.getCompletions(childUri, 0, prefix.length).map { it.label }).containsExactly("ItemOverlay")
            assertThat(adapter.getCachedResult(childUri)?.diagnostics).isEqualTo(cached.diagnostics)
            assertThat(adapter.getCachedResult(childUri)?.symbols).isEqualTo(cached.symbols)
            adapter.compile(rootUri, root.readText())
            assertThat(adapter.getCompletions(childUri, 0, prefix.length).map { it.label }).containsExactly("ItemDisk")
            assertThat(root.readText()).contains("ItemDisk")
        }
    }

    @Test
    fun `malformed class headers retain outline and folds without stale semantic facts`() {
        val text = "module Headers {\n class Damaged extends {\n  Int inside=1;\n }\n Int later=2;\n}"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "module Headers { class Old {} }")
            val result = adapter.compile(URI, text)
            assertThat(result.diagnostics).isNotEmpty()
            assertThat(result.diagnostics.map { it.code }).doesNotContain("EMB-5")
            assertThat(adapter.findWorkspaceSymbols("").map { it.name }).contains("Damaged", "inside", "later").doesNotContain("Old")
            assertThat(adapter.getFoldingRanges(URI)).anySatisfy {
                assertThat(it.startLine).isEqualTo(1)
                assertThat(it.endLine).isEqualTo(3)
            }
            assertThat(adapter.prepareTypeHierarchy(URI, 1, 8)).isEmpty()
            assertThat(adapter.findDefinition(URI, 2, 8)).isNull()
            assertThat(adapter.compile(URI, text.replace("extends {", "{")).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "class Damaged extends Ow§ner.Base", "class Damaged implements Lis§<String>",
            "class Damaged implements List<§>", "class Damaged extends Owner.§", "module Headers extends Ba§",
        ],
    )
    fun `unsupported composition prefixes do not invent a scope`(declaration: String) {
        val prefix = (if (declaration.startsWith("module")) "" else "module Headers { ") + declaration.substringBefore('§')
        val suffix = declaration.substringAfter('§') + " {}" + if (declaration.startsWith("module")) "" else " }"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, prefix + suffix)
            assertThat(adapter.getCompletions(URI, 0, prefix.length)).isEmpty()
        }
    }

    private companion object {
        const val URI = "untitled:Headers.x"
    }
}
