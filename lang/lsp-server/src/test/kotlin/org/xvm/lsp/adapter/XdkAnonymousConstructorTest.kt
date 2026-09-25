package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ClassStructure
import org.xvm.asm.Component
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.MethodStructure
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.NewExpression
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.toDependency

class XdkAnonymousConstructorTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "new Base<String>(\"x\", te|) { String read() = text; }",
            "new Base<String>(second=\"x\", first=te|) { String read() = text; }",
            "new @Tagged Base<String>(\"x\", te|) { String read() = text; }",
            "new AbstractBase(\"x\", te|) { @Override String read() = text; }",
            "new Object(1, te|) { construct(Int first, String second) {} String read() = text; }",
            "new Reader(\"x\", te|) { construct(String first, String second) {} @Override String read() = text; }",
            "new Base<String>(\"x\", te|) { Int next() { return ++captured; } }",
            "Base<String> value = new Base(\"x\", te|) { String read() = text; }",
            "new Base<String>(\"x\", te|) { String text=\"body\"; String read() = text; }",
        ],
    )
    fun `anonymous constructors fit written arguments without emitting or capturing the body`(expression: String) {
        val prefix = HEADER + expression.substringBefore('|')
        val suffix = expression.substringAfter('|') + "; } }"
        val complete = prefix.dropLast(2) + "text" + suffix
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, complete).diagnostics).describedAs(expression).isEmpty()
            val cached = adapter.compile(URI, prefix + suffix)
            val help = adapter.getSignatureHelp(URI, 0, prefix.length)
            assertThat(help).describedAs(expression).isNotNull()
            assertThat(help!!.signatures.map { it.label }).noneMatch { it.contains(":1") }
            assertThat(help.signatures.map { it.activeParameter }).containsOnly(if (expression.contains("first=te")) 0 else 1)
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).describedAs(expression).containsExactly("text")
            assertThat(
                items.single().textEdit,
            ).isEqualTo(TextEdit(Range(Position(0, prefix.length - 2), Position(0, prefix.length)), "text"))
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(adapter.compile(URI, complete).diagnostics).isEmpty()
        }
        val (analysis, errors) = analyze(prefix, suffix)
        assertThat(errors.errors.map { it.code }).describedAs(expression).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        val site = analysis.sites().single()
        val creation = site.target as NewExpression
        val shell = creation.children().filterIsInstance<TypeCompositionStatement>().single()
        val owner = shell.component.parent
        assertThat(creation.sourceBindings).isNull()
        assertThat(creation.captureOrigins).isEmpty()
        assertThat(site.source.toRawString()).isEqualTo(prefix + suffix)
        assertThat(owner.children().filterIsInstance<ClassStructure>()).containsExactly(shell.component as ClassStructure)
        assertThat(components(owner).filterIsInstance<MethodStructure>()).allSatisfy { method ->
            assertThat(method.ast).describedAs(method.identityConstant.toString()).isNull()
            assertThat(method.hasOps()).isFalse()
        }
        val nodes = analysis.sourceTrees().flatMap(::nodes)
        assertThat(nodes.filterIsInstance<NewExpression>()).contains(creation)
        assertThat(analysis.cursorBindings()[site]!!.candidates()).isNotEmpty()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "new AbstractBase(\"x\", te|) {}",
            "new Reader(\"x\", te|) { @Override String read() = text; }",
            "new Base<String>(True, te|) { String read() = text; }",
            "new Missing(\"x\", te|) {}",
            "new Base<String>(\"x\", missing=te|) {}",
        ],
    )
    fun `invalid anonymous construction never offers an ordinary base call`(expression: String) {
        val prefix = HEADER + expression.substringBefore('|')
        val suffix = expression.substringAfter('|') + "; } }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, prefix.dropLast(2) + "text" + suffix).diagnostics).isNotEmpty()
            adapter.compile(URI, prefix + suffix)
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).describedAs(expression).isNull()
            assertThat(adapter.getCompletions(URI, 0, prefix.length)).describedAs(expression).isEmpty()
        }
    }

    @Test
    fun `empty anonymous slots show real constructors instead of an unusable generated default`() {
        for (prefixValue in listOf("", "te")) {
            val prefix = HEADER + "new Base<String>($prefixValue"
            val suffix = ") { String read() = text; }; } }"
            XdkAdapter().use { adapter ->
                adapter.compile(URI, prefix + suffix)
                val help = adapter.getSignatureHelp(URI, 0, prefix.length)!!
                assertThat(help.signatures.map { it.label }).containsExactly("new Base(String first, String second)")
                assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).contains("text")
                assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).doesNotContain("textNumber")
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["public", "protected", "private"])
    fun `anonymous superclass constructors respect dependency access`(access: String) {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val dependency =
            EmbeddingSupport.instance().compileModule(
                Source("module Library { class Base<T> { $access construct(T first, T second) {} } }", "Library.x"),
                null,
                errors,
            )
        assertThat(errors.errors).isEmpty()
        val prefix = "module Editing { package lib import Library; void run(String text, Int textNumber) { new lib.Base<String>(\"x\", te"
        val suffix = ") { String read() = text; }; } }"
        XdkAdapter().use { adapter ->
            adapter.replaceDependencies(listOf(dependency.toDependency()))
            val completed = adapter.compile(URI, prefix.dropLast(2) + "text" + suffix)
            assertThat(completed.diagnostics.isEmpty()).isEqualTo(access != "private")
            adapter.compile(URI, prefix + suffix)
            val help = adapter.getSignatureHelp(URI, 0, prefix.length)
            assertThat(help != null).isEqualTo(access != "private")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label })
                .containsExactlyElementsOf(if (access == "private") emptyList() else listOf("text"))
        }
    }

    @Test
    fun `interface default constructors keep their zero argument signature`() {
        val prefix = HEADER + "new Reader("
        val suffix = ") { @Override String read() = text; }; } }"
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, prefix + suffix)
            assertThat(cached.diagnostics).isEmpty()
            val help = adapter.getSignatureHelp(URI, 0, prefix.length)!!
            assertThat(help.signatures.map { it.label }).containsExactly("new Reader()")
            assertThat(adapter.getCompletions(URI, 0, prefix.length)).isEmpty()
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
        }
    }

    @Test
    fun `editing the anonymous declaration invalidates its candidate types`() {
        val prefix = HEADER + "new Object(1, te"
        XdkAdapter().use { adapter ->
            for ((type, selected) in listOf("String" to "text", "Int" to "textNumber", "String" to "text")) {
                val suffix = ") { construct(Int first, $type second) {} }; } }"
                assertThat(adapter.compile(URI, prefix.dropLast(2) + selected + suffix).diagnostics).isEmpty()
                val cached = adapter.compile(URI, prefix + suffix)
                assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).containsExactly(selected)
                assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)!!.signatures.map { it.label })
                    .containsExactly("new Object(Int first, $type second)")
                assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            }
        }
    }

    @Test
    fun `missing argument closer keeps the anonymous body and its source constructor`() {
        val prefix = HEADER + "new Reader(\"x\", te"
        val suffix = " { construct(String first, String second) {} @Override String read() = text; }; } }"
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, prefix + suffix)
            assertThat(cached.diagnostics).isNotEmpty()
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).containsExactly("text")
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)!!.signatures.map { it.label })
                .containsExactly("new Reader(String first, String second)")
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(adapter.compile(URI, prefix.dropLast(2) + "text)" + suffix).diagnostics).isEmpty()
        }
    }

    @Test
    fun `anonymous cursor respects cancellation and the first error budget`() {
        CompilerTestSupport.configure()
        val prefix = HEADER + "new Base<String>(\"x\", te"
        val suffix = ") { String read() = text; }; } }"
        for (errors in listOf(ErrorList(ErrorList.FIRST_ERROR), ErrorListener.cancellable(ErrorList()) { true })) {
            val source = Source(prefix + suffix, URI)
            repeat(prefix.length) { source.next() }
            val cursor = source.position
            source.reset()
            val analysis = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, errors)
            assertThat(analysis.pool()).isEmpty()
            assertThat(analysis.cursorBindings()).isEmpty()
        }
    }

    private fun analyze(
        prefix: String,
        suffix: String,
    ): Pair<EmbeddingSupport.PartialAnalysis, ErrorList> {
        CompilerTestSupport.configure()
        val source = Source(prefix + suffix, URI)
        repeat(prefix.length) { source.next() }
        val cursor = source.position
        source.reset()
        val errors = ErrorList()
        return EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, errors) to errors
    }

    private fun nodes(node: AstNode): List<AstNode> = listOf(node) + node.children().flatMap(::nodes)

    private fun components(component: Component): List<Component> = listOf(component) + component.children().flatMap(::components)

    private companion object {
        const val URI = "untitled:Editing.x"
        const val HEADER =
            "module Editing { class Base<T> { construct(T first, T second) {} } annotation Tagged into Object {} " +
                "@Abstract class AbstractBase { construct(String first, String second) {} String read(); } " +
                "interface Reader { String read(); } void run(String text, Int textNumber) { Int captured=1; "
    }
}
