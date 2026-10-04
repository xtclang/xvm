package org.xvm.lsp.adapter.xdk

import java.io.File

/** Simultaneous final destinations, expressed as child-first filesystem operations. */
internal object XdkMoveOperations {
    fun ordered(moves: Map<File, File>): Map<File, File>? {
        // A child following its parent's exact mapping requires no second filesystem operation.
        val minimal = moves.filter { (source, destination) ->
            moves.none { (parent, target) ->
                parent != source && source.toPath().startsWith(parent.toPath()) &&
                    target.toPath().resolve(parent.toPath().relativize(source.toPath())) == destination.toPath()
            }
        }
        if (minimal.values.distinct().size != minimal.size || minimal.any { (source, destination) ->
                // Incoming paths under a moving source require a temporary staging transaction,
                // which neither standard client resource edits nor our VFS command can promise.
                minimal.keys.any { destination.toPath().startsWith(it.toPath()) } ||
                    minimal.any { (other, target) -> other != source && destination.toPath().startsWith(target.toPath()) }
            }) return null
        return minimal.entries.sortedWith(compareByDescending<Map.Entry<File, File>> { it.key.toPath().nameCount }.thenBy { it.key.path })
            .associate { it.toPair() }
    }
}
