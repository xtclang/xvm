package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ConstantPool
import org.xvm.asm.FileStructure
import org.xvm.lsp.HANG_SECONDS
import org.xvm.lsp.adapter.CodeAction.CodeActionKind
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkCodeActionCache
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.TimeUnit.SECONDS
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

    /** What a revision's compile gives each module, by its name: its errors, whether it has facts, its artifact. */
    private data class Outcome(
        val errors: Map<String, List<String>>,
        val facts: Set<String>,
        val artifacts: Map<String, String>,
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
    fun `an edit of the root module compiles every module that depends on it`() {
        val root = cardGame()
        val card = root.resolve("src/main/x/cardGame/Card.x")
        assertThat(incremental(root, null, mapOf(card to "// edited\n" + card.readText()))).isEqualTo(CARD_GAME)
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
            ).get(HANG_SECONDS, SECONDS)
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
    }
}
