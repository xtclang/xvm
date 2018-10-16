package org.xvm.runtime.template;


import org.xvm.util.PackedInteger;

import java.math.BigInteger;


/**
 * 128 bit long implementation.
 */
public class LongLong
        implements Comparable<LongLong>
    {
    public LongLong(long lLowValue, long lHighValue)
        {
        m_lLowValue = lLowValue;
        m_lHighValue = lHighValue;
        }

    public LongLong(long lValue)
        {
        this(lValue, lValue >= 0 ? 0 : -1L);
        }

    public LongLong(BigInteger bi)
        {
        long lLow = 0;
        long lHigh = 0;

        byte[] bytes = bi.toByteArray();

        int i;

        for (i = 0; i < Math.min(4, bytes.length); ++i)
            {
            lLow |= (long)(bytes[i]) << (8 * i);
            }

        for (; i < Math.min(8, bytes.length); ++i)
            {
            lHigh |= (long)(bytes[i]) << (8 * (i - 4));
            }

        m_lLowValue = lLow;
        m_lHighValue = lHigh;
        }

    public LongLong add(LongLong ll)
        {
        long lResultLow = m_lLowValue + ll.m_lLowValue;
        long lResultHigh = m_lHighValue + ll.m_lHighValue;

        if (((m_lLowValue ^ ll.m_lLowValue) & (m_lLowValue ^ lResultLow)) < 0)
            {
            return OVERFLOW;
            }

        if (((m_lLowValue & ll.m_lLowValue) | ((m_lLowValue | ll.m_lLowValue) & ~lResultLow)) < 0)
            {
            if (ll.m_lHighValue < 0)
                {
                if (lResultHigh == Long.MIN_VALUE)
                    {
                    return OVERFLOW;
                    }

                --lResultHigh;
                }
            else
                {
                if (lResultHigh == Long.MAX_VALUE)
                    {
                    return OVERFLOW;
                    }

                ++lResultHigh;
                }
            }

        return new LongLong(lResultLow, lResultHigh);
        }

    public LongLong sub(LongLong ll)
        {
        long lResultLow = m_lLowValue - ll.m_lLowValue;
        long lResultHigh = m_lHighValue - ll.m_lHighValue;

        if (((m_lLowValue ^ ll.m_lLowValue) & (m_lLowValue ^ lResultLow)) < 0)
            {
            return OVERFLOW;
            }

        if (((~m_lLowValue & ll.m_lLowValue) | ((~m_lLowValue | ll.m_lLowValue) & lResultLow)) < 0)
            {
            if (ll.m_lHighValue > 0)
                {
                if (lResultHigh == Long.MIN_VALUE)
                    {
                    return OVERFLOW;
                    }

                --lResultHigh;
                }
            else
                {
                if (lResultHigh == Long.MAX_VALUE)
                    {
                    return OVERFLOW;
                    }

                ++lResultHigh;
                }
            }

        return new LongLong(lResultLow, lResultHigh);
        }

    public LongLong mul(LongLong ll)
        {
        LongLong lhs = this;
        LongLong llResult = LongLong.ZERO;
        int cShift = 0;
        boolean fNeg = false;

        if (m_lHighValue < 0)
            {
            lhs = lhs.negate();
            fNeg = true;
            }

        if (ll.m_lHighValue < 0)
            {
            ll = ll.negate();
            fNeg = !fNeg;
            }

        while (!ll.equals(LongLong.ZERO))
            {
            if ((ll.m_lLowValue & 1) != 0)
                {
                llResult = llResult.add(lhs.shl(cShift));

                if (llResult.equals(OVERFLOW))
                    {
                    return OVERFLOW;
                    }
                }

            ll = ll.ushr(1);
            ++cShift;
            }

        return fNeg ? llResult.negate() : llResult;
        }

    public LongLong div(LongLong ll)
        {
        if (m_lLowValue == 0 && m_lHighValue == 0)
            {
            return LongLong.ZERO; // div by 0 error not required since it's handled by numeric classes
            }

        boolean fNeg = false;
        LongLong llNumerator = this;

        if (llNumerator.compareTo(LongLong.ZERO) < 0)
            {
            llNumerator = llNumerator.negate();
            fNeg = true;
            }

        if (ll.compareTo(LongLong.ZERO) < 0)
            {
            fNeg = !fNeg;
            ll = ll.negate();
            }

        LongLong llQuotient = LongLong.ZERO;
        LongLong llRemainder = LongLong.ZERO;

        for (int i = 127; i >= 0; --i)
            {
            llRemainder = llRemainder.shl(1);
            llRemainder = llRemainder.or((llNumerator.and(LongLong.ONE.shl(i))).ushr(i));

            if (llRemainder.compareTo(ll) >= 0)
                {
                llRemainder = llRemainder.sub(ll);
                llQuotient = llQuotient.or(LongLong.ONE.shl(i));
                }
            }

        return fNeg ? llQuotient.negate() : llQuotient;
        }

    public LongLong mod(LongLong ll)
        {
        if (m_lLowValue == 0 && m_lHighValue == 0)
            {
            return LongLong.ZERO; // div by 0 error not required since it's handled by numeric classes
            }

        boolean fNeg = false;
        LongLong llNumerator = this;

        if (llNumerator.compareTo(LongLong.ZERO) < 0)
            {
            llNumerator = llNumerator.negate();
            fNeg = true;
            }

        if (ll.compareTo(LongLong.ZERO) < 0)
            {
            fNeg = !fNeg;
            ll = ll.negate();
            }

        LongLong llRemainder = LongLong.ZERO;

        for (int i = 127; i >= 0; --i)
            {
            llRemainder = llRemainder.shl(1);
            llRemainder = llRemainder.or((llNumerator.and(LongLong.ONE.shl(i))).ushr(i));

            if (llRemainder.compareTo(ll) >= 0)
                {
                llRemainder = llRemainder.sub(ll);
                }
            }

        return fNeg ? ll.sub(llRemainder) : llRemainder;
        }

    public LongLong next()
        {
        if (m_lLowValue == -1)
            {
            if (m_lHighValue == Long.MAX_VALUE)
                {
                return OVERFLOW;
                }

            return new LongLong(0L, m_lHighValue + 1);
            }

        return new LongLong(m_lLowValue + 1, m_lHighValue);
        }

    public LongLong prev()
        {
        if (m_lLowValue == 0)
            {
            if (m_lHighValue == Long.MIN_VALUE)
                {
                return OVERFLOW;
                }

            return new LongLong(-1L, m_lHighValue - 1);
            }

        return new LongLong(m_lLowValue - 1, m_lHighValue);
        }

    public LongLong negate()
        {
        if (m_lHighValue == Long.MIN_VALUE && m_lLowValue == 0)
            {
            return OVERFLOW;
            }

        return complement().next();
        }

    public LongLong and(LongLong ll)
        {
        return new LongLong(m_lLowValue & ll.m_lLowValue, m_lHighValue & ll.m_lHighValue);
        }

    public LongLong or(LongLong ll)
        {
        return new LongLong(m_lLowValue | ll.m_lLowValue, m_lHighValue | ll.m_lHighValue);
        }

    public LongLong xor(LongLong ll)
        {
        return new LongLong(m_lLowValue ^ ll.m_lLowValue, m_lHighValue ^ ll.m_lHighValue);
        }

    public LongLong complement()
        {
        return new LongLong(~m_lLowValue, ~m_lHighValue);
        }

    public LongLong shl(int n)
        {
        if (n <= 64)
            {
            final long nComp = 64 - n;
            final long bitDifference = ((-1 >>> nComp) << nComp) & m_lLowValue;

            return new LongLong(m_lLowValue << n, (m_lHighValue << n) | (bitDifference >> nComp));
            }

        return new LongLong(0, m_lLowValue << (n - 64));
        }

    public LongLong shr(int n)
        {
        if (n <= 64)
            {
            final long nComp = 64 - n;
            final long bitDifference = (Long.MAX_VALUE >>> nComp) & m_lHighValue;

            return new LongLong((m_lLowValue >>> n) | (bitDifference << nComp), m_lHighValue >> n);
            }

        return new LongLong((m_lHighValue >>> (n - 64)) & Long.MAX_VALUE, m_lHighValue < 0 ? Long.MIN_VALUE : 0);
        }

    public LongLong ushr(int n)
        {
        if (n <= 64)
            {
            final long nComp = 64 - n;
            final long bitDifference = (-1 >>> nComp) & m_lHighValue;

            return new LongLong((m_lLowValue >>> n) | (bitDifference << nComp), m_lHighValue >>> n);
            }

        return new LongLong(m_lHighValue >>> (n - 64), 0);
        }

    public LongLong shl(LongLong n)
        {
        if (n.m_lHighValue != 0 || n.m_lLowValue > 127 || n.m_lLowValue < 0)
            {
            return LongLong.ZERO;
            }

        return shl((int)n.m_lLowValue);
        }

    public LongLong shr(LongLong n)
        {
        if (n.m_lHighValue != 0 || n.m_lLowValue > 127 || n.m_lLowValue < 0)
            {
            return LongLong.ZERO;
            }

        return shr((int)n.m_lLowValue);
        }

    public LongLong ushr(LongLong n)
        {
        if (n.m_lHighValue != 0 || n.m_lLowValue > 127 || n.m_lLowValue < 0)
            {
            return LongLong.ZERO;
            }

        return ushr((int)n.m_lLowValue);
        }

    @Override
    public int hashCode()
        {
        return Long.hashCode(m_lLowValue) ^ Long.hashCode(m_lHighValue);
        }

    @Override
    public boolean equals(Object obj)
        {
        if (!(obj instanceof LongLong))
            {
            return false;
            }

        LongLong ll = (LongLong)obj;
        return m_lLowValue == ll.m_lLowValue && m_lHighValue == ll.m_lHighValue;
        }

    @Override
    public int compareTo(LongLong ll)
        {
        if (m_lHighValue != ll.m_lHighValue)
            {
            return m_lHighValue > ll.m_lHighValue ? 1 : -1;
            }

        return xUnsignedConstrainedInt.unsignedCompare(m_lLowValue, m_lHighValue);
        }

    public long getLowValue()
        {
        return m_lLowValue;
        }

    public long getHighValue()
        {
        return m_lHighValue;
        }

    public BigInteger toBigInteger()
        {
        return new BigInteger(Long.toString(m_lLowValue)).
            or(new BigInteger(Long.toString(m_lHighValue)).shiftLeft(64));
        }

    public PackedInteger toPackedInteger()
        {
        return new PackedInteger(toBigInteger());
        }

    @Override
    public String toString()
        {
        return toBigInteger().toString();
        }

    public static final LongLong ZERO = new LongLong(0, 0);
    public static final LongLong ONE = new LongLong(1, 0);
    public static final LongLong NEG_ONE = new LongLong(-1, -1);

    public static final LongLong OVERFLOW = new Overflow();

    protected final long m_lLowValue;
    protected final long m_lHighValue;

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
