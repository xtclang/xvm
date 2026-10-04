package org.xvm.lsp.adapter.xdk

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException

/** Membership and bytes of moved inputs outside a module's existing resource roots. */
internal class XdkMoveInputs private constructor(
    private val roots: Set<File>,
    val entries: Map<String, String>,
) {
    fun isCurrent(cancelled: () -> Boolean): Boolean = capture(roots, cancelled)?.entries == entries

    companion object {
        fun capture(
            roots: Set<File>,
            cancelled: () -> Boolean,
        ): XdkMoveInputs? {
            val minimal = roots.filterTo(linkedSetOf()) { source -> roots.none { it != source && source.toPath().startsWith(it.toPath()) } }
            return try {
                val entries =
                    buildMap {
                        minimal.forEach { root ->
                            Files.walk(root.toPath()).use { paths ->
                                paths.forEach { path ->
                                    if (cancelled()) throw CancellationException()
                                    if (Files.isSymbolicLink(path)) throw IOException("Moved input contains a symbolic link")
                                    put(path.toString(), XdkResources.entry(path, cancelled))
                                }
                            }
                        }
                    }
                XdkMoveInputs(minimal, entries)
            } catch (_: IOException) {
                null
            }
        }
    }
}
