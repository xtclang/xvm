package org.xvm.util;

import org.xvm.util.converter.AbstractConverterMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A {@link Map} which determines key equality via a {@link Hasher}.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class HasherMap<K, V> extends AbstractConverterMap<K, V, Supplier<K>, V> {
    /**
     * Our key {@link Hasher}.
     */
    final Hasher<K> hasher;

    /**
     * Construct a {@link HasherMap} with a given {@link Hasher}.
     *
     * @param hasher the {@link Hasher} to use in determining key equality
     */
    public HasherMap(Hasher<K> hasher) {
        this(hasher, HashMap::new);
    }

    /**
     * Construct a {@link HasherMap} with a given backing store.
     *
     * @param hasher  the {@link Hasher} to use in determining key equality
     * @param storage the backing store allocator
     */
    public HasherMap(Hasher<K> hasher, Supplier<? extends Map<Supplier<K>, V>> storage) {
        super(storage.get());
        this.hasher = hasher;
    }

    @Override
    protected Supplier<K> keyDown(K key) {
        return new HasherReference<>(key, hasher);
    }

    /**
     * Perform a downwards key conversion for short-lived use cases.
     *
     * @param key the key
     * @return the converted key
     */
    protected TransientHasherReference<K> transientKeyDown(K key) {
        return TransientHasherReference.of(key, hasher);
    }

    @Override
    protected K keyUp(Supplier<K> key) {
        return key == null ? null : key.get();
    }

    @Override
    protected V valueDown(V value) {
        return value;
    }

    @Override
    protected V valueUp(V value) {
        return value;
    }

    @Override
    public V get(Object key) {
        try (var tmpKeyDown = transientKeyDown(unchecked(key))) {
            return read().get(tmpKeyDown);
        }
    }

    @Override
    public boolean containsKey(Object key) {
        try (var tmpKeyDown = transientKeyDown(unchecked(key))) {
            return read().containsKey(tmpKeyDown);
        }
    }

    @Override
    public V remove(Object key) {
        try (var tmpKeyDown = transientKeyDown(unchecked(key))) {
            return write().remove(tmpKeyDown);
        }
    }

    @Override
    public V replace(K key, V value) {
        try (var tmpKeyDown = transientKeyDown(key)) {
            return write().replace(tmpKeyDown, value);
        }
    }

    @Override
    protected Set<K> newKeySet() {
        return new KeySet();
    }

    /**
     * Overridden KeySet.
     */
    protected class KeySet extends AbstractConverterMap<K, V, Supplier<K>, V>.KeySet {
        @Override
        public boolean contains(Object o) {
            try (var tmpKeyDown = transientKeyDown(unchecked(o))) {
                return read().contains(tmpKeyDown);
            }
        }

        @Override
        public boolean remove(Object o) {
            try (var tmpKeyDown = transientKeyDown(unchecked(o))) {
                return write().remove(tmpKeyDown);
            }
        }
    }
}
