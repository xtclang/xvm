package org.xvm.lsp.adapter.xdk

import org.xvm.asm.MethodStructure
import org.xvm.asm.constants.CharConstant
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.IntConstant
import org.xvm.asm.constants.ParameterizedTypeConstant
import org.xvm.asm.constants.SignatureConstant
import org.xvm.asm.constants.StringConstant
import org.xvm.asm.constants.TerminalTypeConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.TypeParameterConstant

/** Render compiler-selected types, never a diagnostic display string or copied source expression. */
internal fun memberSignature(
    signature: SignatureConstant,
    declaration: MethodStructure,
    owner: IdentityConstant,
): String? {
    if (signature.paramCount != declaration.params.size || declaration.params.any { it.annotations.isNotEmpty() }) return null
    val formals =
        declaration.params.take(declaration.typeParamCount).associate { parameter ->
            val name = parameter.name?.takeIf(XdkRename::identifier) ?: return null
            parameter.asTypeParameterConstant(declaration.identityConstant) to name
        }

    fun render(type: TypeConstant): String? = type.memberSourceType(owner, formals)
    val typeParameters =
        signature.params.take(declaration.typeParamCount).zip(formals.values).map { (type, name) ->
            val constraint = type.paramTypes.singleOrNull() ?: return null
            if (constraint == constraint.constantPool.typeObject()) name else "$name extends ${render(constraint) ?: return null}"
        }
    val parameters =
        signature.params.zip(declaration.params).drop(declaration.typeParamCount).map { (type, parameter) ->
            val name = parameter.name?.takeIf(XdkRename::identifier) ?: return null
            val rendered = render(type) ?: return null
            val default =
                if (parameter.hasDefaultValue()) {
                    // A computed or not-yet-validated initializer cannot safely be transplanted to a new scope.
                    val value =
                        when (val constant = parameter.defaultValue) {
                            is StringConstant -> constant.valueString
                            is CharConstant -> constant.valueString
                            is IntConstant -> constant.valueString
                            else -> return null
                        }
                    " = $value"
                } else {
                    ""
                }
            "$rendered $name$default"
        }
    val returns = signature.returns.drop(if (declaration.isConditionalReturn) 1 else 0).map { render(it) ?: return null }
    val result =
        when (returns.size) {
            0 -> "void"
            1 -> returns.single()
            else -> returns.joinToString(", ", "(", ")")
        }
    val generic = if (typeParameters.isEmpty()) "" else typeParameters.joinToString(", ", "<", "> ")
    val conditional = if (declaration.isConditionalReturn) "conditional " else ""
    return "$generic$conditional$result ${signature.name}(${parameters.joinToString(", ")})"
}

/** Recursive named types and this method's own formals; other source spellings remain explicit refusals. */
private fun TypeConstant.memberSourceType(
    owner: IdentityConstant,
    formals: Map<TypeParameterConstant, String>,
): String? {
    return when (this) {
        is ParameterizedTypeConstant -> {
            val base = underlyingType.memberSourceType(owner, formals) ?: return null
            val arguments = paramTypes.map { it.memberSourceType(owner, formals) ?: return null }
            arguments.joinToString(", ", "$base<", ">")
        }

        is TerminalTypeConstant -> {
            when (val identity = definingConstant) {
                is TypeParameterConstant -> {
                    formals[identity]
                }

                is ClassConstant -> {
                    when {
                        constantPool.getImplicitlyImportedIdentity(identity.name) == identity -> identity.name
                        identity.moduleConstant == owner.moduleConstant -> identity.pathString
                        else -> null
                    }
                }

                else -> {
                    null
                }
            }
        }

        else -> {
            null
        }
    }
}
