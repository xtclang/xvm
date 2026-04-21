package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.nObj;
import org.xtclang.ecstasy.nType;

import org.xtclang.ecstasy.numbers.*;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * A simple native Ref implementation.
 */
public class nRef
        extends nObj
        implements Var {
    public nRef(Ctx ctx, nObj referent, TypeConstant referentType, boolean isVar) {
        super(ctx);

        $referent     = referent;
        $referentType = referentType;
        $isVar        = isVar;
    }

    public nObj               $referent;
    public final TypeConstant $referentType;
    public final boolean      $isVar;

    public nType Referent$get(Ctx ctx) {
        return nType.$ensureType(ctx, $referentType);
    }

    @Override
    public nObj get(Ctx ctx) {
        return $referent;
    }

    @Override
    public void set(Ctx ctx, nObj referent) {
        if ($isVar) {
            $referent = referent;
        } else {
            throw Exception.$ro(ctx, "Ref is read-only");
        }
    }

    @Override
    public boolean $isImmut() {
        return false;
    }

    /**
     * Native implementation of:
     *
     *  static <CompileType extends Ref> Boolean equals(CompileType value1, CompileType value2)
     */
    public static boolean equals$p(Ctx ctx, nType CompileType, nRef ref1, nRef ref2) {
        if (ref1.$referentType.isJitPrimitive() && ref2.$referentType.equals(ref1.$referentType)) {
            if (ref1.$referent instanceof org.xtclang.ecstasy.numbers.Number) {
                return switch (ref1.$referent) {
                    case Bit    n1 -> n1.$value == ((Bit)    ref2.$referent).$value;
                    case Nibble n1 -> n1.$value == ((Nibble) ref2.$referent).$value;
                    case Int8   n1 -> n1.$value == ((Int8)   ref2.$referent).$value;
                    case Int16  n1 -> n1.$value == ((Int16)  ref2.$referent).$value;
                    case Int32  n1 -> n1.$value == ((Int32)  ref2.$referent).$value;
                    case Int64  n1 -> n1.$value == ((Int64)  ref2.$referent).$value;
                    case UInt8  n1 -> n1.$value == ((UInt8)  ref2.$referent).$value;
                    case UInt16 n1 -> n1.$value == ((UInt16) ref2.$referent).$value;
                    case UInt32 n1 -> n1.$value == ((UInt32) ref2.$referent).$value;
                    case UInt64 n1 -> n1.$value == ((UInt64) ref2.$referent).$value;

                    case Int128  n1 -> n1.$lowValue  == ((Int128)  ref2.$referent).$lowValue
                                   && n1.$highValue  == ((Int128)  ref2.$referent).$highValue;
                    case UInt128 n1 -> n1.$lowValue  == ((UInt128) ref2.$referent).$lowValue
                                    && n1.$highValue == ((UInt128) ref2.$referent).$highValue;

                    case Float16 n1 -> n1.$value == ((Float16) ref2.$referent).$value;
                    case Float32 n1 -> n1.$value == ((Float32) ref2.$referent).$value;
                    case Float64 n1 -> n1.$value == ((Float64) ref2.$referent).$value;

                    case Dec32  n1 -> n1.$bits == ((Dec32)  ref2.$referent).$bits;
                    case Dec64  n1 -> n1.$bits == ((Dec64)  ref2.$referent).$bits;
                    case Dec128 n1 -> n1.$highBits == ((Dec128) ref2.$referent).$highBits
                                   && n1.$lowBits  == ((Dec128) ref2.$referent).$lowBits;
                    default -> throw new UnsupportedOperationException(ref1.$referentType.getValueString());
                };
            }
            throw new UnsupportedOperationException("TODO " + ref1.$referentType);
        }
        return ref1.$referent == ref2.$referent;
    }
}
