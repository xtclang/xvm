package org.xvm.lsp.adapter.xdk

import org.slf4j.LoggerFactory
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ConstantPool
import org.xvm.asm.FileStructure
import org.xvm.asm.ModuleRepository
import org.xvm.asm.constants.IdentityConstant
import org.xvm.compiler.BuildRepository
import org.xvm.lsp.adapter.ReadOnlyDocument
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.util.ExecutionTrace
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.HexFormat
import java.util.concurrent.CancellationException
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
        parameters: Map<Int, Map<String, SemanticModel.Range>> = emptyMap(),
        /** Emitted by the server's compiler from a source module, not read from a binary on disk. */
        internal val fromSource: Boolean = false,
        /**
         * The importable declarations of a workspace module that replaces a bundled library, which import suggestions
         * offer instead of the bundle's ([XdkAutoImports]); none for any other module. The artifact's revision covers them.
         */
        internal val imports: List<XdkAutoImports.Target> = emptyList(),
    ) {
        /** By URI, in URI order, as the server's info lists them; a Map.copyOf's order changes with each JVM. */
        internal val documents: Map<String, ReadOnlyDocument> = Collections.unmodifiableMap(documents.toSortedMap())

        /**
         * The doc comments of attached sources by constant index, which the artifact does not keep. The revision covers
         * them through the locations, whose source names carry the source text's revision.
         */
        internal val documentation: Map<Int, String> = immutableMap(documentation)

        /**
         * Where the parameters of the methods of [declarations] are, by the method's constant index and the parameter's
         * name, in the source of the method's declaration, which Go to Definition of a named argument goes to; the
         * artifact keeps no locations. The revision covers them.
         */
        internal val parameters: Map<Int, Map<String, SemanticModel.Range>> = immutableMap(parameters.mapValues { immutableMap(it.value) })

        /**
         * The types, methods and properties of the attached sources, as Go to Symbol finds them, where Go to Definition
         * opens them: in the read-only snapshots of [documents]; none for an artifact without attached sources. Parsed at
         * the first search.
         */
        internal val symbols: List<SymbolInfo> by lazy {
            documents.flatMap { (uri, document) ->
                symbolic(libraryDeclarations(document.text, "")).map { SymbolInfo.of(it.name, it.kind, it.range.location(uri)) }
            }
        }

        internal fun withSources(roots: List<File>): XdkDependency {
            val index = XdkAttachedSources.read(this, roots)
            return XdkDependency(
                module,
                artifact,
                index.declarations,
                index.documents,
                index.documentation,
                index.parameters,
                fromSource,
                imports,
            )
        }

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
                        parameters.toSortedMap().forEach { (index, names) ->
                            names.toSortedMap().forEach { (name, range) ->
                                output.writeInt(index)
                                output.writeUTF(name)
                                output.writeInt(range.start.line)
                                output.writeInt(range.start.column)
                                output.writeInt(range.end.line)
                                output.writeInt(range.end.column)
                            }
                        }
                    }
                }.toByteArray()
                .let { HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(it)) }

        /**
         * What a module that depends on this one compiles against, as a whole ([XdkModuleApi.all]) and declaration by
         * declaration, with where each is ([XdkModuleApi]), or null when the artifact cannot be read alone, though no
         * artifact is known that cannot: a module that depends on such an artifact is then compiled again whenever it
         * is, as the log says.
         */
        internal val moduleApi: XdkModuleApi? by lazy {
            readable("API") {
                ExecutionTrace.api("FileStructure.api", module) {
                    val file = FileStructure(ByteArrayInputStream(artifact))
                    // each declaration's identity, read before writing the API renumbers the constants
                    val (located, parameterPlaces) =
                        ConstantPool.withPool(file.constantPool).use {
                            fun identity(index: Int) = file.constantPool.getConstant(index) as IdentityConstant
                            declarations.entries.associate { (index, location) -> identity(index) to location } to
                                parameters.entries.associate { (index, names) -> identity(index) to names }
                        }
                    XdkModuleApi.of(file, located, parameterPlaces)
                }
            }
        }

        /**
         * What this module's compile used of the modules it depends on ([XdkModuleUses]), or null when the artifact cannot
         * be read alone, and the module is compiled again whenever one of them is.
         */
        internal val uses: XdkModuleUses? by lazy {
            readable("uses") {
                ExecutionTrace.api("FileStructure.uses", module) {
                    XdkModuleUses.of(FileStructure(ByteArrayInputStream(artifact)))
                }
            }
        }

        private fun <T> readable(
            what: String,
            read: () -> T,
        ): T? =
            try {
                read()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: RuntimeException) {
                logger.info(
                    "compile: the {} of {}'s artifact cannot be read alone ({}), so the modules that depend on it compile again with it",
                    what,
                    module,
                    failure.message,
                )
                null
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
                parameters: Map<IdentityConstant, Map<String, SemanticModel.Range>>,
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
                // a method's parameters are where the source of its declaration has them
                val parameterRanges =
                    parameters.entries
                        .filter { (method, _) -> method in locations }
                        .mapNotNull { (method, names) ->
                            file.constantPool
                                .getConstant(method)
                                ?.position
                                ?.let { it to names }
                        }.toMap()
                // a workspace module of a bundled library's name replaces the bundle's import suggestions with its own
                val imports =
                    if (file.module.name in XdkLibraries.moduleNames) {
                        file.constantPool.constants
                            .mapNotNull(XdkAutoImports::target)
                            .filter { it.module == file.module.name }
                            .distinct()
                    } else {
                        emptyList()
                    }
                return XdkDependency(
                    file.module.name,
                    artifact,
                    declarations,
                    parameters = parameterRanges,
                    fromSource = true,
                    imports = imports,
                )
            }
        }
    }

/**
 * Worker-only join; compiler equality handles overloads and foreign pools, never display strings. The documentation
 * is the declaration's doc comment from its source, which a compiled module does not keep; the parameters, of a method,
 * are where its parameters' names are in the source of [location], by name.
 */
internal data class DependencyDeclaration(
    val key: XdkDependency.SymbolKey,
    val location: SemanticModel.SourceLocation,
    val documentation: String?,
    val parameters: Map<String, SemanticModel.Range>,
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
        // only a workspace's own modules, compiled by the server, replace a bundled library ([XdkLibraryReplacement])
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
                                dependency.parameters[index].orEmpty(),
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

private val logger = LoggerFactory.getLogger(XdkDependency::class.java)
