package org.xtclang.idea.lsp

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Aggregate evaluated reports, never source directories guessed from build-script text. */
internal object CompilerWorkspaceModels {
    const val PATH = ".gradle/xtc/lsp-workspace.json"

    fun roots(project: Project): List<Path> =
        (listOfNotNull(project.basePath) + GradleSettings.getInstance(project).linkedProjectsSettings.map { it.externalProjectPath })
            .map { Path.of(it).toAbsolutePath().normalize() }.distinct().sorted()

    fun read(roots: List<Path>): String? {
        val reports = roots.mapNotNull { root ->
            listOf(PATH, CompilerBuildModel.PATH).map(root::resolve).firstOrNull(Files::isRegularFile)
                ?.let { CompilerBuildModel.parse(Files.readString(it)) }
        }
        return reports.takeIf { it.isNotEmpty() }?.let(::merge)?.toString()
    }

    fun merge(reports: List<JsonObject>): JsonObject {
        val entries = reports.flatMap { it["sourceSets"].asJsonArray.toList() }
            .groupBy { it.asJsonObject["projectId"].asString to it.asJsonObject["sourceSet"].asString }
            .values.map { matches ->
                require(matches.distinct().size == 1) { "Conflicting Gradle source-set ownership" }
                matches.first()
            }
        return JsonObject().apply {
            addProperty("schemaVersion", 1)
            add("sourceSets", JsonArray().apply { entries.forEach(::add) })
            add("buildRoots", JsonArray().apply {
                reports.flatMap { it["buildRoots"]?.asJsonArray?.toList().orEmpty() }.distinct().forEach(::add)
            })
        }
    }

    fun importScript(): Path {
        val contents = requireNotNull(javaClass.getResourceAsStream("/compiler-model.init.gradle")).use { it.readAllBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(contents).joinToString("") { "%02x".format(it) }
        val file = Path.of(PathManager.getSystemPath(), "ecstasy", "compiler-import", digest, "compiler-model.init.gradle")
        Files.createDirectories(file.parent)
        if (!Files.exists(file)) Files.write(file, contents)
        return file
    }
}
