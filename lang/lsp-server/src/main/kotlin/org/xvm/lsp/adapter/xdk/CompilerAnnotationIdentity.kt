package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.asm.constants.ArrayConstant
import org.xvm.asm.constants.ByteConstant
import org.xvm.asm.constants.CharConstant
import org.xvm.asm.constants.DecimalAutoConstant
import org.xvm.asm.constants.DecimalConstant
import org.xvm.asm.constants.FPNConstant
import org.xvm.asm.constants.Float128Constant
import org.xvm.asm.constants.Float64Constant
import org.xvm.asm.constants.FloatConstant
import org.xvm.asm.constants.IntConstant
import org.xvm.asm.constants.LiteralConstant
import org.xvm.asm.constants.MapConstant
import org.xvm.asm.constants.RangeConstant
import org.xvm.asm.constants.RegExConstant
import org.xvm.asm.constants.SingletonConstant
import org.xvm.asm.constants.StringConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.UInt8ArrayConstant
import java.util.HexFormat

/** Exact detached annotation values; display strings are not identities for compound values. */
internal fun annotationIdentity(value: Constant, identity: (Constant) -> ProofIdentity, depth: Int = 0): ProofIdentity? {
    if (depth >= 64 || value.containsUnresolved()) return null
    fun nested(argument: Constant) = annotationIdentity(argument, identity, depth + 1)
    fun scalar(text: String) = ProofIdentity.Value(value.format, text)
    fun bytes(value: ByteArray) = scalar(HexFormat.of().formatHex(value))
    return when (value) {
        is TypeConstant -> receiverIdentity(value, identity, depth + 1)
        is SingletonConstant -> identity(value.classConstant)
        is ByteConstant, is CharConstant, is IntConstant, is StringConstant, is LiteralConstant -> scalar(value.valueString)
        is FloatConstant -> scalar(value.value.toRawBits().toString())
        is Float64Constant -> scalar(value.value.toRawBits().toString())
        is Float128Constant -> bytes(value.value)
        is FPNConstant -> bytes(value.value)
        is DecimalConstant -> bytes(value.value.toByteArray())
        is DecimalAutoConstant -> bytes(value.value.toByteArray())
        is UInt8ArrayConstant -> bytes(value.value)
        is RegExConstant -> scalar("${value.flags}:${value.value}")
        is ArrayConstant -> ProofIdentity.TypeShape(value.format, listOf(nested(value.type) ?: return null) + value.value.map { nested(it) ?: return null })
        is MapConstant -> ProofIdentity.TypeShape(value.format, listOf(nested(value.type) ?: return null) + value.value.entries.map { (key, item) ->
            ProofIdentity.TypeShape(Constant.Format.MapEntry, listOf(nested(key) ?: return null, nested(item) ?: return null))
        })
        is RangeConstant -> ProofIdentity.TypeShape(value.format, listOf(
            nested(value.first) ?: return null, nested(value.last) ?: return null,
            scalar("${value.isFirstExcluded}:${value.isLastExcluded}"),
        ))
        // Runtime handles, deferred computations and filesystem objects need their own contracts.
        else -> null
    }
}
