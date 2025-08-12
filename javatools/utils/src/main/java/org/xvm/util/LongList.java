package org.xvm.util;


/**
 * Simple list-like data structure for collecting some longs.
 */
public class LongList
    {
    public LongList()
        {
        }

    public LongList(int initialCapacity)
        {
        assert initialCapacity >= 0;
        if (initialCapacity > 0)
            {
            m_aVals = new long[initialCapacity];
            }
        }

    public int size()
        {
        return m_cVals;
        }

    public boolean isEmpty()
        {
        return m_cVals == 0;
        }

    public void add(long n)
        {
        long[] aVals = m_aVals;
        int    cVals = m_cVals;

        // check capacity
        if (aVals == null || aVals.length >= cVals)
            {
            aVals   = new long[Math.max(16, cVals * 2)];
            m_aVals = aVals;
            }

        aVals[cVals] = n;
        m_cVals      = cVals + 1;
        }

    public long[] toArray()
        {
        int cVals = m_cVals;
        if (cVals == 0)
            {
            return NO_LONGS;
            }

        long[] aVals = new long[cVals];
        System.arraycopy(m_aVals, 0, aVals, 0, cVals);
        return aVals;
        }

    public static final long[] NO_LONGS = new long[0];

    private long[] m_aVals;
    private int    m_cVals;
    }
