package org.xvm.lsp.adapter.xdk

import java.io.File
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
    val dependencies: Set<String> = immutableSet(dependencies)
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
        require(name !in XdkLibraries.moduleNames) {
            "Project sources cannot replace bundled compiler libraries"
        }
    }
}

/**
 * Immutable graph. Ordering and reverse edges are established before installing a configuration.
 */
internal class XdkProject(
    modules: List<XdkSourceModule>,
) {
    val modules: Map<String, XdkSourceModule> = immutableMap(modules.associateBy { it.name })

    private data class Configuration(
        val name: String,
        val uri: String,
        val dependencies: Set<String>,
        val resources: List<String>?,
    )

    private val configuration =
        modules.map { Configuration(it.name, it.uri, it.dependencies, it.resourceRoots) }.toSet()
    private val ordered: List<XdkSourceModule>

    init {
        require(this.modules.size == modules.size) { "Duplicate source module name" }
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
                .mapNotNull(this.modules::get)
                .forEach(::visit)
            visiting.remove(module.name)
            visited += module.name
            result += module
        }
        modules.forEach(::visit)
        ordered = result.toList()
    }

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

    fun sameConfiguration(other: XdkProject): Boolean = configuration == other.configuration

    /** Added, removed and changed source inputs, independent of presentation order. */
    fun changedModules(other: XdkProject): Set<String> =
        ((configuration - other.configuration) + (other.configuration - configuration))
            .mapTo(linkedSetOf()) { it.name }

    /** Complete configured source graph, including modules that have never been opened. */
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
