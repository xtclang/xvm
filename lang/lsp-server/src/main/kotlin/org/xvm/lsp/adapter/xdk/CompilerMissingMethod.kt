package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Format
import org.xvm.asm.ConstantPool
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.MethodStructure
import org.xvm.asm.Op
import org.xvm.asm.Register
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AnnotatedTypeExpression
import org.xvm.compiler.ast.AssignmentStatement
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.ExpressionStatement
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.compiler.ast.LambdaExpression
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
) {
    data class LocalType(
        val source: String,
        val type: TypeConstant,
    )
}

private data class MissingType(
    val source: String,
    val proof: CompilerMissingMethod.Type,
)

private data class MissingParameter(
    val source: String,
    val type: CompilerMissingMethod.Type,
    val binding: XdkMissingMethods.ArgumentBinding? = null,
)

/** Read only fresh resolved declarations, never TypeInfo from a failed body-validation attempt. */
internal fun compilerMissingMethods(
    nodes: List<AstNode>,
    errors: ErrorListener,
    inputs: XdkMissingMethods.Inputs = XdkMissingMethods.Inputs(),
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
            val target = destination ?: return@mapNotNull null
            val structure = target.component as? ClassStructure ?: return@mapNotNull null
            val crossOwner = target !== owner
            // Cross-module, generic and synthetic destinations need additional ownership/substitution policy.
            if (crossOwner &&
                (structure.format != Format.CLASS || structure.typeParamCount != 0 || structure.isSynthetic)
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
            if (structure.format !in setOf(Format.CLASS, Format.MODULE) || declaration.isConstructor ||
                declaration.typeParamCount != 0 || declaration.isConditionalReturn || errors.isAbortDesired
            ) {
                return@mapNotNull null
            }
            val info =
                ExecutionTrace.api("TypeConstant.ensureTypeInfo(missing-method)") {
                    structure.formalType.ensureAccess(Access.PRIVATE).ensureTypeInfo(errors)
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

            fun render(type: TypeConstant) =
                type.missingMethodType(structure)?.let { MissingType(it, CompilerMissingMethod.Type.Resolved(type)) }

            fun localType(local: VariableDeclarationStatement) =
                inputs.localTypes[local.declarationLocation()]?.let {
                    MissingType(
                        it.source,
                        CompilerMissingMethod.Type.Detached(it.identity),
                    )
                }
                    ?: local.takeIf { it.hasExplicitMethodType() }?.type?.let(::render)
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

            val result =
                when (returns.size) {
                    0 -> "void"
                    1 -> returns.single().source
                    else -> returns.joinToString(", ", "(", ")") { it.source }
                }
            val arguments = call.childNodes().filter { it !== name }
            val parameters =
                arguments.mapIndexed { index, argument ->
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
                        return@mapIndexed MissingParameter("${rendered.source} arg${index + 1}", rendered.proof, binding)
                    }
                    val parameter =
                        expression?.let { value ->
                            declaration.params.singleOrNull {
                                it.name == value.name &&
                                    it.name !in localNames
                            }
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
                    MissingParameter("${rendered.source} arg${index + 1}", rendered.proof)
                }

            fun position(value: Long) = SemanticModel.Position(Source.calculateLine(value), Source.calculateOffset(value))
            val signature = parameters.joinToString(", ") { it.source }
            CompilerMissingMethod(
                XdkMissingMethods.Candidate(
                    name.calleeLocation(),
                    name.name,
                    position(target.ensureBody().endPosition).let { it.copy(column = it.column - 1) },
                    target.location(),
                    "${if (crossOwner) "public" else "private"} ${if (dispatch == Dispatch.STATIC) "static " else ""}$result ${name.name}($signature)",
                    dispatch,
                    parameters.mapNotNull { it.binding },
                    if (crossOwner) structure.identityConstant.pathString else null,
                ),
                if (crossOwner) parameters.map { it.type } else emptyList(),
                if (crossOwner) returns.map { it.proof } else emptyList(),
            )
        }.distinct()

/**
 * Copy compiler-established body facts without resuming a failed body or requesting its TypeInfo.
 * Inferred locals need a validated initializer; an explicit declaration needs its resolved register.
 */
internal fun EmbeddingSupport.Compilation.missingMethodInputs(): CompilerMissingInputs =
    ExecutionTrace.api("Compilation.missingMethodInputs") {
        val root = parsed() ?: return@api CompilerMissingInputs()
        ConstantPool.withPool(pool()).use {
            fun descendants(node: AstNode): Sequence<AstNode> = sequenceOf(node) + node.childNodes().asSequence().flatMap(::descendants)
            val nodes = descendants(root).toList()
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
                        val rendered = type.missingMethodType(structure) ?: return@mapNotNull null
                        local.declarationLocation() to CompilerMissingInputs.LocalType(rendered, type)
                    }.toMap()
            val receivers =
                nodes
                    .filterIsInstance<InvocationExpression>()
                    .mapNotNull { call ->
                        val callee = call.invokedExpression as? NameExpression ?: return@mapNotNull null
                        val receiver = callee.leftExpression as? NameExpression ?: return@mapNotNull null
                        if (!receiver.isOnlyNames || !receiver.isValidated || !receiver.typeFit.isFit ||
                            generateSequence(receiver) { it.leftExpression as? NameExpression }
                                .any { it.isSuppressDeref || it.hasTrailingTypeParams() }
                        ) {
                            return@mapNotNull null
                        }
                        val identityAndDispatch =
                            when (val target = receiver.resolvedTarget) {
                                is ClassConstant -> {
                                    target to Dispatch.STATIC
                                }

                                is Register -> {
                                    if (receiver.leftExpression != null ||
                                        (target.index < 0 && target.index !in setOf(Op.A_THIS, Op.A_PRIVATE))
                                    ) {
                                        return@mapNotNull null
                                    }
                                    val type = receiver.type ?: return@mapNotNull null
                                    if (!type.isSingleUnderlyingClass(false)) return@mapNotNull null
                                    type.getSingleUnderlyingClass(false) to Dispatch.INSTANCE
                                }

                                else -> {
                                    return@mapNotNull null
                                }
                            }
                        val (identity, dispatch) = identityAndDispatch
                        // Resolve the destination in this module's source AST by compiler identity.
                        // Binary/indexed sources and other modules cannot enter this edit path.
                        val destination =
                            nodes.filterIsInstance<TypeCompositionStatement>().singleOrNull {
                                (it.component as? ClassStructure)?.identityConstant == identity
                            } ?: return@mapNotNull null
                        if (dispatch == Dispatch.STATIC && identity.component.format != Format.CLASS) return@mapNotNull null
                        callee.calleeLocation() to XdkMissingMethods.Receiver(destination.location(), dispatch)
                    }.toMap()
            CompilerMissingInputs(localTypes, receivers)
        }
    }

private fun VariableDeclarationStatement.hasExplicitMethodType(): Boolean =
    childNodes().none { it is VariableTypeExpression || it is AnnotatedTypeExpression }

private fun TypeConstant.missingMethodType(owner: ClassStructure): String? =
    // Declaration analysis resolves the identity but can retain its parser wrapper.
    resolveTypedefs().memberSourceType(owner.identityConstant, emptyMap(), mapOf("ecstasy.xtclang.org" to "ecstasy"))

private fun AstNode.location(
    start: Long = startPosition,
    end: Long = endPosition,
): SemanticModel.SourceLocation {
    fun position(value: Long) = SemanticModel.Position(Source.calculateLine(value), Source.calculateOffset(value))
    return SemanticModel.SourceLocation(source.fileName, SemanticModel.Range(position(start), position(end)))
}

private fun VariableDeclarationStatement.declarationLocation() = location(nameToken.startPosition, nameToken.endPosition)

private fun NameExpression.calleeLocation() = location(nameToken.startPosition, nameToken.endPosition)
