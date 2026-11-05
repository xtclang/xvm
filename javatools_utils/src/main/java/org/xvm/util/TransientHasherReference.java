package org.xvm.util;

/**
 * A reusable {@link HasherReference} intended for use in short-lived operations.
 *
 * <p>{@link TransientHasherReference}s are obtained via {@link #of} and given back via {@link #close}. Each thread
 * keeps one spare reference, which only that thread takes and gives back, so a reference is never held by two threads
 * at once: one thread's close() would reset the key of the other's map operation under it.
 */
public class TransientHasherReference<T>
        extends HasherReference<T> implements AutoCloseable {
    /**
     * Each thread's spare {@link TransientHasherReference}, if it has one.
     */
    private static final ThreadLocal<TransientHasherReference<?>> SPARE = new ThreadLocal<>();

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
     * Return a {@link TransientHasherReference}: the calling thread's spare one, or a new one if it is in use.
     *
     * @param referent the referent
     * @return the reference
     */
    public static <T> TransientHasherReference<T> of(T referent, Hasher<? super T> hasher) {
        TransientHasherReference<?> spare = SPARE.get();
        if (spare == null) {
            return new TransientHasherReference<>(referent, hasher);
        }
        SPARE.set(null);

        @SuppressWarnings("unchecked")
        TransientHasherReference<T> keyRef = (TransientHasherReference<T>) spare;

        keyRef.reset(referent, hasher);
        return keyRef;
    }

    /**
     * Recycle this ref, as the calling thread's spare one unless it already has one.
     */
    @Override
    public void close() {
        reset(null, null);
        if (SPARE.get() == null) {
            SPARE.set(this);
        }
    }
}
