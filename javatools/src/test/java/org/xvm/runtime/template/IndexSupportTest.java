package org.xvm.runtime.template;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * A long-to-int narrowing test.
 */
public class IndexSupportTest {
    private static final int SIZE = 8;

    @Test
    public void anIndexInsideTheContainerIsNarrowed() {
        assertEquals(0, IndexSupport.checkedIndex(0, SIZE));
        assertEquals(4, IndexSupport.checkedIndex(4, SIZE));
        assertEquals(SIZE - 1, IndexSupport.checkedIndex(SIZE - 1, SIZE));
    }

    @Test
    public void anIndexOutsideTheContainerIsRejected() {
        assertEquals(-1, IndexSupport.checkedIndex(SIZE, SIZE));
        assertEquals(-1, IndexSupport.checkedIndex(-1, SIZE));
        assertEquals(-1, IndexSupport.checkedIndex(Long.MIN_VALUE, SIZE));
        assertEquals(-1, IndexSupport.checkedIndex(Long.MAX_VALUE, SIZE));
    }

    /**
     * Test the index far outside the 32 bits range.
     */
    @Test
    public void anIndexWhoseLowBitsAreInRangeIsStillRejected() {
        for (int slot = 0; slot < SIZE; slot++) {
            long lIndex = (1L << 32) + slot;

            assertEquals(slot, (int) lIndex,
                    "precondition: this index narrows to a valid slot");
            assertEquals(-1, IndexSupport.checkedIndex(lIndex, SIZE),
                    () -> "index " + lIndex + " is far outside a container of " + SIZE
                          + " elements and must be rejected, however it narrows");
        }
    }

    @Test
    public void theLowBitsTrapIsRejectedAtOtherSizes() {
        assertEquals(-1, IndexSupport.checkedIndex(4294967300L, 8));
        assertEquals(-1, IndexSupport.checkedIndex((1L << 32) + 999, 1000));
        assertEquals(-1, IndexSupport.checkedIndex((1L << 40) + 3, 16));
        assertEquals(3, IndexSupport.checkedIndex(3, 16));
    }

    @Test
    public void anEmptyContainerRejectsEveryIndex() {
        assertEquals(-1, IndexSupport.checkedIndex(0, 0));
        assertEquals(-1, IndexSupport.checkedIndex(1L << 32, 0));
    }
}
