package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class CompilerBuildUpdatesTest {
    @Test
    fun `atomic report replacement and removal affect inputs but unrelated paths do not`() {
        val root = Path.of("/workspace/app")
        listOf(".gradle/xtc/lsp-model.json", ".gradle/xtc", ".gradle").forEach {
            assertThat(CompilerBuildUpdates.affectsModel(root, root.resolve(it))).isTrue()
        }
        listOf("build.gradle.kts", ".gradle/xtc/temporary.json", "../other/.gradle/xtc/lsp-model.json").forEach {
            assertThat(CompilerBuildUpdates.affectsModel(root, root.resolve(it))).isFalse()
        }
    }
}
