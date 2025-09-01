package org.xvm.util.concurrent;

import org.xvm.util.Hasher;
import org.xvm.util.HasherMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * A thread-safe {@link HasherMap}.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class ConcurrentHasherMap<K, V> extends HasherMap<K, V> implements ConcurrentMap<K, V> {
    /**
     * Construct a {@link ConcurrentHashMap} with a default initial capacity.
     *
     * @param hasher the {@link Hasher} to use in determining key equality
     */
    public ConcurrentHasherMap(Hasher<K> hasher) {
        this(hasher, ConcurrentHashMap::new);
    }

    /**
     * Construct a {@link ConcurrentHashMap} with a default initial capacity.
     *
     * @param hasher  the {@link Hasher} to use in determining key equality
     * @param storage the thread-safe backing store allocator
     */
    public ConcurrentHasherMap(Hasher<K> hasher, Supplier<? extends ConcurrentMap<Supplier<K>, V>> storage) {
        super(hasher, storage);
    }
}
