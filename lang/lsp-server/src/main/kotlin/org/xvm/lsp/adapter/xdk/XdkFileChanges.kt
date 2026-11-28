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
    ): Boolean =
        runCatching {
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
                        (overlays[it.toFile()] ?: XdkSources.read(it.toFile())) != inputs.text[it.toFile()]
                }
            ) {
                return@runCatching false
            }

            if (inputs.resources.entries.isEmpty()) return@runCatching true
            val resourceRoots =
                inputs.resources.roots
                    .map { it.toPath() }
                    .filter { it.overlaps(path) }
            val currentResources =
                resourceRoots.flatMap { entries(if (it.startsWith(path)) it else path) }.toSet()
            val expectedResources = inputs.resources.entries.filterKeys { Path.of(it).startsWith(path) }
            if (!bounded(currentResources)) return@runCatching false
            val current =
                currentResources.associate { it.toString() to XdkResources.entry(it) { false } } +
                    resourceRoots.filterNot(Files::exists).associate { it.toString() to "missing" }
            current == expectedResources
        }.getOrDefault(false)

    /** What one workspace pull's [changedSources] may read in all: [MAX_ENTRIES] entries and [MAX_BYTES] bytes. */
    class ReadBudget {
        var entries = MAX_ENTRIES
        var bytes = MAX_BYTES
    }

    /**
     * Which of the module [root]'s sources are certainly not those of [inputs], which its analysis read, with [overlays],
     * the open documents' texts: [root], its member folder, or both, as a source made, deleted or written since; none when
     * they are those. Listed and read as XdkSources.capture lists and reads them ([XdkSources.sourceFiles]), so that an
     * analysis that read them is never found changed. Null when it can't tell, as when the read would take more than is
     * left of [budget], which it takes what it read from, or a file goes while it is read.
     */
    fun changedSources(
        root: File,
        inputs: XdkSources.Inputs,
        overlays: Map<File, String>,
        budget: ReadBudget,
    ): List<File>? =
        runCatching {
            val (files, directories) = XdkSources.sourceFiles(root, overlays.keys) { --budget.entries < 0 }
            budget.bytes -= files.filter { it !in overlays }.sumOf(File::length)
            if (budget.bytes < 0) return@runCatching null

            fun differs(file: File) = (overlays[file] ?: XdkSources.read(file)) != inputs.text[file]
            val members = files - root
            listOfNotNull(
                root.takeIf { differs(root) },
                root.parentFile.resolve(root.nameWithoutExtension).takeIf {
                    members != inputs.text.keys - root || directories != inputs.directories || members.any(::differs)
                },
            )
        }.getOrNull()

    private fun Path.overlaps(other: Path) = startsWith(other) || other.startsWith(this)

    private fun entries(root: Path): List<Path> =
        if (!Files.exists(root)) {
            emptyList()
        } else {
            Files.walk(root, FOLLOW_LINKS).use { paths ->
                paths.limit(MAX_ENTRIES + 1).toList().also { require(it.size <= MAX_ENTRIES) }
            }
        }

    private fun bounded(paths: Set<Path>): Boolean =
        paths.size <= MAX_ENTRIES &&
            paths.asSequence().filter(Files::isRegularFile).sumOf(Files::size) <= MAX_BYTES
}
