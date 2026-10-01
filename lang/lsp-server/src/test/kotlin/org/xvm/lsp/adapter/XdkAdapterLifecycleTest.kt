package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport.Compilation
import org.xvm.asm.ErrorListener
import org.xvm.asm.FileStructure
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

class XdkAdapterLifecycleTest {
    private class PausedCompiler {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val seen = CopyOnWriteArrayList<String>()

        fun compile(
            source: Source,
            errs: ErrorListener,
        ): Compilation {
            val text = source.toRawString()
            seen.add(text)
            if (seen.size == 1) {
                started.countDown()
                check(release.await(10, SECONDS)) { "test did not release compiler" }
            }
            // Deliberately ignore cancellation: even a compiler stage that cannot stop must
            // never install its old result after an edit, close or shutdown.
            errs.error("PARSER-03", ErrorListener.`in`(source, 0, 1), text)
            return Compilation.forFile(FileStructure("Fixture"))
        }

        fun awaitStart() = check(started.await(10, SECONDS)) { "compiler did not start" }
    }

    @Test
    fun `a running analysis cannot overwrite a newer edit`() {
        val compiler = PausedCompiler()
        XdkAdapter(compiler::compile).use { adapter ->
            val old = adapter.compileAsync(URI, "old")
            try {
                compiler.awaitStart()
                val latest = adapter.compileAsync(URI, "latest")
                assertThat(old.isCancelled).isTrue()
                assertThat(adapter.getCachedResult(URI)).isNull()
                compiler.release.countDown()
                val result = latest.get(10, SECONDS)
                assertThat(result.diagnostics.single().message).contains("latest")
                assertThat(adapter.getCachedResult(URI)).isEqualTo(result)
            } finally {
                compiler.release.countDown()
            }
        }
    }

    @Test
    fun `queued edits are coalesced before they reach the compiler`() {
        val compiler = PausedCompiler()
        XdkAdapter(compiler::compile).use { adapter ->
            val blocker = adapter.compileAsync("file:///Other.x", "blocker")
            try {
                compiler.awaitStart()
                val superseded = (1..50).map { adapter.compileAsync(URI, "edit$it") }
                val latest = adapter.compileAsync(URI, "latest")
                assertThat(superseded).allMatch { it.isCancelled }
                compiler.release.countDown()
                blocker.get(10, SECONDS)
                latest.get(10, SECONDS)
                assertThat(compiler.seen).containsExactly("blocker", "latest")
            } finally {
                compiler.release.countDown()
            }
        }
    }

    @Test
    fun `closing and reopening does not revive the old analysis`() {
        val compiler = PausedCompiler()
        XdkAdapter(compiler::compile).use { adapter ->
            val old = adapter.compileAsync(URI, "old")
            try {
                compiler.awaitStart()
                adapter.closeDocument(URI)
                assertThat(old.isCancelled).isTrue()
                assertThat(adapter.getCachedResult(URI)).isNull()
                val reopened = adapter.compileAsync(URI, "reopened")
                compiler.release.countDown()
                assertThat(
                    reopened
                        .get(10, SECONDS)
                        .diagnostics
                        .single()
                        .message,
                ).contains("reopened")
                adapter.closeDocument(URI)
                assertThat(adapter.getCachedResult(URI)).isNull()
                assertThat(adapter.findWorkspaceSymbols("")).isEmpty()
            } finally {
                compiler.release.countDown()
            }
        }
    }

    @Test
    fun `a caller can cancel an active analysis without leaving a cache entry`() {
        val compiler = PausedCompiler()
        XdkAdapter(compiler::compile).use { adapter ->
            val cancelled = adapter.compileAsync(URI, "cancelled")
            try {
                compiler.awaitStart()
                assertThat(cancelled.cancel(false)).isTrue()
                compiler.release.countDown()
                // Completion of the next job proves that the cancelled worker has finished.
                adapter.compileAsync("file:///Other.x", "next").get(10, SECONDS)
                assertThat(adapter.getCachedResult(URI)).isNull()
            } finally {
                compiler.release.countDown()
            }
        }
    }

    @Test
    fun `selection has one response per cursor even without a parsed tree`() {
        XdkAdapter().use { adapter ->
            val positions = listOf(Position(0, 0), Position(1, 2))
            val ranges = adapter.getSelectionRanges(URI, positions)
            assertThat(ranges.map { it.range.start }).containsExactlyElementsOf(positions)
            assertThat(ranges.map { it.range.end }).containsExactlyElementsOf(positions)
        }
    }

    @Test
    fun `shutdown cancels active and queued work and releases cached analyses`() {
        val compiler = PausedCompiler()
        val adapter = XdkAdapter(compiler::compile)
        try {
            val running = adapter.compileAsync(URI, "running")
            compiler.awaitStart()
            val queued = adapter.compileAsync("file:///Other.x", "queued")
            running.whenComplete { _, _ -> compiler.release.countDown() }
            adapter.close()
            assertThat(running.isCancelled).isTrue()
            assertThat(queued.isCancelled).isTrue()
            assertThat(adapter.getCachedResult(URI)).isNull()
            assertThat(adapter.compileAsync(URI, "too late").isCompletedExceptionally).isTrue()
            assertThat(compiler.seen).containsExactly("running")
        } finally {
            compiler.release.countDown()
            adapter.close()
        }
    }

    private companion object {
        const val URI = "file:///Lifecycle.x"
    }
}
