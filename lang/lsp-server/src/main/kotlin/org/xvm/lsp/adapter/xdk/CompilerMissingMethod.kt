package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Format
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.MethodStructure
import org.xvm.asm.constants.TypeConstant
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.ExpressionStatement
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.LiteralExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.ReturnStatement
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.compiler.ast.VariableDeclarationStatement
import org.xvm.lsp.util.ExecutionTrace

/** Read only fresh resolved declarations, never TypeInfo from a failed body-validation attempt. */
internal fun compilerMissingMethods(
    nodes: List<AstNode>,
    errors: ErrorListener,
): List<XdkMissingMethods.Candidate> =
    nodes
        .filterIsInstance<InvocationExpression>()
        .mapNotNull { call ->
            val name = call.invokedExpression as? NameExpression ?: return@mapNotNull null
            if (call.isAsync || !name.isSimpleName || name.hasTrailingTypeParams() || name.isSuppressDeref ||
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
            val structure = owner.component as? ClassStructure ?: return@mapNotNull null
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
            // A body-local declaration can shadow a parameter or the proposed method. Until body
            // resolution proves those scopes, withhold the action rather than infer a binding.
            val locals =
                nodes
                    .filterIsInstance<VariableDeclarationStatement>()
                    .filter(::enclosed)
                    .map { it.nameToken.valueText }
                    .toSet()
            if (name.name in locals || declaration.params.any { it.name == name.name } ||
                nodes.filterIsInstance<MethodDeclarationStatement>().any { it !== method && it.name == name.name && enclosed(it) }
            ) {
                return@mapNotNull null
            }
            val returns =
                when (val statement = call.parent) {
                    is ExpressionStatement -> {
                        if (statement.expression === call) emptyList() else return@mapNotNull null
                    }

                    is ReturnStatement -> {
                        if (statement.expressions?.singleOrNull() ===
                            call
                        ) {
                            declaration.returnTypes.toList()
                        } else {
                            return@mapNotNull null
                        }
                    }

                    else -> {
                        return@mapNotNull null
                    }
                }

            fun render(type: TypeConstant) =
                type.memberSourceType(structure.identityConstant, emptyMap(), mapOf("ecstasy.xtclang.org" to "ecstasy"))
            val result =
                returns.map { render(it) ?: return@mapNotNull null }.let {
                    when (it.size) {
                        0 -> "void"
                        1 -> it.single()
                        else -> it.joinToString(", ", "(", ")")
                    }
                }
            val arguments = call.childNodes().filter { it !== name }
            val parameters =
                arguments.mapIndexed { index, argument ->
                    val parameter =
                        (argument as? NameExpression)
                            ?.takeIf { it.isSimpleName && !it.hasTrailingTypeParams() && !it.isSuppressDeref }
                            ?.let { expression -> declaration.params.singleOrNull { it.name == expression.name && it.name !in locals } }
                    val type = parameter?.type ?: (argument as? LiteralExpression)?.getImplicitType(null) ?: return@mapNotNull null
                    // Untyped numeric literals still have a choice of runtime representation; do not
                    // turn the compiler's IntLiteral/FPLiteral implementation types into a public signature.
                    if (type == type.constantPool.typeIntLiteral() || type == type.constantPool.typeFPLiteral() ||
                        parameter?.annotations?.isNotEmpty() == true
                    ) {
                        return@mapNotNull null
                    }
                    "${render(type) ?: return@mapNotNull null} arg${index + 1}"
                }

            fun position(value: Long) = SemanticModel.Position(Source.calculateLine(value), Source.calculateOffset(value))
            XdkMissingMethods.Candidate(
                SemanticModel.SourceLocation(
                    call.source.fileName,
                    SemanticModel.Range(position(name.startPosition), position(name.endPosition)),
                ),
                name.name,
                position(owner.ensureBody().endPosition).let { it.copy(column = it.column - 1) },
                position(owner.startPosition),
                "private ${if (declaration.isFunction) "static " else ""}$result ${name.name}(${parameters.joinToString(", ")})",
            )
        }.distinct()
