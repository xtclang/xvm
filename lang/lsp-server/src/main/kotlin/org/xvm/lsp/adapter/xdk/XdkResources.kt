package org.xvm.lsp.adapter.xdk

import org.xvm.tool.ResourceDir
import java.io.File
import java.nio.file.FileVisitOption.FOLLOW_LINKS
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.CancellationException
import java.util.List.copyOf as immutableList
import java.util.Map.copyOf as immutableMap

/**
 * Resource lookup uses compiler rules; cache keys retain only immutable filesystem observations.
 */
@ConsistentCopyVisibility
internal data class XdkResources
    private constructor(
        val roots: List<File>,
        val entries: Map<String, String>,
    ) {
        val revision: String =
            fingerprint(
                (roots.map { it.path } + entries.toSortedMap().flatMap { listOf(it.key, it.value) })
                    .joinToString("\u0000")
                    .toByteArray(),
            )

        fun directory(): ResourceDir = ResourceDir(roots.filter(File::exists))

        companion object {
            fun roots(
                root: File,
                configured: List<File>?,
            ): List<File> =
                configured
                    ?: if (root.parentFile.isDirectory) {
                        ResourceDir.forSource(root, true).locations.map { it.canonicalFile }
                    } else {
                        // A moved/deleted module container has no ModuleInfo yet. Retain its source
                        // directory as a missing input until discovery or host configuration replaces
                        // it.
                        listOf(root.parentFile.canonicalFile)
                    }

            fun capture(
                root: File,
                configured: List<File>?,
                source: Collection<String>,
                cancelled: () -> Boolean,
            ): XdkResources {
                val roots = roots(root, configured)
                // Most modules do not use embedded resources. Use the compiler lexer conservatively:
                // an uncertain lex or interpolation takes the full resource snapshot as well.
                val entries =
                    buildMap {
                        if (source.any(XdkLexical::mayUseResources)) {
                            roots.forEach { location ->
                                if (cancelled()) throw CancellationException()
                                if (!location.exists()) {
                                    put(location.path, "missing")
                                } else {
                                    Files.walk(location.toPath(), FOLLOW_LINKS).use { paths ->
                                        paths.forEach { path ->
                                            if (cancelled()) throw CancellationException()
                                            put(path.toString(), entry(path, cancelled))
                                        }
                                    }
                                }
                            }
                        }
                    }
                return XdkResources(immutableList(roots), immutableMap(entries))
            }

            internal fun entry(
                path: Path,
                cancelled: () -> Boolean,
            ): String {
                val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
                val content =
                    if (attrs.isDirectory) {
                        "directory"
                    } else {
                        val hash = MessageDigest.getInstance("SHA-256")
                        Files.newInputStream(path).use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                if (cancelled()) throw CancellationException()
                                val read = input.read(buffer)
                                if (read < 0) break
                                hash.update(buffer, 0, read)
                            }
                        }
                        HexFormat.of().formatHex(hash.digest())
                    }
                return "$content:${attrs.creationTime()}:${attrs.lastModifiedTime()}"
            }

            private fun fingerprint(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        }
    }
