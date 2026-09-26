package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.ErrorList
import org.xvm.asm.MethodStructure
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.ModuleConstant
import org.xvm.asm.constants.MultiMethodConstant
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.TypeCompositionStatement
import java.nio.file.Files
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/** Matching distribution sources. Parsed declaration ranges and text survive; compiler objects do not. */
internal object XdkLibrarySources {
    private data class Entry(
        val text: String,
        val revision: String,
    )

    private data class Declaration(
        val path: List<String>,
        val range: SemanticModel.Range,
        val firstLine: Int,
        val lastLine: Int,
    )

    private data class SourceFile(
        val uri: String,
        val declarations: List<Declaration>,
    )

    private val entries by lazy {
        val archives = Properties().apply { resource("sources.properties").use { load(it) } }.getProperty("archives").split(',')
        buildMap<String, Entry> {
            archives.forEach { archive ->
                val bytes = resource("sources/$archive").use { it.readBytes() }
                val revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                ZipInputStream(bytes.inputStream()).use { zip ->
                    generateSequence { zip.nextEntry }.filter { !it.isDirectory && it.name.endsWith(".x") }.forEach { entry ->
                        val value = Entry(zip.readBytes().toString(Charsets.UTF_8), revision)
                        val previous = put(entry.name, value)
                        check(previous == null || previous.text == value.text) { "Conflicting bundled source ${entry.name}" }
                    }
                }
            }
        }
    }
    private val directory by lazy { Files.createTempDirectory("xtc-library-sources-") }
    private val sources = ConcurrentHashMap<String, SourceFile>()

    fun owns(uri: String): Boolean {
        if (sources.isEmpty()) return false
        val canonical = XdkSources.file(uri)?.toURI()?.toString() ?: return false
        return sources.values.any { it.uri == canonical }
    }

    fun sourceUri(name: String?): String? = name?.takeIf(::owns)

    /** Use the actual artifact identity, never a name search across library modules. */
    fun declaration(identity: IdentityConstant): DependencyDeclaration? {
        val module = identity.moduleConstant.name
        if (module !in XdkLibraries.moduleNames) return null
        val binary = XdkLibraries.module(module)?.constantPool?.getConstant(identity) as? IdentityConstant ?: return null
        val component = binary.component ?: return null
        val owner = component as? ClassStructure ?: component.getContainingClass(false) ?: return null
        val path = owner.sourcePath?.value ?: return null
        val entry = entries[path] ?: return null
        val source = sources.computeIfAbsent(path) { parse(path, entry.text) }
        val namespace =
            generateSequence(binary) { it.namespace }
                .takeWhile { it !is ModuleConstant }
                .filterNot { it is MultiMethodConstant }
                .map { it.name }
                .toList()
                .asReversed()
        val candidates = source.declarations.filter { it.path == namespace }
        val selected =
            if (component is MethodStructure && candidates.size > 1) {
                // Debug source identifies the selected overload; absent/ambiguous spans provide no target.
                candidates.singleOrNull { component.sourceText != null && component.sourceLineNumber in it.firstLine..it.lastLine }
            } else {
                candidates.singleOrNull()
            }
        return selected?.let {
            DependencyDeclaration(
                XdkDependency.SymbolKey(module, "${XdkLibraries.revision(module)}:${entry.revision}", binary.position),
                SemanticModel.SourceLocation(source.uri, it.range),
            )
        }
    }

    private fun parse(
        path: String,
        text: String,
    ): SourceFile {
        val file = directory.resolve(path).normalize()
        require(file.startsWith(directory)) { "Invalid bundled source path" }
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        check(file.toFile().setReadOnly()) { "Cannot protect bundled source $path" }
        val errors = ErrorList()
        val root =
            try {
                Parser(Source(text), errors).parseSource()
            } catch (_: CompilerException) {
                null
            }
        val declarations =
            buildList {
                fun visit(
                    node: AstNode,
                    parents: List<String>,
                ) {
                    val token =
                        when (node) {
                            is TypeCompositionStatement -> node.nameToken
                            is MethodDeclarationStatement -> node.nameToken
                            is PropertyDeclarationStatement -> node.nameToken
                            else -> null
                        }
                    val module = node is TypeCompositionStatement && node.category.id == Token.Id.MODULE
                    val names = if (token == null || module) parents else parents + token.valueText
                    if (token != null) {
                        fun at(position: Long) = SemanticModel.Position(Source.calculateLine(position), Source.calculateOffset(position))
                        add(
                            Declaration(
                                names,
                                SemanticModel.Range(at(token.startPosition), at(token.endPosition)),
                                Source.calculateLine(node.startPosition),
                                Source.calculateLine(node.endPosition),
                            ),
                        )
                    }
                    node.childNodes().forEach { visit(it, names) }
                }
                if (root != null && !errors.hasSeriousErrors()) {
                    visit(root, path.substringBeforeLast('/', "").split('/').drop(1))
                }
            }
        return SourceFile(
            file
                .toFile()
                .canonicalFile
                .toURI()
                .toString(),
            declarations,
        )
    }

    private fun resource(name: String) =
        checkNotNull(javaClass.getResourceAsStream("/org/xvm/lsp/xdk/$name")) {
            "Bundled XDK source resource is missing: $name"
        }
}
