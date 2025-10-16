package org.xvm.util;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A simple implementation of a Map on top of an ArrayList to maintain order of
 * insertion. This map should work well for small numbers of entries, but will
 * degrade in performance as it grows in size.
 */
public class ListMap<K,V> extends AbstractMap<K,V> {
    /**
     * Construct a new ListMap.
     */
    public ListMap() {
        m_list = new ArrayList<>();
    }

    /**
     * Construct a new ListMap of the specified initial capacity.
     *
     * @param cInitSize  the initial capacity; negative value indicates an immutable empty map
     */
    @SuppressWarnings("unchecked") // Safe: EMPTY_ARRAY_LIST is empty and immutable
    public ListMap(final int cInitSize) {
        m_list = cInitSize >= 0
            ? new ArrayList<>(cInitSize)
            : (ArrayList<SimpleEntry<K,V>>) EMPTY_ARRAY_LIST;
    }

    /**
     * Construct a new ListMap of the same content as the specified ListMap.
     *
     * @param map  the map to clone
     */
    public ListMap(final ListMap<K, V> map) {
        m_list = new ArrayList<>(map.m_list);
    }

    @Override
    public V put(final K key, final V value) {
        Entry<K,V> entry = getEntry(key);
        if (entry != null) {
            return entry.setValue(value);
        }

        if (m_list == EMPTY_ARRAY_LIST) {
            throw new UnsupportedOperationException();
        }

        m_list.add(new SimpleEntry<>(key, value));
        return null;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return m_setEntries;
    }

    /**
     * Obtain a read-only list of entries.
     *
     * @return the entries of the map in a List
     */
    @SuppressWarnings("unchecked") // Safe: SimpleEntry<K,V> extends Entry<K,V>
    public List<Entry<K,V>> asList() {
        return (List<Entry<K,V>>) (List<?>) m_list;
    }

    /**
     * Obtain an entry at the specified index.
     *
     * @param index  the entry index
     *
     * @return an entry
     */
    public Entry<K,V> entryAt(final int index) {
        return m_list.get(index);
    }

    /**
     * Internal: Obtain the entry that corresponds to the specified key.
     *
     * @param key  the key
     *
     * @return the entry if it exists; otherwise null
     */
    protected SimpleEntry<K,V> getEntry(final Object key) {
        for (final SimpleEntry<K, V> entry : m_list) { // avoid Iterator creation
            if (entry.getKey().equals(key)) {
                return entry;
            }
        }

        return null;
    }

    @Override
    public V get(final Object key) {
        SimpleEntry<K, V> entry = getEntry(key);
        return entry == null ? null : entry.getValue();
    }

    /**
     * The contents of the map are stored in an ArrayList of SimpleEntry
     * objects.
     */
    private final ArrayList<SimpleEntry<K, V>> m_list;

    /**
     * The AbstractMap implementation needs an underlying "entry set" to be
     * provided; this is that set, but just sitting on top of {@link #m_list}.
     */
    private final Set<Entry<K, V>> m_setEntries = new AbstractSet<>() {
        @Override
        @SuppressWarnings("unchecked") // Safe: SimpleEntry<K,V> extends Entry<K,V>
        public Iterator<Entry<K, V>> iterator() {
            return (Iterator<Entry<K, V>>) (Iterator<?>) m_list.iterator();
        }

        @Override
        public int size() {
            return m_list.size();
        }
    };

    /**
     * An empty ArrayList.
     */
    private static final ArrayList<?> EMPTY_ARRAY_LIST = new ArrayList<>(0);

    /**
     * An empty ListMap.
     */
    public static final ListMap<?, ?> EMPTY = new ListMap<>(-1);

    /**
     * Return a typed empty ListMap.
     *
     * @param <K>  the key type
     * @param <V>  the value type
     *
     * @return an empty ListMap with the correct type parameters
     */
    @SuppressWarnings("unchecked")
    public static <K, V> ListMap<K, V> empty() {
        return (ListMap<K, V>) EMPTY;
    }
}