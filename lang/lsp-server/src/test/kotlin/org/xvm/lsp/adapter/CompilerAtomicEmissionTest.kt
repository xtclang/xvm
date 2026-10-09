package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.Component
import org.xvm.asm.ErrorList
import org.xvm.asm.FileStructure
import org.xvm.asm.MethodStructure
import org.xvm.asm.ModuleStructure
import org.xvm.asm.MultiMethodStructure
import org.xvm.asm.ast.BinaryAST
import org.xvm.asm.ast.InvokeExprAST
import org.xvm.asm.ast.PropertyExprAST
import org.xvm.asm.ast.ReturnStmtAST
import org.xvm.asm.ast.StmtBlockAST
import org.xvm.asm.ast.UnaryOpExprAST
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Compiles real source and inspects atomic operation types after binary serialization. */
class CompilerAtomicEmissionTest {
    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "++counter.count",
                "counter.count++",
                "--counter.count",
                "counter.count--",
                "++count",
                "count++",
                "--count",
                "count--",
            ],
    )
    fun `atomic sequential results retain their type in the serialized binary AST`(expression: String) {
        val result =
            compile(
                "module Emission { @Atomic Int count = 1; class Counter<T> { @Atomic Int count = 1; } " +
                    "Int run(Counter<String> counter) = $expression; }",
            )
        val method = restoredMethod(result)
        val returned =
            statements(method.ast)
                .filterIsInstance<ReturnStmtAST>()
                .single()
                .exprs
                .single()
        assertThat(returned).isInstanceOf(InvokeExprAST::class.java)
        assertThat(returned.count).isEqualTo(1)
        assertThat(returned.getType(0)).isEqualTo(method.identityConstant.rawReturns.single())
    }

    @ParameterizedTest
    @ValueSource(strings = ["++count", "count++", "--count", "count--"])
    fun `atomic operations can target enclosing instances`(expression: String) {
        val result =
            compile(
                "module Emission { class Counter { @Atomic Int count = 1; " +
                    "class Worker { Int run() = $expression; } } }",
            )
        val method = restoredMethod(result, "Counter", "Worker")
        val returned =
            statements(method.ast)
                .filterIsInstance<ReturnStmtAST>()
                .single()
                .exprs
                .single()
                as InvokeExprAST
        assertThat(returned.count).isEqualTo(1)
        assertThat(returned.getType(0)).isEqualTo(method.identityConstant.rawReturns.single())
        val property = (returned.target as UnaryOpExprAST).expr as PropertyExprAST
        assertThat(
            property.target
                .getType(0)
                .getSingleUnderlyingClass(true)
                .name,
        ).isEqualTo("Counter")
    }

    @ParameterizedTest
    @ValueSource(strings = ["count += 1", "counter.count += 1", "counter.count <<= 1"])
    fun `atomic compound assignments retain their concrete target and void result`(statement: String) {
        val result =
            compile(
                "module Emission { @Atomic Int count = 1; class Counter<T extends IntNumber>(T seed) { @Atomic T count = seed; } " +
                    "void run(Counter<Int> counter) { $statement; } }",
            )
        val method = restoredMethod(result)
        val call = statements(method.ast).filterIsInstance<InvokeExprAST>().single()
        assertThat(call.count).isZero()
        assertThat(call.target.getType(0).getParamType(0))
            .isEqualTo(method.constantPool.typeInt64())
        assertThat(call.args).hasSize(1)
    }

    @Test
    fun `generic atomic referents retain their concrete result and target types`() {
        val result =
            compile(
                "module Emission { class Counter<T extends IntNumber>(T seed) { @Atomic T count = seed; } " +
                    "Int run(Counter<Int> counter) = ++counter.count; }",
            )
        val method = restoredMethod(result)
        val returned =
            statements(method.ast)
                .filterIsInstance<ReturnStmtAST>()
                .single()
                .exprs
                .single()
                as InvokeExprAST
        assertThat(returned.count).isEqualTo(1)
        assertThat(returned.getType(0)).isEqualTo(method.identityConstant.rawReturns.single())
        assertThat(returned.target.getType(0).getParamType(0)).isEqualTo(returned.getType(0))
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int run() = ++count;", "void run() { count -= 1; }"])
    fun `invalid atomic operations produce source errors before emission`(method: String) {
        CompilerTestSupport.configure()
        val errors = ErrorList(100)
        val source = "module Emission { @Atomic String count = \"x\"; $method }"
        val result = EmbeddingSupport.instance().compile(source, null, errors)
        assertThat(result).isNull()
        assertThat(errors.hasSeriousErrors()).isTrue()
        assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5").anyMatch {
            it.startsWith("COMPILER-")
        }
    }

    private fun compile(source: String): ModuleStructure {
        CompilerTestSupport.configure()
        val errors = ErrorList(100)
        val module = EmbeddingSupport.instance().compile(source, null, errors)
        assertThat(errors.errors).isEmpty()
        return requireNotNull(module) { "Compilation failed: ${errors.errors}" }
    }

    private fun restoredMethod(
        result: ModuleStructure,
        vararg owners: String,
    ): MethodStructure {
        val restored =
            owners.fold(roundTrip(result).module as Component) { parent, name ->
                parent.getChild(name)
            }
        return (restored.getChild("run") as MultiMethodStructure).methods().single()
    }

    private fun roundTrip(result: ModuleStructure): FileStructure {
        val bytes = ByteArrayOutputStream().also { result.fileStructure.writeTo(it) }.toByteArray()
        return FileStructure(ByteArrayInputStream(bytes))
    }

    private fun statements(ast: BinaryAST): List<BinaryAST> = if (ast is StmtBlockAST) ast.stmts.flatMap(::statements) else listOf(ast)
}
