package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkWrittenFormalTest {
    private val uri = "untitled:Formals.x"

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "<Element extends String> void damaged(Ele§ value) {}",
                "class Damaged<Element extends String>(Ele§ value) {}",
                "<Element extends String, Other extends Element> void damaged(Oth§ value) {}",
                "<String> void damaged(Str§ value) {}",
            ]
    )
    fun `written formals have their own bound and exact completion edit`(header: String) {
        val marked = "module Formals { $header }"
        val at = marked.indexOf('§')
        val source = marked.replace("§", "")
        val name =
            when {
                "Oth§" in marked -> "Other"
                "Str§" in marked -> "String"
                else -> "Element"
            }
        XdkAdapter().use { adapter ->
            val baseline = adapter.compile(uri, source)
            val item = adapter.getCompletions(uri, 0, at).single { it.label == name }
            assertThat(item.detail)
                .isEqualTo(
                    "type parameter $name extends ${if (name == "String") "Object" else "String"}"
                )
            assertThat(item.textEdit)
                .isEqualTo(TextEdit(Range(Position(0, at - 3), Position(0, at)), name))
            assertThat(adapter.getCachedResult(uri)).isEqualTo(baseline)
            assertThat(adapter.compile(uri, source.replaceRange(at - 3, at, name)).diagnostics)
                .isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "<Element extends Missing> void damaged(Ele§ value) {}",
                "<Element extends Element> void damaged(Ele§ value) {}",
                "<Element extends Other, Other extends Element> void damaged(Ele§ value) {}",
                "<Element extends Missing> void damaged(List<Ele§> value) {}",
            ]
    )
    fun `unresolved and cyclic bounds cannot borrow an outer type`(header: String) {
        val marked = "module Formals { class Element {} $header }"
        XdkAdapter().use { adapter ->
            adapter.compile(uri, marked.replace("§", ""))
            assertThat(adapter.getCompletions(uri, 0, marked.indexOf('§')).map { it.label })
                .doesNotContain("Element")
        }
    }

    @Test
    fun `incomplete header hover describes a written formal as a bound`() {
        val source = "module Formals { <Element extends String> void damaged(Element value }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(uri, source).success).isFalse()
            val partial =
                adapter.analyzeAtAsync(uri, Position(0, source.lastIndexOf("Element") + 3)).join()
            assertThat(partial?.sites?.flatMap { it.formals }?.map { it.name })
                .describedAs("partial=%s", partial?.sites)
                .contains("Element")
            assertThat(adapter.getHoverInfo(uri, 0, source.lastIndexOf("Element") + 2))
                .contains("type parameter Element extends String")
        }
    }

    @Test
    fun `recursive arguments in a concrete formal bound remain copyable`() {
        val source =
            "module Formals { interface Chain<T> {} <Element extends Chain<Element>> void run(Element value) {} }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(uri, source).diagnostics).isEmpty()
            assertThat(adapter.getHoverInfo(uri, 0, source.indexOf("value"))).contains("Element")
        }
    }

    @Test
    fun `formal qualifier uses the resolved parameterized constraint`() {
        val marked =
            "module Formals { class Owner<T> { class Item {} typedef T as Alias; static class ItemStatic {} } " +
                "<Element extends Owner<String>> void damaged(Element.It§ value) {} }"
        XdkAdapter().use { adapter ->
            adapter.compile(uri, marked.replace("§", ""))
            val item =
                adapter.getCompletions(uri, 0, marked.indexOf('§')).single { it.label == "Item" }
            assertThat(item.detail).contains("String")
            assertThat(adapter.compile(uri, marked.replace("It§", "Item")).diagnostics).isEmpty()
            listOf("Ali§", "ItemSt§").forEach { leaf ->
                val rejected = marked.replace("It§", leaf)
                adapter.compile(uri, rejected.replace("§", ""))
                assertThat(adapter.getCompletions(uri, 0, rejected.indexOf('§'))).isEmpty()
            }
        }
    }
}
