package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.asm.constants.AnnotatedTypeConstant
import org.xvm.asm.constants.CharConstant
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.ImmutableTypeConstant
import org.xvm.asm.constants.IntConstant
import org.xvm.asm.constants.ParameterizedTypeConstant
import org.xvm.asm.constants.PropertyConstant
import org.xvm.asm.constants.RelationalTypeConstant
import org.xvm.asm.constants.SingletonConstant
import org.xvm.asm.constants.StringConstant
import org.xvm.asm.constants.TerminalTypeConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.TypeParameterConstant

/** Copy receiver substitutions and annotations; source identities still translate through edits. */
internal fun receiverIdentity(
    receiver: TypeConstant,
    identity: (Constant) -> ProofIdentity,
    depth: Int = 0,
): ProofIdentity? {
    if (depth >= 64 || receiver.containsUnresolved()) return null
    val type = receiver.resolveTypedefs().removeAccess()

    fun nested(type: TypeConstant) = receiverIdentity(type, identity, depth + 1)
    return when (type) {
        is TerminalTypeConstant -> {
            when (val declaration = type.definingConstant) {
                is ClassConstant, is TypeParameterConstant -> identity(declaration)
                is PropertyConstant -> declaration.takeIf { it.isFormalType }?.let(identity)
                else -> null
            }
        }

        is ParameterizedTypeConstant -> {
            val base = nested(type.underlyingType) ?: return null
            val arguments = type.paramTypes.map { nested(it) ?: return null }
            ProofIdentity.TypeShape(type.format, listOf(base) + arguments)
        }

        is AnnotatedTypeConstant -> {
            val base = nested(type.underlyingType) ?: return null
            val annotation = nested(type.annotationType) ?: return null
            val arguments =
                type.annotationParams.map { argument ->
                    when (argument) {
                        is TypeConstant -> nested(argument) ?: return null
                        is StringConstant, is CharConstant, is IntConstant -> ProofIdentity.Value(argument.format, argument.valueString)
                        is SingletonConstant -> identity(argument.classConstant)
                        else -> return null
                    }
                }
            ProofIdentity.TypeShape(type.format, listOf(base, annotation) + arguments)
        }

        is ImmutableTypeConstant -> {
            ProofIdentity.TypeShape(type.format, listOf(nested(type.underlyingType) ?: return null))
        }

        is RelationalTypeConstant -> {
            val operands = listOf(nested(type.underlyingType) ?: return null, nested(type.underlyingType2) ?: return null)
            val components =
                if (type.format == Constant.Format.DifferenceType) {
                    operands
                } else {
                    listOf(ProofIdentity.Alternatives(operands.toSet()))
                }
            ProofIdentity.TypeShape(type.format, components)
        }

        else -> {
            null
        }
    }
}
