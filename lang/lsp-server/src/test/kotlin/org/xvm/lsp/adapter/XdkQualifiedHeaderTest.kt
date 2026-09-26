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
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkQualifiedHeaderTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(
        strings = [
            "void damaged(ecstasy.text.Str| value) {}", "ecstasy.text.Str| property;",
            "ecstasy.text.Str| damaged() = new StringBuffer();",
        ],
    )
    fun `qualified type prefixes replace only the final token`(declaration: String) {
        val prefix = "module Headers { " + declaration.substringBefore('|')
        val suffix = declaration.substringAfter('|') + " Int later=1; }"
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, prefix + suffix)
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).describedAs(declaration).contains("StringBuffer")
            assertThat(items.single { it.label == "StringBuffer" }.textEdit)
                .isEqualTo(TextEdit(Range(Position(0, prefix.length - 3), Position(0, prefix.length)), "StringBuffer"))
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).isNull()
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(adapter.compile(URI, prefix.dropLast(3) + "StringBuffer" + suffix).diagnostics).isEmpty()
        }
    }

    @Test
    fun `qualified scope includes accessible types and excludes values and private children`() {
        val prefix =
            "module Headers { class Owner { class ItemPublic {} private class ItemPrivate {} " +
                "protected class ItemProtected {} static Int ItemValue=1; typedef String as ItemAlias; } " +
                "void damaged(Owner.Ite"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix value) {} }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label })
                .containsExactlyInAnyOrder("ItemPublic", "ItemAlias")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Owner", "Alias"])
    fun `class qualifiers and imported aliases include inherited nested types`(qualifier: String) {
        val declarations =
            "module Headers { import Owner as Alias; class Base { class ItemBase {} } " +
                "class Owner extends Base { class ItemOwn {} } "
        val prefix = declarations + "void damaged($qualifier.Ite"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix value) {} }")
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).containsExactlyInAnyOrder("ItemBase", "ItemOwn")
            for (item in items) {
                assertThat(adapter.compile(URI, prefix.dropLast(3) + item.label + " value) {} }").diagnostics).isEmpty()
            }
        }
    }

    @Test
    fun `private nested types remain available inside their owning class`() {
        val prefix = "module Headers { class Owner { private class ItemPrivate {} void damaged(Owner.Ite"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix value) {} } }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).containsExactly("ItemPrivate")
            assertThat(adapter.compile(URI, prefix.dropLast(3) + "ItemPrivate value) {} } }").diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Owner.Hidden.Ite", "Alias.Ite", "missing.Str", "number.Str"])
    fun `inaccessible unknown and value qualifiers do not fall back to enclosing names`(qualified: String) {
        val alias = if (qualified.startsWith("Alias.")) "import Owner.Hidden as Alias; " else ""
        val prefix =
            "module Headers { class Owner { class ItemPublic {} private class Hidden { class Item {} } } " +
                alias + "Int number=1; void damaged($qualified"
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, "$prefix value) {} }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length)).describedAs(qualified).isEmpty()
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(cached.diagnostics.map { it.code }).doesNotContain("EMB-5")
            val control = prefix.removeSuffix(qualified) + "Owner.Ite"
            adapter.compile(URI, "$control value) {} }")
            assertThat(adapter.getCompletions(URI, 0, control.length).map { it.label }).containsExactly("ItemPublic")
        }
    }

    @Test
    fun `package import aliases resolve through the bundled XDK`() {
        val prefix = "module Headers { import ecstasy.text as Text; void damaged(Text.Str"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix value) {} }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).contains("StringBuffer")
            assertThat(adapter.compile(URI, prefix.dropLast(3) + "StringBuffer value) {} }").diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "void damaged(ecstasy.text.Str|", "void damaged(ecstasy.text.Str| {}", "ecstasy.text.Str|;",
        ],
    )
    fun `qualified type queries keep diagnostics for missing names and delimiters`(declaration: String) {
        val prefix = "module Headers { " + declaration.substringBefore('|')
        val suffix = declaration.substringAfter('|') + " }"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, prefix + suffix)
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).contains("StringBuffer")
            assertThat(adapter.compile(URI, prefix.dropLast(3) + "StringBuffer" + suffix).diagnostics).isNotEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "void damaged(ecstasy.text.Str|ingBuffer value) {}", "void damaged(ecstasy.te|xt.StringBuffer value) {}",
            "void damaged(ecstasy.text.| value) {}", "<T> void damaged(ecstasy.text.Str| value) {}",
            "void damaged(List<String>.Ite| value) {}",
        ],
    )
    fun `unsupported cursor positions and generic headers remain empty`(declaration: String) {
        val prefix = "module Headers { " + declaration.substringBefore('|')
        XdkAdapter().use { adapter ->
            adapter.compile(URI, prefix + declaration.substringAfter('|') + " }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length)).describedAs(declaration).isEmpty()
        }
    }

    @Test
    fun `qualified queries follow unsaved nested types and root invalidation`() {
        val root = directory.resolve("Headers.x").toFile().canonicalFile
        val child = directory.resolve("Headers/Child.x").toFile().canonicalFile
        child.parentFile.mkdirs()
        root.writeText("module Headers { class Owner { class ItemDisk {} } }")
        val prefix = "class Child { void damaged(Owner.Ite"
        child.writeText("$prefix value) {} }")
        XdkAdapter().use { adapter ->
            val rootUri = root.toURI().toString()
            val childUri = child.toURI().toString()
            adapter.compile(rootUri, "module Headers { class Owner { class ItemOverlay {} } }")
            adapter.compile(childUri, child.readText())
            assertThat(adapter.getCompletions(childUri, 0, prefix.length).map { it.label })
                .containsExactly("ItemOverlay")
            adapter.compile(rootUri, root.readText())
            assertThat(adapter.getCompletions(childUri, 0, prefix.length).map { it.label }).containsExactly("ItemDisk")
            assertThat(root.readText()).contains("ItemDisk")
        }
    }

    @Test
    fun `embedding type queries preserve qualifier syntax without validating or caching it`() {
        CompilerTestSupport.configure()
        val prefix = "module Headers { void damaged(ecstasy.text.Str"
        val text = "$prefix value) {} Int later=1; }"
        val source = Source(text, URI)
        repeat(prefix.length) { source.next() }
        val cursor = source.position
        source.reset()
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, errors)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        val site = analysis.sites().single()
        val type = site.target as NamedTypeExpression
        assertThat(type.parent).isSameAs(site)
        assertThat(type.source.toRawString()).isEqualTo(text)
        assertThat(type.names).containsExactly("ecstasy", "text", "Str")
        assertThat(type.isValidated).isFalse()
        assertThat(type.nameBindings.map { it.target() }).containsOnlyNulls()
        assertThat(analysis.cursorBindings()[site]!!.types().map { it.name() }).contains("StringBuffer")
        for (listener in listOf(ErrorList(ErrorList.FIRST_ERROR), ErrorListener.cancellable(ErrorList()) { true })) {
            source.reset()
            val stopped = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, listener)
            assertThat(stopped.pool()).isEmpty()
            assertThat(stopped.cursorBindings()).isEmpty()
        }
    }

    private companion object {
        const val URI = "untitled:Headers.x"
    }
}
