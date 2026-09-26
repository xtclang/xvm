package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.CompositionNode
import org.xvm.compiler.ast.NamedTypeExpression
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

/** Parse source declarations/imports only; no compiler pool, generated binaries or build execution. */
internal object XdkWorkspaceDiscovery {
    private val excluded = setOf(".git", ".gradle", ".idea", ".vscode-test", "build", "out", "node_modules")

    fun scan(
        folders: List<File>,
        overlays: Map<String, String>,
        previous: Collection<XdkSourceModule>,
        cancelled: () -> Boolean,
    ): List<XdkSourceModule> {
        fun checkCurrent() {
            if (cancelled()) throw CancellationException()
        }
        val buffers = overlays.mapNotNull { (uri, text) -> XdkSources.file(uri)?.let { it to text } }.toMap()
        val files =
            folders
                .flatMap { folder ->
                    checkCurrent()
                    if (!folder.isDirectory) {
                        emptyList()
                    } else {
                        folder
                            .walkTopDown()
                            .onEnter { directory ->
                                checkCurrent()
                                directory.name !in excluded && !Files.isSymbolicLink(directory.toPath())
                            }.filter { it.isFile && it.extension == "x" && !Files.isSymbolicLink(it.toPath()) }
                            .map(File::getCanonicalFile)
                            .toList()
                    }
                }.toSet() + buffers.keys.filter { file -> folders.any { file.toPath().startsWith(it.toPath()) } }
        val text =
            files.associateWith { file ->
                checkCurrent()
                buffers[file] ?: file.readText()
            }
        val roots =
            text
                .mapNotNull { (file, content) ->
                    checkCurrent()
                    val name =
                        Parser(Source(content, file.path), ErrorList()).parseModuleNameIgnoreEverythingElse()
                            ?: previous.firstOrNull { it.root == file }?.name
                    name?.takeUnless(XdkLibraries.moduleNames::contains)?.let { file to it }
                }.toMap()
        val names = roots.values.toSet()
        return roots.entries.sortedBy { it.key.path }.map { (root, name) ->
            val members = File(root.parentFile, root.nameWithoutExtension).toPath()
            val imported =
                text
                    .filterKeys { it == root || it.toPath().startsWith(members) }
                    .flatMap { (file, content) ->
                        checkCurrent()
                        val heard = ErrorList()
                        val tree =
                            try {
                                Parser.forPartialAnalysis(Source(content, file.path), heard).parseSource()
                            } catch (_: CompilerException) {
                                null
                            }
                        val imports =
                            tree
                                ?.let(::nodes)
                                .orEmpty()
                                .filterIsInstance<CompositionNode.Import>()
                                .mapNotNull { (it.type as? NamedTypeExpression)?.name }
                        // An incomplete edit must not silently discard the dependency ordering we knew.
                        imports +
                            if (tree == null ||
                                heard.hasSeriousErrors()
                            ) {
                                previous.firstOrNull { it.root == root }?.dependencies.orEmpty()
                            } else {
                                emptySet()
                            }
                    }.filterTo(linkedSetOf()) { it in names && it != name }
            XdkSourceModule(name, root.toURI().toString(), imported)
        }
    }

    private fun nodes(node: AstNode): List<AstNode> = listOf(node) + node.childNodes().flatMap(::nodes)
}
