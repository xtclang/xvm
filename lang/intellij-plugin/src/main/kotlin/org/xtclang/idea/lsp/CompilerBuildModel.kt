package org.xtclang.idea.lsp

import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import org.eclipse.lsp4j.DidChangeConfigurationParams
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/** Portable evaluated Gradle contract. Uses only Community platform APIs. */
object CompilerBuildModel {
    const val PATH = ".gradle/xtc/lsp-model.json"

    fun parse(text: String): JsonObject {
        val model = JsonParser.parseString(text).asJsonObject
        require(model["schemaVersion"]?.asInt == 1 && model["sourceSets"]?.isJsonArray == true) {
            "Unsupported Gradle compiler model; refresh build configuration"
        }
        val entries = model["sourceSets"].asJsonArray.map { it.asJsonObject }
        model["importId"]?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Invalid Gradle import identity" } }
        model["buildRoots"]?.let { roots ->
            require(roots.isJsonArray && roots.asJsonArray.all { URI(it.asString).scheme == "file" }) { "Invalid Gradle build roots" }
        }
        entries.forEach { entry ->
            listOf(
                "projectId",
                "projectPath",
                "projectDirectory",
                "buildFile",
                "sourceSet",
                "resourceTask",
            ).forEach { field ->
                require(
                    entry[field]?.let {
                        it.isJsonPrimitive &&
                            it.asJsonPrimitive.isString &&
                            it.asString.isNotBlank()
                    } == true,
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
            ).forEach { field ->
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
                },
            )
        }
        require(
            entries.map { it["projectId"].asString to it["sourceSet"].asString }.distinct().size ==
                entries.size,
        ) {
            "Duplicate Gradle source-set owner"
        }
        return model
    }

    fun read(project: Project): JsonObject? {
        val imports = project.service<CompilerImportService>().model
        return runCatching { imports.current() }
            .getOrElse {
                logger<CompilerBuildModel>().warn("Cannot read Gradle compiler inputs; retaining previous import", it)
                imports.retained()
            }?.let(::parse)
    }

    fun settings(
        project: Project,
        current: Any?,
    ): JsonObject {
        val settings =
            Gson().toJsonTree(current).takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val model = read(project)
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
            if (SourceGraphConfiguration.read(CompilerSettings.content(project)) != null) {
                "Explicit project override (preserved across Gradle refresh)"
            } else {
                "Gradle model when imported; workspace conventions otherwise"
            }
        val imports = project.service<CompilerImportService>().model
        val options = LibraryConfiguration.read(CompilerSettings.content(project))
        val libraries =
            buildList {
                add("Bundled XDK: read-only, always included")
                add(
                    if (options.modulePath ==
                        null
                    ) {
                        "External libraries: inherited from Gradle"
                    } else {
                        "External libraries: explicit project/application override (ordered)"
                    },
                )
                options.modulePath.orEmpty().forEachIndexed { index, path -> add("  ${index + 1}. $path") }
                options.sourceAttachments.forEach { attachment ->
                    add("Attached sources for ${attachment.module} (read-only navigation):")
                    attachment.roots.forEachIndexed { index, path -> add("  ${index + 1}. $path") }
                }
            }.joinToString("\n")
        val status = imports.description() + "\n" + libraries
        val model =
            read(project)
                ?: return "$origin\n$status\nNo Gradle model imported. Manual paths also work without a build file."
        return origin +
            "\n$status\n\n" +
            model["sourceSets"].asJsonArray.joinToString("\n\n") { value ->
                val entry = value.asJsonObject
                buildList {
                    add(
                        "${entry["projectPath"].asString} / ${entry["sourceSet"].asString} [Gradle model]",
                    )
                    add("Build: ${entry["buildFile"].asString}")
                    val missing = entry["resourceRoots"].asJsonArray.count { !Files.exists(Path.of(URI(it.asString))) }
                    add("Processed resources: " + if (missing == 0) "ready" else "$missing missing; prepare generated resources")
                    listOf("sourceRoots", "resourceSourceRoots", "resourceRoots", "modulePath")
                        .forEach { kind ->
                            entry[kind].asJsonArray.forEach { item ->
                                val uri = URI(item.asString)
                                add(
                                    "  $kind: $uri" +
                                        if (Files.exists(Path.of(uri))) {
                                            ""
                                        } else {
                                            " [missing; prepare generated inputs]"
                                        },
                                )
                            }
                        }
                }.joinToString("\n")
            }
    }

    fun refresh(
        project: Project,
        prepare: Boolean,
        finished: (String?) -> Unit,
    ) {
        val service = project.service<CompilerImportService>()
        ProgressManager
            .getInstance()
            .run(
                object : Task.Backgroundable(
                    project,
                    if (prepare) "Prepare Ecstasy compiler inputs" else "Refresh Ecstasy compiler paths",
                    true,
                ) {
                    private val result =
                        AtomicReference(
                            CompilerImport.Result(
                                CompilerImport.Outcome.CANCELLED,
                                "Import cancelled; previous compiler configuration retained.",
                            ),
                        )

                    override fun run(indicator: ProgressIndicator) {
                        val completed =
                            try {
                                service.run(indicator) {
                                    importModel(project, service.model, indicator, prepare)
                                }
                            } catch (cancelled: ProcessCanceledException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                CompilerImport.Result(CompilerImport.Outcome.FAILED, failure.message ?: "Compiler import failed")
                            }
                        result.set(completed)
                    }

                    override fun onSuccess() = complete()

                    override fun onCancel() = complete()

                    private fun complete() {
                        if (project.isDisposed) return
                        val completed = result.get()
                        if (completed.outcome == CompilerImport.Outcome.SUCCEEDED) publish(project)
                        finished(completed.message.takeUnless { completed.outcome == CompilerImport.Outcome.SUCCEEDED })
                    }
                },
            )
    }

    private fun importModel(
        project: Project,
        model: CompilerImport,
        indicator: ProgressIndicator,
        prepare: Boolean,
    ): CompilerImport.Result {
        // A malformed existing report must not prevent an explicit refresh from repairing it.
        runCatching { model.current() }
        val operation = model.begin(prepare)
        val result =
            try {
                val roots = CompilerWorkspaceModels.roots(project)
                val wrapperName = if (System.getProperty("os.name").startsWith("Windows")) "gradlew.bat" else "gradlew"
                val builds = roots.filter { Files.isRegularFile(it.resolve(wrapperName)) }
                require(builds.isNotEmpty()) { "No Gradle wrapper here; configure manual paths instead" }
                indicator.text = if (prepare) "Preparing generated sources and resources" else "Reading evaluated Gradle inputs"
                val outputs =
                    builds.asSequence().map { root ->
                        indicator.checkCanceled()
                        indicator.text2 = root.toString()
                        val command =
                            GeneralCommandLine(
                                root.resolve(wrapperName).toString(),
                                "--init-script",
                                CompilerWorkspaceModels.importScript().toString(),
                                if (prepare) "prepareEcstasyWorkspaceModel" else "exportEcstasyWorkspaceModel",
                                "--console=plain",
                            ).withWorkDirectory(root.toFile()).withEnvironment("XTC_COMPILER_IMPORT_ID", operation.id)
                        CapturingProcessHandler(command).runProcessWithProgressIndicator(indicator)
                    }
                val output = outputs.firstOrNull { it.isCancelled || it.isTimeout || it.exitCode != 0 }
                when {
                    output?.isCancelled == true || indicator.isCanceled -> {
                        model.finish(operation, CompilerImport.Outcome.CANCELLED)
                    }

                    output != null -> {
                        model.finish(
                            operation,
                            CompilerImport.Outcome.FAILED,
                            "Gradle import failed; previous compiler configuration retained.\n" +
                                (output.stdout + output.stderr).takeLast(8000),
                        )
                    }

                    else -> {
                        model.finish(operation, CompilerImport.Outcome.SUCCEEDED, cancelled = { indicator.isCanceled })
                    }
                }
            } catch (cancelled: ProcessCanceledException) {
                model.finish(operation, CompilerImport.Outcome.CANCELLED)
                throw cancelled
            } catch (failure: Exception) {
                model.finish(operation, CompilerImport.Outcome.FAILED, "${failure.message}; previous compiler configuration retained.")
            }
        if (result.outcome == CompilerImport.Outcome.CANCELLED) throw ProcessCanceledException()
        return result
    }

    fun publish(project: Project) {
        if (project.isDisposed) return
        // The evaluated inputs changed, but persisted user settings did not. LSP4IJ suppresses
        // no-op settings updates, so notify the existing connection directly.
        LanguageServiceAccessor
            .getInstance(project)
            .startedServers
            .filter { it.serverDefinition.id == CompilerSettings.SERVER_ID }
            .forEach { wrapper ->
                wrapper.initializedServer.thenAccept { server ->
                    if (!project.isDisposed) {
                        val current = CompilerSettings.store(project).getLanguageServerSettings(CompilerSettings.SERVER_ID)
                        server.workspaceService.didChangeConfiguration(
                            DidChangeConfigurationParams(settings(project, current?.getLanguageServerConfiguration(project))),
                        )
                    }
                }
            }
    }
}
