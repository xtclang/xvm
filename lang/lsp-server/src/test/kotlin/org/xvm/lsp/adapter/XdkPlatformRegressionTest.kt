package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.MethodStructure
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.util.concurrent.TimeUnit.SECONDS

/** Minimized real-source failures from the platform demo, without a sibling checkout dependency. */
class XdkPlatformRegressionTest {
    @Test
    fun `platform CircularBuffer compiles and keeps anonymous enclosing receivers and named signatures`() {
        val source = javaClass.getResource("/platform/CircularBuffer.x")!!.readText()

        fun position(
            text: String,
            anchor: String,
        ): Position {
            val prefix = text.substring(0, text.indexOf(anchor) + anchor.length)
            return Position(prefix.count { it == '\n' }, prefix.substringAfterLast('\n').length)
        }
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            val incomplete = source.replace("this.CircularBuffer.size", "this.CircularBuffer.si")
            adapter.compile(URI, incomplete)
            val at = position(incomplete, "this.CircularBuffer.si")
            val input = Source(incomplete)
            repeat(incomplete.indexOf("this.CircularBuffer.si") + "this.CircularBuffer.si".length) { input.next() }
            val cursor = input.position
            input.reset()
            val errors = ErrorList()
            val tree = Parser.forPartialAnalysis(input, cursor, errors).parseSource()
            assertThat(errors.errors.map { it.code })
                .describedAs(tree.toDumpString())
                .containsOnly(Parser.INCOMPLETE_EXPRESSION)
            val analysisErrors = ErrorList()
            val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source(incomplete), cursor, null, analysisErrors)
            assertThat(analysis.pool()).describedAs("%s", analysisErrors).isPresent
            assertThat(analysis.sites()).hasSize(1)
            assertThat(analysis.sites().single().source).describedAs("%s", analysisErrors).isNotNull()
            val site = analysis.sites().single()
            assertThat(site.target.isValidated).describedAs("%s", analysisErrors).isTrue()
            assertThat(analysis.cursorBindings()).containsKey(site)
            val method =
                generateSequence(site as AstNode) { it.parent }
                    .filterIsInstance<MethodDeclarationStatement>()
                    .first()
                    .component as MethodStructure
            assertThat(method.ast).isNull()
            assertThat(method.hasOps()).isFalse()
            val items = adapter.getCompletions(URI, at.line, at.column)
            assertThat(items.map { it.label }).describedAs("partial=%s", adapter.analyzeAtAsync(URI, at).join()?.sites).contains("size")
            val size = items.single { it.label == "size" }
            assertThat(size.detail).contains("Int")
            assertThat(
                size.textEdit!!
                    .range.start.column,
            ).isEqualTo(at.column - 2)
            val constructor = source.replace("new Element?[capacity]", "new Element?[cap]")
            adapter.compile(URI, constructor)
            val dimension = position(constructor, "new Element?[cap")
            assertThat(adapter.getCompletions(URI, dimension.line, dimension.column).map { it.label }).contains("capacity")
            adapter.compile(URI, source)
            val call = position(source, "toArray().toString(sep, pre, post, limit, trunc, ")
            val help = adapter.getSignatureHelp(URI, call.line, call.column)!!
            assertThat(help.signatures[help.activeSignature].parameters.map { it.label }).anyMatch { "render" in it }
            assertThat(help.activeParameter).isEqualTo(5)
        }
    }

    @Test
    fun `real platform buffer callback accepts an expected type lambda template`() {
        val fixture = javaClass.getResource("/platform/CircularBuffer.x")!!.readText()
        val line = "    void inspect() { val buffer = new CircularBuffer<Int>(4); buffer.toString(render = ); }"
        val source = fixture.replace("module PlatformBuffer {", "module PlatformBuffer {\n$line")
        val row = source.lines().indexOf(line)
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, source)
            val item = adapter.getCompletions(URI, row, line.indexOf("render = ") + 9).single { it.label == "(arg1) -> TODO()" }
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(adapter.compile(URI, source.replace("render = )", "render = ${item.insertText})")).diagnostics).isEmpty()
        }
    }

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
        ).forEach { (call, parameter) ->
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
