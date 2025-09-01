package org.xvm.util;

import java.util.PrimitiveIterator;

/**
 * A mutating iterator of primitive {@code long} values.
 */
public interface LongMuterator
        extends PrimitiveIterator.OfLong {
    /**
     * Update the last value returned from {@link #next()}.
     *
     * @param value the new value
     */
    void set(long value);
}
