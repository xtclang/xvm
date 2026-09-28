package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkWrittenFormalTest {
    private val URI = "untitled:Formals.x"

    @ParameterizedTest
    @ValueSource(strings = [
        "<Element extends String> void damaged(Ele§ value) {}",
        "class Damaged<Element extends String>(Ele§ value) {}",
        "<Element extends String, Other extends Element> void damaged(Oth§ value) {}",
        "<String> void damaged(Str§ value) {}",
    ])
    fun `written formals have their own bound and exact completion edit`(header: String) {
        val marked = "module Formals { $header }"
        val at = marked.indexOf('§')
        val source = marked.replace("§", "")
        val name = when {
            "Oth§" in marked -> "Other"
            "Str§" in marked -> "String"
            else -> "Element"
        }
        XdkAdapter().use { adapter ->
            val baseline = adapter.compile(URI, source)
            val item = adapter.getCompletions(URI, 0, at).single { it.label == name }
            assertThat(item.detail).isEqualTo("type parameter $name extends ${if (name == "String") "Object" else "String"}")
            assertThat(item.textEdit).isEqualTo(TextEdit(Range(Position(0, at - 3), Position(0, at)), name))
            assertThat(adapter.getCachedResult(URI)).isEqualTo(baseline)
            assertThat(adapter.compile(URI, source.replaceRange(at - 3, at, name)).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "<Element extends Missing> void damaged(Ele§ value) {}",
        "<Element extends Element> void damaged(Ele§ value) {}",
        "<Element extends Other, Other extends Element> void damaged(Ele§ value) {}",
        "<Element extends Missing> void damaged(List<Ele§> value) {}",
    ])
    fun `unresolved and cyclic bounds cannot borrow an outer type`(header: String) {
        val marked = "module Formals { class Element {} $header }"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, marked.replace("§", ""))
            assertThat(adapter.getCompletions(URI, 0, marked.indexOf('§')).map { it.label }).doesNotContain("Element")
        }
    }

    @Test
    fun `formal qualifier uses the resolved parameterized constraint`() {
        val marked = "module Formals { class Owner<T> { typedef T Item; } <Element extends Owner<String>> void damaged(Element.It§ value) {} }"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, marked.replace("§", ""))
            val item = adapter.getCompletions(URI, 0, marked.indexOf('§')).single { it.label == "Item" }
            assertThat(item.detail).contains("String")
            assertThat(adapter.compile(URI, marked.replace("It§", "Item")).diagnostics).isEmpty()
        }
    }
}
