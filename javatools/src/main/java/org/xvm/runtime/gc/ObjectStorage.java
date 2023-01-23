package org.xvm.runtime.gc;

/**
 * Provides a means by which to modify the raw storage of an object.
 *
 * @param <V> the storage type
 * @author mf
 */
public interface ObjectStorage<V>
    {
    /**
     * Set the marker to the given value and return the prior marker value.
     *
     * @param o      the object to mark
     * @param marker the value to mark with
     *
     * @return the prior value
     */
    boolean getAndSetMarker(V o, boolean marker);

    /**
     * Return the marker value for the object.
     *
     * @param o the object
     *
     * @return the object's marker value
     */
    boolean getMarker(V o);


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
