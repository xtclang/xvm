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
     * The hasher being used to compare refrents.
     */
    private Hasher<? super T> hasher;

    /**
     * The referent
     */
    private T referent;

    protected HasherReference(T referant, Hasher<? super T> hasher)
        {
        this.hasher = hasher;
        set(referant);
        }

    /**
     * Reset the referent
     *
     * @param referent the new referent
     */
    public void set(T referent)
        {
        this.referent = referent;
        }

    /**
     * Reset the referent
     *
     * @param referent the new referent
     * @param hasher   the hasher
     */
    public void set(T referent, Hasher<? super T> hasher)
        {
        this.referent = referent;
        this.hasher = hasher;
        }

    /**
     * @return the referant
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
        return obj == this || (obj instanceof Supplier<?> that && hasher.equals(referent, (T) that.get()));
        }
    }
