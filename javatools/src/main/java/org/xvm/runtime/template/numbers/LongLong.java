package org.xvm.runtime.template.numbers;


import java.math.BigInteger;


/**
 * 128 bit long implementation used by both Int128 and UInt128.
 *
 * TODO: optimize out BigInteger use for multiplication and division;
 * @see <a href="https://mrob.com/pub/math/int128.c.txt">int128.c</a>
 */
public class LongLong
    {
    /**
     * Construct a LongLong object based on the low and high long values.
     */
    public LongLong(long lLow, long lHigh)
        {
        m_lLow  = lLow;
        m_lHigh = lHigh;
        }

    /**
     * Construct a signed LongLong object based on the low long value.
     */
    public LongLong(long lValue)
        {
        this(lValue, lValue >= 0 ? 0L : -1L);
        }

    /**
     * Construct a LongLong object based on the low long value.
     */
    public LongLong(long lValue, boolean fSigned)
        {
        this(lValue, lValue >= 0 || !fSigned ? 0L : -1L);
        }

    /**
     * @return true iff the value is small enough to fit into a <tt>long</tt>
     */
    public boolean isSmall(boolean fSigned)
        {
        return fSigned
                ? m_lLow >= 0 ? m_lHigh == 0L : m_lHigh == -1L
                : m_lHigh == 0L;
        }

    public LongLong add(LongLong ll)
        {
        long l1L = m_lLow;
        long l1H = m_lHigh;
        long l2L = ll.m_lLow;
        long l2H = ll.m_lHigh;
        long lrL = l1L + l2L;
        long lrH = l1H + l2H;

        // high overflow check is the same as for signed longs
        if (((l1H ^ lrH) & (l2H ^ lrH)) < 0)
            {
            return OVERFLOW;
            }

        // low overflow check is the same as for unsigned longs
        if (((l1L & l2L) | ((l1L | l2L) & ~lrL)) < 0)
            {
            if (lrH == Long.MAX_VALUE)
                {
                return OVERFLOW;
                }
            lrH++;
            }

        return new LongLong(lrL, lrH);
        }

    public LongLong addUnsigned(LongLong ll)
        {
        long l1L = m_lLow;
        long l1H = m_lHigh;
        long l2L = ll.m_lLow;
        long l2H = ll.m_lHigh;
        long lrL = l1L + l2L;
        long lrH = l1H + l2H;

        // both high and low overflow checks are the same as for unsigned longs
        if (((l1H & l2H) | ((l1H | l2H) & ~lrH)) < 0)
            {
            return OVERFLOW;
            }

        if (((l1L & l2L) | ((l1L | l2L) & ~lrL)) < 0)
            {
            if (lrH == -1) // maximum unsigned long
                {
                return OVERFLOW;
                }
            lrH++;
            }

        return new LongLong(lrL, lrH);
        }

    public LongLong sub(LongLong ll)
        {
        long l1L = m_lLow;
        long l1H = m_lHigh;
        long l2L = ll.m_lLow;
        long l2H = ll.m_lHigh;
        long lrL = l1L - l2L;
        long lrH = l1H - l2H;

        // high overflow check is the same as for signed longs
        if (((l1H ^ l2H) & (l1H ^ lrH)) < 0)
            {
            return OVERFLOW;
            }

        // low overflow check is the same as for unsigned longs
        if (((~l1L & l2L) | ((~l1L | l2L) & lrL)) < 0)
            {
            if (lrH == Long.MIN_VALUE)
                {
                return OVERFLOW;
                }

            lrH--;
            }

        return new LongLong(lrL, lrH);
        }

    public LongLong subUnassigned(LongLong ll)
        {
        long l1L = m_lLow;
        long l1H = m_lHigh;
        long l2L = ll.m_lLow;
        long l2H = ll.m_lHigh;
        long lrL = l1L - l2L;
        long lrH = l1H - l2H;

        // both high and low overflow checks are the same as for unsigned longs
        if (((~l1H & l2H) | ((~l1H | l2H) & lrH)) < 0)
            {
            return OVERFLOW;
            }

        if (((~l1L & l2L) | ((~l1L | l2L) & lrL)) < 0)
            {
            if (lrH == 0)
                {
                return OVERFLOW;
                }

            lrH--;
            }

        return new LongLong(lrL, lrH);
        }

    public LongLong mul(LongLong ll)
        {
        BigInteger bi1 = toBigInteger();
        BigInteger bi2 = ll.toBigInteger();
        BigInteger bir = bi1.multiply(bi2);

        return bir.bitLength() <= 127
            ? fromBigInteger(bir)
            : OVERFLOW;
        }

    public LongLong mulUnsigned(LongLong ll)
        {
        BigInteger bi1 = toUnsignedBigInteger();
        BigInteger bi2 = ll.toUnsignedBigInteger();
        BigInteger bir = bi1.multiply(bi2);

        return bir.bitLength() <= 128
            ? fromBigInteger(bir)
            : OVERFLOW;
        }

    public LongLong div(LongLong ll)
        {
        long l2L = ll.m_lLow;
        long l2H = ll.m_lHigh;

        if (l2H == 0)
            {
            if (l2L == 0)
                {
                return OVERFLOW;
                }

            if (l2L > 0)
                {
                return div(l2L);
                }
            }
        else
            {
            if (l2H == -1 && l2L < 0)
                {
                return div(l2L);
                }
            }

        // the divisor doesn't fit into a long; use the BigInteger for now
        BigInteger bi1 = toBigInteger();
        BigInteger bi2 = ll.toBigInteger();
        BigInteger bir = bi1.divide(bi2);

        return fromBigInteger(bir);
        }

    public LongLong div(long l)
        {
        long l1L = m_lLow;
        long l1H = m_lHigh;

        if (l1H == 0)
            {
            if (l1L == 0)
                {
                return ZERO;
                }

            if (l1L > 0)
                {
                return new LongLong(l1L/l);
                }
            }
        else
            {
            if (l1H == -1 && l1L < 0)
                {
                return new LongLong(l1L/l);
                }
            }

        // the dividend doesn't fit into a long; use the BigInteger for now
        BigInteger bi1 = toBigInteger();
        BigInteger bi2 = BigInteger.valueOf(l);
        BigInteger bir = bi1.divide(bi2);

        return fromBigInteger(bir);
        }

    public LongLong divUnsigned(LongLong ll)
        {
        long l2L = ll.m_lLow;
        long l2H = ll.m_lHigh;

        if (l2H == 0)
            {
            if (l2L == 0)
                {
                return OVERFLOW;
                }

            if (l2L > 0)
                {
                return divUnsigned(l2L);
                }
            }

        // the divisor doesn't fit into a long; use the BigInteger for now
        BigInteger bi1 = toUnsignedBigInteger();
        BigInteger bi2 = ll.toUnsignedBigInteger();
        BigInteger bir = bi1.divide(bi2);

        return fromBigInteger(bir);
        }

    public LongLong divUnsigned(long l)
        {
        long l1L = m_lLow;
        long l1H = m_lHigh;

        if (l1H == 0)
            {
            if (l1L == 0)
                {
                return ZERO;
                }

            if (l1L > 0)
                {
                return new LongLong(l1L/l);
                }
            }

        // the dividend doesn't fit into a long; use the BigInteger for now
        BigInteger bi1 = toUnsignedBigInteger();
        BigInteger bi2 = toUnsignedBigInteger(l);
        BigInteger bir = bi1.divide(bi2);

        return fromBigInteger(bir);
        }

    public LongLong[] divrem(LongLong ll)
        {
        long l2L = ll.m_lLow;
        long l2H = ll.m_lHigh;

        if (l2H == 0)
            {
            if (l2L == 0)
                {
                return OVERFLOWx2;
                }

            if (l2L > 0)
                {
                return divrem(l2L);
                }
            }
        else
            {
            if (l2H == -1 && l2L < 0)
                {
                return divrem(l2L);
                }
            }

        // the divisor doesn't fit into a long; use the BigInteger for now
        BigInteger   bi1  = toBigInteger();
        BigInteger   bi2  = ll.toBigInteger();
        BigInteger[] abir = bi1.divideAndRemainder(bi2);

        return fromBigIntegers(abir);
        }

    public LongLong[] divrem(long l)
        {
        long l1L = m_lLow;
        long l1H = m_lHigh;

        if (l1H == 0)
            {
            if (l1L == 0)
                {
                return ZEROx2;
                }

            if (l1L > 0)
                {
                return new LongLong[] {new LongLong(l1L/l), new LongLong(l1L%l)};
                }
            }
        else
            {
            if (l1H == -1 && l1L < 0)
                {
                return new LongLong[] {new LongLong(l1L/l), new LongLong(l1H%l)};
                }
            }

        // the dividend doesn't fit into a long; use the BigInteger for now
        BigInteger   bi1  = toBigInteger();
        BigInteger   bi2  = BigInteger.valueOf(l);
        BigInteger[] abir = bi1.divideAndRemainder(bi2);

        return fromBigIntegers(abir);
        }

    public LongLong[] divremUnsigned(LongLong ll)
        {
        long l2L = ll.m_lLow;
        long l2H = ll.m_lHigh;

        if (l2H == 0)
            {
            if (l2L == 0)
                {
                return OVERFLOWx2;
                }

            if (l2L > 0)
                {
                return divremUnsigned(l2L);
                }
            }

        // the divisor doesn't fit into a long; use the BigInteger for now
        BigInteger   bi1  = toUnsignedBigInteger();
        BigInteger   bi2  = ll.toUnsignedBigInteger();
        BigInteger[] abir = bi1.divideAndRemainder(bi2);

        return fromBigIntegers(abir);
        }

    public LongLong[] divremUnsigned(long l)
        {
        long l1L = m_lLow;
        long l1H = m_lHigh;

        if (l1H == 0)
            {
            if (l1L == 0)
                {
                return ZEROx2;
                }

            if (l1L > 0)
                {
                return new LongLong[] {new LongLong(l1L/l), new LongLong(l1L%l)};
                }
            }

        // the dividend doesn't fit into a long; use the BigInteger for now
        BigInteger   bi1  = toUnsignedBigInteger();
        BigInteger   bi2  = toUnsignedBigInteger(l);
        BigInteger[] abir = bi1.divideAndRemainder(bi2);

        return fromBigIntegers(abir);
        }

    public LongLong mod(LongLong ll)
        {
        BigInteger bi1 = toBigInteger();
        BigInteger bi2 = ll.toBigInteger();
        BigInteger bir = bi1.mod(bi2);

        return fromBigInteger(bir);
        }

    public LongLong modUnsigned(LongLong ll)
        {
        BigInteger bi1 = toUnsignedBigInteger();
        BigInteger bi2 = ll.toUnsignedBigInteger();
        BigInteger bir = bi1.mod(bi2);

        return fromBigInteger(bir);
        }

    public LongLong next(boolean fSigned)
        {
        if (m_lLow == -1)
            {
            if (m_lHigh == (fSigned ? Long.MAX_VALUE : -1))
                {
                return OVERFLOW;
                }

            return new LongLong(0L, m_lHigh + 1);
            }

        return new LongLong(m_lLow + 1, m_lHigh);
        }

    public LongLong prev(boolean fSigned)
        {
        if (m_lLow == 0)
            {
            if (m_lHigh == (fSigned ? Long.MIN_VALUE : 0))
                {
                return OVERFLOW;
                }

            return new LongLong(-1L, m_lHigh - 1);
            }

        return new LongLong(m_lLow - 1, m_lHigh);
        }

    public LongLong negate()
        {
        if (m_lHigh == Long.MIN_VALUE && m_lLow == 0)
            {
            return OVERFLOW;
            }

        return complement().next(true);
        }

    public LongLong and(LongLong ll)
        {
        return new LongLong(m_lLow & ll.m_lLow, m_lHigh & ll.m_lHigh);
        }

    public LongLong or(LongLong ll)
        {
        return new LongLong(m_lLow | ll.m_lLow, m_lHigh | ll.m_lHigh);
        }

    public LongLong xor(LongLong ll)
        {
        return new LongLong(m_lLow ^ ll.m_lLow, m_lHigh ^ ll.m_lHigh);
        }

    public LongLong complement()
        {
        return new LongLong(~m_lLow, ~m_lHigh);
        }

    public LongLong shl(int n)
        {
        if (n < 64)
            {
            return n == 0
                ? this
                : new LongLong(m_lLow << n, (m_lHigh << n) | (m_lLow >>> (64 - n)));
            }

        return new LongLong(0, m_lLow << (n - 64));
        }

    public LongLong shr(int n)
        {
        if (n < 64)
            {
            return n == 0
                ? this
                : new LongLong((m_lLow >>> n) | (m_lHigh << (64 - n)), m_lHigh >> n);
            }

        return new LongLong(m_lHigh >> (n - 64), m_lHigh < 0 ? -1 : 0);
        }

    public LongLong ushr(int n)
        {
        if (n < 64)
            {
            return n == 0
                ? this
                : new LongLong((m_lLow >>> n) | (m_lHigh << (64 - n)), m_lHigh >>> n);
            }

        return new LongLong(m_lHigh >>> (n - 64), 0);
        }

    public LongLong shl(LongLong n)
        {
        if (n.m_lHigh != 0 || n.m_lLow > 127 || n.m_lLow < 0)
            {
            return LongLong.ZERO;
            }

        return shl((int) n.m_lLow);
        }

    public LongLong shr(LongLong n)
        {
        if (n.m_lHigh != 0 || n.m_lLow > 127 || n.m_lLow < 0)
            {
            return LongLong.ZERO;
            }

        return shr((int) n.m_lLow);
        }

    public LongLong ushr(LongLong n)
        {
        if (n.m_lHigh != 0 || n.m_lLow > 127 || n.m_lLow < 0)
            {
            return LongLong.ZERO;
            }

        return ushr((int) n.m_lLow);
        }

    public int signum()
        {
        return Long.signum(m_lHigh);
        }

    @Override
    public int hashCode()
        {
        return Long.hashCode(m_lLow) ^ Long.hashCode(m_lHigh);
        }

    @Override
    public boolean equals(Object obj)
        {
        if (!(obj instanceof LongLong that))
            {
            return false;
            }

        return this.m_lLow == that.m_lLow && this.m_lHigh == that.m_lHigh;
        }

    public int compare(LongLong ll)
        {
        long lThisHigh = m_lHigh;
        long lThatHigh = ll.m_lHigh;
        if (lThisHigh != lThatHigh)
            {
            return Long.compare(lThisHigh, lThatHigh);
            }

        int nCompare = Long.compareUnsigned(m_lLow, ll.m_lLow);
        return lThisHigh >= 0 ? nCompare : -nCompare;
        }

    public int compareUnsigned(LongLong ll)
        {
        long lThisHigh = m_lHigh;
        long lThatHigh = ll.m_lHigh;
        if (lThisHigh != lThatHigh)
            {
            return Long.compareUnsigned(lThisHigh, lThatHigh);
            }

        return Long.compareUnsigned(m_lLow, ll.m_lLow);
        }

    public long getLowValue()
        {
        return m_lLow;
        }

    public long getHighValue()
        {
        return m_lHigh;
        }

    public BigInteger toBigInteger()
        {
        return toUnsignedBigInteger(m_lLow).
            or(BigInteger.valueOf(m_lHigh).shiftLeft(64));
        }

    public BigInteger toUnsignedBigInteger()
        {
        return toUnsignedBigInteger(m_lLow).
            or(toUnsignedBigInteger(m_lHigh).shiftLeft(64));
        }

    private static BigInteger toUnsignedBigInteger(long l)
        {
        if (l >= 0L)
            {
            return BigInteger.valueOf(l);
            }

        int nHigh = (int) (l >>> 32);
        int nLow  = (int) l;

        return (BigInteger.valueOf(Integer.toUnsignedLong(nHigh))).shiftLeft(32).
            add(BigInteger.valueOf(Integer.toUnsignedLong(nLow)));
        }

    /**
     * Create a LongLong from a 128 bit BigInteger.
     *
     * This algorithm works for both signed and unsigned values.
     *
     * @param bi  the big integer to convert
     *
     * @return a corresponding LongLong value
     */
    public static LongLong fromBigInteger(BigInteger bi)
        {
        assert bi.bitLength() <= 128;

        BigInteger biLow  = bi.and(BIG_MASK64);
        BigInteger biHigh = bi.shiftRight(64);
        return new LongLong(biLow.longValue(), biHigh.longValue());
        }

    public static LongLong[] fromBigIntegers(BigInteger[] abi)
        {
        assert abi.length == 2;
        return new LongLong[] {fromBigInteger(abi[0]), fromBigInteger(abi[1])};
        }

    @Override
    public String toString()
        {
        return toBigInteger().toString();
        }

    public static final LongLong   OVERFLOW   = new Overflow();
    public static final LongLong   ZERO       = new LongLong(0, 0);
    public static final LongLong   MAX_VALUE  = new LongLong(-1, Long.MAX_VALUE);
    public static final LongLong   MIN_VALUE  = new LongLong(0, Long.MIN_VALUE);
    public static final LongLong[] ZEROx2     = new LongLong[] {ZERO, ZERO};
    public static final LongLong[] OVERFLOWx2 = new LongLong[] {OVERFLOW, OVERFLOW};

    protected static final BigInteger BIG_MASK64 = new BigInteger("FFFFFFFFFFFFFFFF", 16);

    protected final long m_lLow;
    protected final long m_lHigh;

    private static class Overflow
            extends LongLong
        {
        private Overflow()
            {
            super(0, 0);
            }

        @Override
        public boolean equals(Object obj)
            {
            return this == obj;
            }
        }
    }