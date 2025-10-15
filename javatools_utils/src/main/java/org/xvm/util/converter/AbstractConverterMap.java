package org.xvm.util.converter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A delegating {@link Map} which supports conversion of its keys and values.
 */
public abstract class AbstractConverterMap<K, V, SK, SV> implements Map<K, V> {
    /**
     * The backing map
     */
    private final Map<SK, SV> storage;

    /**
     * The converter keySet.
     */
    protected final Set<K> keys;

    /**
     * The converter values.
     */
    protected final Collection<V> values;

    /**
     * The converter entrySet.
     */
    protected final Set<Entry<K, V>> entries;

    /**
     * Construct a {@link AbstractConverterMap}.
     *
     * @param storage the backing store
     */
    @SuppressWarnings("this-escape")
    protected AbstractConverterMap(final Map<SK, SV> storage) {
        this.storage = storage;
        keys = newKeySet();
        values = newValues();
        entries = newEntrySet();
    }

    /**
     * @return return {@link #storage} for use in read operations.
     */
    protected Map<SK, SV> read() {
        return storage;
    }

    /**
     * @return return {@link #storage} for use in write operations.
     */
    protected Map<SK, SV> write() {
        return storage;
    }

    /**
     * Convert from public to storage key.
     *
     * @param key the public facing key
     * @return the storage key.
     */
    abstract protected SK keyDown(K key);

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

    /**
     * Perform a blind cast to the desired type.
     *
     * @param x   the object to cast
     * @param <X> the type to cast from
     * @param <Y> the type to cast to
     * @return the supplied object
     */
    @SuppressWarnings("unchecked")
    protected static <X, Y extends X> Y unchecked(final X x) {
        return (Y) x;
    }

    /**
     * @return a new keyset
     */
    protected Set<K> newKeySet() {
        return new KeySet();
    }

    /**
     * @return a new values collection
     */
    protected Collection<V> newValues() {
        return new Values();
    }

    /**
     * @return a new entrySet
     */
    protected Set<Entry<K, V>> newEntrySet() {
        return new EntrySet();
    }

    @Override
    public int size() {
        return read().size();
    }

    @Override
    public boolean isEmpty() {
        return read().isEmpty();
    }

    @Override
    public boolean containsKey(final Object key) {
        return read().containsKey(keyDown(unchecked(key)));
    }

    @Override
    public boolean containsValue(final Object value) {
        return read().containsValue(valueDown(unchecked(value)));
    }

    @Override
    public V get(final Object key) {
        return valueUp(read().get(keyDown(unchecked(key))));
    }

    @Override
    public V put(final K key, final V value) {
        return valueUp(write().put(keyDown(key), valueDown(value)));
    }

    @Override
    public V remove(final Object key) {
        return valueUp(write().remove(keyDown(unchecked(key))));
    }

    @Override
    public void putAll(final Map<? extends K, ? extends V> m) {
        Map<SK, SV> storage = write();
        for (final var entry : m.entrySet()) {
            storage.put(keyDown(entry.getKey()), valueDown(entry.getValue()));
        }
    }

    @Override
    public void clear() {
        write().clear();
    }

    @Override
    public Set<K> keySet() {
        return keys;
    }

    @Override
    public Collection<V> values() {
        return values;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return entries;
    }

    @Override
    public void replaceAll(final BiFunction<? super K, ? super V, ? extends V> function) {
        write().replaceAll((k, v) -> valueDown(function.apply(keyUp(k), valueUp(v))));
    }

    @Override
    public V putIfAbsent(final K key, final V value) {
        return valueUp(write().putIfAbsent(keyDown(key), valueDown(value)));
    }

    @Override
    public boolean remove(final Object key, final Object value) {
        return write().remove(keyDown(unchecked(key)), valueDown(unchecked(value)));
    }

    @Override
    public boolean replace(final K key, final V oldValue, final V newValue) {
        return write().replace(keyDown(key), valueDown(oldValue), valueDown(newValue));
    }

    @Override
    public V replace(final K key, final V value) {
        return valueUp(write().replace(keyDown(key), valueDown(value)));
    }

    @Override
    public V computeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
        return valueUp(write().computeIfAbsent(keyDown(key), _ -> valueDown(mappingFunction.apply(key))));
    }

    @Override
    public V computeIfPresent(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return valueUp(write().computeIfPresent(keyDown(key), (_, v) -> valueDown(remappingFunction.apply(key, valueUp(v)))));
    }

    @Override
    public V compute(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return valueUp(write().compute(keyDown(key), (_, v) -> valueDown(remappingFunction.apply(key, valueUp(v)))));
    }

    @Override
    public V merge(final K key, final V value, final BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return valueUp(write().merge(keyDown(key), valueDown(value), (v1, v2) -> valueDown(remappingFunction.apply(valueUp(v1), valueUp(v2)))));
    }

    /**
     * The converter keyset class
     */
    protected class KeySet extends AbstractConverterSet<K, SK> {
        protected KeySet() {
            super(AbstractConverterMap.this.storage.keySet());
        }

        @Override
        protected Collection<SK> read() {
            AbstractConverterMap.this.read();
            return super.read();
        }

        @Override
        protected Collection<SK> write() {
            AbstractConverterMap.this.write();
            return super.write();
        }

        @Override
        protected K valueUp(final SK value) {
            return AbstractConverterMap.this.keyUp(value);
        }

        @Override
        protected SK valueDown(final K value) {
            return AbstractConverterMap.this.keyDown(value);
        }
    }

    /**
     * The converter values class
     */
    protected class Values extends AbstractConverterCollection<V, SV> {
        protected Values() {
            super(AbstractConverterMap.this.storage.values());
        }

        @Override
        protected Collection<SV> read() {
            AbstractConverterMap.this.read();
            return super.read();
        }

        @Override
        protected Collection<SV> write() {
            AbstractConverterMap.this.write();
            return super.write();
        }

        @Override
        protected V valueUp(final SV value) {
            return AbstractConverterMap.this.valueUp(value);
        }

        @Override
        protected SV valueDown(final V value) {
            return AbstractConverterMap.this.valueDown(value);
        }
    }

    /**
     * The converter entrySet class
     */
    protected class EntrySet extends AbstractConverterSet<Entry<K, V>, Entry<SK, SV>> {
        protected EntrySet() {
            super(AbstractConverterMap.this.storage.entrySet());
        }

        @Override
        protected Collection<Entry<SK, SV>> read() {
            AbstractConverterMap.this.read();
            return super.read();
        }

        @Override
        protected Collection<Entry<SK, SV>> write() {
            AbstractConverterMap.this.write();
            return super.write();
        }

        @Override
        protected Entry<K, V> valueUp(final Entry<SK, SV> value) {
            return new AbstractConverterEntry<>(value) {
                @Override
                protected K keyUp(final SK key) {
                    return AbstractConverterMap.this.keyUp(key);
                }

                @Override
                protected SV valueDown(final V value) {
                    return AbstractConverterMap.this.valueDown(value);
                }

                @Override
                protected V valueUp(final SV value) {
                    return AbstractConverterMap.this.valueUp(value);
                }
            };
        }

        @Override
        protected Entry<SK, SV> valueDown(final Entry<K, V> value) {
            return new AbstractConverterEntry<>(value) {
                @Override
                protected SK keyUp(final K key) {
                    return AbstractConverterMap.this.keyDown(key);
                }

                @Override
                protected V valueDown(final SV value) {
                    return AbstractConverterMap.this.valueUp(value);
                }

                @Override
                protected SV valueUp(final V value) {
                    return AbstractConverterMap.this.valueDown(value);
                }
            };
        }
    }
}
