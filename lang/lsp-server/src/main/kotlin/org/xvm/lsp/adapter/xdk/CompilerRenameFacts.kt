package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Constant
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.MethodStructure
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodConstant
import org.xvm.asm.constants.PackageConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.UnionTypeConstant
import org.xvm.lsp.util.ExecutionTrace
import java.io.File
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

    /** The single compiler-generated shorthand constructor, with no invented declaration span. */
    data class PrimaryConstructor(
        val owner: ProofIdentity,
    ) : ProofIdentity

    /** A mixin super register is relative to its written method and each adopting host's chain. */
    data class Super(
        val method: ProofIdentity,
    ) : ProofIdentity

    /** A predefined receiver is bound to its compiler-selected class and access view. */
    data class Receiver(
        val owner: ProofIdentity,
        val register: Int,
    ) : ProofIdentity

    /** A generated forwarding method is identified by its host, written contracts and receivers. */
    data class Composed(
        val owner: ProofIdentity,
        val members: List<ProofIdentity>,
        val delegates: List<ProofIdentity>,
        val cycles: List<Composed> = emptyList(),
        val alternatives: List<ProofIdentity> = emptyList(),
    ) : ProofIdentity

    /** Unordered alternatives, each retaining its receiver, ordered contracts and delegates. */
    data class Alternatives(
        val targets: Set<ProofIdentity>,
    ) : ProofIdentity

    /** Compiler type structure: base then ordered arguments; relational alternatives are sets. */
    data class TypeShape(
        val format: Constant.Format,
        val components: List<ProofIdentity>,
    ) : ProofIdentity

    /** Literal annotation arguments retain their constant kind as well as their exact value. */
    data class Value(
        val format: Constant.Format,
        val value: String,
    ) : ProofIdentity

    /** An implicit package has a directory identity, with no invented source declaration. */
    data class Directory(
        val path: String,
    ) : ProofIdentity

    /** No cross-attempt equivalence is safe when neither a declaration nor artifact proves it. */
    data class Unproven(
        val id: UUID = UUID.randomUUID(),
    ) : ProofIdentity
}

internal data class CompilerReceiver(
    val owner: IdentityConstant,
    val register: Int,
)

internal class ProofRelations(
    val declarations: Set<ProofIdentity> = emptySet(),
    val chains: List<Chain> = emptyList(),
) {
    data class Chain(
        val owner: ProofIdentity,
        val members: List<ProofIdentity>,
        val supported: Boolean,
        val cycles: List<ProofIdentity.Composed> = emptyList(),
        val alternatives: List<ProofIdentity> = emptyList(),
    )
}

/** Attempt-owned inputs; copied into detached identities before leaving the compiler worker. */
internal data class CompilerExtractionFacts(
    val types: Map<SemanticModel.SourceLocation, TypeConstant>,
    val stableValues: Set<SemanticModel.SourceLocation>,
)

/** Types and stable value reads needed to prove a new helper's signature and captured inputs. */
internal data class ExtractMethodFacts(
    val types: Map<SemanticModel.SourceLocation, ProofIdentity> = emptyMap(),
    val stableValues: Set<SemanticModel.SourceLocation> = emptySet(),
)

/** Detached comparison facts. Keeping compiler constants here retains every root's entire pool. */
internal class CompilerRenameFacts(
    val models: List<SemanticModel>,
    val constants: Map<SemanticModel.SymbolId, ProofIdentity>,
    val methods: ProofRelations = ProofRelations(),
    val properties: ProofRelations = ProofRelations(),
    val imports: List<XdkAutoImports.Target> = emptyList(),
    private val modules: Map<String, CompilerRenameFacts> = emptyMap(),
    val memberActions: List<XdkMemberActions.Candidate> = emptyList(),
    val typeNames: List<TypeName> = emptyList(),
    val typePaths: List<TypePath> = emptyList(),
    val resourceValues: Map<SemanticModel.SourceLocation, String?> = emptyMap(),
    val callables: Map<SemanticModel.SourceLocation, ProofIdentity> = emptyMap(),
    val removableLocals: Set<SemanticModel.SourceLocation> = emptySet(),
    val extraction: ExtractMethodFacts = ExtractMethodFacts(),
    val missingMethods: List<XdkMissingMethods.Candidate> = emptyList(),
    val methodSignatures: Map<SemanticModel.SymbolId, XdkMissingMethods.Signature> = emptyMap(),
    val missingMethodInputs: XdkMissingMethods.Inputs = XdkMissingMethods.Inputs(),
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
                attempts.flatMap { it.memberActions },
                attempts.flatMap { it.typeNames }.distinct(),
                attempts.flatMap { it.typePaths }.distinct(),
                attempts.flatMap { it.resourceValues.entries }.associate { it.toPair() },
                attempts.flatMap { it.callables.entries }.associate { it.toPair() },
                attempts.flatMapTo(linkedSetOf()) { it.removableLocals },
                ExtractMethodFacts(
                    attempts.flatMap { it.extraction.types.entries }.associate { it.toPair() },
                    attempts.flatMapTo(linkedSetOf()) { it.extraction.stableValues },
                ),
                attempts.flatMap { it.missingMethods }.distinct(),
                attempts.flatMap { it.methodSignatures.entries }.associate { it.toPair() },
                XdkMissingMethods.Inputs(
                    attempts.flatMap { it.missingMethodInputs.localTypes.entries }.associate { it.toPair() },
                    attempts.flatMap { it.missingMethodInputs.receivers.entries }.associate { it.toPair() },
                ),
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
    errors: ErrorListener? = null,
    supers: Map<SemanticModel.SymbolId, MethodConstant> = emptyMap(),
    members: List<CompilerMemberAction> = emptyList(),
    receivers: Map<SemanticModel.SymbolId, CompilerReceiver> = emptyMap(),
    typeNames: List<CompilerTypeName> = emptyList(),
    resourceValues: Map<SemanticModel.SourceLocation, String?> = emptyMap(),
    callables: Map<SemanticModel.SourceLocation, Pair<TypeConstant, MethodConstant>> = emptyMap(),
    removableLocals: Set<SemanticModel.SourceLocation> = emptySet(),
    extraction: CompilerExtractionFacts? = null,
    missingMethods: List<CompilerMissingMethod> = emptyList(),
    missingInputs: CompilerMissingInputs = CompilerMissingInputs(),
): CompilerRenameFacts {
    val declarations =
        models
            .flatMap { it.symbols }
            .mapNotNull { symbol ->
                val source = symbol.declarationSource ?: return@mapNotNull null
                val range = symbol.declaration ?: return@mapNotNull null
                constants[symbol.id]?.let { it to SemanticModel.SourceLocation(source, range) }
            }.toMap()
    val sourceMethods =
        models
            .flatMap { model ->
                model.symbols.filter {
                    it.kind == SemanticModel.SymbolKind.METHOD && it.declaration != null &&
                        it.declarationSource == model.sourceName
                }
            }.mapTo(hashSetOf()) { it.id }
    val identities = mutableMapOf<Constant, ProofIdentity>()

    fun identity(constant: Constant): ProofIdentity =
        identities.getOrPut(constant) {
            val value = constant as? IdentityConstant
            val module = value?.moduleConstant?.name
            val location =
                declarations[constant]
                    ?: value?.let { dependencies?.declarations?.get(it)?.location }
            val host = (constant as? MethodConstant)?.namespace
            val sourceHost =
                host != null &&
                    (host in declarations || dependencies?.declarations?.containsKey(host) == true)
            val packageParent = (constant as? PackageConstant)?.let { identity(it.parentConstant) }
            when {
                // Bundled source navigation must use the same artifact identity as binary-only
                // views.
                location != null && module !in XdkLibraries.moduleNames -> {
                    ProofIdentity.Source(location, constant.format, requireNotNull(value).name)
                }

                constant is PackageConstant &&
                    module !in XdkLibraries.moduleNames &&
                    (
                        packageParent is ProofIdentity.Source ||
                            packageParent is ProofIdentity.Directory
                    ) -> {
                    val directory =
                        when (val parent = packageParent) {
                            is ProofIdentity.Source -> {
                                parent.location.sourceName?.let(::File)?.let {
                                    File(it.parentFile, it.nameWithoutExtension)
                                }
                            }

                            is ProofIdentity.Directory -> {
                                File(parent.path)
                            }
                        }
                    directory?.let { ProofIdentity.Directory(File(it, constant.name).path) }
                        ?: ProofIdentity.Unproven()
                }

                constant is MethodConstant &&
                    sourceHost &&
                    (constant.component as? MethodStructure)?.let {
                        it.isSynthetic && it.isShorthandConstructor
                    } == true -> {
                    ProofIdentity.PrimaryConstructor(identity(requireNotNull(host)))
                }

                constant is MethodConstant &&
                    module !in XdkLibraries.moduleNames &&
                    sourceHost &&
                    errors != null -> {
                    val structure = host.component as? ClassStructure
                    val info =
                        structure?.let {
                            ExecutionTrace.api("TypeConstant.ensureTypeInfo(rename-provenance)") {
                                it.formalType.ensureAccess(Access.PRIVATE).ensureTypeInfo(errors)
                            }
                        }
                    val route =
                        info?.let { owner ->
                            owner.getMethodById(constant)?.let { owner.dispatch(it, errors) }
                        }
                    if (
                        route == null ||
                        !route.supported ||
                        route.methods.isEmpty() ||
                        constant in route.methods
                    ) {
                        ProofIdentity.Unproven()
                    } else {
                        route.proof(identity(requireNotNull(host)), ::identity)
                    }
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
                            ProofIdentity.Binary(
                                XdkDependency.SymbolKey(
                                    module,
                                    XdkLibraries.revision(module),
                                    index,
                                ),
                            )
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
                                    XdkDependency.SymbolKey(
                                        module,
                                        XdkLibraries.revision(module),
                                        signature,
                                    ),
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
                        ProofIdentity.Binary(
                            XdkDependency.SymbolKey(
                                module,
                                dependencies.revisions.getValue(module),
                                index,
                            ),
                        )
                    }
                }

                else -> {
                    ProofIdentity.Unproven()
                }
            }
        }

    fun path(value: IdentityConstant): List<String> = value.path.filter { it.format != Constant.Format.Module }.map { it.name }

    fun signatureType(type: CompilerMissingMethod.Type): ProofIdentity? =
        when (type) {
            is CompilerMissingMethod.Type.Resolved -> receiverIdentity(type.value, ::identity)
            is CompilerMissingMethod.Type.Detached -> type.value
        }

    fun cycle(cycle: CompilerDispatch.Cycle) =
        ProofIdentity.Composed(
            receiverIdentity(cycle.receiver, ::identity) ?: ProofIdentity.Unproven(),
            cycle.contracts.map(::identity),
            emptyList(),
        )

    val routedOwners = methods.chains.filter { it.cycles.isNotEmpty() || it.alternatives.isNotEmpty() }.mapTo(hashSetOf()) { it.owner }
    val callableIdentities =
        if (errors == null) {
            emptyMap()
        } else {
            callables.values
                .distinct()
                .filter { (receiver, _) ->
                    receiver.resolveTypedefs().removeAccess() is UnionTypeConstant ||
                        (receiver.isSingleUnderlyingClass(false) && receiver.getSingleUnderlyingClass(false) in routedOwners)
                }.associateWith { (receiver, method) -> callableIdentity(receiver, method, errors, ::identity) }
        }

    return CompilerRenameFacts(
        models,
        constants.mapValues { identity(it.value) } +
            models
                .distinctBy { it.id }
                .flatMap { model ->
                    model.parameters.mapNotNull { (id, slot) ->
                        constants[slot.method]?.let {
                            id to ProofIdentity.Parameter(identity(it), slot.index)
                        }
                    }
                }.toMap() +
            supers.mapValues { ProofIdentity.Super(identity(it.value)) } +
            receivers.mapValues { (_, receiver) -> ProofIdentity.Receiver(identity(receiver.owner), receiver.register) },
        ProofRelations(
            methods.declarations.mapTo(linkedSetOf(), ::identity),
            methods.chains.map {
                ProofRelations.Chain(
                    identity(it.owner),
                    it.methods.map(::identity),
                    it.supported,
                    it.cycles.map(::cycle),
                    it.alternatives.map { choice -> choice.proof(::identity) },
                )
            },
        ),
        ProofRelations(
            properties.declarations.mapTo(linkedSetOf(), ::identity),
            properties.chains.map {
                ProofRelations.Chain(
                    identity(it.owner),
                    it.properties.map(::identity),
                    it.supported,
                )
            },
        ),
        constants.values.mapNotNull(XdkAutoImports::target).distinct(),
        memberActions =
            members.mapNotNull { member ->
                val owner =
                    identity(member.owner) as? ProofIdentity.Source ?: return@mapNotNull null
                val contract = identity(member.contract)
                if (
                    contract !is ProofIdentity.Source &&
                    contract !is ProofIdentity.Binary &&
                    !(
                        contract is ProofIdentity.Method &&
                            contract.parent is ProofIdentity.Binary
                    )
                ) {
                    return@mapNotNull null
                }
                XdkMemberActions.Candidate(
                    owner.location,
                    contract,
                    member.insertion,
                    member.declaration,
                    member.implementation,
                    member.requiredCount,
                    member.imports,
                )
            },
        typeNames =
            typeNames.map {
                TypeName(it.location, it.terminal, identity(it.target), it.target.moduleConstant.name, path(it.target), it.imported)
            },
        typePaths =
            (constants.values.filterIsInstance<IdentityConstant>() + typeNames.map { it.target })
                .flatMap { it.path }
                .filter {
                    it.format in setOf(Constant.Format.Module, Constant.Format.Package, Constant.Format.Class, Constant.Format.Typedef)
                }.distinct()
                .map { TypePath(identity(it), it.moduleConstant.name, path(it)) },
        resourceValues = resourceValues,
        missingMethods =
            missingMethods.mapNotNull { method ->
                if (method.candidate.publicOwner == null) {
                    method.candidate
                } else {
                    val parameters = method.parameters.map { signatureType(it) ?: return@mapNotNull null }
                    val returns = method.returns.map { signatureType(it) ?: return@mapNotNull null }
                    method.candidate.copy(signature = XdkMissingMethods.Signature(parameters, returns))
                }
            },
        methodSignatures =
            constants
                .filterKeys(sourceMethods::contains)
                .mapNotNull { (id, constant) ->
                    val method = (constant as? MethodConstant)?.component as? MethodStructure ?: return@mapNotNull null
                    val parameters = method.params.map { receiverIdentity(it.type, ::identity) ?: return@mapNotNull null }
                    val returns = method.returnTypes.map { receiverIdentity(it, ::identity) ?: return@mapNotNull null }
                    id to XdkMissingMethods.Signature(parameters, returns, method.access == Access.PUBLIC)
                }.toMap(),
        removableLocals = removableLocals,
        extraction =
            ExtractMethodFacts(
                extraction
                    ?.types
                    .orEmpty()
                    .mapNotNull { (at, type) -> receiverIdentity(type, ::identity)?.let { at to it } }
                    .toMap(),
                extraction?.stableValues.orEmpty(),
            ),
        callables =
            callables
                .mapNotNull { (site, selected) ->
                    callableIdentities[selected]?.let { site to it }
                }.toMap(),
        missingMethodInputs =
            XdkMissingMethods.Inputs(
                missingInputs.localTypes.mapValues { (_, type) ->
                    XdkMissingMethods.LocalType(type.source, receiverIdentity(type.type, ::identity) ?: ProofIdentity.Unproven())
                },
                missingInputs.receivers,
            ),
    )
}
