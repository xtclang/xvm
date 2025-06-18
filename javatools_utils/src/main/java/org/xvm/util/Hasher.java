package org.xvm.util;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.function.Function;

/**
 * An interface encapsulating hashing for a given type.
 *
 * @author falcom
 */
public interface Hasher<T>
    {
    /**
     * Return a hash for the supplied object.
     *
     * @param object the object to hash
     * @return the hash of the object
     */
    int hash(T object);

    /**
     * Return {@code true} if the hasher considers the two objects to be equal.
     *
     * @param a the first object
     * @param b the second object
     * @return {@code true} iff the objects are considered to be equal
     */
    boolean equals(T a, T b);

    /**
     * @param <T> the object type
     * @return a hasher which delegates to {@link Objects#hashCode} and {@link Objects#hashCode}.
     */
    @SuppressWarnings("unchecked")
    static <T> Hasher<T> natural()
        {
        class Natural implements Hasher<Object>
            {
            @Override
            public int hash(Object object)
                {
                return Objects.hashCode(object);
                }

            @Override
            public boolean equals(Object a, Object b)
                {
                return Objects.equals(a, b);
                }
            }

        return (Hasher<T>) new Natural();
        }

    /**
     * @param <T> the object type
     * @return a hasher which delegates to {@link System#identityHashCode} and reference equality.
     */
    @SuppressWarnings("unchecked")
    static <T> Hasher<T> identity()
        {
        class Identity implements Hasher<Object>
            {
            @Override
            public int hash(Object object)
                {
                return System.identityHashCode(object);
                }

            @Override
            public boolean equals(Object a, Object b)
                {
                return a == b;
                }
            }

        return (Hasher<T>) new Identity();
        }

    /**
     * Return a hasher which operates on just a portion of the supplied object.
     *
     * @param extractor a funciton used to extract from the source object
     * @param <U>       the outer type to extract from
     * @return the hasher
     */
    default <U> Hasher<U> using(Function<? super U, ? extends T> extractor)
        {
        Hasher<T> outer = this;
        return new Hasher<>()
            {
            @Override
            public int hash(U object)
                {
                return outer.hash(extractor.apply(object));
                }

            @Override
            public boolean equals(U a, U b)
                {
                return outer.equals(extractor.apply(a), extractor.apply(b));
                }
            };
        }
    }
