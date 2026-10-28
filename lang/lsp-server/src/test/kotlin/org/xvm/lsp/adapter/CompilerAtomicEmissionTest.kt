package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ModuleStructure

/** Verifies atomic results and receiver mutations by executing serialized compiler output. */
@Timeout(30)
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
    fun `atomic sequential operations return the correct value and mutate their receiver`(expression: String) {
        val changed = if (expression.contains("++")) 2 else 0
        val expected = if (expression.startsWith("++") || expression.startsWith("--")) changed else 1
        val property = expression.replace("++", "").replace("--", "")
        val result =
            compile(
                "module Emission { class Counter<T> { @Atomic Int count = 1; " +
                    "Int check() { Counter<String> counter = new Counter<String>(); Int value = $expression; " +
                    "assert value == $expected; assert $property == $changed; return 42; } } " +
                    "Int run() = new Counter<String>().check(); }",
            )
        assertThat(CompilerTestSupport.execute(result)).isEqualTo(42)
    }

    @ParameterizedTest
    @ValueSource(strings = ["++count", "count++", "--count", "count--"])
    fun `atomic operations can target enclosing instances`(expression: String) {
        val changed = if (expression.contains("++")) 2 else 0
        val expected = if (expression.startsWith("++") || expression.startsWith("--")) changed else 1
        val result =
            compile(
                "module Emission { class Counter { @Atomic Int count = 1; " +
                    "class Worker { Int check() { Int value = $expression; assert value == $expected; " +
                    "assert count == $changed; return 42; } } Int check() = new Worker().check(); } " +
                    "Int run() = new Counter().check(); }",
            )
        assertThat(CompilerTestSupport.execute(result)).isEqualTo(42)
    }

    @ParameterizedTest
    @ValueSource(strings = ["count += 1", "counter.count += 1", "counter.count <<= 1"])
    fun `atomic compound assignments update the intended receiver`(statement: String) {
        val property = statement.substringBefore(" ")
        val result =
            compile(
                "module Emission { class Counter<T extends IntNumber> { " +
                    "construct(T seed) { count = seed; } @Atomic T count; } " +
                    "class Runner { @Atomic Int count = 1; " +
                    "Int check() { Counter<Int> counter = new Counter<Int>(1); $statement; " +
                    "assert $property == 2; return 42; } } Int run() = new Runner().check(); }",
            )
        assertThat(CompilerTestSupport.execute(result)).isEqualTo(42)
    }

    @Test
    fun `generic atomic referents produce correctly typed executable results`() {
        val result =
            compile(
                "module Emission { class Counter<T extends IntNumber> { " +
                    "construct(T seed) { count = seed; } @Atomic T count; } " +
                    "Int increment(Counter<Int> counter) = ++counter.count; " +
                    "Int run() { Counter<Int> counter = new Counter<Int>(1); assert increment(counter) == 2; " +
                    "assert counter.count == 2; return 42; } }",
            )
        assertThat(CompilerTestSupport.execute(result)).isEqualTo(42)
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
}
