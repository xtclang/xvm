package org.xvm.lsp.adapter

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.testing.awaited
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A rename's proof starts from the revision's compile, which code actions share and prepareRename starts, and after the
 * edit compiles only the modules whose sources it changes and those that depend on them. Each case counts the module
 * compiles, by name, never their time, and checks that the rename is what it was: the edits, or a refusal.
 */
class XdkRenameReuseTest {
    @TempDir lateinit var directory: Path

    /** The modules compiled so far, by name, in the order the compiler worker compiled them. */
    private val compiled = CopyOnWriteArrayList<String>()

    /** Set to hold the next module compile until [release] counts down; [entered] counts down as it starts. */
    private val hold = AtomicBoolean()
    private val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)

    @Test
    fun `a rename after prepareRename compiles only the module it edits, and one in the same revision none before the edit`() {
        val root = chain(OTHER)
        session(root) { adapter ->
            val top = root.resolve("Top.x").uri()
            val base = root.resolve("Base.x").uri()
            val middle = root.resolve("Middle.x").uri()
            // the files a rename starts in, which an editor has open and analyzed, Top after what it depends on
            adapter.compile(base, BASE)
            adapter.compile(top, TOP)
            val start = compiled.size
            val (line, column) = TOP.at("three")
            assertThat(adapter.prepareRename(top, line, column)?.placeholder).isEqualTo("three")
            val edit = requireNotNull(adapter.renameAsync(top, line, column, "drei").awaited())
            assertThat(edit.changes.keys).containsExactly(top)
            assertThat(edit.changes.getValue(top)).hasSize(2).allMatch { it.newText == "drei" }
            // prepareRename's compile of every module, then the proof's of Top as renamed
            assertThat(compiled.drop(start)).containsExactly("Base", "Middle", "Other", "Top", "Top")

            // a rename of Base's function in the same revision: its compile is the cached one, and the proof compiles
            // Base and Middle, whose call it edits; Top, which depends on Middle but uses nothing the edit changes
            // (XdkMemberUseTracker), and Other, which none of them reaches, keep the revision's compile
            val next = compiled.size
            val (baseLine, baseColumn) = BASE.at("one")
            val renamed = requireNotNull(adapter.renameAsync(base, baseLine, baseColumn, "first").awaited())
            assertThat(renamed.changes.keys).containsExactlyInAnyOrder(base, middle)
            assertThat(compiled.drop(next)).containsExactly("Base", "Middle")
        }
    }

    @Test
    fun `an edit after prepareRename's compile makes the rename compile the edited revision's, from it`() {
        val root = chain(OTHER)
        session(root) { adapter ->
            val top = root.resolve("Top.x").uri()
            adapter.compile(top, TOP)
            val (line, column) = TOP.at("three")
            adapter.prepareRename(top, line, column)
            // a rename to the name it has, which waits for prepareRename's compile and compiles nothing after it
            assertThat(adapter.renameAsync(top, line, column, "three").awaited()?.changes).isEmpty()
            adapter.compile(top, CALLED_AGAIN)
            val start = compiled.size
            val edit = requireNotNull(adapter.renameAsync(top, line, column, "drei").awaited())
            // the call that the edit added is renamed too: the proof is of the edited sources, not of the compile before
            assertThat(edit.changes.getValue(top)).hasSize(3)
            // the edited revision's compile, which starts from the one before and compiles Top alone, then the proof's
            assertThat(compiled.drop(start)).containsExactly("Top", "Top")
            assertThat(revisionText(adapter, "Top")).isEqualTo(CALLED_AGAIN)
        }
    }

    @Test
    fun `an edit while prepareRename's compile runs retires it, and the rename proves the edited sources`() {
        val root = chain(OTHER)
        session(root) { adapter ->
            val top = root.resolve("Top.x").uri()
            adapter.compile(top, TOP)
            val (line, column) = TOP.at("three")
            hold.set(true)
            adapter.prepareRename(top, line, column)
            assertThat(entered.awaited()).isTrue()
            val edited = adapter.compileAsync(top, CALLED_AGAIN)
            release.countDown()
            // a rename comes once the edited file is analyzed again, as an editor asks it of the edited text
            assertThat(edited.awaited().success).isTrue()
            val start = compiled.size
            val edit = requireNotNull(adapter.renameAsync(top, line, column, "drei").awaited())
            assertThat(edit.changes.getValue(top)).hasSize(3)
            // nothing of the retired compile is kept: the rename compiles the edited revision's, every module, then
            // Top as renamed; had prepareRename's compile gone on, it would have compiled Top alone, from that one
            assertThat(compiled.drop(start)).containsExactly("Base", "Middle", "Other", "Top", "Top")
            assertThat(revisionText(adapter, "Top")).isEqualTo(CALLED_AGAIN)
        }
    }

    @Test
    fun `a revision's compile in which a module did not compile is not a rename's, which is refused as before`() {
        val root = chain(BROKEN_OTHER)
        val lines =
            logged {
                session(root) { adapter ->
                    val top = root.resolve("Top.x").uri()
                    adapter.compile(top, TOP)
                    val (line, column) = TOP.at("three")
                    val start = compiled.size
                    adapter.prepareRename(top, line, column)
                    assertThat(adapter.renameAsync(top, line, column, "drei").awaited()).isNull()
                    // prepareRename's compile of every module, Other failing, then the proof's own compile of the
                    // sources before the edit, which stops at Other, as it did before there was a revision's compile
                    assertThat(compiled.drop(start)).containsExactly("Base", "Middle", "Other", "Top", "Base", "Middle", "Other")
                }
            }
        assertThat(lines).contains(
            "rename: the revision's compile has no artifact of Other, so the proof compiles the sources before the edit itself",
        )
    }

    /** The text of [module]'s source in the revision's compile that the adapter keeps. */
    private fun revisionText(
        adapter: XdkAdapter,
        module: String,
    ): String? =
        adapter
            .codeActionBase()
            ?.inputs
            ?.get(module)
            ?.text
            ?.values
            ?.single()

    private fun <T> session(
        root: File,
        body: (XdkAdapter) -> T,
    ): T {
        CompilerTestSupport.configure()
        val support = EmbeddingSupport.instance()
        return XdkAdapter(
            { source, repository, errors -> support.compileModule(source, repository, errors) },
            { sources, repository, errors ->
                if (hold.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.awaited())
                }
                support.compileModule(sources, repository, errors).also { compiled += sources.qualifiedModuleName }
            },
            { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
        ).use { adapter ->
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule("Base", root.resolve("Base.x").uri()),
                    XdkSourceModule("Middle", root.resolve("Middle.x").uri(), setOf("Base")),
                    XdkSourceModule("Top", root.resolve("Top.x").uri(), setOf("Middle")),
                    XdkSourceModule("Other", root.resolve("Other.x").uri()),
                ),
            )
            body(adapter)
        }
    }

    /** Base, which Middle depends on, which Top depends on, and Other, with [other], which depends on none. */
    private fun chain(other: String): File =
        directory.toRealPath().toFile().also { root ->
            mapOf("Base" to BASE, "Middle" to MIDDLE, "Top" to TOP, "Other" to other).forEach { (name, text) ->
                root.resolve("$name.x").writeText(text)
            }
        }

    private fun File.uri(): String = toURI().toString()

    /** The line and column of [name]'s first occurrence. */
    private fun String.at(name: String): Pair<Int, Int> {
        val line = lines().indexOfFirst { name in it }
        return line to lines()[line].indexOf(name)
    }

    /** The lines that the server's loggers log at INFO while [body] runs. */
    private fun logged(body: () -> Unit): List<String> {
        val lines = CopyOnWriteArrayList<String>()
        val logger = LoggerFactory.getLogger("org.xvm.lsp") as Logger
        val appender =
            object : AppenderBase<ILoggingEvent>() {
                override fun append(event: ILoggingEvent) {
                    lines += event.formattedMessage
                }
            }.apply { start() }
        logger.addAppender(appender)
        try {
            body()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        return lines
    }

    private companion object {
        val BASE =
            """
            module Base {
                static Int one() = 1;
            }
            """.trimIndent() + "\n"

        val MIDDLE =
            """
            module Middle {
                package base import Base;

                static Int two() = base.one() + 1;
            }
            """.trimIndent() + "\n"

        val TOP =
            """
            module Top {
                package middle import Middle;

                Int three() = middle.two() + 1;

                Int six() = three() * 2;
            }
            """.trimIndent() + "\n"

        /** Top with a second call of three, which an edit adds. */
        val CALLED_AGAIN = TOP.replace("    Int six()", "    Int nine() = three() * 3;\n\n    Int six()")

        const val OTHER = "module Other { Int four() = 4; }\n"

        const val BROKEN_OTHER = "module Other { Int four() = nothing; }\n"
    }
}
