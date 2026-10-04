package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Format
import org.xvm.asm.ConstantPool
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.MethodStructure
import org.xvm.asm.Op
import org.xvm.asm.PackageStructure
import org.xvm.asm.Register
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.TypeParameterConstant
import org.xvm.compiler.Lexer
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AnnotatedTypeExpression
import org.xvm.compiler.ast.AssignmentStatement
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.ExpressionStatement
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.LabeledExpression
import org.xvm.compiler.ast.LiteralExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.ReturnStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.compiler.ast.VariableDeclarationStatement
import org.xvm.compiler.ast.VariableTypeExpression
import org.xvm.lsp.adapter.xdk.XdkMissingMethods.Dispatch
import org.xvm.lsp.util.ExecutionTrace

/** Attempt-owned types are detached by the shared compiler identity collector. */
internal data class CompilerMissingMethod(
    val candidate: XdkMissingMethods.Candidate,
    val parameters: List<Type> = emptyList(),
    val returns: List<Type> = emptyList(),
    val formals: List<TypeConstant> = emptyList(),
    val conditional: Boolean = false,
) {
    /** Fresh declaration types and already detached body types never share compiler pools. */
    sealed interface Type {
        data class Resolved(
            val value: TypeConstant,
        ) : Type

        data class Detached(
            val value: ProofIdentity,
        ) : Type
    }
}

/** Register types may leave the failed attempt only through the shared identity collector. */
internal data class CompilerMissingInputs(
    val localTypes: Map<SemanticModel.SourceLocation, LocalType> = emptyMap(),
    val receivers: Map<SemanticModel.SourceLocation, XdkMissingMethods.Receiver> = emptyMap(),
    val expressions: Map<SemanticModel.SourceLocation, LocalType> = emptyMap(),
) {
    data class LocalType(
        val source: String?,
        val type: TypeConstant,
        val destinationSources: Map<SemanticModel.SourceLocation, XdkMissingMethods.TypeSource> = emptyMap(),
    )
}

private data class MissingType(
    val source: String,
    val proof: CompilerMissingMethod.Type,
    val imports: List<XdkMemberActions.Import> = emptyList(),
)

private data class MissingParameter(
    val source: String,
    val type: CompilerMissingMethod.Type,
    val binding: XdkMissingMethods.ArgumentBinding? = null,
    val imports: List<XdkMemberActions.Import> = emptyList(),
)

/** Read only fresh resolved declarations, never TypeInfo from a failed body-validation attempt. */
internal fun compilerMissingMethods(
    nodes: List<AstNode>,
    errors: ErrorListener,
    inputs: XdkMissingMethods.Inputs = XdkMissingMethods.Inputs(),
    destinations: Map<ClassConstant, XdkMissingMethods.Destination> = emptyMap(),
): List<CompilerMissingMethod> =
    nodes
        .filterIsInstance<InvocationExpression>()
        .mapNotNull { call ->
            val name = call.invokedExpression as? NameExpression ?: return@mapNotNull null
            val qualified = name.leftExpression != null
            if (call.isAsync || name.hasTrailingTypeParams() || name.isSuppressDeref ||
                (if (qualified) name.calleeLocation() !in inputs.receivers else !name.isSimpleName) ||
                !XdkRename.identifier(name.name)
            ) {
                return@mapNotNull null
            }
            val ancestors = generateSequence(call.parent) { it.parent }.toList()
            val method = ancestors.filterIsInstance<MethodDeclarationStatement>().firstOrNull() ?: return@mapNotNull null
            if (ancestors.takeWhile { it !== method }.any { it is LambdaExpression || it is TypeCompositionStatement }) {
                return@mapNotNull null
            }
            val owner =
                generateSequence(method.parent) { it.parent }.filterIsInstance<TypeCompositionStatement>().firstOrNull()
                    ?: return@mapNotNull null
            if (generateSequence(method.parent) { it.parent }
                    .takeWhile { it !== owner }
                    .any { it is MethodDeclarationStatement || it is LambdaExpression }
            ) {
                return@mapNotNull null
            }
            val declaration = method.component as? MethodStructure ?: return@mapNotNull null
            val receiver = inputs.receivers[name.calleeLocation()]
            val destination =
                if (qualified) {
                    nodes.filterIsInstance<TypeCompositionStatement>().singleOrNull { it.location() == receiver?.destination }
                } else {
                    owner
                }
            val external = destinations.entries.singleOrNull { it.value.location == receiver?.destination }
            val target = destination
            val structure = (target?.component ?: external?.key?.component) as? ClassStructure ?: return@mapNotNull null
            val crossOwner = target !== owner
            // Preserve concrete call types; never infer an owner's formal from equal actual types.
            // Exact lexical owner formals are rendered by memberSourceType and re-proven later.
            if (crossOwner &&
                (structure.format !in setOf(Format.CLASS, Format.CONST, Format.SERVICE) || structure.isSynthetic)
            ) {
                return@mapNotNull null
            }
            val dispatch =
                if (qualified) {
                    inputs.receivers.getValue(name.calleeLocation()).dispatch
                } else if (declaration.isFunction) {
                    Dispatch.STATIC
                } else {
                    Dispatch.INSTANCE
                }
            if (structure.format !in setOf(Format.CLASS, Format.CONST, Format.SERVICE, Format.MODULE) || declaration.isConstructor ||
                errors.isAbortDesired
            ) {
                return@mapNotNull null
            }
            val info =
                ExecutionTrace.api("TypeConstant.ensureTypeInfo(missing-method)") {
                    // Reopened artifacts carry source indexes, but their pools are not linked for
                    // TypeInfo. Inspect the dependency through this fresh caller's linked pool.
                    val type =
                        if (external == null) {
                            structure.formalType
                        } else {
                            declaration.constantPool.ensureTerminalTypeConstant(external.key)
                        }
                    type.ensureAccess(Access.PRIVATE).ensureTypeInfo(errors)
                }
            if (errors.hasSeriousErrors() || errors.isAbortDesired ||
                info.methods.values.any { it.signature.name == name.name } || info.properties.values.any { it.name == name.name }
            ) {
                return@mapNotNull null
            }

            fun enclosed(node: AstNode) = generateSequence(node.parent) { it.parent }.any { it === method }
            // An unqualified call must not be redirected around a body-local declaration.
            val locals =
                nodes
                    .filterIsInstance<VariableDeclarationStatement>()
                    .filter(::enclosed)
            val localNames = locals.map { it.name }.toSet()
            if (!qualified &&
                (
                    name.name in localNames || declaration.params.any { it.name == name.name } ||
                        nodes.filterIsInstance<MethodDeclarationStatement>().any { it !== method && it.name == name.name && enclosed(it) }
                )
            ) {
                return@mapNotNull null
            }

            val formals =
                declaration.params.take(declaration.typeParamCount).associate { parameter ->
                    val spelling = parameter.name?.takeIf(XdkRename::identifier) ?: return@mapNotNull null
                    parameter.asTypeParameterConstant(declaration.identityConstant) to spelling
                }

            fun render(type: TypeConstant): MissingType? {
                val rendered =
                    external?.value?.render(type, structure, formals)
                        ?: type.missingMethodType(structure, formals = formals)?.let { XdkMissingMethods.TypeSource(it) }
                        ?: return null
                return MissingType(rendered.source, CompilerMissingMethod.Type.Resolved(type), rendered.imports)
            }

            fun localType(local: VariableDeclarationStatement) =
                inputs.localTypes[local.declarationLocation()]?.let { evidence ->
                    val rendered =
                        if (external == null) {
                            evidence.source?.let { XdkMissingMethods.TypeSource(it) }
                        } else {
                            evidence.destinationSources[external.value.location]
                        }
                    rendered?.let { MissingType(it.source, CompilerMissingMethod.Type.Detached(evidence.identity), it.imports) }
                } ?: local.takeIf { it.hasExplicitMethodType() }?.type?.let(::render)
            val returns =
                when (val statement = call.parent) {
                    is ExpressionStatement -> {
                        if (statement.expression === call) emptyList() else return@mapNotNull null
                    }

                    is ReturnStatement -> {
                        if (statement.expressions?.singleOrNull() !== call) return@mapNotNull null
                        declaration.returnTypes.map { render(it) ?: return@mapNotNull null }
                    }

                    is AssignmentStatement -> {
                        val local = statement.lValue as? VariableDeclarationStatement ?: return@mapNotNull null
                        if (statement.op.id != Token.Id.ASN || statement.rValue !== call || !local.hasExplicitMethodType()) {
                            return@mapNotNull null
                        }
                        listOf(localType(local) ?: return@mapNotNull null)
                    }

                    else -> {
                        return@mapNotNull null
                    }
                }

            val conditional = declaration.isConditionalReturn && call.parent is ReturnStatement
            val writtenReturns = returns.drop(if (conditional) 1 else 0)
            val result =
                when (writtenReturns.size) {
                    0 -> "void"
                    1 -> writtenReturns.single().source
                    else -> writtenReturns.joinToString(", ", "(", ")") { it.source }
                }
            val constraints =
                declaration.params.take(declaration.typeParamCount).map { parameter ->
                    val constraint = parameter.type.paramTypes.singleOrNull() ?: return@mapNotNull null
                    render(constraint) ?: return@mapNotNull null
                }
            val generic =
                if (formals.isEmpty()) {
                    ""
                } else {
                    formals.values.zip(constraints).joinToString(", ", "<", "> ") { (formal, bound) ->
                        "$formal extends ${bound.source}"
                    }
                }
            val arguments = call.childNodes().filter { it !== name }
            val labels = arguments.filterIsInstance<LabeledExpression>().map { it.name }
            if (labels.distinct().size != labels.size || labels.any { !XdkRename.identifier(it) }) return@mapNotNull null
            val argumentNames = buildList {
                arguments.forEachIndexed { index, argument ->
                    add((argument as? LabeledExpression)?.name
                        ?: generateSequence(index + 1) { it + 1 }.map { "arg$it" }.first { it !in labels && it !in this })
                }
            }
            val parameters =
                arguments.mapIndexed { index, wrapped ->
                    val argument = (wrapped as? LabeledExpression)?.underlyingExpression ?: wrapped
                    val argumentName = argumentNames[index]
                    val expression =
                        (argument as? NameExpression)
                            ?.takeIf { it.isSimpleName && !it.hasTrailingTypeParams() && !it.isSuppressDeref }
                    // The declaration pass has not resolved body scopes. Limit proposals to prior
                    // block locals, then require the completed compiler to bind this exact use to
                    // that declaration. No spelling-based binding escapes the repair proof.
                    val local =
                        expression?.let { value ->
                            ancestors.filterIsInstance<StatementBlock>().firstNotNullOfOrNull { block ->
                                locals.singleOrNull { local ->
                                    val statement = local.parent as? AssignmentStatement
                                    val site = statement?.takeIf { it.lValue === local && it.op.id == Token.Id.ASN } ?: local
                                    local.name == value.name && site.parent === block && site.endPosition < call.startPosition
                                }
                            }
                        }
                    if (local != null) {
                        val rendered = localType(local) ?: return@mapNotNull null
                        val binding = XdkMissingMethods.ArgumentBinding(argument.location(), local.declarationLocation())
                        return@mapIndexed MissingParameter("${rendered.source} $argumentName", rendered.proof, binding, rendered.imports)
                    }
                    val parameter =
                        expression?.let { value ->
                            declaration.params.singleOrNull {
                                it.name == value.name &&
                                    it.name !in localNames
                            }
                        }
                    val evidence = inputs.expressions[argument.location()]
                    if (parameter == null && evidence != null) {
                        val rendered = if (external == null) evidence.source?.let { XdkMissingMethods.TypeSource(it) }
                            else evidence.destinationSources[external.value.location]
                        rendered ?: return@mapNotNull null
                        return@mapIndexed MissingParameter("${rendered.source} $argumentName",
                            CompilerMissingMethod.Type.Detached(evidence.identity), imports = rendered.imports)
                    }
                    val type = parameter?.type ?: (argument as? LiteralExpression)?.getImplicitType(null) ?: return@mapNotNull null
                    // Untyped numeric literals still have a choice of runtime representation; do not
                    // turn the compiler's IntLiteral/FPLiteral implementation types into a public signature.
                    if (type == type.constantPool.typeIntLiteral() || type == type.constantPool.typeFPLiteral() ||
                        parameter?.annotations?.isNotEmpty() == true
                    ) {
                        return@mapNotNull null
                    }
                    val rendered = render(type) ?: return@mapNotNull null
                    MissingParameter("${rendered.source} $argumentName", rendered.proof, imports = rendered.imports)
                }

            fun position(value: Long) = SemanticModel.Position(Source.calculateLine(value), Source.calculateOffset(value))
            val signature = parameters.joinToString(", ") { it.source }
            CompilerMissingMethod(
                XdkMissingMethods.Candidate(
                    name.calleeLocation(),
                    name.name,
                    external?.value?.insertion
                        ?: position(requireNotNull(target).ensureBody().endPosition).let { it.copy(column = it.column - 1) },
                    external?.value?.location ?: requireNotNull(target).location(),
                    "${if (crossOwner) "public" else "private"} ${if (dispatch == Dispatch.STATIC) "static " else ""}" +
                        "$generic${if (conditional) "conditional " else ""}$result ${name.name}($signature)",
                    dispatch,
                    parameters.mapNotNull { it.binding },
                    if (crossOwner) structure.identityConstant.pathString else null,
                    importSource = external?.value?.importSource,
                    imports =
                        (parameters.flatMap { it.imports } + returns.flatMap { it.imports } + constraints.flatMap { it.imports })
                            .distinct(),
                ),
                declaration.params.take(declaration.typeParamCount).map { CompilerMissingMethod.Type.Resolved(it.type) } +
                    parameters.map { it.type },
                returns.map { it.proof },
                formals.keys.map { it.type },
                conditional,
            )
        }.distinct()

/**
 * Copy compiler-established body facts without resuming a failed body or requesting its TypeInfo.
 * Inferred locals need a validated initializer; an explicit declaration needs its resolved register.
 */
internal fun EmbeddingSupport.Compilation.missingMethodInputs(
    destinations: Map<ClassConstant, XdkMissingMethods.Destination> = emptyMap(),
): CompilerMissingInputs =
    ExecutionTrace.api("Compilation.missingMethodInputs") {
        val root = parsed() ?: return@api CompilerMissingInputs()
        ConstantPool.withPool(pool()).use {
            fun descendants(node: AstNode): Sequence<AstNode> = sequenceOf(node) + node.childNodes().asSequence().flatMap(::descendants)
            val nodes = descendants(root).toList()
            val receivers =
                nodes
                    .filterIsInstance<InvocationExpression>()
                    .mapNotNull { call ->
                        val callee = call.invokedExpression as? NameExpression ?: return@mapNotNull null
                        val receiver = callee.leftExpression ?: return@mapNotNull null
                        val receiverName = receiver as? NameExpression
                        if (!receiver.isValidated || !receiver.typeFit.isFit ||
                            generateSequence(receiverName) { it.leftExpression as? NameExpression }
                                .any { it.isSuppressDeref || it.hasTrailingTypeParams() }
                        ) {
                            return@mapNotNull null
                        }
                        val identityAndDispatch =
                            when (val target = receiverName?.resolvedTarget) {
                                is ClassConstant -> {
                                    target to
                                        if ((target.component as? ClassStructure)?.isSingleton ==
                                            true
                                        ) {
                                            Dispatch.INSTANCE
                                        } else {
                                            Dispatch.STATIC
                                        }
                                }

                                is Register -> {
                                    if (target.index < 0 && target.index !in setOf(Op.A_THIS, Op.A_PRIVATE)) {
                                        return@mapNotNull null
                                    }
                                    val type = receiver.type ?: return@mapNotNull null
                                    if (!type.isSingleUnderlyingClass(false)) return@mapNotNull null
                                    type.getSingleUnderlyingClass(false) to Dispatch.INSTANCE
                                }

                                else -> {
                                    // Retain the validated computed/property receiver type. Never
                                    // re-evaluate it or query TypeInfo on this failed attempt.
                                    val type = receiver.type ?: return@mapNotNull null
                                    if (!type.isSingleUnderlyingClass(false)) return@mapNotNull null
                                    type.getSingleUnderlyingClass(false) to Dispatch.INSTANCE
                                }
                            }
                        val (identity, dispatch) = identityAndDispatch
                        // Dependency source indexes locate declarations but do not authorize edits.
                        // Only a matching destination from the configured source graph is writable.
                        val destination =
                            nodes
                                .filterIsInstance<TypeCompositionStatement>()
                                .singleOrNull {
                                    (it.component as? ClassStructure)?.identityConstant == identity
                                }?.location() ?: destinations[identity]?.location ?: return@mapNotNull null
                        if (dispatch == Dispatch.STATIC && identity.component.format != Format.CLASS) return@mapNotNull null
                        callee.calleeLocation() to XdkMissingMethods.Receiver(destination, dispatch)
                    }.toMap()
            val destinationLocations = receivers.values.mapTo(hashSetOf()) { it.destination }
            val referencedDestinations = destinations.filterValues { it.location in destinationLocations }
            val localTypes =
                nodes
                    .filterIsInstance<VariableDeclarationStatement>()
                    .mapNotNull { local ->
                        if (!local.hasExplicitMethodType()) {
                            if (local.childNodes().none { it is VariableTypeExpression }) return@mapNotNull null
                            val assignment = local.parent as? AssignmentStatement ?: return@mapNotNull null
                            if (assignment.lValue !== local || assignment.op.id != Token.Id.ASN ||
                                !assignment.rValue.isValidated || !assignment.rValue.typeFit.isFit
                            ) {
                                return@mapNotNull null
                            }
                        }
                        val owner = generateSequence(local.parent) { it.parent }.filterIsInstance<TypeCompositionStatement>().firstOrNull()
                        val structure = owner?.component as? ClassStructure ?: return@mapNotNull null
                        val type = local.register?.originalType ?: return@mapNotNull null
                        val destinationSources =
                            referencedDestinations.entries
                                .mapNotNull { (identity, destination) ->
                                    destination.render(type, identity.component as ClassStructure)?.let {
                                        destination.location to
                                            it
                                    }
                                }.toMap()
                        val rendered = type.missingMethodType(structure)
                        if (rendered == null && destinationSources.isEmpty()) return@mapNotNull null
                        local.declarationLocation() to CompilerMissingInputs.LocalType(rendered, type, destinationSources)
                    }.toMap()
            val expressions = nodes.filterIsInstance<InvocationExpression>().flatMap { call ->
                call.childNodes().filter { it !== call.invokedExpression }.map {
                    (it as? LabeledExpression)?.underlyingExpression ?: it
                }
            }.filterIsInstance<Expression>().mapNotNull { expression ->
                if (!expression.isValidated || !expression.typeFit.isFit || expression.valueCount != 1) return@mapNotNull null
                val type = expression.type ?: return@mapNotNull null
                if (type == pool().typeIntLiteral() || type == pool().typeFPLiteral()) return@mapNotNull null
                val ancestors = generateSequence(expression.parent) { it.parent }.toList()
                val owner = ancestors.filterIsInstance<TypeCompositionStatement>().firstOrNull()?.component as? ClassStructure
                    ?: return@mapNotNull null
                val method = ancestors.filterIsInstance<MethodDeclarationStatement>().firstOrNull()?.component as? MethodStructure
                val formals = method?.params?.take(method.typeParamCount).orEmpty().associate {
                    it.asTypeParameterConstant(requireNotNull(method).identityConstant) to (it.name ?: return@mapNotNull null)
                }
                val destinations = referencedDestinations.entries.mapNotNull { (identity, destination) ->
                    destination.render(type, identity.component as ClassStructure, formals)?.let { destination.location to it }
                }.toMap()
                expression.location() to CompilerMissingInputs.LocalType(type.missingMethodType(owner, formals = formals), type, destinations)
            }.toMap()
            CompilerMissingInputs(localTypes, receivers, expressions)
        }
    }

private fun VariableDeclarationStatement.hasExplicitMethodType(): Boolean =
    childNodes().none { it is VariableTypeExpression || it is AnnotatedTypeExpression }

private fun TypeConstant.missingMethodType(
    owner: ClassStructure,
    modules: Map<String, String> = emptyMap(),
    formals: Map<TypeParameterConstant, String> = emptyMap(),
): String? =
    // Declaration analysis resolves the identity but can retain its parser wrapper.
    resolveTypedefs().memberSourceType(owner.identityConstant, formals, modules + ("ecstasy.xtclang.org" to "ecstasy"))

/** Type spelling and only its required imports travel together across compiler attempts. */
private fun XdkMissingMethods.Destination.render(
    type: TypeConstant,
    owner: ClassStructure,
    formals: Map<TypeParameterConstant, String> = emptyMap(),
): XdkMissingMethods.TypeSource? {
    val resolved = type.resolveTypedefs()
    val source = resolved.missingMethodType(owner, modules, formals) ?: return null
    val required =
        resolved
            .memberClasses()
            .filter { it.constantPool.getImplicitlyImportedIdentity(it.name) != it }
            .mapNotNull { imports[it.moduleConstant.name] }
            .distinct()
    return XdkMissingMethods.TypeSource(source, required)
}

/** Detach source destinations before their compilation and AST leave this worker attempt. */
internal fun EmbeddingSupport.Compilation.missingMethodDestinations(
    dependencies: Set<String> = emptySet(),
): Map<SemanticModel.SourceLocation, XdkMissingMethods.Destination> =
    ExecutionTrace.api("Compilation.missingMethodDestinations") {
        check(succeeded())
        ConstantPool.withPool(pool()).use {
            fun descendants(node: AstNode): Sequence<AstNode> = sequenceOf(node) + node.childNodes().asSequence().flatMap(::descendants)
            val nodes = descendants(requireNotNull(parsed())).filterIsInstance<TypeCompositionStatement>().toList()
            // Reuse module-level imports and reserve all source names before planning new aliases.
            // The host supplies only dependencies already available to this source module.
            val modules =
                nodes
                    .mapNotNull { node ->
                        val structure = node.component as? PackageStructure ?: return@mapNotNull null
                        if (!structure.isModuleImport || structure.parent.format != Format.MODULE) return@mapNotNull null
                        structure.importedModule.identityConstant.name to structure.identityConstant.pathString
                    }.toMap()
            val lexicalErrors = ErrorList()
            val names =
                nodes
                    .map { it.source }
                    .distinctBy { it.fileName }
                    .flatMap { source ->
                        ExecutionTrace.api("Lexer.lex(missing-method-imports)") {
                            Lexer(source.clone(), lexicalErrors).asSequence().map { it.valueText }.toList()
                        }
                    }.toSet()
            if (lexicalErrors.hasSeriousErrors()) return@use emptyMap()
            val existing = modules + ("ecstasy.xtclang.org" to "ecstasy")
            val aliases = XdkMemberActions.moduleAliases(dependencies - file().module.name, existing, names)
            nodes
                .mapNotNull { node ->
                    val structure = node.component as? ClassStructure ?: return@mapNotNull null
                    if (structure.format !in setOf(Format.CLASS, Format.CONST, Format.SERVICE) ||
                        structure.isSynthetic
                    ) {
                        return@mapNotNull null
                    }
                    val moduleNode =
                        generateSequence(node as AstNode) { it.parent }
                            .filterIsInstance<TypeCompositionStatement>()
                            .lastOrNull { it.category.id == Token.Id.MODULE }
                    // A companion admits one top-level declaration. Module package imports
                    // belong in the root, even when the generated method belongs in a companion.
                    val module = moduleNode ?: return@mapNotNull null
                    val importAt =
                        module.ensureBody().startPosition.let {
                            SemanticModel.Position(Source.calculateLine(it), Source.calculateOffset(it) + 1)
                        }
                    val imports =
                        aliases.filterKeys { it !in existing }.mapValues { (module, alias) ->
                            XdkMemberActions.Import(importAt, "    package $alias import $module;")
                        }
                    val end = node.ensureBody().endPosition
                    val declaration = node.location(node.nameToken.startPosition, node.nameToken.endPosition)
                    declaration to
                        XdkMissingMethods.Destination(
                            node.location(),
                            SemanticModel.Position(Source.calculateLine(end), Source.calculateOffset(end) - 1),
                            aliases,
                            imports,
                            module.source.fileName,
                        )
                }.toMap()
        }
    }

/** Reassociate only detached project-owned source locations with this attempt's fresh constants. */
internal fun XdkDependencies.Open.missingMethodDestinations(
    destinations: Map<SemanticModel.SourceLocation, XdkMissingMethods.Destination>,
): Map<ClassConstant, XdkMissingMethods.Destination> =
    declarations.entries
        .mapNotNull { (identity, declaration) ->
            val owner = identity as? ClassConstant ?: return@mapNotNull null
            destinations[declaration.location]?.let { owner to it }
        }.toMap()

private fun AstNode.location(
    start: Long = startPosition,
    end: Long = endPosition,
): SemanticModel.SourceLocation {
    fun position(value: Long) = SemanticModel.Position(Source.calculateLine(value), Source.calculateOffset(value))
    return SemanticModel.SourceLocation(source.fileName, SemanticModel.Range(position(start), position(end)))
}

private fun VariableDeclarationStatement.declarationLocation() = location(nameToken.startPosition, nameToken.endPosition)

private fun NameExpression.calleeLocation() = location(nameToken.startPosition, nameToken.endPosition)
