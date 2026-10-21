package org.xvm.lsp.adapter.xdk

import java.io.File

/**
 * Consumers of a source graph found under folders the host chose, for the host to add to its explicit graph.
 * Nothing is installed: an explicit graph stays the only manifest, so a rename receipt still names every
 * module it checked.
 */
internal object XdkConsumerDiscovery {
    /**
     * The modules under the searched folders that import the graph, directly or through each other, and the
     * modules there whose name a graph module or another found module already uses.
     */
    data class Result(
        val modules: List<XdkSourceModule>,
        val conflicts: List<XdkSourceModule>,
    )

    fun discover(
        roots: List<File>,
        graph: Collection<XdkSourceModule>,
        overlays: Map<String, String>,
        cancelled: () -> Boolean,
    ): Result {
        val folders = roots.map(File::getCanonicalFile)
        // The graph's own roots join the catalog only so that imports of their names count as dependencies.
        val catalog =
            graph.fold(XdkWorkspaceDiscovery.Catalog().rescan(folders, overlays, cancelled)) { catalog, module ->
                val text = overlays[module.uri] ?: module.root.takeIf(File::isFile)?.readText()
                catalog.withSource(module.root.canonicalFile, text, cancelled)
            }
        val names = graph.mapTo(mutableSetOf()) { it.name }
        val inGraph = graph.mapTo(mutableSetOf()) { it.root.canonicalFile }
        val found =
            catalog
                .modules(graph)
                .filter { module -> module.root !in inGraph && folders.any { module.root.toPath().startsWith(it.toPath()) } }
        val repeated =
            found
                .groupingBy { it.name }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        val (conflicts, candidates) = found.partition { it.name in names || it.name in repeated }
        val consumers = mutableSetOf<String>()
        while (true) {
            val next =
                candidates.filter { it.name !in consumers && it.dependencies.any { name -> name in names || name in consumers } }
            if (next.isEmpty()) break
            next.mapTo(consumers) { it.name }
        }
        return Result(candidates.filter { it.name in consumers }.sortedBy { it.name }, conflicts.sortedBy { it.name })
    }
}
