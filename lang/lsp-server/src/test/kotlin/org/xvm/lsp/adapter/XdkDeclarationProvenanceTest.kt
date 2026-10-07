package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.Register
import org.xvm.asm.constants.PropertyConstant
import org.xvm.asm.constants.TypeParameterConstant
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.Parameter
import org.xvm.compiler.ast.TypeCompositionStatement

/** AST4 investigates ownership across declaration-only, validated and generated-method phases. */
class XdkDeclarationProvenanceTest {
    @Test
    fun `declaration analysis binds formals and primary properties before value registers exist`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val declarations =
            EmbeddingSupport
                .instance()
                .analyzeDeclarations(
                    Source(
                        """
                        module Provenance {
                            class Box<T>(T value) {
                                <U> U echo(U input) = input;
                            }
                            interface Api { <V> V read(V argument); }
                        }
                        """.trimIndent(),
                        URI,
                    ),
                    null,
                    errors,
                ).orElseThrow()
        assertThat(errors.errors).isEmpty()
        val parameters = nodes(declarations.ast()).filterIsInstance<Parameter>().toList()
        val primary = parameters.single { it.name == "value" && it.parent is TypeCompositionStatement }
        assertThat(primary.resolvedTarget).isInstanceOf(PropertyConstant::class.java)
        assertThat((primary.resolvedTarget as PropertyConstant).name).isEqualTo("value")
        assertThat(parameters.single { it.name == "T" }.resolvedTarget)
            .isInstanceOf(PropertyConstant::class.java)
        listOf("U", "V").forEach { name ->
            assertThat(parameters.single { it.name == name }.resolvedTarget)
                .isInstanceOf(TypeParameterConstant::class.java)
        }
        listOf("input", "argument").forEach { name ->
            assertThat(parameters.single { it.name == name }.resolvedTarget).isNull()
        }
    }

    @Test
    fun `used parameters receive their lexical registers without inventing bodyless registers`() {
        val compilation =
            compile(
                """
                module Provenance {
                    interface Api { Int read(Int argument); }
                    <T> T identity(T value) = value;
                }
                """.trimIndent(),
            )
        val all = nodes(compilation.parsed()).toList()
        val value = all.filterIsInstance<Parameter>().single { it.name == "value" }
        val register = value.resolvedTarget as Register
        val use = all.filterIsInstance<NameExpression>().single { it.name == "value" }
        assertThat(use.resolvedTarget).isSameAs(register)
        // The method formal consumes register zero, but is a separate source identity.
        assertThat(register.index).isEqualTo(1)
        assertThat(all.filterIsInstance<Parameter>().single { it.name == "T" }.resolvedTarget)
            .isInstanceOf(TypeParameterConstant::class.java)
        val bodyless = all.filterIsInstance<Parameter>().single { it.name == "argument" }
        assertThat(bodyless.parent).isInstanceOf(MethodDeclarationStatement::class.java)
        assertThat(bodyless.resolvedTarget).isNull()
    }

    @Test
    fun `typed lambda parameters exclude captures while cloned syntax keeps historical scalar targets`() {
        val compilation =
            compile(
                """
                module Provenance {
                    Int run(Int outer) {
                        function Int(Int) fn = (Int value) -> value + outer;
                        return fn(1);
                    }
                }
                """.trimIndent(),
            )
        val lambda = nodes(compilation.parsed()).filterIsInstance<LambdaExpression>().single()
        val parameter = nodes(lambda).filterIsInstance<Parameter>().single { it.name == "value" }
        val bindings = requireNotNull(lambda.sourceBindings)
        val binding = bindings.parameters.single()
        assertThat(binding.register()).isSameAs(parameter.resolvedTarget)
        assertThat(binding.name().valueText).isEqualTo("value")
        assertThat(bindings.captureOrigins).hasSize(1)
        assertThat(binding.register().index).isEqualTo(1)
        val clone = lambda.clone() as LambdaExpression
        assertThat(clone.sourceBindings).isNull()
        val copiedParameter = nodes(clone).filterIsInstance<Parameter>().single { it.name == "value" }
        assertThat(copiedParameter).isNotSameAs(parameter)
        // An AST clone is not a new compilation attempt. The existing scalar association is copied,
        // while the generated method/context association is deliberately unavailable on the clone.
        assertThat(copiedParameter.resolvedTarget).isSameAs(parameter.resolvedTarget)
        assertThat(lambda.sourceBindings).isSameAs(bindings)
    }

    private fun compile(text: String): EmbeddingSupport.Compilation {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(Source(text, URI), null, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        assertThat(errors.errors).isEmpty()
        return result
    }

    private fun nodes(root: AstNode): Sequence<AstNode> =
        sequence {
            yield(root)
            for (child in root.childNodes()) yieldAll(nodes(child))
        }

    private companion object {
        const val URI = "untitled:Provenance.x"
    }
}
