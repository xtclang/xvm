package org.xvm.runtime.gc;

import org.xvm.util.LongMuterator;

import java.util.function.Supplier;

/**
 * An interface describing an automatic memory manager for objects with {@code long} addresses.
 * <p>
 * {@link #allocate allocated} objects are considered to be garbage once they are not reachable from a {@link #addRoot root}.
 *
 * @author mf
 */
public interface GcSpace
    {
    /**
     * Allocate an object.
     *
     * @param cFields the number of fields to be able to store
     * @return the address of the allocated resource
     */
    long allocate(int cFields)
            throws OutOfMemoryError;

    /**
     * Allocate a weak-ref based object.
     * <p>
     * As the object is indicated to a "weak" reference, {@link #getField field 0} must be the field which stores
     * the weak referent, and {@link #getField field 1} if it exists is used to store the notifier (if any).
     *
     * @param cFields the number of fields to be able to store
     * @return the address of the allocated resource
     */
    long allocateWeak(int cFields)
            throws OutOfMemoryError;

    /**
     * Return {@code true} if the address maps to an object in this {@link GcSpace}.
     *
     * @param address the address
     * @return {@code true} if the address maps to an object in this {@link GcSpace}.
     */
    boolean isValid(long address);

    /**
     * Get the field value at a given index.
     * @param address the object address
     * @param index the field index
     *
     * @return the field value
     * @throws SegFault if the supplied address does not map to a live object
     */
    long getField(long address, int index)
        throws SegFault;

    /**
     * Set the field value at a given index.
     *
     * @param address the object address
     * @param index   the field index
     * @param handle the value to set
     * @throws SegFault if the supplied address does not map to a live object
     */
    void setField(long address, int index, long handle)
        throws SegFault;


    /**
     * Add a gc root to this space.
     * <p>
     * Added roots must ultimately be {@link #removeRoot removed}.
     *
     * @param root the root object
     */
    void addRoot(Supplier<? extends LongMuterator> root);

    /**
     * Remove a gc root from this space.
     *
     * @param root the root object
     */
    void removeRoot(Supplier<? extends LongMuterator> root);

    /**
     * Perform a garbage collection cycle in an attempt to reclaim memory.
     */
    void gc();

    /**
     * @return the amount of memory consumed by objects managed by this {@link GcSpace}.
     */
    long getByteCount();

    /**
     * The {@code null} address value.
     */
    long NULL = 0;

    /**
     * For weak-refs the field which contains the referent.
     */
    int WEAK_REFERENT_FIELD = 0;

    /**
     * For weak-refs the field which contains the notifier.
     */
    int WEAK_NOTIFIER_FIELD = 1;
    }