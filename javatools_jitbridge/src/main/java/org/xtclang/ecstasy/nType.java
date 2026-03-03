package org.xtclang.ecstasy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
        return new nType(ctx, type);
    }

    /**
     * "Comparable" interface  support.
     */
    public boolean equals$p(Ctx ctx, Comparable value1, Comparable value2) {
        if (value1 == value2) {
            return true;
        }

        if (equalsMethod == null) {
            equalsMethod = ensureMethod("equals", Comparable.class);
        }

        try {
            Boolean result = (Boolean) equalsMethod.invoke(null, ctx, value1, value2);
            return result.$value;
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

    private Method ensureMethod(java.lang.String methodName, Class paramClass) {
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
