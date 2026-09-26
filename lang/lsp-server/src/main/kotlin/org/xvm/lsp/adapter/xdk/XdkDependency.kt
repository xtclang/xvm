package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.FileStructure
import org.xvm.asm.ModuleRepository
import org.xvm.asm.constants.IdentityConstant
import org.xvm.compiler.BuildRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Map.copyOf as immutableMap

/**
 * An immutable artifact and its matching source index. Holds no compiler objects. The revision
 * covers both bytes and locations; constant-table indices are meaningful only within that revision.
 * Source text/files remain host-owned and must be replaced together with the artifact.
 */
class XdkDependency private constructor(
    val module: String,
    private val artifact: ByteArray,
    declarations: Map<Int, SemanticModel.SourceLocation>,
) {
    val declarations: Map<Int, SemanticModel.SourceLocation> = immutableMap(declarations)
    val revision: String =
        ByteArrayOutputStream()
            .also { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeInt(artifact.size)
                    output.write(artifact)
                    declarations.toSortedMap().forEach { (index, location) ->
                        output.writeInt(index)
                        val source = requireNotNull(location.sourceName).toByteArray(Charsets.UTF_8)
                        output.writeInt(source.size)
                        output.write(source)
                        output.writeInt(location.range.start.line)
                        output.writeInt(location.range.start.column)
                        output.writeInt(location.range.end.line)
                        output.writeInt(location.range.end.column)
                    }
                }
            }.toByteArray()
            .let { HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(it)) }

    data class SymbolKey(
        val module: String,
        val revision: String,
        val index: Int,
    )

    /** Returns a defensive copy; a host cannot mutate a queued request's dependency bytes. */
    fun bytes(): ByteArray = artifact.clone()

    /** A fresh compiler-owned tree for each attempt; no mutable repository survives publication. */
    internal fun open(): FileStructure = FileStructure(ByteArrayInputStream(artifact))

    companion object {
        /** Binary-only libraries have identities and types, but deliberately no source locations. */
        fun fromBinary(bytes: ByteArray): XdkDependency {
            val owned = bytes.clone()
            val file = FileStructure(ByteArrayInputStream(owned))
            return XdkDependency(file.module.name, owned, emptyMap())
        }

        internal fun capture(
            compilation: EmbeddingSupport.Compilation,
            locations: Map<IdentityConstant, SemanticModel.SourceLocation>,
        ): XdkDependency {
            val artifact = ByteArrayOutputStream().also { compilation.file().writeTo(it) }.toByteArray()
            // Serialization establishes the artifact's constant indices. Resolve against its pool,
            // since emission may have removed or re-registered constants from the source attempt.
            val file = FileStructure(ByteArrayInputStream(artifact))
            val declarations =
                locations.entries
                    .mapNotNull { (identity, location) ->
                        file.constantPool
                            .getConstant(identity)
                            ?.position
                            ?.let { it to location }
                    }.toMap()
            return XdkDependency(file.module.name, artifact, declarations)
        }
    }
}

/** Worker-only join; compiler equality handles overloads and foreign pools, never display strings. */
internal data class DependencyDeclaration(
    val key: XdkDependency.SymbolKey,
    val location: SemanticModel.SourceLocation,
)

internal class XdkDependencies(
    dependencies: List<XdkDependency>,
) {
    val modules = immutableMap(dependencies.associateBy { it.module })

    init {
        require(modules.size == dependencies.size) { "Duplicate dependency module" }
        require(modules.keys.none { it in XdkLibraries.moduleNames }) {
            "Project dependencies cannot replace the bundled compiler libraries"
        }
    }

    /** Open only on the serialized compiler worker. Both results die with the attempt. */
    fun open(): Open {
        val files = modules.mapValues { it.value.open() }
        val repository = BuildRepository().apply { files.values.forEach { storeModule(it.module) } }
        val declarations =
            buildMap {
                files.forEach { (name, file) ->
                    val dependency = modules.getValue(name)
                    dependency.declarations.forEach { (index, location) ->
                        val identity = file.constantPool.getConstant(index) as? IdentityConstant
                        check(identity != null && identity.moduleConstant.name == name) { "Invalid dependency source index" }
                        put(identity, DependencyDeclaration(XdkDependency.SymbolKey(name, dependency.revision, index), location))
                    }
                }
            }
        return Open(repository, declarations)
    }

    class Open(
        val repository: ModuleRepository,
        val declarations: Map<IdentityConstant, DependencyDeclaration>,
    )
}
