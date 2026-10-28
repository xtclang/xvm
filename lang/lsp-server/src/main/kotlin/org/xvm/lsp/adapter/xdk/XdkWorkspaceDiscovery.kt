package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.CompositionNode
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.lsp.util.ExecutionTrace
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

/** Parsed headers only: no compiler pools or ASTs survive discovery. */
internal object XdkWorkspaceDiscovery {
    private val excluded =
        setOf(".git", ".gradle", ".idea", ".vscode-test", "build", "out", "node_modules")

    /** Source header and imports discovered without requiring a successful module compilation. */
    data class Header(
        val text: String,
        val module: String?,
        val imports: Set<String>,
        val incomplete: Boolean,
    )

    /**
     * Immutable inventory of discovered source headers. Source changes produce a new catalog so a queued
     * query can retain a consistent view.
     */
    class Catalog(
        private val files: Map<File, Header> = emptyMap(),
        // A file the last events added with exactly the text of a file they removed: a rename or move.
        private val moved: Map<File, File> = emptyMap(),
    ) {
        fun withSource(
            file: File,
            text: String?,
            cancelled: () -> Boolean,
        ): Catalog =
            if (text == null) {
                Catalog(files - file)
            } else {
                Catalog(withoutLeftBuffers(files + (file to header(file, text, files[file], cancelled)), mapOf(file to text)))
            }

        fun modules(previous: Collection<XdkSourceModule>): List<XdkSourceModule> {
            val roots =
                files
                    .mapNotNull { (file, header) ->
                        val name = header.module ?: previousAt(file, previous)?.name
                        name?.takeUnless(XdkLibraries.moduleNames::contains)?.let { file to it }
                    }.toMap()
            val names = roots.values.toSet()
            return roots.entries
                .sortedBy { it.key.path }
                .map { (root, name) ->
                    val members = File(root.parentFile, root.nameWithoutExtension).toPath()
                    val headers =
                        files.filterKeys { it == root || it.toPath().startsWith(members) }.values
                    val imports =
                        headers.flatMap { it.imports } +
                            if (headers.any { it.incomplete }) {
                                previousAt(root, previous)?.dependencies.orEmpty()
                            } else {
                                emptySet()
                            }
                    XdkSourceModule(
                        name,
                        root.toURI().toString(),
                        imports.filterTo(linkedSetOf()) { it in names && it != name },
                    )
                }
        }

        /**
         * The module [previous] had at [file], or at the path [file] was moved from. A header that does not parse
         * keeps the name and imports it had, also after a rename, until it parses again.
         */
        private fun previousAt(
            file: File,
            previous: Collection<XdkSourceModule>,
        ): XdkSourceModule? =
            previous.firstOrNull { it.root == file } ?: moved[file]?.let { from -> previous.firstOrNull { it.root == from } }

        fun rescan(
            folders: List<File>,
            overlays: Map<String, String>,
            cancelled: () -> Boolean,
        ): Catalog {
            val buffers = buffers(overlays)
            val sources =
                folders
                    .flatMap { folder ->
                        checkCurrent(cancelled)
                        if (!folder.isDirectory) emptyList() else walk(folder, cancelled)
                    }.toSet() + buffers.keys.filter { includes(folders, it) }
            return Catalog(
                withoutLeftBuffers(
                    sources.associateWith { file ->
                        checkCurrent(cancelled)
                        header(file, buffers[file] ?: file.readText(), files[file], cancelled)
                    },
                    buffers,
                ),
            )
        }

        /**
         * This catalog after file events at [paths], reading only what they name: a source file is read again,
         * a folder is walked, and a path that is gone takes its sources with it. The result is what [rescan]
         * would find, without walking the workspace; open buffers keep their headers.
         */
        fun withEvents(
            folders: List<File>,
            paths: Collection<File>,
            overlays: Map<String, String>,
            cancelled: () -> Boolean,
        ): Catalog {
            val buffers = buffers(overlays)
            val sources = files.toMutableMap()
            val before = files.toMap()
            for (path in paths) {
                checkCurrent(cancelled)
                sources.keys.removeIf { (it == path || it.toPath().startsWith(path.toPath())) && it !in buffers }
                val found =
                    when {
                        !reachable(folders, path) -> emptyList()
                        Files.isDirectory(path.toPath()) -> walk(path, cancelled)
                        path.isFile && path.extension == "x" && !Files.isSymbolicLink(path.toPath()) -> listOf(path)
                        else -> emptyList()
                    }
                found.forEach { file ->
                    checkCurrent(cancelled)
                    sources[file] = header(file, buffers[file] ?: file.readText(), files[file], cancelled)
                }
            }
            val current = withoutLeftBuffers(sources, buffers)
            val removed = (before.keys - current.keys).groupBy { before.getValue(it).text }
            val moves =
                (current.keys - before.keys)
                    .mapNotNull { file -> removed[current.getValue(file).text]?.singleOrNull()?.let { file to it } }
                    .toMap()
            return Catalog(current, moves)
        }
    }

    /**
     * Drops an open buffer whose file is gone when a file on disk has exactly its text: an editor left open on a file
     * renamed or moved outside the IDE, which would otherwise declare the module a second time. Once its text changes
     * it is a source again, like any unsaved buffer.
     */
    private fun withoutLeftBuffers(
        sources: Map<File, Header>,
        buffers: Map<File, String>,
    ): Map<File, Header> {
        val onDisk = sources.filterKeys { it !in buffers || it.isFile }.values.mapTo(mutableSetOf()) { it.text }
        return sources.filter { (file, header) -> file !in buffers || file.isFile || header.text !in onDisk }
    }

    private fun buffers(overlays: Map<String, String>): Map<File, String> =
        overlays
            .mapNotNull { (uri, text) -> XdkSources.file(uri)?.let { it to text } }
            .toMap()

    /** The source files under [directory] that a workspace walk finds. */
    private fun walk(
        directory: File,
        cancelled: () -> Boolean,
    ): List<File> =
        directory
            .walkTopDown()
            .onEnter {
                checkCurrent(cancelled)
                it.name !in excluded && !Files.isSymbolicLink(it.toPath())
            }.filter {
                it.isFile &&
                    it.extension == "x" &&
                    !Files.isSymbolicLink(it.toPath())
            }.map(File::getCanonicalFile)
            .toList()

    /** Whether a walk of [folders] enters every directory above [path], and so would find what is there. */
    private fun reachable(
        folders: List<File>,
        path: File,
    ): Boolean =
        folders.any { folder ->
            folder.isDirectory &&
                path.toPath().startsWith(folder.toPath()) &&
                generateSequence(path.parentFile) { it.parentFile }
                    .takeWhile { it.toPath().startsWith(folder.toPath()) }
                    .all { it.name !in excluded && !Files.isSymbolicLink(it.toPath()) }
        }

    fun includes(
        folders: List<File>,
        file: File,
    ): Boolean =
        file.extension == "x" &&
            folders.any { folder ->
                file.toPath().startsWith(folder.toPath()) &&
                    folder.toPath().relativize(file.toPath()).none { it.toString() in excluded }
            }

    fun scan(
        folders: List<File>,
        overlays: Map<String, String>,
        previous: Collection<XdkSourceModule>,
        cancelled: () -> Boolean,
    ): List<XdkSourceModule> = Catalog().rescan(folders, overlays, cancelled).modules(previous)

    private fun header(
        file: File,
        text: String,
        previous: Header?,
        cancelled: () -> Boolean,
    ): Header {
        checkCurrent(cancelled)
        if (previous?.text == text) return previous
        val heard = ErrorList()
        val errors = ErrorListener.cancellable(heard, cancelled)
        val name =
            ExecutionTrace.api("Parser.parseModuleName", file.toURI().toString()) {
                Parser(Source(text, file.path), errors).parseModuleNameIgnoreEverythingElse()
            }
        val tree =
            try {
                ExecutionTrace.api(
                    "Parser.parseSource(partial-discovery)",
                    file.toURI().toString(),
                ) {
                    Parser.forPartialAnalysis(Source(text, file.path), errors).parseSource()
                }
            } catch (_: CompilerException) {
                null
            }
        checkCurrent(cancelled)
        val imports =
            tree
                ?.let(::nodes)
                .orEmpty()
                .filterIsInstance<CompositionNode.Import>()
                .mapNotNull { (it.type as? NamedTypeExpression)?.name }
                .toSet()
        return Header(text, name, imports, tree == null || heard.hasSeriousErrors())
    }

    private fun checkCurrent(cancelled: () -> Boolean) {
        if (cancelled()) throw CancellationException()
    }

    private fun nodes(node: AstNode): List<AstNode> = listOf(node) + node.childNodes().flatMap(::nodes)
}
