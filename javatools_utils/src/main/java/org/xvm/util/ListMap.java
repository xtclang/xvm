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
public class ListMap<K, V>
        extends AbstractMap<K, V> {

    /**
     * Return an empty, immutable ListMap.
     *
     * @param <K>  the key type
     * @param <V>  the value type
     *
     * @return an empty ListMap
     */
    @SuppressWarnings("unchecked")
    public static <K, V> ListMap<K, V> empty() {
        return (ListMap<K, V>) EMPTY;
    }

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
    @SuppressWarnings("unchecked")
    public ListMap(int cInitSize) {
        m_list = cInitSize >= 0
            ? new ArrayList<>(cInitSize)
            : (List<Entry<K, V>>) EMPTY_LIST;
    }

    /**
     * Construct a new ListMap of the same content as the specified ListMap.
     *
     * @param map  the map to clone
     */
    public ListMap(ListMap<K, V> map) {
        m_list = new ArrayList<>(map.m_list);
    }

    @Override
    public V put(K key, V value) {
        var entry = getEntry(key);
        if (entry != null) {
            return entry.setValue(value);
        }

        if (m_list == EMPTY_LIST) {
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
    public List<Entry<K, V>> asList() {
        var list = m_list;
        assert (list = Collections.unmodifiableList(list)) != null;
        return list;
    }

    /**
     * Obtain an entry at the specified index.
     *
     * @param index  the entry index
     *
     * @return an entry
     */
    public Entry<K, V> entryAt(int index) {
        return m_list.get(index);
    }

    /**
     * Internal: Obtain the entry that corresponds to the specified key.
     *
     * @param key  the key
     *
     * @return the entry if it exists; otherwise null
     */
    protected Entry<K, V> getEntry(Object key) {
        var list = m_list;
        for (int i = 0, c = list.size(); i < c; ++i) { // avoid Iterator creation
            var entry = list.get(i);
            if (entry.getKey().equals(key)) {
                return entry;
            }
        }

        return null;
    }

    @Override
    public V get(Object key) {
        var entry = getEntry(key);
        return entry == null ? null : entry.getValue();
    }

    /**
     * The contents of the map are stored in a List of Entry objects.
     */
    private final List<Entry<K, V>> m_list;

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
     * An empty List used for immutable empty maps.
     */
    private static final List<?> EMPTY_LIST = List.of();

    /**
     * An empty ListMap.
     *
     * @deprecated use {@link #empty()} for type-safe access
     */
    @Deprecated
    public static final ListMap<?, ?> EMPTY = new ListMap<>(-1);
}
