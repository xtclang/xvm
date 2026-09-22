package org.xvm.lsp.adapter.xdk

import org.xvm.tool.ModuleInfo
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.Map.copyOf as immutableMap

/** One module's immutable text and membership, with editor overlays taking precedence over disk. */
internal class XdkSources private constructor(
    private val root: File,
    text: Map<File, String>,
    directories: Set<File>,
    private val aliases: Map<File, String>,
) : ModuleInfo(root, false) {
    private val text = immutableMap(text)
    private val entries: Map<File, List<SourceEntry>> =
        buildMap<File, MutableMap<File, SourceEntry>> {
            val boundary = File(root.parentFile, root.nameWithoutExtension)
            for (directory in directories) {
                getOrPut(directory) { linkedMapOf() }
                if (directory != boundary) {
                    getOrPut(directory.parentFile) { linkedMapOf() }[directory] = SourceEntry(directory, true)
                }
            }
            for (file in text.keys.filter { it != root }) {
                getOrPut(file.parentFile) { linkedMapOf() }[file] = SourceEntry(file, false)
                var directory = file.parentFile
                while (directory != boundary) {
                    getOrPut(directory.parentFile) { linkedMapOf() }[directory] = SourceEntry(directory, true)
                    directory = directory.parentFile
                }
            }
        }.mapValues { (_, files) -> files.values.sortedBy { it.file().name } }

    val sourceUris: Map<String, String> = text.keys.associate { it.path to uri(it) }

    val documentUris: Set<String> = text.keys.mapTo(linkedSetOf(), ::uri)

    fun uri(file: File): String = aliases[file] ?: file.toURI().toString()

    override fun getSourceFile(): File = root

    override fun getSourceDir(): File = root.parentFile

    override fun isSourceTree(): Boolean = entries.isNotEmpty()

    override fun sourceEntries(directory: File): List<SourceEntry> = entries[directory].orEmpty()

    override fun readSource(file: File): CharArray =
        text[file]?.toCharArray() ?: throw IOException("Source is absent from this compilation snapshot: $file")

    companion object {
        /** Canonical paths join editor URIs, compiler source names and filesystem notifications. */
        fun file(name: String): File? =
            runCatching {
                val uri = URI(name)
                when {
                    uri.scheme == "file" -> File(uri).canonicalFile
                    uri.scheme == null && File(name).isAbsolute -> File(name).canonicalFile
                    else -> null
                }
            }.getOrNull()

        fun moduleRoot(
            uri: String,
            overlays: Map<String, String>,
        ): File? {
            val file = file(uri) ?: return null
            val openFiles = overlays.keys.mapNotNull(::file).toSet()
            var root = file
            var directory = file.parentFile
            while (directory?.parentFile != null) {
                val candidate = File(directory.parentFile, directory.name + ".x")
                if (candidate.isFile || candidate in openFiles) root = candidate
                directory = directory.parentFile
            }
            return root
        }

        /** Runs on the compiler worker. No source or temporary file is written by an overlay. */
        fun capture(
            root: File,
            overlays: Map<String, String>,
            cancelled: () -> Boolean,
        ): XdkSources {
            val buffers = overlays.entries.mapNotNull { (uri, text) -> file(uri)?.let { it to (uri to text) } }.toMap()
            val directory = File(root.parentFile, root.nameWithoutExtension).toPath()
            val files = linkedSetOf(root)
            val directories = linkedSetOf<File>()
            if (Files.isDirectory(directory)) {
                Files.walk(directory).use { paths ->
                    paths.forEach {
                        if (cancelled()) throw CancellationException()
                        val file = it.toFile().canonicalFile
                        if (file.toPath().startsWith(directory)) {
                            if (Files.isDirectory(it)) {
                                directories += file
                            } else if (Files.isRegularFile(it) && file.extension == "x") {
                                files += file
                            }
                        }
                    }
                }
            }
            files += buffers.keys.filter { it.toPath().startsWith(directory) }
            val text =
                files.associateWith { file ->
                    if (cancelled()) throw CancellationException()
                    buffers[file]?.second ?: file.readText()
                }
            return XdkSources(root, text, directories, buffers.mapValues { it.value.first })
        }
    }
}
