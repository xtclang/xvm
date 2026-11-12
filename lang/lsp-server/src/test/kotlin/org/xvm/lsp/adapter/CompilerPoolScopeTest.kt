package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ConstantPool
import org.xvm.asm.ErrorList
import org.xvm.asm.FileStructure
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.semanticSnapshot
import org.xvm.lsp.adapter.xdk.semanticSnapshots
import org.xvm.lsp.adapter.xdk.toDependency

/** Compiler entry points own their scopes; detached LSP facts need no ambient compiler context. */
class CompilerPoolScopeTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `compilation snapshot and artifact extraction preserve the caller pool`(bound: Boolean) {
        CompilerTestSupport.configure()
        assertThat(ConstantPool.getCurrentPool()).isNull()
        val caller = if (bound) FileStructure("Unrelated").constantPool else null
        val source = "module Scoped { Int read(Int value) = value; }"
        val errors = ErrorList()
        val model =
            ConstantPool.withPool(caller).use {
                val compilation =
                    EmbeddingSupport.instance().compileModule(Source(source, URI), null, errors)
                assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
                assertThat(ConstantPool.getCurrentPool()).isSameAs(caller)

                val snapshot = compilation.semanticSnapshots(errors).single()
                assertThat(ConstantPool.getCurrentPool()).isSameAs(caller)
                assertThat(compilation.toDependency().module).isEqualTo("Scoped")
                assertThat(ConstantPool.getCurrentPool()).isSameAs(caller)
                snapshot
            }

        assertThat(ConstantPool.getCurrentPool()).isNull()
        val use = source.lastIndexOf("value")
        assertThat(model.definitionAt(0, use)).isNotNull()
        assertThat(model.typeAt(0, use)?.displayName).contains("Int")
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `failed compilation still exports semantic facts and restores the caller pool`(bound: Boolean) {
        CompilerTestSupport.configure()
        assertThat(ConstantPool.getCurrentPool()).isNull()
        val caller = if (bound) FileStructure("Unrelated").constantPool else null
        ConstantPool.withPool(caller).use {
            val errors = ErrorList()
            val compilation =
                EmbeddingSupport.instance().compileModule(
                    Source("module Broken { Int read() = missing; }", URI),
                    null,
                    errors,
                )
            assertThat(compilation.succeeded()).isFalse()
            assertThat(errors.hasSeriousErrors()).isTrue()
            assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
            assertThat(ConstantPool.getCurrentPool()).isSameAs(caller)
            assertThat(compilation.semanticSnapshot().status).isEqualTo(SemanticModel.Status.PARTIAL)
            assertThat(ConstantPool.getCurrentPool()).isSameAs(caller)
        }
        assertThat(ConstantPool.getCurrentPool()).isNull()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `partial member inspection binds the attempt pool and restores the caller pool`(bound: Boolean) {
        CompilerTestSupport.configure()
        assertThat(ConstantPool.getCurrentPool()).isNull()
        val caller = if (bound) FileStructure("Unrelated").constantPool else null
        val prefix = "module Editing { Int read(String value) { return value."
        ConstantPool.withPool(caller).use {
            val errors = ErrorList()
            val source = Source("$prefix; } }", URI)
            repeat(prefix.length) { source.next() }
            val cursor = source.position
            source.position = 0
            val analysis = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, errors)
            assertThat(analysis.pool()).describedAs(errors.errors.toString()).isNotNull()
            assertThat(ConstantPool.getCurrentPool()).isSameAs(caller)
            val snapshot = analysis.semanticSnapshot(errors)
            assertThat(
                snapshot.sites
                    .single()
                    .members
                    .map { it.name },
            ).contains("size")
            assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
            assertThat(ConstantPool.getCurrentPool()).isSameAs(caller)
        }
        assertThat(ConstantPool.getCurrentPool()).isNull()
    }

    private companion object {
        const val URI = "file:///Scoped.x"
    }
}
