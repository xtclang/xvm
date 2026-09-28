package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.MethodStructure
import org.xvm.asm.PropertyStructure
import org.xvm.asm.constants.PropertyConstant

/**
 * The compiler creates one synthetic shorthand constructor from a class header. Its parameters
 * initialize the correspondingly named properties. Both flags survive artifact serialization;
 * explicit constructors have their own written parameter contracts and are not joined here.
 * Read only on the compiler worker, then copy the property identity into detached source facts.
 */
internal fun MethodStructure.primaryProperty(index: Int): PropertyConstant? {
    if (!isConstructor || !isSynthetic || !isShorthandConstructor) return null
    val owner = identityConstant.namespace.component as? ClassStructure ?: return null
    val parameter = params.getOrNull(index + typeParamCount) ?: return null
    return (owner.getChild(parameter.name) as? PropertyStructure)?.identityConstant
}
