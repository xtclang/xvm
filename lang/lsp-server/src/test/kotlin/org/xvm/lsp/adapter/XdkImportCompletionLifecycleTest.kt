package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

class XdkImportCompletionLifecycleTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["edit", "close", "configuration", "cancel"])
    fun `retired import proof cannot publish ordinary or import completions`(change: String) {
        CompilerTestSupport.configure()
        val support = EmbeddingSupport.instance()
        val hold = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val text = "module App { Doc value; }"
        val uri =
            directory
                .resolve("App.x")
                .toFile()
                .also { it.writeText(text) }
                .canonicalFile
                .toURI()
                .toString()
        XdkAdapter(
            { source, repository, errors -> support.compileModule(source, repository, errors) },
            { sources, repository, errors ->
                if (hold.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.await(20, SECONDS))
                    // A callback that ignores cancellation still cannot publish stale edits.
                    support.compileModule(sources, repository, ErrorList())
                } else {
                    support.compileModule(sources, repository, errors)
                }
            },
            { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
        ).use { adapter ->
            try {
                adapter.initializeWorkspace(listOf(directory.toString()))
                adapter.compile(uri, text)
                hold.set(true)
                val query = adapter.getCompletionsAsync(uri, 0, text.indexOf("Doc") + 3, null)
                assertThat(entered.await(20, SECONDS)).isTrue()
                when (change) {
                    "edit" -> adapter.compileAsync(uri, "\n$text")
                    "close" -> adapter.closeDocument(uri)
                    "configuration" -> adapter.replaceSourceModules(emptyList())
                    "cancel" -> query.cancel(false)
                    else -> error("Unknown change: $change")
                }
                assertThat(query.isCancelled).isTrue()
            } finally {
                release.countDown()
            }
        }
    }
}
