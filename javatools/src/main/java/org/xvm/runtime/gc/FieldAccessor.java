package org.xvm.runtime.gc;

/**
 * A means to access the fields within storage.
 *
 * @param <V>
 *
 * @author mf
 */
public interface FieldAccessor<V>
    {
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
