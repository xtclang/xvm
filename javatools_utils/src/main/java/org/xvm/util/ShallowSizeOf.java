package org.xvm.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A set of helpers for computing the shallow size of objects.
 *
 * @author mf
 */
public class ShallowSizeOf
    {
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
    private static final Map<Class<?>, Integer> SIZE_BY_CLASS = new ConcurrentHashMap<>();

    /**
     * Return the shallow size of the given object.
     *
     * @param o the object to size
     * @return the object size
     */
    public static int object(Object o)
        {
        if (o == null)
            {
            return 0;
            }
        Class<?> clz = o.getClass();
        return clz.isArray() ? arrayOf(clz.getComponentType(), Array.getLength(o)) : align(instanceOf(clz));
        }

    /**
     * Return the shallow size of an array of a given type.
     *
     * @param clzComp the array's component type
     * @param slots   the number of slots in the array
     * @return the size in bytes
     */
    public static int arrayOf(Class<?> clzComp, int slots)
        {
        return align(HEADER + 4 + (fieldOf(clzComp) * slots));
        }

    /**
     * Return a rough estimate of the shallow size of instances of a given java class.
     *
     * @param clz the class
     * @return its estimated shallow size in bytes
     */
    public static int instanceOf(Class<?> clz)
        {
        Integer size = SIZE_BY_CLASS.get(clz);
        if (size == null)
            {
            int cb = clz == Object.class ? HEADER : 0;
            for (Field f : clz.getDeclaredFields())
                {
                if (!Modifier.isStatic(f.getModifiers()))
                    {
                    cb += fieldOf(f.getType());
                    }
                }

            if (clz.isArray())
                {
                cb += fieldOf(int.class);
                }

            Class<?> clzSuper = clz.getSuperclass();
            if (clzSuper != null)
                {
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
     * Return the size of a field of the given type in bytes.
     *
     * @param type the field type
     * @return the byte size
     */
    public static int fieldOf(Class<?> type)
        {
        if (type.isPrimitive())
            {
            if (type == long.class)
                {
                return 8;
                }
            else if (type == double.class)
                {
                return 8;
                }
            else if (type == int.class)
                {
                return 4;
                }
            else if (type == float.class)
                {
                return 4;
                }
            else if (type == boolean.class)
                {
                return 1;
                }
            else if (type == byte.class)
                {
                return 1;
                }
            else if (type == char.class)
                {
                return 2;
                }
            else if (type == short.class)
                {
                return 2;
                }
            else
                {
                throw new IllegalStateException();
                }
            }
        else
            {
            return COMPRESSED ? 4 : 8;
            }
        }

    /**
     * Compute the aligned size of object of a given size.
     *
     * @param cb the object size
     * @return the aligned size
     */
    private static int align(int cb)
        {
        return (cb + 7) & ~7;
        }
    }
