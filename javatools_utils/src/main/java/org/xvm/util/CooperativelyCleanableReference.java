package org.xvm.util;

import java.io.InterruptedIOException;
import java.util.Objects;
import java.util.Set;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link WeakReference} coupled with an {@link AutoCloseable} "cleaning" function which is automatically invoked some
 * time after the referent becomes unreachable. This allows for {@code finalizer} like functionality without the need to
 * manually manage as {@link ReferenceQueue}.
 *
 * <p>For example if we wanted to track how many instances of a particular type of class are currently allocated:
 * <pre>{@code
 *
 * class CountableObject {
 *   private static final AtomicLong COUNT = new AtomicLong();
 *   private static final AutoCloseable DECR_COUNT = COUNT::decrementAndGet;
 *
 *   public CountableObject() {
 *       COUNT.incrementAndGet(); // track the allocation
 *       new CooperativeCleanableReference(this, DECR_COUNT); // track the eventual deallocation
 *   }
 *
 *   public static long getInstanceCount() {
 *       return COUNT.get();
 *   }
 * }
 * }</pre>
 *
 * <p>Note the cleaning is "cooperative" and performed on some subsequent creation of another
 * {@link CooperativelyCleanableReference} instance. If the cleanup operation is non-trivial then it should dispatch
 * itself into a thread-pool to avoid holding the cooperative thread for an extended period.
 *
 * @param <V> the referent type
 */
public class CooperativelyCleanableReference<V> extends WeakReference<V> {
    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(CooperativelyCleanableReference.class.getName());

    /**
     * Queues of unreachable references with unreachable referents.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final ReferenceQueue<Object>[] QUEUE = new ReferenceQueue[
            Integer.highestOneBit(Runtime.getRuntime().availableProcessors() << 1)];

    static {
        for (int i = 0; i < QUEUE.length; ++i) {
            QUEUE[i] = new ReferenceQueue<>();
    }
}

    /**
     * A set of refs which have yet to be cleaned, this ensures the refs don't get GC'd before their referent is cleaned.
     */
    private static final Set<CooperativelyCleanableReference<?>> KEEP_ALIVE = ConcurrentHashMap.newKeySet();

    /**
     * The cleanup function to run once the referent is unreachable.
     */
    private final AutoCloseable cleaner;

    /**
     * Create a {@link CooperativelyCleanableReference} for a given referent.
     *
     * @param referent the referent to manage
     * @param cleaner  the function to run once the referent is unreachable, this function must not reference the referent
     */
    public static <V> CooperativelyCleanableReference<V> create(V referent, AutoCloseable cleaner) {
        var ref = new CooperativelyCleanableReference<>(referent, cleaner);
        KEEP_ALIVE.add(ref);
        return ref;
    }

    private CooperativelyCleanableReference(V referent, AutoCloseable cleaner) {
        super(referent, clean(QUEUE[ThreadLocalRandom.current().nextInt(QUEUE.length)]));
        this.cleaner = Objects.requireNonNull(cleaner, "null cleaner");
    }

    /**
     * Perform cleanup for at least some unreachable referents in the specified queue.
     *
     * @return the queue
     */
    private static ReferenceQueue<Object> clean(ReferenceQueue<Object> queue) {
        long startMillis = System.currentTimeMillis();
        boolean restoreInterrupt = Thread.interrupted();
        try {
            CooperativelyCleanableReference<?> ref;
            int c = 0;
            do {
                ++c;
                ref = (CooperativelyCleanableReference<?>) queue.poll();
                try {
                    if (KEEP_ALIVE.remove(ref)) {
                        ref.cleaner.close();
                }
            } catch (Exception e) {
                    LOGGER.log(Level.INFO, "ignoring exception during cooperative cleanup", e);
                    Throwable t = e;
                    while (!restoreInterrupt && t != null) {
                        if (t instanceof InterruptedException || t instanceof InterruptedIOException) {
                            restoreInterrupt = true;
                    }
                        t = t.getCause();
                }
            }
        } while (ref != null && (c < 2 || System.currentTimeMillis() <= startMillis + 1));
    } finally {
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
        }
    }

        return queue;
}
}
