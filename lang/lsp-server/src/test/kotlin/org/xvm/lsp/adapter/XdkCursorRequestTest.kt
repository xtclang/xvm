package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.FileStructure
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.PartialSemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.tool.ModuleInfo
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

class XdkCursorRequestTest {
    @TempDir
    lateinit var directory: Path

    private class PausedCursor {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val threads = CopyOnWriteArrayList<String>()

        fun analyze(
            source: Source,
            sources: ModuleInfo?,
            cursor: Long,
            errors: ErrorListener,
        ): EmbeddingSupport.PartialAnalysis {
            threads.add(Thread.currentThread().name)
            if (threads.size == 1) {
                started.countDown()
                check(release.await(10, SECONDS)) { "test did not release cursor analysis" }
            }
            // Deliberately finish with an independent listener: even an uninterruptible compiler
            // stage must not deliver old facts after edit, close, cancellation or shutdown.
            CompilerTestSupport.configure()
            val listener = if (errors.isAbortDesired) ErrorList() else errors
            val support = EmbeddingSupport.instance()
            return if (sources == null) {
                support.analyzeIncomplete(source, cursor, null, listener)
            } else {
                support.analyzeIncomplete(sources, Path.of(URI(source.fileName)).toFile(), cursor, null, listener)
            }
        }

        fun adapter(): XdkAdapter =
            XdkAdapter(
                { _, _ -> EmbeddingSupport.Compilation.forFile(FileStructure("Fixture")) },
                { _, _ -> EmbeddingSupport.Compilation.forFile(FileStructure("Fixture")) },
                ::analyze,
            )

        fun awaitStart() = check(started.await(10, SECONDS)) { "cursor analysis did not start" }
    }

    @Test
    fun `a running cursor cannot return facts after an edit`() {
        val compiler = PausedCursor()
        compiler.adapter().use { adapter ->
            adapter.compile(URI, text("String"))
            val old = adapter.analyzeAtAsync(URI, position("String"))
            try {
                compiler.awaitStart()
                val edit = adapter.compileAsync(URI, text("Int"))
                assertThat(old.isCancelled).isTrue()
                compiler.release.countDown()
                edit.get(10, SECONDS)
                val latest = adapter.analyzeAtAsync(URI, position("Int")).get(10, SECONDS)!!
                assertThat(receiverType(latest)).contains("Int")
                assertThat(compiler.threads).containsOnly("xtc-compile")
            } finally {
                compiler.release.countDown()
            }
        }
    }

    @Test
    fun `cursor requests are coalesced and cancellation leaves normal diagnostics intact`() {
        val compiler = PausedCursor()
        compiler.adapter().use { adapter ->
            adapter.compile(URI, text("String"))
            val cached = adapter.getCachedResult(URI)
            val running = adapter.analyzeAtAsync(URI, position("String"))
            try {
                compiler.awaitStart()
                val superseded = (1..20).map { adapter.analyzeAtAsync(URI, position("String")) }
                val latest = adapter.analyzeAtAsync(URI, position("String"))
                assertThat(running.isCancelled).isTrue()
                assertThat(superseded).allMatch { it.isCancelled }
                assertThat(latest.cancel(false)).isTrue()
                compiler.release.countDown()
                adapter.compileAsync("untitled:Barrier.x", text("String")).get(10, SECONDS)
                assertThat(compiler.threads).hasSize(1)
                assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            } finally {
                compiler.release.countDown()
            }
        }
    }

    @Test
    fun `completion and signature queries do not cancel one another`() {
        val compiler = PausedCursor()
        compiler.adapter().use { adapter ->
            adapter.compile(URI, text("String"))
            val completion = adapter.getCompletionsAsync(URI, 0, prefix("String").length)
            try {
                compiler.awaitStart()
                val signature = adapter.getSignatureHelpAsync(URI, 0, prefix("String").length)
                assertThat(completion.isDone).isFalse()
                compiler.release.countDown()
                assertThat(completion.get(10, SECONDS).map { it.label }).contains("size")
                assertThat(signature.get(10, SECONDS)).isNull()
                assertThat(compiler.threads).hasSize(2)
            } finally {
                compiler.release.countDown()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["member", "empty", "typed"])
    fun `canceling converted completion results cancels their compiler request`(kind: String) {
        val compiler = PausedCursor()
        val prefix =
            when (kind) {
                "member" -> prefix("String")
                "empty" -> "module Editing { void take(String value) {} void run(String text) { take("
                else -> "module Editing { void take(String value) {} void run(String text) { take(te"
            }
        val source = "$prefix } }"
        compiler.adapter().use { adapter ->
            adapter.compile(URI, source)
            val running = adapter.getCompletionsAsync(URI, 0, prefix.length)
            try {
                compiler.awaitStart()
                assertThat(running.cancel(false)).isTrue()
                compiler.release.countDown()
                adapter.compileAsync("untitled:Barrier.x", text("String")).get(10, SECONDS)
                assertThat(running.isCancelled).isTrue()
                assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).contains(
                    if (kind ==
                        "member"
                    ) {
                        "size"
                    } else {
                        "text"
                    },
                )
            } finally {
                compiler.release.countDown()
            }
        }
    }

    @Test
    fun `close and reopen invalidate a running cursor`() {
        val compiler = PausedCursor()
        compiler.adapter().use { adapter ->
            adapter.compile(URI, text("String"))
            val old = adapter.analyzeAtAsync(URI, position("String"))
            try {
                compiler.awaitStart()
                adapter.closeDocument(URI)
                assertThat(old.isCancelled).isTrue()
                assertThat(adapter.analyzeAtAsync(URI, position("String")).get(10, SECONDS)).isNull()
                val reopened = adapter.compileAsync(URI, text("Int"))
                compiler.release.countDown()
                reopened.get(10, SECONDS)
                assertThat(receiverType(adapter.analyzeAtAsync(URI, position("Int")).get(10, SECONDS)!!)).contains("Int")
            } finally {
                compiler.release.countDown()
            }
        }
    }

    @Test
    fun `shutdown cancels running and queued cursor work`() {
        val compiler = PausedCursor()
        val adapter = compiler.adapter()
        try {
            adapter.compile(URI, text("String"))
            val running = adapter.analyzeAtAsync(URI, position("String"))
            compiler.awaitStart()
            val other = "untitled:Other.x"
            val compilation = adapter.compileAsync(other, text("String"))
            val queued = adapter.analyzeAtAsync(other, position("String"))
            running.whenComplete { _, _ -> compiler.release.countDown() }
            adapter.close()
            assertThat(running.isCancelled).isTrue()
            assertThat(compilation.isCancelled).isTrue()
            assertThat(queued.isCancelled).isTrue()
            assertThat(compiler.threads).hasSize(1)
            assertThat(adapter.analyzeAtAsync(URI, position("String")).isCompletedExceptionally).isTrue()
        } finally {
            compiler.release.countDown()
            adapter.close()
        }
    }

    @Test
    fun `real module cursor requests use overlays without replacing diagnostics`() {
        val root = directory.resolve("Editing.x").toFile().canonicalFile
        val member = directory.resolve("Editing/Child.x").toFile().canonicalFile
        member.parentFile.mkdirs()
        root.writeText("module Editing { class Base { Int value = 1; } }")
        val prefix = "class Child extends Base { Int run() { return value."
        member.writeText("$prefix } }")
        XdkAdapter().use { adapter ->
            adapter.compile(root.toURI().toString(), "module Editing { class Base { String value = \"overlay\"; } }")
            val uri = member.toURI().toString()
            adapter.compile(uri, member.readText())
            val cached = adapter.getCachedResult(uri)
            val snapshot = adapter.analyzeAtAsync(uri, Position(0, prefix.length)).get(10, SECONDS)!!
            assertThat(receiverType(snapshot)).contains("String")
            assertThat(snapshot.semantics.sourceName).isEqualTo(member.path)
            assertThat(adapter.getCachedResult(uri)).isEqualTo(cached)
            assertThat(adapter.analyzeAtAsync(uri, Position(100, 0)).get(10, SECONDS)).isNull()
            assertThat(root.readText()).contains("Int value")
        }
    }

    @Test
    fun `editing a module root cancels its member cursor request`() {
        val root = directory.resolve("Editing.x").toFile().canonicalFile
        val member = directory.resolve("Editing/Child.x").toFile().canonicalFile
        member.parentFile.mkdirs()
        val diskRoot = "module Editing { class Base { Int value = 1; } }"
        root.writeText(diskRoot)
        val prefix = "class Child extends Base { Int run() { return value."
        member.writeText("$prefix } }")
        val rootUri = root.toURI().toString()
        val uri = member.toURI().toString()
        val compiler = PausedCursor()
        compiler.adapter().use { adapter ->
            adapter.compile(rootUri, "module Editing { class Base { String value = \"overlay\"; } }")
            adapter.compile(uri, member.readText())
            val old = adapter.analyzeAtAsync(uri, Position(0, prefix.length))
            try {
                compiler.awaitStart()
                val edited = adapter.compileAsync(rootUri, diskRoot)
                assertThat(old.isCancelled).isTrue()
                compiler.release.countDown()
                edited.get(10, SECONDS)
                val latest = adapter.analyzeAtAsync(uri, Position(0, prefix.length)).get(10, SECONDS)!!
                assertThat(receiverType(latest)).contains("Int")
                assertThat(compiler.threads).hasSize(2).containsOnly("xtc-compile")
            } finally {
                compiler.release.countDown()
            }
        }
    }

    @Test
    fun `editor positions retain UTF-16 columns across CRLF and source escapes`() {
        val prefix = "    Int run(String value) { String text = \"😀\\u0041\"; return value."
        val text = "module Editing {\r\n$prefix }\r\n}"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, text)
            val snapshot = adapter.analyzeAtAsync(URI, Position(1, prefix.length)).get(10, SECONDS)!!
            assertThat(receiverType(snapshot)).contains("String")
            assertThat(
                snapshot.sites
                    .single()
                    .range.end.line,
            ).isEqualTo(1)
            assertThat(
                snapshot.sites
                    .single()
                    .range.end.column,
            ).isEqualTo(prefix.length)
            listOf(Position(-1, 0), Position(0, -1), Position(1, prefix.length + 100)).forEach {
                assertThat(adapter.analyzeAtAsync(URI, it).get(10, SECONDS)).isNull()
            }
        }
    }

    private fun prefix(type: String): String = "module Editing { Int run($type value) { return value."

    private fun text(type: String): String = prefix(type) + " } }"

    private fun position(type: String): Position = Position(0, prefix(type).length)

    private fun receiverType(model: PartialSemanticModel): String = model.semantics.type(model.sites.single().receiverType!!)!!.displayName

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
