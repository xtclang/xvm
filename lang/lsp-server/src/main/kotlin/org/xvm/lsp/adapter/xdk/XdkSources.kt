package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorListener
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.lsp.util.ExecutionTrace
import org.xvm.tool.ModuleInfo
import org.xvm.tool.ResourceDir
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.Map.copyOf as immutableMap
import java.util.Set.copyOf as immutableSet

/** One module's immutable text and membership, with editor overlays taking precedence over disk. */
internal class XdkSources
    private constructor(
        private val root: File,
        text: Map<File, String>,
        directories: Set<File>,
        private val aliases: Map<File, String>,
        private val resources: XdkResources,
        private val configuredResourceRoots: List<File>?,
    ) : ModuleInfo(root, moduleName(root, text.getValue(root))) {
        private val text = immutableMap(text)
        private val resourceDirectory = resources.directory()

        /** Cache identity contains only immutable input values, never a parsed ModuleInfo tree. */
        val inputs = Inputs(this.text, immutableSet(directories), immutableMap(aliases), resources)

        data class Inputs(
            val text: Map<File, String>,
            val directories: Set<File>,
            val aliases: Map<File, String>,
            val resources: XdkResources,
        )

        private val entries: Map<File, List<SourceEntry>> =
            buildMap<File, MutableMap<File, SourceEntry>> {
                val boundary = File(root.parentFile, root.nameWithoutExtension)
                for (directory in directories) {
                    getOrPut(directory) { linkedMapOf() }
                    if (directory != boundary) {
                        getOrPut(directory.parentFile) { linkedMapOf() }[directory] =
                            SourceEntry(directory, true)
                    }
                }
                for (file in text.keys.filter { it != root }) {
                    getOrPut(file.parentFile) { linkedMapOf() }[file] = SourceEntry(file, false)
                    var directory = file.parentFile
                    while (directory != boundary) {
                        getOrPut(directory.parentFile) { linkedMapOf() }[directory] =
                            SourceEntry(directory, true)
                        directory = directory.parentFile
                    }
                }
            }.mapValues { (_, files) -> files.values.sortedBy { it.file().name } }

        val sourceUris: Map<String, String> = text.keys.associate { it.path to uri(it) }

        val documentUris: Set<String> = text.keys.mapTo(linkedSetOf(), ::uri)

        fun uri(file: File): String = aliases[file] ?: file.toURI().toString()

        fun resourcesCurrent(cancelled: () -> Boolean): Boolean =
            resources == XdkResources.capture(root, configuredResourceRoots, text.values, cancelled)

        override fun getResourceDir(): ResourceDir = resourceDirectory

        override fun getSourceFile(): File = root

        override fun getSourceDir(): File = root.parentFile

        override fun isSourceTree(): Boolean = entries.isNotEmpty()

        override fun sourceEntries(directory: File): List<SourceEntry> = entries[directory].orEmpty()

        override fun readSource(file: File): CharArray =
            text[file]?.toCharArray()
                ?: throw IOException("Source is absent from this compilation snapshot: $file")

        private data class Overlay(
            val uri: String,
            val text: String,
        )

        companion object {
            private fun moduleName(
                root: File,
                text: String,
            ): String =
                ExecutionTrace.api("Parser.parseModuleName(snapshot)", root.path) {
                    try {
                        Parser(
                            Source(text, root.path),
                            ErrorListener.silent(ErrorListener.Silence.DISCARD),
                        ).parseModuleNameIgnoreEverythingElse()
                    } catch (_: CompilerException) {
                        null
                    } ?: root.nameWithoutExtension
                }

            /** Replay an exact snapshot with proposed edits; never read or write the filesystem. */
            fun replay(
                root: File,
                inputs: Inputs,
                text: Map<String, String>,
                moves: Map<String, String> = emptyMap(),
            ): XdkSources =
                XdkSources(
                    File(moves[root.path] ?: root.path),
                    inputs.text.entries.associate { (file, original) ->
                        val path = moves[file.path] ?: file.path
                        File(path) to (text[path] ?: original)
                    },
                    inputs.directories.mapTo(linkedSetOf()) { File(moves[it.path] ?: it.path) },
                    inputs.aliases.filterKeys { it.path !in moves },
                    inputs.resources,
                    inputs.resources.roots,
                )

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
                resourceRoots: List<File>? = null,
                cancelled: () -> Boolean,
            ): XdkSources {
                val buffers =
                    overlays.entries
                        .mapNotNull { (uri, text) -> file(uri)?.let { it to Overlay(uri, text) } }
                        .toMap()
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
                        buffers[file]?.text ?: file.readText()
                    }
                return XdkSources(
                    root,
                    text,
                    directories,
                    buffers.filterKeys { it in files }.mapValues { it.value.uri },
                    XdkResources.capture(root, resourceRoots, text.values, cancelled),
                    resourceRoots,
                )
            }
        }
    }
