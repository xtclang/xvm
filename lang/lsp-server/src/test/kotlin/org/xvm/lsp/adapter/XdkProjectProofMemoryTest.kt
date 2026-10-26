package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkProject
import org.xvm.lsp.adapter.xdk.XdkProjectQueries
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.util.Watched
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** A bounded graph proof workload, independent of editor focus and client-side caches. */
class XdkProjectProofMemoryTest {
    @TempDir lateinit var directory: Path

    /** Enumerates proof-query exit paths that must all release temporary compiler state. */
    enum class Outcome {
        SUCCESS,
        REJECTED,
        CANCELLED,
        FAILURE,
    }

    @ParameterizedTest
    @EnumSource(Outcome::class)
    fun `property rename across 24 independent roots releases proof attempts`(outcome: Outcome) {
        CompilerTestSupport.configure()
        val watched = Watched()
        val cancelled = AtomicBoolean()
        val modules =
            (0 until 24).map { index ->
                val file = directory.toRealPath().resolve("Root$index.x").toFile()
                file.writeText(
                    "module Root$index { class Box { Int number = 1; Int read() = number; } }",
                )
                XdkSourceModule("Root$index", file.toURI().toString())
            }
        val query =
            XdkProjectQueries(
                XdkProject(modules),
                emptyMap(),
                XdkDependencies(emptyList()),
                { sources, repository, errors ->
                    // No configured root imports another: the proof must not deserialize preceding
                    // roots just to leave their declarations and pools unused in this compilation.
                    assertThat(repository?.moduleNames).isEmpty()
                    EmbeddingSupport.instance().compileModule(sources, repository, errors).also { compilation ->
                        watched.add(compilation)
                        watched.add(compilation.pool())
                        compilation.sourceTrees().forEach(watched::add)
                        if (watched.size > 24 * 3) {
                            when (outcome) {
                                Outcome.CANCELLED -> cancelled.set(true)
                                Outcome.FAILURE -> error("proof compiler failure")
                                else -> Unit
                            }
                        }
                    }
                },
                cancelled::get,
            )
        val target = modules.first()
        val at = "module Root0 { class Box { Int ".length
        when (outcome) {
            Outcome.SUCCESS -> {
                val edit = requireNotNull(query.rename(target.uri, 0, at, "count"))
                assertThat(edit.changes.keys).containsExactly(target.uri)
                assertThat(edit.changes.getValue(target.uri)).hasSize(2)
            }

            Outcome.REJECTED -> {
                assertThat(query.rename(target.uri, 0, at, "read")).isNull()
            }

            Outcome.CANCELLED -> {
                assertThatThrownBy { query.rename(target.uri, 0, at, "count") }
                    .isInstanceOf(CancellationException::class.java)
            }

            Outcome.FAILURE -> {
                assertThatThrownBy { query.rename(target.uri, 0, at, "count") }
                    .isInstanceOf(IllegalStateException::class.java)
                    .hasMessage("proof compiler failure")
            }
        }
        assertThat(watched.size).isGreaterThanOrEqualTo(48)
        // what the query and the compiler still reach, with their classes' statics, without waiting for a collection
        val started = System.nanoTime()
        assertThat(watched.reachedFrom(query, EmbeddingSupport.instance()))
            .describedAs("graph proof attempts, pools and AST roots still reached")
            .isEmpty()
        println(
            "24-root $outcome property proof: ${watched.size} compiler objects released, walked in ${(System.nanoTime() - started) / 1_000_000} ms",
        )
    }
}
