package org.xvm.util;

/**
 * A reusable {@link HasherReference} intended for use in short-lived operations.
 *
 * <p>{@link TransientHasherReference}s are obtained via {@link #of} and given back via {@link #close}. Each thread
 * keeps a few spare references, which only that thread takes and gives back, so a reference is never held by two
 * threads at once: one thread's close() would reset the key of the other's map operation under it. A thread keeps up to
 * {@link #SPARES} of them, so that lookups nested that deep, such as a lookup in a hasher's own code, reuse theirs too.
 */
public class TransientHasherReference<T>
        extends HasherReference<T> implements AutoCloseable {
    /**
     * How many spare references a thread keeps.
     */
    static final int SPARES = 4;

    /**
     * Each thread's spare {@link TransientHasherReference}s, made when the thread first gives one back, so that a
     * thread that never does, such as a short-lived virtual thread, makes none.
     */
    private static final ThreadLocal<Spares> SPARES_OF_THREAD = new ThreadLocal<>();

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
     * Return a {@link TransientHasherReference}: one of the calling thread's spares, or a new one if it has none left.
     *
     * @param referent the referent
     * @return the reference
     */
    public static <T> TransientHasherReference<T> of(T referent, Hasher<? super T> hasher) {
        var spares = SPARES_OF_THREAD.get();
        var spare  = spares == null ? null : spares.take();
        if (spare == null) {
            return new TransientHasherReference<>(referent, hasher);
        }

        @SuppressWarnings("unchecked")
        TransientHasherReference<T> keyRef = (TransientHasherReference<T>) spare;

        keyRef.reset(referent, hasher);
        return keyRef;
    }

    /**
     * Recycle this ref, as one of the calling thread's spares unless it already keeps {@link #SPARES}.
     */
    @Override
    public void close() {
        reset(null, null);
        var spares = SPARES_OF_THREAD.get();
        if (spares == null) {
            spares = new Spares();
            SPARES_OF_THREAD.set(spares);
        }
        spares.give(this);
    }

    /**
     * One thread's spare references, a stack of at most {@link #SPARES}, which only that thread uses.
     */
    private static final class Spares {
        /**
         * Take the spare given back last, or null when there is none.
         */
        TransientHasherReference<?> take() {
            if (count == 0) {
                return null;
            }
            var spare = refs[--count];
            refs[count] = null;
            return spare;
        }

        /**
         * Keep {@code ref} as a spare, unless there are {@link #SPARES} already.
         */
        void give(TransientHasherReference<?> ref) {
            if (count < refs.length) {
                refs[count++] = ref;
            }
        }

        private final TransientHasherReference<?>[] refs = new TransientHasherReference<?>[SPARES];
        private int count;
    }
}
