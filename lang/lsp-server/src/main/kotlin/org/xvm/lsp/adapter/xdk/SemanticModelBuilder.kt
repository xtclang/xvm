package org.xvm.lsp.adapter.xdk

import java.util.Collections
import java.util.IdentityHashMap
import java.util.List.copyOf as immutableList
import java.util.Set.copyOf as immutableSet
import java.util.UUID
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.Argument
import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Composition
import org.xvm.asm.Constant
import org.xvm.asm.ConstantPool
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.MethodStructure
import org.xvm.asm.PackageStructure
import org.xvm.asm.PropertyStructure
import org.xvm.asm.Register
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.FormalConstant
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodConstant
import org.xvm.asm.constants.PropertyConstant
import org.xvm.asm.constants.PseudoConstant
import org.xvm.asm.constants.SignatureConstant
import org.xvm.asm.constants.SingletonConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.TypeParameterConstant
import org.xvm.asm.constants.TypedefConstant
import org.xvm.compiler.CursorBinding
import org.xvm.compiler.InvocationBinding
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.ComponentStatement
import org.xvm.compiler.ast.CompositionNode
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
import org.xvm.compiler.ast.TypeExpression
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
import org.xvm.lsp.util.ExecutionTrace

/**
 * Copy facts while the calling thread exclusively owns the compilation. This does not resume
 * validation or build TypeInfo. The result holds no compiler objects; each call creates a new
 * identity domain, so a host normally keeps one snapshot per document version.
 */
fun EmbeddingSupport.Compilation.semanticSnapshot(): SemanticModel {
    val documents = semanticSnapshots()
    require(documents.size == 1) {
        "Use semanticSnapshots() for a compilation containing multiple sources"
    }
    return documents.single()
}

/** Copy all source views together, sharing symbol/type IDs within this compilation only. */
fun EmbeddingSupport.Compilation.semanticSnapshots(): List<SemanticModel> =
    ExecutionTrace.api("Compilation.semanticSnapshots") {
        val builder = SemanticModelBuilder()
        val pool = pool() ?: return@api builder.build(this)
        return@api ConstantPool.withPool(pool).use { builder.build(this) }
    }

/**
 * Explicit compiler-worker inspection of implementation chains, reporting through the host
 * listener.
 */
fun EmbeddingSupport.Compilation.semanticSnapshots(errors: ErrorListener): List<SemanticModel> =
    ExecutionTrace.api("Compilation.semanticSnapshots(type-info)") {
        val builder = SemanticModelBuilder()
        val pool = pool() ?: return@api builder.build(this)
        return@api ConstantPool.withPool(pool).use { builder.build(this, errors) }
    }

/**
 * Bindings for local renames or partial repairs; never resume failed validation or inspect
 * TypeInfo.
 */
internal fun EmbeddingSupport.Compilation.renameFacts(
    dependencies: XdkDependencies.Open
): CompilerRenameFacts =
    ExecutionTrace.api("Compilation.renameFacts") {
        ConstantPool.withPool(pool()).use {
            val builder =
                SemanticModelBuilder(
                    dependencies.declarations.filterKeys { it.moduleConstant != file()?.moduleId }
                )
            captureRenameFacts(
                builder.build(this),
                builder.constantBindings(),
                dependencies,
                supers = builder.superBindings(),
            )
        }
    }

/** Graph proof facts retain dependency declaration associations and actual dispatch chains. */
internal fun EmbeddingSupport.Compilation.projectRenameFacts(
    dependencies: XdkDependencies.Open,
    errors: ErrorListener,
    includeMembers: Boolean = false,
): CompilerRenameFacts =
    ExecutionTrace.api("Compilation.projectRenameFacts") {
        ConstantPool.withPool(pool()).use {
            val builder =
                SemanticModelBuilder(
                    dependencies.declarations.filterKeys { it.moduleConstant != file()?.moduleId }
                )
            val models = builder.build(this, errors)
            captureRenameFacts(
                models,
                builder.constantBindings(),
                dependencies,
                builder.methodRelations(this, errors),
                builder.propertyRelations(this, errors),
                errors,
                builder.superBindings(),
                if (includeMembers) builder.memberActions(this, errors) else emptyList(),
            )
        }
    }

/**
 * Inspect only a fresh, successfully resolved declaration attempt; never resume a failed compiler.
 */
internal fun EmbeddingSupport.DeclarationAnalysis.memberActionFacts(
    dependencies: XdkDependencies.Open,
    errors: ErrorListener,
): CompilerRenameFacts =
    ExecutionTrace.api("DeclarationAnalysis.memberActionFacts") {
        ConstantPool.withPool(pool()).use {
            val builder =
                SemanticModelBuilder(
                    dependencies.declarations.filterKeys { it.moduleConstant != file().moduleId }
                )
            builder.declarationFacts(this, dependencies, errors)
        }
    }

/**
 * Export only successful attempts, atomically pairing emitted bytes with their own source spans.
 */
fun EmbeddingSupport.Compilation.toDependency(): XdkDependency =
    ExecutionTrace.api("Compilation.toDependency") {
        require(succeeded()) { "A dependency artifact requires successful compilation" }
        return@api ConstantPool.withPool(pool()).use {
            val builder = SemanticModelBuilder()
            builder.build(this)
            XdkDependency.capture(this, builder.declarations())
        }
    }

internal fun EmbeddingSupport.Compilation.semanticSnapshots(
    errors: ErrorListener,
    dependencies: XdkDependencies.Open,
): List<SemanticModel> =
    ExecutionTrace.api("Compilation.semanticSnapshots(dependencies)") {
        ConstantPool.withPool(pool()).use {
            SemanticModelBuilder(
                    dependencies.declarations.filterKeys { it.moduleConstant != file()?.moduleId }
                )
                .build(this, errors)
        }
    }

/**
 * Explicit worker-only member inspection followed by copying. Unlike ordinary semanticSnapshot,
 * this may build receiver TypeInfo and report through [errors]. Never call it on a request thread
 * or concurrently with another compilation using the same repository.
 */
fun EmbeddingSupport.PartialAnalysis.semanticSnapshot(errors: ErrorListener): PartialSemanticModel =
    ExecutionTrace.api("PartialAnalysis.semanticSnapshot") {
        val builder = SemanticModelBuilder()
        if (errors.isAbortDesired)
            return@api PartialSemanticModel(builder.unavailable(), emptyList())
        val pool =
            pool().orElse(null)
                ?: return@api PartialSemanticModel(builder.unavailable(), emptyList())
        return@api ConstantPool.withPool(pool).use { builder.buildPartial(this, errors) }
    }

/** Compiler-worker extraction. All mutable state dies with the builder. */
private class SemanticModelBuilder(
    private val dependencies: Map<IdentityConstant, DependencyDeclaration> = emptyMap()
) {
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
    private val functionCalls = linkedMapOf<SourceLocation, SemanticModel.FunctionCallSite>()
    private val lambdaSites = linkedMapOf<SourceLocation, SemanticModel.LambdaSite>()
    private val callables = linkedMapOf<SymbolId, SemanticModel.Callable>()
    private val callableNodes = IdentityHashMap<AstNode, SymbolId>()
    private val parameters = mutableMapOf<Pair<MethodConstant, Int>, SymbolId>()
    private val supers = mutableMapOf<SymbolId, MethodConstant>()

    fun declarationFacts(
        analysis: EmbeddingSupport.DeclarationAnalysis,
        dependencies: XdkDependencies.Open,
        errors: ErrorListener,
    ): CompilerRenameFacts {
        val nodes = nodesIn(analysis.ast())
        collect(nodes, emptyMap(), emptyMap(), analysis.pool())
        return captureRenameFacts(
            finish(nodes, false, emptyMap(), emptyMap()),
            constantBindings(),
            dependencies,
            compilerMethodRelations(nodes, errors),
            compilerPropertyRelations(nodes, errors),
            errors,
            members = compilerMemberActions(nodes, errors),
        )
    }

    fun constantBindings(): Map<SymbolId, Constant> =
        constants.entries.associate { (constant, id) -> id to constant }

    fun superBindings(): Map<SymbolId, MethodConstant> = supers.toMap()

    fun methodRelations(
        compilation: EmbeddingSupport.Compilation,
        errors: ErrorListener,
    ): CompilerMethodRelations =
        compilerMethodRelations(nodesIn(requireNotNull(compilation.parsed())), errors)

    fun propertyRelations(
        compilation: EmbeddingSupport.Compilation,
        errors: ErrorListener,
    ): CompilerPropertyRelations =
        compilerPropertyRelations(nodesIn(requireNotNull(compilation.parsed())), errors)

    fun memberActions(
        compilation: EmbeddingSupport.Compilation,
        errors: ErrorListener,
    ): List<CompilerMemberAction> =
        compilerMemberActions(nodesIn(requireNotNull(compilation.parsed())), errors)

    fun declarations(): Map<IdentityConstant, SourceLocation> =
        constants.entries
            .mapNotNull { (constant, id) ->
                val identity = constant as? IdentityConstant ?: return@mapNotNull null
                val symbol = symbols.getValue(id)
                val range = symbol.declaration ?: return@mapNotNull null
                if (symbol.declarationSource == null || symbol.dependency != null)
                    return@mapNotNull null
                identity to SourceLocation(symbol.declarationSource, range)
            }
            .toMap()

    fun unavailable(): SemanticModel =
        SemanticModel(
            id,
            Status.UNAVAILABLE,
            null,
            SemanticModel.Facts(symbols, types),
            emptyList(),
            emptyList(),
        )

    fun build(
        compilation: EmbeddingSupport.Compilation,
        errors: ErrorListener? = null,
    ): List<SemanticModel> {
        val root = compilation.parsed() ?: return listOf(unavailable())
        val nodes = nodesIn(root)
        collect(
            nodes,
            compilation.callBindings(),
            compilation.functionBindings(),
            compilation.pool(),
            compilation.succeeded(),
        )
        compilation.constructorBindings().forEach { (node, binding) ->
            copyConstruction(node, binding)
        }
        val implementations =
            if (compilation.succeeded() && errors != null)
                compilerImplementationTargets(nodes, errors)
            else emptyMap()
        // An inherited accessor need not appear in a written call or the consumer's constant table.
        // Inspect its linked compiler identity, never the unlinked artifact used as the index key.
        implementations.values
            .flatten()
            .filter { it in dependencies }
            .forEach { implementation ->
                symbol(implementation, implementation.name, kind(implementation))
            }
        val declarations =
            if (compilation.succeeded() && errors != null) compilerDeclarationTargets(nodes, errors)
            else emptyMap()
        (declarations.keys + declarations.values.flatten()).forEach { identity ->
            symbol(identity, identity.name, kind(identity))
        }
        return finish(nodes, compilation.succeeded(), implementations, declarations)
    }

    private fun collect(
        nodes: List<AstNode>,
        bindings: Map<InvocationExpression, InvocationBinding>,
        functions: Map<InvocationExpression, InvocationBinding.FunctionCall>,
        pool: ConstantPool?,
        complete: Boolean = false,
    ) {
        dependencies.forEach { (identity, declaration) ->
            // Artifact identities supply source associations, not semantic metadata: their pools
            // have not linked core libraries. Read only the corresponding consumer-owned constant.
            val linked = pool?.getConstant(identity) as? IdentityConstant ?: return@forEach
            symbol(linked, linked.name, kind(linked), declaration.location)
        }
        nodes.filterIsInstance<NewExpression>().forEach {
            capturedProperties.putAll(it.captureOrigins)
            it.sourceBindings?.let { bindings -> captureOrigins.putAll(bindings.captureOrigins) }
        }

        val lambdas = nodes.filterIsInstance<LambdaExpression>()
        lambdas.forEach {
            it.sourceBindings?.let { bindings -> captureOrigins.putAll(bindings.captureOrigins) }
        }
        lambdas.forEach { lambda ->
            lambda.sourceBindings?.parameters?.forEach { binding ->
                declare(binding.name(), binding.register(), SymbolKind.PARAMETER, lambda.source)
                (normalized(binding.register()) as? Register)?.let(registers::get)?.let { id ->
                    symbols[id]?.let {
                        // Function types expose positional arguments, not the lambda's written
                        // names.
                        // The original register/source binding remains stable through nested
                        // captures.
                        symbols[id] =
                            it.copy(
                                inferred =
                                    lambda.hasOnlyParamNames() && validatedType(lambda) != null,
                                renameable = complete,
                            )
                    }
                }
            }
            validatedType(lambda)?.let(::functionSignature)?.let { signature ->
                val arrow = lambda.operator
                val at = location(lambda.source, arrow.startPosition, arrow.endPosition)
                lambdaSites[at] = SemanticModel.LambdaSite(at.range, signature)
            }
        }
        // Parameters precede synthetic properties that share their source tokens.
        nodes.filterIsInstance<Parameter>().forEach {
            val method = (it.parent as? MethodDeclarationStatement)?.component as? MethodStructure
            val parameter = method?.params?.singleOrNull { parameter -> parameter.name == it.name }
            if (it.resolvedTarget == null && method != null && parameter != null) {
                // A bodyless method has no register. Its written parameter is still a real source
                // declaration, identified by its resolved signature slot and original source span.
                val at = location(it.source, it.nameToken.startPosition, it.nameToken.endPosition)
                val symbol = SymbolId(id, symbols.size)
                val kind =
                    if (parameter.isTypeParameter) SymbolKind.TYPE_PARAMETER
                    else SymbolKind.PARAMETER
                symbols[symbol] =
                    Symbol(
                        symbol,
                        it.name,
                        kind,
                        at.range,
                        type(parameter.type),
                        null,
                        at.sourceName,
                    )
                occurrences[at] =
                    Occurrence(
                        at.range,
                        it.name,
                        Role.DECLARATION,
                        symbol,
                        symbols.getValue(symbol).type,
                    )
                parameters[method.identityConstant to (parameter.index - method.typeParamCount)] =
                    symbol
                return@forEach
            }
            declare(
                it.nameToken,
                it.resolvedTarget,
                if (it.resolvedTarget is Register) SymbolKind.PARAMETER
                else kind(it.resolvedTarget),
                it.source,
            )
            val register = normalized(it.resolvedTarget) as? Register
            val id = register?.let(registers::get)
            if (method != null && register != null && id != null) {
                parameters[method.identityConstant to (register.index - method.typeParamCount)] = id
            }
        }
        nodes.forEach { node ->
            when (node) {
                is VariableDeclarationStatement -> {
                    declare(node.nameToken, node.register, SymbolKind.VARIABLE, node.source)
                    (normalized(node.register) as? Register)?.let(registers::get)?.let { id ->
                        symbols[id]
                            ?.takeIf { it.kind == SymbolKind.VARIABLE }
                            ?.let { symbols[id] = it.copy(renameable = true) }
                    }
                    if (nodesIn(node).any { it is VariableTypeExpression }) {
                        (normalized(node.register) as? Register)?.let(registers::get)?.let { id ->
                            symbols[id]?.let { symbols[id] = it.copy(inferred = true) }
                        }
                    }
                }

                is MethodDeclarationStatement -> {
                    declare(node.nameToken, identity(node), SymbolKind.METHOD, node.source)
                    identity(node)?.let(constants::get)?.let { id ->
                        symbols[id]?.let {
                            symbols[id] = it.copy(documentation = node.documentation?.trim())
                        }
                    }
                }

                is PropertyDeclarationStatement -> {
                    declare(node.nameToken, identity(node), SymbolKind.PROPERTY, node.source)
                }

                is TypeCompositionStatement -> {
                    // Anonymous classes borrow the constructed type's span for their generated
                    // name. Keep their identity, but never present that span as a declaration:
                    // Packet<String> already contains the written Packet and String references.
                    if (node.parent is NewExpression) {
                        symbol(identity(node), node.name, SymbolKind.TYPE)
                    } else {
                        declare(node.nameToken, identity(node), kind(identity(node)), node.source)
                    }
                }

                is TypedefStatement -> {
                    declare(node.nameToken, identity(node), SymbolKind.TYPE, node.source)
                }
            }
            if (node is InvocationExpression) {
                val callee = node.invokedExpression
                val method = bindings[node]?.method() ?: node.resolvedMethod
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
                    symbol(method.identityConstant, "<lambda>", SymbolKind.METHOD, at)?.let {
                        callable(node, it)
                    }
                }
            }
        }
        val writes =
            compilerWrites(nodes).associate { (name, usage) ->
                location(name.source, name.nameToken.startPosition, name.nameToken.endPosition) to
                    usage
            }
        nodes.forEach { node ->
            val expressionType = validatedType(node as? Expression)
            type(expressionType)?.let {
                expressions[location(node.source, node.startPosition, node.endPosition)] = it
            }
            when (node) {
                is InvocationExpression -> {
                    bindings[node]?.let { copyCall(node, it) }
                    functions[node]?.let { copyFunctionCall(node, it) }
                }

                is NameExpression -> {
                    val at =
                        location(
                            node.source,
                            node.nameToken.startPosition,
                            node.nameToken.endPosition,
                        )
                    // A simple delegate clause is validated through its composition property,
                    // not by validating this NameExpression. Use the compiler-selected identity.
                    val delegate =
                        if (complete && node.isSimpleName) {
                            (node.parent as? CompositionNode.Delegates)
                                ?.takeIf { it.delegatee === node }
                                ?.contribution
                                ?.delegatePropertyConstant
                        } else {
                            null
                        }
                    val target = callees[node] ?: node.resolvedTarget ?: delegate
                    refer(node.nameToken, target, expressionType, node.source, writes[at])
                    if (complete && target is Register && target.isSuper) {
                        val method =
                            generateSequence(node.parent) { it.parent }
                                .filterIsInstance<MethodDeclarationStatement>()
                                .firstOrNull()
                                ?.component as? MethodStructure
                        val symbol = occurrences[at]?.symbol
                        if (method != null && symbol != null)
                            supers[symbol] = method.identityConstant
                    }
                }

                is NamedTypeExpression -> {
                    // Module import names bypass ordinary type-name resolution. Their authoritative
                    // identity is the package's linked imported module, not a spelling lookup.
                    val imported =
                        (node.parent as? CompositionNode.Import)?.let {
                            ((it.parent as? TypeCompositionStatement)?.component
                                    as? PackageStructure)
                                ?.importedModule
                                ?.identityConstant
                        }
                    node.nameBindings.forEach {
                        refer(
                            it.name(),
                            it.target() ?: imported,
                            expressionType.takeIf { _ -> it.name() === node.nameToken },
                            node.source,
                        )
                    }
                }
            }
        }
        parameters.forEach { (parameter, id) ->
            val method = parameter.first.component as? MethodStructure ?: return@forEach
            // Calls through function values are positional (COMPILER-141 rejects named arguments).
            if (method.access == Access.PRIVATE && !method.isConstructor) {
                symbols[id]?.let { symbols[id] = it.copy(renameable = true) }
            }
        }
    }

    private fun finish(
        nodes: List<AstNode>,
        complete: Boolean,
        implementations: Map<IdentityConstant, Set<IdentityConstant>> = emptyMap(),
        declarations: Map<IdentityConstant, Set<IdentityConstant>> = emptyMap(),
    ): List<SemanticModel> {
        val hierarchy = if (complete) hierarchy(nodes) else emptyMap()
        val parameterSlots =
            parameters.entries
                .filter { it.key.second >= 0 }
                .mapNotNull { (binding, id) ->
                    // Partial signatures can have unresolved types: preserve their written symbols
                    // without claiming a validated callable slot for rename.
                    symbol(binding.first, binding.first.name, SymbolKind.METHOD)?.let { owner ->
                        id to SemanticModel.ParameterSlot(owner, binding.second)
                    }
                }
                .toMap()
        // Inferred/narrowed types can name a nominal type that never occurs in written source.
        // Intern those declarations before freezing the tables. Their own declared types may
        // introduce further IDs; process each once without iterating a map being mutated.
        val definitionTargets = buildMap {
            while (true) {
                val pending = typeIds.filterValues { it !in this }
                if (pending.isEmpty()) break
                pending.forEach { (constant, id) -> put(id, typeDefinitions(constant)) }
            }
        }
        val facts =
            SemanticModel.Facts(
                symbols = symbols,
                types = types,
                typeDeclarations = hierarchy,
                typeDefinitions = definitionTargets,
                implementations =
                    implementations.entries
                        .mapNotNull { (target, implementations) ->
                            constants[target]?.let {
                                it to implementations.mapNotNull(constants::get)
                            }
                        }
                        .toMap(),
                callables = callables,
                parameters = parameterSlots,
                declarations =
                    declarations.entries
                        .mapNotNull { (target, contracts) ->
                            constants[target]?.let { it to contracts.mapNotNull(constants::get) }
                        }
                        .toMap(),
            )
        return immutableList(
            nodes
                .map { it.source?.fileName }
                .distinct()
                .map { source ->
                    SemanticModel(
                        id = id,
                        status = if (complete) Status.COMPLETE else Status.PARTIAL,
                        sourceName = source,
                        facts = facts,
                        occurrences =
                            occurrences
                                .filterKeys { it.sourceName == source }
                                .values
                                .sortedBy { it.range.start },
                        expressions =
                            expressions
                                .filterKeys { it.sourceName == source }
                                .map { (location, type) ->
                                    ExpressionType(location.range, type)
                                },
                        calls =
                            calls
                                .filterKeys { it.sourceName == source }
                                .values
                                .sortedBy { it.range.start },
                        functionCalls =
                            functionCalls
                                .filterKeys { it.sourceName == source }
                                .values
                                .sortedBy { it.range.start },
                        imports =
                            if (complete)
                                compilerImportAliases(nodes, source, occurrences, constants)
                            else emptyList(),
                        lambdas =
                            if (complete)
                                lambdaSites.filterKeys { it.sourceName == source }.values.toList()
                            else emptyList(),
                    )
                }
        )
    }

    private fun copyCall(
        node: InvocationExpression,
        binding: InvocationBinding,
    ) {
        val method = binding.method().component as? MethodStructure ?: return
        val selected = signature(method, binding.signature(), visibleOnly = true) ?: return
        val target = symbol(binding.method(), method.name, SymbolKind.METHOD) ?: return
        binding.arguments().forEach { argument ->
            val label = argument.label() ?: return@forEach
            val parameter = parameter(method, argument.parameterIndex()) ?: return@forEach
            val at = location(node.source, label.startPosition(), label.endPosition())
            occurrences[at] =
                Occurrence(
                    at.range,
                    label.name(),
                    Role.REFERENCE,
                    parameter,
                    symbols[parameter]?.type,
                )
        }
        val site = location(node.source, node.startPosition, node.endPosition)
        val callee = node.invokedExpression
        calls[site] =
            SemanticModel.CallSite(
                range = site.range,
                callee = location(node.source, callee.startPosition, callee.endPosition).range,
                method = target,
                signature = selected,
                arguments =
                    immutableList(
                        binding.arguments().map {
                            SemanticModel.CallArgument(
                                location(node.source, it.startPosition(), it.endPosition()).range,
                                it.parameterIndex(),
                                it.named(),
                            )
                        }
                    ),
                caller =
                    generateSequence(node.parent) { it.parent }
                        .firstOrNull {
                            it in callableNodes ||
                                it is PropertyDeclarationStatement ||
                                it is TypeCompositionStatement
                        }
                        ?.let(callableNodes::get),
            )
    }

    private fun parameter(
        method: MethodStructure,
        index: Int,
    ): SymbolId? {
        method.primaryProperty(index)?.let { property ->
            return symbol(property, property.name, SymbolKind.PROPERTY)
        }
        val value = method.params.getOrNull(index + method.typeParamCount) ?: return null
        return parameters.getOrPut(method.identityConstant to index) {
            val id = SymbolId(this.id, symbols.size)
            symbols[id] =
                Symbol(id, value.name, SymbolKind.PARAMETER, null, type(value.type), null, null)
            id
        }
    }

    private fun copyConstruction(
        node: NewExpression,
        binding: InvocationBinding,
    ) {
        val method = binding.method().component as? MethodStructure ?: return
        val target = symbol(binding.method(), method.name, SymbolKind.METHOD) ?: return
        binding.arguments().forEach { argument ->
            val label = argument.label() ?: return@forEach
            val parameter = parameter(method, argument.parameterIndex()) ?: return@forEach
            val at = location(node.source, label.startPosition(), label.endPosition())
            occurrences[at] =
                Occurrence(
                    at.range,
                    label.name(),
                    Role.REFERENCE,
                    parameter,
                    symbols[parameter]?.type,
                )
        }
        // A synthetic shorthand constructor has a compiler-proven owner identity for rename
        // proof, but no fabricated written declaration or source call-hierarchy target.
        if (
            symbols[target]?.declaration == null &&
                !(method.isSynthetic && method.isShorthandConstructor)
        )
            return
        val selected = signature(method, binding.signature(), visibleOnly = true) ?: return
        val at = location(node.source, node.startPosition, node.endPosition)
        calls[at] =
            SemanticModel.CallSite(
                at.range,
                at.range,
                target,
                selected,
                binding.arguments().map {
                    SemanticModel.CallArgument(
                        location(node.source, it.startPosition(), it.endPosition()).range,
                        it.parameterIndex(),
                        it.named(),
                    )
                },
                caller =
                    generateSequence(node.parent) { it.parent }
                        .firstOrNull {
                            it in callableNodes ||
                                it is PropertyDeclarationStatement ||
                                it is TypeCompositionStatement
                        }
                        ?.let(callableNodes::get),
            )
    }

    private fun copyFunctionCall(
        node: InvocationExpression,
        binding: InvocationBinding.FunctionCall,
    ) {
        val signature = functionSignature(binding.type()) ?: return
        val at = location(node.source, node.startPosition, node.endPosition)
        val callee = node.invokedExpression
        functionCalls[at] =
            SemanticModel.FunctionCallSite(
                range = at.range,
                callee = location(node.source, callee.startPosition, callee.endPosition).range,
                signature = signature,
                arguments =
                    immutableList(
                        binding.arguments().map {
                            SemanticModel.CallArgument(
                                location(node.source, it.startPosition(), it.endPosition()).range,
                                it.parameterIndex(),
                            )
                        }
                    ),
            )
    }

    private fun functionSignature(function: TypeConstant): Signature? {
        val pool = function.constantPool
        val parameters = pool.extractFunctionParams(function) ?: return null
        val returns = pool.extractFunctionReturns(function) ?: return null
        return Signature(
            immutableList(
                parameters.map {
                    SemanticModel.Parameter(
                        name = null,
                        type = type(it) ?: return null,
                        typeParameter = false,
                        defaulted = false,
                    )
                }
            ),
            immutableList(returns.map { type(it) ?: return null }),
            false,
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

    private fun validatedType(expression: Expression?): TypeConstant? =
        expression?.takeIf { it.isValidated && it.typeFit.isFit }?.type

    private fun sourceVariable(variable: CursorBinding.Variable): PartialSemanticModel.Member? {
        val id = symbol(variable.register(), variable.name(), SymbolKind.VARIABLE) ?: return null
        return PartialSemanticModel.Member(
            id,
            variable.name(),
            symbols.getValue(id).kind,
            type(variable.type()),
            null,
        )
    }

    private fun sourceProperty(property: CursorBinding.Property): PartialSemanticModel.Member? {
        val id =
            symbol(property.identity(), property.name(), kind(property.identity())) ?: return null
        return PartialSemanticModel.Member(
            id,
            property.name(),
            symbols.getValue(id).kind,
            type(property.type()),
            null,
        )
    }

    fun buildPartial(
        analysis: EmbeddingSupport.PartialAnalysis,
        errors: ErrorListener,
    ): PartialSemanticModel {
        val source =
            analysis.sites().singleOrNull()?.source
                ?: return PartialSemanticModel(unavailable(), emptyList())
        val nodes = nodesIn(analysis.sourceTrees())
        collect(
            nodes,
            analysis.callBindings(),
            analysis.functionBindings(),
            analysis.pool().orElse(null),
        )
        val sites =
            analysis.sites().map { site ->
                val operation = site.argumentCall.orElse(site)
                val parents = generateSequence(site.parent) { it.parent }.toList()
                val owner =
                    parents.filterIsInstance<TypeCompositionStatement>().firstOrNull()?.component
                        as? ClassStructure
                val scope =
                    parents
                        .filterIsInstance<MethodDeclarationStatement>()
                        .firstOrNull()
                        ?.let(::identity)
                        ?.let { constants[it] }
                val receiver = site.receiver.orElse(null)
                val receiverType = validatedType(receiver)
                val cursor = analysis.cursorBindings()[site]
                val callFacts = cursor?.callFacts()
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
                val callee = (operation.target as? NameExpression)?.name.takeIf { operation.isCall }
                val locals =
                    cursor
                        ?.variables()
                        .orEmpty()
                        .filter { it.readable() }
                        .mapNotNull(::sourceVariable)
                val scopeMembers =
                    if (
                        cursor != null &&
                            owner != null &&
                            !site.isTypeCompletion &&
                            (site.isNameCompletion || receiver == null)
                    ) {
                        receiverMembers(
                                cursor.thisType(),
                                owner,
                                errors,
                                if (cursor.instance()) Lookup.IMPLICIT else Lookup.STATIC,
                            )
                            .filter { member ->
                                cursor.variables().none { it.name() == member.name }
                            }
                    } else {
                        emptyList()
                    }
                val scopeTypes =
                    cursor?.types().orEmpty().mapNotNull { named ->
                        val id =
                            symbol(named.identity(), named.name(), kind(named.identity()))
                                ?: return@mapNotNull null
                        PartialSemanticModel.Member(
                            id,
                            named.name(),
                            symbols[id]!!.kind,
                            type(named.type()),
                            null,
                        )
                    }
                val members =
                    if (site.isTypeCompletion) {
                        scopeTypes
                    } else if (site.isNameCompletion) {
                        locals.filter { local -> scopeTypes.none { it.symbol == local.symbol } } +
                            scopeMembers.filter { member ->
                                scopeTypes.none { it.name == member.name }
                            } +
                            scopeTypes
                    } else if (site.isCall && receiver == null) {
                        scopeMembers.filter { it.kind == SymbolKind.METHOD && it.name == callee }
                    } else if (receiverType != null && owner != null && !errors.isAbortDesired) {
                        receiverMembers(
                                staticType ?: receiverType,
                                owner,
                                errors,
                                lookupKind,
                            )
                            .filter {
                                !site.isCall || (it.kind == SymbolKind.METHOD && it.name == callee)
                            }
                    } else {
                        emptyList()
                    }
                PartialSemanticModel.Site(
                    kind =
                        when {
                            operation.isCall -> PartialSemanticModel.Kind.CALL
                            site.isNameCompletion -> PartialSemanticModel.Kind.NAME
                            else -> PartialSemanticModel.Kind.MEMBER_ACCESS
                        },
                    range = location(site.source, site.startPosition, site.endPosition).range,
                    operator =
                        location(
                                site.source,
                                operation.operator.startPosition,
                                operation.operator.endPosition,
                            )
                            .range,
                    receiver =
                        receiver?.let {
                            location(site.source, it.startPosition, it.endPosition).range
                        },
                    receiverType = type(receiverType),
                    calleeName = callee,
                    scope = scope,
                    arguments =
                        immutableList(
                            (operation.leadingArguments + operation.arguments).map {
                                PartialSemanticModel.Argument(
                                    location(site.source, it.startPosition, it.endPosition).range,
                                    (it as? LabeledExpression)?.name,
                                    type(validatedType(it)),
                                )
                            }
                        ),
                    separators =
                        immutableList(
                            operation.separators.map {
                                Position(
                                    Source.calculateLine(it.startPosition),
                                    Source.calculateOffset(it.startPosition),
                                )
                            }
                        ),
                    members = immutableList(members),
                    formals =
                        immutableList(
                            cursor?.formals().orEmpty().mapNotNull { formal ->
                                val bound = formal.constraint()?.let(::type)
                                if (bound == null && formal.writtenConstraint() == null)
                                    return@mapNotNull null
                                val token = formal.name()
                                PartialSemanticModel.Formal(
                                    token.valueText,
                                    bound,
                                    location(site.source, token.startPosition, token.endPosition)
                                        .range,
                                    formal.writtenConstraint(),
                                )
                            }
                        ),
                    memberPrefix =
                        (site.argumentPrefix.orElse(null) ?: site.memberName.orElse(null))?.let {
                            name ->
                            PartialSemanticModel.MemberPrefix(
                                site.completionPrefix,
                                location(site.source, name.startPosition, name.endPosition).range,
                            )
                        }
                            ?: PartialSemanticModel.MemberPrefix(
                                "",
                                location(site.source, site.endPosition, site.endPosition).range,
                            ),
                    callCandidates =
                        callFacts
                            ?.takeIf { it.inspected() }
                            ?.candidates()
                            ?.let { candidates ->
                                immutableList(
                                    candidates.mapNotNull { candidate ->
                                        val method =
                                            candidate.method().component as? MethodStructure
                                                ?: return@mapNotNull null
                                        val declaredSignature =
                                            signature(
                                                method,
                                                candidate.signature(),
                                                visibleOnly = true,
                                            ) ?: return@mapNotNull null
                                        val offset = if (candidate.receiverArgument()) 1 else 0
                                        val signature =
                                            declaredSignature.copy(
                                                parameters =
                                                    declaredSignature.parameters.drop(offset)
                                            )
                                        val id =
                                            symbol(
                                                candidate.method(),
                                                method.name,
                                                SymbolKind.METHOD,
                                            ) ?: return@mapNotNull null
                                        val name =
                                            when {
                                                !method.isConstructor -> {
                                                    method.name
                                                }

                                                method.containingClass.isAnonInnerClass -> {
                                                    "new ${operation.target.childNodes().filterIsInstance<TypeExpression>().single()}"
                                                }

                                                else -> {
                                                    "new ${method.containingClass.name}"
                                                }
                                            }
                                        PartialSemanticModel.CallCandidate(
                                            member =
                                                PartialSemanticModel.Member(
                                                    id,
                                                    name,
                                                    SymbolKind.METHOD,
                                                    null,
                                                    signature,
                                                ),
                                            arguments =
                                                immutableList(
                                                    candidate
                                                        .arguments()
                                                        .filter { it.parameterIndex() >= offset }
                                                        .map {
                                                            SemanticModel.CallArgument(
                                                                location(
                                                                        site.source,
                                                                        it.startPosition(),
                                                                        it.endPosition(),
                                                                    )
                                                                    .range,
                                                                it.parameterIndex() - offset,
                                                            )
                                                        }
                                                ),
                                            converting = candidate.converting(),
                                            constructor = method.isConstructor,
                                        )
                                    }
                                )
                            },
                    pendingArgumentName = operation.pendingArgumentName.orElse(null)?.valueText,
                    functions =
                        immutableList(
                            callFacts?.functions().orEmpty().mapNotNull { candidate ->
                                val signature =
                                    functionSignature(candidate.type()) ?: return@mapNotNull null
                                PartialSemanticModel.FunctionCandidate(
                                    signature,
                                    immutableList(
                                        candidate.arguments().map {
                                            SemanticModel.CallArgument(
                                                location(
                                                        site.source,
                                                        it.startPosition(),
                                                        it.endPosition(),
                                                    )
                                                    .range,
                                                it.parameterIndex(),
                                            )
                                        }
                                    ),
                                )
                            }
                        ),
                    argumentValues =
                        immutableList(
                            callFacts?.argumentValues().orEmpty().mapNotNull(::sourceVariable) +
                                callFacts
                                    ?.argumentProperties()
                                    .orEmpty()
                                    .mapNotNull(::sourceProperty)
                        ),
                    argumentOffset = operation.leadingArguments.size,
                    argumentLiterals = immutableList(callFacts?.argumentLiterals().orEmpty()),
                )
            }
        return if (errors.isAbortDesired) {
            PartialSemanticModel(unavailable(), emptyList())
        } else {
            PartialSemanticModel(
                finish(nodes, false).single { it.sourceName == source.fileName },
                sites,
            )
        }
    }

    private enum class Lookup {
        INSTANCE,
        STATIC,
        IMPLICIT,
    }

    private fun receiverMembers(
        receiver: TypeConstant,
        owner: ClassStructure,
        errors: ErrorListener,
        lookupKind: Lookup = Lookup.INSTANCE,
    ): List<PartialSemanticModel.Member> {
        // This explicit inspection owns its diagnostics. Ordinary snapshot extraction stays
        // passive.
        val lookup =
            ErrorListener.cancellable(ErrorListener.collecting(errors::log), errors::isAbortDesired)
        val info =
            ExecutionTrace.api("TypeConstant.ensureTypeInfo(cursor-members)") {
                receiver.ensureTypeInfo(owner.identityConstant, lookup)
            }
        if (lookup.hasSeriousErrors() || lookup.isAbortDesired) return emptyList()
        val privateAccess = info.type.access == Access.PRIVATE
        val methods =
            info.methods.values
                .filter {
                    it.identity.isTopLevel &&
                        !it.isCtorOrValidator &&
                        (lookupKind == Lookup.IMPLICIT ||
                            it.isFunction == (lookupKind == Lookup.STATIC)) &&
                        (privateAccess || it.isVisible(owner.identityConstant))
                }
                .mapNotNull { method ->
                    val structure =
                        method.getOptionalTopmostMethodStructure(info) ?: return@mapNotNull null
                    val signature =
                        signature(structure, method.signature, visibleOnly = true)
                            ?: return@mapNotNull null
                    val symbol =
                        symbol(structure.identityConstant, structure.name, SymbolKind.METHOD)
                            ?: return@mapNotNull null
                    PartialSemanticModel.Member(
                        symbol,
                        structure.name,
                        SymbolKind.METHOD,
                        null,
                        signature,
                    )
                }
        val properties =
            info
                .ensurePropertiesByName()
                .values
                .filter {
                    (lookupKind != Lookup.STATIC || it.isConstant) &&
                        (privateAccess || it.isVisible(owner.identityConstant))
                }
                .mapNotNull { property ->
                    val type = type(property.inferImmutable(receiver)) ?: return@mapNotNull null
                    val symbol =
                        symbol(property.identity, property.name, SymbolKind.PROPERTY)
                            ?: return@mapNotNull null
                    PartialSemanticModel.Member(
                        symbol,
                        property.name,
                        SymbolKind.PROPERTY,
                        type,
                        null,
                    )
                }
        val children =
            info.childInfosByName.values
                .filter {
                    info.type.access.canSee(it.access) ||
                        it.identity.classIdentity.isNestMateOf(owner.identityConstant)
                }
                .mapNotNull { child ->
                    val id =
                        symbol(child.identity, child.name, SymbolKind.TYPE)
                            ?: return@mapNotNull null
                    PartialSemanticModel.Member(
                        id,
                        child.name,
                        SymbolKind.TYPE,
                        type(child.identity.type),
                        null,
                    )
                }
        return (methods + properties + children).sortedWith(
            compareBy({ it.name }, { it.kind }, { it.symbol.index })
        )
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
                            it.composition == Composition.Extends ||
                                it.composition == Composition.Implements
                        }
                        .mapNotNull { contribution ->
                            val type = contribution.typeConstant ?: return@mapNotNull null
                            if (type.containsUnresolved()) return@mapNotNull null
                            val parent =
                                type.getSingleUnderlyingClass(true) ?: return@mapNotNull null
                            val target =
                                symbol(parent, parent.name, SymbolKind.TYPE)
                                    ?: return@mapNotNull null
                            SemanticModel.Supertype(target, type(type) ?: return@mapNotNull null)
                        }
                put(
                    id,
                    SemanticModel.TypeDeclaration(
                        id,
                        node.category.id.TEXT.orEmpty(),
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
        occurrences[location] =
            Occurrence(
                location.range,
                token.valueText,
                Role.DECLARATION,
                symbol,
                symbols[symbol]?.type,
            )
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
                SymbolKind.VARIABLE,
                SymbolKind.PARAMETER,
                SymbolKind.PROPERTY -> usage ?: SemanticModel.Usage.READ
                else -> null
            }
        occurrences[location] =
            Occurrence(location.range, token.valueText, Role.REFERENCE, symbol, type, access)
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
        val dependency =
            (target as? IdentityConstant)?.let {
                dependencies[it] ?: XdkLibrarySources.declaration(it)
            }
        val location = declaration ?: dependency?.location
        if (target is Register) registers[target] = symbol
        else constants[target as Constant] = symbol
        symbols[symbol] =
            Symbol(
                id = symbol,
                name = name,
                kind = kind,
                declaration = location?.range,
                type = type(declaredType(target)),
                signature = signature(target),
                declarationSource = location?.sourceName,
                modifiers = modifiers(target),
                dependency = dependency?.key,
                documentation =
                    (target as? IdentityConstant)?.component?.documentation?.trim()?.takeIf {
                        it.isNotEmpty()
                    },
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
                if (component is PropertyStructure && component.isConstant)
                    add(SemanticModel.Modifier.READONLY)
            }
        )

    private fun normalized(argument: Argument?): Argument? {
        if (argument is PropertyConstant && argument in capturedProperties)
            return normalized(capturedProperties[argument])
        if (argument is Register) {
            var register = argument.originalRegister
            val seen = Collections.newSetFromMap(IdentityHashMap<Register, Boolean>())
            while (seen.add(register)) {
                val origin = captureOrigins[register]?.originalRegister
                if (origin == null) {
                    val type = register.type
                    val formal =
                        if (type.isTypeOfType && type.isParamsSpecified) type.getParamType(0)
                        else null
                    if (
                        formal != null &&
                            !formal.containsUnresolved() &&
                            formal.isSingleDefiningConstant
                    ) {
                        val parameter = formal.definingConstant
                        if (
                            parameter is TypeParameterConstant &&
                                parameter.register == register.index
                        )
                            return parameter
                    }
                    return register
                }
                register = origin
            }
            return register
        }
        var target = argument
        if (
            target is TypeConstant &&
                !target.containsUnresolved() &&
                target.isSingleDefiningConstant
        )
            target = target.definingConstant
        if (target is Constant && target.containsUnresolved()) return null
        if (target is PseudoConstant) {
            target =
                when (target.format) {
                    Constant.Format.ThisClass,
                    Constant.Format.ParentClass,
                    Constant.Format.ChildClass -> target.declarationLevelClass
                    else -> return null
                }
        }
        return (target as? IdentityConstant)?.takeUnless {
            it.containsUnresolved() || (it is MethodConstant && it.isNascent)
        }
    }

    private fun identity(statement: ComponentStatement): IdentityConstant? =
        statement.component?.identityConstant

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

                    Constant.Format.Method,
                    Constant.Format.MultiMethod -> {
                        SymbolKind.METHOD
                    }

                    Constant.Format.Property -> {
                        if ((target as PropertyConstant).isFormalType) SymbolKind.TYPE_PARAMETER
                        else SymbolKind.PROPERTY
                    }

                    Constant.Format.TypeParameter,
                    Constant.Format.FormalTypeChild,
                    Constant.Format.DynamicFormal -> {
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
            method.paramArray.withIndex().drop(if (visibleOnly) method.typeParamCount else 0).map {
                (index, parameter) ->
                SemanticModel.Parameter(
                    name = parameter.name,
                    type = type(parameterTypes[index]) ?: return null,
                    typeParameter = parameter.isTypeParameter,
                    defaulted = parameter.hasDefaultValue(),
                )
            }
        val returns = signature.rawReturns.map { type(it) ?: return null }
        return Signature(
            immutableList(parameters),
            immutableList(returns),
            method.isConditionalReturn,
        )
    }

    private fun type(constant: TypeConstant?): TypeId? {
        if (constant == null || !copyableType(constant)) return null
        typeIds[constant]?.let {
            return it
        }
        val id = TypeId(id, typeIds.size)
        typeIds[constant] = id // intern before following recursive type relationships
        val arguments =
            if (constant.isParamsSpecified && !constant.isRelationalType) {
                constant.paramTypes.map { type(it)!! }
            } else {
                emptyList()
            }
        val underlying =
            when {
                constant.isRelationalType ->
                    listOf(type(constant.underlyingType)!!, type(constant.underlyingType2)!!)
                constant.isModifyingType -> listOf(type(constant.underlyingType)!!)
                else -> emptyList()
            }
        val form =
            when (constant.format) {
                Constant.Format.TerminalType ->
                    if (constant.isFormalType) TypeForm.FORMAL else TypeForm.NAMED
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
        types[id] =
            Type(
                id,
                constant.valueString,
                form,
                immutableList(arguments),
                immutableList(underlying),
                constant.isNullable,
            )
        return id
    }

    /** A formal identity can be resolved while its written constraint is still unresolved. */
    private fun copyableType(
        constant: TypeConstant,
        seen: Set<TypeConstant> = emptySet(),
    ): Boolean {
        if (constant in seen) return true
        if (constant.containsUnresolved() || !acyclicConstraint(constant)) return false
        val visited = seen + constant
        if (
            constant.isSingleDefiningConstant && constant.definingConstant is TypeParameterConstant
        ) {
            val formal = constant.definingConstant as TypeParameterConstant
            val method = formal.method
            if (method.isNascent || !copyableType(method.rawParams[formal.register], visited))
                return false
        }
        if (
            constant.isParamsSpecified &&
                !constant.isRelationalType &&
                !constant.paramTypes.all { copyableType(it, visited) }
        )
            return false
        return when {
            constant.isRelationalType ->
                copyableType(constant.underlyingType, visited) &&
                    copyableType(constant.underlyingType2, visited)
            constant.isModifyingType -> copyableType(constant.underlyingType, visited)
            else -> true
        }
    }

    /**
     * Nullability follows formal bounds, so T extends T (including indirect cycles) is unsafe. A
     * bound such as Iterable<T> terminates at Iterable; its arguments may legally refer to T. Keep
     * this bound walk separate from the recursive graph copied by copyableType.
     */
    private fun acyclicConstraint(
        constant: TypeConstant,
        seen: Set<TypeConstant> = emptySet(),
    ): Boolean {
        if (constant in seen || constant.containsUnresolved()) return false
        val visited = seen + constant
        val formal =
            if (constant.isSingleDefiningConstant) constant.definingConstant as? FormalConstant
            else null
        return when {
            formal is TypeParameterConstant &&
                (formal.method.isNascent ||
                    formal.method.rawParams[formal.register].containsUnresolved()) -> {
                false
            }

            formal != null -> {
                acyclicConstraint(formal.constraintType, visited)
            }

            constant.isRelationalType -> {
                acyclicConstraint(constant.underlyingType, visited) &&
                    acyclicConstraint(constant.underlyingType2, visited)
            }

            constant.isModifyingType -> {
                acyclicConstraint(constant.underlyingType, visited)
            }

            else -> {
                true
            }
        }
    }

    /**
     * Resolve only type identity. Modifiers unwrap; relational operands retain multiple targets.
     */
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

            constant.isNullable -> {
                // T? navigates to T, not to the Nullable marker that makes the union nullable.
                typeDefinitions(constant.removeNullable(), visited)
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
                val target = normalized(constant)
                listOfNotNull(
                    constants[target]
                        ?: (target as? IdentityConstant)?.let {
                            symbol(
                                it,
                                it.name,
                                if (constant.isFormalType) SymbolKind.TYPE_PARAMETER
                                else SymbolKind.TYPE,
                            )
                        }
                )
            }
        }.distinct()
    }

    private fun nodesIn(root: AstNode): List<AstNode> = nodesIn(listOf(root))

    private fun nodesIn(roots: List<AstNode>): List<AstNode> {
        val seen = Collections.newSetFromMap(IdentityHashMap<AstNode, Boolean>())
        val nodes = roots.filter { seen.add(it) }.toMutableList()
        var index = 0
        while (index < nodes.size) {
            nodes[index++].childNodes().forEach { if (seen.add(it)) nodes.add(it) }
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
