package org.xvm.util;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A pooled {@link HasherReference} intended for use in short-lived operations.
 * <p>
 * {@link TransientHasherReference} are obtained from the pool via {@link #of} and returned to the pool via
 * {@link #close}.
 *
 * @author falcom
 */
class TransientHasherReference<K>
        extends HasherReference<K> implements AutoCloseable
    {
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
    protected TransientHasherReference(K referent, Hasher<? super K> hasher)
        {
        super(referent, hasher);
        }

    /**
     * Return a {@link TransientHasherReference} which is not-suitable for storage, but can be used for lookups.
     *
     * @param key the referent
     * @return the reference
     */
    public static <K> TransientHasherReference<K> of(K key, Hasher<? super K> hasher)
        {
        int slot = Thread.currentThread().hashCode() & (HEADS.length() - 1);
        TransientHasherReference<?> ref = HEADS.get(slot);
        while (ref != null && !HEADS.compareAndSet(slot, ref, ref.next))
            {
            ref = HEADS.get(slot);
            }

        if (ref == null)
            {
            return new TransientHasherReference<>(key, hasher);
            }

        @SuppressWarnings("unchecked")
        TransientHasherReference<K> keyRef = (TransientHasherReference<K>) ref;

        keyRef.next = null;
        keyRef.set(key, hasher);
        return keyRef;
        }

    /**
     * Recycle this ref.
     */
    @Override
    public void close()
        {
        set(null, null);
        int slot = Thread.currentThread().hashCode() & (HEADS.length() - 1);
        next = HEADS.get(slot);
        while (!HEADS.compareAndSet(slot, next, this))
            {
            next = HEADS.get(slot);
            }
        }
    }
