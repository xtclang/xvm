package org.xvm.lsp.adapter.xdk

import java.io.File
import java.nio.file.Files

/** Separate compiler source remapping from the minimal, ordered client resource operations. */
internal class XdkSourceMoves(
    val paths: Map<String, String> = emptyMap(),
    val resources: Map<String, String> = emptyMap(),
) {
    companion object {
        fun directory(
            source: File,
            name: String,
            texts: Map<String, String>,
            directories: Set<File>,
        ): XdkSourceMoves? {
            if (source !in directories || !source.isDirectory) return null
            val destination = File(source.parentFile, name)
            if (
                destination.exists() ||
                    destination in directories ||
                    File(source.parentFile, "$name.x").exists()
            )
                return null
            if (source.walkTopDown().any { Files.isSymbolicLink(it.toPath()) }) return null
            val prefix = source.toPath()
            val entries = texts.keys.map(::File) + directories
            val paths =
                entries
                    .filter { it.toPath().startsWith(prefix) }
                    .associate {
                        it.path to
                            destination.toPath().resolve(prefix.relativize(it.toPath())).toString()
                    }
            return XdkSourceMoves(paths, mapOf(source.path to destination.path))
        }

        fun plan(
            source: File,
            name: String,
            texts: Map<String, String>,
            directories: Set<File>,
        ): XdkSourceMoves? {
            val destination = File(source.parentFile, "$name.x")
            val companion = File(source.parentFile, source.nameWithoutExtension)
            val movedCompanion = File(source.parentFile, name)
            if (
                destination.exists() ||
                    destination.path in texts ||
                    movedCompanion.exists() ||
                    movedCompanion in directories
            )
                return null
            // Symlinks may escape the captured tree; a rename proof cannot establish their
            // membership.
            if (
                Files.isSymbolicLink(source.toPath()) ||
                    (companion.exists() &&
                        companion.walkTopDown().any { Files.isSymbolicLink(it.toPath()) })
            ) {
                return null
            }
            val prefix = companion.toPath()
            val members = texts.keys.map(::File).filter { it.toPath().startsWith(prefix) }
            val descendants = directories.filter { it.toPath().startsWith(prefix) }
            val paths = buildMap {
                put(source.path, destination.path)
                (members + descendants).forEach { file ->
                    put(
                        file.path,
                        movedCompanion
                            .toPath()
                            .resolve(prefix.relativize(file.toPath()))
                            .toString(),
                    )
                }
            }
            val resources = buildMap {
                put(source.path, destination.path)
                // A virtual companion with only unsaved files has no directory to rename,
                // so the client cannot apply this resource operation safely.
                if (companion.isDirectory) {
                    put(companion.path, movedCompanion.path)
                } else if (members.isNotEmpty()) {
                    return null
                }
            }
            return XdkSourceMoves(paths, resources)
        }
    }
}
