package org.xtclang.idea.lsp

import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import org.eclipse.lsp4j.DidChangeConfigurationParams

/** Portable evaluated Gradle contract. Uses only Community platform APIs. */
object CompilerBuildModel {
    const val PATH = ".gradle/xtc/lsp-model.json"
    private val lastGoodModel = Key.create<JsonObject>("xtc.compiler.buildModel")

    fun parse(text: String): JsonObject {
        val model = JsonParser.parseString(text).asJsonObject
        require(model["schemaVersion"]?.asInt == 1 && model["sourceSets"]?.isJsonArray == true) {
            "Unsupported Gradle compiler model; refresh build configuration"
        }
        val entries = model["sourceSets"].asJsonArray.map { it.asJsonObject }
        entries.forEach { entry ->
            listOf(
                    "projectId",
                    "projectPath",
                    "projectDirectory",
                    "buildFile",
                    "sourceSet",
                    "resourceTask",
                )
                .forEach { field ->
                    require(
                        entry[field]?.let {
                            it.isJsonPrimitive &&
                                it.asJsonPrimitive.isString &&
                                it.asString.isNotBlank()
                        } == true
                    ) {
                        "Invalid build-model $field"
                    }
                }
            listOf(
                    "sourceRoots",
                    "sourceFiles",
                    "moduleRoots",
                    "resourceSourceRoots",
                    "resourceRoots",
                    "modulePath",
                )
                .forEach { field ->
                    require(entry[field]?.isJsonArray == true) { "Missing build-model $field" }
                    entry[field].asJsonArray.forEach {
                        require(URI(it.asString).scheme == "file") {
                            "Build-model paths must be file URIs"
                        }
                    }
                }
            listOf("buildFile", "projectDirectory").forEach {
                require(URI(entry[it].asString).scheme == "file")
            }
            require(entry["projectDependencies"]?.isJsonArray == true)
            require(
                entry["projectDependencies"].asJsonArray.all {
                    it.isJsonPrimitive && it.asJsonPrimitive.isString
                }
            )
        }
        require(
            entries.map { it["projectId"].asString to it["sourceSet"].asString }.distinct().size ==
                entries.size
        ) {
            "Duplicate Gradle source-set owner"
        }
        return model
    }

    fun read(project: Project): JsonObject? =
        project.basePath?.let { root ->
            Path.of(root).resolve(PATH).takeIf(Files::isRegularFile)?.let {
                parse(Files.readString(it))
            }
        }

    fun settings(project: Project, current: Any?): JsonObject {
        val settings =
            Gson().toJsonTree(current).takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val model = runCatching {
            read(project).also { project.putUserData(lastGoodModel, it) }
        }
            .getOrElse {
                logger<CompilerBuildModel>()
                    .warn("Cannot read Gradle compiler inputs; retaining previous import", it)
                project.getUserData(lastGoodModel)
            }
        val xtc =
            settings["xtc"]?.takeIf { it.isJsonObject }?.asJsonObject
                ?: JsonObject().also { settings.add("xtc", it) }
        val compiler =
            xtc["compiler"]?.takeIf { it.isJsonObject }?.asJsonObject
                ?: JsonObject().also { xtc.add("compiler", it) }
        if (!compiler.has("sourceModules")) compiler.add("sourceModules", JsonNull.INSTANCE)
        compiler.add("buildModels", Gson().toJsonTree(listOfNotNull(model)))
        return settings
    }

    fun describe(project: Project): String {
        val origin =
            if (SourceGraphConfiguration.read(CompilerSettings.content(project)) != null)
                "Explicit project override (preserved across Gradle refresh)"
            else "Gradle model when imported; workspace conventions otherwise"
        val model =
            read(project)
                ?: return "$origin\nNo Gradle model imported. Manual paths also work without a build file."
        return origin +
            "\n\n" +
            model["sourceSets"].asJsonArray.joinToString("\n\n") { value ->
                val entry = value.asJsonObject
                buildList {
                        add(
                            "${entry["projectPath"].asString} / ${entry["sourceSet"].asString} [Gradle model]"
                        )
                        add("Build: ${entry["buildFile"].asString}")
                        listOf("sourceRoots", "resourceSourceRoots", "resourceRoots", "modulePath")
                            .forEach { kind ->
                                entry[kind].asJsonArray.forEach { item ->
                                    val uri = URI(item.asString)
                                    add(
                                        "  $kind: $uri" +
                                            if (Files.exists(Path.of(uri))) ""
                                            else " [missing; prepare generated inputs]"
                                    )
                                }
                            }
                    }
                    .joinToString("\n")
            }
    }

    fun refresh(project: Project, prepare: Boolean, finished: (String?) -> Unit) {
        ProgressManager.getInstance()
            .run(
                object : Task.Backgroundable(project, "Import XTC compiler paths", true) {
                    override fun run(indicator: ProgressIndicator) {
                        val failure = runCatching {
                            val root = Path.of(requireNotNull(project.basePath))
                            val wrapper =
                                root.resolve(
                                    if (System.getProperty("os.name").startsWith("Windows"))
                                        "gradlew.bat"
                                    else "gradlew"
                                )
                            require(Files.isRegularFile(wrapper)) {
                                "No Gradle wrapper here; configure manual paths instead"
                            }
                            val command =
                                GeneralCommandLine(
                                        wrapper.toString(),
                                        if (prepare) "prepareXtcLspModel" else "exportXtcLspModel",
                                        "--console=plain",
                                    )
                                    .withWorkDirectory(root.toFile())
                            val result =
                                CapturingProcessHandler(command)
                                    .runProcessWithProgressIndicator(indicator)
                            check(
                                !result.isCancelled && !result.isTimeout && result.exitCode == 0
                            ) {
                                "Gradle import failed; previous configuration retained.\n" +
                                    (result.stdout + result.stderr).takeLast(8000)
                            }
                            requireNotNull(read(project)) {
                                "Gradle did not export an XTC compiler model"
                            }
                        }
                            .exceptionOrNull()
                            ?.message
                        ApplicationManager.getApplication().invokeLater {
                            if (project.isDisposed) return@invokeLater
                            if (failure == null) publish(project)
                            finished(failure)
                        }
                    }
                }
            )
    }

    fun publish(project: Project) {
        val current =
            CompilerSettings.store(project).getLanguageServerSettings(CompilerSettings.SERVER_ID)
        val config = settings(project, current?.getLanguageServerConfiguration(project))
        // The evaluated inputs changed, but persisted user settings did not. LSP4IJ suppresses
        // no-op settings updates, so notify the existing connection directly.
        LanguageServiceAccessor.getInstance(project)
            .startedServers
            .filter { it.serverDefinition.id == CompilerSettings.SERVER_ID }
            .forEach { wrapper ->
                wrapper.initializedServer.thenAccept { server ->
                    if (!project.isDisposed)
                        server.workspaceService.didChangeConfiguration(
                            DidChangeConfigurationParams(config)
                        )
                }
            }
    }
}
