package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.HANG_SECONDS
import org.xvm.lsp.adapter.CodeAction.CodeActionKind
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

/**
 * What a code action request costs, counted as the module compiles its proofs run, never as time: a request computes
 * only the kinds it asks for.
 */
class XdkCodeActionCostTest {
    @TempDir lateinit var directory: Path

    /** The module compiles, the base compile and each proof; the compiler queue runs one at a time. */
    private val compiles = AtomicInteger()

    @Test
    fun `a request for organize imports runs only the import proofs`() {
        session { adapter, uri ->
            val range = Range(Position(9, 15), Position(9, 24))
            // the first request of the revision, which shares nothing yet
            val (organized, organizing) =
                counted { adapter.getCodeActionsAsync(uri, range, emptyList(), setOf(CodeActionKind.SOURCE_ORGANIZE_IMPORTS)) }
            val (all, _) = counted { adapter.getCodeActionsAsync(uri, range, emptyList()) }
            println("module compiles: source.organizeImports $organizing")
            assertThat(organized).isEqualTo(all.filter { it.kind == CodeActionKind.SOURCE_ORGANIZE_IMPORTS }).hasSize(1)
            // the base compile, one proof for each of the three imports, and one for the edit that organizes them
            assertThat(organizing).isEqualTo(1 + 3 + 1)
            assertThat(all.map { it.kind }).contains(CodeActionKind.REFACTOR_EXTRACT)
        }
    }

    @Test
    fun `a request for refactorings proves no import`() {
        session { adapter, uri ->
            val range = Range(Position(9, 15), Position(9, 24))
            // the first request of the revision, which shares nothing yet
            val (extracted, extracting) =
                counted { adapter.getCodeActionsAsync(uri, range, emptyList(), setOf(CodeActionKind.REFACTOR_EXTRACT)) }
            val (all, _) = counted { adapter.getCodeActionsAsync(uri, range, emptyList()) }
            println("module compiles: refactor.extract $extracting")
            assertThat(extracted).isEqualTo(all.filter { it.kind == CodeActionKind.REFACTOR_EXTRACT }).isNotEmpty()
            // the base compile and one proof for each extraction the selection allows, a local and a method
            assertThat(extracting).isEqualTo(1 + extracted.size)
            assertThat(counted { adapter.getCodeActionsAsync(uri, range, emptyList(), emptySet()) }).isEqualTo(emptyList<CodeAction>() to 0)
        }
    }

    @Test
    fun `the requests of one revision share its compile and its import proofs`() {
        session { adapter, uri ->
            val range = Range(Position(9, 15), Position(9, 24))
            val (first, proving) = counted { adapter.getCodeActionsAsync(uri, range, emptyList()) }
            val (again, reproving) = counted { adapter.getCodeActionsAsync(uri, range, emptyList()) }
            val (organized, organizing) =
                counted { adapter.getCodeActionsAsync(uri, range, emptyList(), setOf(CodeActionKind.SOURCE_ORGANIZE_IMPORTS)) }
            println("module compiles: first request $proving, the same again $reproving, organize imports then $organizing")
            assertThat(again).isEqualTo(first)
            assertThat(organized).isEqualTo(first.filter { it.kind == CodeActionKind.SOURCE_ORGANIZE_IMPORTS })
            // again, nothing is proved: the compile, the imports and the two extractions the range allows are shared
            assertThat(proving).isEqualTo(1 + 3 + 1 + 2)
            assertThat(reproving).isZero()
            assertThat(organizing).isZero()
            // an edit is another revision, which proves its own
            assertThat(adapter.compile(uri, SOURCE.replace("input + 2", "input + 3")).diagnostics).isEmpty()
            assertThat(
                counted { adapter.getCodeActionsAsync(uri, range, emptyList(), setOf(CodeActionKind.SOURCE_ORGANIZE_IMPORTS)) }.second,
            ).isEqualTo(1 + 3 + 1)
        }
    }

    /** The actions a request answers with, and the module compiles it ran. */
    private fun counted(request: () -> CompletableFuture<List<CodeAction>>): Pair<List<CodeAction>, Int> {
        val start = compiles.get()
        val actions = request().get(HANG_SECONDS, SECONDS)
        return actions to compiles.get() - start
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
                compiles.incrementAndGet()
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
