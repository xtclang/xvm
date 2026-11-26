package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.Argument
import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Composition
import org.xvm.asm.Component.Format
import org.xvm.asm.Constant
import org.xvm.asm.ConstantPool
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.MethodStructure
import org.xvm.asm.Op
import org.xvm.asm.PackageStructure
import org.xvm.asm.PropertyStructure
import org.xvm.asm.Register
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.DeferredValueConstant
import org.xvm.asm.constants.FormalConstant
import org.xvm.asm.constants.FormalTypeChildConstant
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodConstant
import org.xvm.asm.constants.ModuleConstant
import org.xvm.asm.constants.PropertyConstant
import org.xvm.asm.constants.PseudoConstant
import org.xvm.asm.constants.SignatureConstant
import org.xvm.asm.constants.SingletonConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.TypeParameterConstant
import org.xvm.asm.constants.TypedefConstant
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AnnotationExpression
import org.xvm.compiler.ast.AssignmentStatement
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.ComponentStatement
import org.xvm.compiler.ast.CompositionNode
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.FunctionTypeExpression
import org.xvm.compiler.ast.GotoStatement
import org.xvm.compiler.ast.IgnoredNameExpression
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.compiler.ast.LabeledExpression
import org.xvm.compiler.ast.LabeledStatement
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.compiler.ast.NewExpression
import org.xvm.compiler.ast.Parameter
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.StatementBlock.TargetInfo
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.compiler.ast.TypeExpression
import org.xvm.compiler.ast.TypedefStatement
import org.xvm.compiler.ast.VariableDeclarationStatement
import org.xvm.compiler.ast.VariableTypeExpression
import org.xvm.compiler.ast.bindings.CursorBinding
import org.xvm.compiler.ast.bindings.InvocationBinding
import org.xvm.compiler.ast.partial.IncompleteLocalDeclaration
import org.xvm.compiler.ast.partial.IncompleteStatement
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
import org.xvm.lsp.adapter.xdk.SemanticModel.TypeCategory
import org.xvm.lsp.adapter.xdk.SemanticModel.TypeForm
import org.xvm.lsp.adapter.xdk.SemanticModel.TypeId
import org.xvm.lsp.util.ExecutionTrace
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import kotlin.jvm.optionals.getOrNull
import java.util.List.copyOf as immutableList
import java.util.Set.copyOf as immutableSet

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
    dependencies: XdkDependencies.Open,
    includeMissingMethods: Boolean = false,
    missingDestinations: Map<SourceLocation, XdkMissingMethods.Destination> = emptyMap(),
): CompilerRenameFacts =
    ExecutionTrace.api("Compilation.renameFacts") {
        ConstantPool.withPool(pool()).use {
            val builder =
                SemanticModelBuilder(
                    dependencies.declarations.filterKeys { it.moduleConstant != file()?.moduleId },
                )
            captureRenameFacts(
                builder.build(this),
                builder.constantBindings(),
                dependencies,
                supers = builder.superBindings(),
                receivers = builder.receiverBindings(),
                missingInputs =
                    if (includeMissingMethods) {
                        missingMethodInputs(
                            dependencies.missingMethodDestinations(missingDestinations),
                        )
                    } else {
                        CompilerMissingInputs()
                    },
                packageImports = packageImports(),
                module = file()?.moduleId?.name,
                anonymousClasses = builder.anonymousClasses(),
            )
        }
    }

/** The module-level package imports as the compiler linked them, with the implicit ecstasy one. */
private fun EmbeddingSupport.Compilation.packageImports(): Map<String, String> =
    parsed()?.let { compilerModuleAliases(it.descendants().toList()) }.orEmpty()

/** Successful emission and detached semantic facts from a single extraction. */
internal class CompiledSemantics(
    val models: List<SemanticModel>,
    val navigation: XdkNavigationIndex.Facts?,
    val artifact: XdkDependency?,
)

/** One compilation's semantic models; once it has succeeded, its artifact and, for [navigation], its rename facts. */
internal fun EmbeddingSupport.Compilation.compiledSemantics(
    dependencies: XdkDependencies.Open,
    errors: ErrorListener,
    navigation: Boolean = false,
    library: Boolean = false,
): CompiledSemantics =
    ExecutionTrace.api("Compilation.compiledSemantics") {
        ConstantPool.withPool(pool()).use {
            val builder =
                SemanticModelBuilder(
                    dependencies.declarations.filterKeys { it.moduleConstant != file()?.moduleId },
                    library,
                )
            val models = builder.build(this, errors)
            val bindings = builder.constantBindings()
            val facts =
                if (navigation && succeeded() && !errors.hasSeriousErrors()) {
                    captureRenameFacts(
                        models,
                        bindings,
                        dependencies,
                        errors = errors,
                        supers = builder.superBindings(),
                        receivers = builder.receiverBindings(),
                        module = file()?.moduleId?.name,
                        anonymousClasses = builder.anonymousClasses(),
                    )
                } else {
                    null
                }
            val artifact =
                if (succeeded() && !errors.hasSeriousErrors()) {
                    XdkDependency.capture(this, builder.declarations(), builder.parameterDeclarations())
                } else {
                    null
                }
            CompiledSemantics(
                models,
                facts?.let { XdkNavigationIndex.Facts(models, it.constants) },
                artifact,
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
                    dependencies.declarations.filterKeys { it.moduleConstant != file()?.moduleId },
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
                builder.receiverBindings(),
                builder.typeNames(this),
                builder.resourceValues(this),
                builder.callableBindings(this),
                if (includeMembers) builder.removableLocals(this) else emptySet(),
                if (includeMembers) builder.extractionFacts(this) else null,
                constantProperties = if (includeMembers) builder.constantProperties(this) else emptySet(),
                packageImports = packageImports(),
                module = file()?.moduleId?.name,
                anonymousClasses = builder.anonymousClasses(),
            )
        }
    }

/**
 * Inspect only a fresh, successfully resolved declaration attempt; never resume a failed compiler.
 */
internal fun EmbeddingSupport.DeclarationAnalysis.memberActionFacts(
    dependencies: XdkDependencies.Open,
    errors: ErrorListener,
    missingMethodInputs: XdkMissingMethods.Inputs = XdkMissingMethods.Inputs(),
    missingDestinations: Map<SourceLocation, XdkMissingMethods.Destination> = emptyMap(),
): CompilerRenameFacts =
    ExecutionTrace.api("DeclarationAnalysis.memberActionFacts") {
        ConstantPool.withPool(pool()).use {
            val builder =
                SemanticModelBuilder(
                    dependencies.declarations.filterKeys { it.moduleConstant != file().moduleId },
                )
            builder.declarationFacts(this, dependencies, errors, missingMethodInputs, missingDestinations)
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
            XdkDependency.capture(this, builder.declarations(), builder.parameterDeclarations())
        }
    }

internal fun EmbeddingSupport.Compilation.semanticSnapshots(
    errors: ErrorListener,
    dependencies: XdkDependencies.Open,
): List<SemanticModel> =
    ExecutionTrace.api("Compilation.semanticSnapshots(dependencies)") {
        ConstantPool.withPool(pool()).use {
            SemanticModelBuilder(
                dependencies.declarations.filterKeys { it.moduleConstant != file()?.moduleId },
            ).build(this, errors)
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
        if (errors.isAbortDesired) {
            return@api PartialSemanticModel(builder.unavailable(), emptyList())
        }
        val pool =
            pool()
                ?: return@api PartialSemanticModel(builder.unavailable(), emptyList())
        return@api ConstantPool.withPool(pool).use { builder.buildPartial(this, errors) }
    }

/** Compiler-worker extraction. All mutable state dies with the builder. */
private class SemanticModelBuilder(
    private val dependencies: Map<IdentityConstant, DependencyDeclaration> = emptyMap(),
    // a bundled library's sources compiled as a whole for navigation: their implementation and declaration targets,
    // a TypeInfo for every type of the core library, cost more memory than a server has
    private val library: Boolean = false,
) {
    private val id = UUID.randomUUID()
    private val captureOrigins = IdentityHashMap<Register, Register>()
    private val capturedProperties = mutableMapOf<PropertyConstant, Register>()
    private val registers = IdentityHashMap<Register, SymbolId>()

    // Registers named with & somewhere: a Ref or Var to them can change them from code that does not name them.
    private val referenced: MutableSet<Register> = Collections.newSetFromMap(IdentityHashMap())
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

    // The this of each property accessor, by the method that names it and the class whose instance it is ([outerThis]).
    private val outerReceivers = linkedMapOf<Pair<IdentityConstant?, IdentityConstant>, SymbolId>()
    private val outerReserved = linkedMapOf<Triple<IdentityConstant?, IdentityConstant, String>, SymbolId>()

    // The span of the type that each anonymous class constructs, which the compiler gives it for a name ([anonymousClasses]).
    private val anonymousClasses = linkedMapOf<IdentityConstant, SourceLocation>()

    fun declarationFacts(
        analysis: EmbeddingSupport.DeclarationAnalysis,
        dependencies: XdkDependencies.Open,
        errors: ErrorListener,
        missingMethodInputs: XdkMissingMethods.Inputs,
        missingDestinations: Map<SourceLocation, XdkMissingMethods.Destination>,
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
            missingMethods =
                compilerMissingMethods(
                    nodes,
                    errors,
                    missingMethodInputs,
                    dependencies.missingMethodDestinations(missingDestinations),
                ),
            module = analysis.file().moduleId.name,
            anonymousClasses = anonymousClasses(),
        )
    }

    fun constantBindings(): Map<SymbolId, Constant> = constants.entries.associate { (constant, id) -> id to constant }

    fun superBindings(): Map<SymbolId, MethodConstant> = supers.toMap()

    /**
     * Each anonymous class, by the span of the type it constructs, as in `new Iterator<Int>() {...}`, which the compiler
     * gives it for a name. It is no declaration, but no other class has it, and a proof places the class there.
     */
    fun anonymousClasses(): Map<IdentityConstant, SourceLocation> = anonymousClasses.toMap()

    fun receiverBindings(): Map<SymbolId, CompilerReceiver> =
        registers.entries
            .mapNotNull { (register, id) ->
                if (register.index !in setOf(Op.A_THIS, Op.A_PUBLIC, Op.A_PROTECTED, Op.A_PRIVATE, Op.A_STRUCT)) return@mapNotNull null
                val type = register.type
                if (!copyableType(type) || !type.isSingleUnderlyingClass(false)) return@mapNotNull null
                id to CompilerReceiver(type.getSingleUnderlyingClass(false), register.index)
            }.toMap() + outerReceivers.entries.associate { (key, id) -> id to CompilerReceiver(key.second, Op.A_THIS) }

    /** A selected declaration alone loses union alternatives and recursive receiver provenance. */
    fun callableBindings(compilation: EmbeddingSupport.Compilation): Map<SourceLocation, Pair<TypeConstant, MethodConstant>> =
        nodesIn(requireNotNull(compilation.parsed()))
            .filterIsInstance<NameExpression>()
            .flatMap { node ->
                val method = (callees[node] ?: node.resolvedTarget) as? MethodConstant ?: return@flatMap emptyList()
                val receiver = node.leftExpression?.let(::validatedType) ?: return@flatMap emptyList()
                if (receiver.isTypeOfType) return@flatMap emptyList()
                val selected = receiver to method
                listOf(
                    location(node.source, node.nameToken.startPosition, node.nameToken.endPosition) to selected,
                    location(node.source, node.startPosition, node.endPosition) to selected,
                )
            }.toMap()

    fun methodRelations(
        compilation: EmbeddingSupport.Compilation,
        errors: ErrorListener,
    ): CompilerMethodRelations = compilerMethodRelations(nodesIn(requireNotNull(compilation.parsed())), errors)

    fun propertyRelations(
        compilation: EmbeddingSupport.Compilation,
        errors: ErrorListener,
    ): CompilerPropertyRelations = compilerPropertyRelations(nodesIn(requireNotNull(compilation.parsed())), errors)

    fun memberActions(
        compilation: EmbeddingSupport.Compilation,
        errors: ErrorListener,
    ): List<CompilerMemberAction> = compilerMemberActions(nodesIn(requireNotNull(compilation.parsed())), errors)

    /** The module that [build] builds the model of, whose identities are its own declarations. */
    private var module: ModuleConstant? = null

    fun declarations(): Map<IdentityConstant, SourceLocation> =
        constants.entries
            .mapNotNull { (constant, id) ->
                val identity = constant as? IdentityConstant ?: return@mapNotNull null
                val symbol = symbols.getValue(id)
                val range = symbol.declaration ?: return@mapNotNull null
                if (symbol.declarationSource == null || symbol.dependency != null) {
                    return@mapNotNull null
                }
                identity to SourceLocation(symbol.declarationSource, range)
            }.toMap()

    /**
     * Where the parameters of the module's own methods are, by method and name, in the source of the method's declaration:
     * where Go to Definition of a named argument goes from a module that depends on this one.
     */
    fun parameterDeclarations(): Map<IdentityConstant, Map<String, Range>> =
        parameters.entries
            .mapNotNull { (slot, id) ->
                val (method, index) = slot
                if (index < 0 || module == null || method.moduleConstant != module) return@mapNotNull null
                val symbol = symbols[id]?.takeIf { it.declarationSource != null } ?: return@mapNotNull null
                Triple(method, symbol.name, symbol.declaration ?: return@mapNotNull null)
            }.groupBy({ it.first }, { it.second to it.third })
            .mapValues { (_, names) -> names.toMap() }

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
        // the XDK's own sources declare a bundled library's identities themselves; a bundled library's read-only copy
        // keeps answering with the bundled declarations' content, as it is the bundled library
        module = compilation.file()?.moduleId?.takeUnless { root.source?.fileName?.let(XdkLibrarySources::isCopy) == true }
        val nodes = nodesIn(root)
        collect(
            nodes,
            compilation.callBindings(),
            compilation.functionBindings(),
            compilation.pool(),
            compilation.succeeded(),
        )
        compilation.initializerBindings().forEach { (owner, binding) ->
            binding.references().forEach { reference ->
                val span = reference.span()
                val at = location(owner.source, span.startPosition(), span.endPosition())
                if (occurrences[at]?.symbol == null) {
                    occurrences.remove(at)
                    refer(reference.name(), at, reference.target(), reference.type())
                }
            }
            binding.expressions().forEach { expression ->
                val span = expression.span()
                type(expression.type())?.let { resolved ->
                    expressions[location(owner.source, span.startPosition(), span.endPosition())] =
                        resolved
                }
            }
            binding.calls().forEach { call ->
                copyCall(
                    owner.source,
                    location(owner.source, call.span().startPosition(), call.span().endPosition()),
                    location(
                        owner.source,
                        call.callee().startPosition(),
                        call.callee().endPosition(),
                    ).range,
                    call.binding(),
                    callableNodes[owner],
                    call.construction(),
                )
            }
        }
        compilation.constructorBindings().forEach { (node, binding) ->
            copyConstruction(node, binding)
        }
        val implementations =
            if (!library && compilation.succeeded() && errors != null) {
                compilerImplementationTargets(nodes, errors)
            } else {
                emptyMap()
            }
        // An inherited accessor need not appear in a written call or the consumer's constant table.
        // Inspect its linked compiler identity, never the unlinked artifact used as the index key.
        implementations.values
            .flatten()
            .filter { it in dependencies }
            .forEach { implementation ->
                symbol(implementation, implementation.name, kind(implementation))
            }
        val declarations =
            if (!library && compilation.succeeded() && errors != null) {
                compilerDeclarationTargets(nodes, errors)
            } else {
                emptyMap()
            }
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
            // Conditional incorporation names refer to the mixin's registered formals. Their
            // parser representation is a Parameter, but they do not declare another formal.
            if (it.parent is CompositionNode.Incorporates) return@forEach
            // A name in a function type, as of its return values in function (String name, String password)(String),
            // labels a value to which no code refers: a call through a function value takes and returns by position.
            if (it.parent is FunctionTypeExpression) {
                val token = it.nameToken ?: return@forEach
                val at = location(it.source, token.startPosition, token.endPosition)
                val symbol = SymbolId(id, symbols.size)
                val declared = it.type.ensureTypeConstant().takeUnless { type -> type.containsUnresolved() }
                symbols[symbol] =
                    Symbol(symbol, it.name, SymbolKind.PARAMETER, at.range, type(declared), null, at.sourceName, renameable = true)
                occurrences[at] = Occurrence(at.range, it.name, Role.DECLARATION, symbol, symbols.getValue(symbol).type)
                return@forEach
            }
            val declaration = it.parent as? MethodDeclarationStatement
            val method = declaration?.component as? MethodStructure
            // A name of a method's return values, as in (Int count, String label) pair(), declares the value it
            // names, which no code refers to; the method's structure holds the values after a conditional's Boolean.
            val returned = declaration?.returns?.indexOfFirst { value -> value === it } ?: -1
            if (returned >= 0) {
                val value = method?.returns?.getOrNull(returned + if (method.isConditionalReturn) 1 else 0)
                val token = it.nameToken
                if (value == null || token == null) {
                    declare(token, null, SymbolKind.PARAMETER, it.source)
                    return@forEach
                }
                val at = location(it.source, token.startPosition, token.endPosition)
                val symbol = SymbolId(id, symbols.size)
                symbols[symbol] =
                    Symbol(symbol, it.name, SymbolKind.PARAMETER, at.range, type(value.type), null, at.sourceName, renameable = true)
                occurrences[at] = Occurrence(at.range, it.name, Role.DECLARATION, symbol, symbols.getValue(symbol).type)
                return@forEach
            }
            val parameter = method?.params?.singleOrNull { parameter -> parameter.name == it.name }
            if (it.resolvedTarget == null && method != null && parameter != null) {
                // A bodyless method has no register. Its written parameter is still a real source
                // declaration, identified by its resolved signature slot and original source span.
                val at = location(it.source, it.nameToken.startPosition, it.nameToken.endPosition)
                val symbol = SymbolId(id, symbols.size)
                val kind =
                    if (parameter.isTypeParameter) {
                        SymbolKind.TYPE_PARAMETER
                    } else {
                        SymbolKind.PARAMETER
                    }
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
                if (it.resolvedTarget is Register) {
                    SymbolKind.PARAMETER
                } else {
                    kind(it.resolvedTarget)
                },
                it.source,
            )
            val register = normalized(it.resolvedTarget) as? Register
            val id = register?.let(registers::get)
            if (method != null && register != null && id != null) {
                parameters[method.identityConstant to (register.index - method.typeParamCount)] = id
            }
        }
        bindFinallyParameters(nodes)
        bindLabels(nodes)
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

                is PropertyDeclarationStatement -> declare(node.nameToken, identity(node), SymbolKind.PROPERTY, node.source)

                is TypeCompositionStatement -> {
                    // Anonymous classes borrow the constructed type's span for their generated
                    // name. Keep their identity, but never present that span as a declaration:
                    // Packet<String> already contains the written Packet and String references.
                    if (node.parent is NewExpression) {
                        symbol(identity(node), node.name, SymbolKind.TYPE)
                        identity(node)?.let {
                            anonymousClasses[it] = location(node.source, node.nameToken.startPosition, node.nameToken.endPosition)
                        }
                    } else {
                        declare(node.nameToken, identity(node), kind(identity(node)), node.source)
                    }
                }

                is TypedefStatement -> declare(node.nameToken, identity(node), SymbolKind.TYPE, node.source)
            }
            if (node is InvocationExpression) {
                val callee = node.invokedExpression
                val method = bindings[node]?.method() ?: node.resolvedMethod
                // The name of a function bound at a register, as in &super(_) or &fn(_), is resolved by the
                // invocation alone. A super with a method to dispatch to has it as the invocation's method.
                val target = method ?: node.boundRegister
                if (callee is NameExpression && target != null) callees[callee] = target
            }
            recordHeaderStart(node)
        }
        nodes.forEach { node ->
            when (node) {
                is MethodDeclarationStatement -> identity(node)?.let(constants::get)?.let { callable(node, it) }

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
                is Parameter -> {
                    val incorporation = node.parent as? CompositionNode.Incorporates ?: return@forEach
                    val type = incorporation.contribution?.typeConstant
                    val owner =
                        type
                            ?.takeIf { it.isSingleUnderlyingClass(false) }
                            ?.getSingleUnderlyingClass(false)
                            ?.component as? ClassStructure
                    val formal = (owner?.getChild(node.name) as? PropertyStructure)?.takeIf { it.isGenericTypeParameter }
                    refer(node.nameToken, formal?.identityConstant, formal?.type, node.source)
                }

                is InvocationExpression -> {
                    bindings[node]?.let { copyCall(node, it) }
                    functions[node]?.let { copyFunctionCall(node, it) }
                }

                // The _ placeholder, as in (_, String label) = pair(), names nothing: it is no occurrence.
                is IgnoredNameExpression -> {}

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
                    if (target is TargetInfo && (node.name == "this" || node.name.startsWith("this:"))) {
                        val symbol = if (node.name == "this") outerThis(node, target) else outerReserved(node, target)
                        if (at !in occurrences) {
                            val type = type(expressionType) ?: symbols[symbol]?.type
                            occurrences[at] = Occurrence(at.range, node.name, Role.REFERENCE, symbol, type, SemanticModel.Usage.READ)
                        }
                    } else {
                        // this.Outer, or this.Host in a property's class, names the outer instance by a class, which
                        // the compiler reaches as an outer TargetInfo: the name is an occurrence of the class it spells,
                        // ecstasy's Outer interface, which any virtual child may name, as this.Host in a method is of Host
                        val named =
                            (target as? TargetInfo)?.let { outer ->
                                outer.id.takeIf { it.name == node.name } ?: pool?.clzOuter()?.takeIf { it.name == node.name }
                            }
                        val property = (target as? Register)?.let { headerProperty(node, it) }
                        refer(node.nameToken, named ?: property ?: target, expressionType, node.source, writes[at])
                    }
                    // &fn(_) binds the function that fn holds; it makes no Ref to fn.
                    if (node.isSuppressDeref && node.resolvedTarget != null) (normalized(target) as? Register)?.let(referenced::add)
                    if (complete && target is Register && target.isSuper) {
                        val method =
                            generateSequence(node.parent) { it.parent }
                                .filterIsInstance<MethodDeclarationStatement>()
                                .firstOrNull()
                                ?.component as? MethodStructure
                        val symbol = occurrences[at]?.symbol
                        if (method != null && symbol != null) {
                            supers[symbol] = method.identityConstant
                        }
                    }
                }

                is NamedTypeExpression -> {
                    // Module import names bypass ordinary type-name resolution. Their authoritative
                    // identity is the package's linked imported module, not a spelling lookup.
                    val imported =
                        (node.parent as? CompositionNode.Import)?.let {
                            (
                                (it.parent as? TypeCompositionStatement)?.component
                                    as? PackageStructure
                            )?.importedModule
                                ?.identityConstant
                        }
                    // A dynamic type, such as t1.DataType, or type in new Pair<String, type>(), names a value's
                    // formal type: the compiler validates its names as expressions, which bind the first to the local
                    // or parameter that it spells, also where the type binding is that value's type.
                    val dynamic =
                        generateSequence(node.dynamicExpression) { it.leftExpression as? NameExpression }
                            .associate { it.nameToken to it.resolvedTarget }
                    node.nameBindings.forEach {
                        refer(
                            it.name(),
                            dynamic[it.name()] ?: it.target() ?: imported,
                            expressionType.takeIf { _ -> it.name() === node.nameToken },
                            node.source,
                            annotation =
                                node.parent is AnnotationExpression && it.name() === node.nameToken &&
                                    (normalized(it.target()) as? ClassConstant)?.component?.format == Format.ANNOTATION,
                        )
                    }
                }
            }
        }
        parameters.forEach { (parameter, id) ->
            val method = parameter.first.component as? MethodStructure ?: return@forEach
            // Calls through function values are positional (COMPILER-141 rejects named arguments). A constructor's
            // finally block is a private method over the constructor's parameters, which named arguments name.
            if (method.access == Access.PRIVATE && !method.isConstructor && !method.isConstructorFinalizer) {
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
        val sourceLinks =
            if (complete) {
                compilerSourceLinks(nodes) { identity ->
                    val target = symbol(identity, identity.name, kind(identity))?.let(symbols::get)
                    target?.declaration?.let { SourceLocation(target.declarationSource, it) }
                }
            } else {
                emptyMap()
            }
        val parameterSlots =
            parameters.entries
                .filter { it.key.second >= 0 }
                .mapNotNull { (binding, id) ->
                    // Partial signatures can have unresolved types: preserve their written symbols
                    // without claiming a validated callable slot for rename.
                    symbol(binding.first, binding.first.name, SymbolKind.METHOD)?.let { owner ->
                        id to SemanticModel.ParameterSlot(owner, binding.second)
                    }
                }.toMap()
        // Inferred/narrowed types can name a nominal type that never occurs in written source.
        // Intern those declarations before freezing the tables. Their own declared types may
        // introduce further IDs; process each once without iterating a map being mutated.
        val definitionTargets =
            buildMap {
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
                        }.toMap(),
                callables = callables,
                parameters = parameterSlots,
                declarations =
                    declarations.entries
                        .mapNotNull { (target, contracts) ->
                            constants[target]?.let { it to contracts.mapNotNull(constants::get) }
                        }.toMap(),
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
                            if (complete) {
                                compilerImportAliases(nodes, source, occurrences, constants)
                            } else {
                                emptyList()
                            },
                        lambdas =
                            if (complete) {
                                lambdaSites.filterKeys { it.sourceName == source }.values.toList()
                            } else {
                                emptyList()
                            },
                        sourceLinks = sourceLinks[source].orEmpty(),
                    )
                },
        )
    }

    private fun copyCall(
        node: InvocationExpression,
        binding: InvocationBinding,
    ) {
        copyCall(
            node.source,
            location(node.source, node.startPosition, node.endPosition),
            location(
                node.source,
                node.invokedExpression.startPosition,
                node.invokedExpression.endPosition,
            ).range,
            binding,
            caller(node),
        )
    }

    private fun caller(node: AstNode): SymbolId? =
        generateSequence(node.parent) { it.parent }
            .firstOrNull {
                it in callableNodes ||
                    it is PropertyDeclarationStatement ||
                    it is TypeCompositionStatement
            }?.let(callableNodes::get)

    private fun copyCall(
        source: Source?,
        site: SourceLocation,
        callee: Range,
        binding: InvocationBinding,
        caller: SymbolId?,
        construction: Boolean = false,
    ) {
        val method = binding.method().component as? MethodStructure ?: return
        val target = symbol(binding.method(), method.name, SymbolKind.METHOD) ?: return
        binding.arguments().forEach { argument ->
            val label = argument.label() ?: return@forEach
            val parameter = parameter(method, argument.parameterIndex()) ?: return@forEach
            val at = location(source, label.startPosition(), label.endPosition())
            occurrences[at] =
                Occurrence(
                    at.range,
                    label.name(),
                    Role.REFERENCE,
                    parameter,
                    symbols[parameter]?.type,
                )
        }
        // Synthetic shorthand constructors have compiler-proven identity, but no invented
        // declaration.
        if (
            construction &&
            symbols[target]?.declaration == null &&
            !(method.isSynthetic && method.isShorthandConstructor)
        ) {
            return
        }
        val selected = signature(method, binding.signature(), visibleOnly = true) ?: return
        calls[site] =
            SemanticModel.CallSite(
                range = site.range,
                callee = callee,
                method = target,
                signature = selected,
                arguments =
                    immutableList(
                        binding.arguments().map {
                            SemanticModel.CallArgument(
                                location(source, it.startPosition(), it.endPosition()).range,
                                it.parameterIndex(),
                                it.named(),
                            )
                        },
                    ),
                caller = caller,
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
            // a parameter of another module's method, as a named argument names it, is where that method's source has it
            val declared = dependency(method.identityConstant)?.takeIf { value.name in it.parameters }
            symbols[id] =
                Symbol(
                    id,
                    value.name,
                    SymbolKind.PARAMETER,
                    declared?.parameters?.get(value.name),
                    type(value.type),
                    null,
                    declared?.location?.sourceName,
                )
            id
        }
    }

    private fun copyConstruction(
        node: NewExpression,
        binding: InvocationBinding,
    ) {
        val at = location(node.source, node.startPosition, node.endPosition)
        copyCall(node.source, at, at.range, binding, caller(node), construction = true)
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
                        },
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
                },
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

    private fun validatedType(expression: Expression?): TypeConstant? = expression?.takeIf { it.isValidated && it.typeFit.isFit }?.type

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
            analysis.pool(),
        )
        val sites =
            analysis.sites().map { site ->
                val operation: IncompleteStatement = site.argumentCall.getOrNull() ?: site
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
                        ).filter { member ->
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
                        ).filter {
                            !site.isCall || (it.kind == SymbolKind.METHOD && it.name == callee)
                        }
                    } else {
                        emptyList()
                    }
                PartialSemanticModel.Site(
                    kind =
                        when {
                            site.declarationType.isPresent -> PartialSemanticModel.Kind.DECLARATION_NAME
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
                        ).range,
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
                            },
                        ),
                    separators =
                        immutableList(
                            operation.separators.map {
                                Position(
                                    Source.calculateLine(it.startPosition),
                                    Source.calculateOffset(it.startPosition),
                                )
                            },
                        ),
                    members = immutableList(members),
                    declarationNameType =
                        site.declarationType
                            .map {
                                XdkSyntaxCompletions.declarationName(it, (site.parent as? IncompleteLocalDeclaration)?.initializer)
                            }.orElse(null),
                    formals =
                        immutableList(
                            cursor?.formals().orEmpty().mapNotNull { formal ->
                                val bound = formal.constraint()?.let(::type)
                                if (bound == null && formal.writtenConstraint() == null) {
                                    return@mapNotNull null
                                }
                                val token = formal.name()
                                PartialSemanticModel.Formal(
                                    token.valueText,
                                    bound,
                                    location(site.source, token.startPosition, token.endPosition)
                                        .range,
                                    formal.writtenConstraint(),
                                )
                            },
                        ),
                    memberPrefix =
                        (site.argumentPrefix.orElse(null) ?: site.memberName.orElse(null))?.let { name ->
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
                                                    declaredSignature.parameters.drop(offset),
                                            )
                                        val id =
                                            symbol(
                                                candidate.method(),
                                                method.name,
                                                SymbolKind.METHOD,
                                            ) ?: return@mapNotNull null
                                        val name =
                                            when {
                                                !method.isConstructor -> method.name

                                                method.containingClass.isAnonInnerClass ->
                                                    "new ${operation.target.childNodes().filterIsInstance<TypeExpression>().single()}"

                                                else -> "new ${method.containingClass.name}"
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
                                                                ).range,
                                                                it.parameterIndex() - offset,
                                                            )
                                                        },
                                                ),
                                            converting = candidate.converting(),
                                            constructor = method.isConstructor,
                                        )
                                    },
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
                                                ).range,
                                                it.parameterIndex(),
                                            )
                                        },
                                    ),
                                )
                            },
                        ),
                    argumentValues =
                        immutableList(
                            callFacts?.argumentValues().orEmpty().mapNotNull(::sourceVariable) +
                                callFacts
                                    ?.argumentProperties()
                                    .orEmpty()
                                    .mapNotNull(::sourceProperty),
                        ),
                    argumentOffset = operation.leadingArguments.size,
                    argumentLiterals = immutableList(callFacts?.argumentLiterals().orEmpty()),
                    argumentExpressions = immutableList(callFacts?.argumentExpressions().orEmpty()),
                    enclosingExpressions = immutableList(cursor?.enclosingExpressions().orEmpty()),
                    argumentTemplates = immutableList(cursor?.argumentTemplates().orEmpty()),
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

    /** Member lookup context used to distinguish instance, static and implicit receivers. */
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
                        (
                            lookupKind == Lookup.IMPLICIT ||
                                it.isFunction == (lookupKind == Lookup.STATIC)
                        ) &&
                        (privateAccess || it.isVisible(owner.identityConstant))
                }.mapNotNull { method ->
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
                }.mapNotNull { property ->
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
                }.mapNotNull { child ->
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
            compareBy({ it.name }, { it.kind }, { it.symbol.index }),
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
                        }.mapNotNull { contribution ->
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
                        node.category.id.TEXT
                            .orEmpty(),
                        location(node.source, node.startPosition, node.endPosition),
                        immutableList(parents),
                    ),
                )
            }
        }

    private fun recordHeaderStart(node: AstNode) {
        val declaration =
            when (node) {
                is TypeCompositionStatement -> node.takeUnless { it.parent is NewExpression }
                is MethodDeclarationStatement -> node
                is PropertyDeclarationStatement -> node
                is TypedefStatement -> node
                else -> null
            } ?: return
        val target = declaration.component?.takeUnless { it.isSynthetic }?.identityConstant ?: return
        val id = constants[target] ?: return
        val symbol = symbols[id] ?: return
        val start = location(declaration.source, declaration.startPosition, declaration.startPosition).range.start
        symbols[id] = symbol.copy(headerStart = start)
    }

    /**
     * The property that a primary constructor's parameter initializes, for [node], a name in an extends or incorporates
     * clause's arguments that the compiler bound to [register]: it validates the arguments in the class's synthetic
     * shorthand constructor, whose parameters no source declares apart from the header's, which declare the properties.
     */
    private fun headerProperty(
        node: NameExpression,
        register: Register,
    ): PropertyConstant? {
        val chain = generateSequence<AstNode>(node) { it.parent }.toList()
        val header = chain.indexOfFirst { it is TypeCompositionStatement }
        if (header < 1 || chain[header - 1] !is CompositionNode || chain.take(header).any { it is LambdaExpression }) return null
        val owner = (chain[header] as TypeCompositionStatement).component as? ClassStructure ?: return null
        val constructor = owner.findMethod("construct", MethodStructure::isShorthandConstructor) ?: return null
        return constructor.primaryProperty(register.index - constructor.typeParamCount)?.takeIf { it.name == node.name }
    }

    /**
     * The symbol of `this` in a property's accessor, such as a lazy property's `calc()`: the instance that holds the
     * property, which the compiler reaches as an outer this ([TargetInfo]) that names its class rather than a register.
     * Each method has its own, as each has its own this register, and a proof knows it as that class's receiver.
     */
    private fun outerThis(
        node: AstNode,
        receiver: TargetInfo,
    ): SymbolId =
        outerReceivers.getOrPut(enclosingMethod(node) to receiver.id) {
            SymbolId(id, symbols.size).also {
                symbols[it] =
                    Symbol(it, "this", SymbolKind.VARIABLE, null, type(receiver.type), null, null, setOf(SemanticModel.Modifier.READONLY))
            }
        }

    /**
     * The symbol of a reserved name other than `this`, such as `this:class`, in a property's accessor, which the compiler
     * reaches through the instance that holds the property ([TargetInfo]), as it does `this` ([outerThis]). In a method of
     * the class it is a register; here, too, each method has its own, which no proof binds, as it binds no such register.
     */
    private fun outerReserved(
        node: NameExpression,
        target: TargetInfo,
    ): SymbolId =
        outerReserved.getOrPut(Triple(enclosingMethod(node), target.id, node.name)) {
            SymbolId(id, symbols.size).also {
                symbols[it] =
                    Symbol(it, node.name, SymbolKind.VARIABLE, null, type(target.type), null, null, setOf(SemanticModel.Modifier.READONLY))
            }
        }

    private fun enclosingMethod(node: AstNode): IdentityConstant? =
        generateSequence(node.parent) { it.parent }
            .filterIsInstance<MethodDeclarationStatement>()
            .firstOrNull()
            ?.let(::identity)

    /**
     * Binds the parameters of a constructor with a `finally` block to the parameters it declares, in the constructor's
     * body and in a call's named argument. The block is a method of its own, which the compiler compiles after the
     * constructor over the same written parameters, so each parameter's node is the block's and keeps the block's
     * register (StatementBlock.RootContext): the body's names, which the constructor's registers resolve, and a named
     * argument of the constructor had symbols of their own with no declaration. Find References and highlights of a
     * parameter missed them, Go to Definition went nowhere from them, and a rename of the parameter was refused. The
     * constructor's slot of each parameter is registered after the block's, so the parameter is the constructor's.
     */
    private fun bindFinallyParameters(nodes: List<AstNode>) {
        val constructors =
            nodes
                .filterIsInstance<MethodDeclarationStatement>()
                .filter { statement ->
                    val constructor = statement.takeIf { it.isConstructor }?.component as? MethodStructure ?: return@filter false
                    val block = statement.constructorFinally?.component as? MethodStructure ?: return@filter false
                    for (slot in 0 until constructor.paramCount - constructor.typeParamCount) {
                        parameters[block.identityConstant to slot]?.let { parameters[constructor.identityConstant to slot] = it }
                    }
                    true
                }.toCollection(Collections.newSetFromMap(IdentityHashMap()))
        if (constructors.isEmpty()) return
        nodes.filterIsInstance<NameExpression>().forEach { name ->
            val register = normalized(name.resolvedTarget) as? Register ?: return@forEach
            if (register in registers) return@forEach
            // the method, lambda or class whose body the name is in; a lambda's captures and a class's are their own
            val scope =
                generateSequence(name.parent) { it.parent }.firstOrNull {
                    it is MethodDeclarationStatement || it is LambdaExpression || it is TypeCompositionStatement
                }
            if (scope !in constructors) return@forEach
            val method = (scope as MethodDeclarationStatement).component as MethodStructure
            // the constructor's register of each of its parameters has the parameter's name and index
            val parameter = method.params.getOrNull(register.index)?.takeUnless { it.isTypeParameter } ?: return@forEach
            if (parameter.name != register.name) return@forEach
            parameters[method.identityConstant to (register.index - method.typeParamCount)]?.let { registers[register] = it }
        }
    }

    /**
     * Declares each label, as Loop in Loop: for (...), and binds the names that refer to it: a break or continue that
     * names it, and its variable, as Loop in Loop.first, which the compiler registers anew each time it validates the
     * statement (LabeledStatement.LabelVar). No occurrence held a label, and its variable had a symbol of its own with
     * no declaration: hover, highlights, Find References, Go to Definition and a rename missed it. The compiler refuses
     * a label of a name that a variable or an enclosing label has, so the label that a name refers to is the innermost
     * one of its name around it.
     */
    private fun bindLabels(nodes: List<AstNode>) {
        val statements = nodes.filterIsInstance<LabeledStatement>()
        if (statements.isEmpty()) return
        val labels = IdentityHashMap<LabeledStatement, SymbolId>()
        statements.forEach { statement ->
            val symbol = SymbolId(id, symbols.size)
            val at = named(statement.source, statement.startPosition, statement.name, ending = false)
            symbols[symbol] = Symbol(symbol, statement.name, SymbolKind.VARIABLE, at.range, null, null, at.sourceName, renameable = true)
            labels[statement] = symbol
        }

        fun label(
            node: AstNode,
            name: String,
        ) = generateSequence(node.parent) { it.parent }.filterIsInstance<LabeledStatement>().firstOrNull { it.name == name }
        val variables =
            nodes.filterIsInstance<NameExpression>().mapNotNull { name ->
                val variable = name.resolvedTarget as? LabeledStatement.LabelVar ?: return@mapNotNull null
                // a label that the walked tree does not hold, as one of a copy the compiler validated, has no symbol
                registers[variable] = label(name, name.name)?.let(labels::get) ?: return@mapNotNull null
                variable
            }
        // every label's variable has the type Label, which a compile has where a name refers to one
        val type = type(variables.firstOrNull()?.type)
        statements.forEach { statement ->
            val symbol = labels.getValue(statement)
            val label = symbols.getValue(symbol).copy(type = type)
            symbols[symbol] = label
            val at = SourceLocation(label.declarationSource, requireNotNull(label.declaration))
            if (at !in occurrences) occurrences[at] = Occurrence(at.range, statement.name, Role.DECLARATION, symbol, type)
        }
        nodes.filterIsInstance<GotoStatement>().forEach { jump ->
            val name = jump.labeledName ?: return@forEach
            val symbol = label(jump, name)?.let(labels::get) ?: return@forEach
            val at = named(jump.source, jump.endPosition, name, ending = true)
            if (at !in occurrences) occurrences[at] = Occurrence(at.range, name, Role.REFERENCE, symbol, symbols[symbol]?.type)
        }
    }

    /** Where [name] is written in [source], starting at [position], or ending there; a name is on one line. */
    private fun named(
        source: Source?,
        position: Long,
        name: String,
        ending: Boolean,
    ): SourceLocation {
        val line = Source.calculateLine(position)
        val column = Source.calculateOffset(position)
        val start = if (ending) column - name.length else column
        return SourceLocation(source?.fileName, Range(Position(line, start), Position(line, start + name.length)))
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
        annotation: Boolean = false,
    ) {
        if (token == null) return
        refer(
            token.valueText,
            location(source, token.startPosition, token.endPosition),
            target,
            expressionType,
            usage,
            annotation,
        )
    }

    private fun refer(
        name: String,
        location: SourceLocation,
        target: Argument?,
        expressionType: TypeConstant?,
        usage: SemanticModel.Usage? = null,
        annotation: Boolean = false,
    ) {
        if (location in occurrences) return
        val symbol = symbol(target, name, kind(target))
        // Failed name validation can leave a required/placeholder type on the expression.
        val type = if (symbol == null) null else type(expressionType) ?: symbols[symbol]?.type
        val access =
            when (symbols[symbol]?.kind) {
                SymbolKind.VARIABLE,
                SymbolKind.PARAMETER,
                SymbolKind.PROPERTY,
                -> usage ?: SemanticModel.Usage.READ

                else -> null
            }
        occurrences[location] =
            Occurrence(location.range, name, Role.REFERENCE, symbol, type, access, annotation)
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
        val dependency = (target as? IdentityConstant)?.let(::dependency)
        val location = declaration ?: dependency?.location
        val component = (target as? IdentityConstant)?.component
        if (target is Register) {
            registers[target] = symbol
        } else {
            constants[target as Constant] = symbol
        }
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
                // a compiled library keeps no doc comments; its source does
                documentation =
                    component?.documentation?.trim()?.takeIf {
                        it.isNotEmpty()
                    } ?: dependency?.documentation,
                typeCategory =
                    when (component?.format) {
                        Format.CLASS -> TypeCategory.CLASS
                        Format.INTERFACE -> TypeCategory.INTERFACE
                        Format.MIXIN -> TypeCategory.MIXIN
                        Format.SERVICE -> TypeCategory.SERVICE
                        Format.CONST -> TypeCategory.CONST
                        Format.ENUM -> TypeCategory.ENUM
                        Format.ENUMVALUE -> TypeCategory.ENUM_VALUE
                        else -> null
                    },
                isFunction = component is MethodStructure && component.isFunction,
                isDefaultLibrary = (target as? IdentityConstant)?.moduleConstant?.name?.let { it in XdkLibraries.moduleNames } == true,
            )
        return symbol
    }

    /** Where another module, a dependency or a bundled library, declares [identity], or null for one of the module's own. */
    private fun dependency(identity: IdentityConstant): DependencyDeclaration? =
        // the module being built declares its own identities, also one that a bundled library has: the XDK's own sources
        // compile a module of a bundled library's name
        dependencies[identity] ?: identity.takeUnless { it.moduleConstant == module }?.let(XdkLibrarySources::declaration)

    private fun modifiers(target: Argument): Set<SemanticModel.Modifier> =
        immutableSet(
            buildSet {
                if (target is Register && !target.isWritable) add(SemanticModel.Modifier.READONLY)
                val component = (target as? IdentityConstant)?.component
                if (component?.isStatic == true) add(SemanticModel.Modifier.STATIC)
                if (component?.isAbstract == true) add(SemanticModel.Modifier.ABSTRACT)
                if (component is PropertyStructure && component.isConstant) {
                    add(SemanticModel.Modifier.READONLY)
                }
            },
        )

    private fun normalized(argument: Argument?): Argument? {
        if (argument is PropertyConstant && argument in capturedProperties) {
            return normalized(capturedProperties[argument])
        }
        if (argument is Register) {
            var register = argument.originalRegister
            val seen = Collections.newSetFromMap(IdentityHashMap<Register, Boolean>())
            while (seen.add(register)) {
                val origin = captureOrigins[register]?.originalRegister
                if (origin == null) {
                    val type = register.type
                    val formal =
                        if (type.isTypeOfType && type.isParamsSpecified) {
                            type.getParamType(0)
                        } else {
                            null
                        }
                    if (
                        formal != null &&
                        !formal.containsUnresolved() &&
                        formal.isSingleDefiningConstant
                    ) {
                        val parameter = formal.definingConstant
                        if (
                            parameter is TypeParameterConstant &&
                            parameter.register == register.index
                        ) {
                            return parameter
                        }
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
        ) {
            target = target.definingConstant
        }
        if (target is Constant && target.containsUnresolved()) return null
        if (target is PseudoConstant) {
            target =
                when (target.format) {
                    Constant.Format.ThisClass,
                    Constant.Format.ParentClass,
                    Constant.Format.ChildClass,
                    -> target.declarationLevelClass

                    else -> return null
                }
        }
        return (target as? IdentityConstant)
            ?.takeUnless {
                it.containsUnresolved() || (it is MethodConstant && it.isNascent)
            }?.let {
                when (it) {
                    is FormalTypeChildConstant -> constrained(it) ?: it
                    is PropertyConstant -> it.implicitFormal() ?: it
                    else -> it
                }
            }
    }

    /**
     * The formal type that [child] names, as Element in CompileType.Element, for a CompileType that extends List: the
     * formal type of its name that its parent's constraint has, List's Element, which the compiler resolves it by
     * (FormalTypeChildConstant.getConstraintType); null when the constraint has none, as for OuterType in
     * PublicType.OuterType, which the compiler resolves by its name alone. The compiler takes the type that the
     * constraint gives the name, which a contribution can map to a formal type of another name, as `interface Crate<E>
     * extends Box<E>` maps Box's Item to E: the name is still Box's Item's, which a rename of E does not change.
     */
    private fun constrained(child: FormalTypeChildConstant): PropertyConstant? {
        val constraint = child.parentConstant.constraintType
        if (constraint.isTuple || !constraint.containsGenericParam(child.name) || !constraint.isSingleUnderlyingClass(true)) return null
        return (constraint.getSingleUnderlyingClass(true).component as? ClassStructure)?.formalNamed(child.name)
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
            is Register -> SymbolKind.VARIABLE

            is Constant -> {
                when (target.format) {
                    Constant.Format.Module -> SymbolKind.MODULE

                    Constant.Format.Package -> SymbolKind.PACKAGE

                    Constant.Format.Method,
                    Constant.Format.MultiMethod,
                    -> SymbolKind.METHOD

                    Constant.Format.Property -> {
                        if ((target as PropertyConstant).isFormalType) {
                            SymbolKind.TYPE_PARAMETER
                        } else {
                            SymbolKind.PROPERTY
                        }
                    }

                    Constant.Format.TypeParameter,
                    Constant.Format.FormalTypeChild,
                    Constant.Format.DynamicFormal,
                    -> SymbolKind.TYPE_PARAMETER

                    else -> SymbolKind.TYPE
                }
            }

            else -> SymbolKind.VARIABLE
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
        return copyType(constant)
    }

    /** Copy a graph already checked by [copyableType]; recursive edges reuse interned IDs. */
    private fun copyType(constant: TypeConstant): TypeId {
        typeIds[constant]?.let {
            return it
        }
        val id = TypeId(id, typeIds.size)
        typeIds[constant] = id // intern before following recursive type relationships
        val arguments =
            if (constant.isParamsSpecified && !constant.isRelationalType) {
                constant.paramTypes.map(::copyType)
            } else {
                emptyList()
            }
        val underlying =
            when {
                constant.isRelationalType -> listOf(copyType(constant.underlyingType), copyType(constant.underlyingType2))
                constant.isModifyingType -> listOf(copyType(constant.underlyingType))
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
            if (method.isNascent || !copyableType(method.rawParams[formal.register], visited)) {
                return false
            }
        }
        if (
            constant.isParamsSpecified &&
            !constant.isRelationalType &&
            !constant.paramTypes.all { copyableType(it, visited) }
        ) {
            return false
        }
        return when {
            constant.isRelationalType -> {
                copyableType(constant.underlyingType, visited) &&
                    copyableType(constant.underlyingType2, visited)
            }

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
            if (constant.isSingleDefiningConstant) {
                constant.definingConstant as? FormalConstant
            } else {
                null
            }
        return when {
            formal is TypeParameterConstant &&
                (
                    formal.method.isNascent ||
                        formal.method.rawParams[formal.register].containsUnresolved()
                ) -> false

            // a property that is not a formal type, as the core library's own compile has, has no constraint
            formal is PropertyConstant && !formal.isFormalType -> true

            formal != null -> acyclicConstraint(formal.constraintType, visited)

            constant.isRelationalType -> {
                acyclicConstraint(constant.underlyingType, visited) &&
                    acyclicConstraint(constant.underlyingType2, visited)
            }

            constant.isModifyingType -> acyclicConstraint(constant.underlyingType, visited)

            else -> true
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
            resolved != constant -> typeDefinitions(resolved, visited)

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

            constant.isModifyingType -> typeDefinitions(constant.underlyingType, visited)

            else -> {
                val target = normalized(constant)
                listOfNotNull(
                    constants[target]
                        ?: (target as? IdentityConstant)?.let {
                            symbol(
                                it,
                                it.name,
                                if (constant.isFormalType) {
                                    SymbolKind.TYPE_PARAMETER
                                } else {
                                    SymbolKind.TYPE
                                },
                            )
                        },
                )
            }
        }.distinct()
    }

    /** A value may be passed earlier only if the compiler proves its register cannot change. */
    fun extractionFacts(compilation: EmbeddingSupport.Compilation): CompilerExtractionFacts? {
        if (!compilation.succeeded()) return null
        val values =
            registers.entries.mapNotNull { (register, id) ->
                val symbol = symbols[id] ?: return@mapNotNull null
                if (symbol.kind !in setOf(SymbolKind.VARIABLE, SymbolKind.PARAMETER)) return@mapNotNull null
                val source = symbol.declarationSource ?: return@mapNotNull null
                val range = symbol.declaration ?: return@mapNotNull null
                // An @Inject local is a Ref only to receive its injected value where it is declared; that value never
                // changes, unlike that of a local with any other Ref or Var annotation, such as @Future or @Volatile.
                val injected =
                    register.isVar &&
                        register.ensureRegType(true).annotations.map { it.annotationClass } ==
                        listOf(register.originalType.constantPool.clzInject())
                // The compiler still calls a register effectively final when &name lets a Var change it.
                val plain = (!register.isVar || injected) && register !in referenced
                Value(SourceLocation(source, range), register.originalType, register.isEffectivelyFinal && plain, plain, injected)
            }
        val nodes = nodesIn(requireNotNull(compilation.parsed()))
        val modules = compilerModuleAliases(nodes)
        val expressionTypes =
            nodes.filterIsInstance<Expression>().mapNotNull { node ->
                validatedType(node)?.let { location(node.source, node.startPosition, node.endPosition) to it }
            }
        val sourceTypes =
            nodes
                .filterIsInstance<Expression>()
                .mapNotNull { node ->
                    val type = validatedType(node) ?: return@mapNotNull null
                    val owner = generateSequence(node.parent) { it.parent }.filterIsInstance<TypeCompositionStatement>().firstOrNull()
                    val structure = owner?.component as? ClassStructure ?: return@mapNotNull null
                    val source =
                        type.resolveTypedefs().memberSourceType(
                            structure.identityConstant,
                            emptyMap(),
                            modules,
                        )
                            ?: return@mapNotNull null
                    location(node.source, node.startPosition, node.endPosition) to source
                }.toMap()
        // One declaration can have several registers, and only some of them are named with &: all must agree.
        val declarations = values.groupBy { it.location }
        return CompilerExtractionFacts(
            expressionTypes.toMap() + values.associate { it.location to it.type },
            declarations.filterValues { all -> all.all { it.stable } }.keys.toCollection(linkedSetOf()),
            sourceTypes,
            declarations.filterValues { all -> all.all { it.plain } }.keys.toCollection(linkedSetOf()),
            declarations.filterValues { all -> all.all { it.injected && it.plain } }.keys.toCollection(linkedSetOf()),
        )
    }

    /**
     * A local or parameter: plain when no Ref or Var can change it, stable when it is plain and never changes, and
     * injected when @Inject alone annotates it.
     */
    private data class Value(
        val location: SourceLocation,
        val type: TypeConstant,
        val stable: Boolean,
        val plain: Boolean,
        val injected: Boolean,
    )

    /** Inspect validated initializers on the worker; do not retain ASTs or infer purity from spelling. */
    fun removableLocals(compilation: EmbeddingSupport.Compilation): Set<SourceLocation> =
        if (!compilation.succeeded()) {
            emptySet()
        } else {
            nodesIn(requireNotNull(compilation.parsed()))
                .filterIsInstance<AssignmentStatement>()
                .mapNotNull { assignment ->
                    val local = assignment.lValue as? VariableDeclarationStatement ?: return@mapNotNull null
                    if (assignment.op.id != Token.Id.ASN || local.hasRefAnnotations() ||
                        !assignment.rValue.isConstant || assignment.rValue.hasSideEffects()
                    ) {
                        return@mapNotNull null
                    }
                    location(local.source, local.nameToken.startPosition, local.nameToken.endPosition)
                }.toSet()
        }

    /** Static alone does not imply compile-time evaluation; runtime initializers stay in place. */
    fun constantProperties(compilation: EmbeddingSupport.Compilation): Set<SourceLocation> =
        if (!compilation.succeeded()) {
            emptySet()
        } else {
            nodesIn(requireNotNull(compilation.parsed()))
                .filterIsInstance<PropertyDeclarationStatement>()
                .mapNotNull { node ->
                    val property = node.component as? PropertyStructure ?: return@mapNotNull null
                    val value = property.initialValue ?: return@mapNotNull null
                    if (!property.isConstant || value is DeferredValueConstant ||
                        value.containsUnresolved() || property.initializer != null
                    ) {
                        return@mapNotNull null
                    }
                    location(node.source, node.nameToken.startPosition, node.nameToken.endPosition)
                }.toSet()
        }

    fun resourceValues(compilation: EmbeddingSupport.Compilation): Map<SourceLocation, String?> =
        compilerResourceValues(nodesIn(requireNotNull(compilation.parsed())))

    fun typeNames(compilation: EmbeddingSupport.Compilation): List<CompilerTypeName> =
        compilerTypeNames(nodesIn(requireNotNull(compilation.parsed())))

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

/**
 * The formal type that this one stands for when the compiler adds it to a mixin or annotation, as Inner to
 * `annotation Cap into Holder` for a Holder<Inner>, which then names its contribution as Holder<Cap.Inner>
 * (ClassStructure.addImplicitTypeParameters): the contribution's formal type of its name, which the source declares;
 * null for any other property. The compiler adds the formal types of a contribution written without its parameters
 * only, and then names the contribution with those formal types themselves, Holder<Holder.Inner>, so a contribution
 * whose parameters were written gives none. Where several give one of the same name, the compiler adds one, of their
 * shared constraint, which this binds to the first's: a rename of that one's formal type, which would move the binding to
 * the next one's, is refused, as a rename of a class's formal type is.
 */
internal fun PropertyConstant.implicitFormal(): PropertyConstant? {
    val property = component as? PropertyStructure ?: return null
    val mixin = property.parent as? ClassStructure ?: return null
    if (!property.isSynthetic || !property.isGenericTypeParameter || mixin.format !in setOf(Format.MIXIN, Format.ANNOTATION)) {
        return null
    }
    return mixin.contributionsAsList
        .asSequence()
        .filter { it.composition in formalContributions && it.typeConstant.isSingleUnderlyingClass(true) }
        .mapNotNull { contribution ->
            val type = contribution.typeConstant
            val formal =
                ((type.getSingleUnderlyingClass(true).component as? ClassStructure)?.getChild(name) as? PropertyStructure)
                    ?.takeIf { it.isGenericTypeParameter }
                    ?.identityConstant
            formal?.takeIf { type.paramTypes.any { it.isGenericType && it.definingConstant == formal } }
        }.firstOrNull()
}

/**
 * The formal type of [name] that this class declares, or else the first that one of its contributions has, in the order
 * the class writes them: the declaration that a name of a formal type of the class spells.
 */
private fun ClassStructure.formalNamed(
    name: String,
    seen: MutableSet<ClassStructure> = hashSetOf(),
): PropertyConstant? {
    if (!seen.add(this)) return null
    (getChild(name) as? PropertyStructure)?.takeIf { it.isGenericTypeParameter }?.let { return it.identityConstant }
    return contributionsAsList
        .asSequence()
        .filter { it.composition in formalContributions }
        .map { it.typeConstant }
        .filter { it.isSingleUnderlyingClass(true) }
        .mapNotNull { (it.getSingleUnderlyingClass(true).component as? ClassStructure)?.formalNamed(name, seen) }
        .firstOrNull()
}

/** The contributions through which a class has another's formal types: what it extends, implements, incorporates or is into. */
private val formalContributions =
    setOf(Composition.Extends, Composition.Implements, Composition.Incorporates, Composition.Into)
