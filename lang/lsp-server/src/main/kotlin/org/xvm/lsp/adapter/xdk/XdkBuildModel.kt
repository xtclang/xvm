package org.xvm.lsp.adapter.xdk

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

/** Evaluated, versioned host input. No build-script parsing, Gradle execution or IDE dependency. */
internal class XdkBuildModel private constructor(
    private val sourceSets: List<SourceSet>,
) {
    /** Sources, resources and dependency paths exported for one Gradle source set. */
    data class SourceSet(
        val project: String,
        val name: String,
        val sources: List<File>,
        val roots: Set<File>,
        val resources: List<String>,
        val dependencies: Set<String>,
        val modulePath: List<File>,
        /** The project's path in the whole build tree, such as `:xdk:lib-json`, which names its tasks; null from an older plugin. */
        val buildTreePath: String? = null,
        /** The project's run settings, which apply to the modules of each of its source sets; null from an older plugin. */
        val runs: Runs? = null,
    )

    /**
     * A project's run settings, as its `xtcRun` and `xtcTest` have them: the method and arguments that `xtcRun` runs each
     * module it names with, by the module's name, the JVM options of each, and what of each the export could not read.
     */
    data class Runs(
        val modules: Map<String, Run>,
        val jvmArgs: List<String>,
        val testJvmArgs: List<String>,
        val problems: List<String> = emptyList(),
        val testProblems: List<String> = emptyList(),
    )

    /** The [method] and [args] that a project's `xtcRun` runs one of the modules it names with. */
    data class Run(
        val method: String,
        val args: List<String>,
    )

    /** Source modules and binary dependencies assembled from an accepted Gradle build model. */
    data class Inputs(
        val modules: List<XdkSourceModule>,
        val binaries: List<XdkDependency>,
        /** The Gradle project that owns each source module, by module name, as its path in the build tree, where known. */
        val projects: Map<String, String> = emptyMap(),
        /**
         * Whether [modules] are the model's own, every module of the composite build it was exported from with its
         * included builds, rather than a host's explicit list.
         */
        val composite: Boolean = false,
        /** What the Gradle project that owns each source module runs it with, by module name, where the model says. */
        val runs: Map<String, XdkRunSettings> = emptyMap(),
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
            XdkLibraryReplacement.apply(
                discovered.map { module ->
                    val owner = owners.getValue(module)
                    XdkSourceModule(
                        module.name,
                        module.uri,
                        module.dependencies.intersect(allowed(owner)),
                        owner.resources,
                    )
                },
            )
        // Validate the complete source graph before a live configuration is replaced.
        XdkProject(modules)
        val binaries =
            sourceSets
                .asSequence()
                .flatMap { it.modulePath }
                .distinct()
                .flatMap { root ->
                    when {
                        root.isDirectory -> root.listFiles().orEmpty().filter { it.isFile && it.extension == "xtc" }
                        root.isFile && root.extension == "xtc" -> listOf(root)
                        else -> emptyList()
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
        val owned = owners.entries.associate { (module, owner) -> module.name to owner.buildTreePath }
        val projects = modules.mapNotNull { module -> owned[module.name]?.let { module.name to it } }.toMap()
        val settings = owners.entries.associate { (module, owner) -> module.name to owner.runs?.let { settings(module.name, owner, it) } }
        val runs = modules.mapNotNull { module -> settings[module.name]?.let { module.name to it } }.toMap()
        return Inputs(modules, binaries, projects, composite = true, runs = runs)
    }

    /**
     * What [owner]'s project, whose run settings are [runs], runs its module [module] with: `xtcRun` names the module by
     * its qualified name, or by its first part, as the runner finds `MyApp.example.org` by `MyApp`.
     */
    private fun settings(
        module: String,
        owner: SourceSet,
        runs: Runs,
    ): XdkRunSettings {
        val run = runs.modules[module] ?: runs.modules[module.substringBefore('.')]
        return XdkRunSettings(owner.buildTreePath, run?.method, run?.args, runs.jvmArgs, runs.testJvmArgs, runs.problems, runs.testProblems)
    }

    companion object {
        fun read(models: List<JsonObject>): XdkBuildModel {
            // what the run settings' reading notes, once for a read, as every source set of a project has the same
            val notes = linkedSetOf<String>()
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
                            entry["buildTreePath"]?.let { string("buildTreePath") },
                            runs(entry, project, notes),
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
            notes.forEach(logger::warn)
            return XdkBuildModel(entries)
        }

        private val logger = LoggerFactory.getLogger(XdkBuildModel::class.java)

        /**
         * A source set's project's run settings, its `run` and `test`: `run` has the JVM options of the project's `xtcRun`,
         * by module name the method and arguments it runs each module it names with, and what the export could not read;
         * `test` has `xtcTest`'s JVM options and what of them it could not read. Null where the source set has neither,
         * as from an older plugin. They are read leniently: a malformed section, module or field of them is left out, which
         * [notes] says, and never makes the model refused, as the model's sources and paths do not depend on them; [notes]
         * also has what the export could not read.
         */
        private fun runs(
            entry: JsonObject,
            project: String,
            notes: MutableSet<String>,
        ): Runs? {
            fun warn(what: String) {
                notes.add("build model: $what of $project is malformed, and left out")
            }
            val run = section(entry["run"], "run", ::warn)
            val test = section(entry["test"], "test", ::warn)
            if (run == null && test == null) return null
            val modules =
                run?.get("modules")?.let { value ->
                    value.takeIf { it.isJsonObject }?.asJsonObject ?: null.also { warn("run modules") }
                }
            return Runs(
                modules
                    ?.entrySet()
                    .orEmpty()
                    .mapNotNull { (name, value) ->
                        val module = value.takeIf { name.isNotBlank() && it.isJsonObject }?.asJsonObject
                        val method = module?.get("methodName")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                        val args = module?.let { strings(it["moduleArgs"]) }
                        if (method.isNullOrBlank() || args == null) {
                            null.also { warn("run module '$name'") }
                        } else {
                            name to Run(method, args)
                        }
                    }.toMap(),
                run?.let { strings(it["jvmArgs"]) ?: emptyList<String>().also { warn("run jvmArgs") } }.orEmpty(),
                test?.let { strings(it["jvmArgs"]) ?: emptyList<String>().also { warn("test jvmArgs") } }.orEmpty(),
                run?.let { strings(it["problems"]) ?: emptyList<String>().also { warn("run problems") } }.orEmpty(),
                test?.let { strings(it["problems"]) ?: emptyList<String>().also { warn("test problems") } }.orEmpty(),
            ).also { runs ->
                (runs.problems + runs.testProblems).forEach { notes.add("build model: the export of $project could not read $it") }
            }
        }

        /** The object that [value], the build model's section [key], is: none without one, and none, with [warn], if it is not one. */
        private fun section(
            value: JsonElement?,
            key: String,
            warn: (String) -> Unit,
        ): JsonObject? =
            when {
                value == null -> null
                value.isJsonObject -> value.asJsonObject
                else -> null.also { warn(key) }
            }

        /** The strings of [value]: none for none, and null where it is anything but an array of strings. */
        private fun strings(value: JsonElement?): List<String>? =
            when {
                value == null -> emptyList()

                value.isJsonArray && value.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString } ->
                    value.asJsonArray.map { it.asString }

                else -> null
            }
    }
}
