package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.Heavy
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import org.xvm.lsp.awaited
import org.xvm.lsp.util.Watched
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.NANOSECONDS

/**
 * Verifies that dependency replacement, editing, renaming and cursor queries release compiler graphs:
 * nothing the adapter or the compiler holds still reaches them, which a walk of the object graph finds
 * without waiting for a collection. The regular test covers every cursor/header variant; the heavy test repeats the workload longer
 * to expose accumulation across many requests. Neither test defines a performance SLA.
 */
class XdkRetentionTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `source graphs replacements rename and cursor proofs release attempts after close and shutdown`() {
        // Four headers, each selected once every nine cycles, plus all eight expression variants.
        verifyRetention(36)
    }

    @Test
    @Heavy
    fun `extended editing workload does not accumulate compiler graphs`() {
        verifyRetention(CYCLES)
    }

    private fun verifyRetention(cycles: Int) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        val support = EmbeddingSupport.instance()
        val watched = Watched()

        fun EmbeddingSupport.Compilation.observe(): EmbeddingSupport.Compilation =
            also {
                watched.add(it)
                watched.add(it.pool())
                it.sourceTrees().forEach(watched::add)
            }
        val artifacts =
            (1..2).map { version ->
                support
                    .compileModule(
                        Source("module External { static Int value() = $version; }", "External.x"),
                        null,
                        ErrorList(),
                    ).toDependency()
            }
        val library =
            directory.resolve("Library.x").toFile().apply {
                writeText(
                    "module Library { package ext import External; static Int value() = ext.value(); class Box { Int number = 1; } }",
                )
            }
        val consumer = directory.resolve("Consumer.x").toFile().apply { writeText(CONSUMER) }
        val uri = consumer.toURI().toString()
        val timings = mutableListOf<Long>()
        // an edit storm's compiles wait for its last edit, as the debounce has them wait for an editor's pause, so that
        // which of them the storm cancels does not depend on how fast the edits arrive; other compiles run at once
        val debounces = Executors.newSingleThreadExecutor()
        val held = mutableListOf<FutureTask<Unit>>()
        var storming = false
        // Other.x's compile holds the adapter's one compile worker until the test opens the gate it hands over
        val gates = LinkedBlockingQueue<CountDownLatch>()
        val other = directory.resolve("Other.x").toUri().toString()
        val adapter =
            XdkAdapter(
                { source, repository, errors ->
                    if (source.fileName.endsWith("Other.x")) CountDownLatch(1).also(gates::add).awaited()
                    support.compileModule(source, repository, errors).observe()
                },
                { sources, repository, errors ->
                    support.compileModule(sources, repository, errors).observe()
                },
                { source, sources, cursor, repository, errors ->
                    support
                        .analyzeIncomplete(
                            sources,
                            File(URI(source.fileName)),
                            cursor,
                            repository,
                            errors,
                        ).also {
                            watched.add(it)
                            watched.add(it.pool())
                            it.sourceTrees().forEach(watched::add)
                        }
                },
                { work ->
                    FutureTask(work, Unit).also { task -> synchronized(held) { if (storming) held += task else debounces.execute(task) } }
                },
            )
        try {
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule("Library", library.toURI().toString()),
                    XdkSourceModule("Consumer", uri, setOf("Library")),
                ),
            )
            val cursorCases =
                listOf(
                    "box." to "number",
                    "take(" to "box",
                    "take(bo" to "box",
                    "textTake(te" to "textValue",
                    "new String[2](te" to "textValue",
                    "new Int[si|];" to "sizeValue",
                    "Object parts = [textValue.si" to "size",
                    "new Object(te|) { construct(String value) {} Int read() = box.number; };" to
                        "textValue",
                )
            repeat(cycles) { cycle ->
                adapter.replaceDependencies(listOf(artifacts[cycle % artifacts.size]))
                val started = System.nanoTime()
                synchronized(held) { storming = true }
                val storm =
                    (0..7).map { edit -> adapter.compileAsync(uri, "$CONSUMER // $cycle:$edit") }
                synchronized(held) {
                    storming = false
                    held.forEach(debounces::execute)
                    held.clear()
                }
                assertThat(storm.last().awaited().success).isTrue()
                assertThat(storm.dropLast(1)).allMatch { it.isCancelled }
                timings += System.nanoTime() - started
                val renamed =
                    adapter
                        .renameAsync(uri, 0, CONSUMER.indexOf("local"), "renamed")
                        .awaited()
                assertThat(renamed?.changes?.get(uri)).hasSize(2)
                val headerQuery = cycle % (cursorCases.size + 1) == cursorCases.size
                val (marked, expected) =
                    when {
                        !headerQuery -> {
                            cursorCases[cycle % cursorCases.size]
                        }

                        (cycle / (cursorCases.size + 1)) % 4 == 0 -> {
                            "void probe(Str| value) {}" to "String"
                        }

                        (cycle / (cursorCases.size + 1)) % 4 == 1 -> {
                            "void probe(Map<Int, List<Str|>> value) {}" to "String"
                        }

                        (cycle / (cursorCases.size + 1)) % 4 == 2 -> {
                            "interface Probe extends List<Str|> {}" to "String"
                        }

                        else -> {
                            "void probe(ecstasy.text.Str| value) {}" to "StringBuffer"
                        }
                    }
                val prefix = marked.substringBefore('|')
                val incomplete =
                    if (headerQuery) {
                        CONSUMER.replace("void probe(lib.Box box) {}", marked.replace("|", ""))
                    } else {
                        CONSUMER.replace("box) {}", "box) { ${marked.replace("|", "")} }")
                    }
                assertThat(adapter.compile(uri, incomplete).success).isFalse()
                val column = incomplete.lastIndexOf(prefix) + prefix.length
                assertThat(
                    adapter.getCompletionsAsync(uri, 0, column).awaited().map {
                        it.label
                    },
                ).contains(expected)
                val cancelled = adapter.getCompletionsAsync(uri, 0, column)
                cancelled.cancel(false)
                assertThat(adapter.compile(uri, CONSUMER).success).isTrue()
                // the rename waits behind another module's compile, so the close finds it queued however fast the
                // worker would have run it
                val occupying = adapter.compileAsync(other, "module Other {}")
                val gate = gates.awaited()
                val rename = adapter.renameAsync(uri, 0, CONSUMER.indexOf("local"), "renamed")
                adapter.closeDocument(uri)
                assertThat(rename.isCancelled).isTrue()
                gate.countDown()
                assertThat(occupying.awaited().success).isTrue()
                adapter.closeDocument(other)
                assertThat(adapter.getCachedResult(uri)).isNull()
            }
            assertReleased(watched, adapter, support)
        } finally {
            adapter.close()
            debounces.shutdownNow()
        }
        assertReleased(watched, adapter, support)
        val millis = timings.map(NANOSECONDS::toMillis).sorted()
        println(
            "Retention workload: cycles=$cycles, edit requests=${cycles * 8}, compiler objects=${watched.size}, reached=0, " +
                "rebuild p50=${millis[millis.size / 2]}ms, p95=${millis[(millis.size * 0.95).toInt()]}ms",
        )
    }

    /** Whether the adapter or the compiler still reaches a compiler attempt, without waiting for a collection. */
    private fun assertReleased(
        watched: Watched,
        adapter: XdkAdapter,
        support: EmbeddingSupport,
    ) {
        assertThat(watched.reachedFrom(adapter, support))
            .describedAs("compiler attempts, roots and pools still reached")
            .isEmpty()
    }

    private companion object {
        val CYCLES =
            System.getProperty("xtc.lsp.retentionCycles", "120").toInt().also {
                require(it >= 2) { "Retention workload requires at least two cycles" }
            }
        const val CONSUMER =
            "module Consumer { package lib import Library; " +
                "String textValue = \"x\"; Int sizeValue = 2; void textTake(String value) {} " +
                "Int run() { Int local = lib.value(); return local; } void take(lib.Box value) {} void probe(lib.Box box) {} }"
    }
}
