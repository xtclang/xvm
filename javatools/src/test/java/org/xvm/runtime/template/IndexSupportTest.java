package org.xvm.runtime.template;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * An index arrives as a {@code long} and the storage it addresses is indexed by {@code int}, so the
 * narrowing has to happen somewhere. {@link IndexSupport#checkedIndex} is where, and it range-checks
 * the value it was given rather than the value it produced.
 *
 * <p>The order matters. {@code extractArrayValue} used to narrow first and check afterwards:</p>
 *
 * <pre>
 * int nIx = (int) lIndex;
 * return nIx &lt; 0 || nIx &gt;= ach.length ? raise(...) : ach[nIx];
 * </pre>
 *
 * <p>{@code (int)} keeps only the low 32 bits, so an index of 2<sup>32</sup>&nbsp;+&nbsp;4 became 4,
 * satisfied the guard, and read element 4. The cases below are that arithmetic: every index whose
 * low 32 bits land inside the container must still be rejected.</p>
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
     * The defect this exists for: an index far outside the container whose LOW 32 BITS are inside
     * it. Each of these narrows to a valid slot and must still be rejected.
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

    /** The same trap one container-size up, so the test is not tied to a size of 8. */
    @Test
    public void theLowBitsTrapIsRejectedAtOtherSizes() {
        assertEquals(-1, IndexSupport.checkedIndex(4294967300L, 8));
        assertEquals(-1, IndexSupport.checkedIndex((1L << 32) + 999, 1000));
        assertEquals(-1, IndexSupport.checkedIndex((1L << 40) + 3, 16));
        assertEquals(3, IndexSupport.checkedIndex(3, 16));
    }

    /** An empty container has no valid index at all. */
    @Test
    public void anEmptyContainerRejectsEveryIndex() {
        assertEquals(-1, IndexSupport.checkedIndex(0, 0));
        assertEquals(-1, IndexSupport.checkedIndex(1L << 32, 0));
    }
}
