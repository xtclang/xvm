package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import java.io.File
import java.lang.ref.WeakReference
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeUnit.SECONDS

/** A bounded repeatable retention workload; it is not a multi-hour editor soak or a performance SLA. */
class XdkRetentionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `source graphs replacements rename and cursor proofs release attempts after close and shutdown`() {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        val support = EmbeddingSupport.instance()
        val observed = CopyOnWriteArrayList<WeakReference<Any>>()

        fun observe(value: Any?) {
            if (value != null) observed.add(WeakReference(value))
        }

        fun EmbeddingSupport.Compilation.observe(): EmbeddingSupport.Compilation =
            also {
                observe(it)
                observe(it.pool())
                it.sourceTrees().forEach(::observe)
            }
        val artifacts =
            (1..2).map { version ->
                support
                    .compileModule(
                        Source("module External { static Int value()=$version; }", "External.x"),
                        null,
                        ErrorList(),
                    ).toDependency()
            }
        val library =
            directory.resolve("Library.x").toFile().apply {
                writeText("module Library { package ext import External; static Int value()=ext.value(); class Box { Int number=1; } }")
            }
        val consumer = directory.resolve("Consumer.x").toFile().apply { writeText(CONSUMER) }
        val uri = consumer.toURI().toString()
        val timings = mutableListOf<Long>()
        val adapter =
            XdkAdapter(
                { source, repository, errors -> support.compileModule(source, repository, errors).observe() },
                { sources, repository, errors -> support.compileModule(sources, repository, errors).observe() },
                { source, sources, cursor, repository, errors ->
                    support.analyzeIncomplete(sources, File(URI(source.fileName)), cursor, repository, errors).also {
                        observe(it)
                        observe(it.pool().orElse(null))
                        it.sourceTrees().forEach(::observe)
                    }
                },
            )
        try {
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule("Library", library.toURI().toString()),
                    XdkSourceModule("Consumer", uri, setOf("Library")),
                ),
            )
            repeat(CYCLES) { cycle ->
                adapter.replaceDependencies(listOf(artifacts[cycle % artifacts.size]))
                val started = System.nanoTime()
                val storm = (0..7).map { edit -> adapter.compileAsync(uri, "$CONSUMER // $cycle:$edit") }
                assertThat(storm.last().get(30, SECONDS).success).isTrue()
                assertThat(storm.dropLast(1)).allMatch { it.isCancelled }
                timings += System.nanoTime() - started
                val renamed = adapter.renameAsync(uri, 0, CONSUMER.indexOf("local"), "renamed").get(30, SECONDS)
                assertThat(renamed?.changes?.get(uri)).hasSize(2)
                val prefix = listOf("box.", "take(", "take(bo")[cycle % 3]
                val expected = if (cycle % 3 == 0) "number" else "box"
                val incomplete = CONSUMER.replace("box) {}", "box) { $prefix }")
                assertThat(adapter.compile(uri, incomplete).success).isFalse()
                val column = incomplete.lastIndexOf(prefix) + prefix.length
                assertThat(adapter.getCompletionsAsync(uri, 0, column).get(30, SECONDS).map { it.label }).contains(expected)
                val cancelled = adapter.getCompletionsAsync(uri, 0, column)
                cancelled.cancel(false)
                assertThat(adapter.compile(uri, CONSUMER).success).isTrue()
                val rename = adapter.renameAsync(uri, 0, CONSUMER.indexOf("local"), "renamed")
                adapter.closeDocument(uri)
                assertThat(rename.isCancelled).isTrue()
                assertThat(adapter.getCachedResult(uri)).isNull()
            }
            assertReleased(observed)
        } finally {
            adapter.close()
        }
        assertReleased(observed)
        val millis = timings.map(NANOSECONDS::toMillis).sorted()
        println(
            "Retention workload: cycles=$CYCLES, edit requests=${CYCLES * 8}, weak references=${observed.size}, retained=0, " +
                "rebuild p50=${millis[millis.size / 2]}ms, p95=${millis[(millis.size * 0.95).toInt()]}ms (includes debounce)",
        )
    }

    private fun assertReleased(observed: List<WeakReference<Any>>) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).untilAsserted {
            System.gc()
            assertThat(
                observed.count { it.get() != null },
            ).describedAs("compiler attempts, roots and pools still strongly reachable").isZero()
        }
    }

    private companion object {
        const val CYCLES = 120
        const val CONSUMER =
            "module Consumer { package lib import Library; " +
                "Int run() { Int local=lib.value(); return local; } void take(lib.Box value) {} void probe(lib.Box box) {} }"
    }
}
