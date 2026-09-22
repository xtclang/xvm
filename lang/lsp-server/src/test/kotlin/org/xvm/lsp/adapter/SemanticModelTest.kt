package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ConstantPool
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.NewExpression
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.semanticSnapshot
import java.util.concurrent.Executors

/** Consumer contracts: all semantic answers survive independently of the compiler and its pool. */
class SemanticModelTest {
    @Test
    fun `class and method type parameters point to their written declarations`() {
        val source =
            """
            module Formals {
                class Box</*classDecl*/Element> {
                    /*classUse*/Element echo(Element value) = value;
                }
                interface Mapper { </*abstractDecl*/Item> /*abstractUse*/Item map(Item value); }
                </*methodDecl*/Value> /*methodUse*/Value identity(Value value) {
                    Type<Value> type = /*valueUse*/Value;
                    return value;
                }
            }
            """.trimIndent()
        val model = compile(source).semanticSnapshot()
        for ((declaration, uses) in listOf(
            "classDecl" to listOf("classUse"),
            "methodDecl" to listOf("methodUse", "valueUse"),
            "abstractDecl" to listOf("abstractUse"),
        )) {
            val declared = occurrence(model, source, declaration)
            assertThat(model.symbol(declared.symbol!!)!!.kind).isEqualTo(SemanticModel.SymbolKind.TYPE_PARAMETER)
            uses.forEach { assertThat(occurrence(model, source, it).symbol).isEqualTo(declared.symbol) }
        }
    }

    @Test
    fun `anonymous class capture points to the enclosing parameter`() {
        val source =
            """
            module Anonymous {
                String run(String /*declaration*/text) {
                    Object value = new Object() {
                        @Override String toString() = /*capture*/text;
                    };
                    return value.toString();
                }
            }
            """.trimIndent()
        val model = compile(source).semanticSnapshot()
        val declared = occurrence(model, source, "declaration")
        val captured = occurrence(model, source, "capture")
        assertThat(captured.symbol).isEqualTo(declared.symbol)
        assertThat(model.symbol(captured.symbol!!)!!.declaration).isEqualTo(declared.range)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `nested and mutable anonymous captures retain source identity`(mutable: Boolean) {
        val declaration = if (mutable) "@Volatile Int /*declaration*/value = 1;" else "Int /*declaration*/value = 1;"
        val body =
            if (mutable) {
                "return (++/*capture*/value).toString();"
            } else {
                "function String() f = () -> /*capture*/value.toString(); return f();"
            }
        val source =
            """
            module AnonymousCaptures {
                String run() {
                    $declaration
                    Object object = new Object() {
                        @Override String toString() { $body }
                        String shadow(Int /*shadowDecl*/value) = /*shadowUse*/value.toString();
                    };
                    return object.toString();
                }
            }
            """.trimIndent()
        val compilation = compile(source)
        val model = compilation.semanticSnapshot()
        assertThat(occurrence(model, source, "capture").symbol).isEqualTo(occurrence(model, source, "declaration").symbol)
        assertThat(occurrence(model, source, "shadowUse").symbol).isEqualTo(occurrence(model, source, "shadowDecl").symbol)
        assertThat(occurrence(model, source, "shadowUse").symbol).isNotEqualTo(occurrence(model, source, "declaration").symbol)

        fun nodes(node: AstNode): Sequence<AstNode> =
            sequence {
                yield(node)
                for (child in node.children()) yieldAll(nodes(child))
            }
        val expression = nodes(compilation.parsed()).filterIsInstance<NewExpression>().single()
        val bindings = requireNotNull(expression.sourceBindings)
        val copy = expression.clone() as NewExpression
        assertThat(copy.sourceBindings).isNull()
        assertThat(copy.captureOrigins).isEmpty()
        assertThat(expression.sourceBindings).isSameAs(bindings)
        assertThat(bindings.captureOrigins).isNotEmpty()
    }

    @Test
    fun `overloads aliases and nested generic types keep distinct bindings`() {
        val source =
            """
            module Bindings {
                typedef List<List<String?>> as /*aliasDecl*/Names;
                Int /*intDecl*/choose(Int value) = value;
                String /*stringDecl*/choose(String value) = value;
                void run(/*aliasUse*/Names names) {
                    Int number = /*intUse*/choose(1);
                    String text = /*stringUse*/choose("ok");
                }
            }
            """.trimIndent()
        val model = compile(source).semanticSnapshot()
        for ((declaration, use) in listOf("aliasDecl" to "aliasUse", "intDecl" to "intUse", "stringDecl" to "stringUse")) {
            assertThat(occurrence(model, source, use).symbol).isEqualTo(occurrence(model, source, declaration).symbol)
        }
        assertThat(occurrence(model, source, "intUse").symbol).isNotEqualTo(occurrence(model, source, "stringUse").symbol)
    }

    @Test
    fun `declared and narrowed types share a symbol while preserving each expression type`() {
        val source =
            """
            module Narrowed {
                String text(Object /*declaration*/value) {
                    if (/*test*/value.is(String)) { return /*narrowed*/value; }
                    return /*original*/value.toString();
                }
            }
            """.trimIndent()
        val model = compile(source).semanticSnapshot()
        val declaration = occurrence(model, source, "declaration")
        val narrowed = occurrence(model, source, "narrowed")
        assertThat(model.status).isEqualTo(SemanticModel.Status.COMPLETE)
        assertThat(model.sourceName).isEqualTo(URI)
        assertThat(narrowed.symbol).isEqualTo(declaration.symbol)
        assertThat(model.type(declaration.type!!)!!.displayName).contains("Object")
        assertThat(model.type(narrowed.type!!)!!.displayName).contains("String")
        assertThat(occurrence(model, source, "original").symbol).isEqualTo(declaration.symbol)
    }

    @Test
    fun `structured types and callable signatures retain their relationships`() {
        val source =
            """
            module Signatures {
                List<String?> /*method*/copy(List<String?> /*parameter*/values, Int count = 1) {
                    return values;
                }
            }
            """.trimIndent()
        val model = compile(source).semanticSnapshot()
        val method = model.symbol(occurrence(model, source, "method").symbol!!)!!
        val signature = method.signature!!
        assertThat(signature.parameters.map { it.name!! }).containsExactly("values", "count")
        assertThat(signature.parameters.last().defaulted).isTrue()
        assertThat(signature.conditional).isFalse()
        val list = model.type(signature.parameters.first().type)!!
        assertThat(list.form).isEqualTo(SemanticModel.TypeForm.PARAMETERIZED)
        assertThat(signature.returns).containsExactly(list.id)
        val element = model.type(list.arguments.single())!!
        assertThat(element.nullable).isTrue()
        assertThat(element.form).isEqualTo(SemanticModel.TypeForm.UNION)
        assertThat(element.underlying).hasSize(2)
        assertThat(model.types).allSatisfy { type ->
            assertThat(type.arguments + type.underlying).allSatisfy { assertThat(model.type(it)).isNotNull() }
        }
    }

    @Test
    fun `failed validation retains resolved facts and explicitly unresolved occurrences`() {
        val source =
            """
            module Partial {
                Int /*good*/valid(Int value) { return value; }
                Int broken() { return /*missing*/missing; }
            }
            """.trimIndent()
        val model = compile(source, succeeds = false).semanticSnapshot()
        assertThat(model.status).isEqualTo(SemanticModel.Status.PARTIAL)
        assertThat(occurrence(model, source, "good").symbol).isNotNull()
        val missing = occurrence(model, source, "missing")
        assertThat(missing.symbol).isNull()
        assertThat(missing.type).isNull()
        val at = position(source, "missing")
        assertThat(model.definitionAt(at.line, at.column)).isNull()
        assertThat(model.referencesAt(at.line, at.column, true)).isEmpty()
        assertThat(model.typeAt(at.line, at.column)).isNull()
    }

    @Test
    fun `a resolved qualified prefix survives an unresolved final segment`() {
        val source = "module PartialType { class Outer {} void use(/*prefix*/Outer. /*missing*/Absent value) {} }"
        val model = compile(source, succeeds = false).semanticSnapshot()
        assertThat(occurrence(model, source, "prefix").symbol).isNotNull()
        assertThat(occurrence(model, source, "missing").symbol).isNull()
    }

    @Test
    fun `cancellation before parsing yields an unavailable empty snapshot`() {
        CompilerTestSupport.configure()
        val compilation =
            EmbeddingSupport.instance().compileModule(
                Source("module Cancelled {}", URI),
                null,
                ErrorListener.cancellable(ErrorList()) { true },
            )
        val model = compilation.semanticSnapshot()
        assertThat(model.status).isEqualTo(SemanticModel.Status.UNAVAILABLE)
        assertThat(model.symbols).isEmpty()
        assertThat(model.occurrences).isEmpty()
        assertThat(model.types).isEmpty()
    }

    @Test
    fun `snapshot identities and nested collections cannot be reused or changed by a host`() {
        val compilation = compile("module Immutable { Int value = 1; Int read() { return value; } }")
        val first = compilation.semanticSnapshot()
        val second = compilation.semanticSnapshot()
        assertThat(second.id).isNotEqualTo(first.id)
        assertThat(second.symbol(first.symbols.first().id)).isNull()
        assertThat(second.type(first.types.first().id)).isNull()
        assertThatThrownBy { (first.symbols as MutableList).clear() }.isInstanceOf(UnsupportedOperationException::class.java)
        assertThatThrownBy { (first.occurrences as MutableList).clear() }.isInstanceOf(UnsupportedOperationException::class.java)
        val signature = requireNotNull(first.symbols.first { it.signature != null }.signature)
        assertThatThrownBy { (first.types.first().underlying as MutableList).clear() }
            .isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun `concurrent queries need no ambient pool and remain valid after another compilation`() {
        val source = "module Concurrent { Int read(Int /*declaration*/value) { return /*use*/value; } }"
        val model = compile(source).semanticSnapshot()
        val at = position(source, "use")
        val expected = occurrence(model, source, "declaration").range
        Executors.newFixedThreadPool(4).use { readers ->
            val queries =
                (1..32).map {
                    readers.submit {
                        assertThat(ConstantPool.getCurrentPool()).isNull()
                        repeat(25) {
                            assertThat(model.definitionAt(at.line, at.column)).isEqualTo(expected)
                            assertThat(model.typeAt(at.line, at.column)!!.displayName).contains("Int")
                            assertThat(model.referencesAt(at.line, at.column, true)).hasSize(2)
                        }
                    }
                }
            compile("module Concurrent { String read(String value) { return value; } }")
            queries.forEach { it.get() }
        }
        assertThat(model.definitionAt(at.line, at.column)).isEqualTo(expected)
    }

    @Test
    fun `cloning a validated lambda cannot expose its original context bindings`() {
        val compilation = compile("module Cloned { Int read(Int value) { function Int() f = () -> value; return f(); } }")

        fun nodes(node: AstNode): Sequence<AstNode> =
            sequence {
                yield(node)
                for (child in node.children()) yieldAll(nodes(child))
            }
        val lambda = nodes(compilation.parsed()).filterIsInstance<LambdaExpression>().single()
        val bindings = requireNotNull(lambda.sourceBindings)
        val origins = bindings.captureOrigins
        assertThat(origins).hasSize(1)
        val copy = lambda.clone() as LambdaExpression
        assertThat(copy.sourceBindings).isNull()
        assertThat(lambda.sourceBindings).isSameAs(bindings)
        assertThat(bindings.captureOrigins).isEqualTo(origins)
    }

    private fun compile(
        source: String,
        succeeds: Boolean = true,
    ): EmbeddingSupport.Compilation {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(Source(source, URI), null, errors)
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isEqualTo(succeeds)
        return compilation
    }

    private fun occurrence(
        model: SemanticModel,
        source: String,
        marker: String,
    ): SemanticModel.Occurrence = position(source, marker).let { model.occurrenceAt(it.line, it.column)!! }

    private fun position(
        source: String,
        marker: String,
    ): SemanticModel.Position {
        val comment = "/*$marker*/"
        val start = source.indexOf(comment)
        require(start >= 0)
        val offset = start + comment.length
        return SemanticModel.Position(source.take(offset).count { it == '\n' }, offset - source.lastIndexOf('\n', offset) - 1)
    }

    private companion object {
        const val URI = "file:///Semantic.x"
    }
}
