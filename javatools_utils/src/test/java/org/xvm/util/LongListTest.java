package org.xvm.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LongList}.
 */
public class LongListTest {
    @Test
    public void emptyListHasNoValues() {
        LongList list = new LongList();
        assertTrue(list.isEmpty());
        assertEquals(0, list.size());
        assertSame(LongList.NO_LONGS, list.toArray());
    }

    @Test
    public void addKeepsEveryValue() {
        LongList list = new LongList();
        list.add(1L);
        list.add(2L);
        list.add(3L);

        assertEquals(3, list.size());
        assertArrayEquals(new long[] {1L, 2L, 3L}, list.toArray());
    }

    @Test
    public void addKeepsEveryValueWhenTheListGrows() {
        LongList list     = new LongList();
        long[]   expected = new long[100];
        for (int i = 0; i < expected.length; ++i) {
            expected[i] = 1L << (i % 64);
            list.add(expected[i]);
        }

        assertEquals(expected.length, list.size());
        assertArrayEquals(expected, list.toArray());
    }

    @Test
    public void addKeepsEveryValueBeyondTheInitialCapacity() {
        LongList list = new LongList(2);
        list.add(-1L);
        list.add(0L);
        list.add(Long.MAX_VALUE);

        assertArrayEquals(new long[] {-1L, 0L, Long.MAX_VALUE}, list.toArray());
    }
}
