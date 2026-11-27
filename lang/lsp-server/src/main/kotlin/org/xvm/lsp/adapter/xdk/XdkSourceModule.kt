package org.xvm.lsp.adapter.xdk

import java.io.File
import java.util.Collections
import java.util.List.copyOf as immutableList
import java.util.Map.copyOf as immutableMap
import java.util.Set.copyOf as immutableSet

/**
 * Explicit host configuration; dependency names may identify source modules or binary artifacts.
 */
class XdkSourceModule(
    val name: String,
    uri: String,
    dependencies: Set<String> = emptySet(),
    resourceRoots: List<String>? = null,
) {
    internal val root: File =
        requireNotNull(XdkSources.file(uri)) { "A source module requires a file URI: $uri" }
    val uri: String = root.toURI().toString()

    /** In name order, as diagnostics list them; a Set.copyOf's order changes with each JVM. */
    val dependencies: Set<String> = Collections.unmodifiableSet(dependencies.toSortedSet())
    internal val resourceFiles: List<File>? =
        resourceRoots
            ?.map {
                requireNotNull(XdkSources.file(it)) { "A resource root requires a file URI: $it" }
            }?.let(::immutableList)
    val resourceRoots: List<String>? =
        resourceFiles?.map { it.toURI().toString().trimEnd('/') + "/" }?.let(::immutableList)

    init {
        require(resourceFiles.orEmpty().all { !it.exists() || it.isDirectory }) {
            "Resource roots must be directories"
        }
        require(resourceFiles == null || resourceFiles.distinct().size == resourceFiles.size) {
            "Duplicate resource roots"
        }
        require(name.isNotBlank() && root.extension == "x") {
            "A source module requires a name and an .x root"
        }
        require(name !in XdkLibraryReplacement.BOOTSTRAP) {
            "A source module cannot replace the bundled bootstrap module $name"
        }
    }
}

/**
 * Immutable graph. Ordering and reverse edges are established before installing a configuration. The order is the
 * graph's alone, not that of the list it is made from, which is often another project's modules. [xdks] is the XDK that
 * the Gradle build of each module compiles it with, by name, where that is not the bundled one ([XdkBuildModel.Inputs]).
 */
internal class XdkProject(
    modules: List<XdkSourceModule>,
    xdks: Map<String, XdkBuildModel.Xdk> = emptyMap(),
) {
    /** The modules by name, in [buildOrder]. */
    val modules: Map<String, XdkSourceModule>

    /** The bundled libraries that this project's own modules replace ([XdkLibraryReplacement]). */
    val replaced: Set<String> = immutableSet(modules.map { it.name }.filter(XdkLibraryReplacement::replaces))

    /** The modules built against a replaced library: the replacing modules and every module that depends on one. */
    val onReplaced: Set<String>

    /** The XDK that the Gradle build of each of the graph's modules compiles it with, by name, where it is another. */
    val xdks: Map<String, XdkBuildModel.Xdk> = immutableMap(xdks.filterKeys { name -> modules.any { it.name == name } })

    /**
     * Why the server does not build or run each module that it does not, by name, which builds and runs with Gradle: one
     * built against a replaced library ([onReplaced]), a module that its Gradle build compiles with another XDK ([xdks]),
     * and every module that depends on one of them, which is built with it; a replaced library's reason before another
     * XDK's ([noEmbedded]).
     */
    val gradleOnly: Map<String, NoEmbeddedBuild>

    private val configuration =
        modules.map { Configuration(it.name, it.uri, it.dependencies, it.resourceRoots) }.toSet()
    private val ordered: List<XdkSourceModule>

    init {
        val named = immutableMap(modules.associateBy { it.name })
        require(named.size == modules.size) {
            modules
                .groupBy { it.name }
                .filterValues { it.size > 1 }
                .entries
                .joinToString("; ", "Duplicate source module name ") { (name, same) -> "$name: ${same.joinToString { it.uri }}" }
        }
        require(modules.map { it.root }.distinct().size == modules.size) {
            "Duplicate source module root"
        }
        modules.forEach { owner ->
            val members = File(owner.root.parentFile, owner.root.nameWithoutExtension).toPath()
            require(modules.none { it !== owner && it.root.toPath().startsWith(members) }) {
                "Overlapping source module roots: ${owner.uri}"
            }
        }
        val visited = mutableSetOf<String>()
        val visiting = linkedSetOf<String>()
        val result = mutableListOf<XdkSourceModule>()

        fun visit(module: XdkSourceModule) {
            if (module.name in visited) return
            require(visiting.add(module.name)) {
                "Cyclic source dependencies: ${visiting.joinToString(" -> ")} -> ${module.name}"
            }
            module.dependencies
                .sorted()
                .mapNotNull(named::get)
                .forEach(::visit)
            visiting.remove(module.name)
            visited += module.name
            result += module
        }
        // the modules in name order, as each one's dependencies above: the values of an immutable map, such as
        // Map.copyOf's, come in an order that each JVM randomizes
        modules.sortedBy { it.name }.forEach(::visit)
        ordered = immutableList(result)
        this.modules = Collections.unmodifiableMap(ordered.associateByTo(LinkedHashMap()) { it.name })
        // in build order, each module's dependencies come before it
        onReplaced =
            immutableSet(
                ordered.fold(setOf<String>()) { found, module ->
                    if (module.name in replaced || module.dependencies.any(found::contains)) found + module.name else found
                },
            )
        // a replaced library, then the module's own XDK, then what a dependency it is built with has, ranked alike: of
        // another XDK, that the module depends on the module whose build has it, as its own build may not
        val library = XdkLibraryReplacement.noEmbedded(replaced)
        gradleOnly =
            Collections.unmodifiableMap(
                ordered.fold(LinkedHashMap<String, NoEmbeddedBuild>()) { found, module ->
                    val inherited =
                        ranked(module.dependencies.sorted().mapNotNull(found::get))?.let { dependency ->
                            dependency.xdk?.noEmbedded(module.name, dependency.owner ?: module.name) ?: dependency
                        }
                    (library.takeIf { module.name in replaced } ?: this.xdks[module.name]?.noEmbedded(module.name) ?: inherited)
                        ?.let { found[module.name] = it }
                    found
                },
            )
    }

    /**
     * Why the server does not build or run [names], those of them that it does not, as the editors say it of a folder and
     * a refused build of them says it: a replaced library's reason, as the whole folder that holds the replacing module
     * has it, before another XDK's, and of each kind the first module's in build order; null when it builds them all.
     */
    fun noEmbedded(names: Collection<String>): NoEmbeddedBuild? =
        ranked(ordered.filter { it.name in names }.mapNotNull { gradleOnly[it.name] })

    private fun ranked(reasons: List<NoEmbeddedBuild>): NoEmbeddedBuild? = reasons.firstOrNull { !it.otherXdk } ?: reasons.firstOrNull()

    /** Canonical source-module inputs used to compare project configurations. */
    private data class Configuration(
        val name: String,
        val uri: String,
        val dependencies: Set<String>,
        val resources: List<String>?,
    )

    fun scope(uri: String): String? = XdkSources.file(uri)?.let(::scope)

    fun scope(file: File): String? =
        modules.values
            .filter {
                file == it.root ||
                    file
                        .toPath()
                        .startsWith(File(it.root.parentFile, it.root.nameWithoutExtension).toPath())
            }.maxByOrNull { it.root.path.length }
            ?.uri

    fun resourceScopes(uri: String): Set<String> {
        val file = XdkSources.file(uri)?.toPath() ?: return emptySet()
        return modules.values
            .filter { module ->
                XdkResources.roots(module.root, module.resourceFiles).any {
                    file.startsWith(it.toPath()) || it.toPath().startsWith(file)
                }
            }.mapTo(linkedSetOf()) { it.uri }
    }

    fun inputWatchRoots(): Set<File> =
        modules.values
            .flatMap {
                XdkResources.roots(it.root, it.resourceFiles) + it.root.parentFile
            }.toSet()

    fun sameConfiguration(other: XdkProject): Boolean = configuration == other.configuration && xdks == other.xdks

    /** Added, removed and changed source inputs, independent of presentation order. */
    fun changedModules(other: XdkProject): Set<String> =
        ((configuration - other.configuration) + (other.configuration - configuration))
            .mapTo(linkedSetOf()) { it.name }

    /**
     * Complete configured source graph, including modules that have never been opened: each module after its
     * dependencies, in the depth-first order that visits the modules, and each one's dependencies, by name.
     */
    fun buildOrder(): List<XdkSourceModule> = ordered

    fun buildOrder(scope: String): List<XdkSourceModule> {
        val target = modules.values.firstOrNull { it.uri == scope } ?: return emptyList()
        val names = mutableSetOf<String>()

        fun include(module: XdkSourceModule) {
            if (names.add(module.name)) {
                module.dependencies.mapNotNull(modules::get).forEach(::include)
            }
        }
        include(target)
        return ordered.filter { it.name in names }
    }

    /** Includes the changed module and all consumers, in dependency order. */
    fun affected(scope: String): Set<String> {
        val changed = modules.values.firstOrNull { it.uri == scope } ?: return setOf(scope)
        val names = mutableSetOf(changed.name)
        return buildSet {
            ordered.forEach { module ->
                if (module.name in names || module.dependencies.any(names::contains)) {
                    names += module.name
                    add(module.uri)
                }
            }
        }
    }

    fun orderedScopes(scopes: Set<String>): Set<String> =
        ordered.map { it.uri }.filterTo(linkedSetOf()) { it in scopes }.apply { addAll(scopes) }
}
