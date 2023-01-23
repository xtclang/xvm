package org.xvm.runtime.gc;

import java.util.PrimitiveIterator;
import java.util.function.Supplier;

/**
 * An interface describing an automatic memory manager for objects of type {@link V} which are to be
 * {@link #get accessed} via {@code long} addresses.
 * <p>
 * {@link #allocate allocated} objects are considered to be garbage once they are not reachable from a {@link #add(Root)} root}.
 *
 * @author mf
 */
public interface GcSpace<V>
    {

    /**
     * Allocate an object using the specified "constructor".
     *
     * @param constructor the function to run to construct the object
     * @return the address of the allocated resource
     */
    long allocate(Supplier<? extends V> constructor)
            throws OutOfMemoryError;

    /**
     * Return the object at a given address.
     *
     * @param address the address
     * @return the object
     * @throws SegFault if the supplied address does not map to a live object
     */
    V get(long address)
        throws SegFault;

    /**
     * Add a gc root to this space.
     * <p>
     * Added roots must ultimately be {@link #remove removed}.
     *
     * @param root the root object
     */
    void add(Root root);

    /**
     * Remove a gc root from this space.
     *
     * @param root the root object
     */
    void remove(Root root);

    /**
     * Perform a garbage collection cycle in an attempt to reclaim memory.
     */
    void gc();

    /**
     * @return the amount of memory the {@link V}s within this {@link GcSpace} are consuming.
     */
    long getByteCount();

    /**
     * A representation of a gc root object.
     */
    interface Root
        {
        /**
         * @return an iterator over the objects directly accessible from the root.
         */
        PrimitiveIterator.OfLong collectables();
        }
    }
