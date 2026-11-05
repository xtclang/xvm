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
            val (all, everything) = counted { adapter.getCodeActionsAsync(uri, range, emptyList()) }
            val (organized, organizing) =
                counted { adapter.getCodeActionsAsync(uri, range, emptyList(), setOf(CodeActionKind.SOURCE_ORGANIZE_IMPORTS)) }
            println("module compiles: every kind $everything, source.organizeImports $organizing")
            assertThat(organized).isEqualTo(all.filter { it.kind == CodeActionKind.SOURCE_ORGANIZE_IMPORTS }).hasSize(1)
            // the base compile, one proof for each of the three imports, and one for the edit that organizes them
            assertThat(organizing).isEqualTo(1 + 3 + 1)
            assertThat(everything).isGreaterThan(organizing)
            assertThat(all.map { it.kind }).contains(CodeActionKind.REFACTOR_EXTRACT)
        }
    }

    @Test
    fun `a request for refactorings proves no import`() {
        session { adapter, uri ->
            val range = Range(Position(9, 15), Position(9, 24))
            val (all, _) = counted { adapter.getCodeActionsAsync(uri, range, emptyList()) }
            val (extracted, extracting) =
                counted { adapter.getCodeActionsAsync(uri, range, emptyList(), setOf(CodeActionKind.REFACTOR_EXTRACT)) }
            println("module compiles: refactor.extract $extracting")
            assertThat(extracted).isEqualTo(all.filter { it.kind == CodeActionKind.REFACTOR_EXTRACT }).isNotEmpty()
            // the base compile and one proof for each extraction the selection allows, a local and a method
            assertThat(extracting).isEqualTo(1 + extracted.size)
            assertThat(counted { adapter.getCodeActionsAsync(uri, range, emptyList(), emptySet()) }).isEqualTo(emptyList<CodeAction>() to 0)
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
