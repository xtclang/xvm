package org.xvm.runtime.gc;

import org.junit.Test;
import org.xvm.util.ShallowSizeOf;

import java.util.*;
import java.util.function.LongConsumer;

import static org.junit.Assert.*;

/**
 * Tests of the {@link MarkAndSweepGcSpace}.
 *
 * @author mf
 */
public class MarkAndSweepGcSpaceTests<O>
    {
    GcSpace<O> makeSpace()
        {
        return makeSpace(l -> {}, Long.MAX_VALUE);
        }

    @SuppressWarnings("unchecked")
    GcSpace<O> makeSpace(LongConsumer cleared, long capacity)
        {
        return (GcSpace<O>) new MarkAndSweepGcSpace<>(LongArrayObjectManager.INSTANCE, cleared, capacity, capacity);
        }

    static class RootSet
        {
        final Set<Long> retained = new HashSet<>();

        public PrimitiveIterator.OfLong retained()
            {
            return retained.stream().mapToLong(l -> l).iterator();
            }
        }

    @Test
    public void shouldAllocateAndGet()
        {
        GcSpace<O> space = makeSpace();
        long p = space.allocate(0);
        assertNotNull(space.get(p));
        }

    @Test
    public void shouldCollectUnreachables()
        {
        GcSpace<O> space = makeSpace();
        RootSet root = new RootSet();
        space.addRoot(root::retained);
        long p1 = space.allocate(3);
        long p2 = space.allocate(3);
        root.retained.add(p1);
        root.retained.add(p2);
        assertNotNull(space.get(p1));
        assertNotNull(space.get(p2));
        assertNotEquals(p1, p2);

        // force a gc and verify that we didn't lose anything
        space.gc();
        assertNotNull(space.get(p1));
        assertNotNull(space.get(p2));
        assertNotEquals(p1, p2);

        // remove an object from the root; gc, and verify it has been removed from the space
        root.retained.remove(p2);

        space.gc();
        assertNotNull(space.get(p1));
        try
            {
            space.get(p2);
            fail();
            }
        catch (SegFault e)
            {
            // expected
            }
        }

    @Test
    public void shouldNotCollectDeepReachables()
        {
        RootSet root = new RootSet();
        GcSpace<O> space = makeSpace();
        FieldAccessor<O> accessor = space.accessor();
        space.addRoot(root::retained);
        long p1 = space.allocate(3);
        long p2 = space.allocate(3);
        long p3 = space.allocate(3);
        long p4 = space.allocate(3);
        root.retained.add(p1);
        assertNotNull(space.get(p1));
        assertNotNull(space.get(p2));
        assertNotNull(space.get(p3));
        assertNotNull(space.get(p4));

        accessor.setField(space.get(p1), 0, p2);
        accessor.setField(space.get(p2), 2, p3);
        accessor.setField(space.get(p4), 0, p4); // cycle

        // force a gc and verify that we didn't lose anything which was reachable
        space.gc();
        assertNotNull(space.get(p1));
        assertNotNull(space.get(p2));
        assertNotNull(space.get(p3));
        assertNotEquals(p1, p2);

        // verify that
        try
            {
            space.get(p4);
            }
        catch (SegFault e)
            {
            // expected
            }
        }

    @Test
    public void shouldAutoCollect()
        {
        GcSpace<O> space = makeSpace();
        FieldAccessor<O> accessor = space.accessor();
        RootSet root = new RootSet();
        space.addRoot(root::retained);
        long p1 = space.allocate(3);
        long p2 = space.allocate(3);
        long p3 = space.allocate(3);
        root.retained.add(p1);

        accessor.setField(space.get(p1), 0, p2);
        accessor.setField(space.get(p2), 2, p3);

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

        // verify we did see signficant growth
        assertTrue(maxCb > cb * 4);

        // verify we've shrunk back to near our reachable set size
        assertTrue(space.getByteCount() < cb * 2);

        // verify we retained the reachable objects
        assertNotNull(space.get(p1));
        assertNotNull(space.get(p2));
        assertNotNull(space.get(p3));
        }

    @Test
    public void shouldOOMEOnHardLimit()
        {
        long cbLimit = 1024*2048;
        GcSpace<O> space = makeSpace(l -> {}, cbLimit);
        FieldAccessor<O> accessor = space.accessor();
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
                accessor.setField(space.get(pLast), 0, p);
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
        GcSpace<O> space = makeSpace(cleared::add, Long.MAX_VALUE);
        FieldAccessor<O> accessor = space.accessor();
        RootSet root = new RootSet();
        space.addRoot(root::retained);
        long p1 = space.allocate(0);
        long p2 = space.allocate(1);
        long wp1 = space.allocate(2, true);
        accessor.setField(space.get(wp1), 0, p1);
        accessor.setField(space.get(wp1), 1, space.allocate(0)); // notifier
        accessor.setField(space.get(p2), 0, p1);
        root.retained.add(wp1);
        root.retained.add(p2);

        space.gc();

        // p2 keeps p1 alive; verify that p1 was not collected, and that wp1 was not cleared
        assertNotNull(space.get(p1));
        assertEquals(p1, accessor.getField(space.get(wp1), 0));

        // verify there have been no notifications
        assertTrue(cleared.isEmpty());

        // remove the strong ref to p1 and verify that it gets gc'd and that wp1 gets cleared
        accessor.setField(space.get(p2), 0, GcSpace.NULL);
        space.gc();

        assertEquals(GcSpace.NULL, accessor.getField(space.get(wp1), 0));
        try
            {
            space.get(p1);
            fail();
            }
        catch (SegFault e)
            {
            // expected
            }

        // verify that cleared was notified
        assertTrue(cleared.remove(wp1));
        }
    }
