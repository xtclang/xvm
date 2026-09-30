package org.xtclang.ecstasy.reflect;

import java.lang.invoke.MethodHandle;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.nObject;
import org.xtclang.ecstasy.nException;
import org.xtclang.ecstasy.nType;

import org.xtclang.ecstasy.numbers.*;

import org.xtclang.ecstasy.temporal.Date;
import org.xtclang.ecstasy.temporal.Duration;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * A simple native Ref/Var implementation.
 */
public class nRef
        extends nObject
        implements Var {
    /**
     * Construct a value-backed Ref or Var.
     *
     * @param ctx           the current context
     * @param referent      the initial referent
     * @param referentType  the referent type
     * @param isVar         true for a Var; false for a Ref
     */
    public nRef(Ctx ctx, nObject referent, TypeConstant referentType, boolean isVar) {
        super(ctx);

        $referent     = referent;
        $referentType = referentType;
        $isVar        = isVar;
        $getter       = null;
        $setter       = null;
    }

    /**
     * Construct a property-backed Ref or Var.
     *
     * @param ctx           the current context
     * @param referentType  the referent type
     * @param isVar         true for a Var; false for a Ref
     * @param getter        the property getter
     * @param setter        the property setter for a Var, or null for a Ref
     */
    public nRef(Ctx ctx, TypeConstant referentType, boolean isVar,
                MethodHandle getter, MethodHandle setter) {
        super(ctx);

        assert getter != null;
        assert isVar == (setter != null);

        $referent     = null;
        $referentType = referentType;
        $isVar        = isVar;
        $getter       = getter;
        $setter       = setter;
    }

    public nObject $referent;
    public final TypeConstant $referentType;
    public final boolean      $isVar;
    public final MethodHandle $getter;
    public final MethodHandle $setter;

    public nType Referent$get(Ctx ctx) {
        return nType.$ensureType(ctx, $referentType);
    }

    @Override
    public boolean assigned$get$p(Ctx ctx) {
        return $getter == null
                ? $referent != null
                : get(ctx) != null;
    }

    @Override
    public Object get(Ctx ctx) {
        if ($getter == null) {
            // value-backed Ref
            return $referent;
        }

        try {
            return (Object) $getter.invoke(ctx);
        } catch (nException nEx) {
            throw nEx;
        } catch (Throwable e) {
            throw Exception.$typeMismatch(ctx, e.getMessage());
        }
    }

    @Override
    public void set(Ctx ctx, Object value) {
        if (!$isVar) {
            throw Exception.$ro(ctx, "Ref is read-only");
        }

        if ($setter == null) {
            // value-backed Var
            $referent = (nObject) value;
            return;
        }

        // property-backed Var
        try {
            $setter.invoke(ctx, value);
        } catch (nException nEx) {
            throw nEx;
        } catch (Throwable e) {
            throw Exception.$typeMismatch(ctx, e.getMessage());
        }
    }

    @Override
    public boolean $isImmut() {
        return false;
    }

    /**
     * Native implementation of:
     *
     * <pre>{@code
     *     static <CompileType extends Ref> Boolean equals(CompileType value1, CompileType value2)
     * }</pre>
     * Used by {@link Ref#equals$p(Ctx, nType, Ref, Ref)} for native and generated references.
     */
    public static boolean equals$p(Ctx ctx, nType CompileType, Ref ref1, Ref ref2) {
        nObject value1 = (nObject) ref1.get(ctx);
        nObject value2 = (nObject) ref2.get(ctx);
        if (value1 == value2) {
            return true;
        }
        if (value1 == null || value2 == null) {
            return false;
        }

        TypeConstant type1 = value1.$xvmType(ctx);
        if (type1.isJitPrimitive() && value2.$xvmType(ctx).equals(type1)) {
            if (value1 instanceof org.xtclang.ecstasy.numbers.Number) {
                return switch (value1) {
                    case Bit    n1 -> n1.$value == ((Bit)    value2).$value;
                    case Nibble n1 -> n1.$value == ((Nibble) value2).$value;
                    case Int8   n1 -> n1.$value == ((Int8)   value2).$value;
                    case Int16  n1 -> n1.$value == ((Int16)  value2).$value;
                    case Int32  n1 -> n1.$value == ((Int32)  value2).$value;
                    case Int64  n1 -> n1.$value == ((Int64)  value2).$value;
                    case UInt8  n1 -> n1.$value == ((UInt8)  value2).$value;
                    case UInt16 n1 -> n1.$value == ((UInt16) value2).$value;
                    case UInt32 n1 -> n1.$value == ((UInt32) value2).$value;
                    case UInt64 n1 -> n1.$value == ((UInt64) value2).$value;

                    case Int128  n1 -> n1.$lowValue  == ((Int128)  value2).$lowValue
                                   && n1.$highValue  == ((Int128)  value2).$highValue;
                    case UInt128 n1 -> n1.$lowValue  == ((UInt128) value2).$lowValue
                                    && n1.$highValue == ((UInt128) value2).$highValue;

                    case BFloat16 n1 -> n1.$value == ((BFloat16) value2).$value;
                    case Float8e4 n1 -> Float8e4.$compare(n1.$value, ((Float8e4) value2).$value) == 0;
                    case Float8e5 n1 -> Float8e5.$compare(n1.$value, ((Float8e5) value2).$value) == 0;
                    case Float16 n1 -> n1.$value == ((Float16) value2).$value;
                    case Float32 n1 -> n1.$value == ((Float32) value2).$value;
                    case Float64 n1 -> n1.$value == ((Float64) value2).$value;

                    case Dec32  n1 -> n1.$bits == ((Dec32)  value2).$bits;
                    case Dec64  n1 -> n1.$bits == ((Dec64)  value2).$bits;
                    case Dec128 n1 -> n1.$highBits == ((Dec128) value2).$highBits
                                   && n1.$lowBits  == ((Dec128) value2).$lowBits;
                    default -> throw new UnsupportedOperationException(type1.getValueString());
                };
            }
            // else non-number JIT primitive
            return switch (value1) {
                case Date     dt -> Date.$equals(dt.epochDay, ((Date) value2).epochDay);
                case Duration d1 -> {
                    Duration d2 = (Duration) value2;
                    yield Duration.$equals(d1.picoseconds$0, d1.picoseconds$1,
                                           d2.picoseconds$0, d2.picoseconds$1);
                }
                default -> throw new UnsupportedOperationException("TODO " + type1);
            };
        }
        return false;
    }
}
