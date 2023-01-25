package org.xvm.runtime.gc;

import org.xvm.util.ShallowSizeOf;

/**
 * Provides a means by which to modify the raw storage of an object.
 *
 * @param <V> the storage type
 * @author mf
 */
public interface ObjectStorage<V>
    {
    /**
     * Return the header for this object.
     *
     * @param o the object
     * @return the header
     */
    long getHeader(V o);

    /**
     * Set the header for this object.
     *
     * @param o the object
     * @return the header
     */
    void setHeader(V o, long header);

    /**
     * Return the shallow size of the object.
     *
     * @param o the object
     *
     * @return the shallow size in bytes
     */
    long getByteSize(V o);

    /**
     * Return the number of fields in the object.
     *
     * @param o the object
     *
     * @return the field count
     */
    int getFieldCount(V o);

    /**
     * Get the field value at a given index.
     * @param o     the object
     * @param index the field index
     *
     * @return the field value
     */
    long getField(V o, int index);

    /**
     * Set the field value at a given index.
     *
     * @param o       the object
     * @param index   the field index
     * @param address the field value
     */
    void setField(V o, int index, long address);
    }
