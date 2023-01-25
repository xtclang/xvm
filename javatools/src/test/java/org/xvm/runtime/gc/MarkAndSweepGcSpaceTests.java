package org.xvm.runtime.gc;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.xvm.util.ShallowSizeOf;

import javax.swing.text.LayoutQueue;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests of the {@link MarkAndSweepGcSpace}
 *
 * @author mf
 */
public class MarkAndSweepGcSpaceTests
    {
    static GcSpace<long[]> makeSpace()
        {
        return new MarkAndSweepGcSpace<>(LongArrayStorage.INSTANCE, w -> {});
        }

    static class RootSet
            implements GcSpace.Root
        {
        final Set<Long> collectables = new HashSet<>();

        @Override
        public PrimitiveIterator.OfLong collectables()
            {
            return collectables.stream().mapToLong(l -> l).iterator();
            }
        }

    @Test
    public void shouldAllocateAndGet()
        {
        GcSpace<long[]> space = makeSpace();
        long p = space.allocate(() -> LongArrayStorage.INSTANCE.allocate(0));
        assertNotNull(space.get(p));
        }

    @Test
    public void shouldCollectUnreachables()
        {
        RootSet root = new RootSet();
        GcSpace<long[]> space = makeSpace();
        space.add(root);
        long p1 = space.allocate(() -> LongArrayStorage.INSTANCE.allocate(3));
        long p2 = space.allocate(() -> LongArrayStorage.INSTANCE.allocate(3));
        root.collectables.add(p1);
        root.collectables.add(p2);
        assertNotNull(space.get(p1));
        assertNotNull(space.get(p2));
        assertNotEquals(p1, p2);

        // force a gc and verify that we didn't lose anything
        space.gc();
        assertNotNull(space.get(p1));
        assertNotNull(space.get(p2));
        assertNotEquals(p1, p2);

        // remove an object from the root; gc, and verify it has been removed from the space
        root.collectables.remove(p2);

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
        GcSpace<long[]> space = makeSpace();
        space.add(root);
        long p1 = space.allocate(() -> LongArrayStorage.INSTANCE.allocate(3));
        long p2 = space.allocate(() -> LongArrayStorage.INSTANCE.allocate(3));
        long p3 = space.allocate(() -> LongArrayStorage.INSTANCE.allocate(3));
        long p4 = space.allocate(() -> LongArrayStorage.INSTANCE.allocate(3));
        root.collectables.add(p1);
        assertNotNull(space.get(p1));
        assertNotNull(space.get(p2));
        assertNotNull(space.get(p3));
        assertNotNull(space.get(p4));

        LongArrayStorage.INSTANCE.setField(space.get(p1), 0, p2);
        LongArrayStorage.INSTANCE.setField(space.get(p2), 2, p3);
        LongArrayStorage.INSTANCE.setField(space.get(p4), 0, p4); // cycle

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
        RootSet root = new RootSet();
        GcSpace<long[]> space = makeSpace();
        space.add(root);
        long p1 = space.allocate(() -> LongArrayStorage.INSTANCE.allocate(3));
        long p2 = space.allocate(() -> LongArrayStorage.INSTANCE.allocate(3));
        long p3 = space.allocate(() -> LongArrayStorage.INSTANCE.allocate(3));
        root.collectables.add(p1);

        LongArrayStorage.INSTANCE.setField(space.get(p1), 0, p2);
        LongArrayStorage.INSTANCE.setField(space.get(p2), 2, p3);

        long cb = space.getByteCount();
        // add objects until we auto-gc
        long maxCb = cb;
        long lastCb;
        do
            {
            lastCb = space.getByteCount();
            space.allocate(() -> LongArrayStorage.INSTANCE.allocate(1));
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
        RootSet root = new RootSet();
        long cbLimit = 1024*2048;
        GcSpace<long[]> space = new MarkAndSweepGcSpace<>(LongArrayStorage.INSTANCE, w -> {}, 1024*1024, cbLimit);
        space.add(root);
        long p1 = space.allocate(() -> LongArrayStorage.INSTANCE.allocate(3));
        root.collectables.add(p1);

        long pLast = p1;
        try
            {
            for (int i = 0; i < 2_000_000; ++i)
                {
                long p = space.allocate(() -> LongArrayStorage.INSTANCE.allocate(512));
                LongArrayStorage.INSTANCE.setField(space.get(pLast), 0, p);
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
        LongArrayStorage storage = LongArrayStorage.INSTANCE;
        List<Long> cleared = new ArrayList<>();
        GcSpace<long[]> space = new MarkAndSweepGcSpace<>(LongArrayStorage.INSTANCE, cleared::add);
        RootSet root = new RootSet();
        space.add(root);
        long p1 = space.allocate(() -> storage.allocate(0));
        long p2 = space.allocate(() -> storage.allocate(1));
        long wp1 = space.allocate(() -> storage.allocate(2), true);
        storage.setField(space.get(wp1), 0, p1);
        storage.setField(space.get(wp1), 1, space.allocate(() -> storage.allocate(0))); // notifier
        storage.setField(space.get(p2), 0, p1);
        root.collectables.add(wp1);
        root.collectables.add(p2);

        space.gc();

        // p2 keeps p1 alive; verify that p1 was not collected, and that wp1 was not cleared
        assertNotNull(space.get(p1));
        assertEquals(p1, storage.getField(space.get(wp1), 0));

        // verify there have been no notifications
        assertTrue(cleared.isEmpty());

        // remove the strong ref to p1 and verify that it gets gc'd and that wp1 gets cleared
        storage.setField(space.get(p2), 0, GcSpace.NULL);
        space.gc();

        assertEquals(GcSpace.NULL, storage.getField(space.get(wp1), 0));
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
