package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class XdkProjectTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `source artifacts are reused only when captured sources and dependency revisions match`() {
        val (library, consumer) = fixture()
        val calls = ConcurrentHashMap<String, AtomicInteger>()
        val support = EmbeddingSupport.instance()
        XdkAdapter(
            { source, repository, errors -> support.compileModule(source, repository, errors) },
            { sources, repository, errors ->
                calls.computeIfAbsent(sources.sourceFile.name) { AtomicInteger() }.incrementAndGet()
                support.compileModule(sources, repository, errors)
            },
            { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
        ).use { adapter ->
            configure(adapter, library, consumer)
            assertThat(adapter.compile(consumer.toURI().toString(), CONSUMER).success).isTrue()
            assertThat(adapter.compile(consumer.toURI().toString(), "\n$CONSUMER").success).isTrue()
            assertThat(calls.getValue("Library.x").get()).isEqualTo(1)
            assertThat(calls.getValue("Consumer.x").get()).isEqualTo(2)
            library.writeText(INCOMPATIBLE)
            assertThat(adapter.compile(consumer.toURI().toString(), CONSUMER).success).isFalse()
            assertThat(calls.getValue("Library.x").get()).isEqualTo(2)
        }
    }

    @Test
    fun `rapid edits cancel obsolete futures and coalesce before entering the compiler`() {
        val (library, consumer) = fixture()
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val support = EmbeddingSupport.instance()
        XdkAdapter(
            { source, repository, errors ->
                entered.countDown()
                check(release.await(10, SECONDS))
                support.compileModule(source, repository, errors)
            },
            { sources, repository, errors ->
                calls.incrementAndGet()
                support.compileModule(sources, repository, errors)
            },
            { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
        ).use { adapter ->
            configure(adapter, library, consumer)
            val blocker = adapter.compileAsync("untitled:Hold.x", "module Hold {}")
            try {
                check(entered.await(10, SECONDS))
                val requests = (1..30).map { adapter.compileAsync(consumer.toURI().toString(), CONSUMER + " // $it") }
                release.countDown()
                assertThat(blocker.get(30, SECONDS).success).isTrue()
                assertThat(requests.last().get(30, SECONDS).success).isTrue()
                assertThat(requests.dropLast(1)).allMatch { it.isCancelled }
                assertThat(calls.get()).isEqualTo(2)
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `all closed source members are captured before the first dependency enters compilation`() {
        val (library, consumer) = fixture()
        val member = File(consumer.parentFile, "Consumer/Extra.x")
        member.parentFile.mkdirs()
        member.writeText("class Extra {}")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val held = AtomicBoolean()
        val support = EmbeddingSupport.instance()
        XdkAdapter(
            { source, repository, errors -> support.compileModule(source, repository, errors) },
            { sources, repository, errors ->
                if (!held.getAndSet(true)) {
                    entered.countDown()
                    check(release.await(10, SECONDS))
                }
                support.compileModule(sources, repository, errors)
            },
            { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
        ).use { adapter ->
            configure(adapter, library, consumer)
            val first = adapter.compileAsync(consumer.toURI().toString(), CONSUMER)
            try {
                check(entered.await(10, SECONDS))
                member.writeText("class Extra { MissingType broken; }")
                release.countDown()
                assertThat(first.get(30, SECONDS).success).isTrue()
                assertThat(adapter.compile(consumer.toURI().toString(), CONSUMER).diagnostics).anyMatch { it.code == "COMPILER-38" }
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `an uncooperative old dependency compilation cannot cache or publish stale facts`() {
        val (library, consumer) = fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val held = AtomicBoolean()
        val support = EmbeddingSupport.instance()
        XdkAdapter(
            { source, repository, errors -> support.compileModule(source, repository, errors) },
            { sources, repository, errors ->
                if (!held.getAndSet(true)) {
                    entered.countDown()
                    check(release.await(10, SECONDS))
                    support.compileModule(sources, repository, ErrorList())
                } else {
                    support.compileModule(sources, repository, errors)
                }
            },
            { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
        ).use { adapter ->
            configure(adapter, library, consumer)
            val old = adapter.compileAsync(consumer.toURI().toString(), CONSUMER)
            try {
                check(entered.await(10, SECONDS))
                adapter.compileAsync(library.toURI().toString(), INCOMPATIBLE)
                val latest = adapter.compileAsync(consumer.toURI().toString(), CONSUMER)
                assertThat(old.isCancelled).isTrue()
                assertThat(adapter.getCachedResult(consumer.toURI().toString())).isNull()
                release.countDown()
                assertThat(latest.get(30, SECONDS).success).isFalse()
                assertThat(adapter.getCachedResult(consumer.toURI().toString())?.success).isFalse()
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `dependency edits cancel cursor work and new probes see rebuilt member types`() {
        val (library, consumer) = fixture()
        library.writeText("module Library { class Box { Int number=1; } }")
        val source = "module Consumer { package lib import Library; void run(lib.Box box) { box. } }"
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val held = AtomicBoolean()
        val support = EmbeddingSupport.instance()
        XdkAdapter(
            { text, repository, errors -> support.compileModule(text, repository, errors) },
            { sources, repository, errors -> support.compileModule(sources, repository, errors) },
            { text, sources, cursor, repository, errors ->
                if (!held.getAndSet(true)) {
                    entered.countDown()
                    check(release.await(10, SECONDS))
                }
                support.analyzeIncomplete(sources, File(URI(text.fileName)), cursor, repository, errors)
            },
        ).use { adapter ->
            configure(adapter, library, consumer)
            adapter.compile(consumer.toURI().toString(), source)
            val cursor = source.indexOf("box.") + 4
            val old = adapter.getCompletionsAsync(consumer.toURI().toString(), 0, cursor, ".")
            try {
                check(entered.await(10, SECONDS))
                adapter.compileAsync(library.toURI().toString(), "module Library { class Box { String label=\"new\"; } }")
                assertThat(old.isCompletedExceptionally).isTrue()
                assertThatThrownBy { old.join() }.hasRootCauseInstanceOf(CancellationException::class.java)
                release.countDown()
                adapter.compile(consumer.toURI().toString(), source)
                val candidates = adapter.getCompletionsAsync(consumer.toURI().toString(), 0, cursor, ".").get(20, SECONDS)
                assertThat(candidates.map { it.label }).contains("label").doesNotContain("number")
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `cyclic duplicate and mismatched module configurations fail explicitly without replacing a valid graph`() {
        val (library, consumer) = fixture()
        XdkAdapter().use { adapter ->
            configure(adapter, library, consumer)
            val uri = consumer.toURI().toString()
            assertThat(adapter.compile(uri, CONSUMER).success).isTrue()
            val cached = adapter.getCachedResult(uri)
            assertThatThrownBy {
                adapter.replaceSourceModules(
                    listOf(
                        XdkSourceModule("Library", library.toURI().toString(), setOf("Consumer")),
                        XdkSourceModule("Consumer", uri, setOf("Library")),
                    ),
                )
            }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Cyclic")
            assertThatThrownBy {
                adapter.replaceSourceModules(
                    listOf(XdkSourceModule("Library", uri), XdkSourceModule("Library", library.toURI().toString())),
                )
            }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Duplicate")
            assertThat(adapter.getCachedResult(uri)).isEqualTo(cached)
            adapter.replaceSourceModules(listOf(XdkSourceModule("Wrong", library.toURI().toString())))
            assertThat(adapter.compile(library.toURI().toString(), LIBRARY).diagnostics).anyMatch { it.code == "PROJECT-MODULE" }
        }
    }

    private fun configure(
        adapter: XdkAdapter,
        library: File,
        consumer: File,
    ) = adapter.replaceSourceModules(
        listOf(
            XdkSourceModule("Library", library.toURI().toString()),
            XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")),
        ),
    )

    private fun fixture(): Pair<File, File> {
        CompilerTestSupport.configure()
        val library =
            directory
                .toRealPath()
                .resolve("Library.x")
                .toFile()
                .also { it.writeText(LIBRARY) }
        val consumer =
            directory
                .toRealPath()
                .resolve("Consumer.x")
                .toFile()
                .also { it.writeText(CONSUMER) }
        return library to consumer
    }

    private companion object {
        const val LIBRARY = "module Library { static Int value()=1; }"
        const val INCOMPATIBLE = "module Library { static String value()=\"text\"; }"
        const val CONSUMER = "module Consumer { package lib import Library; Int run()=lib.value(); }"
    }
}
