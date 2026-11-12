package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ConstantPool
import org.xvm.asm.Constants.Access
import org.xvm.asm.FileStructure
import org.xvm.asm.MethodStructure
import org.xvm.asm.ModuleRepository
import org.xvm.asm.PropertyStructure
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodConstant
import org.xvm.compiler.BuildRepository
import org.xvm.lsp.adapter.ReadOnlyDocument
import org.xvm.lsp.util.ExecutionTrace
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.HexFormat
import java.util.Map.copyOf as immutableMap

/**
 * An immutable artifact and its matching source index. Holds no compiler objects. The revision
 * covers both bytes and locations; constant-table indices are meaningful only within that revision.
 * Source text/files remain host-owned and must be replaced together with the artifact.
 */
class XdkDependency
    private constructor(
        val module: String,
        private val artifact: ByteArray,
        declarations: Map<Int, SemanticModel.SourceLocation>,
        documents: Map<String, ReadOnlyDocument> = emptyMap(),
        documentation: Map<Int, String> = emptyMap(),
        /** Emitted by the server's compiler from a source module, not read from a binary on disk. */
        internal val fromSource: Boolean = false,
    ) {
        /** By URI, in URI order, as the server's info lists them; a Map.copyOf's order changes with each JVM. */
        internal val documents: Map<String, ReadOnlyDocument> = Collections.unmodifiableMap(documents.toSortedMap())

        /**
         * The doc comments of attached sources by constant index, which the artifact does not keep. The revision covers
         * them through the locations, whose source names carry the source text's revision.
         */
        internal val documentation: Map<Int, String> = immutableMap(documentation)

        internal fun withSources(roots: List<File>): XdkDependency {
            val index = XdkAttachedSources.read(this, roots)
            return XdkDependency(module, artifact, index.declarations, index.documents, index.documentation, fromSource)
        }

        internal val symbolIndex: XdkArtifactSymbols by lazy { XdkArtifactSymbols.capture(artifact) }

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

        /**
         * What a module that depends on this one compiles against: the artifact without its methods' code and source
         * text, its build time and its source directory, with whether each method has code, and where each declaration
         * is. A module that depends on this one compiles against another artifact with the same as it did against this
         * one, and its facts name this one's declarations at the same places.
         */
        internal val api: String get() = apis.all

        /**
         * [api] without where the private methods and properties are, and what they declare. A module that compiled
         * against this one cannot name them, so against another artifact with the same it compiles as it did, and its
         * facts name this one's declarations at the same places. The facts of a module whose compile failed are what it
         * got that far, which name every member of a type it used, private ones too: [api] is what keeps those.
         */
        internal val accessibleApi: String get() = apis.accessible

        /** [api] and [accessibleApi], from one read of the artifact. */
        private class Apis(
            val all: String,
            val accessible: String,
        )

        private val apis: Apis by lazy {
            ExecutionTrace.api("FileStructure.api", module) {
                val file = FileStructure(ByteArrayInputStream(artifact))
                ConstantPool.withPool(file.constantPool).use {
                    val pool = file.constantPool
                    // read before the write below renumbers the constants, each with whether it is private
                    val located =
                        declarations.entries.map { (index, location) ->
                            val constant = pool.getConstant(index)
                            val place = "$constant ${location.sourceName} ${location.range}"
                            place to (constant is IdentityConstant && isPrivate(constant))
                        }
                    val methods =
                        pool.constants
                            .filterIsInstance<MethodConstant>()
                            .mapNotNull { it.component as? MethodStructure }
                            .distinct()
                    val implemented = methods.map { "${it.identityConstant} ${it.isAbstract} ${it.hasCode()} ${it.isNative}" }
                    methods.forEach { method ->
                        // marking a method abstract drops its code; it is then marked as it was
                        val abstract = method.isAbstract
                        method.setAbstract(true)
                        method.setAbstract(abstract)
                        method.configureSource(null, 0)
                    }
                    file.moduleIds().forEach { id ->
                        file.getModule(id).apply {
                            timestamp = null
                            sourceDir = null
                        }
                    }
                    val stripped = ByteArrayOutputStream().also(file::writeTo).toByteArray()

                    fun digest(places: List<String>): String {
                        val digest = MessageDigest.getInstance("SHA-256")
                        digest.update(stripped)
                        (places.sorted() + implemented.sorted()).forEach {
                            digest.update(it.toByteArray(Charsets.UTF_8))
                            digest.update(0)
                        }
                        return HexFormat.of().formatHex(digest.digest())
                    }
                    Apis(digest(located.map { it.first }), digest(located.filterNot { it.second }.map { it.first }))
                }
            }
        }

        /**
         * Whether [identity] is a private method or property, other than a constructor, or is declared in one: what
         * only its own class can name.
         */
        private fun isPrivate(identity: IdentityConstant): Boolean =
            generateSequence(identity) { it.parentConstant }.any { parent ->
                when (val component = parent.component) {
                    is MethodStructure -> !component.isConstructor && component.access == Access.PRIVATE
                    is PropertyStructure -> component.access == Access.PRIVATE
                    else -> false
                }
            }

        /** Constant-table symbol identity qualified by its module and immutable artifact revision. */
        data class SymbolKey(
            val module: String,
            val revision: String,
            val index: Int,
        )

        /** Returns a defensive copy; a host cannot mutate a queued request's dependency bytes. */
        fun bytes(): ByteArray = artifact.clone()

        /** A fresh compiler-owned tree for each attempt; no mutable repository survives publication. */
        internal fun open(): FileStructure =
            ExecutionTrace.api("FileStructure.read(dependency)", module) {
                FileStructure(ByteArrayInputStream(artifact))
            }

        companion object {
            /**
             * Binary-only libraries have identities and types, but deliberately no source locations.
             */
            fun fromBinary(bytes: ByteArray): XdkDependency {
                val owned = bytes.clone()
                val file =
                    ExecutionTrace.api("FileStructure.read(binary)") {
                        FileStructure(ByteArrayInputStream(owned))
                    }
                return XdkDependency(file.module.name, owned, emptyMap())
            }

            internal fun capture(
                compilation: EmbeddingSupport.Compilation,
                locations: Map<IdentityConstant, SemanticModel.SourceLocation>,
            ): XdkDependency {
                val artifact =
                    ByteArrayOutputStream()
                        .also {
                            ExecutionTrace.api("FileStructure.writeTo") {
                                compilation.file().writeTo(it)
                            }
                        }.toByteArray()
                // Serialization establishes the artifact's constant indices. Resolve against its pool,
                // since emission may have removed or re-registered constants from the source attempt.
                val file =
                    ExecutionTrace.api("FileStructure.read(emitted)") {
                        FileStructure(ByteArrayInputStream(artifact))
                    }
                val declarations =
                    locations.entries
                        .mapNotNull { (identity, location) ->
                            file.constantPool
                                .getConstant(identity)
                                ?.position
                                ?.let { it to location }
                        }.toMap()
                return XdkDependency(file.module.name, artifact, declarations, fromSource = true)
            }
        }
    }

/**
 * Worker-only join; compiler equality handles overloads and foreign pools, never display strings. The documentation
 * is the declaration's doc comment from its source, which a compiled module does not keep.
 */
internal data class DependencyDeclaration(
    val key: XdkDependency.SymbolKey,
    val location: SemanticModel.SourceLocation,
    val documentation: String?,
)

/**
 * Immutable set of external binary modules for a compilation. Rejects duplicate or bundled-module
 * replacements and opens fresh compiler repositories on the worker so mutable pools are not shared between
 * attempts.
 */
internal class XdkDependencies(
    dependencies: List<XdkDependency>,
) {
    /** By module name, in name order, as the server's info lists them; a Map.copyOf's order changes with each JVM. */
    val modules: Map<String, XdkDependency> = Collections.unmodifiableMap(dependencies.associateBy { it.module }.toSortedMap())

    init {
        require(modules.size == dependencies.size) { "Duplicate dependency module" }
        // only the XDK's own sources, compiled by the server, replace a bundled library ([XdkWorkspaceMode])
        require(modules.values.none { it.module in XdkLibraries.moduleNames && !it.fromSource }) {
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
                        check(identity != null && identity.moduleConstant.name == name) {
                            "Invalid dependency source index"
                        }
                        put(
                            identity,
                            DependencyDeclaration(
                                XdkDependency.SymbolKey(name, dependency.revision, index),
                                location,
                                dependency.documentation[index],
                            ),
                        )
                    }
                }
            }
        return Open(repository, declarations, modules.mapValues { it.value.revision }, modules)
    }

    /**
     * Attempt-owned repository and declaration identities, paired with the artifact revisions that produced
     * them.
     */
    class Open(
        val repository: ModuleRepository,
        val declarations: Map<IdentityConstant, DependencyDeclaration>,
        val revisions: Map<String, String>,
        val artifacts: Map<String, XdkDependency> = emptyMap(),
    )
}
