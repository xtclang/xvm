package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.awaited
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * A code action request that its client cancels while a proof compiles runs no further proof. The proof is held with
 * a hand-off, not timed, and the work is counted as module compiles.
 */
class XdkCodeActionCancellationTest {
    @TempDir lateinit var directory: Path

    /** The module compiles so far; the compiler queue runs them one at a time. */
    private val compiles = AtomicInteger()

    /** The number of the module compile that waits for [release], once it has signaled [entered]. */
    @Volatile private var held = 0
    private val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)

    @Test
    fun `a request cancelled during a proof runs no further proof`() {
        session { adapter, uri ->
            val start = compiles.get()
            // the request's base compile, then its first proof, of the first of three imports
            held = start + 2
            val request = adapter.getCodeActionsAsync(uri, Range(Position(9, 15), Position(9, 24)), emptyList())
            assertThat(entered.awaited()).isTrue()
            request.cancel(false)
            release.countDown()
            // the queue runs one query at a time, so the cancelled one has ended once a later one answers
            adapter.getSignatureHelpAsync(uri, 9, 15).awaited()
            assertThat(request.isCancelled).isTrue()
            assertThat(compiles.get() - start).isEqualTo(2)
            // uncancelled, the same request runs every proof
            assertThat(adapter.getCodeActionsAsync(uri, Range(Position(9, 15), Position(9, 24)), emptyList()).awaited())
                .isNotEmpty()
            assertThat(compiles.get() - start).isGreaterThan(2 + 5)
        }
    }

    private fun session(body: (XdkAdapter, String) -> Unit) {
        CompilerTestSupport.configure()
        val support = EmbeddingSupport.instance()
        val uri =
            directory
                .resolve("App.x")
                .toFile()
                .also { it.writeText(SOURCE) }
                .canonicalFile
                .toURI()
                .toString()
        XdkAdapter(
            { source, repository, errors -> support.compileModule(source, repository, errors) },
            { sources, repository, errors ->
                if (compiles.incrementAndGet() == held) {
                    entered.countDown()
                    check(release.awaited())
                }
                support.compileModule(sources, repository, errors)
            },
            { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
        ).use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, SOURCE).diagnostics).isEmpty()
            body(adapter, uri)
        }
    }

    private companion object {
        /** Three imports that every module has implicitly, so each is unused, and a selection, `input + 2`, to extract. */
        val SOURCE =
            """
            module App {
                import ecstasy.text.StringBuffer;
                import ecstasy.maps.HashMap;
                import ecstasy.maps.ListMap;

                StringBuffer buffer() = new StringBuffer();

                Int run(Int input) {
                    Int unused = input + 1;
                    return input + 2;
                }
            }
            """.trimIndent() + "\n"
    }
}
