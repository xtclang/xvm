package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkEnclosingValueCompletionTest {
    @Test
    fun `enclosing instances fit ordinary named and qualified argument slots`() {
        listOf("§" to "this.Outer", "value = §" to "this.Outer", "this.Ou§" to "Outer").forEach { (argument, expected) ->
            val marked = "module Editing { class Outer { void take(Outer value) {} class Nested { void run() { take($argument); } } } }"
            val source = marked.replace("§", "")
            val at = marked.indexOf('§')
            XdkAdapter().use { adapter ->
                val original = adapter.compile(URI, source)
                assertThat(adapter.compile(URI, marked.replace(argument, "this.Outer")).diagnostics)
                    .describedAs("ordinary enclosing call: %s", marked)
                    .isEmpty()
                adapter.compile(URI, source)
                val items = adapter.getCompletions(URI, 0, at)
                assertThat(
                    items.map {
                        it.label
                    },
                ).describedAs("%s: %s", marked, adapter.analyzeAtAsync(URI, Position(0, at)).join()?.sites).contains(expected)
                val item = items.single { it.label == expected }
                assertThat(item.detail).contains("Enclosing instance")
                assertThat(adapter.getCachedResult(URI)).isEqualTo(original)
                val edit = item.textEdit!!
                assertThat(
                    adapter.compile(URI, source.replaceRange(edit.range.start.column, edit.range.end.column, edit.newText)).diagnostics,
                ).isEmpty()
            }
        }
    }

    @Test
    fun `static boundaries and incompatible argument types reject enclosing instances`() {
        listOf(
            "class Outer { static void run() { take(§); } } void take(Outer value) {}",
            "class Outer { static class Nested { void run() { take(§); } } } void take(Outer value) {}",
            "class Outer { void run() { take(§); } } void take(Int value) {}",
        ).forEach { body ->
            val marked = "module Editing { $body }"
            XdkAdapter().use { adapter ->
                adapter.compile(URI, marked.replace("§", ""))
                assertThat(adapter.getCompletions(URI, 0, marked.indexOf('§')))
                    .describedAs(marked)
                    .noneMatch { it.detail.contains("Enclosing instance") }
            }
        }
    }

    @Test
    fun `ordinary returns initializers and operands complete enclosing instances`() {
        listOf(
            "Owner current() = thi§;" to "this.Owner",
            "Owner current() = this.Ow§;" to "Owner",
            "void run() { Owner current = thi§; }" to "this.Owner",
            "Boolean same(Owner owner) = owner == thi§;" to "this.Owner",
        ).forEach { (body, expected) ->
            val marked = "module Editing { class Owner { class Nested { $body } } }"
            val source = marked.replace("§", "")
            XdkAdapter().use { adapter ->
                val cached = adapter.compile(URI, source)
                val item = adapter.getCompletions(URI, 0, marked.indexOf('§')).single { it.label == expected }
                assertThat(item.detail).isEqualTo("Accessible enclosing instance")
                assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
                val edit = item.textEdit!!
                assertThat(adapter.compile(URI, source.replaceRange(edit.range.start.column, edit.range.end.column, edit.newText)).diagnostics)
                    .describedAs(marked).isEmpty()
            }
        }
    }

    @Test
    fun `ordinary enclosing proposals respect static boundaries and expected types`() {
        listOf(
            "class Owner { static Owner current() = thi§; }",
            "class Owner { static class Nested { Owner current() = thi§; } }",
            "class Owner { Int current() = thi§; }",
        ).forEach { body ->
            val marked = "module Editing { $body }"
            XdkAdapter().use { adapter ->
                adapter.compile(URI, marked.replace("§", ""))
                assertThat(adapter.getCompletions(URI, 0, marked.indexOf('§'))).describedAs(marked)
                    .noneMatch { it.label == "this.Owner" }
            }
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
