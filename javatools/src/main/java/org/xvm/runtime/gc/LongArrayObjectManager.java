package org.xvm.runtime.gc;

import org.xvm.util.ShallowSizeOf;

/**
 * An {@link ObjectManager} implementation which stores objects in {@code long[]}s.
 *
 * @author mf
 */
public class LongArrayObjectManager
    implements ObjectManager<long[]>
    {
    /**
     * Singleton instance.
     */
    public static final LongArrayObjectManager INSTANCE = new LongArrayObjectManager();

    @Override
    public long[] allocate(int cFields)
        {
        return new long[cFields + 1];
        }

    @Override
    public void free(long[] o)
        {
        // mark as invalid in case there are any dangling references
        o[0] = INVALIDATION_MASK;
        // allow java gc to collect it
        }

    @Override
    public long getByteSize(long[] o)
        {
        return ShallowSizeOf.arrayOf(long.class, validate(o).length);
        }

    @Override
    public long getHeader(long[] o)
        {
        return validate(o)[0];
        }

    @Override
    public void setHeader(long[] o, long header)
        {
        validate(o)[0] = header;
        }

    @Override
    public int getFieldCount(long[] o)
        {
        return validate(o).length - 1;
        }

    @Override
    public long getField(long[] o, int index)
        {
        return validate(o)[index + 1];
        }

    @Override
    public void setField(long[] o, int index, long address)
        {
        validate(o)[index + 1] = address;
        }

    /**
     * Verify that the object is valid, i.e. not freed.
     *
     * @param o the object to validate
     * @return the valid object
     * @throws SegFault if invalid
     */
    protected long[] validate(long[] o)
        {
        if ((o[0] & INVALIDATION_MASK) != 0)
            {
            throw new SegFault();
            }
        return o;
        }

    /**
     * The bitmask indicating that an object has been freed and is no longer valid.
     */
    private static final long INVALIDATION_MASK = 1L << 63;
    }
