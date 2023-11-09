package org.xvm.runtime;


import org.junit.jupiter.api.Test;

import org.xvm.runtime.template.numbers.LongLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for various native number implementations.
 */
public class TestNumber
    {
    // ---- add ------------------------------------------------------------------------------------

    static final long MAX64 = Long.MAX_VALUE;
    static final long MIN64 = Long.MIN_VALUE;

    static final LongLong MAX128L = new LongLong(-1, 0);
    static final LongLong MIN128H = new LongLong(0, 1);  // MAX128L + 1
    static final LongLong MAX128 = new LongLong(-1, MAX64);
    static final LongLong MIN128 = new LongLong(0, MIN64);

    static final long MAX63U = Long.MAX_VALUE;
    static final long MIN64H = Long.MIN_VALUE; // MAX63U + 1
    static final long MAX64U = -1L;

    static final long MAX32 = Integer.MAX_VALUE;
    static final long MIN32 = Integer.MIN_VALUE;

    static final long MAX32U = 0xFFFF_FFFFL;
    static final long MIN32H = 0x8000_0000L;
    static final long MAX31U = 0x7FFF_FFFFL;

    @Test
    public void test128Signed()
        {
        // add
        assertEquals(new LongLong(1+2), new LongLong(1).add(new LongLong(2)), "1+2");
        assertEquals(new LongLong(-1+2), new LongLong(-1).add(new LongLong(2)), "-1+2");
        assertEquals(new LongLong(-1-2), new LongLong(-1).add(new LongLong(-2)), "-1-2");
        assertEquals(new LongLong(MIN64, 0), new LongLong(MAX64).add(new LongLong(1)), "MAX64+1");
        assertEquals(MIN128H, MAX128L.add(new LongLong(1)),"MAX64LOW+1");
        assertEquals(MAX128L, MIN128H.add(new LongLong(-1)), "MIN128HIGH-1");
        assertEquals(new LongLong(-1), MAX128.add(MIN128), "MAX+MIN");

        assertEquals(LongLong.OVERFLOW, MAX128.add(new LongLong(1)), "MAX+1");
        assertEquals(LongLong.OVERFLOW, MAX128.add(MAX128), "MAX+MAX");
        assertEquals(LongLong.OVERFLOW, MIN128.add(new LongLong(-1)), "MIN-1");
        assertEquals(LongLong.OVERFLOW, MIN128.add(MIN128), "MIN+MIN");

        // sub
        assertEquals(new LongLong(2-1), new LongLong(2).sub(new LongLong(1)), "2-1");
        assertEquals(new LongLong(-1+2), new LongLong(-1).sub(new LongLong(-2)), "-1+2");
        assertEquals(new LongLong(-1-2), new LongLong(-1).sub(new LongLong(2)), "-1-2");
        assertEquals(new LongLong(MIN64, 0), new LongLong(MAX64).sub(new LongLong(-1)), "MAX64+1");
        assertEquals(MIN128H, MAX128L.sub(new LongLong(-1)), "MAX128LOW+1");
        assertEquals(MAX128L, MIN128H.sub(new LongLong(1)), "MIN128HIGH-1");

        assertEquals(LongLong.OVERFLOW, MAX128.sub(new LongLong(-1)), "MAX+1");
        assertEquals(LongLong.OVERFLOW, MIN128.sub(new LongLong(1)), "MIN-1");
        }

    @Test
    public void test128Unsigned()
        {
        // TODO
        }

    @Test
    public void test64Signed()
        {
        final int SHIFT = 0;

        // add
        assertEquals(-1L + 2L, longAdd(-1L, 2L, SHIFT), "-1+2");
        assertEquals(-1L - 2L, longAdd(-1L, -2L, SHIFT), "-1-2");
        assertEquals(-1L, longAdd(MAX64, MIN64, SHIFT), "MAX+MIN");
        assertEquals(MAX64 - 1, longAdd(MAX64, -1L, SHIFT), "MAX-1");
        assertEquals(MIN64 + 1, longAdd(MIN64, +1L, SHIFT), "MIN+1");
        try
            {
            longAdd(MAX64, 1, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longAdd(MIN64, -1, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}

        // sub
        assertEquals(2L - 1L, longSub(2L, 1L, SHIFT), "2-1");
        assertEquals(1L - 2L, longSub(1L, 2L, SHIFT), "1-2");
        assertEquals(-1L + 2L, longSub(-1L, -2L, SHIFT), "-1+2");
        assertEquals(0L, longSub(MAX64, MAX64, SHIFT), "MAX-MAX");
        assertEquals(MAX64 - 1, longSub(MAX64, 1L, SHIFT), "MAX-1");
        assertEquals(MIN64 + 1, longSub(MIN64, -1L, SHIFT), "MIN+1");
        try
            {
            longSub(MIN64, 1, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longSub(MAX64, -1, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}
        }

    @Test
    public void test64Unsigned()
        {
        final int SHIFT = 0;

        // add
        assertEquals(1L-2L, longUnsignedAdd(1L, -2L, SHIFT), "1-2");
        assertEquals(MIN64H, longUnsignedAdd(MAX63U, 1L, SHIFT), "MAX63+1");
        assertEquals(MAX64U, longUnsignedAdd(MAX63U, MIN64H, SHIFT), "MAX");
        try
            {
            longUnsignedAdd(MAX64U, 1, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longUnsignedAdd(MAX64U, MAX64U, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}

        // sub
        assertEquals(2L - 1L, longUnsignedSub(2L, 1L, SHIFT), "2-1");
        assertEquals(MAX63U, longUnsignedSub(MIN64H, 1L, SHIFT), "MIN64H-1");
        assertEquals(MAX63U, longUnsignedSub(MAX64U, MIN64H, SHIFT), "MAX-MIN");
        try
            {
            longUnsignedSub(1L, 2L, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longUnsignedSub(1L, MAX64U, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}
        }

    @Test
    public void test32Signed()
        {
        final int SHIFT = 32;

        // add
        assertEquals(1L + 2L, longAdd(1L, 2L, SHIFT), "1+2");
        assertEquals(-1L + 2L, longAdd(-1L, 2L, SHIFT), "-1+2");
        assertEquals(-1 - 2, longAdd(-1L, -2L, SHIFT), "-1-2");
        assertEquals(-1L, longAdd(MAX32, MIN32, SHIFT), "MAX+MIN");
        assertEquals(MAX32 - 1, longAdd(MAX32, -1L, SHIFT), "MAX-1");
        assertEquals(MIN32 + 1, longAdd(MIN32, +1L, SHIFT), "MIN+1");
        try
            {
            longAdd(MAX32, 1, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longAdd(MIN32, -1, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}

        // sub
        assertEquals(2L - 1L, longSub(2L, 1L, SHIFT), "2-1");
        assertEquals(1L - 2L, longSub(1L, 2L, SHIFT), "1-2");
        assertEquals(-1L + 2L, longSub(-1L, -2L, SHIFT), "-1+2");
        assertEquals(0L, longSub(MAX32, MAX32, SHIFT), "MAX-MAX");
        assertEquals(MAX32 - 1, longSub(MAX32, 1L, SHIFT), "MAX-1");
        assertEquals(MIN32 + 1, longSub(MIN32, -1L, SHIFT), "MIN+1");
        try
            {
            longSub(MIN32, 1, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longSub(MAX32, -1, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}
        }

    @Test
    public void test32Unsigned()
        {
        final int SHIFT = 32;

        // add
        assertEquals(1L + 2L, longUnsignedAdd(1L, 2L, SHIFT), "1+2");
        assertEquals(1L - 2L, longUnsignedAdd(1L, -2L, SHIFT), "1-2");
        assertEquals(MAX31U + 1, longUnsignedAdd(MAX31U, 1L, SHIFT), "MAX+1");
        try
            {
            longUnsignedAdd(MAX32U, 1, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longUnsignedAdd(MAX32U, MAX32U, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}

        // sub
        assertEquals(2L - 1L, longUnsignedSub(2L, 1L, SHIFT), "2-1");
        assertEquals(MAX31U, longUnsignedSub(MIN32H, 1L, SHIFT), "MIN32H-1");
        assertEquals(MAX31U, longUnsignedSub(MAX32U, MIN32H, SHIFT), "MAX-MIN");
        try
            {
            longUnsignedSub(1L, 2L, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longUnsignedSub(1, MAX32U, SHIFT);
            fail();
            }
        catch (ArithmeticException ignore) {}
        }


    // ----- the algorithms used by native code ----------------------------------------------------

    private long longAdd(long l1, long l2, int nShift)
        {
        long lr = l1 + l2;

        if ((((l1 ^ lr) & (l2 ^ lr)) << nShift) < 0)
            {
            throw new ArithmeticException("overflow");
            }

        return lr;
        }

    private long longSub(long l1, long l2, int nShift)
        {
        long lr = l1 - l2;

        if ((((l1 ^ l2) & (l1 ^ lr)) << nShift) < 0)
            {
            throw new ArithmeticException("overflow");
            }

        return lr;
        }


    private long longUnsignedAdd(long l1, long l2, int nShift)
        {
        long lr = l1 + l2;

        if ((((l1 & l2) | ((l1 | l2) & ~lr)) << nShift) < 0)
            {
            throw new ArithmeticException("overflow");
            }

        return lr;
        }

    private long longUnsignedSub(long l1, long l2, int nShift)
        {
        long lr = l1 - l2;

        if ((((~l1 & l2) | ((~l1 | l2) & lr)) << nShift) < 0)
            {
            throw new ArithmeticException("overflow");
            }

        return lr;
        }
    }
