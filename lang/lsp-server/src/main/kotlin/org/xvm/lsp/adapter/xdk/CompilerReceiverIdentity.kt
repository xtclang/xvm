package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.asm.constants.AnnotatedTypeConstant
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.ImmutableTypeConstant
import org.xvm.asm.constants.InnerChildTypeConstant
import org.xvm.asm.constants.ParameterizedTypeConstant
import org.xvm.asm.constants.PropertyConstant
import org.xvm.asm.constants.RelationalTypeConstant
import org.xvm.asm.constants.RecursiveTypeConstant
import org.xvm.asm.constants.ServiceTypeConstant
import org.xvm.asm.constants.TerminalTypeConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.TypeParameterConstant
import org.xvm.asm.constants.VirtualChildTypeConstant

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
                type.annotationParams.map { annotationIdentity(it, identity, depth + 1) ?: return null }
            ProofIdentity.TypeShape(type.format, listOf(base, annotation) + arguments)
        }

        is ImmutableTypeConstant, is ServiceTypeConstant -> {
            ProofIdentity.TypeShape(type.format, listOf(nested(type.underlyingType) ?: return null))
        }

        is VirtualChildTypeConstant -> {
            val parent = nested(type.parentType) ?: return null
            val origin = type.originParentType?.let { nested(it) ?: return null }
            ProofIdentity.TypeShape(type.format, listOf(parent, identity(type.definingConstant)) + listOfNotNull(origin))
        }

        is InnerChildTypeConstant -> {
            ProofIdentity.TypeShape(type.format, listOf(nested(type.parentType) ?: return null, identity(type.definingConstant)))
        }

        is RecursiveTypeConstant -> identity(type.typedef)

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
