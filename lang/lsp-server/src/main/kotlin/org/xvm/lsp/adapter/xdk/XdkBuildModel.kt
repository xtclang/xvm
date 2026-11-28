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
        /** The XDK that the project compiles and runs with; null from an older plugin, or for a project without one. */
        val xdk: Xdk? = null,
    )

    /**
     * The XDK that a source set's project compiles and runs with, as the model says it: its [version]; the directory of
     * its [libraries], where the plugin extracts `ecstasy.xtc` and the other core modules for the project's builds and
     * runs; and what it holds, as the export read it from the XDK's distribution, the SHA-256 of each of its module files,
     * its [modules], and of its `javatools.jar`, its [javatools]. Where the export could not say what it holds, it has
     * either the [project] of the build tree that makes it, which the export neither builds nor reads, or the [problem]
     * that kept the export from reading it; null [modules] with neither is a model that does not say.
     */
    data class Xdk(
        val version: String?,
        val libraries: File?,
        val modules: List<String>? = null,
        val javatools: String? = null,
        val project: String? = null,
        val problem: String? = null,
    ) {
        /** Whether this is the bundled XDK: its modules and javatools are the bundled ones ([XdkLibraries.bundles]). */
        val bundled: Boolean get() = project == null && problem == null && modules != null && XdkLibraries.bundles(modules, javatools)

        /**
         * Why the server does not build or run [module], whose Gradle build compiles it with this XDK, which is not the
         * bundled one, or, for another [owner], which depends on that module, whose Gradle build does: what the module's
         * workspace folder does, after its name, and what a build of it that the server refuses says.
         */
        fun noEmbedded(
            module: String,
            owner: String = module,
        ): NoEmbeddedBuild =
            described().let {
                val builds = if (owner == module) "builds with $it" else "depends on the module $owner, which builds with $it"
                NoEmbeddedBuild(
                    builds,
                    "The module $module $builds, and the server builds and runs only with the bundled XDK, so build and run " +
                        "it with Gradle (Ecstasy build mode: Gradle)",
                    this,
                    owner,
                )
            }

        /** This XDK against the bundled one, as a module builds with it: `XDK 0.5.0, not the bundled XDK 0.4.4`. */
        private fun described(): String {
            val bundled = XdkLibraries.version?.let { "the bundled XDK $it" } ?: "the bundled XDK"
            val named = version?.let { "XDK $it" } ?: "an XDK"
            val unknown = "which the language server cannot compare with $bundled"
            return when {
                project != null -> "$named that the build's own project $project makes, $unknown"
                problem != null -> "$named, $unknown, as $problem"
                modules == null -> "$named, $unknown, as the Gradle model does not say what it holds"
                version != null && version == XdkLibraries.version -> "another build of XDK $version than the bundled one"
                else -> "$named, not $bundled"
            }
        }
    }

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
        /**
         * The XDK that the Gradle project that owns each source module compiles and runs it with, by module name, where the
         * model names one that is not the bundled XDK, which the server builds and runs with: such a module builds and runs
         * with Gradle ([XdkProject.gradleOnly]).
         */
        val xdks: Map<String, Xdk> = emptyMap(),
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
        // the reads of the build's sources are one discovery for the trace
        val catalog =
            XdkWorkspaceDiscovery.traced("build-model") {
                sources.filter(File::isFile).fold(XdkWorkspaceDiscovery.Catalog()) { result, file ->
                    result.withSource(file, XdkSources.read(file)) { false }
                }
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
        return Inputs(modules, binaries, projects, composite = true, runs = runs, xdks = otherXdks(owners, modules))
    }

    /**
     * The XDK that the Gradle project of each of [modules] builds it with, by module name, where the model of its [owners]
     * source set names one that is not the bundled XDK. Telling it from the bundled one compares the hashes that the
     * model has, without reading a file.
     */
    private fun otherXdks(
        owners: Map<XdkSourceModule, SourceSet>,
        modules: List<XdkSourceModule>,
    ): Map<String, Xdk> {
        val built = owners.entries.associate { (module, owner) -> module.name to owner.xdk }
        return modules.mapNotNull { module -> built[module.name]?.takeUnless { it.bundled }?.let { module.name to it } }.toMap()
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
                            xdk(entry["xdk"], project, notes),
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

        /**
         * A source set's project's XDK, its `xdk`: none where the source set has none, as from an older plugin or for a
         * project without an XDK dependency, whose modules keep to the bundled XDK. One that is not an object, or whose
         * fields are not what they should be, is one that the model does not say the contents of, which [notes] says: the
         * server cannot tell that it is the bundled XDK, and does not take it for one.
         */
        private fun xdk(
            value: JsonElement?,
            project: String,
            notes: MutableSet<String>,
        ): Xdk? {
            if (value == null) return null
            val xdk = value.takeIf { it.isJsonObject }?.asJsonObject

            fun text(key: String) =
                xdk
                    ?.get(key)
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString
                    ?.takeIf { it.isNotBlank() }
            val modules = xdk?.get("modules")?.let(::strings)
            val read =
                Xdk(
                    text("version"),
                    text("libraries")?.let(XdkSources::file),
                    modules,
                    text("javatools"),
                    text("project"),
                    text("problem"),
                )
            if (read.modules == null && read.project == null && read.problem == null) {
                notes.add("build model: the XDK of $project is malformed, and taken for another than the bundled one")
            }
            return read
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
