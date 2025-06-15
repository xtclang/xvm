package org.xvm.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CooperativelyCloseableReference}
 */
public class CooperativelyCloseableReferenceTest
    {
    @Test
    void shouldClean()
        {
        AtomicLong cleaned = new AtomicLong();
        while (cleaned.get() == 0)
            {
            new CooperativelyCloseableReference<>(new Object(), cleaned::incrementAndGet);
            System.gc();
            }
        }

    @Test
    void shouldCleanIdempotently() throws Exception
        {
        AtomicLong cleaned = new AtomicLong();
        for (int i = 0; cleaned.get() == 0; ++i)
            {
            AtomicBoolean wasCleaned = new AtomicBoolean();
            CooperativelyCloseableReference<?> ref = new CooperativelyCloseableReference<>(new Object(), () -> {
            cleaned.incrementAndGet();
            if (wasCleaned.getAndSet(true))
                {
                throw new Error("already cleaned");
                }
            });

            if ((i & 1) == 0)
                {
                ref.close();
                }
            System.gc();
            }
        }

    @Test
    void shouldIgnoreExceptionsWhileCleaning()
        {
        AtomicLong cleaned = new AtomicLong();
        while (cleaned.get() == 0)
            {
            new CooperativelyCloseableReference<>(new Object(), () -> {
            cleaned.incrementAndGet();
            throw new NullPointerException();
            });
            System.gc();
            }
        }

    @Test
    void shouldIgnoreAndRestoreInterruptExceptionsWhileCleaning()
        {
        AtomicLong cleaned = new AtomicLong();
        while (cleaned.get() == 0)
            {
            new CooperativelyCloseableReference<>(new Object(), () -> {
            cleaned.incrementAndGet();
            throw new RuntimeException(new InterruptedException());
            });
            System.gc();
            }
        assertTrue(Thread.interrupted());
        }
    }
