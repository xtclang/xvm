package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodConstant
import java.util.UUID

/** Compiler-proven identities copied before an attempt's pool and AST can be released. */
internal sealed interface ProofIdentity {
    data class Source(
        val location: SemanticModel.SourceLocation,
        val format: Constant.Format,
        val name: String,
    ) : ProofIdentity

    data class Binary(
        val key: XdkDependency.SymbolKey,
    ) : ProofIdentity

    /** Generated method identities can reuse an artifact signature without occupying its table. */
    data class Method(
        val parent: ProofIdentity,
        val signature: XdkDependency.SymbolKey,
    ) : ProofIdentity

    data class Parameter(
        val method: ProofIdentity,
        val index: Int,
    ) : ProofIdentity

    /** No cross-attempt equivalence is safe when neither a declaration nor artifact proves it. */
    data class Unproven(
        val id: UUID = UUID.randomUUID(),
    ) : ProofIdentity
}

internal class ProofRelations(
    val declarations: Set<ProofIdentity> = emptySet(),
    val chains: List<Chain> = emptyList(),
) {
    data class Chain(
        val owner: ProofIdentity,
        val members: List<ProofIdentity>,
        val supported: Boolean,
    )
}

/** Detached comparison facts. Keeping compiler constants here retains every root's entire pool. */
internal class CompilerRenameFacts(
    val models: List<SemanticModel>,
    val constants: Map<SemanticModel.SymbolId, ProofIdentity>,
    val methods: ProofRelations = ProofRelations(),
    val properties: ProofRelations = ProofRelations(),
    val imports: List<XdkAutoImports.Target> = emptyList(),
    private val modules: Map<String, CompilerRenameFacts> = emptyMap(),
) {
    /** Unchanged independent modules cannot acquire new bindings from a source edit elsewhere. */
    fun within(scopes: Set<String>): CompilerRenameFacts = merge(modules.filterKeys(scopes::contains))

    companion object {
        fun merge(modules: Map<String, CompilerRenameFacts>): CompilerRenameFacts {
            val attempts = modules.values
            return CompilerRenameFacts(
                attempts.flatMap { it.models },
                attempts.flatMap { it.constants.entries }.associate { it.toPair() },
                ProofRelations(
                    attempts.flatMapTo(linkedSetOf()) { it.methods.declarations },
                    attempts.flatMap { it.methods.chains },
                ),
                ProofRelations(
                    attempts.flatMapTo(linkedSetOf()) { it.properties.declarations },
                    attempts.flatMap { it.properties.chains },
                ),
                attempts.flatMap { it.imports }.distinct(),
                modules,
            )
        }
    }
}

/** Resolve keys by compiler equality, never by a printed signature or type name. */
internal fun captureRenameFacts(
    models: List<SemanticModel>,
    constants: Map<SemanticModel.SymbolId, Constant>,
    dependencies: XdkDependencies.Open? = null,
    methods: CompilerMethodRelations = CompilerMethodRelations(emptySet(), emptyList()),
    properties: CompilerPropertyRelations = CompilerPropertyRelations(),
): CompilerRenameFacts {
    val declarations =
        models
            .flatMap { it.symbols }
            .mapNotNull { symbol ->
                val source = symbol.declarationSource ?: return@mapNotNull null
                val range = symbol.declaration ?: return@mapNotNull null
                constants[symbol.id]?.let { it to SemanticModel.SourceLocation(source, range) }
            }.toMap()
    val identities = mutableMapOf<Constant, ProofIdentity>()

    fun identity(constant: Constant): ProofIdentity =
        identities.getOrPut(constant) {
            val value = constant as? IdentityConstant
            val module = value?.moduleConstant?.name
            val location = declarations[constant] ?: value?.let { dependencies?.declarations?.get(it)?.location }
            when {
                // Bundled source navigation must use the same artifact identity as binary-only views.
                location != null && module !in XdkLibraries.moduleNames -> {
                    ProofIdentity.Source(location, constant.format, requireNotNull(value).name)
                }

                module != null && module in XdkLibraries.moduleNames -> {
                    val index =
                        XdkLibraries
                            .module(module)
                            .constantPool
                            .getConstant(constant)
                            ?.position
                    when {
                        index != null -> {
                            ProofIdentity.Binary(XdkDependency.SymbolKey(module, XdkLibraries.revision(module), index))
                        }

                        constant is MethodConstant && !constant.isLambda -> {
                            val signature =
                                XdkLibraries
                                    .module(module)
                                    .constantPool
                                    .getConstant(constant.signature)
                                    ?.position
                            if (signature == null) {
                                ProofIdentity.Unproven()
                            } else {
                                ProofIdentity.Method(
                                    identity(constant.parentConstant),
                                    XdkDependency.SymbolKey(module, XdkLibraries.revision(module), signature),
                                )
                            }
                        }

                        else -> {
                            ProofIdentity.Unproven()
                        }
                    }
                }

                module != null && dependencies?.revisions?.containsKey(module) == true -> {
                    val index =
                        dependencies.repository
                            .loadModule(module)
                            ?.constantPool
                            ?.getConstant(constant)
                            ?.position
                    if (index == null) {
                        ProofIdentity.Unproven()
                    } else {
                        ProofIdentity.Binary(XdkDependency.SymbolKey(module, dependencies.revisions.getValue(module), index))
                    }
                }

                else -> {
                    ProofIdentity.Unproven()
                }
            }
        }
    return CompilerRenameFacts(
        models,
        constants.mapValues { identity(it.value) } +
            models
                .distinctBy { it.id }
                .flatMap { model ->
                    model.parameters.mapNotNull { (id, slot) ->
                        constants[slot.method]?.let { id to ProofIdentity.Parameter(identity(it), slot.index) }
                    }
                }.toMap(),
        ProofRelations(
            methods.declarations.mapTo(linkedSetOf(), ::identity),
            methods.chains.map { ProofRelations.Chain(identity(it.owner), it.methods.map(::identity), it.supported) },
        ),
        ProofRelations(
            properties.declarations.mapTo(linkedSetOf(), ::identity),
            properties.chains.map { ProofRelations.Chain(identity(it.owner), it.properties.map(::identity), it.supported) },
        ),
        constants.values.mapNotNull(XdkAutoImports::target).distinct(),
    )
}
