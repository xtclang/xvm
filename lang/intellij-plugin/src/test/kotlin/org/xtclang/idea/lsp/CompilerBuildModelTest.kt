package org.xtclang.idea.lsp

import com.google.gson.Gson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CompilerBuildModelTest {
    private val entry =
        mapOf(
            "projectId" to "file:///workspace/#:library",
            "projectPath" to ":library",
            "projectDirectory" to "file:///workspace/library/",
            "buildFile" to "file:///workspace/library/build.gradle.kts",
            "sourceSet" to "main",
            "sourceRoots" to listOf("file:///outside/custom-source/"),
            "sourceFiles" to listOf("file:///outside/custom-source/Library.x"),
            "moduleRoots" to listOf("file:///outside/custom-source/Library.x"),
            "resourceSourceRoots" to listOf("file:///outside/assets/"),
            "resourceRoots" to listOf("file:///workspace/build/processed/", "file:///fallback/"),
            "resourceTask" to ":library:processXtcResources",
            "projectDependencies" to emptyList<String>(),
            "modulePath" to listOf("file:///outside/Library.xtc"),
        )

    private fun text(
        entries: List<Map<String, Any>> = listOf(entry),
        version: Int = 1,
    ) = Gson().toJson(mapOf("schemaVersion" to version, "sourceSets" to entries))

    @Test
    fun `evaluated model preserves external paths resource precedence and main test ownership`() {
        val model = CompilerBuildModel.parse(text(listOf(entry, entry + ("sourceSet" to "test"))))
        assertThat(model["sourceSets"].asJsonArray).hasSize(2)
        assertThat(
            model["sourceSets"].asJsonArray[0].asJsonObject["resourceRoots"].asJsonArray.map {
                it.asString
            },
        ).containsExactly("file:///workspace/build/processed/", "file:///fallback/")
    }

    @Test
    fun `unsupported malformed and duplicate models are refused before configuration changes`() {
        listOf(
            text(version = 2),
            text(listOf(entry, entry)),
            text(listOf(entry + ("sourceRoots" to listOf("relative/path")))),
            text(listOf(entry + ("projectId" to 42))),
            text(listOf(entry - "resourceRoots")),
            text(listOf(entry + ("projectDependencies" to listOf(1)))),
        ).forEach { value ->
            assertThatThrownBy { CompilerBuildModel.parse(value) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
