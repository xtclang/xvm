package org.xvm.lsp.adapter.xdk

import org.xvm.tool.ResourceDir
import java.io.File

/** Read proposed resource paths through original files, without moving anything during proof. */
internal class XdkResourceMoves(
    private val moves: Map<String, String>,
    private val changedSources: Set<String>,
) {
    class UnprovenResource(
        reason: String,
    ) : RuntimeException(reason)

    fun path(file: File): File =
        moves.entries.firstOrNull { file.toPath().startsWith(File(it.key).toPath()) }?.let {
            File(it.value).toPath().resolve(File(it.key).toPath().relativize(file.toPath())).toFile()
        } ?: file

    fun directory(resources: XdkResources): ResourceDir {
        if (resources.entries.isEmpty()) return resources.directory()
        // A moved root has its entire old contents captured. An incoming subtree from outside
        // those roots does not: approving its old, empty destination would miss new resources.
        if (moves.any { (from, to) ->
                from !in resources.entries &&
                    resources.roots.any { root ->
                        val destination = File(to).toPath()
                        val proposedRoot = path(root).toPath()
                        !root.toPath().startsWith(from) &&
                            (destination.startsWith(proposedRoot) || proposedRoot.startsWith(destination))
                    }
            }
        ) {
            throw UnprovenResource("The move introduces resources outside the captured roots")
        }
        val entries =
            resources.entries
                .filterValues { it != "missing" }
                .keys
                .map(::File)
                .associate { path(it) to it }
        return Directory(null, "", resources.roots.map(::path), entries)
    }

    private inner class Directory(
        parent: ResourceDir?,
        name: String,
        private val roots: List<File>,
        private val entries: Map<File, File>,
    ) : ResourceDir(parent, name, roots.mapNotNull(entries::get)) {
        override fun getNames(): Set<String> =
            roots
                .flatMap { root ->
                    entries.keys.filter { it.parentFile == root || (it == root && entries[it]?.isFile == true) }
                }.map { it.name }
                .toSortedSet(String.CASE_INSENSITIVE_ORDER)

        override fun getByName(name: String): Any? {
            val children =
                roots.mapNotNull { root ->
                    val child = if (entries[root]?.isFile == true && root.name.equals(name, ignoreCase = true)) root else File(root, name)
                    entries[child]?.let { child to it }
                }
            val first = children.firstOrNull() ?: return null
            if (!first.second.isDirectory) {
                if (first.second.path in
                    changedSources
                ) {
                    throw UnprovenResource("A source edited by this move is also embedded as a resource")
                }
                // FileExpression reads the original bytes, but embeds the proposed basename.
                return object : File(first.second.path) {
                    override fun getName(): String = name
                }
            }
            return Directory(this, name, children.filter { it.second.isDirectory }.map { it.first }, entries)
        }
    }
}
