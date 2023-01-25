package org.xvm.runtime.gc;

import org.xvm.util.ShallowSizeOf;

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
    public long getByteSize(long[] o)
        {
        return ShallowSizeOf.arrayOf(long.class, o.length);
        }

    @Override
    public long getHeader(long[] o)
        {
        return o[0];
        }

    @Override
    public void setHeader(long[] o, long header)
        {
        o[0] = header;
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
