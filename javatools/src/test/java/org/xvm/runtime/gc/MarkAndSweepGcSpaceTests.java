package org.xvm.runtime.gc;

import org.junit.Test;

import javax.swing.text.LayoutQueue;
import java.util.HashSet;
import java.util.PrimitiveIterator;
import java.util.Set;

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
        return new MarkAndSweepGcSpace<>(LongArrayStorage.INSTANCE);
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
        root.collectables.add(p1);
        assertNotNull(space.get(p1));
        assertNotNull(space.get(p2));
        assertNotEquals(p1, p2);

        LongArrayStorage.INSTANCE.setField(space.get(p1), 0, p2);

        // force a gc and verify that we didn't lose anything
        space.gc();
        assertNotNull(space.get(p1));
        assertNotNull(space.get(p2));
        assertNotEquals(p1, p2);
        }
    }
