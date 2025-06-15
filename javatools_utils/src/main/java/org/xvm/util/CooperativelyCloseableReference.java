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
 * <p>The {@link #close} and corresponding cleaning is "cooperative" and performed on some subsequent creation of another
 * {@link CooperativelyCloseableReference} instance. The reference's {@link #close} operation is idempotent and will
 * only invoke the cleaner at most once.
 *
 * @param <V> the referent type
 * @author falcom
 */
public class CooperativelyCloseableReference<V> extends WeakReference<V> implements AutoCloseable
    {
    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(CooperativelyCloseableReference.class.getName());

    /**
     * Queues of unreachable references with unreachable referents..
     */
    @SuppressWarnings("unchecked")
    private static final ReferenceQueue<Object>[] QUEUE = new ReferenceQueue[
            Integer.highestOneBit(Runtime.getRuntime().availableProcessors() << 1)];

    static
        {
        for (int i = 0; i < QUEUE.length; ++i)
            {
            QUEUE[i] = new ReferenceQueue<>();
            }
        }

    /**
     * A set of refs which have yet to be cleaned, this ensures the refs don't get GC'd before their referent is cleaned.
     */
    private static final Set<CooperativelyCloseableReference<?>> KEEPALIVE = ConcurrentHashMap.newKeySet();

    /**
     * The cleanup function to run once the referent is unreachable.
     */
    private final AutoCloseable cleaner;

    /**
     * Construct a {@link CooperativelyCloseableReference} for a given referent.
     *
     * @param referent the referent to manage
     * @param cleaner  the function to run once the referent is unreachable, this function must not reference the referent
     */
    public CooperativelyCloseableReference(V referent, AutoCloseable cleaner)
        {
        super(referent, clean(QUEUE[ThreadLocalRandom.current().nextInt(QUEUE.length)]));
        this.cleaner = Objects.requireNonNull(cleaner, "null cleaner");
        KEEPALIVE.add(this);
        }

    /**
     * {@link #clear Clears} the reference and runs this references cleaning function.
     *
     * <p>Called automatically at some point after the referent becomes unreachable.
     */
    @Override
    public void close() throws Exception
        {
        if (KEEPALIVE.remove(this))
            {
            clear();
            cleaner.close();
            }
        }

    /**
     * Perform cleanup for at least some unreachable referents in the specified queue.
     *
     * @return the queue
     */
    private static ReferenceQueue<Object> clean(ReferenceQueue<Object> queue)
        {
        long startMillis = System.currentTimeMillis();
        boolean restoreInterrupt = Thread.interrupted();
        try
            {
            CooperativelyCloseableReference<?> ref;
            int c = 0;
            do
                {
                ++c;
                ref = (CooperativelyCloseableReference<?>) queue.poll();
                try
                    {
                    ref.close();
                    }
                catch (Exception e)
                    {
                    LOGGER.log(Level.INFO, "ignoring exception during cooperative cleanup", e);
                    Throwable t = e;
                    while (!restoreInterrupt && t != null)
                        {
                        if (t instanceof InterruptedException || t instanceof InterruptedIOException)
                            {
                            restoreInterrupt = true;
                            }
                        t = t.getCause();
                        }
                    }
                }
            while (ref != null && (c < 2 || System.currentTimeMillis() <= startMillis + 1));
            }
        finally
            {
            if (restoreInterrupt)
                {
                Thread.currentThread().interrupt();
                }
            }

        return queue;
        }
    }
