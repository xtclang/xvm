package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkProject
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Path

class XdkProjectGraphTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `affected modules include transitive diamond consumers once in dependency order`() {
        fun module(
            name: String,
            vararg dependencies: String,
        ) = XdkSourceModule(name, directory.resolve("$name.x").toUri().toString(), dependencies.toSet())

        val base = module("Base")
        val left = module("Left", "Base")
        val right = module("Right", "Base")
        val leaf = module("Leaf", "Left", "Right")
        val unrelated = module("Unrelated")
        val project = XdkProject(listOf(unrelated, leaf, right, left, base))

        assertThat(project.affected(base.uri)).containsExactly(base.uri, left.uri, right.uri, leaf.uri)
        assertThat(project.affected(left.uri)).containsExactly(left.uri, leaf.uri)
        assertThat(project.affected(leaf.uri)).containsExactly(leaf.uri)
        assertThat(project.affected(unrelated.uri)).containsExactly(unrelated.uri)
        assertThat(project.affected("untitled:Standalone.x")).containsExactly("untitled:Standalone.x")
    }
}
