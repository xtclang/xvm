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
import org.xvm.asm.ModuleRepository
import org.xvm.asm.MultiMethodStructure
import org.xvm.asm.op.GP_Sub
import org.xvm.asm.op.Invoke_01
import org.xvm.asm.op.JumpInt
import org.xvm.compiler.BuildRepository
import org.xvm.compiler.Source
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Source-level probes for the remaining post-validation TypeInfo consumers. */
class CompilerEmissionAuditTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `generic atomic operations preserve their warning across source and dependency use`(external: Boolean) {
        val base = "class Base<T> { @Atomic Int count = 1; }"
        val repository =
            if (external) {
                BuildRepository().apply {
                    storeModule(roundTrip(compile("module Library { $base }")).module)
                }
            } else {
                null
            }
        val declarations = if (external) "package lib import Library; import lib.Base;" else base
        val source =
            "module Emission { $declarations " +
                "class Derived<T> extends Base<T> { @Atomic @Override Int count = 2; } " +
                "Int run(Derived<String> value) { value.count += 1; return ++value.count; } }"
        val result = compile(source, repository, expectedCodes = listOf("VERIFY-75"))
        assertThat(restoredMethod(result).ops).isNotEmpty()
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "Bit",
                "Nibble",
                "Char",
                "Int8",
                "Int16",
                "Int32",
                "Int64",
                "Int128",
                "IntN",
                "UInt8",
                "UInt16",
                "UInt32",
                "UInt64",
                "UInt128",
                "UIntN",
            ],
    )
    fun `dense switches retain conversion instructions in serialized bytecode`(type: String) {
        val first =
            if (type == "Char") {
                "'a'"
            } else if (type == "Bit") {
                "0"
            } else {
                "1"
            }
        val second =
            if (type == "Char") {
                "'b'"
            } else if (type == "Bit") {
                "1"
            } else {
                "2"
            }
        val result =
            compile(
                "module Emission { Int run($type value) { switch (value) { " +
                    "case $first: return 10; case $second: return 20; default: return 30; } } }",
            )
        val method = restoredMethod(result)
        assertThat(method.ops.filterIsInstance<JumpInt>()).hasSize(1)
        assertThat(method.ops.filterIsInstance<Invoke_01>()).hasSize(if (type == "Int64") 0 else 1)
        assertThat(method.ops.filterIsInstance<GP_Sub>()).hasSize(if (type == "Bit") 0 else 1)
    }

    @Test
    fun `enum switches avoid ordinal conversion in serialized code`() {
        val result =
            compile(
                "module Emission { enum Choice { First, Second } Int run(Choice value) { switch (value) { " +
                    "case First: return 1; case Second: return 2; } } }",
            )
        val method = restoredMethod(result)
        assertThat(method.ops.filterIsInstance<JumpInt>()).isEmpty()
        assertThat(method.ops.filterIsInstance<Invoke_01>()).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int run() = ++count;", "void run() { count -= 1; }"])
    fun `invalid atomic operations produce source errors before emission`(method: String) {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val source =
            Source(
                "module Emission { @Atomic String count = \"x\"; $method }",
                "file:///Emission.x",
            )
        val result = EmbeddingSupport.instance().compileModule(source, null, errors)
        assertThat(result.succeeded()).isFalse()
        assertThat(errors.hasSeriousErrors()).isTrue()
        assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5").anyMatch {
            it.startsWith("COMPILER-")
        }
    }

    private fun compile(
        source: String,
        repository: ModuleRepository? = null,
        expectedCodes: List<String> = emptyList(),
    ): EmbeddingSupport.Compilation {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result =
            EmbeddingSupport
                .instance()
                .compileModule(Source(source, "file:///Emission.x"), repository, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        assertThat(errors.errors.map { it.code }).containsExactlyElementsOf(expectedCodes)
        return result
    }

    private fun restoredMethod(
        result: EmbeddingSupport.Compilation,
        vararg owners: String,
    ): MethodStructure {
        val restored =
            owners.fold(roundTrip(result).module as Component) { parent, name ->
                parent.getChild(name)
            }
        return (restored.getChild("run") as MultiMethodStructure).methods().single()
    }

    private fun roundTrip(result: EmbeddingSupport.Compilation): FileStructure {
        val bytes = ByteArrayOutputStream().also { result.file().writeTo(it) }.toByteArray()
        return FileStructure(ByteArrayInputStream(bytes))
    }
}
