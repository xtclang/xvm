package org.xvm.runtime;

import org.junit.Assert;
import org.junit.Test;

import org.xvm.runtime.template.numbers.LongLong;


/**
 * Unit tests for various native number implementations.
 */
public class TestNumber
    {
    // ---- add ------------------------------------------------------------------------------------

    final static long MAX64 = Long.MAX_VALUE;
    final static long MIN64 = Long.MIN_VALUE;

    final static LongLong MAX128L = new LongLong(-1, 0);
    final static LongLong MIN128H = new LongLong(0, 1);  // MAX128L + 1
    final static LongLong MAX128 = new LongLong(-1, MAX64);
    final static LongLong MIN128 = new LongLong(0, MIN64);

    final static long MAX63U = Long.MAX_VALUE;
    final static long MIN64H = Long.MIN_VALUE; // MAX63U + 1
    final static long MAX64U = -1L;

    final static long MAX32 = Integer.MAX_VALUE;
    final static long MIN32 = Integer.MIN_VALUE;

    final static long MAX32U = 0xFFFF_FFFFL;
    final static long MIN32H = 0x8000_0000L;
    final static long MAX31U = 0x7FFF_FFFFL;

    @Test
    public void test128Signed()
        {
        // add
        Assert.assertEquals("1+2", new LongLong(1+2), new LongLong(1).add(new LongLong(2)));
        Assert.assertEquals("-1+2", new LongLong(-1+2), new LongLong(-1).add(new LongLong(2)));
        Assert.assertEquals("-1-2", new LongLong(-1-2), new LongLong(-1).add(new LongLong(-2)));
        Assert.assertEquals("MAX64+1", new LongLong(MIN64, 0), new LongLong(MAX64).add(new LongLong(1)));
        Assert.assertEquals("MAX64LOW+1", MIN128H, MAX128L.add(new LongLong(1)));
        Assert.assertEquals("MIN128HIGH-1", MAX128L, MIN128H.add(new LongLong(-1)));
        Assert.assertEquals("MAX+MIN", new LongLong(-1), MAX128.add(MIN128));

        Assert.assertEquals("MAX+1", LongLong.OVERFLOW, MAX128.add(new LongLong(1)));
        Assert.assertEquals("MAX+MAX", LongLong.OVERFLOW, MAX128.add(MAX128));
        Assert.assertEquals("MIN-1", LongLong.OVERFLOW, MIN128.add(new LongLong(-1)));
        Assert.assertEquals("MIN+MIN", LongLong.OVERFLOW, MIN128.add(MIN128));

        // sub
        Assert.assertEquals("2-1", new LongLong(2-1), new LongLong(2).sub(new LongLong(1)));
        Assert.assertEquals("-1+2", new LongLong(-1+2), new LongLong(-1).sub(new LongLong(-2)));
        Assert.assertEquals("-1-2", new LongLong(-1-2), new LongLong(-1).sub(new LongLong(2)));
        Assert.assertEquals("MAX64+1", new LongLong(MIN64, 0), new LongLong(MAX64).sub(new LongLong(-1)));
        Assert.assertEquals("MAX128LOW+1", MIN128H, MAX128L.sub(new LongLong(-1)));
        Assert.assertEquals("MIN128HIGH-1", MAX128L, MIN128H.sub(new LongLong(1)));

        Assert.assertEquals("MAX+1", LongLong.OVERFLOW, MAX128.sub(new LongLong(-1)));
        Assert.assertEquals("MIN-1", LongLong.OVERFLOW, MIN128.sub(new LongLong(1)));
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
        Assert.assertEquals("-1+2", -1L+2L, longAdd(-1L, 2L, SHIFT));
        Assert.assertEquals("-1-2", -1L-2L, longAdd(-1L, -2L, SHIFT));
        Assert.assertEquals("MAX+MIN", -1L, longAdd(MAX64, MIN64, SHIFT));
        Assert.assertEquals("MAX-1", MAX64 - 1, longAdd(MAX64, -1L, SHIFT));
        Assert.assertEquals("MIN+1", MIN64 + 1, longAdd(MIN64, +1L, SHIFT));
        try
            {
            longAdd(MAX64, 1, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longAdd(MIN64, -1, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}

        // sub
        Assert.assertEquals("2-1", 2L-1L, longSub(2L, 1L, SHIFT));
        Assert.assertEquals("1-2", 1L-2L, longSub(1L, 2L, SHIFT));
        Assert.assertEquals("-1+2", -1L+2L, longSub(-1L, -2L, SHIFT));
        Assert.assertEquals("MAX-MAX", 0L, longSub(MAX64, MAX64, SHIFT));
        Assert.assertEquals("MAX-1", MAX64 - 1, longSub(MAX64, 1L, SHIFT));
        Assert.assertEquals("MIN+1", MIN64 + 1, longSub(MIN64, -1L, SHIFT));
        try
            {
            longSub(MIN64, 1, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longSub(MAX64, -1, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}
        }

    @Test
    public void test64Unsigned()
        {
        final int SHIFT = 0;

        // add
        Assert.assertEquals("1-2", 1L-2L, longUnsignedAdd(1L, -2L, SHIFT));
        Assert.assertEquals("MAX63+1", MIN64H, longUnsignedAdd(MAX63U, 1L, SHIFT));
        Assert.assertEquals("MAX", MAX64U, longUnsignedAdd(MAX63U, MIN64H, SHIFT));
        try
            {
            longUnsignedAdd(MAX64U, 1, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longUnsignedAdd(MAX64U, MAX64U, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}

        // sub
        Assert.assertEquals("2-1", 2L-1L, longUnsignedSub(2L, 1L, SHIFT));
        Assert.assertEquals("MIN64H-1", MAX63U, longUnsignedSub(MIN64H, 1L, SHIFT));
        Assert.assertEquals("MAX-MIN", MAX63U, longUnsignedSub(MAX64U, MIN64H, SHIFT));
        try
            {
            longUnsignedSub(1L, 2L, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longUnsignedSub(1L, MAX64U, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}
        }

    @Test
    public void test32Signed()
        {
        final int SHIFT = 32;

        // add
        Assert.assertEquals("1+2", 1L+2L, longAdd(1L, 2L, SHIFT));
        Assert.assertEquals("-1+2", -1L+2L, longAdd(-1L, 2L, SHIFT));
        Assert.assertEquals("-1-2", -1-2, longAdd(-1L, -2L, SHIFT));
        Assert.assertEquals("MAX+MIN", -1L, longAdd(MAX32, MIN32, SHIFT));
        Assert.assertEquals("MAX-1", MAX32 - 1, longAdd(MAX32, -1L, SHIFT));
        Assert.assertEquals("MIN+1", MIN32 + 1, longAdd(MIN32, +1L, SHIFT));
        try
            {
            longAdd(MAX32, 1, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longAdd(MIN32, -1, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}

        // sub
        Assert.assertEquals("2-1", 2L-1L, longSub(2L, 1L, SHIFT));
        Assert.assertEquals("1-2", 1L-2L, longSub(1L, 2L, SHIFT));
        Assert.assertEquals("-1+2", -1L+2L, longSub(-1L, -2L, SHIFT));
        Assert.assertEquals("MAX-MAX", 0L, longSub(MAX32, MAX32, SHIFT));
        Assert.assertEquals("MAX-1", MAX32 - 1, longSub(MAX32, 1L, SHIFT));
        Assert.assertEquals("MIN+1", MIN32 + 1, longSub(MIN32, -1L, SHIFT));
        try
            {
            longSub(MIN32, 1, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longSub(MAX32, -1, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}
        }

    @Test
    public void test32Unsigned()
        {
        final int SHIFT = 32;

        // add
        Assert.assertEquals("1+2", 1L + 2L, longUnsignedAdd(1L, 2L, SHIFT));
        Assert.assertEquals("1-2", 1L-2L, longUnsignedAdd(1L, -2L, SHIFT));
        Assert.assertEquals("MAX+1", MAX31U + 1, longUnsignedAdd(MAX31U, 1L, SHIFT));
        try
            {
            longUnsignedAdd(MAX32U, 1, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longUnsignedAdd(MAX32U, MAX32U, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}

        // sub
        Assert.assertEquals("2-1", 2L-1L, longUnsignedSub(2L, 1L, SHIFT));
        Assert.assertEquals("MIN32H-1", MAX31U, longUnsignedSub(MIN32H, 1L, SHIFT));
        Assert.assertEquals("MAx-MIN", MAX31U, longUnsignedSub(MAX32U, MIN32H, SHIFT));
        try
            {
            longUnsignedSub(1L, 2L, SHIFT);
            Assert.fail();
            }
        catch (ArithmeticException ignore) {}
        try
            {
            longUnsignedSub(1, MAX32U, SHIFT);
            Assert.fail();
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