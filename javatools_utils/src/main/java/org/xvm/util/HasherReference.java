package org.xvm.util;

import java.util.function.Supplier;

/**
 * A {@code Reference} like class which uses {@link Hasher} for equality.
 *
 * @author falcom
 */
public class HasherReference<T> implements Supplier<T>
    {
    /**
     * The hasher being used to compare referents.
     */
    private Hasher<? super T> hasher;

    /**
     * The referent
     */
    private T referent;

    /**
     * Return a new {@link HasherReference}.
     *
     * @param referent the referent
     * @param hasher   the {@link Hasher} used to determine equality
     */
    public HasherReference(T referent, Hasher<? super T> hasher)
        {
        this.referent = referent;
        this.hasher = hasher;
        }

    /**
     * Reset the referent
     *
     * @param referent the new referent
     * @param hasher   the hasher
     */
    protected void reset(T referent, Hasher<? super T> hasher)
        {
        this.referent = referent;
        this.hasher = hasher;
        }

    /**
     * @return the referent
     */
    public T get()
        {
        return referent;
        }

    @Override
    public int hashCode()
        {
        return hasher.hash(referent);
        }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object obj)
        {
        return obj == this || (obj instanceof Supplier<?> that && hasher.equals(get(), (T) that.get()));
        }
    }
