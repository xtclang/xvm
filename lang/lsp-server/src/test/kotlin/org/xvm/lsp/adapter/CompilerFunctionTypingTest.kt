package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ConstantPool
import org.xvm.asm.ErrorList
import org.xvm.asm.FileStructure
import org.xvm.asm.ModuleStructure
import org.xvm.asm.MultiMethodStructure
import org.xvm.asm.ast.BinaryAST
import org.xvm.asm.ast.BindFunctionAST
import org.xvm.asm.ast.ReturnStmtAST
import org.xvm.asm.ast.StmtBlockAST
import org.xvm.asm.constants.TypeConstant
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.Token.Id
import org.xvm.compiler.ast.Context
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.compiler.ast.LiteralExpression
import org.xvm.compiler.ast.NameExpression
import org.xvm.util.PackedInteger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Verifies ordinary function typing without the partial-analysis or selected-call APIs. */
class CompilerFunctionTypingTest {
    @ParameterizedTest
    @ValueSource(strings = ["id", "&id", "pick", "&pick"])
    fun `bound generic functions retain their callable type after serialization`(reference: String) {
        val module =
            compile(
                "module FunctionTypes { static <T> T id(T value) = value; <T> T pick(T value) = value; " +
                    "function Int(Int) make() = $reference; }",
            )
        val bytes = ByteArrayOutputStream().also { module.fileStructure.writeTo(it) }.toByteArray()
        val restored = FileStructure(ByteArrayInputStream(bytes)).module
        val method = (restored.getChild("make") as MultiMethodStructure).methods().single()
        val returned =
            statements(method.ast)
                .filterIsInstance<ReturnStmtAST>()
                .single()
                .exprs
                .single()

        assertThat(returned).isInstanceOf(BindFunctionAST::class.java)
        val binding = returned as BindFunctionAST
        assertThat(binding.indexes).containsExactly(0)
        assertThat(binding.args).hasSize(1)
        assertThat(binding.getType(0)).isEqualTo(method.identityConstant.rawReturns.single())
        assertThat(method.constantPool.extractFunctionParams(binding.getType(0)))
            .containsExactly(method.constantPool.typeInt64())
        assertThat(method.constantPool.extractFunctionReturns(binding.getType(0)))
            .containsExactly(method.constantPool.typeInt64())
    }

    @ParameterizedTest
    @ValueSource(strings = ["1, \"x\"", "1", "True, \"x\"", "1, \"x\", 2"])
    fun `function calls accept matching arguments and reject invalid arguments`(arguments: String) {
        CompilerTestSupport.configure()
        val source =
            "module FunctionCalls { function Int(Int, String) make(function Int(Int, String) fn) = fn; " +
                "Int apply(function Int(Int, String) fn) = make(fn)($arguments); }"
        val errors = ErrorList(100)
        val module = EmbeddingSupport.instance().compile(source, null, errors)
        if (arguments == "1, \"x\"") {
            assertThat(module).isNotNull()
            assertThat(errors.errors).isEmpty()
        } else {
            assertThat(module).isNull()
            assertThat(errors.hasSeriousErrors()).isTrue()
            assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["valid", "missing", "excess", "wrong type"])
    fun `matching return types do not erase argument fitting failures`(case: String) {
        val module = compile("module FunctionFit {}")
        val pool = module.constantPool
        val source = Source("fn(\"x\")")

        fun stringArgument() = LiteralExpression(Token(3, 6, Id.LIT_STRING, "x"))
        val arguments =
            when (case) {
                "missing" -> emptyList()
                "excess" -> listOf(stringArgument(), stringArgument())
                "wrong type" -> listOf(LiteralExpression(Token(3, 4, Id.LIT_INT, PackedInteger.ONE)))
                else -> listOf(stringArgument())
            }
        val invocation = FunctionFitInvocation(pool, source, arguments)
        val context =
            object : Context(null, false) {
                override fun pool(): ConstantPool = pool

                override fun getThisType(): TypeConstant = module.formalType

                override fun getSource(): Source = source
            }
        val errors = ErrorList(100)
        ConstantPool.withPool(pool).use {
            val function = pool.buildFunctionType(arrayOf(pool.typeString()), pool.typeInt64())
            val fitted = invocation.fit(context, function, pool.typeInt64(), errors)
            if (case == "valid") {
                assertThat(fitted).isEqualTo(function)
                assertThat(errors.errors).isEmpty()
            } else {
                assertThat(errors.hasSeriousErrors()).isTrue()
                assertThat(fitted).isNull()
            }
        }
    }

    // Source diagnostics alone reject invalid programs even when the fitting routine mistakenly
    // returns a usable function type. Exercise its documented success/absence contract directly.
    private class FunctionFitInvocation(
        private val compilerPool: ConstantPool,
        private val inputSource: Source,
        arguments: List<Expression>,
    ) : InvocationExpression(NameExpression(Token(0, 2, Id.IDENTIFIER, "fn")), false, arguments, 7) {
        init {
            introduceParentage()
        }

        override fun pool(): ConstantPool = compilerPool

        override fun getSource(): Source = inputSource

        fun fit(
            context: Context,
            function: TypeConstant,
            requiredReturn: TypeConstant,
            errors: ErrorList,
        ): TypeConstant? = testFunction(context, function, 0, 0, arrayOf(requiredReturn), errors)
    }

    private fun compile(source: String): ModuleStructure {
        CompilerTestSupport.configure()
        val errors = ErrorList(100)
        val module = EmbeddingSupport.instance().compile(source, null, errors)
        assertThat(errors.errors).isEmpty()
        return requireNotNull(module) { "Compilation failed: ${errors.errors}" }
    }

    private fun statements(ast: BinaryAST): List<BinaryAST> = if (ast is StmtBlockAST) ast.stmts.flatMap(::statements) else listOf(ast)
}
