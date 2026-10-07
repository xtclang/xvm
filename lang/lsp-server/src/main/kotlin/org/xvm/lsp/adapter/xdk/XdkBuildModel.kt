package org.xvm.lsp.adapter.xdk

import com.google.gson.JsonObject
import java.io.File
import java.io.IOException

/** Evaluated, versioned host input. No build-script parsing, Gradle execution or IDE dependency. */
internal class XdkBuildModel private constructor(
    private val sourceSets: List<SourceSet>,
) {
    data class SourceSet(
        val project: String,
        val name: String,
        val sources: List<File>,
        val roots: Set<File>,
        val resources: List<String>,
        val dependencies: Set<String>,
        val modulePath: List<File>,
    )

    data class Inputs(
        val modules: List<XdkSourceModule>,
        val binaries: List<XdkDependency>,
    )

    fun resolve(): Inputs =
        try {
            resolveFiles()
        } catch (failure: IOException) {
            throw IllegalArgumentException(
                "Cannot read evaluated compiler inputs: ${failure.message}",
                failure,
            )
        }

    private fun resolveFiles(): Inputs {
        val sources = sourceSets.flatMap { it.sources }.distinct()
        val catalog =
            sources.filter(File::isFile).fold(XdkWorkspaceDiscovery.Catalog()) { result, file ->
                result.withSource(file, file.readText()) { false }
            }
        val roots = sourceSets.flatMap { it.roots }.toSet()
        val discovered = catalog.modules(emptyList()).filter { it.root in roots }
        val owners =
            discovered.associateWith { module ->
                sourceSets.singleOrNull { module.root in it.sources }
                    ?: throw IllegalArgumentException("Ambiguous build ownership for ${module.uri}")
            }

        fun allowed(owner: SourceSet): Set<String> {
            val projects =
                generateSequence(owner.dependencies) { current ->
                    (
                        current +
                            sourceSets
                                .filter { it.project in current }
                                .flatMap { it.dependencies }
                    ).takeUnless { it == current }
                }.last()
            return owners
                .filterValues {
                    it === owner ||
                        it.project in projects ||
                        (owner.name == "test" && it.project == owner.project && it.name == "main")
                }.keys
                .mapTo(linkedSetOf()) { it.name }
        }
        val modules =
            discovered.map { module ->
                val owner = owners.getValue(module)
                XdkSourceModule(
                    module.name,
                    module.uri,
                    module.dependencies.intersect(allowed(owner)),
                    owner.resources,
                )
            }
        // Validate the complete source graph before a live configuration is replaced.
        XdkProject(modules)
        val binaries =
            sourceSets
                .asSequence()
                .flatMap { it.modulePath }
                .distinct()
                .flatMap { root ->
                    when {
                        root.isDirectory -> {
                            root.listFiles().orEmpty().filter { it.isFile && it.extension == "xtc" }
                        }

                        root.isFile && root.extension == "xtc" -> {
                            listOf(root)
                        }

                        else -> {
                            emptyList()
                        }
                    }
                }.distinct()
                .map { XdkDependency.fromBinary(it.readBytes()) }
                .filter {
                    it.module !in XdkLibraries.moduleNames &&
                        it.module !in modules.map(XdkSourceModule::name)
                }.groupBy { it.module }
                .map { (name, values) ->
                    require(values.map { it.revision }.distinct().size == 1) {
                        "Conflicting build artifacts for $name"
                    }
                    values.first()
                }.toList()
        return Inputs(modules, binaries)
    }

    companion object {
        fun read(models: List<JsonObject>): XdkBuildModel {
            val entries =
                models.flatMap { model ->
                    require(
                        model["schemaVersion"]?.let {
                            it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.asInt == 1
                        } == true,
                    ) {
                        "Unsupported Gradle compiler model version"
                    }
                    require(model["sourceSets"]?.isJsonArray == true) {
                        "Gradle compiler model requires sourceSets"
                    }
                    model["sourceSets"].asJsonArray.map { value ->
                        require(value.isJsonObject) { "Gradle source sets must be objects" }
                        val entry = value.asJsonObject

                        fun strings(key: String): List<String> {
                            require(entry[key]?.isJsonArray == true) {
                                "Build-model $key must be an array"
                            }
                            return entry[key].asJsonArray.map {
                                require(it.isJsonPrimitive && it.asJsonPrimitive.isString) {
                                    "Invalid build-model $key"
                                }
                                it.asString
                            }
                        }

                        fun string(key: String): String {
                            val text = entry[key]
                            require(
                                text != null &&
                                    text.isJsonPrimitive &&
                                    text.asJsonPrimitive.isString &&
                                    text.asString.isNotBlank(),
                            ) {
                                "Build-model $key must be a non-blank string"
                            }
                            return text.asString
                        }

                        fun files(key: String) =
                            strings(key).map {
                                requireNotNull(XdkSources.file(it)) {
                                    "Build-model $key requires file URIs"
                                }
                            }
                        val project = string("projectId")
                        val name = string("sourceSet")
                        SourceSet(
                            project,
                            name,
                            files("sourceFiles"),
                            files("moduleRoots").toSet(),
                            files("resourceRoots").map { it.toURI().toString() },
                            strings("projectDependencies").toSet(),
                            files("modulePath"),
                        )
                    }
                }
            require(entries.map { it.project to it.name }.distinct().size == entries.size) {
                "Duplicate Gradle source-set ownership"
            }
            require(
                entries.flatMap { it.sources }.distinct().size == entries.sumOf { it.sources.size },
            ) {
                "Overlapping Gradle module roots"
            }
            require(entries.all { it.sources.containsAll(it.roots) }) {
                "Module roots must belong to their source set"
            }
            return XdkBuildModel(entries)
        }
    }
}
