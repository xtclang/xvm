package org.xvm.lsp.adapter.xdk

import java.io.File
import java.nio.file.FileVisitOption.FOLLOW_LINKS
import java.nio.file.Files
import java.nio.file.Path

/**
 * A delayed watcher event may describe inputs already compiled. Compare against that snapshot, not
 * against an observation taken when the notification arrives. This uses no compiler APIs. Bound
 * notification-thread I/O; uncertain or larger changes take the normal invalidation path.
 */
internal object XdkFileChanges {
    private const val MAX_ENTRIES = 512L
    private const val MAX_BYTES = 1_000_000L

    fun unchanged(
        file: File,
        root: File,
        inputs: XdkSources.Inputs,
        overlays: Map<File, String> = emptyMap(),
    ): Boolean = runCatching {
        val path = file.toPath()
        val member = root.parentFile.resolve(root.nameWithoutExtension).toPath()
        val sourceRoots = listOf(root.toPath(), member).filter { it.overlaps(path) }
        val sourceEntries =
            sourceRoots
                .flatMap { entries(if (it.startsWith(path)) it else path) }
                .filter { Files.isDirectory(it) || it.toString().endsWith(".x") }
                .toSet()
        val expectedSources =
            (inputs.text.keys + inputs.directories)
                .map { it.toPath() }
                .filter { it.startsWith(path) }
                .toSet()
        if (sourceEntries != expectedSources || !bounded(sourceEntries)) return@runCatching false
        if (
            sourceEntries.any {
                Files.isRegularFile(it) &&
                    (overlays[it.toFile()] ?: Files.readString(it)) != inputs.text[it.toFile()]
            }
        )
            return@runCatching false

        if (inputs.resources.entries.isEmpty()) return@runCatching true
        val resourceRoots = inputs.resources.roots.map { it.toPath() }.filter { it.overlaps(path) }
        val currentResources =
            resourceRoots.flatMap { entries(if (it.startsWith(path)) it else path) }.toSet()
        val expectedResources = inputs.resources.entries.filterKeys { Path.of(it).startsWith(path) }
        if (!bounded(currentResources)) return@runCatching false
        val current =
            currentResources.associate { it.toString() to XdkResources.entry(it) { false } } +
                resourceRoots.filterNot(Files::exists).associate { it.toString() to "missing" }
        current == expectedResources
    }
        .getOrDefault(false)

    private fun Path.overlaps(other: Path) = startsWith(other) || other.startsWith(this)

    private fun entries(root: Path): List<Path> =
        if (!Files.exists(root)) emptyList()
        else
            Files.walk(root, FOLLOW_LINKS).use { paths ->
                paths.limit(MAX_ENTRIES + 1).toList().also { require(it.size <= MAX_ENTRIES) }
            }

    private fun bounded(paths: Set<Path>): Boolean =
        paths.size <= MAX_ENTRIES &&
            paths.asSequence().filter(Files::isRegularFile).sumOf(Files::size) <= MAX_BYTES
}
