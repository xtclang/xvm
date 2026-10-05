package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.asm.ConstantPool
import org.xvm.asm.Constants.Access
import org.xvm.asm.FileStructure
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodConstant
import org.xvm.asm.constants.MultiMethodConstant
import org.xvm.lsp.adapter.SymbolMoniker
import org.xvm.lsp.util.ExecutionTrace
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Map.copyOf as immutableMap

/**
 * Content-addressed identities, version 1. Only build time and checkout directory are normalized;
 * code, signatures, module versions, resources and relative source/debug information remain inputs.
 * Original constant indices map to the normalized artifact's indices, never to display signatures.
 * This table retains no compiler objects and does not change the host artifact or source index.
 */
internal class XdkArtifactSymbols private constructor(
    private val revision: String,
    entries: Map<Int, Entry>,
) {
    private val entries = immutableMap(entries)

    private data class Entry(
        val index: Int,
        val exported: Boolean,
    )

    fun moniker(
        index: Int,
        imported: Boolean,
    ): SymbolMoniker? =
        entries[index]?.let {
            SymbolMoniker(
                "ecstasy-artifact-v1",
                "$revision/${it.index}",
                when {
                    !it.exported -> SymbolMoniker.Kind.LOCAL
                    imported -> SymbolMoniker.Kind.IMPORT
                    else -> SymbolMoniker.Kind.EXPORT
                },
            )
        }

    companion object {
        fun capture(bytes: ByteArray): XdkArtifactSymbols =
            ExecutionTrace.api("FileStructure.symbolIdentities") {
                val file = FileStructure(bytes.inputStream())
                ConstantPool.withPool(file.constantPool).use {
                    val identities =
                        file.constantPool.constants
                            .filterIsInstance<IdentityConstant>()
                            .filter {
                                it.moduleConstant == file.moduleId && it.format in FORMATS &&
                                    (it !is MethodConstant || !it.isLambda) && it.component != null
                            }.associateBy { it.position }
                    file.moduleIds().forEach { id ->
                        file.getModule(id).apply {
                            timestamp = null
                            sourceDir = null
                        }
                    }
                    val canonical = ByteArrayOutputStream().also(file::writeTo).toByteArray()
                    val revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical))
                    val entries =
                        identities
                            .mapNotNull { (original, identity) ->
                                val current =
                                    file.constantPool.getConstant(identity) as? IdentityConstant
                                        ?: return@mapNotNull null
                                val exported =
                                    generateSequence(current) { it.parentConstant }
                                        .filterNot { it is MultiMethodConstant }
                                        .all { it.component?.access in setOf(Access.PUBLIC, Access.PROTECTED) }
                                original to Entry(current.position, exported)
                            }.toMap()
                    XdkArtifactSymbols(revision, entries)
                }
            }

        private val FORMATS =
            setOf(
                Constant.Format.Module,
                Constant.Format.Package,
                Constant.Format.Class,
                Constant.Format.Method,
                Constant.Format.Property,
            )
    }
}

/** Run only on the compiler worker. Failed/partial attempts and unproven bindings supply no IDs. */
internal object XdkMonikers {
    fun capture(
        artifact: XdkDependency,
        bindings: Map<SemanticModel.SymbolId, Constant>,
        dependencies: XdkDependencies.Open,
    ): Map<SemanticModel.SymbolId, SymbolMoniker> {
        val own = artifact.open()
        return bindings
            .mapNotNull { (symbol, constant) ->
                val identity = constant as? IdentityConstant ?: return@mapNotNull null
                val module = identity.moduleConstant.name
                val imported = module != artifact.module
                val pool =
                    when {
                        !imported -> own.constantPool
                        module in dependencies.artifacts -> dependencies.repository.loadModule(module)?.constantPool
                        else -> XdkLibraries.module(module)?.constantPool
                    } ?: return@mapNotNull null
                val index = pool.getConstant(identity)?.position ?: return@mapNotNull null
                val table =
                    when {
                        !imported -> artifact.symbolIndex
                        module in dependencies.artifacts -> dependencies.artifacts.getValue(module).symbolIndex
                        else -> XdkLibraries.symbolIndex(module)
                    }
                table?.moniker(index, imported)?.let { symbol to it }
            }.toMap()
    }
}
