package org.xvm.lsp.adapter

import java.lang.ref.WeakReference
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkProject
import org.xvm.lsp.adapter.xdk.XdkProjectQueries
import org.xvm.lsp.adapter.xdk.XdkSourceModule

/** A bounded graph proof workload, independent of editor focus and client-side caches. */
class XdkProjectProofMemoryTest {
    @TempDir lateinit var directory: Path

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
        val observed = mutableListOf<WeakReference<Any>>()
        val cancelled = AtomicBoolean()
        val modules =
            (0 until 24).map { index ->
                val file = directory.toRealPath().resolve("Root$index.x").toFile()
                file.writeText(
                    "module Root$index { class Box { Int number = 1; Int read() = number; } }"
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
                    EmbeddingSupport.instance().compileModule(sources, repository, errors).also {
                        compilation ->
                        observed += WeakReference(compilation)
                        compilation.pool()?.let { observed += WeakReference(it) }
                        compilation.sourceTrees().forEach { observed += WeakReference(it) }
                        if (observed.size > 24 * 3) {
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
        assertThat(observed).hasSizeGreaterThanOrEqualTo(48)
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).untilAsserted {
            System.gc()
            assertThat(observed.count { it.get() != null })
                .describedAs("retained graph proof attempts, pools and AST roots")
                .isZero()
        }
        println(
            "24-root $outcome property proof: compiler objects released=${observed.size}, heap budget=${Runtime.getRuntime().maxMemory()} bytes"
        )
    }
}
