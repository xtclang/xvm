package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.CodeAction.CodeActionKind
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.awaited
import org.xvm.lsp.model.Diagnostic
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
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
            // unresolved: a compile of PlayerTest, which it leaves broken, and on which no module depends
            val hidden = counted { adapter.getCodeActionsAsync(uri, next, emptyList(), setOf(CodeActionKind.QUICKFIX)) }
            // a client that lists quick fixes with the rest, as VS Code's lightbulb does, gets the one already proved
            val everyKind = counted { adapter.getCodeActionsAsync(uri, caret(lines, "new Joker", 6), emptyList()) }
            println(
                "module compiles: quick fixes for the error $fix, intention list on it $onError, at new Jo|ker $atNew, " +
                    "on the next statement $clean, quick fixes there $hidden, every kind at new Jo|ker $everyKind",
            )
            // the compile of the edited project, every module, then the proof of Create class 'Joker', PlayerTest alone
            assertThat(fix).isEqualTo(MODULES + 1)
            assertThat(onError).isZero()
            assertThat(atNew).isZero()
            assertThat(clean).isZero()
            assertThat(hidden).isEqualTo(1)
            assertThat(everyKind).isZero()
        }
    }

    @Test
    fun `after Optimize Imports, the intention list's request compiles PlayerTest and proves each import`() {
        session { adapter, root ->
            // the requests before the edit compile the revision before it
            val player = root.resolve("src/test/x/PlayerTest.x")
            val first =
                counted {
                    adapter.getCodeActionsAsync(
                        player.toURI().toString(),
                        Range(Position(0, 0), Position(0, 0)),
                        emptyList(),
                        intentions,
                    )
                }
            val (uri, lines) = organized(adapter, root)
            val full = counted { adapter.getCodeActionsAsync(uri, caret(lines, "GameConstants", 4), emptyList(), intentions) }
            println("module compiles: the intention list before Optimize Imports $first, after it $full")
            // the edited revision's compile, PlayerTest alone, on which no module depends, as it starts from the compile of
            // the revision before, then the proof that each of the five imports is used, PlayerTest alone again: tour run 7
            // waited 13.9 s on the EDT for 43, every proof and the revision's compile compiling 7 or 8 modules
            assertThat(full).isEqualTo(1 + IMPORTS)
        }
    }

    @Test
    fun `the first request of a session compiles every module, with nothing to start from`() {
        session { adapter, root ->
            val (uri, lines) = organized(adapter, root)
            val full = counted { adapter.getCodeActionsAsync(uri, caret(lines, "GameConstants", 4), emptyList(), intentions) }
            println("module compiles: the intention list after Optimize Imports, first of the session $full")
            assertThat(full).isEqualTo(MODULES + IMPORTS)
        }
    }

    @Test
    fun `the tour's unused import in PlayerTest is offered its removal, PlayerTest compiled for the revision and each proof`() {
        session { adapter, root ->
            val (listed, fixes) =
                tourEdit(
                    adapter,
                    root.resolve("src/test/x/PlayerTest.x"),
                    "    import cardGame.Card;\n",
                    "    import cardGame.Card;\n    import cardGame.Deck;\n",
                ) { caret(it, "import cardGame.Deck", "import cardGame.".length) }
            println("unused import: intention list $listed, quick fixes $fixes")
            assertThat(listed.first).contains("Remove unused import 'Deck'")
            // PlayerTest for the revision, as it starts from the revision before, then for the cleanup's proofs: the
            // removal of each of its seven imports, and Organize imports; tour run 8b, with no compile to start from,
            // took 16 and 4,989 ms, and IntelliJ's Alt+Enter waited for it
            assertThat(listed.second).isEqualTo(1 + 7 + 1)
            // the removal is no quick fix, so LSP4IJ's request for the diagnostic under the caret never has it
            assertThat(fixes).isEqualTo(emptyList<String>() to 0)
        }
    }

    @Test
    fun `the tour's unused local in Hand is offered its removal, every module compiled for the revision and its proof`() {
        session { adapter, root ->
            val (listed, fixes) =
                tourEdit(
                    adapter,
                    root.resolve("src/main/x/cardGame/Hand.x"),
                    "        console.print();\n",
                    "        Int unused = 0;\n        console.print();\n",
                ) { caret(it, "Int unused", "Int un".length) }
            println("unused local: intention list $listed, quick fixes $fixes")
            assertThat(listed.first).contains("Remove unused local variable")
            // Hand is in cardGame, on which every module depends: the revision's compile and the removal's proof each
            // compile them all, as tour run 8b's did, 16 in 4,481 ms
            assertThat(listed.second).isEqualTo(MODULES + MODULES)
            assertThat(fixes).isEqualTo(emptyList<String>() to 0)
        }
    }

    /**
     * The tour's edit of [file], its first [old] replaced by [new], analyzed as its didChange has it, after a request of
     * the revision before it: what LSP4IJ's two requests at the [caret] then answer, the intention list's and the quick
     * fixes' for the diagnostics under it, each as its actions' titles and the module compiles it ran.
     */
    private fun tourEdit(
        adapter: XdkAdapter,
        file: File,
        old: String,
        new: String,
        caret: (List<String>) -> Range,
    ): Pair<Pair<List<String>, Int>, Pair<List<String>, Int>> {
        val uri = file.toURI().toString()
        counted { adapter.getCodeActionsAsync(uri, Range(Position(0, 0), Position(0, 0)), emptyList(), intentions) }
        val text = file.readText().replaceFirst(old, new)
        val diagnostics = adapter.compile(uri, text).diagnostics
        val at = caret(text.lines())
        val listed = answered { adapter.getCodeActionsAsync(uri, at, emptyList(), intentions) }
        val fixes = answered { adapter.getCodeActionsAsync(uri, at, diagnostics.under(at), setOf(CodeActionKind.QUICKFIX)) }
        return listed to fixes
    }

    /** The diagnostics whose range holds [range]'s start, as LSP4IJ asks for each diagnostic under the caret. */
    private fun List<Diagnostic>.under(range: Range): List<Diagnostic> {
        fun offset(
            line: Int,
            column: Int,
        ) = (line.toLong() shl 32) + column
        val caret = offset(range.start.line, range.start.column)
        return filter {
            val at = it.location
            offset(at.startLine, at.startColumn) <= caret && caret <= offset(at.endLine, at.endColumn)
        }
    }

    /** The titles of what [request] answers, and the module compiles it runs. */
    private fun answered(request: () -> CompletableFuture<List<CodeAction>>): Pair<List<String>, Int> {
        val start = compiles.get()
        val titles = request().awaited().map { it.title }
        return titles to compiles.get() - start
    }

    /** PlayerTest.x of [root] after Optimize Imports, as tour run 7 left it, analyzed as its didChange has it. */
    private fun organized(
        adapter: XdkAdapter,
        root: File,
    ) = edited(adapter, root, listOf("Card", "GameConstants", "Player", "Rank", "Suit"))

    /** PlayerTest.x of [root] with [imports] in place of its own, analyzed as a didChange has it: its URI and lines. */
    private fun edited(
        adapter: XdkAdapter,
        root: File,
        imports: List<String>,
    ): Pair<String, List<String>> {
        val file = root.resolve("src/test/x/PlayerTest.x")
        val uri = file.toURI().toString()
        val text =
            file.readText().replace(
                listOf("Card", "Player", "Hand", "Suit", "Rank", "GameConstants").joinToString("") { "    import cardGame.$it;\n" },
                imports.joinToString("") { "    import cardGame.$it;\n" },
            )
        assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
        return uri to text.lines()
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
        request().awaited()
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

        /** PlayerTest's imports after Optimize Imports. */
        const val IMPORTS = 5
    }
}
