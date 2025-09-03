package org.xvm.util;

import java.lang.constant.ClassDesc;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A set of helpers for computing the shallow size of objects.
 */
public final class ShallowSizeOf {
    /**
     * {@code true} if this JVM uses compressed references and object headers
     */
    static final boolean COMPRESSED = Runtime.getRuntime().maxMemory() < 30L * 1024 * 1024 * 1024;

    /**
     * The size of an object header.
     */
    private static final int HEADER = COMPRESSED ? 12 : 16;

    /**
     * A mapping of Java class type to its instances shallow object size.
     */
    private static final Map<Class<?>, Long> SIZE_BY_CLASS = new ConcurrentHashMap<>();

    private ShallowSizeOf() {}

    /**
     * Return the shallow size of the given object.
     *
     * @param o the object to size
     *
     * @return the object size
     */
    public static long object(Object o) {
        if (o == null) {
            return 0;
        }
        Class<?> clz = o.getClass();
        return clz.isArray()
            ? arrayOf(clz.getComponentType(), Array.getLength(o))
            : align(instanceOf(clz));
    }

    /**
     * Return the shallow size of an array of a given type.
     *
     * @param clzComp  the array's component type
     * @param slots    the number of slots in the array
     *
     * @return the size in bytes
     */
    public static long arrayOf(Class<?> clzComp, int slots) {
        return align(HEADER + 4 + (fieldOf(clzComp) * (long) slots));
    }

    /**
     * Return a rough estimate of the shallow size of instances of a given java class.
     *
     * @param clz the class
     *
     * @return its estimated shallow size in bytes
     */
    public static long instanceOf(Class<?> clz) {
        Long size = SIZE_BY_CLASS.get(clz);
        if (size == null) {
            long cb = clz == Object.class ? HEADER : 0;
            for (Field f : clz.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    cb += fieldOf(f.getType());
                }
            }

            if (clz.isArray()) {
                cb += fieldOf(int.class);
            }

            Class<?> clzSuper = clz.getSuperclass();
            if (clzSuper != null) {
                cb += instanceOf(clzSuper);
            }

            // TODO: account for field alignment
            SIZE_BY_CLASS.put(clz, cb);
            return cb;
        }

        // assuming 8 byte word alignment for each allocation
        return size;
    }

    /**
     * Return the size of a field of the given Java class in bytes.
     *
     * @param clz the field class
     *
     * @return the byte size
     */
    public static int fieldOf(Class<?> clz) {
        if (clz.isPrimitive()) {
            if (clz == long.class) {
                return 8;
            } else if (clz == double.class) {
                return 8;
            } else if (clz == int.class) {
                return 4;
            } else if (clz == float.class) {
                return 4;
            } else if (clz == boolean.class) {
                return 1;
            } else if (clz == byte.class) {
                return 1;
            } else if (clz == char.class) {
                return 2;
            } else if (clz == short.class) {
                return 2;
            } else {
                throw new IllegalStateException();
            }
        } else {
            return COMPRESSED ? 4 : 8;
        }
    }
    /**
     * Return the size of a field of the given ClassDesc in bytes.
     *
     * @param cd the field ClassDesc
     *
     * @return the byte size
     */
    public static int fieldOf(ClassDesc cd) {
        if (cd.isPrimitive()) {
            return switch (cd.descriptorString()) {
                case "J", "D" -> 8;
                case "I", "F" -> 4;
                case "C", "S" -> 2;
                case "B", "Z" -> 1;
                default       -> throw new IllegalStateException();
            };
        } else {
            return COMPRESSED ? 4 : 8;
        }
    }

    /**
     * Compute the aligned size of object of a given size.
     *
     * @param cb the object size
     * @return the aligned size
     */
    public static long align(long cb) {
        return (cb + 7) & ~7;
    }
}
