package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.Argument
import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Composition
import org.xvm.asm.Constant
import org.xvm.asm.ConstantPool
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.MethodStructure
import org.xvm.asm.PropertyStructure
import org.xvm.asm.Register
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodConstant
import org.xvm.asm.constants.PropertyConstant
import org.xvm.asm.constants.PseudoConstant
import org.xvm.asm.constants.SignatureConstant
import org.xvm.asm.constants.SingletonConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.TypeParameterConstant
import org.xvm.asm.constants.TypedefConstant
import org.xvm.compiler.InvocationBinding
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.ComponentStatement
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.compiler.ast.LabeledExpression
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.compiler.ast.NewExpression
import org.xvm.compiler.ast.Parameter
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.compiler.ast.TypedefStatement
import org.xvm.compiler.ast.VariableDeclarationStatement
import org.xvm.compiler.ast.VariableTypeExpression
import org.xvm.lsp.adapter.xdk.SemanticModel.ExpressionType
import org.xvm.lsp.adapter.xdk.SemanticModel.Occurrence
import org.xvm.lsp.adapter.xdk.SemanticModel.Position
import org.xvm.lsp.adapter.xdk.SemanticModel.Range
import org.xvm.lsp.adapter.xdk.SemanticModel.Role
import org.xvm.lsp.adapter.xdk.SemanticModel.Signature
import org.xvm.lsp.adapter.xdk.SemanticModel.SourceLocation
import org.xvm.lsp.adapter.xdk.SemanticModel.Status
import org.xvm.lsp.adapter.xdk.SemanticModel.Symbol
import org.xvm.lsp.adapter.xdk.SemanticModel.SymbolId
import org.xvm.lsp.adapter.xdk.SemanticModel.SymbolKind
import org.xvm.lsp.adapter.xdk.SemanticModel.Type
import org.xvm.lsp.adapter.xdk.SemanticModel.TypeForm
import org.xvm.lsp.adapter.xdk.SemanticModel.TypeId
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.List.copyOf as immutableList
import java.util.Set.copyOf as immutableSet

/**
 * Copy facts while the calling thread exclusively owns the compilation. This does not resume
 * validation or build TypeInfo. The result holds no compiler objects; each call creates a new
 * identity domain, so a host normally keeps one snapshot per document version.
 */
fun EmbeddingSupport.Compilation.semanticSnapshot(): SemanticModel {
    val documents = semanticSnapshots()
    require(documents.size == 1) { "Use semanticSnapshots() for a compilation containing multiple sources" }
    return documents.single()
}

/** Copy all source views together, sharing symbol/type IDs within this compilation only. */
fun EmbeddingSupport.Compilation.semanticSnapshots(): List<SemanticModel> {
    val builder = SemanticModelBuilder()
    val pool = pool() ?: return builder.build(this)
    return ConstantPool.withPool(pool).use { builder.build(this) }
}

/** Explicit compiler-worker inspection of implementation chains, reporting through the host listener. */
fun EmbeddingSupport.Compilation.semanticSnapshots(errors: ErrorListener): List<SemanticModel> {
    val builder = SemanticModelBuilder()
    val pool = pool() ?: return builder.build(this)
    return ConstantPool.withPool(pool).use { builder.build(this, errors) }
}

/**
 * Explicit worker-only member inspection followed by copying. Unlike ordinary semanticSnapshot,
 * this may build receiver TypeInfo and report through [errors]. Never call it on a request thread
 * or concurrently with another compilation using the same repository.
 */
fun EmbeddingSupport.PartialAnalysis.semanticSnapshot(errors: ErrorListener): PartialSemanticModel {
    val builder = SemanticModelBuilder()
    if (errors.isAbortDesired) return PartialSemanticModel(builder.unavailable(), emptyList())
    val pool = pool().orElse(null) ?: return PartialSemanticModel(builder.unavailable(), emptyList())
    return ConstantPool.withPool(pool).use { builder.buildPartial(this, errors) }
}

/** Compiler-worker extraction. All mutable state dies with the builder. */
private class SemanticModelBuilder {
    private val id = UUID.randomUUID()
    private val captureOrigins = IdentityHashMap<Register, Register>()
    private val capturedProperties = mutableMapOf<PropertyConstant, Register>()
    private val registers = IdentityHashMap<Register, SymbolId>()
    private val constants = linkedMapOf<Constant, SymbolId>()
    private val symbols = linkedMapOf<SymbolId, Symbol>()
    private val typeIds = linkedMapOf<TypeConstant, TypeId>()
    private val types = linkedMapOf<TypeId, Type>()
    private val occurrences = linkedMapOf<SourceLocation, Occurrence>()
    private val expressions = linkedMapOf<SourceLocation, TypeId>()
    private val callees = IdentityHashMap<NameExpression, Argument>()
    private val calls = linkedMapOf<SourceLocation, SemanticModel.CallSite>()
    private val callables = linkedMapOf<SymbolId, SemanticModel.Callable>()
    private val callableNodes = IdentityHashMap<AstNode, SymbolId>()

    fun unavailable(): SemanticModel =
        SemanticModel(id, Status.UNAVAILABLE, null, SemanticModel.Facts(symbols, types), emptyList(), emptyList())

    fun build(
        compilation: EmbeddingSupport.Compilation,
        errors: ErrorListener? = null,
    ): List<SemanticModel> {
        val root =
            compilation.parsed()
                ?: return listOf(unavailable())
        val nodes = nodesIn(root)
        collect(nodes, compilation.callBindings())
        val implementations =
            if (compilation.succeeded() && errors != null) compilerImplementationTargets(nodes, errors) else emptyMap()
        return finish(nodes, compilation.succeeded(), implementations)
    }

    private fun collect(
        nodes: List<AstNode>,
        bindings: Map<InvocationExpression, InvocationBinding>,
    ) {
        nodes.filterIsInstance<NewExpression>().forEach {
            capturedProperties.putAll(it.captureOrigins)
            it.sourceBindings?.let { bindings -> captureOrigins.putAll(bindings.captureOrigins) }
        }

        val lambdas = nodes.filterIsInstance<LambdaExpression>()
        lambdas.forEach { it.sourceBindings?.let { bindings -> captureOrigins.putAll(bindings.captureOrigins) } }
        lambdas.forEach { lambda ->
            lambda.sourceBindings?.parameters?.forEach { declare(it.name(), it.register(), SymbolKind.PARAMETER, lambda.source) }
        }
        // Parameters precede synthetic properties that share their source tokens.
        nodes.filterIsInstance<Parameter>().forEach {
            declare(
                it.nameToken,
                it.resolvedTarget,
                if (it.resolvedTarget is Register) SymbolKind.PARAMETER else kind(it.resolvedTarget),
                it.source,
            )
        }
        nodes.forEach { node ->
            when (node) {
                is VariableDeclarationStatement -> {
                    declare(node.nameToken, node.register, SymbolKind.VARIABLE, node.source)
                    if (node
                            .children()
                            .iterator()
                            .asSequence()
                            .any { it is VariableTypeExpression }
                    ) {
                        (normalized(node.register) as? Register)?.let(registers::get)?.let { id ->
                            symbols[id]?.let { symbols[id] = it.copy(inferred = true) }
                        }
                    }
                }

                is MethodDeclarationStatement -> {
                    declare(node.nameToken, identity(node), SymbolKind.METHOD, node.source)
                }

                is PropertyDeclarationStatement -> {
                    declare(node.nameToken, identity(node), SymbolKind.PROPERTY, node.source)
                }

                is TypeCompositionStatement -> {
                    declare(node.nameToken, identity(node), kind(identity(node)), node.source)
                }

                is TypedefStatement -> {
                    declare(node.nameToken, identity(node), SymbolKind.TYPE, node.source)
                }
            }
            if (node is InvocationExpression) {
                val callee = node.invokedExpression
                val method = node.resolvedMethod
                if (callee is NameExpression && method != null) callees[callee] = method
            }
        }
        nodes.forEach { node ->
            when (node) {
                is MethodDeclarationStatement -> {
                    identity(node)?.let(constants::get)?.let { callable(node, it) }
                }

                is LambdaExpression -> {
                    val method = node.lambda ?: return@forEach
                    val at = location(node.source, node.startPosition, node.startPosition)
                    symbol(method.identityConstant, "<lambda>", SymbolKind.METHOD, at)?.let { callable(node, it) }
                }
            }
        }
        val writes =
            compilerWrites(nodes).associate { (name, usage) ->
                location(name.source, name.nameToken.startPosition, name.nameToken.endPosition) to usage
            }
        nodes.forEach { node ->
            val expressionType = validatedType(node as? Expression)
            type(expressionType)?.let { expressions[location(node.source, node.startPosition, node.endPosition)] = it }
            when (node) {
                is InvocationExpression -> {
                    bindings[node]?.let { copyCall(node, it) }
                }

                is NameExpression -> {
                    val at = location(node.source, node.nameToken.startPosition, node.nameToken.endPosition)
                    refer(node.nameToken, callees[node] ?: node.resolvedTarget, expressionType, node.source, writes[at])
                }

                is NamedTypeExpression -> {
                    node.nameBindings.forEach {
                        refer(it.name(), it.target(), expressionType.takeIf { _ -> it.name() === node.nameToken }, node.source)
                    }
                }
            }
        }
    }

    private fun finish(
        nodes: List<AstNode>,
        complete: Boolean,
        implementations: Map<IdentityConstant, Set<IdentityConstant>> = emptyMap(),
    ): List<SemanticModel> {
        val hierarchy = if (complete) hierarchy(nodes) else emptyMap()
        val facts =
            SemanticModel.Facts(
                symbols,
                types,
                hierarchy,
                typeIds.entries.associate { (constant, id) -> id to typeDefinitions(constant) },
                implementations.entries
                    .mapNotNull { (target, implementations) ->
                        constants[target]?.let { it to implementations.mapNotNull(constants::get) }
                    }.toMap(),
                callables,
            )
        return immutableList(
            nodes.map { it.source?.fileName }.distinct().map { source ->
                SemanticModel(
                    id,
                    if (complete) Status.COMPLETE else Status.PARTIAL,
                    source,
                    facts,
                    occurrences.filterKeys { it.sourceName == source }.values.sortedBy { it.range.start },
                    expressions.filterKeys { it.sourceName == source }.map { (location, type) -> ExpressionType(location.range, type) },
                    calls.filterKeys { it.sourceName == source }.values.sortedBy { it.range.start },
                )
            },
        )
    }

    private fun copyCall(
        node: InvocationExpression,
        binding: InvocationBinding,
    ) {
        val method = binding.method().component as? MethodStructure ?: return
        val selected = signature(method, binding.signature(), visibleOnly = true) ?: return
        val target = symbol(binding.method(), method.name, SymbolKind.METHOD) ?: return
        val site = location(node.source, node.startPosition, node.endPosition)
        val callee = node.invokedExpression
        calls[site] =
            SemanticModel.CallSite(
                site.range,
                location(node.source, callee.startPosition, callee.endPosition).range,
                target,
                selected,
                immutableList(
                    binding.arguments().map {
                        SemanticModel.CallArgument(
                            location(node.source, it.startPosition(), it.endPosition()).range,
                            it.parameterIndex(),
                            it.named(),
                        )
                    },
                ),
                generateSequence(node.parent) { it.parent }
                    .firstOrNull { it in callableNodes || it is PropertyDeclarationStatement || it is TypeCompositionStatement }
                    ?.let(callableNodes::get),
            )
    }

    private fun callable(
        node: AstNode,
        id: SymbolId,
    ) {
        val selection = symbols[id]?.declaration ?: return
        val source = location(node.source, node.startPosition, node.endPosition)
        if (selection.start < source.range.start || selection.end > source.range.end) return
        callableNodes[node] = id
        callables[id] = SemanticModel.Callable(id, source, selection)
    }

    private fun validatedType(expression: Expression?): TypeConstant? = expression?.takeIf { it.isValidated && it.typeFit.isFit }?.type

    fun buildPartial(
        analysis: EmbeddingSupport.PartialAnalysis,
        errors: ErrorListener,
    ): PartialSemanticModel {
        val source = analysis.sites().singleOrNull()?.source ?: return PartialSemanticModel(unavailable(), emptyList())
        val nodes = nodesIn(analysis.sourceTrees())
        collect(nodes, analysis.callBindings())
        val sites =
            analysis.sites().map { site ->
                val parents = generateSequence(site.parent) { it.parent }.toList()
                val owner = parents.filterIsInstance<TypeCompositionStatement>().firstOrNull()?.component as? ClassStructure
                val scope =
                    parents
                        .filterIsInstance<MethodDeclarationStatement>()
                        .firstOrNull()
                        ?.let(::identity)
                        ?.let { constants[it] }
                val receiver = site.receiver.orElse(null)
                val receiverType = validatedType(receiver)
                val cursor = analysis.cursorBindings()[site]
                val identity = (receiver as? NameExpression)?.resolvedTarget
                val staticType =
                    when (identity) {
                        is ClassConstant -> identity.type
                        is TypedefConstant -> identity.referredToType
                        else -> null
                    }
                val lookupKind =
                    when {
                        identity is SingletonConstant -> Lookup.IMPLICIT
                        staticType != null -> Lookup.STATIC
                        else -> Lookup.INSTANCE
                    }
                val callee = (site.target as? NameExpression)?.name.takeIf { site.isCall }
                val locals =
                    cursor?.variables().orEmpty().filter { it.readable() }.mapNotNull { variable ->
                        val id = symbol(variable.register(), variable.name(), SymbolKind.VARIABLE) ?: return@mapNotNull null
                        PartialSemanticModel.Member(id, variable.name(), symbols[id]!!.kind, type(variable.type()), null)
                    }
                val scopeMembers =
                    if (cursor != null && owner != null && (site.isNameCompletion || receiver == null)) {
                        receiverMembers(cursor.thisType(), owner, errors, if (cursor.instance()) Lookup.IMPLICIT else Lookup.STATIC)
                            .filter { member -> cursor.variables().none { it.name() == member.name } }
                    } else {
                        emptyList()
                    }
                val scopeTypes =
                    cursor?.types().orEmpty().mapNotNull { named ->
                        val id = symbol(named.identity(), named.name(), kind(named.identity())) ?: return@mapNotNull null
                        PartialSemanticModel.Member(id, named.name(), symbols[id]!!.kind, type(named.identity().type), null)
                    }
                val members =
                    if (site.isNameCompletion) {
                        locals + scopeMembers.filter { member -> scopeTypes.none { it.name == member.name } } + scopeTypes
                    } else if (site.isCall && receiver == null) {
                        scopeMembers.filter { it.kind == SymbolKind.METHOD && it.name == callee }
                    } else if (receiverType != null && owner != null && !errors.isAbortDesired) {
                        receiverMembers(
                            staticType ?: receiverType,
                            owner,
                            errors,
                            lookupKind,
                        ).filter {
                            !site.isCall || (it.kind == SymbolKind.METHOD && it.name == callee)
                        }
                    } else {
                        emptyList()
                    }
                PartialSemanticModel.Site(
                    when {
                        site.isCall -> PartialSemanticModel.Kind.CALL
                        site.isNameCompletion -> PartialSemanticModel.Kind.NAME
                        else -> PartialSemanticModel.Kind.MEMBER_ACCESS
                    },
                    location(site.source, site.startPosition, site.endPosition).range,
                    location(site.source, site.operator.startPosition, site.operator.endPosition).range,
                    receiver?.let { location(site.source, it.startPosition, it.endPosition).range },
                    type(receiverType),
                    callee,
                    scope,
                    immutableList(
                        site.arguments.map {
                            PartialSemanticModel.Argument(
                                location(site.source, it.startPosition, it.endPosition).range,
                                (it as? LabeledExpression)?.name,
                                type(validatedType(it)),
                            )
                        },
                    ),
                    immutableList(
                        site.separators.map { Position(Source.calculateLine(it.startPosition), Source.calculateOffset(it.startPosition)) },
                    ),
                    immutableList(members),
                    if (site.isCall) {
                        null
                    } else {
                        site.memberName.orElse(null)?.let { name ->
                            PartialSemanticModel.MemberPrefix(
                                name.valueText,
                                location(site.source, name.startPosition, name.endPosition).range,
                            )
                        } ?: PartialSemanticModel.MemberPrefix("", location(site.source, site.endPosition, site.endPosition).range)
                    },
                    cursor?.takeIf { it.callsInspected() }?.candidates()?.let { candidates ->
                        immutableList(
                            candidates.mapNotNull { candidate ->
                                val method = candidate.method().component as? MethodStructure ?: return@mapNotNull null
                                val signature = signature(method, candidate.signature(), visibleOnly = true) ?: return@mapNotNull null
                                val id = symbol(candidate.method(), method.name, SymbolKind.METHOD) ?: return@mapNotNull null
                                PartialSemanticModel.CallCandidate(
                                    PartialSemanticModel.Member(id, method.name, SymbolKind.METHOD, null, signature),
                                    immutableList(
                                        candidate.arguments().map {
                                            SemanticModel.CallArgument(
                                                location(site.source, it.startPosition(), it.endPosition()).range,
                                                it.parameterIndex(),
                                            )
                                        },
                                    ),
                                    candidate.converting(),
                                )
                            },
                        )
                    },
                    site.pendingArgumentName.orElse(null)?.valueText,
                )
            }
        return if (errors.isAbortDesired) {
            PartialSemanticModel(unavailable(), emptyList())
        } else {
            PartialSemanticModel(finish(nodes, false).single { it.sourceName == source.fileName }, sites)
        }
    }

    private enum class Lookup { INSTANCE, STATIC, IMPLICIT }

    private fun receiverMembers(
        receiver: TypeConstant,
        owner: ClassStructure,
        errors: ErrorListener,
        lookupKind: Lookup = Lookup.INSTANCE,
    ): List<PartialSemanticModel.Member> {
        // This explicit inspection owns its diagnostics. Ordinary snapshot extraction stays passive.
        val lookup = ErrorListener.cancellable(ErrorListener.collecting(errors::log), errors::isAbortDesired)
        val info = receiver.ensureTypeInfo(owner.identityConstant, lookup)
        if (lookup.hasSeriousErrors() || lookup.isAbortDesired) return emptyList()
        val privateAccess = info.type.access == Access.PRIVATE
        val methods =
            info.methods.values
                .filter {
                    it.identity.isTopLevel && !it.isCtorOrValidator &&
                        (lookupKind == Lookup.IMPLICIT || it.isFunction == (lookupKind == Lookup.STATIC)) &&
                        (privateAccess || it.isVisible(owner.identityConstant))
                }.mapNotNull { method ->
                    val structure = method.getOptionalTopmostMethodStructure(info) ?: return@mapNotNull null
                    val signature = signature(structure, method.signature, visibleOnly = true) ?: return@mapNotNull null
                    val symbol = symbol(structure.identityConstant, structure.name, SymbolKind.METHOD) ?: return@mapNotNull null
                    PartialSemanticModel.Member(symbol, structure.name, SymbolKind.METHOD, null, signature)
                }
        val properties =
            info
                .ensurePropertiesByName()
                .values
                .filter {
                    (lookupKind != Lookup.STATIC || it.isConstant) && (privateAccess || it.isVisible(owner.identityConstant))
                }.mapNotNull { property ->
                    val type = type(property.inferImmutable(receiver)) ?: return@mapNotNull null
                    val symbol = symbol(property.identity, property.name, SymbolKind.PROPERTY) ?: return@mapNotNull null
                    PartialSemanticModel.Member(symbol, property.name, SymbolKind.PROPERTY, type, null)
                }
        val children =
            info.childInfosByName.values
                .filter {
                    info.type.access.canSee(it.access) || it.identity.classIdentity.isNestMateOf(owner.identityConstant)
                }.mapNotNull { child ->
                    val id = symbol(child.identity, child.name, SymbolKind.TYPE) ?: return@mapNotNull null
                    PartialSemanticModel.Member(id, child.name, SymbolKind.TYPE, type(child.identity.type), null)
                }
        return (methods + properties + children).sortedWith(compareBy({ it.name }, { it.kind }, { it.symbol.index }))
    }

    private fun hierarchy(nodes: List<AstNode>): Map<SymbolId, SemanticModel.TypeDeclaration> =
        buildMap {
            for (node in nodes.filterIsInstance<TypeCompositionStatement>()) {
                val component = node.component as? ClassStructure ?: continue
                val id = constants[component.identityConstant] ?: continue
                if (symbols[id]?.kind != SymbolKind.TYPE) continue
                val parents =
                    component.contributionsAsList
                        .filter {
                            it.composition == Composition.Extends || it.composition == Composition.Implements
                        }.mapNotNull { contribution ->
                            val type = contribution.typeConstant ?: return@mapNotNull null
                            if (type.containsUnresolved()) return@mapNotNull null
                            val parent = type.getSingleUnderlyingClass(true) ?: return@mapNotNull null
                            val target = symbol(parent, parent.name, SymbolKind.TYPE) ?: return@mapNotNull null
                            SemanticModel.Supertype(target, type(type) ?: return@mapNotNull null)
                        }
                put(
                    id,
                    SemanticModel.TypeDeclaration(
                        id,
                        node.category.id.TEXT
                            .orEmpty(),
                        location(node.source, node.startPosition, node.endPosition),
                        immutableList(parents),
                    ),
                )
            }
        }

    private fun declare(
        token: Token?,
        target: Argument?,
        kind: SymbolKind,
        source: Source?,
    ) {
        if (token == null) return
        val location = location(source, token.startPosition, token.endPosition)
        if (location in occurrences) return
        val symbol = symbol(target, token.valueText, kind, location)
        occurrences[location] = Occurrence(location.range, token.valueText, Role.DECLARATION, symbol, symbols[symbol]?.type)
    }

    private fun refer(
        token: Token?,
        target: Argument?,
        expressionType: TypeConstant?,
        source: Source?,
        usage: SemanticModel.Usage? = null,
    ) {
        if (token == null) return
        val location = location(source, token.startPosition, token.endPosition)
        if (location in occurrences) return
        val symbol = symbol(target, token.valueText, kind(target))
        // Failed name validation can leave a required/placeholder type on the expression.
        val type = if (symbol == null) null else type(expressionType) ?: symbols[symbol]?.type
        val access =
            when (symbols[symbol]?.kind) {
                SymbolKind.VARIABLE, SymbolKind.PARAMETER, SymbolKind.PROPERTY -> usage ?: SemanticModel.Usage.READ
                else -> null
            }
        occurrences[location] = Occurrence(location.range, token.valueText, Role.REFERENCE, symbol, type, access)
    }

    private fun symbol(
        argument: Argument?,
        name: String,
        kind: SymbolKind,
        declaration: SourceLocation? = null,
    ): SymbolId? {
        val target = normalized(argument) ?: return null
        val existing = if (target is Register) registers[target] else constants[target as Constant]
        if (existing != null) return existing
        val symbol = SymbolId(id, symbols.size)
        if (target is Register) registers[target] = symbol else constants[target as Constant] = symbol
        symbols[symbol] =
            Symbol(
                symbol,
                name,
                kind,
                declaration?.range,
                type(declaredType(target)),
                signature(target),
                declaration?.sourceName,
                modifiers(target),
            )
        return symbol
    }

    private fun modifiers(target: Argument): Set<SemanticModel.Modifier> =
        immutableSet(
            buildSet {
                if (target is Register && !target.isWritable) add(SemanticModel.Modifier.READONLY)
                val component = (target as? IdentityConstant)?.component
                if (component?.isStatic == true) add(SemanticModel.Modifier.STATIC)
                if (component?.isAbstract == true) add(SemanticModel.Modifier.ABSTRACT)
                if (component is PropertyStructure && component.isConstant) add(SemanticModel.Modifier.READONLY)
            },
        )

    private fun normalized(argument: Argument?): Argument? {
        if (argument is PropertyConstant && argument in capturedProperties) return normalized(capturedProperties[argument])
        if (argument is Register) {
            var register = argument.originalRegister
            val seen = Collections.newSetFromMap(IdentityHashMap<Register, Boolean>())
            while (seen.add(register)) {
                val origin = captureOrigins[register]?.originalRegister
                if (origin == null) {
                    val type = register.type
                    val formal = if (type.isTypeOfType && type.isParamsSpecified) type.getParamType(0) else null
                    if (formal != null && !formal.containsUnresolved() && formal.isSingleDefiningConstant) {
                        val parameter = formal.definingConstant
                        if (parameter is TypeParameterConstant && parameter.register == register.index) return parameter
                    }
                    return register
                }
                register = origin
            }
            return register
        }
        var target = argument
        if (target is TypeConstant && !target.containsUnresolved() && target.isSingleDefiningConstant) target = target.definingConstant
        if (target is Constant && target.containsUnresolved()) return null
        if (target is PseudoConstant) {
            target =
                when (target.format) {
                    Constant.Format.ThisClass, Constant.Format.ParentClass, Constant.Format.ChildClass -> target.declarationLevelClass
                    else -> return null
                }
        }
        return (target as? IdentityConstant)?.takeUnless { it.containsUnresolved() || (it is MethodConstant && it.isNascent) }
    }

    private fun identity(statement: ComponentStatement): IdentityConstant? = statement.component?.identityConstant

    private fun declaredType(target: Argument): TypeConstant? =
        when (target) {
            is Register -> target.type

            is PropertyConstant -> (target.component as? PropertyStructure)?.type

            // its declared callable signature carries parameter/result types
            is MethodConstant -> null

            is IdentityConstant -> if (target.format.isTypeable) target.type else null

            else -> null
        }

    private fun kind(target: Argument?): SymbolKind =
        when (target) {
            is Register -> {
                SymbolKind.VARIABLE
            }

            is Constant -> {
                when (target.format) {
                    Constant.Format.Module -> {
                        SymbolKind.MODULE
                    }

                    Constant.Format.Package -> {
                        SymbolKind.PACKAGE
                    }

                    Constant.Format.Method, Constant.Format.MultiMethod -> {
                        SymbolKind.METHOD
                    }

                    Constant.Format.Property -> {
                        if ((target as PropertyConstant).isFormalType) SymbolKind.TYPE_PARAMETER else SymbolKind.PROPERTY
                    }

                    Constant.Format.TypeParameter, Constant.Format.FormalTypeChild, Constant.Format.DynamicFormal -> {
                        SymbolKind.TYPE_PARAMETER
                    }

                    else -> {
                        SymbolKind.TYPE
                    }
                }
            }

            else -> {
                SymbolKind.VARIABLE
            }
        }

    private fun signature(target: Argument): Signature? {
        val method = ((target as? MethodConstant)?.component as? MethodStructure) ?: return null
        return signature(method, method.identityConstant.signature)
    }

    private fun signature(
        method: MethodStructure,
        signature: SignatureConstant,
        visibleOnly: Boolean = false,
    ): Signature? {
        val parameterTypes = signature.rawParams
        if (parameterTypes.size != method.paramCount) return null
        val parameters =
            method.paramArray.withIndex().drop(if (visibleOnly) method.typeParamCount else 0).map { (index, parameter) ->
                SemanticModel.Parameter(
                    parameter.name,
                    type(parameterTypes[index]) ?: return null,
                    parameter.isTypeParameter,
                    parameter.hasDefaultValue(),
                )
            }
        val returns = signature.rawReturns.map { type(it) ?: return null }
        return Signature(immutableList(parameters), immutableList(returns), method.isConditionalReturn)
    }

    private fun type(constant: TypeConstant?): TypeId? {
        if (constant == null || constant.containsUnresolved()) return null
        typeIds[constant]?.let { return it }
        val id = TypeId(id, typeIds.size)
        typeIds[constant] = id // intern before following recursive type relationships
        val arguments =
            if (constant.isParamsSpecified &&
                !constant.isRelationalType
            ) {
                constant.paramTypes.map { type(it)!! }
            } else {
                emptyList()
            }
        val underlying =
            when {
                constant.isRelationalType -> listOf(type(constant.underlyingType)!!, type(constant.underlyingType2)!!)
                constant.isModifyingType -> listOf(type(constant.underlyingType)!!)
                else -> emptyList()
            }
        val form =
            when (constant.format) {
                Constant.Format.TerminalType -> if (constant.isFormalType) TypeForm.FORMAL else TypeForm.NAMED
                Constant.Format.ParameterizedType -> TypeForm.PARAMETERIZED
                Constant.Format.ImmutableType -> TypeForm.IMMUTABLE
                Constant.Format.ServiceType -> TypeForm.SERVICE
                Constant.Format.AccessType -> TypeForm.ACCESS
                Constant.Format.AnnotatedType -> TypeForm.ANNOTATED
                Constant.Format.UnionType -> TypeForm.UNION
                Constant.Format.IntersectionType -> TypeForm.INTERSECTION
                Constant.Format.DifferenceType -> TypeForm.DIFFERENCE
                Constant.Format.RecursiveType -> TypeForm.RECURSIVE
                else -> TypeForm.OTHER
            }
        types[id] = Type(id, constant.valueString, form, immutableList(arguments), immutableList(underlying), constant.isNullable)
        return id
    }

    /** Resolve only type identity. Modifiers unwrap; relational operands retain multiple targets. */
    private fun typeDefinitions(
        constant: TypeConstant,
        seen: Set<TypeConstant> = emptySet(),
    ): List<SymbolId> {
        if (constant in seen || constant.containsUnresolved()) return emptyList()
        val visited = seen + constant
        val resolved = constant.resolveTypedefs()
        return when {
            resolved != constant -> {
                typeDefinitions(resolved, visited)
            }

            constant.isRelationalType -> {
                val operands =
                    if (constant.format == Constant.Format.DifferenceType) {
                        listOf(constant.underlyingType)
                    } else {
                        listOf(constant.underlyingType, constant.underlyingType2)
                    }
                operands.flatMap { typeDefinitions(it, visited) }
            }

            constant.isModifyingType -> {
                typeDefinitions(constant.underlyingType, visited)
            }

            else -> {
                listOfNotNull(constants[normalized(constant)])
            }
        }.distinct()
    }

    private fun nodesIn(root: AstNode): List<AstNode> = nodesIn(listOf(root))

    private fun nodesIn(roots: List<AstNode>): List<AstNode> {
        val seen = Collections.newSetFromMap(IdentityHashMap<AstNode, Boolean>())
        val nodes = roots.filter { seen.add(it) }.toMutableList()
        var index = 0
        while (index < nodes.size) {
            nodes[index++].children().forEachRemaining { if (seen.add(it)) nodes.add(it) }
        }
        return nodes
    }

    private fun location(
        source: Source?,
        start: Long,
        end: Long,
    ): SourceLocation =
        SourceLocation(
            source?.fileName,
            Range(
                Position(Source.calculateLine(start), Source.calculateOffset(start)),
                Position(Source.calculateLine(end), Source.calculateOffset(end)),
            ),
        )
}
