package org.xvm.util.converter;

import org.xvm.util.Hash;

import java.util.Map;
import java.util.Objects;

/**
 * A delegating {@link Map.Entry} which converts keys and values.
 */
public abstract class AbstractConverterEntry<K, V, SK, SV> implements Map.Entry<K, V> {
    /**
     * The entry storage.
     */
    protected Map.Entry<SK, SV> storage;

    /**
     * lazily cached key
     */
    protected K key;

    /**
     * Construct a {@link AbstractConverterEntry}.
     *
     * @param storage the storage entry
     */
    protected AbstractConverterEntry(Map.Entry<SK, SV> storage) {
        this.storage = storage;
    }

    /**
     * @return the storage entry for read operations.
     */
    protected Map.Entry<SK, SV> read() {
        return storage;
    }

    /**
     * @return the storage entry for read operations.
     */
    protected Map.Entry<SK, SV> write() {
        return storage;
    }

    /**
     * Convert from storage key to the public key.
     *
     * @param key the storage facing key
     * @return the public key.
     */
    abstract protected K keyUp(SK key);

    /**
     * Convert from public to storage value.
     *
     * @param value the public facing value
     * @return the storage value.
     */
    abstract protected SV valueDown(V value);

    /**
     * Convert from storage key to the public value.
     *
     * @param value the storage facing value
     * @return the public value.
     */
    abstract protected V valueUp(SV value);

    @Override
    public K getKey() {
        K key = this.key;
        return key == null ? this.key = keyUp(read().getKey()) : key;
    }

    @Override
    public V getValue() {
        return valueUp(read().getValue());
    }

    @Override
    public V setValue(V value) {
        return valueUp(write().setValue(valueDown(value)));
    }

    @Override
    public int hashCode() {
        return Hash.of(getKey());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Map.Entry<?, ?> that
                && Objects.equals(getKey(), that.getKey()) && Objects.equals(getValue(), that.getValue());
    }
}
