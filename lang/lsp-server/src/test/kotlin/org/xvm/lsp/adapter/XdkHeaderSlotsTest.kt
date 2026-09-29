package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.partial.IncompleteTypeCompositionStatement
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkHeaderSlotsTest {
    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "void damaged(ecstasy.text.§ value) {}",
                "void damaged(List<ecstasy.text.§> value) {}",
                "ecstasy.text.§ property;",
                "void damaged(ecstasy.te§xt.StringBuffer value) {}",
                "void damaged((Int | §) value) {}",
                "void damaged((§ | Int) value) {}",
                "void damaged(Int | § value) {}",
                "void damaged(List<(Int | §)> value) {}",
                "void damaged(Object + § value) {}",
                "void damaged(Object - § value) {}",
                "(Int, Str§) damaged() { return 1, \"x\"; }",
                "(Int count, Str§ text) damaged() { return 1, \"x\"; }",
                "conditional Str§ damaged() { return True, \"x\"; }",
                "<T> void damaged(Str§ value) {}",
                "<T> Str§ damaged(T value) = \"x\";",
                "<T extends Str§> void damaged(T value) {}",
                "class Damaged<T extends Str§> {}",
                "package damaged incorporates SharedMi§ {} mixin SharedMixin into Package {}",
            ]
    )
    fun `header slots retain exact edits and compile after acceptance`(declaration: String) {
        val marked = "module Headers { $declaration }"
        val at = marked.indexOf('§')
        val before =
            when {
                declaration.contains("te§xt") -> 2
                declaration.contains("Str§") -> 3
                declaration.contains("SharedMi§") -> 8
                else -> 0
            }
        val after = if (declaration.contains("te§xt")) 2 else 0
        val selected =
            when {
                declaration.contains("te§xt") -> "text"
                declaration.contains("text.§") -> "StringBuffer"
                declaration.contains("SharedMi§") -> "SharedMixin"
                else -> "String"
            }
        val source = marked.replace("§", "")
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, source)
            val item = adapter.getCompletions(URI, 0, at).single { it.label == selected }
            assertThat(item.textEdit)
                .describedAs(declaration)
                .isEqualTo(
                    TextEdit(Range(Position(0, at - before), Position(0, at + after)), selected)
                )
            assertThat(adapter.getSignatureHelp(URI, 0, at)).isNull()
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(
                    adapter
                        .compile(URI, source.replaceRange(at - before, at + after, selected))
                        .diagnostics
                )
                .describedAs(declaration)
                .isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["<String> void damaged(Str§ value) {}", "class Damaged<String> extends Str§ {}"]
    )
    fun `unregistered formals shadow outer types and report their own bound`(declaration: String) {
        val marked = "module Headers { $declaration }"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, marked.replace("§", ""))
            assertThat(
                    adapter
                        .getCompletions(URI, 0, marked.indexOf('§'))
                        .single { it.label == "String" }
                        .detail
                )
                .isEqualTo("type parameter String extends Object")
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "module Headers incorporates SharedMi§ { class Hidden {} }",
                "module Headers implements ecstasy.text.Str§ { class Hidden {} }",
            ]
    )
    fun `module header queries preserve diagnostics and never expose an invented body scope`(
        marked: String
    ) {
        XdkAdapter().use { adapter ->
            val source = marked.replace("§", "")
            val cached = adapter.compile(URI, source)
            val completions = adapter.getCompletions(URI, 0, marked.indexOf('§'))
            assertThat(cached.diagnostics).isNotEmpty()
            assertThat(cached.diagnostics.map { it.code }).doesNotContain("EMB-5")
            if (marked.contains("ecstasy.text.Str")) {
                val input = Source(source, URI)
                repeat(marked.indexOf('§')) { input.next() }
                val cursor = input.position
                input.reset()
                val errors = ErrorList()
                val analysis =
                    EmbeddingSupport.instance().analyzeIncomplete(input, cursor, null, errors)
                val root = analysis.sites().single().parent as IncompleteTypeCompositionStatement
                assertThat(root.component.getChild("Hidden")).isNull()
                assertThat(errors.errors.map { it.code })
                    .containsExactly(Parser.INCOMPLETE_EXPRESSION)
                assertThat(
                        completions.map {
                            it.label
                        }
                    )
                    .describedAs(
                        "%s; sites=%s; bindings=%s",
                        errors.errors,
                        analysis.sites(),
                        analysis.cursorBindings(),
                    )
                    .contains("String")
            }
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
        }
    }

    @Test
    fun `trailing parameterized qualifier resolves its written arguments`() {
        val prefix = "module Headers { class Owner<T> { class Item {} } void damaged(Owner<String>."
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix value) {} }")
            val item = adapter.getCompletions(URI, 0, prefix.length).single { it.label == "Item" }
            assertThat(item.textEdit)
                .isEqualTo(
                    TextEdit(Range(Position(0, prefix.length), Position(0, prefix.length)), "Item")
                )
            assertThat(adapter.compile(URI, "${prefix}Item value) {} }").diagnostics).isEmpty()
        }
    }

    private companion object {
        const val URI = "untitled:Headers.x"
    }
}
