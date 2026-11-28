package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkProject
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks that dependency watch roots include unopened source modules and missing resource directories.
 * Ensures later file creation can invalidate the same graph that compilation uses.
 */
class XdkProjectGraphTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `watch roots include unopened sources and missing resources`() {
        val source = directory.resolve("external/sources/Library.x")
        val assets = directory.resolve("generated/assets")
        val project =
            XdkProject(
                listOf(
                    XdkSourceModule(
                        "Library",
                        source.toUri().toString(),
                        resourceRoots = listOf(assets.toUri().toString()),
                    ),
                ),
            )
        assertThat(project.inputWatchRoots())
            .containsExactlyInAnyOrder(
                source.parent.toFile().canonicalFile,
                assets.toFile().canonicalFile,
            )
    }

    @Test
    fun `resource membership survives missing source containers and generated ancestors`() {
        val container = Files.createDirectory(directory.resolve("source"))
        val root = container.resolve("App.x")
        val assets = directory.resolve("generated/assets")
        val implicit = XdkProject(listOf(XdkSourceModule("App", root.toUri().toString())))
        Files.delete(container)
        assertThat(implicit.inputWatchRoots()).contains(container.toFile().canonicalFile)
        assertThat(implicit.resourceScopes(container.toUri().toString()))
            .contains(
                root
                    .toFile()
                    .canonicalFile
                    .toURI()
                    .toString(),
            )
        val explicit =
            XdkProject(
                listOf(
                    XdkSourceModule(
                        "App",
                        root.toUri().toString(),
                        resourceRoots = listOf(assets.toUri().toString()),
                    ),
                ),
            )
        assertThat(explicit.resourceScopes(assets.parent.toUri().toString()))
            .containsExactly(
                root
                    .toFile()
                    .canonicalFile
                    .toURI()
                    .toString(),
            )
        assertThat(explicit.resourceScopes(directory.resolve("unrelated").toUri().toString()))
            .isEmpty()
    }

    @Test
    fun `affected modules include transitive diamond consumers once in dependency order`() {
        fun module(
            name: String,
            vararg dependencies: String,
        ) = XdkSourceModule(
            name,
            directory.resolve("$name.x").toUri().toString(),
            dependencies.toSet(),
        )

        val base = module("Base")
        val left = module("Left", "Base")
        val right = module("Right", "Base")
        val leaf = module("Leaf", "Left", "Right")
        val unrelated = module("Unrelated")
        val project = XdkProject(listOf(unrelated, leaf, right, left, base))

        assertThat(project.affected(base.uri))
            .containsExactly(base.uri, left.uri, right.uri, leaf.uri)
        assertThat(project.affected(left.uri)).containsExactly(left.uri, leaf.uri)
        assertThat(project.affected(leaf.uri)).containsExactly(leaf.uri)
        assertThat(project.affected(unrelated.uri)).containsExactly(unrelated.uri)
        assertThat(project.affected("untitled:Standalone.x"))
            .containsExactly("untitled:Standalone.x")
    }

    @Test
    fun `the build order is the graph's whatever the order of the modules it is made from`() {
        fun module(
            name: String,
            vararg dependencies: String,
        ) = XdkSourceModule(
            name,
            directory.resolve("$name.x").toUri().toString(),
            dependencies.toSet(),
        )

        fun <T> permutations(items: List<T>): List<List<T>> =
            if (items.size <= 1) {
                listOf(items)
            } else {
                items.flatMap { item -> permutations(items - item).map { listOf(item) + it } }
            }

        val modules =
            listOf(module("Base"), module("Left", "Base"), module("Right", "Base"), module("Leaf", "Right", "Left"), module("Unrelated"))
        // each module after its dependencies, visiting the modules, and each one's dependencies, by name
        val expected = listOf("Base", "Left", "Right", "Leaf", "Unrelated")
        permutations(modules).forEach { order ->
            val project = XdkProject(order)
            assertThat(project.buildOrder().map { it.name }).describedAs("from %s", order.map { it.name }).isEqualTo(expected)
            assertThat(project.modules.keys).describedAs("from %s", order.map { it.name }).containsExactlyElementsOf(expected)
            // a project made from another's modules, as the adapter makes the one it installs
            assertThat(XdkProject(project.modules.values.toList()).buildOrder()).isEqualTo(project.buildOrder())
        }
    }
}
