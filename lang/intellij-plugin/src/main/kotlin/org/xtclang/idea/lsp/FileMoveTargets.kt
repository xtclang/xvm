package org.xtclang.idea.lsp

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Reject ambiguous or occupied destinations before requesting proof and again before applying. */
internal object FileMoveTargets {
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
                    moves.keys.none { it != from && (from.startsWith(it) || to.startsWith(it)) }
            }
}
