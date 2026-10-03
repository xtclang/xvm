package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileMoveTargetsTest {
    @TempDir lateinit var root: Path

    @Test
    fun `destination validation refuses collisions missing parents and ambiguous batches without writes`() {
        val source = Files.createDirectories(root.resolve("source"))
        val a = Files.writeString(source.resolve("A.x"), "class A {}")
        val b = Files.writeString(source.resolve("B.x"), "class B {}")
        val target = Files.createDirectory(root.resolve("target"))
        val moved = target.resolve("A.x")
        assertThat(FileMoveTargets.valid(mapOf(a to moved, b to target.resolve("B.x")))).isTrue()
        listOf(
            mapOf(a to b),
            mapOf(a to moved, b to moved),
            mapOf(source to source.resolve("child")),
            mapOf(source to target.resolve("source"), a to moved),
            mapOf(a to root.resolve("missing/A.x")),
            mapOf(a to target.resolve("../target/A.x")),
        ).forEach { assertThat(FileMoveTargets.valid(it)).isFalse() }
        assertThat(Files.readString(a)).isEqualTo("class A {}")
        assertThat(moved).doesNotExist()
        Files.createSymbolicLink(moved, root.resolve("absent"))
        assertThat(FileMoveTargets.valid(mapOf(a to moved))).isFalse()
    }

    @Test
    fun `single source move accepts a new basename while batches preserve names`() {
        val source = Files.writeString(root.resolve("Old.x"), "class Old {}")
        val other = Files.writeString(root.resolve("Other.x"), "class Other {}")
        val destination = Files.createDirectory(root.resolve("target"))
        assertThat(FileMoveTargets.inDirectory(listOf(source), destination, "New.x"))
            .containsExactlyEntriesOf(mapOf(source to destination.resolve("New.x")))
        assertThat(FileMoveTargets.inDirectory(listOf(source, other), destination))
            .containsExactlyEntriesOf(mapOf(source to destination.resolve("Old.x"), other to destination.resolve("Other.x")))
        listOf("", " ", "../escape.x", "sub/File.x", "sub\\File.x", ".", "..").forEach { name ->
            assertThat(FileMoveTargets.inDirectory(listOf(source), destination, name)).isNull()
        }
        assertThat(FileMoveTargets.inDirectory(listOf(source, other), destination, "New.x")).isNull()
        assertThat(destination.resolve("New.x")).doesNotExist()
    }

    @Test
    fun `rechecking catches destination created while compiler proof is pending`() {
        val source = Files.writeString(root.resolve("A.x"), "class A {}")
        val target = root.resolve("B.x")
        val moves = mapOf(source to target)
        assertThat(FileMoveTargets.valid(moves)).isTrue()
        Files.writeString(target, "class B {}")
        assertThat(FileMoveTargets.valid(moves)).isFalse()
        assertThat(Files.readString(target)).isEqualTo("class B {}")
    }
}
