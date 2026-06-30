package org.xtclang.ecstasy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.xtclang.ecstasy.collections.Hashable;

import org.xtclang.ecstasy.numbers.*;

import org.xtclang.ecstasy.reflect.Class;
import org.xtclang.ecstasy.reflect.Type;

import org.xtclang.ecstasy.text.Char;
import org.xtclang.ecstasy.text.String;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;
import org.xvm.javajit.TypeSystem;

import org.xvm.util.ByteHashCollector;

/**
 * All Ecstasy `Type` types must extend this class.
 */
public class nType
        extends nConst
        implements Type {

    private nType(Ctx ctx, TypeConstant type) {
        super(ctx);

        assert !type.containsFormalType(true);

        $ctx      = ctx;
        $dataType = type;
    }

    public final Ctx          $ctx;
    public final TypeConstant $dataType;

    private Method equalsMethod;
    private Method compareMethod;
    private Method hashCodeMethod;
    private Class  xvmClass;

    public nObj alloc(Ctx ctx) {
        throw Exception.$unsupported(ctx, "Type " + $dataType);
    }

    /**
     * @return  an instance of the {@link Class class of class} for this type
     */
    public Class $xvmClass(Ctx ctx) {
        if (xvmClass == null) {
            TypeSystem       typeSystem  = ctx.container.typeSystem;
            java.lang.String className   = $dataType.ensureJitClassName(typeSystem);
            java.lang.String classOfType = TypeSystem.classOfClass(className);
            try {
                java.lang.Class clz;
                try {
                    clz = typeSystem.loader.loadClass(classOfType);
                } catch (ClassNotFoundException cnfe) {
                    return xvmClass = new Class(ctx, $dataType);
                }
                xvmClass = (Class) clz.getDeclaredConstructor(Ctx.class, TypeConstant.class).
                            newInstance(ctx, $dataType);

            } catch (java.lang.Exception e) {
                throw new Exception(ctx).$init(ctx, "Failed to load a class for: " + className, e);
            }
        }
        return xvmClass;
    }

    @Override public TypeConstant $xvmType(Ctx ctx) {
        return $dataType.getType();
    }

    @Override public boolean $isA(Ctx ctx, nType t) {
        return $dataType.isA(t.$dataType);
    }

    @Override
    public Boolean structConstructor(Ctx ctx, Type OuterType, Object outer) {
        throw Exception.$unsupported(ctx, "Type " + $dataType);
    }

    // TODO: Stringable methods below are temporary; remove when we can compile Type.x
    public long estimateStringLength$p(Ctx ctx) {
        return 0;
    }

    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : $dataType.getValueString().toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
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

        // TODO: replace the reflection with a virtual call to $xvmClass().equals(this, v1, v2)
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

    public long hashCode$p(Ctx ctx, Hashable value) {
        if ($dataType.isJitPrimitive() ) {
            ByteHashCollector collector = ctx.createHashCollector();
            collector = switch (value) {
                case Bit n1     -> collector.addInt8(n1.$value);
                case Boolean b  -> collector.addInt8(b.$value ? 1 : 0);
                case Char c     -> collector.addInt32(c.$value);
                case Nibble n1  -> collector.addInt8(n1.$value);
                case Int8 n1    -> collector.addInt8(n1.$value);
                case Int16 n1   -> collector.addInt16(n1.$value);
                case Int32 n1   -> collector.addInt32(n1.$value);
                case Int64 n1   -> collector.addLong(n1.$value);
                case UInt8 n1   -> collector.addInt8(n1.$value);
                case UInt16 n1  -> collector.addInt16(n1.$value);
                case UInt32 n1  -> collector.addInt32(n1.$value);
                case UInt64 n1  -> collector.addLong(n1.$value);

                case Int128 n1  -> collector.addLong(n1.$lowValue).addLong(n1.$highValue);
                case UInt128 n1 -> collector.addLong(n1.$lowValue).addLong(n1.$highValue);

                case Float32 n1 -> collector.addInt32(Float.floatToRawIntBits(n1.$value));
                case Float64 n1 -> collector.addLong(Double.doubleToRawLongBits(n1.$value));

                case Dec32 n1 -> collector.addInt32(n1.$bits);
                case Dec64 n1 -> collector.addLong(n1.$bits);
                case Dec128 n1 -> collector.addLong(n1.$lowBits).addLong(n1.$highBits);

                default -> throw new UnsupportedOperationException($dataType.getValueString());
            };
            return collector.compute();
        }

        if (hashCodeMethod == null) {
            hashCodeMethod = ensureMethod("hashCode$p", Hashable.class);
        }

        try {
            return (long) hashCodeMethod.invoke(null, ctx, this, value);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw Exception.$unsupported($ctx,
                    "Failed to invoke 'hashCode$p()` on class " + $dataType.getValueString());
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
