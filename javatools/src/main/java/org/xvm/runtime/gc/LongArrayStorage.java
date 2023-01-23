package org.xvm.runtime.gc;

/**
 * An {@link ObjectStorage} implementation which stores objects in {@code long[]}.
 *
 * @author mf
 */
public class LongArrayStorage
    implements ObjectStorage<long[]>
    {
    /**
     * Singleton instance.
     */
    public static final LongArrayStorage INSTANCE = new LongArrayStorage();

    /**
     * Allocate a new object with the specified field count.
     *
     * @param cFields the field count
     *
     * @return the object
     */
    public long[] allocate(int cFields)
        {
        return new long[cFields + 1];
        }

    @Override
    public boolean getAndSetMarker(long[] o, boolean marker)
        {
        long header = o[0];
        o[0] = marker ? header | 1 : header & ~1;
        return (header & 1) == 1;
        }

    @Override
    public boolean getMarker(long[] o)
        {
        return (o[0] & 1) == 1;
        }

    @Override
    public int getFieldCount(long[] o)
        {
        return o.length - 1;
        }

    @Override
    public long getField(long[] o, int index)
        {
        return o[index + 1];
        }

    @Override
    public void setField(long[] o, int index, long address)
        {
        o[index + 1] = address;
        }
    }
