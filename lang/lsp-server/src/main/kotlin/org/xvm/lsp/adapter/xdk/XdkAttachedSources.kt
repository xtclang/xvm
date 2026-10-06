package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.ConstantPool
import org.xvm.asm.MethodStructure
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.ModuleConstant
import org.xvm.asm.constants.MultiMethodConstant
import org.xvm.lsp.adapter.ReadOnlyDocument
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** Navigation-only snapshots: attachments never become writable project modules. */
internal object XdkAttachedSources {
    data class Index(
        val declarations: Map<Int, SemanticModel.SourceLocation>,
        val documents: Map<String, ReadOnlyDocument>,
    )

    private val directory by lazy { Files.createTempDirectory("xtc-attached-sources-") }

    fun read(
        dependency: XdkDependency,
        roots: List<File>,
    ): Index {
        val binary = dependency.open()
        return ConstantPool.withPool(binary.constantPool).use {
            val identities =
                binary.constantPool.constants
                    .toList()
                    .filterIsInstance<IdentityConstant>()
                    .filter { it.moduleConstant?.name == dependency.module }
            val files =
                identities
                    .mapNotNull { identity ->
                        val component = identity.component ?: return@mapNotNull null
                        val owner = component as? ClassStructure ?: component.getContainingClass(false)
                        owner?.sourcePath?.value
                    }.distinct()
                    .associateWith { sourcePath ->
                        val relative = Path.of(sourcePath).normalize()
                        require(
                            !relative.isAbsolute && !relative.startsWith(".."),
                        ) { "Attachment requires a relative artifact source path: $sourcePath" }
                        val source = roots.asSequence().map { it.toPath().resolve(relative).toFile() }.firstOrNull(File::isFile)
                        requireNotNull(source) { "No attached source for ${dependency.module}: $sourcePath" }
                        val text = source.readText()
                        val revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))
                        val snapshot = directory.resolve(dependency.revision).resolve(revision).resolve(relative)
                        Files.createDirectories(snapshot.parent)
                        synchronized(directory) {
                            if (!Files.exists(snapshot)) {
                                Files.writeString(snapshot, text)
                                check(snapshot.toFile().setReadOnly()) { "Cannot protect attached source $sourcePath" }
                            }
                        }
                        val uri =
                            URI(
                                XdkLibrarySources.SCHEME,
                                dependency.module,
                                "/${dependency.revision}/$revision/$sourcePath",
                                null,
                                null,
                            ).toASCIIString()
                        Triple(
                            snapshot
                                .toFile()
                                .canonicalFile
                                .toURI()
                                .toString(),
                            ReadOnlyDocument(uri, text),
                            libraryDeclarations(text, sourcePath),
                        )
                    }
            val declarations =
                identities
                    .mapNotNull { identity ->
                        val component = identity.component ?: return@mapNotNull null
                        val owner = component as? ClassStructure ?: component.getContainingClass(false)
                        val source = files[owner?.sourcePath?.value] ?: return@mapNotNull null
                        val namespace =
                            generateSequence(identity) { it.namespace }
                                .takeWhile { it !is ModuleConstant }
                                .filterNot { it is MultiMethodConstant }
                                .map { it.name }
                                .toList()
                                .asReversed()
                        val method = component as? MethodStructure
                        method?.sourceText?.takeIf(String::isNotBlank)?.let { original ->
                            require(
                                source.second.text.contains(original.trim()),
                            ) { "Attached source does not match ${dependency.module}: ${owner?.sourcePath?.value}" }
                        }
                        val candidates = source.third.filter { it.path == namespace }
                        selectLibraryDeclaration(
                            candidates,
                            method?.takeIf { it.sourceText != null }?.sourceLineNumber,
                        ) { it.firstLine..it.lastLine }
                            ?.let { identity.position to SemanticModel.SourceLocation(source.first, it.range) }
                    }.toMap()
            require(declarations.isNotEmpty()) { "No matching artifact declarations in attached sources for ${dependency.module}" }
            Index(declarations, files.values.associate { it.first to it.second })
        }
    }
}
