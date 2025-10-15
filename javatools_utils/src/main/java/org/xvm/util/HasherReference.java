package org.xvm.util;

import java.util.function.Supplier;

/**
 * A {@code Reference} like class which uses {@link Hasher} for equality.
 */
public class HasherReference<T> implements Supplier<T> {
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
    public HasherReference(final T referent, final Hasher<? super T> hasher) {
        reset(referent, hasher);
    }

    /**
     * Reset the referent
     *
     * @param referent the new referent
     * @param hasher   the hasher
     */
    protected final void reset(final T referent, final Hasher<? super T> hasher) {
        this.referent = referent;
        this.hasher = hasher;
    }

    /**
     * @return the referent
     */
    public T get() {
        return referent;
    }

    @Override
    public int hashCode() {
        return hasher.hash(referent);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(final Object obj) {
        return obj == this || (obj instanceof final Supplier<?> that && hasher.equals(get(), (T) that.get()));
    }
}
