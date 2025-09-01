package org.xvm.util;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A pooled {@link HasherReference} intended for use in short-lived operations.
 * <p>
 * {@link TransientHasherReference} are obtained from the pool via {@link #of} and returned to the pool via
 * {@link #close}.
 */
class TransientHasherReference<T>
        extends HasherReference<T> implements AutoCloseable {
    /**
     * Stacks of {@link TransientHasherReference}s.
     */
    private static final AtomicReferenceArray<TransientHasherReference<?>> HEADS =
            new AtomicReferenceArray<>(Integer.highestOneBit(Runtime.getRuntime().availableProcessors()) << 1);

    /**
     * The next in our stack
     */
    private TransientHasherReference<?> next;

    /**
     * Construct a {@link TransientHasherReference}.
     *
     * @param referent the referent
     * @param hasher   the hasher
     */
    protected TransientHasherReference(T referent, Hasher<? super T> hasher) {
        super(referent, hasher);
    }

    /**
     * Return a {@link TransientHasherReference}.
     *
     * @param referent the referent
     * @return the reference
     */
    public static <T> TransientHasherReference<T> of(T referent, Hasher<? super T> hasher) {
        int slot = Thread.currentThread().hashCode() & (HEADS.length() - 1);
        TransientHasherReference<?> ref = HEADS.get(slot);
        while (ref != null && !HEADS.compareAndSet(slot, ref, ref.next)) {
            ref = HEADS.get(slot);
        }

        if (ref == null) {
            return new TransientHasherReference<>(referent, hasher);
        }

        @SuppressWarnings("unchecked")
        TransientHasherReference<T> keyRef = (TransientHasherReference<T>) ref;

        keyRef.next = null;
        keyRef.reset(referent, hasher);
        return keyRef;
    }

    /**
     * Recycle this ref.
     */
    @Override
    public void close() {
        reset(null, null);
        int slot = Thread.currentThread().hashCode() & (HEADS.length() - 1);
        next = HEADS.get(slot);
        while (!HEADS.compareAndSet(slot, next, this)) {
            next = HEADS.get(slot);
        }
    }
}
