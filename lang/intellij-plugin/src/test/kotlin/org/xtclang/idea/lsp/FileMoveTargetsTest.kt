package org.xtclang.idea.lsp

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

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
            )
            .forEach { assertThat(FileMoveTargets.valid(it)).isFalse() }
        assertThat(Files.readString(a)).isEqualTo("class A {}")
        assertThat(moved).doesNotExist()
        Files.createSymbolicLink(moved, root.resolve("absent"))
        assertThat(FileMoveTargets.valid(mapOf(a to moved))).isFalse()
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
