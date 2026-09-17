package org.xvm.util;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A simple implementation of a Map on top of an ArrayList to maintain order of
 * insertion. This map should work well for small numbers of entries, but will
 * degrade in performance as it grows in size.
 */
public class ListMap<K,V>
        extends AbstractMap<K,V> {
    /**
     * Construct a new ListMap.
     */
    public ListMap() {
        m_list       = new ArrayList<>();
        f_fImmutable = false;
    }

    /**
     * Construct a new ListMap of the specified initial capacity.
     *
     * @param cInitSize  the initial capacity; negative value indicates an immutable empty map
     */
    public ListMap(int cInitSize) {
        m_list       = new ArrayList<>(Math.max(cInitSize, 0));
        f_fImmutable = cInitSize < 0;
    }

    /**
     * Construct a new ListMap of the same content as the specified ListMap.
     *
     * @param map  the map to clone
     */
    public ListMap(ListMap<K, V> map) {
        m_list       = new ArrayList<>(map.m_list.size());
        f_fImmutable = false;

        for (Entry<K, V> entry : map.m_list) {
            m_list.add(new SimpleEntry<>(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * @return the immutable empty ListMap
     *
     * @param <K>  the key type
     * @param <V>  the value type
     */
    @SuppressWarnings("unchecked")
    public static <K, V> ListMap<K, V> empty() {
        return (ListMap<K, V>) EMPTY;
    }

    @Override
    public V put(K key, V value) {
        if (f_fImmutable) {
            throw new UnsupportedOperationException();
        }

        Entry<K,V> entry = getEntry(key);
        if (entry != null) {
            return entry.setValue(value);
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
    public List<Entry<K,V>> asList() {
        return Collections.unmodifiableList(m_list);
    }

    /**
     * Obtain an entry at the specified index.
     *
     * @param index  the entry index
     *
     * @return an entry
     */
    public Entry<K,V> entryAt(int index) {
        return m_list.get(index);
    }

    /**
     * Internal: Obtain the entry that corresponds to the specified key.
     *
     * @param key  the key
     *
     * @return the entry if it exists; otherwise null
     */
    protected Entry<K,V> getEntry(Object key) {
        ArrayList<Entry<K,V>> list = m_list;
        for (int i = 0, c = list.size(); i < c; ++i) { // avoid Iterator creation
            Entry<K, V> entry = list.get(i);
            if (entry.getKey().equals(key)) {
                return entry;
            }
        }

        return null;
    }

    @Override
    public V get(Object key) {
        Entry<K, V> entry = getEntry(key);
        return entry == null ? null : entry.getValue();
    }

    /**
     * The contents of the map are stored in an ArrayList of SimpleEntry
     * objects.
     */
    private final ArrayList<Entry<K, V>> m_list;

    /**
     * True iff this map refuses to take entries; see {@link #ListMap(int)}.
     */
    private final boolean f_fImmutable;

    /**
     * The AbstractMap implementation needs an underlying "entry set" to be
     * provided; this is that set, but just sitting on top of {@link #m_list}.
     */
    private final Set<Entry<K, V>> m_setEntries = new AbstractSet<>() {
        @Override
        public Iterator<Entry<K, V>> iterator() {
            return m_list.iterator();
        }

        @Override
        public int size() {
            return m_list.size();
        }
    };

    /**
     * An empty ListMap. Immutable - {@link #put} rejects it - so one instance serves every
     * parameterization; reach it through {@link #empty()}.
     */
    private static final ListMap<?, ?> EMPTY = new ListMap<>(-1);
}
