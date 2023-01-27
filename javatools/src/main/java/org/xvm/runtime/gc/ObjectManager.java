package org.xvm.runtime.gc;

/**
 * Provides a means by which to modify the raw storage of an object.
 *
 * @param <V> the storage type
 * @author mf
 */
public interface ObjectManager<V>
    extends FieldAccessor<V>
    {
    /**
     * Allocate a new object with the specified field count.
     *
     * @param cFields the field count
     *
     * @return the object
     */
    V allocate(int cFields);

    /**
     * Return a object to the manager.
     *
     * @param o the object to free
     */
    void free(V o);

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
    }
