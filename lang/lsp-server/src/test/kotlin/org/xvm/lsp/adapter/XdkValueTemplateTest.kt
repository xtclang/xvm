package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkValueTemplateTest {
    @Test
    fun `lambda templates fit expected arity and compile in named ordinary and constructor arguments`() {
        listOf(
            "void take(function Int(Int) action) {} void run() { take(§); }" to "(arg1) -> TODO()",
            "void take(function void() action) {} void run() { take(action = §); }" to "() -> TODO()",
            "void take(function Int(Int, Int) action) {} void run() { take(§); }" to "(arg1, arg2) -> TODO()",
            "class Box(function String(Int) action) {} void run() { Box box = new Box(§); }" to "(arg1) -> TODO()",
            "void run(function void(function Int(Int)) callback) { callback(§); }" to "(arg1) -> TODO()",
        ).forEach { (body, expected) ->
            val marked = "module Editing { $body }"
            val source = marked.replace("§", "")
            XdkAdapter().use { adapter ->
                val cached = adapter.compile(URI, source)
                val item = adapter.getCompletions(URI, 0, marked.indexOf('§')).single { it.label == expected }
                assertThat(item.kind).isEqualTo(CompletionItem.CompletionKind.SNIPPET)
                assertThat(item.snippet).contains("${'$'}{1:TODO()}${'$'}0")
                assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
                val edit = item.textEdit!!
                assertThat(
                    adapter.compile(URI, source.replaceRange(edit.range.start.column, edit.range.end.column, edit.newText)).diagnostics,
                ).describedAs(marked)
                    .isEmpty()
            }
        }
    }

    @Test
    fun `template trials refuse nonfunction slots incompatible siblings and qualified prefixes`() {
        listOf(
            "void take(Int value) {} void run() { take(§); }",
            "void take(function Int(Int) action, String value) {} void run() { take(§, 1); }",
            "void take(function Int(Int) action) {} void run(String text) { take(text.§); }",
        ).forEach { body ->
            val marked = "module Editing { $body }"
            XdkAdapter().use { adapter ->
                adapter.compile(URI, marked.replace("§", ""))
                assertThat(adapter.getCompletions(URI, 0, marked.indexOf('§')))
                    .describedAs(marked)
                    .noneMatch { it.detail == "Lambda fitting this argument" }
            }
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
