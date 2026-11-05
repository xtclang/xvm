package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.HANG_SECONDS
import org.xvm.lsp.adapter.CodeAction.CodeActionKind
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

/**
 * What LSP4IJ's code action requests cost on the tour's card game, counted as module compiles, never as time: its
 * intention list's request on each caret move, Automatic with no only, which the server asks of the adapter for every
 * kind but quick fixes, since the list hides them, and its request for each diagnostic under the caret, for quick fixes
 * only.
 */
class XdkCodeActionPatternTest {
    @TempDir lateinit var directory: Path

    /** The module compiles so far; the compiler queue runs them one at a time. */
    private val compiles = AtomicInteger()

    /** The kinds of LSP4IJ's intention list request, as the server asks the adapter for them. */
    private val intentions = CodeActionKind.entries.toSet() - CodeActionKind.QUICKFIX

    @Test
    fun `caret moves in clean code compile the project once`() {
        session { adapter, root ->
            val file = root.resolve("src/main/x/cardGame/Eights.x")
            val uri = file.toURI().toString()
            val text = file.readText()
            adapter.compile(uri, text)
            val lines = text.lines()
            val moves =
                listOf("Player current = one", "deck.shuffle", "Boolean isDone", "Inject Console", "discardPile.addCard").map {
                    counted { adapter.getCodeActionsAsync(uri, caret(lines, it), emptyList(), intentions) }
                }
            val again = counted { adapter.getCodeActionsAsync(uri, caret(lines, "discardPile.addCard"), emptyList(), intentions) }
            val quickFixes =
                counted { adapter.getCodeActionsAsync(uri, caret(lines, "deck.shuffle"), emptyList(), setOf(CodeActionKind.QUICKFIX)) }
            println("module compiles: caret moves $moves, the same request again $again, quick fixes on clean code $quickFixes")
            // the first move compiles each of the project's modules once; the rest share that compile
            assertThat(moves).containsExactly(MODULES, 0, 0, 0, 0)
            assertThat(again).isZero()
            assertThat(quickFixes).isZero()
        }
    }

    @Test
    fun `a caret on an error proves its quick fix once and the intention list proves nothing more`() {
        session { adapter, root ->
            val file = root.resolve("src/test/x/PlayerTest.x")
            val uri = file.toURI().toString()
            val broken =
                file.readText().replaceFirst(
                    "Player player = new Player(\"Alice\");",
                    "Joker joker = new Joker();\n        Player player = new Player(\"Alice\");",
                )
            val diagnostic = adapter.compile(uri, broken).diagnostics.first()
            val lines = broken.lines()
            val at = diagnostic.location
            val span = Range(Position(at.startLine, at.startColumn), Position(at.endLine, at.endColumn))
            val fix = counted { adapter.getCodeActionsAsync(uri, span, listOf(diagnostic), setOf(CodeActionKind.QUICKFIX)) }
            val onError = counted { adapter.getCodeActionsAsync(uri, caret(lines, "Joker joker", 2), emptyList(), intentions) }
            val atNew = counted { adapter.getCodeActionsAsync(uri, caret(lines, "new Joker", 6), emptyList(), intentions) }
            val next = caret(lines, "Player player", 2)
            val clean = counted { adapter.getCodeActionsAsync(uri, next, emptyList(), intentions) }
            // the quick fixes the list hides would prove there an import of the Player that the broken method leaves
            // unresolved, up to the module that is still broken
            val hidden = counted { adapter.getCodeActionsAsync(uri, next, emptyList(), setOf(CodeActionKind.QUICKFIX)) }
            // a client that lists quick fixes with the rest, as VS Code's lightbulb does, gets the one already proved
            val everyKind = counted { adapter.getCodeActionsAsync(uri, caret(lines, "new Joker", 6), emptyList()) }
            println(
                "module compiles: quick fixes for the error $fix, intention list on it $onError, at new Jo|ker $atNew, " +
                    "on the next statement $clean, quick fixes there $hidden, every kind at new Jo|ker $everyKind",
            )
            // the compile of the edited project, then the proof of Create class 'Joker', each over every module
            assertThat(fix).isEqualTo(MODULES + MODULES)
            assertThat(onError).isZero()
            assertThat(atNew).isZero()
            assertThat(clean).isZero()
            assertThat(hidden).isPositive()
            assertThat(everyKind).isZero()
        }
    }

    /** An empty selection [offset] characters into the first [word] of [lines]. */
    private fun caret(
        lines: List<String>,
        word: String,
        offset: Int = 1,
    ): Range {
        val line = lines.indexOfFirst { word in it }
        val column = lines[line].indexOf(word) + offset
        return Range(Position(line, column), Position(line, column))
    }

    /** The module compiles that [request] runs. */
    private fun counted(request: () -> CompletableFuture<List<CodeAction>>): Int {
        val start = compiles.get()
        request().get(HANG_SECONDS, SECONDS)
        return compiles.get() - start
    }

    private fun session(body: (XdkAdapter, File) -> Unit) {
        CompilerTestSupport.configure()
        val support = EmbeddingSupport.instance()
        val root = directory.toRealPath().toFile()
        File(System.getProperty("xtc.composite.root"), "lang/test-fixtures/tour/cardGame").copyRecursively(root)
        XdkAdapter(
            { source, repository, errors -> support.compileModule(source, repository, errors) },
            { sources, repository, errors ->
                compiles.incrementAndGet()
                support.compileModule(sources, repository, errors)
            },
            { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
        ).use { adapter ->
            adapter.initializeWorkspace(listOf(root.path))
            body(adapter, root)
        }
    }

    private companion object {
        /** The card game's modules: the game and its seven test modules. */
        const val MODULES = 8
    }
}
