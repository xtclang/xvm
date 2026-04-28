package org.xtclang.ecstasy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.xtclang.ecstasy.numbers.Bit;
import org.xtclang.ecstasy.numbers.Dec128;
import org.xtclang.ecstasy.numbers.Dec32;
import org.xtclang.ecstasy.numbers.Dec64;
import org.xtclang.ecstasy.numbers.Float16;
import org.xtclang.ecstasy.numbers.Float32;
import org.xtclang.ecstasy.numbers.Float64;
import org.xtclang.ecstasy.numbers.Int128;
import org.xtclang.ecstasy.numbers.Int16;
import org.xtclang.ecstasy.numbers.Int32;
import org.xtclang.ecstasy.numbers.Int64;
import org.xtclang.ecstasy.numbers.Int8;
import org.xtclang.ecstasy.numbers.Nibble;
import org.xtclang.ecstasy.numbers.UInt128;
import org.xtclang.ecstasy.numbers.UInt16;
import org.xtclang.ecstasy.numbers.UInt32;
import org.xtclang.ecstasy.numbers.UInt64;
import org.xtclang.ecstasy.numbers.UInt8;
import org.xtclang.ecstasy.reflect.Type;
import org.xtclang.ecstasy.text.String;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `Type` types must extend this class.
 */
public class nType
    extends nConst
        implements Type {

    private nType(Ctx ctx, TypeConstant type) {
        super(ctx);

        $ctx      = ctx;
        $dataType = type;
    }

    public final Ctx          $ctx;
    public final TypeConstant $dataType;

    private Method equalsMethod;
    private Method compareMethod;

    public nObj alloc(Ctx ctx) {
        throw Exception.$unsupported(ctx, "Type " + $dataType);
    }

    @Override public TypeConstant $xvmType(Ctx ctx) {
        return $dataType.getType();
    }

    @Override public boolean $isA(Ctx ctx, nType t) {
        return $dataType.isA(t.$dataType);
    }

    @Override
    public Boolean structConstructor(Ctx ctx, Type OuterType, nObj outer) {
        throw Exception.$unsupported(ctx, "Type " + $dataType);
    }

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, $dataType.getValueString());
    }

    /**
     * @return nType instance for the specified type
     */
    public static nType $ensureType(Ctx ctx, TypeConstant type) {
        // TODO: cache type -> nType
        return new nType(ctx, type);
    }

    /**
     * "Comparable" interface  support.
     */
    public boolean equals$p(Ctx ctx, Object value1, Object value2) {
        if (value1 == value2) {
            return true;
        }

        if (equalsMethod == null) {
            equalsMethod = ensureMethod("equals$p", Object.class);
        }

        try {
            java.lang.Boolean result = (java.lang.Boolean)
                    equalsMethod.invoke(null, ctx, this, value1, value2);
            return result.booleanValue();
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw Exception.$unsupported($ctx,
                "Failed to invoke 'equals()` on class " + $dataType.getValueString());
        }
    }


    /**
     * "Orderable" interface  support.
     */
    public Ordered compare(Ctx ctx, Orderable value1, Orderable value2) {
        if (value1 == value2) {
            return Ordered.Equal.$INSTANCE;
        }

        nType type1 = ((nObj) value1).$type(ctx);
        nType type2 = ((nObj) value2).$type(ctx);
        if ($dataType.isJitPrimitive() && type1.$dataType.equals(type2.$dataType)) {
            int result = switch (value1) {
                case Bit n1    -> n1.$value - ((Bit)    value2).$value;
                case Nibble n1 -> n1.$value - ((Nibble) value2).$value;
                case Int8 n1   -> n1.$value - ((Int8)   value2).$value;
                case Int16 n1  -> n1.$value - ((Int16)  value2).$value;
                case Int32 n1  -> n1.$value - ((Int32)  value2).$value;
                case Int64 n1  -> Long.compare(n1.$value, ((Int64)  value2).$value);
                case UInt8 n1  -> n1.$value - ((UInt8)  value2).$value;
                case UInt16 n1 -> n1.$value - ((UInt16) value2).$value;
                case UInt32 n1 -> n1.$value - ((UInt32) value2).$value;
                case UInt64 n1 -> Long.compareUnsigned(n1.$value, ((UInt64) value2).$value);

                case Int128 n1 -> Int128.$compare(n1.$lowValue, n1.$highValue,
                        ((Int128)  value2).$lowValue, ((Int128)  value2).$highValue);
                case UInt128 n1 -> UInt128.$compare(n1.$lowValue, n1.$highValue,
                        ((UInt128) value2).$lowValue, ((UInt128) value2).$highValue);

                case Float16 n1 -> Float.compare(n1.$value, ((Float16) value2).$value);
                case Float32 n1 -> Float.compare(n1.$value, ((Float32) value2).$value);
                case Float64 n1 -> Double.compare(n1.$value, ((Float64) value2).$value);

                case Dec32 n1 -> Dec32.$compare(n1.$bits, ((Dec32)  value2).$bits);
                case Dec64 n1 -> Dec64.$compare(n1.$bits, ((Dec64)  value2).$bits);
                case Dec128 n1 -> Dec128.$compare(n1.$lowBits, n1.$highBits,
                        ((Dec128) value2).$lowBits, ((Dec128) value2).$highBits);

                default -> throw new UnsupportedOperationException($dataType.getValueString());
            };
            return result < 0 ? Ordered.Lesser.$INSTANCE
                    : result > 0 ? Ordered.Greater.$INSTANCE
                    : Ordered.Equal.$INSTANCE;
        }

        if (compareMethod == null) {
            compareMethod = ensureMethod("compare", Orderable.class);
        }

        try {
            return (Ordered) compareMethod.invoke(null, ctx, this, value1, value2);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw Exception.$unsupported($ctx,
                "Failed to invoke 'compare()` on class " + $dataType.getValueString());
        }
    }

    private Method ensureMethod(java.lang.String methodName, java.lang.Class paramClass) {
        java.lang.String clzName = $dataType.ensureJitClassName($ctx.container.typeSystem);
        java.lang.Class  clz;
        try {
            clz = java.lang.Class.forName(clzName);
        } catch (ClassNotFoundException e) {
            throw Exception.$unsupported($ctx, "No such class " + clzName);
        }

        while (true) {
            try {
                return clz.getDeclaredMethod(methodName,
                        Ctx.class, nType.class, paramClass, paramClass);
            } catch (NoSuchMethodException e) {
                clz = clz.getSuperclass();
                if (clz == null) {
                    throw Exception.$unsupported($ctx,
                        "No method " + methodName + " on class " + $dataType.getValueString());
                }
            }
        }
    }
}
