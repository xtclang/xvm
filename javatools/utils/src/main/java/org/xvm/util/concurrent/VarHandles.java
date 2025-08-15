package org.xvm.util.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Helper class for easily obtaining {@link VarHandle} instances.
 *
 * @author mf
 */
public class VarHandles {
    /**
     * Return a {@link VarHandle} for accessing the specified field within the provided class.
     * @param clz the class
     * @param fieldName the field within the class to access
     * @return the {@link VarHandle}.
     */
    public static VarHandle of(Class<?> clz, String fieldName) {
        try {
            Field field = clz.getDeclaredField(fieldName);
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(clz, MethodHandles.lookup());
            return Modifier.isStatic(field.getModifiers())
                    ? lookup.findStaticVarHandle(clz, fieldName, field.getType())
                    : lookup.findVarHandle(clz, fieldName, field.getType());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Return a {@link VarHandle} for accessing the element of an array of the specified type.
     *
     * @param arrayClz the array type, i.e. {@code int[].class}
     * @return the {@link VarHandle}
     */
    public static VarHandle ofArray(Class<?> arrayClz) {
        return MethodHandles.arrayElementVarHandle(arrayClz);
    }
}
