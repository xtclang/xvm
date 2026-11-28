package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.testing.awaited
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * A rename that the adapter can't prove while the workspace is as it is, which it refuses saying why, rather than with no
 * edit, as it refuses a rename whose proof finds a binding changed: the client then shows the cause, and may ask again
 * once it is gone.
 */
class XdkUnprovenRenameTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `a rename while discovery has a problem with the workspace's modules says what it is`() {
        val root = directory.toRealPath()
        val uri = root.source("First", "module First { package second import Second; Int run() { Int local = 1; return local; } }")
        root.source("Second", "module Second { package first import First; }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(root.toString()))
            val text = root.resolve("First.x").toFile().readText()
            adapter.compile(uri, text)
            assertUnproven(
                adapter.renameAsync(uri, 0, text.indexOf("local"), "value"),
                "This rename can't be proven while the workspace's source modules have a problem: Cyclic source dependencies",
            )
        }
    }

    @Test
    fun `a rename while its module does not compile says which, until it compiles again`() {
        val text = "module Broken { Int run() { Int local = 1; return local; } }"
        val uri = directory.toRealPath().source("Broken", text)
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(uri, text.replace("return local;", "return local")).success).isFalse()
            assertUnproven(
                adapter.renameAsync(uri, 0, text.indexOf("local"), "value"),
                "This rename can't be proven while Broken.x does not compile: fix the errors, then rename again",
            )
            assertThat(adapter.compile(uri, text).success).isTrue()
            assertThat(
                adapter
                    .renameAsync(uri, 0, text.indexOf("local"), "value")
                    .awaited()
                    ?.changes
                    ?.get(uri),
            ).hasSize(2)
        }
    }

    /** That [rename] fails with an unproven refusal whose reason starts with [reason]. */
    private fun assertUnproven(
        rename: CompletableFuture<*>,
        reason: String,
    ) {
        assertThatThrownBy { rename.awaited() }
            .rootCause()
            .isInstanceOfSatisfying(RenameRefused::class.java) { assertThat(it.unproven).isTrue() }
            .hasMessageStartingWith(reason)
    }

    /** Writes the module [name]'s root, [text], in this folder, and returns its URI. */
    private fun Path.source(
        name: String,
        text: String,
    ): String =
        resolve("$name.x")
            .toFile()
            .apply { writeText(text) }
            .toURI()
            .toString()
}
