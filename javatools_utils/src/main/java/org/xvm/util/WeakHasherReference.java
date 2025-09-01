package org.xvm.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.function.Supplier;

/**
 * A {@link WeakReference} which uses {@link Hasher} for its equality.
 */
public class WeakHasherReference<T> extends WeakReference<T> implements Supplier<T> {
    /**
     * The hasher being used to compare referents.
     */
    private final Hasher<? super T> hasher;

    /**
     * A cached hash of the referent allowing WHR to still function as a hash key even after being cleared.
     */
    private final int hash;

    /**
     * Construct a {@link WeakHasherReference}.
     *
     * @param referent the object to reference
     * @param hasher   the hasher to use in comparing
     * @param queue    the queue to add cleared references to
     */
    public WeakHasherReference(T referent, Hasher<? super T> hasher, ReferenceQueue<? super T> queue) {
        super(referent, queue);
        this.hasher = hasher;
        this.hash = hasher.hash(referent);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof Supplier<?> that && hasher.equals(get(), (T) that.get()));
    }
}
