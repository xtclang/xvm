package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Format
import org.xvm.asm.Constants.Access
import org.xvm.asm.constants.TypeConstant
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.TypeCompositionStatement

/**
 * Attempt-owned source types, including substitutions actually validated by the compiler.
 * Conditional compositions may exist on Box<String> without existing on its formal Box<T>.
 * Never invent instantiations or ask a bare type parameter for a private class view.
 */
internal fun compilerSourceTypes(
    nodes: List<AstNode>,
    includeMixins: Boolean = true,
): List<TypeConstant> {
    val classes =
        nodes
            .filterIsInstance<TypeCompositionStatement>()
            .mapNotNull { it.component as? ClassStructure }
            .filter { includeMixins || it.format != Format.MIXIN }
            .associateBy { it.identityConstant }
    val instantiated =
        nodes
            .filterIsInstance<Expression>()
            .filter { it.isValidated && it.typeFit.isFit }
            .mapNotNull { it.type }
            .filter { !it.isFormalType && it.isSingleUnderlyingClass(false) && it.getSingleUnderlyingClass(false) in classes }
    return (classes.values.map { it.formalType } + instantiated)
        .map { it.ensureAccess(Access.PRIVATE) }
        .distinct()
}
