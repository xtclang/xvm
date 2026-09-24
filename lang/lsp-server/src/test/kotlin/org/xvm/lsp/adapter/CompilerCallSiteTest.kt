package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ConstantPool
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.ModuleRepository
import org.xvm.compiler.BuildRepository
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.lsp.adapter.xdk.PartialSemanticModel
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.semanticSnapshot
import java.util.concurrent.Executors

class CompilerCallSiteTest {
    @Test
    fun `function valued calls expose typed signatures without inventing method targets`() {
        val source = "module Calls { Int apply(function Int(Int) fn) { return fn(42); } }"
        val compilation = compile(source)
        val model = compilation.semanticSnapshot()
        assertThat(model.calls).isEmpty()
        val call = model.functionCalls.single()
        assertThat(call.signature.parameters.map { model.type(it.type)!!.displayName }).containsExactly("Int")
        assertThat(call.signature.parameters.map { it.name }).containsExactly(null)
        assertThat(call.signature.returns.map { model.type(it)!!.displayName }).containsExactly("Int")
        assertThat(compilation.functionBindings()).hasSize(1)
        val node = compilation.functionBindings().keys.single()
        assertThat(compilation.functionBindings()[node.clone() as InvocationExpression]).isNull()
        assertThatThrownBy { (compilation.functionBindings() as MutableMap).clear() }.isInstanceOf(
            UnsupportedOperationException::class.java,
        )
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).success).isTrue()
            val help = requireNotNull(adapter.getSignatureHelp(URI, 0, source.indexOf("42") + 1))
            assertThat(help.signatures.single().label).isEqualTo("Int fn(Int)")
            assertThat(help.activeParameter).isZero()
        }
    }

    @Test
    fun `super records the inherited body and instantiated signature`() {
        val model =
            compile(
                "module Calls { class Base<T> { T pick(T value)=value; } " +
                    "class Child extends Base<String> { @Override String pick(String value)=super(value); } }",
            ).semanticSnapshot()
        val call = model.calls.single()
        assertThat(model.symbol(call.method)!!.name).isEqualTo("pick")
        assertThat(
            model
                .symbol(call.method)!!
                .declaration!!
                .start.column,
        ).isEqualTo(33)
        assertThat(call.signature.parameters.map { model.type(it.type)!!.displayName }).containsExactly("String")
        assertThat(call.signature.returns.map { model.type(it)!!.displayName }).containsExactly("String")
    }

    @Test
    fun `selected generic calls copy instantiated signatures and named argument ordering`() {
        val source =
            """
            module Calls {
                <T> T echo(T value, T backup) { return value; }
                void run() { String text = echo(backup="b", value="a"); }
            }
            """.trimIndent()
        val compilation = compile(source)
        val model = compilation.semanticSnapshot()
        val call = model.calls.single { model.symbol(it.method)?.name == "echo" }
        assertThat(call.signature.parameters.map { it.name }).containsExactly("value", "backup")
        assertThat(call.signature.parameters.map { model.type(it.type)!!.displayName }).allSatisfy { assertThat(it).contains("String") }
        assertThat(call.signature.returns.map { model.type(it)!!.displayName }).containsExactly("String")
        assertThat(call.arguments.map { it.parameterIndex }).containsExactly(1, 0)
        assertThat(call.arguments).allSatisfy { assertThat(it.named).isTrue() }
        assertThat(call.arguments.map { source.lines()[it.range.start.line].substring(it.range.start.column, it.range.end.column) })
            .containsExactly("backup=\"b\"", "value=\"a\"")

        ConstantPool.withPool(compilation.pool()).use {
            val node = nodes(compilation.parsed()).filterIsInstance<InvocationExpression>().single { it.resolvedMethod?.name == "echo" }
            assertThat(compilation.callBindings()[node]).isNotNull()
            assertThat(compilation.callBindings()[node.clone() as InvocationExpression]).isNull()
            assertThatThrownBy { (compilation.callBindings() as MutableMap).clear() }.isInstanceOf(
                UnsupportedOperationException::class.java,
            )
        }
    }

    @Test
    fun `defaulted arguments retain their declaration metadata without synthetic source ranges`() {
        val model =
            compile("module Calls { Int add(Int left=1, Int right=2) { return left; } void run() { Int n=add(right=4); } }")
                .semanticSnapshot()
        val call = model.calls.single { model.symbol(it.method)?.name == "add" }
        assertThat(call.signature.parameters).hasSize(2).allSatisfy { assertThat(it.defaulted).isTrue() }
        assertThat(call.arguments.map { it.parameterIndex }).containsExactly(1)
        assertThat(
            call.arguments
                .single()
                .range.start,
        ).isLessThan(
            call.arguments
                .single()
                .range.end,
        )
    }

    @Test
    fun `foreign receiver candidates use substituted types and exclude inaccessible members`() {
        val library =
            compile(
                """
                module Library {
                    class Box<Element> {
                        Element echo(Element value) { return value; }
                        String choose(String value) { return value; }
                        Int choose(Int value) { return value; }
                        String label="box";
                        private String secret() { return "hidden"; }
                        private String hidden="hidden";
                    }
                }
                """.trimIndent(),
            )
        val repository = BuildRepository().apply { storeModule(library.module()) }
        val model = partial("module Consumer { package Lib import Library; void run(Lib.Box<String> box) { box.", repository)
        val site = model.sites.single()
        assertThat(model.semantics.status).isEqualTo(SemanticModel.Status.PARTIAL)
        assertThat(model.semantics.symbol(site.scope!!)!!.name).isEqualTo("run")
        assertThat(site.members.map { it.name }).contains("echo", "choose", "label").doesNotContain("secret", "hidden")
        val echo = site.members.single { it.name == "echo" }.signature!!
        assertThat(echo.parameters.map { model.semantics.type(it.type)!!.displayName }).containsExactly("String")
        assertThat(echo.returns.map { model.semantics.type(it)!!.displayName }).containsExactly("String")
        assertThat(site.members.filter { it.name == "choose" }).hasSize(2)
        assertThatThrownBy { (site.members as MutableList).clear() }.isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun `incomplete call candidates have no selected overload and count only top-level commas`() {
        val model =
            partial(
                "module Calls { void run(String text) { text.indexOf(text.replace(\"a,b\", \"c\"), startAt=2, ",
            )
        val site = model.sites.single()
        assertThat(site.kind).isEqualTo(PartialSemanticModel.Kind.CALL)
        assertThat(site.calleeName).isEqualTo("indexOf")
        assertThat(site.members).isNotEmpty().allSatisfy { assertThat(it.name).isEqualTo("indexOf") }
        assertThat(site.argumentIndexAt(site.range.end)).isEqualTo(2)
        assertThat(site.arguments.map { it.label }).containsExactly(null, "startAt")
        assertThat(model.semantics.calls.map { model.semantics.symbol(it.method)?.name }).contains("replace").doesNotContain("indexOf")
    }

    @Test
    fun `inherited receiver type parameters are instantiated at the selected call`() {
        val model =
            compile(
                """
                module Inherited {
                    class Base<Element> { Element echo(Element value) { return value; } }
                    class Child extends Base<String> {}
                    String run(Child child) { return child.echo("ok"); }
                }
                """.trimIndent(),
            ).semanticSnapshot()
        val call = model.calls.single { model.symbol(it.method)?.name == "echo" }
        assertThat(call.signature.parameters.map { model.type(it.type)!!.displayName }).containsExactly("String")
        assertThat(call.signature.returns.map { model.type(it)!!.displayName }).containsExactly("String")
    }

    @Test
    fun `own receiver exposes private members and copied facts survive other compilations and threads`() {
        val model = partial("module Own { private String hidden=\"value\"; private Int secret() {return 1;} void run() { this.")
        val site = model.sites.single()
        assertThat(site.members.map { it.name }).contains("hidden", "secret")
        val firstScope = site.scope!!
        val next = partial("module Own { void run(Int number) { number.")
        assertThat(next.semantics.symbol(firstScope)).isNull()
        Executors.newSingleThreadExecutor().use { executor ->
            val copied =
                executor
                    .submit<List<String>> {
                        site.members.filter { it.name == "hidden" }.map { model.semantics.type(it.type!!)!!.displayName }
                    }.get()
            assertThat(copied).containsExactly("String")
        }
        assertThatThrownBy { (model.semantics.calls as MutableList).clear() }.isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun `failed overload resolution and partial applications cannot claim selected call facts`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val failed =
            EmbeddingSupport.instance().compileModule(
                Source("module Failed { Int choose(Int n) { return n; } void run() { Int n=choose(\"bad\"); }", URI),
                null,
                errors,
            )
        assertThat(failed.succeeded()).isFalse()
        assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
        assertThat(failed.semanticSnapshot().calls).isEmpty()
        val binding =
            compile(
                "module Binding { Int choose(Int n) { return n; } void run() { function Int(Int) fn=&choose(_); Int n=fn(1); } }",
            ).semanticSnapshot()
        assertThat(binding.calls).isEmpty()
        assertThat(binding.functionCalls).hasSize(1)
        assertThat(
            binding.functionCalls
                .single()
                .signature.parameters,
        ).allSatisfy { assertThat(it.name).isNull() }
    }

    @Test
    fun `nested compilation records surviving lambdas anonymous methods and initializers once`() {
        val compilation =
            compile(
                """
                module Nested {
                    String label=1.toString();
                    String run(Int number) {
                        function String() fn = () -> number.toString();
                        Object object = new Object() {
                            @Override String toString() { return number.toString(); }
                        };
                        return object.toString();
                    }
                }
                """.trimIndent(),
            )
        val model = compilation.semanticSnapshot()
        val calls = model.calls.filter { model.symbol(it.method)?.name == "toString" }
        assertThat(calls).hasSize(4)
        assertThat(calls.map { it.range.start.line }).containsExactly(1, 3, 5, 7)
        assertThat(calls.map { it.signature.returns.map { type -> model.type(type)!!.displayName } })
            .allSatisfy { assertThat(it).containsExactly("String") }
        assertThat(nodes(compilation.parsed()).toSet()).containsAll(compilation.callBindings().keys)
    }

    @Test
    fun `unknown receiver and cancellation do not produce candidate members`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source("module Missing { void run() { unknown.", URI), null, errors)
        val model = analysis.semanticSnapshot(errors)
        assertThat(model.sites.single().receiverType).isNull()
        assertThat(model.sites.single().members).isEmpty()
        val cancelled = analysis.semanticSnapshot(ErrorListener.cancellable(errors) { true })
        assertThat(cancelled.sites).isEmpty()
        assertThat(cancelled.semantics.status).isEqualTo(SemanticModel.Status.UNAVAILABLE)
    }

    private fun compile(text: String): EmbeddingSupport.Compilation {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(Source(text, URI), null, errors)
        assertThat(errors.errors).describedAs("%s", errors.errors).isEmpty()
        assertThat(result.succeeded()).isTrue()
        return result
    }

    private fun partial(
        text: String,
        repository: ModuleRepository? = null,
    ): PartialSemanticModel {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), repository, errors).semanticSnapshot(errors)
        assertThat(errors.errors.map { it.code }).describedAs("%s", errors.errors).containsExactly(Parser.UNEXPECTED_EOF)
        return result
    }

    private fun nodes(root: AstNode): Sequence<AstNode> =
        sequence {
            yield(root)
            for (child in root.children()) yieldAll(nodes(child))
        }

    private companion object {
        const val URI = "untitled:Calls.x"
    }
}
