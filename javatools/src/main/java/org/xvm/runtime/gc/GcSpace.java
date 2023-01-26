package org.xvm.runtime.gc;

import java.util.PrimitiveIterator;
import java.util.function.Supplier;

/**
 * An interface describing an automatic memory manager for objects of type {@link V} which are to be
 * {@link #get accessed} via {@code long} addresses.
 * <p>
 * {@link #allocate allocated} objects are considered to be garbage once they are not reachable from a {@link #addRoot root}.
 *
 * @author mf
 */
public interface GcSpace<V>
    {
    /**
     * @return the {@link FieldAccessor} for the space's object type
     */
    FieldAccessor<V> accessor();

    /**
     * Allocate an object using the specified "constructor".
     *
     * @param cFields the number of fields to be able to store
     * @return the address of the allocated resource
     */
    default long allocate(int cFields)
        throws OutOfMemoryError
        {
        return allocate(cFields, false);
        }

    /**
     * Allocate an object using the specified "constructor".
     *
     * <p>
     * If the object is indicated to a "weak" reference, then {@link #get field 0} must be the field which stores
     * the weak referent, and {@link #get field 1} if it exists is used to store the notifier (if any).
     *
     * @param cFields the number of fields to be able to store
     * @param fWeak {@code true} if the object represents a "weak" reference
     * @return the address of the allocated resource
     */
    long allocate(int cFields, boolean fWeak)
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
     * Added roots must ultimately be {@link #removeRoot removed}.
     *
     * @param root the root object
     */
    void addRoot(Supplier<? extends PrimitiveIterator.OfLong> root);

    /**
     * Remove a gc root from this space.
     *
     * @param root the root object
     */
    void removeRoot(Supplier<? extends PrimitiveIterator.OfLong> root);

    /**
     * Perform a garbage collection cycle in an attempt to reclaim memory.
     */
    void gc();

    /**
     * @return the amount of memory the {@link V}s within this {@link GcSpace} are consuming.
     */
    long getByteCount();

    /**
     * The {@code null} pointer value.
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
