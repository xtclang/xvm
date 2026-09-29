package org.xvm.lsp.adapter

import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter

/** Minimized real-source failures from the platform demo, without a sibling checkout dependency. */
class XdkPlatformRegressionTest {
    @Test
    fun `hover names the selected generic callee rather than its enclosing method`() {
        CompilerTestSupport.configure()
        val source = "module Demo { <T> T echo(T value) = value; String run() = echo(\"value\"); }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            assertThat(adapter.getHoverInfo(URI, 0, source.lastIndexOf("echo")))
                .contains("String echo(String value)")
                .doesNotContain("run")
        }
    }

    @Test
    fun `whitespace before a written argument retains selected signature metadata and named mapping`() {
        CompilerTestSupport.configure()
        listOf(
                "echo(\"a\", §\"b\")" to 1,
                "echo(backup = \"b\", §value = \"a\")" to 0,
            )
            .forEach { (call, parameter) ->
                val marked =
                    "module Demo { <T> T echo(T value, T backup) = value; String run() = $call; }"
                XdkAdapter().use { adapter ->
                    assertThat(adapter.compile(URI, marked.replace("§", "")).diagnostics).isEmpty()
                    val help = adapter.getSignatureHelp(URI, 0, marked.indexOf('§'))!!
                    assertThat(help.signatures.single().parameters).hasSize(2)
                    assertThat(help.activeParameter).isEqualTo(parameter)
                }
            }
    }

    @Test
    fun `member completion before existing parentheses retains direct and chained receivers`() {
        CompilerTestSupport.configure()
        listOf("text.tr§()", "text.trim().tr§()").forEach { expression ->
            val marked = "module Demo { String run(String text) = $expression; }"
            XdkAdapter().use { adapter ->
                adapter.compile(URI, marked.replace("§", ""))
                val items =
                    adapter.getCompletionsAsync(URI, 0, marked.indexOf('§')).get(30, SECONDS)
                assertThat(items.map { it.label }).contains("trim")
            }
        }
    }

    @Test
    fun `narrowed JSON object resolves its underlying bundled map type`() {
        CompilerTestSupport.configure()
        val source =
            "module Demo { package json import json.xtclang.org; import json.Doc; import json.JsonObject; " +
                "String run(Doc value) { assert value.is(JsonObject); return value.toString(); } }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            val locations = adapter.findTypeDefinitions(URI, 0, source.lastIndexOf("value"))
            assertThat(locations).isNotEmpty()
            assertThat(locations.map { it.uri }).anyMatch { it.endsWith("/Map.x") }
        }
    }

    private companion object {
        const val URI = "file:///Demo.x"
    }
}
