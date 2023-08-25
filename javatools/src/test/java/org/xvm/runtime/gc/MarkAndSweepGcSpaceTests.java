package org.xvm.runtime.gc;


import org.xvm.util.LongMuterator;
import org.xvm.util.ShallowSizeOf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.LongConsumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests of the {@link MarkAndSweepGcSpace}.
 *
 * @author mf
 */
public class MarkAndSweepGcSpaceTests
    {
    GcSpace makeSpace()
        {
        return makeSpace(l -> {}, Long.MAX_VALUE);
        }

    GcSpace makeSpace(LongConsumer cleared, long capacity)
        {
        return new MarkAndSweepGcSpace<>(LongArrayObjectManager.INSTANCE, cleared, capacity, capacity);
        }

    static class RootSet
        {
        final Set<Long> retained = new HashSet<>();

        public LongMuterator retained()
            {
            return new LongMuterator()
                {
                long lPrior = GcSpace.NULL;
                PrimitiveIterator.OfLong delegate = retained.stream().mapToLong(l -> l).iterator();

                @Override
                public void set(long value)
                    {
                    if (lPrior == GcSpace.NULL)
                        {
                        throw new IllegalStateException();
                        }

                    retained.remove(lPrior);
                    retained.add(value);
                    }

                @Override
                public long nextLong()
                    {
                    return lPrior = delegate.nextLong();
                    }

                @Override
                public boolean hasNext()
                    {
                    return delegate.hasNext();
                    }
                };
            }
        }

    @Test
    public void shouldAllocateAndGet()
        {
        GcSpace space = makeSpace();
        long p = space.allocate(0);
        assertTrue(space.isValid(p));
        }

    @Test
    public void shouldCollectUnreachables()
        {
        GcSpace space = makeSpace();
        RootSet root = new RootSet();
        space.addRoot(root::retained);
        long p1 = space.allocate(3);
        long p2 = space.allocate(3);
        root.retained.add(p1);
        root.retained.add(p2);
        assertTrue(space.isValid(p1));
        assertTrue(space.isValid(p2));
        assertNotEquals(p1, p2);

        // force a gc and verify that we didn't lose anything
        space.gc();
        assertTrue(space.isValid(p1));
        assertTrue(space.isValid(p2));
        assertNotEquals(p1, p2);

        // remove an object from the root; gc, and verify it has been removed from the space
        root.retained.remove(p2);

        space.gc();
        assertTrue(space.isValid(p1));
        assertFalse(space.isValid(p2));
        }

    @Test
    public void shouldNotCollectDeepReachables()
        {
        RootSet root = new RootSet();
        GcSpace space = makeSpace();
        space.addRoot(root::retained);
        long p1 = space.allocate(3);
        long p2 = space.allocate(3);
        long p3 = space.allocate(3);
        long p4 = space.allocate(3);
        root.retained.add(p1);
        assertTrue(space.isValid(p1));
        assertTrue(space.isValid(p2));
        assertTrue(space.isValid(p3));
        assertTrue(space.isValid(p4));

        space.setField(p1, 0, p2);
        space.setField(p2, 2, p3);
        space.setField(p4, 0, p4); // cycle

        // force a gc and verify that we didn't lose anything which was reachable
        space.gc();
        assertTrue(space.isValid(p1));
        assertTrue(space.isValid(p2));
        assertTrue(space.isValid(p3));
        assertNotEquals(p1, p2);

        // verify that
        try
            {
            space.isValid(p4);
            }
        catch (SegFault e)
            {
            // expected
            }
        }

    @Test
    public void shouldAutoCollect()
        {
        GcSpace space = makeSpace();

        RootSet root = new RootSet();
        space.addRoot(root::retained);
        long p1 = space.allocate(3);
        long p2 = space.allocate(3);
        long p3 = space.allocate(3);
        root.retained.add(p1);

        space.setField(p1, 0, p2);
        space.setField(p2, 2, p3);

        long cb = space.getByteCount();
        // add objects until we auto-gc
        long maxCb = cb;
        long lastCb;
        do
            {
            lastCb = space.getByteCount();
            space.allocate(1);
            maxCb = Math.max(maxCb, space.getByteCount());
            }
        while (space.getByteCount() > lastCb);

        // verify we did see significant growth
        assertTrue(maxCb > cb * 4);

        // verify we've shrunk back to near our reachable set size
        assertTrue(space.getByteCount() < cb * 2);

        // verify we retained the reachable objects
        assertTrue(space.isValid(p1));
        assertTrue(space.isValid(p2));
        assertTrue(space.isValid(p3));
        }

    @Test
    public void shouldOOMEOnHardLimit()
        {
        long cbLimit = 1024 * 1024 * 128;
        GcSpace space = makeSpace(l ->
            {
            }, cbLimit);
        RootSet root = new RootSet();
        space.addRoot(root::retained);
        long p1 = space.allocate(3);
        root.retained.add(p1);

        long pLast = p1;
        try
            {
            for (int i = 0; i < 2_000_000; ++i)
                {
                long p = space.allocate(512);
                space.setField(pLast, 0, p);
                pLast = p;
                }

            fail();
            }
        catch (OutOfMemoryError e)
            {
            assertTrue(space.getByteCount() >= cbLimit);
            assertTrue(space.getByteCount() <= cbLimit + ShallowSizeOf.arrayOf(long.class, 512));
            }
        }

    @Test
    public void shouldClearWeakRefsToUnreachables()
        {
        List<Long> cleared = new ArrayList<>();
        GcSpace space = makeSpace(cleared::add, Long.MAX_VALUE);
        RootSet root = new RootSet();
        space.addRoot(root::retained);
        long p1 = space.allocate(0);
        long p2 = space.allocate(1);
        long wp1 = space.allocateWeak(2);
        space.setField(wp1, 0, p1);
        space.setField(wp1, 1, space.allocate(0)); // notifier
        space.setField(p2, 0, p1);
        root.retained.add(wp1);
        root.retained.add(p2);

        space.gc();

        // p2 keeps p1 alive; verify that p1 was not collected, and that wp1 was not cleared
        assertTrue(space.isValid(p1));
        assertEquals(p1, space.getField(wp1, 0));

        // verify there have been no notifications
        assertTrue(cleared.isEmpty());

        // remove the strong ref to p1 and verify that it gets gc'd and that wp1 gets cleared
        space.setField(p2, 0, GcSpace.NULL);
        space.gc();

        assertEquals(GcSpace.NULL, space.getField(wp1, 0));
        assertFalse(space.isValid(p1));

        // verify that cleared was notified
        assertTrue(cleared.remove(wp1));
        }
    }
