package org.xtclang.idea.lsp

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Reject ambiguous or occupied destinations before requesting proof and again before applying. */
internal object FileMoveTargets {
    /** A single-source move may also change its basename; batches retain individual names. */
    fun inDirectory(
        sources: List<Path>,
        directory: Path,
        name: String? = null,
    ): Map<Path, Path>? {
        if (name != null && (
                sources.size != 1 || name.isBlank() || name != name.trim() ||
                    name in setOf(".", "..") || name.any { it == '/' || it == '\\' }
            )
        ) {
            return null
        }
        return sources.associateWith { directory.resolve(name ?: it.fileName.toString()) }.takeIf(::valid)
    }

    fun valid(moves: Map<Path, Path>): Boolean =
        moves.isNotEmpty() &&
            moves.values.distinct().size == moves.size &&
            moves.all { (from, to) ->
                from.isAbsolute &&
                    to.isAbsolute &&
                    from == from.normalize() &&
                    to == to.normalize() &&
                    Files.exists(from, NOFOLLOW_LINKS) &&
                    !Files.isSymbolicLink(from) &&
                    !Files.exists(to, NOFOLLOW_LINKS) &&
                    Files.isDirectory(to.parent) &&
                    !to.startsWith(from) &&
                    moves.keys.none(to::startsWith) &&
                    moves.values.none { it != to && to.startsWith(it) }
            }
}
