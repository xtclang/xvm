package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Format
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodConstant
import org.xvm.asm.constants.TerminalTypeConstant
import org.xvm.asm.constants.TypeConstant
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

/** Ordinary inherited methods only. A proposed declaration still needs a complete graph proof. */
internal fun compilerMemberActions(nodes: List<AstNode>, errors: ErrorListener): List<CompilerMemberAction> =
    nodes.filterIsInstance<TypeCompositionStatement>().flatMap { node ->
        val structure = node.component as? ClassStructure ?: return@flatMap emptyList()
        if (structure.format != Format.CLASS || structure.isSynthetic || errors.isAbortDesired) return@flatMap emptyList()
        val info = ExecutionTrace.api("TypeConstant.ensureTypeInfo(member-actions)") {
            structure.formalType.ensureAccess(Access.PRIVATE).ensureTypeInfo(errors)
        }
        if (errors.hasSeriousErrors() || errors.isAbortDesired) return@flatMap emptyList()
        val end = node.ensureBody().endPosition
        val insertion = SemanticModel.Position(Source.calculateLine(end), Source.calculateOffset(end) - 1)
        info.methods.values.filter { it.identity.isTopLevel && it.isVirtual && !it.isCtorOrValidator }.mapNotNull { method ->
            val declaration = method.getTopmostMethodStructure(info)
            if (declaration.containingClass == structure || declaration.isSynthetic || declaration.isNative ||
                declaration.typeParamCount != 0 || declaration.isConditionalReturn || declaration.defaultParamCount != 0 ||
                method.isOp || method.isAuto || !info.dispatch(method, errors).supported
            ) return@mapNotNull null
            val signature = method.signature
            if (signature.returnCount > 1 || signature.paramCount != declaration.params.size) return@mapNotNull null
            val result = signature.returns.singleOrNull()?.sourceType(structure.identityConstant) ?: if (signature.returnCount == 0) {
                "void"
            } else return@mapNotNull null
            val parameters = signature.params.zip(declaration.params).map { (type, parameter) ->
                val name = parameter.name?.takeIf(XdkRename::identifier) ?: return@mapNotNull null
                val rendered = type.sourceType(structure.identityConstant) ?: return@mapNotNull null
                "$rendered $name"
            }
            val access = if (method.access == Access.PROTECTED) "protected " else ""
            CompilerMemberAction(
                structure.identityConstant, declaration.identityConstant, insertion,
                "$access$result ${signature.name}(${parameters.joinToString(", ")})", method.isAbstract,
            )
        }.distinct()
    }

/** No guesses for parameterized, relational, annotated or cross-module type spellings. */
private fun TypeConstant.sourceType(owner: IdentityConstant): String? {
    if (this !is TerminalTypeConstant) return null
    val identity = definingConstant as? IdentityConstant ?: return null
    return when {
        constantPool.getImplicitlyImportedIdentity(identity.name) == identity -> identity.name
        identity.moduleConstant == owner.moduleConstant -> identity.pathString
        else -> null
    }
}
