package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ConstantPool
import org.xvm.asm.FileStructure
import org.xvm.lsp.adapter.CodeAction.CodeActionKind
import org.xvm.lsp.adapter.xdk.CompilerRenameFacts
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkCodeActionCache
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkProject
import org.xvm.lsp.adapter.xdk.XdkProjectQueries
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.awaited
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.atomic.AtomicInteger

/**
 * A revision's compile for code actions starts from the previous revision's: it compiles only the modules whose sources
 * changed and those that depend on them, and keeps the rest. Each case compares it, after one edit, with the compile of
 * the same sources from nothing: the same errors, the same modules compiled, the same artifacts, with the module compiles
 * counted, never timed.
 */
class XdkIncrementalBaseTest {
    @TempDir lateinit var directory: Path

    /** The module compiles so far; the compiler queue runs them one at a time. */
    private val compiles = AtomicInteger()

    /**
     * What a revision's compile gives each module, by its name: its errors, whether it has facts, its artifact, and the
     * modules that its facts name by artifact revision.
     */
    private data class Outcome(
        val errors: Map<String, List<String>>,
        val facts: Set<String>,
        val artifacts: Map<String, String>,
        val named: Map<String, Set<String>>,
    )

    @Test
    fun `an edit of a module that no module depends on compiles it alone`() {
        val root = cardGame()
        val player = root.resolve("src/test/x/PlayerTest.x")
        val edited =
            player.readText().replace(
                "    @Test\n    void testPlayerCanBeCreated",
                "    @Test\n    // edited\n    void testPlayerCanBeCreated",
            )
        assertThat(incremental(root, null, mapOf(player to edited))).isEqualTo(1)
    }

    @Test
    fun `an edit of the root module that moves its declarations compiles every module that depends on it`() {
        val root = cardGame()
        val card = root.resolve("src/main/x/cardGame/Card.x")
        // the modules that depend on it name its declarations where they are
        assertThat(incremental(root, null, mapOf(card to "// edited\n" + card.readText()))).isEqualTo(CARD_GAME)
    }

    @Test
    fun `a body edit of the root module after all but its private declarations compiles it alone`() {
        val root = cardGame()
        val hand = root.resolve("src/main/x/cardGame/Hand.x")
        // the tour's unused local, in display's body, which moves only heading, private, declared after it: the modules
        // that depend on it compile as they did
        val edited = hand.readText().replaceFirst("        console.print();\n", "        Int unused = 0;\n        console.print();\n")
        assertThat(incremental(root, null, mapOf(hand to edited))).isEqualTo(1)
    }

    @Test
    fun `an edit that moves only private declarations compiles the module alone, as the modules that compiled cannot name them`() {
        val root = counter(USE_COUNTER)
        val lib = root.resolve("Lib.x")
        assertThat(incremental(root, ::libraryAndUse, mapOf(lib to COUNTER.replace(NEXT_BODY, "$UNUSED$NEXT_BODY")))).isEqualTo(1)
    }

    @Test
    fun `an edit that moves only private declarations compiles a module that did not compile, as its facts name them`() {
        // Use's compile fails at the call of step, which is not accessible: its facts are what the compile got that
        // far, and they name Counter's members where they are, step and PROMPT too
        val root = counter(USE_STEP)
        val lib = root.resolve("Lib.x")
        assertThat(incremental(root, ::libraryAndUse, mapOf(lib to COUNTER.replace(NEXT_BODY, "$UNUSED$NEXT_BODY")))).isEqualTo(2)
    }

    @Test
    fun `a body edit of the root module before a declaration compiles every module that depends on it`() {
        val root = cardGame()
        val hand = root.resolve("src/main/x/cardGame/Hand.x")
        // the constructor's body grows by a line, which moves display, declared after it
        val edited =
            hand.readText().replaceFirst(
                "        construct CardCollection(label);\n",
                "        construct CardCollection(label);\n\n",
            )
        assertThat(incremental(root, null, mapOf(hand to edited))).isEqualTo(CARD_GAME)
    }

    @Test
    fun `a signature edit of the root module compiles every module that depends on it`() {
        val root = cardGame()
        val hand = root.resolve("src/main/x/cardGame/Hand.x")
        val edited = hand.readText().replaceFirst("    void display() {", "    void display(Int indent = 0) {")
        assertThat(incremental(root, null, mapOf(hand to edited))).isEqualTo(CARD_GAME)
    }

    @Test
    fun `an edit of a constant's value in the root module compiles every module that depends on it`() {
        val root = cardGame()
        val constants = root.resolve("src/main/x/cardGame/GameConstants.x")
        val edited = constants.readText().replaceFirst("static Int INITIAL_HAND_SIZE = 5;", "static Int INITIAL_HAND_SIZE = 7;")
        assertThat(incremental(root, null, mapOf(constants to edited))).isEqualTo(CARD_GAME)
    }

    @Test
    fun `an edit of a computed constant's expression in the root module compiles it alone`() {
        val root = cardGame()
        val constants = root.resolve("src/main/x/cardGame/GameConstants.x")
        val edited =
            constants.readText().replaceFirst(
                "static Int DECK_SIZE = Rank.values.size * Suit.values.size;",
                "static Int DECK_SIZE = Suit.values.size * Rank.values.size;",
            )
        // its value comes from its initializer's code, which the modules that use it do not compile against
        assertThat(incremental(root, null, mapOf(constants to edited))).isEqualTo(1)
    }

    @Test
    fun `a module whose facts name an edited one by revision is compiled, though the edit kept the API`() {
        val root =
            directory.toRealPath().toFile().also { root ->
                root.resolve("Lib.x").writeText(LIB)
                root.resolve("Use.x").writeText(USE)
            }
        val graph = XdkProject(libraryAndUse(root))
        val lib = graph.modules.getValue("Lib").uri
        val use = graph.modules.getValue("Use").uri
        val edited = mapOf(root.resolve("Lib.x").toURI().toString() to LIB.replace("return 1;", "return 2;"))
        val cache = XdkCodeActionCache()
        revision(graph, emptyMap(), cache)
        // A member with no declaration in a project module is named by its artifact's revision, which changes with any
        // edit. Every member that Use could name in Lib has a declaration, as on the card game, so the case adds that
        // name of Lib to Use's facts from the revision before.
        val before = cache.base()!!
        val named =
            CompilerRenameFacts.merge(
                mapOf(
                    "compiled" to before.facts.module(use)!!,
                    "named" to CompilerRenameFacts(emptyList(), emptyMap(), binaryModules = setOf("Lib")),
                ),
            )
        val seeded = before.copy(facts = CompilerRenameFacts.merge(mapOf(lib to before.facts.module(lib)!!, use to named)))
        cache.base("seeded") { seeded }
        // Lib's edit, in one's body, keeps its API, but Use is compiled, as its facts name Lib by revision
        assertThat(revision(graph, edited, cache)).isEqualTo(2)
        val whole = XdkCodeActionCache().also { revision(graph, edited, it) }
        assertThat(outcome(cache.base()!!)).isEqualTo(outcome(whole.base()!!))
    }

    @Test
    fun `an edit of a middle module compiles it and the modules that depend on it`() {
        val root = chain()
        assertThat(incremental(root, ::modules, mapOf(root.resolve("Middle.x") to "// edited\n$MIDDLE"))).isEqualTo(2)
    }

    @Test
    fun `an edit that fixes a module compiles it and the modules that depend on it, which failed before`() {
        val root = chain()
        val middle = root.resolve("Middle.x")
        // Middle did not compile, so Top, which depends on it, was not compiled either
        assertThat(incremental(root, ::modules, mapOf(middle to MIDDLE), before = mapOf(middle to BROKEN_MIDDLE))).isEqualTo(2)
    }

    @Test
    fun `an edit that breaks a module compiles it, and the modules that depend on it are not compiled`() {
        val root = chain()
        assertThat(incremental(root, ::modules, mapOf(root.resolve("Middle.x") to BROKEN_MIDDLE))).isEqualTo(1)
    }

    @Test
    fun `an edit downstream of a module that did not compile compiles every module`() {
        val root = chain()
        val base = root.resolve("Base.x")
        // from the previous compile, only Top would be compiled, which cannot be without Base: none at all
        val compiled = incremental(root, ::modules, mapOf(root.resolve("Top.x") to "// edited\n$TOP"), before = mapOf(base to BROKEN_BASE))
        // Base, which fails, and Other; Middle and Top wait for Base
        assertThat(compiled).isEqualTo(2)
    }

    /**
     * The module compiles of the revision's compile after [edit], which [incremental] compares with the compile of the
     * same sources from nothing; [before] is the text that the previous revision had in place of the files' own.
     */
    private fun incremental(
        root: File,
        modules: ((File) -> List<XdkSourceModule>)?,
        edit: Map<File, String>,
        before: Map<File, String> = emptyMap(),
    ): Int {
        val (step, compiled) =
            session(root, modules) { adapter ->
                before.forEach { (file, text) -> adapter.compile(file.toURI().toString(), text) }
                compiled(adapter, root)
                edit.forEach { (file, text) -> adapter.compile(file.toURI().toString(), text) }
                val count = compiled(adapter, root)
                outcome(adapter.codeActionBase()!!) to count
            }
        val (whole, _) =
            session(root, modules) { adapter ->
                (before + edit).forEach { (file, text) -> adapter.compile(file.toURI().toString(), text) }
                val count = compiled(adapter, root)
                outcome(adapter.codeActionBase()!!) to count
            }
        println("module compiles: from the previous compile $compiled; outcome $step")
        assertThat(step).isEqualTo(whole)
        return compiled
    }

    /**
     * The module compiles of the revision's compile of [graph], with [overlays] in place of its files, as a code action
     * request makes it, from the compile that [cache] keeps of the revision before.
     */
    private fun revision(
        graph: XdkProject,
        overlays: Map<String, String>,
        cache: XdkCodeActionCache,
    ): Int {
        CompilerTestSupport.configure()
        val support = EmbeddingSupport.instance()
        val start = compiles.get()
        XdkProjectQueries(
            graph,
            overlays,
            XdkDependencies(emptyList()),
            { sources, repository, errors ->
                support.compileModule(sources, repository, errors).also { compiles.incrementAndGet() }
            },
            { false },
            actionCache = cache,
        ).codeActions(
            graph.buildOrder().first().uri,
            Range(Position(0, 0), Position(0, 0)),
            emptyList(),
            setOf(CodeActionKind.REFACTOR_INLINE),
        )
        return compiles.get() - start
    }

    /** The module compiles of a code action request that asks only for the revision's compile. */
    private fun compiled(
        adapter: XdkAdapter,
        root: File,
    ): Int {
        val any =
            root
                .walkTopDown()
                .first { it.isFile && it.extension == "x" }
                .toURI()
                .toString()
        val start = compiles.get()
        adapter
            .getCodeActionsAsync(
                any,
                Range(Position(0, 0), Position(0, 0)),
                emptyList(),
                setOf(CodeActionKind.REFACTOR_INLINE),
            ).awaited()
        return compiles.get() - start
    }

    private fun outcome(base: XdkCodeActionCache.Base): Outcome {
        val modules = base.project!!.modules.values
        return Outcome(
            base.errors,
            modules
                .filter {
                    base.facts.module(it.uri) != null
                }.mapTo(sortedSetOf()) { it.name },
            base.artifacts.mapValues { fingerprint(it.value) },
            modules.associate { module ->
                module.name to
                    base.facts
                        .module(module.uri)
                        ?.binaryModules
                        .orEmpty()
            },
        )
    }

    /** An artifact's bytes without what each compile gives anew, its time and the directory it was compiled in. */
    private fun fingerprint(artifact: XdkDependency): String {
        val file = FileStructure(artifact.bytes().inputStream())
        return ConstantPool.withPool(file.constantPool).use {
            file.moduleIds().forEach { id ->
                file.getModule(id).apply {
                    timestamp = null
                    sourceDir = null
                }
            }
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ByteArrayOutputStream().also(file::writeTo).toByteArray()))
        }
    }

    private fun <T> session(
        root: File,
        modules: ((File) -> List<XdkSourceModule>)?,
        body: (XdkAdapter) -> T,
    ): T {
        CompilerTestSupport.configure()
        val support = EmbeddingSupport.instance()
        return XdkAdapter(
            { source, repository, errors -> support.compileModule(source, repository, errors) },
            { sources, repository, errors ->
                support.compileModule(sources, repository, errors).also { compiles.incrementAndGet() }
            },
            { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
        ).use { adapter ->
            if (modules == null) adapter.initializeWorkspace(listOf(root.path)) else adapter.replaceSourceModules(modules(root))
            body(adapter)
        }
    }

    /** The tour's card game, its game module and the seven test modules that depend on it. */
    private fun cardGame(): File =
        directory.toRealPath().toFile().also {
            File(System.getProperty("xtc.composite.root"), "lang/test-fixtures/tour/cardGame").copyRecursively(it)
        }

    /** Base, which Middle depends on, which Top depends on, and Other, which depends on none. */
    private fun chain(): File =
        directory.toRealPath().toFile().also { root ->
            mapOf("Base" to BASE, "Middle" to MIDDLE, "Top" to TOP, "Other" to OTHER).forEach { (name, text) ->
                root.resolve("$name.x").writeText(text)
            }
        }

    /** Lib, with [COUNTER], and Use, with [use], which depends on it. */
    private fun counter(use: String): File =
        directory.toRealPath().toFile().also { root ->
            root.resolve("Lib.x").writeText(COUNTER)
            root.resolve("Use.x").writeText(use)
        }

    /** Lib, and Use, which depends on it. */
    private fun libraryAndUse(root: File): List<XdkSourceModule> =
        listOf(
            XdkSourceModule("Lib", root.resolve("Lib.x").toURI().toString()),
            XdkSourceModule("Use", root.resolve("Use.x").toURI().toString(), setOf("Lib")),
        )

    private fun modules(root: File): List<XdkSourceModule> =
        listOf(
            XdkSourceModule("Base", root.resolve("Base.x").toURI().toString()),
            XdkSourceModule("Middle", root.resolve("Middle.x").toURI().toString(), setOf("Base")),
            XdkSourceModule("Top", root.resolve("Top.x").toURI().toString(), setOf("Middle")),
            XdkSourceModule("Other", root.resolve("Other.x").toURI().toString()),
        )

    private companion object {
        /** The card game's modules. */
        const val CARD_GAME = 8

        const val BASE = "module Base { static Int one() = 1; }"
        const val BROKEN_BASE = "module Base { static Int one() = nothing; }"
        const val MIDDLE = "module Middle { package base import Base; static Int two() = base.one() + 1; }"
        const val BROKEN_MIDDLE = "module Middle { package base import Base; static Int two() = base.none(); }"
        const val TOP = "module Top { package middle import Middle; Int three() = middle.two() + 1; }"
        const val OTHER = "module Other { Int four() = 4; }"

        /** A function, whose body Lib's edit changes. */
        val LIB =
            """
            module Lib {
                static Int one() {
                    return 1;
                }
            }
            """.trimIndent() + "\n"

        /** A use of Lib's function. */
        val USE =
            """
            module Use {
                package lib import Lib;

                Int two() = lib.one() + 1;
            }
            """.trimIndent() + "\n"

        /** The body of Counter's public method, which an edit grows by [UNUSED], moving the private members after it. */
        const val NEXT_BODY = "            return step();\n"

        const val UNUSED = "            Int unused = 0;\n"

        /** A class with a public method, and after it a private method and a private constant, as the card game's Hand. */
        val COUNTER =
            """
            module Lib {
                class Counter {
                    Int next() {
                        return step();
                    }

                    private Int step() = 1;

                    private static String PROMPT = "Next";
                }
            }
            """.trimIndent() + "\n"

        /** A use of Counter's public method. */
        val USE_COUNTER =
            """
            module Use {
                package lib import Lib;

                Int two() = new lib.Counter().next() + 1;
            }
            """.trimIndent() + "\n"

        /** A call of Counter's private method, which does not compile. */
        val USE_STEP =
            """
            module Use {
                package lib import Lib;

                Int two() = new lib.Counter().step() + 1;
            }
            """.trimIndent() + "\n"
    }
}
