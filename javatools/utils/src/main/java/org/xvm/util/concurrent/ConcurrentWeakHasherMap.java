package org.xvm.util.concurrent;

import org.xvm.util.Hasher;
import org.xvm.util.WeakHasherMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * A thread-safe {@link WeakHasherMap}.
 *
 * @author falcom
 */
public class ConcurrentWeakHasherMap<K, V> extends WeakHasherMap<K, V> implements ConcurrentMap<K, V>
    {
    /**
     * Construct a natural hashing {@link ConcurrentWeakHasherMap} with a default initial capacity.
     */
    public ConcurrentWeakHasherMap()
        {
        this(Hasher.natural());
        }

    /**
     * Construct a {@link ConcurrentWeakHasherMap} with a default initial capacity.
     *
     * @param hasher the hasher to use in comparing keys
     */
    public ConcurrentWeakHasherMap(Hasher<K> hasher)
        {
        this(hasher, ConcurrentHashMap::new);
        }

    /**
     * Construct a {@link ConcurrentWeakHasherMap} with an initial capacity and storage allocator.
     *
     * @param hasher  the hasher to use in comparing keys
     * @param storage the thread-safe, non-week backing store allocator
     */
    public ConcurrentWeakHasherMap(Hasher<K> hasher, Supplier<? extends ConcurrentMap<Supplier<K>, V>> storage)
        {
        super(hasher, storage);
        }
    }
