package org.xtclang.ecstasy.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.collections.ArrayᐸObjectᐳ;
import org.xtclang.ecstasy.nEnum;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;
import org.xvm.javajit.TypeSystem;

import static org.xvm.javajit.Builder.Instance;

/**
 * All Ecstasy {@code Class<Enum>} types must extend this class.
 */
public abstract class Enumeration extends Class {
    protected Enumeration(Ctx ctx, TypeConstant publicType) {
        super(ctx, publicType);
    }

    /**
     * A helper method to obtain an instance of an enum value.
     *
     * @param ctx    the current context
     * @param type   the enum type
     * @param value  the name of the enum value to return
     *
     * @return the enum value with the requested name
     *
     * @throws org.xtclang.ecstasy.IllegalArgument if the enum type does not have a value with
     *         the specified name
     */
    public static nEnum $getEnumValue(Ctx ctx, TypeConstant type, java.lang.String value) {
        TypeSystem       typeSystem = ctx.container.typeSystem;
        java.lang.String className  = type.getJitCCName(typeSystem);
        java.lang.String enumClass  = TypeSystem.enumerationClass(className);
        ArrayᐸObjectᐳ    values;

        try {
            java.lang.Class<?> clz         = typeSystem.loader.loadClass(enumClass);
            Field              field       = clz.getDeclaredField(Instance);
            Enumeration        enumeration = (Enumeration) field.get(null);
            Method             method      = clz.getDeclaredMethod("values$get", Ctx.class);
            values = (ArrayᐸObjectᐳ) method.invoke(enumeration, ctx);
            long               count       = values.size$get$p(ctx);

            for (long i = 0; i < count; i++) {
                nEnum e = (nEnum) values.getElement$p(ctx, i);
                if (e.name$get(ctx).toString().equals(value)) {
                    return e;
                }
            }
        } catch (java.lang.Exception e) {
            throw new RuntimeException(e);
        }

        java.lang.String name = type.getSingleUnderlyingClass(false).getName();
        throw Exception.$illegalArg(ctx, "Injectable " + name + "=\"" + value
                + "\" does not match any names in enum " + className + " "
                + values.toString(ctx).toString());
    }
}
