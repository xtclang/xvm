package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.MethodStructure
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.semanticSnapshot
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS

class XdkDelimiterRecoveryTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(
        strings = [
            "(value.|", "((value.si|", "use((value.|", "values[value.|", "use(values[value.|", "values[value.si|]",
            "use((value.si|)", "values[(value.si|]", "use(values[value.si|)", "pair((value.si|, 2)",
        ],
    )
    fun `completion retains a cursor inside unclosed grouping calls and indexes`(expression: String) {
        val prefix = "$HEADER return ${expression.substringBefore('|')}"
        val suffix = expression.substringAfter('|') + ";"
        XdkAdapter().use { adapter ->
            val result = adapter.compile(URI, "$prefix$suffix } Int later() = 42; }")
            assertThat(result.diagnostics).isNotEmpty()
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).describedAs(expression).contains("size")
            assertThat(adapter.getCachedResult(URI)).isEqualTo(result)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["(pair(1, ", "use((pair(1, ", "values[pair(1, ", "use(values[pair(1, ", "(fn(", "use(new Box("])
    fun `signature help retains the innermost call with missing enclosing delimiters`(expression: String) {
        val prefix = "$HEADER return $expression"
        XdkAdapter().use { adapter ->
            val result = adapter.compile(URI, "$prefix; } Int later() = 42; }")
            assertThat(result.diagnostics).isNotEmpty()
            val help = adapter.getSignatureHelp(URI, 0, prefix.length)
            assertThat(help).describedAs(expression).isNotNull()
            val signature = requireNotNull(help).signatures.single()
            assertThat(signature.label).isEqualTo(
                when {
                    expression.contains("fn(") -> "Int fn(Int)"
                    expression.contains("new Box(") -> "new Box(Int n)"
                    else -> "Int pair(Int first, Int second)"
                },
            )
            assertThat(signature.activeParameter).isEqualTo(if (expression.contains("pair(")) 1 else 0)
            assertThat(adapter.getCachedResult(URI)).isEqualTo(result)
        }
    }

    @Test
    fun `cursor at EOF retains missing expression and block delimiters`() {
        val prefix = "$HEADER return use((value."
        XdkAdapter().use { adapter ->
            adapter.compile(URI, prefix)
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).contains("size")
        }
    }

    @Test
    fun `cursor statements can end at the enclosing brace without a semicolon`() {
        for (statement in listOf("(value.", "1 + value.", "return (value.", "Int result = (value.")) {
            val prefix = "$HEADER $statement"
            XdkAdapter().use { adapter ->
                adapter.compile(URI, "$prefix } Int later() = 42; }")
                assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).describedAs(statement).contains("size")
            }
        }
    }

    @Test
    fun `unrelated syntax damage is not repaired by a cursor hole`() {
        XdkAdapter().use { adapter ->
            for ((expression, suffix) in listOf(
                "(value." to "; } void broken( { } }",
                "values[1 + , value." to "; } }",
                "(value." to " + ; } }",
            )) {
                val prefix = "$HEADER return $expression"
                adapter.compile(URI, prefix + suffix)
                assertThat(adapter.getCompletions(URI, 0, prefix.length)).describedAs(expression + suffix).isEmpty()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["; } }", ""])
    fun `delimiter recovery preserves the original source and never emits the incomplete method`(suffix: String) {
        CompilerTestSupport.configure()
        val prefix = "$HEADER return use((value.si"
        val text = prefix + suffix
        val source = Source(text, URI)
        repeat(prefix.length) { source.next() }
        val cursor = source.position
        source.reset()
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, errors)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        assertThat(analysis.pool()).isPresent()
        val site = analysis.sites().single()
        assertThat(site.source.toRawString()).isEqualTo(text)
        assertThat(site.endPosition).isEqualTo(cursor)
        val method = generateSequence(site.parent) { it.parent }.filterIsInstance<MethodDeclarationStatement>().first()
        assertThat((method.component as MethodStructure).ast).isNull()
        val model = analysis.semanticSnapshot(errors)
        assertThat(
            model.sites
                .single()
                .members
                .map { it.name },
        ).contains("size")
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
    }

    @Test
    fun `missing delimiters in a member use unsaved module overlays and flow narrowing`() {
        val root = directory.resolve("Editing.x").toFile().canonicalFile
        val member = directory.resolve("Editing/Child.x").toFile().canonicalFile
        member.parentFile.mkdirs()
        root.writeText("module Editing { class Base { Int value = 1; } }")
        val prefix = "class Child extends Base { Int run(Object other) { if (other.is(String)) { return (value.si"
        member.writeText("$prefix; } return 0; } }")
        XdkAdapter().use { adapter ->
            adapter.compile(root.toURI().toString(), "module Editing { class Base { String value = \"overlay\"; } }")
            val uri = member.toURI().toString()
            adapter.compile(uri, member.readText())
            val cached = adapter.getCachedResult(uri)
            val model = adapter.analyzeAtAsync(uri, Position(0, prefix.length)).get(10, SECONDS)!!
            assertThat(
                model.sites
                    .single()
                    .members
                    .map { it.name },
            ).contains("size")
            assertThat(adapter.getCachedResult(uri)).isEqualTo(cached)
            val narrowed = prefix.replace("(value.si", "(other.si")
            adapter.compile(uri, "$narrowed; } return 0; } }")
            assertThat(adapter.getCompletions(uri, 0, narrowed.length).map { it.label }).contains("size")
            assertThat(root.readText()).contains("Int value")
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
        const val HEADER =
            "module Editing { Int use(Int n) = n; Int pair(Int first, Int second) = first; " +
                "class Box { construct(Int n) {} } " +
                "Int run(String value, Int[] values, function Int(Int) fn) {"
    }
}
