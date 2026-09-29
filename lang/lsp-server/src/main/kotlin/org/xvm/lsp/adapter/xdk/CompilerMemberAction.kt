package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Format
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodConstant
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.util.ExecutionTrace

/** Worker-only candidates; compiler constants are detached before the compilation is released. */
internal data class CompilerMemberAction(
    val owner: IdentityConstant,
    val contract: MethodConstant,
    val insertion: SemanticModel.Position,
    val declaration: String,
    val implementation: Boolean,
)

/** Inherited methods from source and immutable dependency artifacts. A proposed declaration still needs a complete graph proof. */
internal fun compilerMemberActions(
    nodes: List<AstNode>,
    errors: ErrorListener,
): List<CompilerMemberAction> =
    nodes.filterIsInstance<TypeCompositionStatement>().flatMap { node ->
        val structure = node.component as? ClassStructure ?: return@flatMap emptyList()
        if (structure.format != Format.CLASS || structure.isSynthetic || errors.isAbortDesired) return@flatMap emptyList()
        val info =
            ExecutionTrace.api("TypeConstant.ensureTypeInfo(member-actions)") {
                structure.formalType.ensureAccess(Access.PRIVATE).ensureTypeInfo(errors)
            }
        if (errors.hasSeriousErrors() || errors.isAbortDesired) return@flatMap emptyList()
        val end = node.ensureBody().endPosition
        val insertion = SemanticModel.Position(Source.calculateLine(end), Source.calculateOffset(end) - 1)
        info.methods.values
            .filter { it.identity.isTopLevel && it.isVirtual && !it.isCtorOrValidator }
            .mapNotNull { method ->
                val declaration = method.getTopmostMethodStructure(info)
                if (declaration.containingClass == structure || declaration.isSynthetic || declaration.isNative ||
                    method.isOp || method.isAuto || !info.dispatch(method, errors).supported
                ) {
                    return@mapNotNull null
                }
                val signature = memberSignature(method.signature, declaration, structure.identityConstant) ?: return@mapNotNull null
                val access = if (method.access == Access.PROTECTED) "protected " else ""
                CompilerMemberAction(
                    structure.identityConstant,
                    declaration.identityConstant,
                    insertion,
                    "$access$signature",
                    method.isAbstract,
                )
            }.distinct()
    }
