package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkResources
import org.xvm.tool.ModuleInfo
import org.xvm.tool.ResourceDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks a module's resource roots when its container moves away while the compiler deduces them, as when a
 * move is undone and redone at once: the server then keeps the container as a missing input, as it does for one
 * already gone, rather than reject the source configuration.
 */
class XdkResourcesTest {
    @TempDir lateinit var directory: Path

    private val container by lazy { Files.createDirectories(directory.resolve("second")).toFile() }
    private val module by lazy { File(container, "Library.x").apply { writeText("module Library {}\n") } }

    @Test
    fun `a container that moves away before the compiler finds the module is a missing input`() {
        val roots =
            XdkResources.roots(module, null) {
                check(container.deleteRecursively())
                ResourceDir.forSource(it, true).locations
            }
        assertThat(roots).containsExactly(container.canonicalFile)
    }

    @Test
    fun `a container that moves away before the compiler checks its source directory is a missing input`() {
        // ResourceDir.forSource's two steps, with the move between them
        val roots =
            XdkResources.roots(module, null) {
                val source = ModuleInfo(it, true).sourceDir
                check(container.deleteRecursively())
                ResourceDir(listOf(source)).locations
            }
        assertThat(roots).containsExactly(container.canonicalFile)
    }

    @Test
    fun `the compiler's refusal of a container that is still there is not taken for a move`() {
        assertThatThrownBy { XdkResources.roots(module, null) { throw IllegalArgumentException("refused") } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("refused")
    }
}
