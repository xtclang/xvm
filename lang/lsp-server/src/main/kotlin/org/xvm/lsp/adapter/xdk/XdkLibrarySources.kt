package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.ConstantPool
import org.xvm.asm.MethodStructure
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.ModuleConstant
import org.xvm.asm.constants.MultiMethodConstant
import org.xvm.lsp.adapter.ReadOnlyDocument
import org.xvm.lsp.adapter.SymbolMoniker
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Matching distribution sources. Parsed declaration ranges and text survive; compiler objects do
 * not.
 */
internal object XdkLibrarySources {
    private data class Entry(
        val text: String,
        val revision: String,
    )

    private data class SourceFile(
        val module: String,
        val path: String,
        val uri: String,
        val document: ReadOnlyDocument,
        val declarations: List<LibraryDeclaration>,
    ) {
        // Evaluated only on the compiler worker. The cached value contains copied ranges/IDs only.
        val monikers by lazy { declarationMonikers(this) }
    }

    const val SCHEME = "ecstasy-library"

    private val entries by lazy {
        val archives =
            Properties()
                .apply { resource("sources.properties").use { load(it) } }
                .getProperty("archives")
                .split(',')
        buildMap<String, Entry> {
            archives.forEach { archive ->
                val bytes = resource("sources/$archive").use { it.readBytes() }
                val revision =
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                ZipInputStream(bytes.inputStream()).use { zip ->
                    generateSequence { zip.nextEntry }
                        .filter { !it.isDirectory && it.name.endsWith(".x") }
                        .forEach { entry ->
                            val value = Entry(zip.readBytes().toString(Charsets.UTF_8), revision)
                            val previous = put(entry.name, value)
                            check(previous == null || previous.text == value.text) {
                                "Conflicting bundled source ${entry.name}"
                            }
                        }
                }
            }
        }
    }
    private val directory by lazy { Files.createTempDirectory("xtc-library-sources-") }
    private val sources = ConcurrentHashMap<String, SourceFile>()

    private fun source(uri: String): SourceFile? {
        if (sources.isEmpty()) return null
        if (uri.startsWith("$SCHEME:")) return sources.values.find { it.document.uri == uri }
        val canonical = XdkSources.file(uri)?.toURI()?.toString() ?: return null
        return sources.values.find { it.uri == canonical }
    }

    fun owns(uri: String): Boolean = source(uri) != null

    fun document(uri: String): ReadOnlyDocument? = source(uri)?.document

    /** A parsed name is not proof: require one exact artifact declaration at this source range. */
    fun monikers(
        uri: String,
        line: Int,
        column: Int,
    ): List<SymbolMoniker> {
        if (line < 0 || column < 0) return emptyList()
        val at = SemanticModel.Position(line, column)
        return source(uri)
            ?.monikers
            ?.filterKeys { at in it }
            ?.values
            ?.singleOrNull()
            ?.let(::listOf)
            .orEmpty()
    }

    private fun declarationMonikers(source: SourceFile): Map<SemanticModel.Range, SymbolMoniker> {
        val module = XdkLibraries.module(source.module) ?: return emptyMap()
        val symbols = XdkLibraries.symbolIndex(source.module) ?: return emptyMap()
        return ConstantPool.withPool(module.constantPool).use {
            module.constantPool.constants
                .asSequence()
                .toList()
                .asSequence()
                .filterIsInstance<IdentityConstant>()
                .filter { it.moduleConstant.name == source.module }
                .mapNotNull { identity ->
                    val component = identity.component ?: return@mapNotNull null
                    val owner = component as? ClassStructure ?: component.getContainingClass(false)
                    if (owner?.sourcePath?.value != source.path) return@mapNotNull null
                    val target = declaration(identity) ?: return@mapNotNull null
                    val moniker = symbols.moniker(identity.position, imported = false) ?: return@mapNotNull null
                    target.location.range to moniker
                }.groupBy({ it.first }, { it.second })
                .mapNotNull { (range, candidates) -> candidates.distinct().singleOrNull()?.let { range to it } }
                .toList()
                .toMap()
        }
    }

    fun sourceUri(name: String?): String? = name?.takeIf(::owns)

    /** Use the actual artifact identity, never a name search across library modules. */
    fun declaration(identity: IdentityConstant): DependencyDeclaration? {
        val module = identity.moduleConstant?.name ?: return null
        if (module !in XdkLibraries.moduleNames) return null
        val binary =
            XdkLibraries.module(module)?.constantPool?.getConstant(identity) as? IdentityConstant
                ?: return null
        val component = binary.component ?: return null
        val owner =
            component as? ClassStructure ?: component.getContainingClass(false) ?: return null
        val path = owner.sourcePath?.value ?: return null
        val entry = entries[path] ?: return null
        val source = sources.computeIfAbsent(path) { parse(module, path, entry) }
        val namespace =
            generateSequence(binary) { it.namespace }
                .takeWhile { it !is ModuleConstant }
                .filterNot { it is MultiMethodConstant }
                .map { it.name }
                .toList()
                .asReversed()
        val candidates = source.declarations.filter { it.path == namespace }
        val sourceLine = (component as? MethodStructure)?.takeIf { it.sourceText != null }?.sourceLineNumber
        val selected = selectLibraryDeclaration(candidates, sourceLine) { it.firstLine..it.lastLine }
        return selected?.let {
            DependencyDeclaration(
                XdkDependency.SymbolKey(
                    module,
                    "${XdkLibraries.revision(module)}:${entry.revision}",
                    binary.position,
                ),
                SemanticModel.SourceLocation(source.uri, it.range),
            )
        }
    }

    private fun parse(
        module: String,
        path: String,
        entry: Entry,
    ): SourceFile {
        val text = entry.text
        val file = directory.resolve(path).normalize()
        require(file.startsWith(directory)) { "Invalid bundled source path" }
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        check(file.toFile().setReadOnly()) { "Cannot protect bundled source $path" }
        val declarations = libraryDeclarations(text, path)
        val virtualUri = URI(SCHEME, module, "/${XdkLibraries.revision(module)}/${entry.revision}/$path", null, null).toASCIIString()
        return SourceFile(
            module,
            path,
            file
                .toFile()
                .canonicalFile
                .toURI()
                .toString(),
            ReadOnlyDocument(virtualUri, text),
            declarations,
        )
    }

    private fun resource(name: String) =
        checkNotNull(javaClass.getResourceAsStream("/org/xvm/lsp/xdk/$name")) {
            "Bundled XDK source resource is missing: $name"
        }
}

/** A unique namespace suffices; overloaded names require one unambiguous debug-source span. */
internal fun <T> selectLibraryDeclaration(
    candidates: List<T>,
    sourceLine: Int?,
    lines: (T) -> IntRange,
): T? = candidates.singleOrNull() ?: sourceLine?.let { line -> candidates.singleOrNull { line in lines(it) } }
