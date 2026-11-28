package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import org.xvm.testing.awaited
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Forces changes and cancellation during project queries. Open overlays, closed disk inputs and graph
 * ownership must stay current until publication; trial results must not become normal editor analyses.
 */
class XdkProjectQueryLifecycleTest {
    @TempDir lateinit var directory: Path

    /** Selects the project query whose ownership and stale-result behavior is exercised. */
    enum class Feature {
        REFERENCES,
        CODE_LENSES,
        RENAME,
    }

    /** Enumerates changes that must retire an outstanding project query. */
    private enum class Change {
        EDIT,
        CLOSE,
        CONFIGURATION,
        REPOSITORY,
        CANCEL,
    }

    @Test
    fun `real adapter queries include unopened modules without installing proof results`() {
        Session().use { session ->
            val adapter = session.adapter
            assertThat(
                adapter
                    .findReferences(session.libraryUri, 0, LIBRARY.indexOf("pick"), true)
                    .map { it.uri },
            ).containsExactlyInAnyOrder(session.libraryUri, session.consumerUri)
            val edit =
                requireNotNull(
                    adapter.rename(session.libraryUri, 0, LIBRARY.indexOf("pick"), "choose"),
                )
            assertThat(edit.changes.keys)
                .containsExactlyInAnyOrder(session.libraryUri, session.consumerUri)
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
            val overlay = CONSUMER.replace("box.pick(1)", "box.pick(1) + box.pick(2)")
            assertThat(session.adapter.compile(alias, overlay).success).isTrue()
            val references =
                session.adapter.findReferences(
                    session.libraryUri,
                    0,
                    LIBRARY.indexOf("pick"),
                    false,
                )
            assertThat(references).hasSize(2)
            assertThat(references.map { it.uri }).containsOnly(alias)
            val edit =
                requireNotNull(
                    session.adapter.rename(session.libraryUri, 0, LIBRARY.indexOf("pick"), "choose"),
                )
            assertThat(edit.changes.getValue(alias)).hasSize(2)
            assertThat(session.consumer.readText()).isEqualTo(CONSUMER)
        }
    }

    @ParameterizedTest
    @EnumSource(Feature::class)
    fun `changes in another module and client cancellation retire blocked graph work`(feature: Feature) {
        for (change in Change.entries) {
            Session().use { session ->
                val retained = session.adapter.getCachedResult(session.libraryUri)
                session.hold.set(true)
                val query = session.request(feature)
                assertThat(session.entered.awaited())
                    .describedAs("$feature / $change")
                    .isTrue()
                when (change) {
                    Change.EDIT -> {
                        session.adapter.compileAsync(session.consumerUri, "\n$CONSUMER")
                    }

                    Change.CLOSE -> {
                        session.adapter.closeDocument(session.consumerUri)
                    }

                    Change.CONFIGURATION -> {
                        session.adapter.replaceSourceModules(listOf(session.libraryModule))
                    }

                    Change.REPOSITORY -> {
                        session.adapter.replaceDependencies(listOf(session.other))
                    }

                    Change.CANCEL -> {
                        query.cancel(false)
                    }
                }
                assertThat(query.isCancelled).describedAs("$feature / $change").isTrue()
                session.release.countDown()
                assertThat(session.adapter.getCachedResult(session.libraryUri)).isEqualTo(retained)
                if (change == Change.CONFIGURATION) {
                    // The independent library stays valid, but graph queries must use the new graph.
                    when (feature) {
                        Feature.REFERENCES -> {
                            assertThat(
                                session.adapter
                                    .findReferences(session.libraryUri, 0, LIBRARY.indexOf("pick"), true)
                                    .map { it.uri },
                            ).containsExactly(session.libraryUri)
                        }

                        Feature.CODE_LENSES -> {
                            val lenses = session.adapter.getCodeLenses(session.libraryUri)
                            assertThat(lenses.filter { it.command?.command == "xtc.showReferences" })
                                .allMatch { it.command!!.title == "0 references" }
                        }

                        Feature.RENAME -> {
                            assertThat(
                                session.adapter
                                    .rename(session.libraryUri, 0, LIBRARY.indexOf("pick"), "choose")
                                    ?.changes
                                    ?.keys,
                            ).containsExactly(session.libraryUri)
                        }
                    }
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
            assertThat(session.entered.awaited()).isTrue()
            session.consumer.writeText("\n$CONSUMER")
            session.release.countDown()
            when (feature) {
                Feature.REFERENCES -> {
                    assertThat(query.awaited() as List<*>).isEmpty()
                }

                Feature.CODE_LENSES -> {
                    // The rejected graph yields no reference lenses, and Library has no entry point to run.
                    assertThat((query.awaited() as List<*>).filterIsInstance<CodeLens>()).isEmpty()
                }

                Feature.RENAME -> {
                    // overtaken, not refused: the rename asked again proves the graph as it now is
                    assertThatThrownBy { query.awaited() }.rootCause().isInstanceOf(RenameOvertaken::class.java)
                }
            }
            assertThat(session.adapter.getCachedResult(session.libraryUri)?.diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @EnumSource(Feature::class)
    fun `a newer query of something else supersedes blocked work but preserves shared analysis`(feature: Feature) {
        Session().use { session ->
            session.hold.set(true)
            val old = session.request(feature)
            assertThat(session.entered.awaited()).isTrue()
            val current = session.request(feature, other = true)
            assertThat(old.isCancelled).isTrue()
            session.release.countDown()
            assertThat(current.awaited()).isNotNull()
            assertThat(session.adapter.getCachedResult(session.libraryUri)?.success).isTrue()
        }
    }

    @ParameterizedTest
    @EnumSource(Feature::class)
    fun `blocked work that two requests share goes on as one of them is canceled`(feature: Feature) {
        Session().use { session ->
            session.hold.set(true)
            val old = session.request(feature)
            assertThat(session.entered.awaited()).isTrue()
            val current = session.request(feature)
            // as LSP4IJ cancels its own request of a document's code lenses, which the playbook's shares
            old.cancel(true)
            assertThat(current).isNotDone()
            session.release.countDown()
            assertThat(current.awaited()).isNotNull()
        }
    }

    @ParameterizedTest
    @EnumSource(Feature::class)
    fun `an equal newer query shares blocked work, and both answer`(feature: Feature) {
        Session().use { session ->
            session.hold.set(true)
            val old = session.request(feature)
            assertThat(session.entered.awaited()).isTrue()
            val current = session.request(feature)
            session.release.countDown()
            assertThat(old.awaited()).isNotNull()
            assertThat(current.awaited()).isNotNull()
        }
    }

    @Test
    fun `background code actions do not cancel a quick fix requested at another range`() {
        Session().use { session ->
            session.hold.set(true)
            val quickFix =
                session.adapter.getCodeActionsAsync(
                    session.libraryUri,
                    Range(Position(0, 25), Position(0, 29)),
                    emptyList(),
                )
            assertThat(session.entered.awaited()).isTrue()
            val background =
                session.adapter.getCodeActionsAsync(
                    session.libraryUri,
                    Range(Position(0, 0), Position(0, 0)),
                    emptyList(),
                )
            assertThat(quickFix.isCancelled).isFalse()
            session.release.countDown()
            assertThat(quickFix.awaited()).allMatch {
                it.kind == CodeAction.CodeActionKind.REFACTOR_REWRITE
            }
            assertThat(background.awaited().map { it.title }).containsExactly("Generate documentation comment")
        }
    }

    /** Owns a real adapter and a two-module fixture with controllable compilation delays. */
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
                support
                    .compileModule(Source("module Other {}", "Other.x"), null, ErrorList())
                    .toDependency()
            }
        val adapter =
            XdkAdapter(
                { source, repository, errors -> support.compileModule(source, repository, errors) },
                { sources, repository, errors ->
                    if (hold.compareAndSet(true, false)) {
                        entered.countDown()
                        check(release.awaited())
                        // Even a compiler callback ignoring cancellation cannot publish an obsolete
                        // proof.
                        support.compileModule(sources, repository, ErrorList())
                    } else {
                        support.compileModule(sources, repository, errors)
                    }
                },
                { source, _, cursor, repository, errors ->
                    support.analyzeIncomplete(source, cursor, repository, errors)
                },
            )

        init {
            adapter.replaceSourceModules(
                listOf(libraryModule, XdkSourceModule("Consumer", consumerUri, setOf("Library"))),
            )
            val result = adapter.compile(libraryUri, LIBRARY)
            assertThat(result.success).describedAs(result.diagnostics.toString()).isTrue()
        }

        /** The query of [feature]; or, [other], one that asks something else of it, or the same of other text. */
        fun request(
            feature: Feature,
            other: Boolean = false,
        ): CompletableFuture<*> =
            when (feature) {
                Feature.REFERENCES -> {
                    adapter.findReferencesAsync(libraryUri, 0, LIBRARY.indexOf("pick"), !other)
                }

                Feature.CODE_LENSES -> {
                    if (other) adapter.updateDocument(consumerUri, "\n$CONSUMER")
                    adapter.getCodeLensesAsync(libraryUri, true)
                }

                Feature.RENAME -> {
                    adapter.renameAsync(libraryUri, 0, LIBRARY.indexOf("pick"), if (other) "select" else "choose")
                }
            }

        override fun close() {
            release.countDown()
            adapter.close()
        }
    }

    private companion object {
        const val LIBRARY = "module Library { class Box { Int pick(Int value) = value; } }"
        const val CONSUMER =
            "module Consumer { package lib import Library; Int run(lib.Box box) = box.pick(1); }"
    }
}
