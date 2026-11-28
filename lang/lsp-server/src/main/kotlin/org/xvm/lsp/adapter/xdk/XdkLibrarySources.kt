package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component
import org.xvm.asm.ConstantPool
import org.xvm.asm.MethodStructure
import org.xvm.asm.PropertyStructure
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.ModuleConstant
import org.xvm.asm.constants.MultiMethodConstant
import org.xvm.lsp.adapter.ReadOnlyDocument
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.protocol.ServerProperties
import org.xvm.lsp.util.ProcessDirectories
import org.xvm.lsp.util.UserTemporary
import java.io.File
import java.net.URI
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Matching distribution sources. Parsed declaration ranges, doc comments and text survive; compiler
 * objects do not.
 */
internal object XdkLibrarySources {
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

    /** The start of the name of each server process's folder of read-only copies, in the temporary directory. */
    private const val FOLDER_PREFIX = "xtc-library-sources-"

    /** The folder of the read-only copies, deleted when this process exits, or by the next server if it is killed. */
    private val folder = lazy { ProcessDirectories.create(FOLDER_PREFIX) }

    /** The path of [folder], which makes it. */
    private val directory by folder

    private val sources = ConcurrentHashMap<String, SourceFile>()

    /** The read-only files of the bundled sources written so far, by source path. */
    private val copies = ConcurrentHashMap<String, Path>()

    /** The system property that turns off compiling a library source's copy with its whole library. */
    const val COMPILE_LIBRARIES = ServerProperties.COMPILE_LIBRARIES

    /**
     * Whether a library source's read-only copy is compiled with its whole library ([libraryRoot]), which answers for
     * every name in it, or else on its own: `-Dxtc.lsp.compileLibraries=false` turns it off, which the editors' setting
     * gives.
     */
    fun compilesLibraries(): Boolean = System.getProperty(COMPILE_LIBRARIES)?.toBooleanStrictOrNull() ?: true

    /**
     * The least maximum heap with which a library is compiled as a whole: ecstasy's compile holds about 450 MB of live
     * data at its peak, most of it the compiler's TypeInfos, on top of what the server holds, and runs out of 384 MB.
     * It is 1 GB, less the survivor space that some collectors leave out of [Runtime.maxMemory].
     */
    const val LIBRARY_HEAP_BYTES = (1L shl 30) - (1L shl 26)

    /** Whether a JVM whose maximum heap is [maxHeap] can compile a library as a whole; see [LIBRARY_HEAP_BYTES]. */
    fun fitsLibraries(maxHeap: Long = Runtime.getRuntime().maxMemory()): Boolean = maxHeap >= LIBRARY_HEAP_BYTES

    /**
     * The least maximum heap with which a rename is proven in a workspace that compiles ecstasy from its sources, as the
     * XVM repository's does: the proof holds the workspace's compile before the edit and the one after. A rename in
     * copies of the repository's lib_ecstasy and lib_json runs out of 1 GB and is proven in 2 GB.
     */
    const val PROOF_HEAP_BYTES = 2 * LIBRARY_HEAP_BYTES

    /** Whether a JVM whose maximum heap is [maxHeap] can prove a rename over ecstasy's sources; see [PROOF_HEAP_BYTES]. */
    fun fitsProofs(maxHeap: Long = Runtime.getRuntime().maxMemory()): Boolean = maxHeap >= PROOF_HEAP_BYTES

    /** The module files of the libraries whose sources have all been written, by their source paths. */
    private val libraries = ConcurrentHashMap<String, Path>()

    /** A resource that a source names: `$/` from the library's root, or `./` and `../` from the source's folder. */
    private val resourceName = Regex("""(?<![\w.$])#?(\$|\.\.|\.)/(\w[\w./-]*\.\w+)""")

    /** Attached source text and the revision used to detect a changed source file. */
    private data class Entry(
        val text: String,
        val revision: String,
    )

    /** Read-only library source document and its declarations, identified by module and relative path. */
    private data class SourceFile(
        val module: String,
        val path: String,
        val uri: String,
        val document: ReadOnlyDocument,
        val declarations: List<LibraryDeclaration>,
    ) {
        // evaluated only on the compiler worker; the cached value holds copied ranges and text only
        val hovers by lazy { declarationHovers(this) }

        // parsed, with no compiler objects
        val references by lazy { libraryReferences(document.text, path) }
    }

    private fun source(uri: String): SourceFile? {
        if (uri.startsWith("$SCHEME:")) return sources.values.find { it.document.uri == uri }
        // another server's copy names its folder, as this server's copies do, so no other file costs a look at the disk
        if (sources.isEmpty() && FOLDER_PREFIX !in uri) return null
        val canonical = fileOf(uri)?.toURI()?.toString() ?: return null
        return sources.values.find { it.uri == canonical }
    }

    /**
     * True when [uri] is a library source: one that [document] has, or a file in another server process's folder of
     * copies ([ownCopy]), which no compile of this server takes as a module's own source, even when this server has
     * no such source.
     */
    fun owns(uri: String): Boolean = source(uri) != null || (FOLDER_PREFIX in uri && XdkSources.file(uri)?.let(::otherFolder) != null)

    /**
     * True when [uri] is one of the read-only copies, as navigation or a debugger's stack frame opens them, another
     * server process's copy of a source that this server has ([ownCopy]), or a copy's ecstasy-library content
     * ([fileOf]): a library's own source, which belongs to no project, so its compile on its own reports errors that
     * are not its own. [owns] is true of each copy too.
     */
    fun isCopy(uri: String): Boolean {
        if (copies.isEmpty() && FOLDER_PREFIX !in uri) return false
        val file = fileOf(uri)?.toPath() ?: return false
        return copies.values.any { it == file }
    }

    /**
     * The file that [uri] names, or the read-only copy that answers for it: for the ecstasy-library content of a library
     * source, as VS Code shows it, the copy whose text that is, so that the content compiles, and answers, as the copy
     * does; and for another server process's copy, this server's copy of the same source ([ownCopy]).
     */
    fun fileOf(uri: String): File? =
        if (uri.startsWith("$SCHEME:")) {
            source(uri)?.uri?.let { File(URI(it)) }
        } else {
            XdkSources.file(uri)?.let { ownCopy(it) ?: it }
        }

    /** The folder of copies of another server process that holds [file], a canonical file, or null when none does. */
    private fun otherFolder(file: File): Path? {
        if (FOLDER_PREFIX !in file.path) return null
        // this server's folder is made with its first copy
        return ProcessDirectories
            .holder(file.toPath(), FOLDER_PREFIX)
            ?.takeUnless { folder.isInitialized() && it.fileName == directory.fileName }
    }

    /**
     * This server's copy of the bundled source of which [other], a canonical file, is the copy of another server process,
     * or null when [other] is not such a copy, or this server has no such source. An editor that had a copy open opens
     * it again at the server that a restart started, while the server before it deletes its copies as it exits, or the
     * next server does once it was killed: the copy is answered for as this server's own, never as a source of a module
     * whose compile would read the other server's folder while it goes. As with each copy, this server's text is what
     * compiles and answers, also for the copy of a server of another build, whose text can differ.
     */
    private fun ownCopy(other: File): File? {
        val otherFolder = otherFolder(other) ?: return null
        val path = otherFolder.relativize(other.toPath()).joinToString("/")
        val name = path.substringBefore('/').removeSuffix(".x")
        val module = XdkLibraries.moduleNames.find { it.substringBefore('.') == name } ?: return null
        return file(module, path)?.let(::File)
    }

    fun document(uri: String): ReadOnlyDocument? = source(uri)?.document

    /**
     * The most library declarations that Go to Symbol lists for a query, after the workspace's own: ecstasy alone has
     * thousands, of which a two-letter query, as "to", matches more than a thousand; a hundred is more than a list shows
     * at once, while each source of one is written as its read-only copy, and every one goes to the client.
     */
    const val SYMBOL_LIMIT = 100

    /** The shortest query that Go to Symbol finds library declarations for: a letter matches most of them. */
    const val SYMBOL_QUERY = 2

    /** A declaration of a bundled source, as Go to Symbol finds it, by its library and the source's path. */
    private data class Symbol(
        val module: String,
        val path: String,
        val declaration: LibraryDeclaration,
    )

    /**
     * The declarations of every bundled source, but the modules', parsed once, at the first search: some 760 sources, of
     * which ecstasy has most, and no compiler objects. A library's sources are under the folder of its module's simple
     * name.
     */
    private val symbols by lazy {
        val modules = XdkLibraries.moduleNames.associateBy { it.substringBefore('.') }
        entries.flatMap { (path, entry) ->
            val module = modules[path.substringBefore('/').removeSuffix(".x")] ?: return@flatMap emptyList()
            symbolic(libraryDeclarations(entry.text, path)).map { Symbol(module, path, it) }
        }
    }

    /**
     * The bundled libraries' types, methods and properties whose names contain [query], ignoring case, as Go to Symbol
     * finds a name, but those of the libraries [excluded]: the declarations named [query] first, then those whose names
     * start with it, then the rest, each by name and source, at most [limit]. Each is where Go to Definition opens it, in
     * the read-only copy of its source, which this writes for each source it finds a declaration in.
     */
    fun symbols(
        query: String,
        limit: Int,
        excluded: Set<String>,
    ): List<SymbolInfo> =
        symbols
            .filter { it.module !in excluded && it.declaration.name.contains(query, ignoreCase = true) }
            .sortedWith(compareBy({ matchRank(it.declaration.name, query) }, { it.declaration.name }, { it.path }))
            .take(limit)
            .map { (module, path, declaration) ->
                val entry = entries.getValue(path)
                val source = sources.computeIfAbsent(path) { parse(module, path, entry) }
                SymbolInfo.of(declaration.name, declaration.kind, declaration.range.location(source.uri))
            }

    /**
     * What hover shows of the artifact declaration whose name is at this source position, from the library's binary: its
     * declaration, and its doc comment. A parsed name is not proof, so this is null when there is not exactly one.
     */
    fun hover(
        uri: String,
        line: Int,
        column: Int,
    ): String? = declared(uri, line, column)?.value

    /**
     * Where the name at this source position is declared: in [uri], for the name of an artifact declaration, as [hover]
     * finds it; or where the type or package is declared that a name in an import or a type there names, as
     * [libraryTarget] resolves it in the library's binary. Null when neither is found.
     */
    fun definition(
        uri: String,
        line: Int,
        column: Int,
    ): SemanticModel.SourceLocation? {
        declared(uri, line, column)?.let { return SemanticModel.SourceLocation(uri, it.key) }
        if (line < 0 || column < 0) return null
        val source = source(uri) ?: return null
        val at = SemanticModel.Position(line, column)
        val reference = source.references.find { reference -> reference.ranges.any { at in it } } ?: return null
        val module = XdkLibraries.module(source.module) ?: return null
        val imports = source.references.filter { it.import && it.alias != null }
        return ConstantPool.withPool(module.constantPool).use {
            libraryTarget(module, imports, reference, reference.ranges.indexOfFirst { at in it } + 1)
                ?.let { declaration(it.identityConstant) }
                ?.location
        }
    }

    private fun declared(
        uri: String,
        line: Int,
        column: Int,
    ): Map.Entry<SemanticModel.Range, String>? {
        if (line < 0 || column < 0) return null
        val at = SemanticModel.Position(line, column)
        return source(uri)?.hovers?.entries?.singleOrNull { at in it.key }
    }

    private fun declarationHovers(source: SourceFile): Map<SemanticModel.Range, String> =
        declarations(source) { _, component, target ->
            label(component)?.let { "```xtc\n$it\n```" + target.documentation?.let { doc -> "\n\n$doc" }.orEmpty() }
        }

    /** [value] of each artifact declaration in [source], by its name's range, where exactly one is declared. */
    private fun <T : Any> declarations(
        source: SourceFile,
        value: (IdentityConstant, Component, DependencyDeclaration) -> T?,
    ): Map<SemanticModel.Range, T> {
        val module = XdkLibraries.module(source.module) ?: return emptyMap()
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
                    val found = value(identity, component, target) ?: return@mapNotNull null
                    target.location.range to found
                }.groupBy({ it.first }, { it.second })
                .mapNotNull { (range, candidates) -> candidates.distinct().singleOrNull()?.let { range to it } }
                .toList()
                .toMap()
        }
    }

    /** A declaration as hover names it: a type by what it is, a property by its type, a method by its signature. */
    private fun label(component: Component): String? =
        when (component) {
            is ClassStructure -> "${component.format.name.lowercase()} ${component.name}"

            is PropertyStructure -> "${component.type.valueString} ${component.name}"

            is MethodStructure -> {
                val conditional = component.isConditionalReturn
                val returns =
                    component.identityConstant.signature.rawReturns
                        .drop(if (conditional) 1 else 0)
                        .map { it.valueString }
                val result = returns.singleOrNull() ?: if (returns.isEmpty()) "void" else returns.joinToString(prefix = "(", postfix = ")")
                val parameters = component.paramArray.drop(component.typeParamCount).joinToString { "${it.type.valueString} ${it.name}" }
                "${if (conditional) "conditional " else ""}$result ${component.name}($parameters)"
            }

            else -> null
        }

    fun sourceUri(name: String?): String? = name?.takeIf(::owns)

    /**
     * The read-only file with the bundled source of [path] in the library [module], which navigation opens too, or null
     * when [module] is not a bundled library or has no such source. It is parsed as navigation's is, so [owns] is true of
     * it: a library document, where hover and Go to Definition answer from the library, and which nothing edits.
     */
    fun file(
        module: String,
        path: String,
    ): String? {
        if (module !in XdkLibraries.moduleNames) return null
        val entry = entries[path] ?: return null
        sources.computeIfAbsent(path) { parse(module, path, entry) }
        return copy(path, entry).toString()
    }

    /**
     * The 1-based line where the bundled source of [path] in the library [module], as [file] writes it, declares the
     * method [name], as a stack frame names it, `Class.method`; null when [module] is not a bundled library, or the source
     * does not declare exactly one such method.
     */
    fun methodLine(
        module: String,
        path: String,
        name: String,
    ): Int? {
        if (module !in XdkLibraries.moduleNames) return null
        val entry = entries[path] ?: return null
        val names = name.split('.')
        return sources
            .computeIfAbsent(path) { parse(module, path, entry) }
            .declarations
            .singleOrNull { it.path.takeLast(names.size) == names }
            ?.let { it.range.start.line + 1 }
    }

    /**
     * The read-only copy of the module file of the library that the copy [file] is a source of, with every other source
     * of that library written beside it, so that the library compiles as a whole, as its sources' module, and the
     * resources its sources name: the language's implicit imports as the compiler has them, and an empty file for each
     * other, since a library compiled for navigation never runs. Null when [file] is not a copy, or its library has no
     * bundled module file.
     */
    fun libraryRoot(file: File): File? {
        val path = copies.entries.find { it.value == file.toPath() }?.key ?: return null
        val name = path.substringBefore('/').removeSuffix(".x")
        val root = "$name.x"
        val entry = entries[root] ?: return null
        return libraries
            .computeIfAbsent(root) {
                val members = entries.filterKeys { it.startsWith("$name/") }
                members.forEach { (member, source) -> copy(member, source) }
                members.forEach { (member, source) -> resources(name, member, source.text) }
                resources(name, root, entry.text)
                copy(root, entry)
            }.toFile()
    }

    /**
     * Writes each resource that [text], the source at [path] of the library [name], names and that is not there yet.
     * A name in a comment gets its file too, which does no harm, but never one that would be a source of the library.
     */
    private fun resources(
        name: String,
        path: String,
        text: String,
    ) {
        val folder = path.substringBeforeLast('/', "")
        resourceName.findAll(text).forEach { match ->
            val (prefix, relative) = match.destructured
            val base =
                when (prefix) {
                    "$" -> ""
                    "." -> folder
                    else -> folder.substringBeforeLast('/', "")
                }
            val target = directory.resolve(base).resolve(relative).normalize()
            val source = target.startsWith(directory.resolve(name)) && relative.endsWith(".x")
            if (target.startsWith(directory) && !source && Files.notExists(target, NOFOLLOW_LINKS)) {
                val implicit = prefix == "$" && relative == "implicit.x"
                val content =
                    (if (implicit) XdkLibrarySources::class.java.getResourceAsStream("/implicit.x")?.use { it.readBytes() } else null)
                        ?: ByteArray(0)
                try {
                    UserTemporary.current.write(target, content)
                } catch (_: FileAlreadyExistsException) {
                    // another library's resource of that name, written meanwhile, or a link, which nothing is written through
                }
            }
        }
    }

    /** The read-only file with the bundled source of [path], written the first time it is asked for. */
    private fun copy(
        path: String,
        entry: Entry,
    ): Path =
        copies.computeIfAbsent(path) {
            val file = directory.resolve(path).normalize()
            require(file.startsWith(directory)) { "Invalid bundled source path" }
            // in the root as it is now, never through a link
            UserTemporary.current.write(file, entry.text.toByteArray())
            check(file.toFile().setReadOnly()) { "Cannot protect bundled source $path" }
            file.toFile().canonicalFile.toPath()
        }

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
                it.documentation,
                it.parameters,
            )
        }
    }

    private fun parse(
        module: String,
        path: String,
        entry: Entry,
    ): SourceFile {
        val text = entry.text
        val file = copy(path, entry)
        val declarations = libraryDeclarations(text, path)
        val virtualUri = URI(SCHEME, module, "/${XdkLibraries.revision(module)}/${entry.revision}/$path", null, null).toASCIIString()
        return SourceFile(
            module,
            path,
            // as File gives it, which owns() compares with
            file.toFile().toURI().toString(),
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

/** How [name] matches [query], as Go to Symbol ranks library declarations: 0 when it is [query], 1 when it starts with it, else 2. */
internal fun matchRank(
    name: String,
    query: String,
): Int =
    when {
        name.equals(query, ignoreCase = true) -> 0
        name.startsWith(query, ignoreCase = true) -> 1
        else -> 2
    }

/** The location of this range in the document [uri]. */
internal fun SemanticModel.Range.location(uri: String): Location = Location(uri, start.line, start.column, end.line, end.column)
