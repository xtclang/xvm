package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

class XdkProjectQueryLifecycleTest {
    @TempDir
    lateinit var directory: Path

    enum class Feature { REFERENCES, RENAME }

    private enum class Change { EDIT, CLOSE, CONFIGURATION, REPOSITORY, CANCEL }

    @Test
    fun `real adapter queries include unopened modules without installing proof results`() {
        Session().use { session ->
            val adapter = session.adapter
            assertThat(adapter.findReferences(session.libraryUri, 0, LIBRARY.indexOf("pick"), true).map { it.uri })
                .containsExactlyInAnyOrder(session.libraryUri, session.consumerUri)
            val edit = requireNotNull(adapter.rename(session.libraryUri, 0, LIBRARY.indexOf("pick"), "choose"))
            assertThat(edit.changes.keys).containsExactlyInAnyOrder(session.libraryUri, session.consumerUri)
            assertThat(adapter.getCachedResult(session.consumerUri)).isNull()
            assertThat(adapter.getCachedResult(session.libraryUri)?.diagnostics).isEmpty()
            assertThat(session.consumer.readText()).isEqualTo(CONSUMER)
        }
    }

    @Test
    fun `graph queries use unsaved consumer text and return its URI alias`() {
        Session().use { session ->
            val alias =
                session.consumer
                    .toPath()
                    .toUri()
                    .toString()
            val overlay = CONSUMER.replace("box.pick(1)", "box.pick(1)+box.pick(2)")
            assertThat(session.adapter.compile(alias, overlay).success).isTrue()
            val references = session.adapter.findReferences(session.libraryUri, 0, LIBRARY.indexOf("pick"), false)
            assertThat(references).hasSize(2)
            assertThat(references.map { it.uri }).containsOnly(alias)
            val edit = requireNotNull(session.adapter.rename(session.libraryUri, 0, LIBRARY.indexOf("pick"), "choose"))
            assertThat(edit.changes.getValue(alias)).hasSize(2)
            assertThat(session.consumer.readText()).isEqualTo(CONSUMER)
        }
    }

    @ParameterizedTest
    @EnumSource(Feature::class)
    fun `changes in another module and client cancellation retire blocked graph work`(feature: Feature) {
        for (change in Change.entries) {
            Session().use { session ->
                session.hold.set(true)
                val query = session.request(feature)
                assertThat(session.entered.await(20, SECONDS)).describedAs("$feature / $change").isTrue()
                when (change) {
                    Change.EDIT -> session.adapter.compileAsync(session.consumerUri, "\n$CONSUMER")
                    Change.CLOSE -> session.adapter.closeDocument(session.consumerUri)
                    Change.CONFIGURATION -> session.adapter.replaceSourceModules(listOf(session.libraryModule))
                    Change.REPOSITORY -> session.adapter.replaceDependencies(listOf(session.other))
                    Change.CANCEL -> query.cancel(false)
                }
                assertThat(query.isCancelled).describedAs("$feature / $change").isTrue()
                session.release.countDown()
                if (change == Change.CONFIGURATION) {
                    assertThat(session.adapter.getCachedResult(session.libraryUri)).isNull()
                } else {
                    assertThat(session.adapter.getCachedResult(session.libraryUri)?.success).isTrue()
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Feature::class)
    fun `closed disk changes without watcher delivery reject the captured graph`(feature: Feature) {
        Session().use { session ->
            session.hold.set(true)
            val query = session.request(feature)
            assertThat(session.entered.await(20, SECONDS)).isTrue()
            session.consumer.writeText("\n$CONSUMER")
            session.release.countDown()
            val result = query.get(30, SECONDS)
            when (feature) {
                Feature.REFERENCES -> assertThat(result as List<*>).isEmpty()
                Feature.RENAME -> assertThat(result).isNull()
            }
            assertThat(session.adapter.getCachedResult(session.libraryUri)?.diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @EnumSource(Feature::class)
    fun `a newer query supersedes blocked work but preserves shared analysis`(feature: Feature) {
        Session().use { session ->
            session.hold.set(true)
            val old = session.request(feature)
            assertThat(session.entered.await(20, SECONDS)).isTrue()
            val current = session.request(feature)
            assertThat(old.isCancelled).isTrue()
            session.release.countDown()
            assertThat(current.get(30, SECONDS)).isNotNull()
            assertThat(session.adapter.getCachedResult(session.libraryUri)?.success).isTrue()
        }
    }

    @Test
    fun `background code actions do not cancel a quick fix requested at another range`() {
        Session().use { session ->
            session.hold.set(true)
            val quickFix = session.adapter.getCodeActionsAsync(session.libraryUri, Range(Position(0, 25), Position(0, 29)), emptyList())
            assertThat(session.entered.await(20, SECONDS)).isTrue()
            val background = session.adapter.getCodeActionsAsync(session.libraryUri, Range(Position(0, 0), Position(0, 0)), emptyList())
            assertThat(quickFix.isCancelled).isFalse()
            session.release.countDown()
            assertThat(quickFix.get(30, SECONDS)).isEmpty()
            assertThat(background.get(30, SECONDS)).isEmpty()
        }
    }

    private inner class Session : AutoCloseable {
        val library =
            directory
                .toRealPath()
                .resolve("Library.x")
                .toFile()
                .apply { writeText(LIBRARY) }
        val consumer =
            directory
                .toRealPath()
                .resolve("Consumer.x")
                .toFile()
                .apply { writeText(CONSUMER) }
        val libraryUri = library.toURI().toString()
        val consumerUri = consumer.toURI().toString()
        val libraryModule = XdkSourceModule("Library", libraryUri)
        val hold = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        private val support = EmbeddingSupport.instance()
        val other =
            run {
                CompilerTestSupport.configure()
                support.compileModule(Source("module Other {}", "Other.x"), null, ErrorList()).toDependency()
            }
        val adapter =
            XdkAdapter(
                { source, repository, errors -> support.compileModule(source, repository, errors) },
                { sources, repository, errors ->
                    if (hold.compareAndSet(true, false)) {
                        entered.countDown()
                        check(release.await(20, SECONDS))
                        // Even a compiler callback ignoring cancellation cannot publish an obsolete proof.
                        support.compileModule(sources, repository, ErrorList())
                    } else {
                        support.compileModule(sources, repository, errors)
                    }
                },
                { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
            )

        init {
            adapter.replaceSourceModules(listOf(libraryModule, XdkSourceModule("Consumer", consumerUri, setOf("Library"))))
            val result = adapter.compile(libraryUri, LIBRARY)
            assertThat(result.success).describedAs(result.diagnostics.toString()).isTrue()
        }

        fun request(feature: Feature): CompletableFuture<*> =
            when (feature) {
                Feature.REFERENCES -> adapter.findReferencesAsync(libraryUri, 0, LIBRARY.indexOf("pick"), true)
                Feature.RENAME -> adapter.renameAsync(libraryUri, 0, LIBRARY.indexOf("pick"), "choose")
            }

        override fun close() {
            release.countDown()
            adapter.close()
        }
    }

    private companion object {
        const val LIBRARY = "module Library { class Box { Int pick(Int value)=value; } }"
        const val CONSUMER = "module Consumer { package lib import Library; Int run(lib.Box box)=box.pick(1); }"
    }
}
