package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.awaited
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * An embedded build reads its module's files, and its resources, which for a module that may name them include the
 * files of its source directory, before it compiles, and checks them again after: a file that the editor saves on disk
 * in between, as when a run's build starts just as the editor saves, ends that compile as cancelled. An editor's compile
 * runs again on the save's file event; nothing runs a build again, so the build compiles again from the files as they
 * are then, rather than end cancelled and leave its run unstarted.
 */
class EmbeddedBuildInputsTest {
    @Test
    fun `a build whose files change on disk while it compiles compiles them again`(
        @TempDir directory: Path,
    ) {
        CompilerTestSupport.configure()
        val workspace = directory.toRealPath()
        val sources = workspace.resolve("src/main/x")
        val app = sources.resolve("App.x").apply { parent.createDirectories() }.apply { writeText(APP) }
        val part = sources.resolve("App/Part.x").apply { parent.createDirectories() }.apply { writeText(PART) }
        val support = EmbeddingSupport.instance()
        val compiles = AtomicInteger()
        val adapter =
            XdkAdapter(
                { source, repository, errors -> support.compileModule(source, repository, errors) },
                { tree, repository, errors ->
                    // the editor saves Part.x once the build's first compile has read the module's files
                    if (compiles.incrementAndGet() == 1) part.writeText(SAVED)
                    support.compileModule(tree, repository, errors)
                },
                { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
            )
        adapter.use {
            it.initializeWorkspace(listOf(workspace.toString()))
            it.replaceSourceModules(listOf(XdkSourceModule("App", app.toUri().toString())))
            val build = it.buildAsync("App", rebuild = false).awaited()

            assertThat(build.cancelled).describedAs("the build ended cancelled").isFalse()
            assertThat(build.succeeded).describedAs(build.diagnostics.toString()).isTrue()
            assertThat(build.compiled[part.toFile().canonicalFile]).isEqualTo(SAVED)
            assertThat(compiles.get()).isEqualTo(2)
        }
    }

    private companion object {
        const val APP = "module App {\n    Int run() = Part.half(8);\n}\n"

        // a division, which the lexer cannot tell from a file literal's path, makes the source directory a resource
        const val PART = "class Part {\n    static Int half(Int n) = n / 2;\n}\n"
        const val SAVED = "class Part {\n    static Int half(Int n) = n / 2;\n\n    static Int twice(Int n) = n * 2;\n}\n"
    }
}
