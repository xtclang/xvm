package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.CompositionNode
import org.xvm.compiler.ast.NamedTypeExpression
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

/** Parsed headers only: no compiler pools or ASTs survive discovery. */
internal object XdkWorkspaceDiscovery {
    private val excluded = setOf(".git", ".gradle", ".idea", ".vscode-test", "build", "out", "node_modules")

    data class Header(
        val text: String,
        val module: String?,
        val imports: Set<String>,
        val incomplete: Boolean,
    )

    class Catalog(
        private val files: Map<File, Header> = emptyMap(),
    ) {
        fun withSource(
            file: File,
            text: String?,
            cancelled: () -> Boolean,
        ): Catalog = if (text == null) Catalog(files - file) else Catalog(files + (file to header(file, text, files[file], cancelled)))

        fun modules(previous: Collection<XdkSourceModule>): List<XdkSourceModule> {
            val roots =
                files
                    .mapNotNull { (file, header) ->
                        val name = header.module ?: previous.firstOrNull { it.root == file }?.name
                        name?.takeUnless(XdkLibraries.moduleNames::contains)?.let { file to it }
                    }.toMap()
            val names = roots.values.toSet()
            return roots.entries.sortedBy { it.key.path }.map { (root, name) ->
                val members = File(root.parentFile, root.nameWithoutExtension).toPath()
                val headers = files.filterKeys { it == root || it.toPath().startsWith(members) }.values
                val imports =
                    headers.flatMap { it.imports } +
                        if (headers.any { it.incomplete }) previous.firstOrNull { it.root == root }?.dependencies.orEmpty() else emptySet()
                XdkSourceModule(name, root.toURI().toString(), imports.filterTo(linkedSetOf()) { it in names && it != name })
            }
        }

        fun rescan(
            folders: List<File>,
            overlays: Map<String, String>,
            cancelled: () -> Boolean,
        ): Catalog {
            val buffers = overlays.mapNotNull { (uri, text) -> XdkSources.file(uri)?.let { it to text } }.toMap()
            val sources =
                folders
                    .flatMap { folder ->
                        checkCurrent(cancelled)
                        if (!folder.isDirectory) {
                            emptyList()
                        } else {
                            folder
                                .walkTopDown()
                                .onEnter {
                                    checkCurrent(cancelled)
                                    it.name !in excluded && !Files.isSymbolicLink(it.toPath())
                                }.filter { it.isFile && it.extension == "x" && !Files.isSymbolicLink(it.toPath()) }
                                .map(File::getCanonicalFile)
                                .toList()
                        }
                    }.toSet() + buffers.keys.filter { includes(folders, it) }
            return Catalog(
                sources.associateWith { file ->
                    checkCurrent(cancelled)
                    header(file, buffers[file] ?: file.readText(), files[file], cancelled)
                },
            )
        }
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
        val name = Parser(Source(text, file.path), errors).parseModuleNameIgnoreEverythingElse()
        val tree =
            try {
                Parser.forPartialAnalysis(Source(text, file.path), errors).parseSource()
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
